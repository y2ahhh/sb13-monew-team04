param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string] $PgContainer,
    [string] $DbUser = 'monew',
    [string] $DbName = 'monew',
    [ValidateRange(1, 20)]
    [int] $WarmupRuns = 3,
    [ValidateRange(1, 20)]
    [int] $MeasureRuns = 5,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string] $ResultSet = 'mid4-244-rdb-index-compare'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$queryRoot = Join-Path $repoRoot 'scripts\performance\activity-history\mid4-227\after'
$resultRoot = Join-Path $repoRoot "scripts\performance\activity-history\k6\results\$ResultSet\sql"
$applyIndexPath = Join-Path $PSScriptRoot 'partial-covering-indexes.sql'
$dropIndexPath = Join-Path $PSScriptRoot 'drop-partial-covering-indexes.sql'
$queryNames = @(
    'recent-comments',
    'recent-comment-likes',
    'recent-article-views',
    'subscribed-interests'
)

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql,
        [switch] $TuplesOnly
    )

    $arguments = @(
        'exec', '-i', $PgContainer,
        'psql', '-X', '-q',
        '-U', $DbUser,
        '-d', $DbName,
        '-v', 'ON_ERROR_STOP=1'
    )
    if ($TuplesOnly) {
        $arguments += @('-t', '-A')
    }

    $output = $Sql | & docker @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed. container=$PgContainer output=$($output -join [Environment]::NewLine)"
    }
    return (($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine).Trim()
}

function Invoke-SqlFile {
    param([Parameter(Mandatory = $true)][string] $Path)

    $sql = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    return Invoke-Psql -Sql $sql
}

function Get-Median {
    param([double[]] $Values)

    $sorted = @($Values | Sort-Object)
    $middle = [math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return [double] $sorted[$middle]
    }
    return ([double] $sorted[$middle - 1] + [double] $sorted[$middle]) / 2
}

function Add-PlanStats {
    param(
        $Plan,
        [Parameter(Mandatory = $true)] $Stats
    )

    if ($null -eq $Plan) {
        return
    }

    if ($null -ne $Plan.'Heap Fetches') {
        $Stats.heapFetches += [long] $Plan.'Heap Fetches'
    }
    if ($null -ne $Plan.'Rows Removed by Filter') {
        $Stats.rowsRemovedByFilter += [long] $Plan.'Rows Removed by Filter' * [long] $Plan.'Actual Loops'
    }
    if ($Plan.'Node Type' -eq 'Index Only Scan') {
        $Stats.indexOnlyScanCount++
    }
    if ($null -ne $Plan.'Subplan Name') {
        $Stats.subplanLoopCount += [long] $Plan.'Actual Loops'
    }
    foreach ($child in @($Plan.Plans)) {
        if ($null -ne $child) {
            Add-PlanStats -Plan $child -Stats $Stats
        }
    }
}

function Invoke-Warmup {
    param([string] $Variant, [string] $QueryName, [string] $QuerySql)

    for ($run = 1; $run -le $WarmupRuns; $run++) {
        Write-Host "warm-up: variant=$Variant query=$QueryName run=$run/$WarmupRuns"
        Invoke-Psql -Sql $QuerySql -TuplesOnly | Out-Null
    }
}

function Invoke-PlainTiming {
    param([string] $Variant, [string] $QueryName, [string] $QuerySql, [int] $Run)

    Write-Host "plain timing: variant=$Variant query=$QueryName run=$Run/$MeasureRuns"
    $output = Invoke-Psql -Sql "\timing on`n$QuerySql"
    $matches = [regex]::Matches($output, 'Time:\s+([0-9]+(?:\.[0-9]+)?)\s+ms')
    if ($matches.Count -eq 0) {
        throw "Unable to parse psql timing. variant=$Variant query=$QueryName output=$output"
    }
    return [double]::Parse(
        $matches[$matches.Count - 1].Groups[1].Value,
        [Globalization.CultureInfo]::InvariantCulture
    )
}

