[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status', 'logs', 'help')]
    [string]$Action = 'start'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$RuntimeDir = Join-Path $RepoRoot '.run\dev'
$PidDir = Join-Path $RuntimeDir 'pids'
$LogDir = Join-Path $RuntimeDir 'logs'
$EnvFile = Join-Path $RepoRoot '.env'
$Services = @('fraud-gateway', 'risk-admin', 'risk-console')
$Monitoring = $false
$StatusExitCode = 0

function Show-Usage {
    @'
Usage: scripts\dev.cmd [start|stop|status|logs]

  start   Start core Docker dependencies, build, and run the two APIs and console.
  stop    Stop only the application processes recorded by this script.
  status  Show the recorded application process state and core container state.
  logs    Follow application logs under .run\dev\logs.
'@ | Write-Host
}

function Get-PidFile([string]$Name) {
    return Join-Path $PidDir "$Name.pid"
}

function Get-ServicePid([string]$Name) {
    $Path = Get-PidFile $Name
    if (-not (Test-Path $Path)) { return $null }
    $Value = (Get-Content -Raw $Path).Trim()
    if ($Value -notmatch '^\d+$') { return $null }
    return [int]$Value
}

function Test-ServiceRunning([string]$Name) {
    $ServicePid = Get-ServicePid $Name
    if ($null -eq $ServicePid) { return $false }
    return $null -ne (Get-Process -Id $ServicePid -ErrorAction SilentlyContinue)
}

function Remove-StalePid([string]$Name) {
    $Path = Get-PidFile $Name
    if ((Test-Path $Path) -and -not (Test-ServiceRunning $Name)) {
        Remove-Item $Path -Force
    }
}

function Stop-DevProcesses {
    foreach ($Name in $Services) {
        $ServicePid = Get-ServicePid $Name
        if ($null -ne $ServicePid) {
            $Process = Get-Process -Id $ServicePid -ErrorAction SilentlyContinue
            if ($null -ne $Process) {
                Write-Host "Stopping $Name (pid $ServicePid)"
                & taskkill.exe /PID $ServicePid /T 2>$null | Out-Null
                try {
                    Wait-Process -Id $ServicePid -Timeout 20 -ErrorAction Stop
                } catch {
                    & taskkill.exe /PID $ServicePid /T /F 2>$null | Out-Null
                }
            }
            Remove-Item (Get-PidFile $Name) -Force -ErrorAction SilentlyContinue
        }
    }
}

function Require-Command([string]$Name) {
    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Import-DotEnv {
    if (-not (Test-Path $EnvFile)) {
        throw "$EnvFile is missing; copy .env.example to .env and replace every placeholder"
    }
    foreach ($Line in Get-Content $EnvFile) {
        $Trimmed = $Line.Trim()
        if (-not $Trimmed -or $Trimmed.StartsWith('#')) { continue }
        if ($Trimmed -notmatch '^([^=]+)=(.*)$') { continue }
        $Name = $Matches[1].Trim()
        $Value = $Matches[2].Trim()
        if (($Value.StartsWith('"') -and $Value.EndsWith('"')) -or
            ($Value.StartsWith("'") -and $Value.EndsWith("'"))) {
            $Value = $Value.Substring(1, $Value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
    }

    # The dev profile deliberately uses H2, while Redis and Kafka use Compose.
    # Respect explicit application settings and otherwise map host-port overrides.
    if (-not [Environment]::GetEnvironmentVariable('REDIS_HOST', 'Process')) {
        [Environment]::SetEnvironmentVariable('REDIS_HOST', 'localhost', 'Process')
    }
    if (-not [Environment]::GetEnvironmentVariable('REDIS_PORT', 'Process')) {
        $RedisPort = [Environment]::GetEnvironmentVariable('REDIS_HOST_PORT', 'Process')
        if (-not $RedisPort) { $RedisPort = '6379' }
        [Environment]::SetEnvironmentVariable('REDIS_PORT', $RedisPort, 'Process')
    }
    if (-not [Environment]::GetEnvironmentVariable('KAFKA_BOOTSTRAP', 'Process')) {
        $KafkaPort = [Environment]::GetEnvironmentVariable('KAFKA_HOST_PORT', 'Process')
        if (-not $KafkaPort) { $KafkaPort = '9092' }
        [Environment]::SetEnvironmentVariable('KAFKA_BOOTSTRAP', "localhost:$KafkaPort", 'Process')
    }
}

function Invoke-Checked([scriptblock]$Command, [string]$Description) {
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE"
    }
}

function Find-RunnableJar([string]$Module) {
    $Target = Join-Path $RepoRoot "$Module\target"
    $Jars = @(Get-ChildItem -Path $Target -Filter '*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { -not $_.Name.EndsWith('.original') })
    if ($Jars.Count -ne 1) {
        throw "Expected one runnable JAR for $Module under $Target, found $($Jars.Count)"
    }
    return $Jars[0].FullName
}

function Start-LoggedProcess(
    [string]$Name,
    [string]$Executable,
    [string[]]$Arguments,
    [string]$WorkingDirectory
) {
    Remove-StalePid $Name
    if (Test-ServiceRunning $Name) {
        throw "$Name is already running; use status or stop first"
    }
    New-Item -ItemType Directory -Force -Path $PidDir, $LogDir | Out-Null
    $OutLog = Join-Path $LogDir "$Name.log"
    $ErrorLog = Join-Path $LogDir "$Name.error.log"
    $Process = Start-Process -FilePath $Executable -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory -PassThru `
        -RedirectStandardOutput $OutLog -RedirectStandardError $ErrorLog
    Set-Content -Path (Get-PidFile $Name) -Value $Process.Id
    Write-Host "Started $Name (pid $($Process.Id), logs $OutLog / $ErrorLog)"
}

function Start-DevStack {
    Require-Command 'docker'
    Require-Command 'java'
    Require-Command 'node'
    Require-Command 'npm.cmd'
    $MavenWrapper = Join-Path $RepoRoot 'mvnw.cmd'
    if (-not (Test-Path $MavenWrapper)) { throw "Maven Wrapper not found: $MavenWrapper" }
    Import-DotEnv

    foreach ($Name in $Services) {
        Remove-StalePid $Name
        if (Test-ServiceRunning $Name) {
            throw "$Name is already running; use status or stop first"
        }
    }

    Write-Host 'Starting core infrastructure...'
    Invoke-Checked {
        & docker compose --env-file $EnvFile -f (Join-Path $RepoRoot 'docker-compose.yml') `
            up -d mysql redis kafka elasticsearch kibana
    } 'Docker Compose startup'

    Write-Host 'Building backend applications with the Maven Wrapper...'
    Push-Location $RepoRoot
    try {
        Invoke-Checked {
            & $MavenWrapper -B -DskipTests -pl 'fraud-gateway,risk-admin' -am package
        } 'Maven build'
    } finally {
        Pop-Location
    }

    Write-Host 'Installing the locked frontend dependencies...'
    $ConsoleDir = Join-Path $RepoRoot 'risk-console'
    Push-Location $ConsoleDir
    try {
        Invoke-Checked { & npm.cmd ci } 'npm ci'
    } finally {
        Pop-Location
    }

    Start-LoggedProcess 'fraud-gateway' 'java' @('-jar', (Find-RunnableJar 'fraud-gateway')) $RepoRoot
    Start-LoggedProcess 'risk-admin' 'java' @('-jar', (Find-RunnableJar 'risk-admin')) $RepoRoot
    Start-LoggedProcess 'risk-console' 'npm.cmd' @('run', 'dev', '--', '--host', '0.0.0.0') $ConsoleDir

    $script:Monitoring = $true
    Write-Host ''
    Write-Host 'Development stack is running:'
    Write-Host '  Console:        http://localhost:5173'
    Write-Host '  Fraud gateway:  http://localhost:8082'
    Write-Host '  Management API: http://localhost:8083'
    Write-Host 'Press Ctrl+C to stop application processes; infrastructure containers remain running.'

    while ($true) {
        foreach ($Name in $Services) {
            if (-not (Test-ServiceRunning $Name)) {
                throw "$Name exited; stopping the remaining application processes. See $LogDir"
            }
        }
        Start-Sleep -Seconds 2
    }
}

function Show-Status {
    New-Item -ItemType Directory -Force -Path $PidDir, $LogDir | Out-Null
    $AllRunning = $true
    foreach ($Name in $Services) {
        Remove-StalePid $Name
        if (Test-ServiceRunning $Name) {
            Write-Host ("{0,-18} running (pid {1})" -f $Name, (Get-ServicePid $Name))
        } else {
            Write-Host ("{0,-18} stopped" -f $Name)
            $AllRunning = $false
        }
    }
    if ($null -ne (Get-Command docker -ErrorAction SilentlyContinue) -and (Test-Path $EnvFile)) {
        Write-Host "`nCore containers:"
        & docker compose --env-file $EnvFile -f (Join-Path $RepoRoot 'docker-compose.yml') `
            ps mysql redis kafka elasticsearch kibana
    }
    if (-not $AllRunning) { $script:StatusExitCode = 1 }
}

function Follow-Logs {
    $Logs = @(Get-ChildItem -Path $LogDir -Filter '*.log' -File -ErrorAction SilentlyContinue)
    if ($Logs.Count -eq 0) { throw "No application logs found under $LogDir" }
    Get-Content -Path $Logs.FullName -Tail 100 -Wait
}

try {
    switch ($Action) {
        'start' { Start-DevStack }
        'stop' { Stop-DevProcesses }
        'status' { Show-Status }
        'logs' { Follow-Logs }
        'help' { Show-Usage }
    }
} finally {
    if ($Monitoring) {
        Stop-DevProcesses
    }
}

exit $StatusExitCode
