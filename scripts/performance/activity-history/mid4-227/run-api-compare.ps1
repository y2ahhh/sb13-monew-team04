param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('before', 'after')]
    [string] $BuildLabel,
    [Parameter(Mandatory = $true)]
    [ValidateSet('fanout', 'exclusion', 'general')]
    [string] $Overlay,
    [Parameter(Mandatory = $true)]
    [string] $AppJar,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{7,40}$')]
    [string] $AppCommit,
    [int] $DbPort = 15428,
    [int] $AppPort = 8080,
    [ValidatePattern('^MID4-[0-9]+$')]
    [string] $Ticket = 'MID4-227',
    [ValidatePattern('^[a-z0-9][a-z0-9-]*$')]
    [string] $ProjectNameOverride,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string] $ResultSetOverride,
    [string] $PostOverlaySql,
    [int[]] $ThroughputRates = @(),
    [switch] $StopOnRepeatedFailure,
    [switch] $ReuseDatabase,
    [switch] $SkipThroughputMatrix,
    [switch] $SkipSoak,
    [ValidateRange(0, 5000)]
    [int] $SoakRate = 0,
    [ValidateRange(1, 10)]
    [int] $ThroughputRepeatCount = 3,
    [ValidateRange(0, 300)]
    [int] $StabilizationSeconds = 30
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$appJarPath = (Resolve-Path -LiteralPath $AppJar).Path
$projectName = if ([string]::IsNullOrWhiteSpace($ProjectNameOverride)) {
    "monew-perf-227-api-$BuildLabel-$Overlay"
} else {
    $ProjectNameOverride
}
$pgContainer = "$projectName-postgres-1"
$resultSet = if ([string]::IsNullOrWhiteSpace($ResultSetOverride)) {
    "mid4-227-rdb-$BuildLabel-$Overlay"
} else {
    $ResultSetOverride
}
$resultRoot = Join-Path $repoRoot "scripts\performance\activity-history\k6\results\$resultSet"
$appLogRoot = Join-Path $resultRoot 'app'
$envFile = Join-Path $repoRoot '.env.perf.local'
$k6Runner = Join-Path $repoRoot 'scripts\performance\activity-history\k6\run-mongodb-compare.ps1'
$overlayFile = if ($Overlay -eq 'general') {
    $null
} else {
    Join-Path $repoRoot "scripts\performance\activity-history\$Overlay-overlay.sql"
}
$baseUrl = "http://host.docker.internal:$AppPort"
$localBaseUrl = "http://localhost:$AppPort"
$targetUserId = '00000001-0000-4000-8000-000000000001'
$appProcess = $null
$rateDecisions = [System.Collections.Generic.List[object]]::new()

if (-not $projectName.StartsWith('monew-perf-')) {
    throw "Unexpected compose project name: $projectName"
}

if ($SkipThroughputMatrix -and $SkipSoak) {
    throw 'SkipThroughputMatrix and SkipSoak cannot be used together.'
}

$postOverlaySqlPath = $null
if (-not [string]::IsNullOrWhiteSpace($PostOverlaySql)) {
    $postOverlaySqlPath = (Resolve-Path -LiteralPath $PostOverlaySql).Path
}

New-Item -ItemType Directory -Path $appLogRoot -Force | Out-Null

Get-Content -LiteralPath $envFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}

$env:MONEW_DB_HOST = 'localhost'
$env:MONEW_DB_PORT = [string] $DbPort
$env:MONEW_DB_NAME = 'monew'
$env:MONEW_SERVER_PORT = [string] $AppPort
$env:MONEW_DOCKER_COMPOSE_ENABLED = 'false'
$env:MONEW_ARTICLE_BACKUP_ENABLED = 'false'
$env:MONEW_ARTICLE_COLLECT_ENABLED = 'false'
$env:MONEW_NOTIFICATION_DELETE_ENABLED = 'false'
$env:MONEW_USER_AUTO_DELETE_ENABLED = 'false'
$env:MONEW_JPA_SHOW_SQL = 'false'
$env:MONEW_FLYWAY_ENABLED = 'true'

function Invoke-Compose {
    param([string[]] $Arguments)

    Push-Location $repoRoot
    try {
        & docker compose -p $projectName --env-file $envFile @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose failed. project=$projectName arguments=$($Arguments -join ' ')"
        }
    } finally {
        Pop-Location
    }
}

function Stop-App {
    if ($null -eq $script:appProcess) {
        return
    }

    $script:appProcess.Refresh()
    if (-not $script:appProcess.HasExited) {
        Stop-Process -Id $script:appProcess.Id -Force
        $script:appProcess.WaitForExit()
    }
    $script:appProcess = $null
}