function Invoke-Explain {
    param([string] $Variant, [string] $QueryName, [string] $QuerySql, [int] $Run)

    Write-Host "explain: variant=$Variant query=$QueryName run=$Run/$MeasureRuns"
    $explainSql = "SET track_io_timing = on;`nEXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS, TIMING, FORMAT JSON)`n$QuerySql"
    $json = Invoke-Psql -Sql $explainSql -TuplesOnly
    $jsonPath = Join-Path $resultRoot "$Variant-$QueryName-explain-run$Run.json"
    Set-Content -LiteralPath $jsonPath -Value $json -Encoding UTF8

    $root = ($json | ConvertFrom-Json)[0]
    $stats = [pscustomobject]@{
        heapFetches = [long] 0
        rowsRemovedByFilter = [long] 0
        indexOnlyScanCount = [long] 0
        subplanLoopCount = [long] 0
    }
    Add-PlanStats -Plan $root.Plan -Stats $stats

    return [pscustomobject]@{
        run = $Run
        executionTimeMs = [double] $root.'Execution Time'
        planningTimeMs = [double] $root.'Planning Time'
        sharedHitBlocks = [long] $root.Plan.'Shared Hit Blocks'
        sharedReadBlocks = [long] $root.Plan.'Shared Read Blocks'
        ioReadTimeMs = if ($null -eq $root.Plan.'I/O Read Time') { 0.0 } else { [double] $root.Plan.'I/O Read Time' }
        heapFetches = $stats.heapFetches
        rowsRemovedByFilter = $stats.rowsRemovedByFilter
        indexOnlyScanCount = $stats.indexOnlyScanCount
        subplanLoopCount = $stats.subplanLoopCount
        file = $jsonPath.Substring($repoRoot.Length + 1)
    }
}

function Invoke-ExplainText {
    param([string] $Variant, [string] $QueryName, [string] $QuerySql)

    $sql = "SET track_io_timing = on;`nEXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS, TIMING)`n$QuerySql"
    $plan = Invoke-Psql -Sql $sql
    $path = Join-Path $resultRoot "$Variant-$QueryName-explain-text.txt"
    Set-Content -LiteralPath $path -Value $plan -Encoding UTF8
    return $path.Substring($repoRoot.Length + 1)
}

function Measure-Variant {
    param([Parameter(Mandatory = $true)][string] $Variant)

    $variantSummary = [ordered]@{}
    foreach ($queryName in $queryNames) {
        $queryPath = Join-Path $queryRoot "$queryName.sql"
        $querySql = Get-Content -LiteralPath $queryPath -Raw -Encoding UTF8
        Invoke-Warmup -Variant $Variant -QueryName $queryName -QuerySql $querySql

        $plainTimes = @()
        for ($run = 1; $run -le $MeasureRuns; $run++) {
            $plainTimes += Invoke-PlainTiming -Variant $Variant -QueryName $queryName -QuerySql $querySql -Run $run
        }

        $explainRuns = @()
        for ($run = 1; $run -le $MeasureRuns; $run++) {
            $explainRuns += Invoke-Explain -Variant $Variant -QueryName $queryName -QuerySql $querySql -Run $run
        }

        $executionMedian = Get-Median -Values @($explainRuns | ForEach-Object { $_.executionTimeMs })
        $representative = $explainRuns |
            Sort-Object @{ Expression = { [math]::Abs($_.executionTimeMs - $executionMedian) } }, run |
            Select-Object -First 1

        $variantSummary[$queryName] = [ordered]@{
            sqlFile = $queryPath.Substring($repoRoot.Length + 1)
            plainTimesMs = $plainTimes
            plainMedianMs = Get-Median -Values $plainTimes
            explainRuns = $explainRuns
            explainMedianMs = $executionMedian
            representativeRun = $representative.run
            representativeFile = $representative.file
            textPlanFile = Invoke-ExplainText -Variant $Variant -QueryName $queryName -QuerySql $querySql
        }
    }
    return $variantSummary
}

$running = docker inspect --format '{{.State.Running}}' $PgContainer 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
    throw "PostgreSQL container is not running: $PgContainer"
}

$summary = [ordered]@{
    ticket = 'MID4-244'
    container = $PgContainer
    warmupRuns = $WarmupRuns
    measureRuns = $MeasureRuns
    measuredAt = (Get-Date).ToString('o')
    variants = [ordered]@{}
}

Write-Host 'restore current develop index state'
Invoke-SqlFile -Path $dropIndexPath | Out-Null
$summary.variants.current = Measure-Variant -Variant 'current'

Write-Host 'apply partial covering index candidate'
$indexOutput = Invoke-SqlFile -Path $applyIndexPath
Set-Content -LiteralPath (Join-Path $resultRoot 'partial-index-sizes.txt') -Value $indexOutput -Encoding UTF8
$summary.variants.partial = Measure-Variant -Variant 'partial'

$summaryPath = Join-Path $resultRoot 'summary.json'
$summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
Write-Output "SQL index comparison complete: $summaryPath"
