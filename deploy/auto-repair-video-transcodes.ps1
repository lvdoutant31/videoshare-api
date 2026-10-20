[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$RepairScript,
    [Parameter(Mandatory)]
    [string]$ConfigFile,
    [string]$BaseUrl = "https://www.ysd-hsdy.com.cn:11443",
    [string]$MysqlExe,
    [Parameter(Mandatory)]
    [string]$HealthUrl,
    [ValidateRange(10, 1800)]
    [int]$HealthTimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-AutoLog {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [AUTO-TRANSCODE] $Message"
}

try {
    if (-not (Test-Path -LiteralPath $RepairScript -PathType Leaf)) {
        throw "Repair script not found: $RepairScript"
    }

    Write-AutoLog "Waiting for API health: $HealthUrl"
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    $healthy = $false
    do {
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                $healthy = $true
                break
            }
        }
        catch {
            # The API is expected to refuse connections while Spring Boot is starting.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if (-not $healthy) {
        throw "API did not become healthy within $HealthTimeoutSeconds seconds: $HealthUrl"
    }

    Write-AutoLog "API is healthy. Repairing the current recommendation feed."
    $repairParameters = @{
        ConfigFile = $ConfigFile
        BaseUrl = $BaseUrl
        Apply = $true
    }
    if (-not [string]::IsNullOrWhiteSpace($MysqlExe)) {
        $repairParameters.MysqlExe = $MysqlExe
    }
    & $RepairScript @repairParameters
    if ($LASTEXITCODE -ne 0) {
        throw "Repair script exited with code $LASTEXITCODE"
    }
    Write-AutoLog "Current recommendation feed repair completed."
    exit 0
}
catch {
    Write-Error "Automatic video transcode repair failed: $($_.Exception.Message)"
    exit 1
}
