[CmdletBinding()]
param(
    [string]$BaseUrl = "https://www.ysd-hsdy.com.cn",
    [int]$TimeoutSeconds = 20
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd('/')
$checks = @(
    @{ Name = "API health"; Url = "$base/videoshare/health" },
    @{ Name = "H5 frontend"; Url = "$base/videoshare/h5/" },
    @{ Name = "Admin frontend"; Url = "$base/videoshare/admin/" }
)

$failed = $false
foreach ($check in $checks) {
    try {
        $response = Invoke-WebRequest -Uri $check.Url -UseBasicParsing -TimeoutSec $TimeoutSeconds
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
            throw "HTTP $($response.StatusCode)"
        }
        Write-Host "[OK] $($check.Name): $($response.StatusCode) $($check.Url)" -ForegroundColor Green
    }
    catch {
        $failed = $true
        Write-Host "[FAILED] $($check.Name): $($check.Url) - $($_.Exception.Message)" -ForegroundColor Red
    }
}

if ($failed) {
    exit 1
}

Write-Host "All deployment checks passed." -ForegroundColor Green
