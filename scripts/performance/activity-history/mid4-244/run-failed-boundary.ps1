param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('fanout-200-1m', 'fanout-200-10m', 'general-300-10m')]
    [string] $Boundary,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string] $PgContainer,
    [string] $BaseUrl = 'http://host.docker.internal:8080',
    [string[]] $TargetUserIds = @('00000001-0000-4000-8000-000000000001'),
    [ValidateSet('current', 'partial')]
    [string] $IndexVariant = 'current',
    [ValidateSet(1, 2, 5)]
    [int] $FanoutMultiplier = 1,
    [ValidateRange(0, 300)]
    [int] $StabilizationSeconds = 30,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string] $ResultSet = 'mid4-244-rdb-failed-boundary'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$k6Runner = Join-Path $repoRoot 'scripts\performance\activity-history\k6\run-mongodb-compare.ps1'
$indexFile = Join-Path $PSScriptRoot 'partial-covering-indexes.sql'
$resultRoot = Join-Path $repoRoot "scripts\performance\activity-history\k6\results\$ResultSet"

$running = docker inspect --format '{{.State.Running}}' $PgContainer 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
    throw "PostgreSQL container is not running: $PgContainer"
}

if ($IndexVariant -eq 'partial') {
    Write-Host "apply partial covering indexes: container=$PgContainer"
    Get-Content -LiteralPath $indexFile -Raw -Encoding UTF8 |
        docker exec -i $PgContainer psql -X -q -U monew -d monew -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to apply partial covering indexes.'
    }
}

$rate = if ($Boundary.StartsWith('fanout-')) { 200 } else { 300 }
$duration = if ($Boundary.EndsWith('-1m')) { '1m' } else { '10m' }
$repeatCount = if ($Boundary -eq 'fanout-200-1m' -and $FanoutMultiplier -eq 1) { 3 } else { 1 }

$metadata = [ordered]@{
    ticket = 'MID4-244'
    boundary = $Boundary
    indexVariant = $IndexVariant
    fanoutMultiplier = $FanoutMultiplier
    postgresContainer = $PgContainer
    rate = $rate
    duration = $duration
    repeatCount = $repeatCount
    stabilizationSeconds = $StabilizationSeconds
    targetUserIds = $TargetUserIds
    startedAt = (Get-Date).ToString('o')
}
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$metadataPath = Join-Path $resultRoot "metadata-$Boundary-$IndexVariant.json"
$metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $metadataPath -Encoding UTF8

try {
    Write-Host "warm-up: boundary=$Boundary variant=$IndexVariant duration=1m"
    & $k6Runner `
        -Ticket MID4-244 `
        -Variant rdb `
        -Scenario smoke `
        -Duration 1m `
        -BaseUrl $BaseUrl `
        -TargetUserIds $TargetUserIds `
        -UserPickStrategy round-robin `
        -PgContainer $PgContainer `
        -ResultSet $ResultSet `
        -AllowFailure
    if ($LASTEXITCODE -ne 0) {
        throw 'Warm-up runner failed.'
    }

    Write-Host "measure failed boundary: boundary=$Boundary variant=$IndexVariant rate=$rate duration=$duration repeats=$repeatCount"
    & $k6Runner `
        -Ticket MID4-244 `
        -Variant rdb `
        -Scenario throughput `
        -Rates @($rate) `
        -Duration $duration `
        -PreAllocatedVUs 500 `
        -MaxVUs 500 `
        -BaseUrl $BaseUrl `
        -TargetUserIds $TargetUserIds `
        -UserPickStrategy round-robin `
        -PgContainer $PgContainer `
        -ResultSet $ResultSet `
        -RepeatCount $repeatCount `
        -StabilizationSeconds $StabilizationSeconds `
        -AllowFailure
    if ($LASTEXITCODE -ne 0) {
        throw 'Boundary runner failed.'
    }

    $metadata.status = 'completed'
} catch {
    $metadata.status = 'failed'
    $metadata.error = $_.Exception.Message
    throw
} finally {
    $metadata.completedAt = (Get-Date).ToString('o')
    $metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $metadataPath -Encoding UTF8
}

Write-Output "Failed-boundary run complete: $metadataPath"