function Start-App {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('migration', 'debug', 'performance')]
        [string] $Mode
    )

    Stop-App

    if ($Mode -eq 'debug') {
        $env:LOGGING_LEVEL_ORG_HIBERNATE_SQL = 'DEBUG'
        $env:LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BATCH = 'WARN'
        $env:LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND = 'OFF'
    } else {
        $env:LOGGING_LEVEL_ORG_HIBERNATE_SQL = 'WARN'
        $env:LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BATCH = 'WARN'
        $env:LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND = 'OFF'
    }

    $stdoutPath = Join-Path $appLogRoot "$Mode.out.log"
    $stderrPath = Join-Path $appLogRoot "$Mode.err.log"
    $script:appProcess = Start-Process `
        -FilePath java `
        -ArgumentList @('-jar', $appJarPath, '--spring.profiles.active=dev') `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath

    for ($attempt = 1; $attempt -le 120; $attempt++) {
        $script:appProcess.Refresh()
        if ($script:appProcess.HasExited) {
            throw "Application exited before readiness. mode=$Mode exitCode=$($script:appProcess.ExitCode) log=$stdoutPath"
        }

        try {
            $response = Invoke-WebRequest `
                -Uri "$localBaseUrl/v3/api-docs" `
                -UseBasicParsing `
                -TimeoutSec 2 `
                -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Host "application ready: build=$BuildLabel overlay=$Overlay mode=$Mode pid=$($script:appProcess.Id)"
                return
            }
        } catch { }

        Start-Sleep -Seconds 1
    }

    throw "Application readiness timeout. mode=$Mode"
}

function Invoke-K6Matrix {
    Write-Host "k6 smoke warm-up: build=$BuildLabel overlay=$Overlay"
    & $k6Runner `
        -Ticket $Ticket `
        -Variant rdb `
        -Scenario smoke `
        -Duration 1m `
        -BaseUrl $baseUrl `
        -PgContainer $pgContainer `
        -ResultSet $resultSet
    if ($LASTEXITCODE -ne 0) { throw 'k6 smoke warm-up failed.' }

    if ($SkipThroughputMatrix) {
        Write-Host 'skip 1m throughput matrix: requested by caller'
    } else {
        $rates = if ($ThroughputRates.Count -gt 0) {
            $ThroughputRates
        } elseif ($Overlay -eq 'fanout') {
            @(10, 20, 30, 40, 50, 100, 150, 200)
        } elseif ($Overlay -eq 'general') {
            @(350, 400, 450, 500)
        } else {
            @(10, 20, 30, 40, 50)
        }

        Write-Host "k6 1m matrix: build=$BuildLabel overlay=$Overlay rates=$($rates -join ',') repeats=$ThroughputRepeatCount"
        if ($StopOnRepeatedFailure) {
            foreach ($rate in $rates) {
                $rateStartedAtUtc = (Get-Date).ToUniversalTime()
                & $k6Runner `
                    -Ticket $Ticket `
                    -Variant rdb `
                    -Scenario throughput `
                    -Rates @($rate) `
                    -Duration 1m `
                    -PreAllocatedVUs 500 `
                    -MaxVUs 500 `
                    -BaseUrl $baseUrl `
                    -PgContainer $pgContainer `
                    -ResultSet $resultSet `
                    -RepeatCount $ThroughputRepeatCount `
                    -StabilizationSeconds $StabilizationSeconds `
                    -AllowFailure
                if ($LASTEXITCODE -ne 0) { throw "k6 1m rate runner failed. rate=$rate" }

                $summaries = @(Get-ChildItem -LiteralPath $resultRoot -Filter "activity-history-rdb-throughput-${rate}rps*-summary.json" |
                    Where-Object { $_.LastWriteTimeUtc -ge $rateStartedAtUtc.AddSeconds(-1) } |
                    Sort-Object LastWriteTimeUtc |
                    ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json })
                if ($summaries.Count -ne $ThroughputRepeatCount) {
                    throw "Unexpected summary count. rate=$rate expected=$ThroughputRepeatCount actual=$($summaries.Count)"
                }

                $passedRuns = @($summaries | Where-Object {
                    $_.metrics.errorRate -lt 0.01 -and
                    $_.metrics.checksRate -gt 0.99 -and
                    $_.metrics.droppedIterations -eq 0 -and
                    $_.metrics.durationP95Ms -lt 200 -and
                    $_.metrics.durationP99Ms -lt 500
                }).Count
                $failedRuns = $ThroughputRepeatCount - $passedRuns
                $decision = if ($failedRuns -eq 0) {
                    'stable-pass'
                } elseif ($failedRuns -ge 2) {
                    'repeated-failure'
                } else {
                    'unstable-pass'
                }
                $script:rateDecisions.Add([ordered]@{
                    rate = $rate
                    passedRuns = $passedRuns
                    failedRuns = $failedRuns
                    decision = $decision
                })
                Write-Host "rate decision: rate=$rate passed=$passedRuns failed=$failedRuns decision=$decision"

                if ($failedRuns -ge 2) {
                    Write-Host "stop after first repeated failure: rate=$rate"
                    break
                }
            }
        } else {
            & $k6Runner `
                -Ticket $Ticket `
                -Variant rdb `
                -Scenario throughput `
                -Rates $rates `
                -Duration 1m `
                -PreAllocatedVUs 500 `
                -MaxVUs 500 `
                -BaseUrl $baseUrl `
                -PgContainer $pgContainer `
                -ResultSet $resultSet `
                -RepeatCount $ThroughputRepeatCount `
                -StabilizationSeconds $StabilizationSeconds `
                -AllowFailure
            if ($LASTEXITCODE -ne 0) { throw 'k6 1m matrix runner failed.' }
        }
    }

    if ($SkipSoak) {
        Write-Host 'skip 10m soak: requested by caller'
        return
    }

    $soakRates = if ($SoakRate -gt 0) {
        @($SoakRate)
    } elseif ($Overlay -eq 'fanout') {
        @(10, 20)
    } elseif ($Overlay -eq 'general') {
        @(300)
    } else {
        @(50)
    }
    Write-Host "k6 10m soak: build=$BuildLabel overlay=$Overlay rates=$($soakRates -join ',')"
    & $k6Runner `
        -Ticket $Ticket `
        -Variant rdb `
        -Scenario throughput `
        -Rates $soakRates `
        -Duration 10m `
        -PreAllocatedVUs 500 `
        -MaxVUs 500 `
        -BaseUrl $baseUrl `
        -PgContainer $pgContainer `
        -ResultSet $resultSet `
        -RepeatCount 1 `
        -StabilizationSeconds $StabilizationSeconds `
        -AllowFailure
    if ($LASTEXITCODE -ne 0) { throw 'k6 soak runner failed.' }
}

$metadata = [ordered]@{
    ticket = $Ticket
    buildLabel = $BuildLabel
    commit = $AppCommit
    overlay = $Overlay
    postgresImage = 'postgres:16'
    seedScale = '10m'
    appPort = $AppPort
    dbPort = $DbPort
    userPickStrategy = 'round-robin'
    preAllocatedVUs = 500
    maxVUs = 500
    throughputRepeatCount = $ThroughputRepeatCount
    stabilizationSeconds = $StabilizationSeconds
    throughputRates = $ThroughputRates
    stopOnRepeatedFailure = [bool] $StopOnRepeatedFailure
    reuseDatabase = [bool] $ReuseDatabase
    skipThroughputMatrix = [bool] $SkipThroughputMatrix
    postOverlaySql = if ($null -eq $postOverlaySqlPath) { $null } else { Split-Path -Leaf $postOverlaySqlPath }
    skipSoak = [bool] $SkipSoak
    soakRate = $SoakRate
    rateDecisions = @()
    startedAt = (Get-Date).ToString('o')
}
$metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultRoot 'metadata.json') -Encoding UTF8

try {
    if ($ReuseDatabase) {
        Write-Host "reuse database: project=$projectName"
        Invoke-Compose @('up', '-d', '--wait', 'postgres')
    } else {
        Write-Host "reset database: project=$projectName"
        Invoke-Compose @('down', '-v', '--remove-orphans')
        $env:MONEW_DB_PORT = [string] $DbPort
        Invoke-Compose @('up', '-d', '--wait', 'postgres')

        Start-App -Mode migration
        Stop-App

        Write-Host "seed database: build=$BuildLabel overlay=$Overlay scale=10m"
        Invoke-Compose @('--profile', 'perf-seed', 'run', '--rm', '-e', 'SEED_SCALE=10m', 'postgres-seed')

        if ($null -ne $overlayFile) {
            Write-Host "apply overlay: $Overlay"
            Get-Content -LiteralPath $overlayFile -Raw -Encoding UTF8 |
                docker exec -i $pgContainer psql -X -U monew -d monew -v ON_ERROR_STOP=1
            if ($LASTEXITCODE -ne 0) { throw "Failed to apply overlay: $Overlay" }
        } else {
            Write-Host 'skip overlay: general data'
        }

        if ($null -ne $postOverlaySqlPath) {
            Write-Host "apply post-overlay SQL: $postOverlaySqlPath"
            Get-Content -LiteralPath $postOverlaySqlPath -Raw -Encoding UTF8 |
                docker exec -i $pgContainer psql -X -U monew -d monew -v ON_ERROR_STOP=1
            if ($LASTEXITCODE -ne 0) { throw "Failed to apply post-overlay SQL: $postOverlaySqlPath" }
        }
    }

    Start-App -Mode debug
    $activityResponse = Invoke-WebRequest `
        -Uri "$localBaseUrl/api/user-activities/$targetUserId" `
        -UseBasicParsing `
        -TimeoutSec 60 `
        -ErrorAction Stop
    if ($activityResponse.StatusCode -ne 200) {
        throw "Activity API SQL verification failed. status=$($activityResponse.StatusCode)"
    }
    Stop-App

    Start-App -Mode performance
    Invoke-K6Matrix
    $metadata.completedAt = (Get-Date).ToString('o')
    $metadata.status = 'completed'
} catch {
    $metadata.completedAt = (Get-Date).ToString('o')
    $metadata.status = 'failed'
    $metadata.error = $_.Exception.Message
    throw
} finally {
    Stop-App
    $metadata.rateDecisions = @($script:rateDecisions)
    $metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultRoot 'metadata.json') -Encoding UTF8
}

Write-Output "API comparison run complete: build=$BuildLabel overlay=$Overlay result=$resultRoot"
