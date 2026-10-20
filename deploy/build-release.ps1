[CmdletBinding()]
param(
    [string]$Version = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string]$AdminRepository,
    [string]$H5Repository,
    [string]$OutputRoot,
    [string]$FfmpegDirectory,
    [string]$FfmpegPath,
    [string]$WinSWPath,
    [string]$PublicHost = "www.ysd-hsdy.com.cn",
    [string]$DeployRoot = "F:/videoshare",
    # The external gateway forwards HTTPS 11443 to this internal Nginx port.
    [int]$ListenPort = 8092,
    [string]$SslCertificate = "F:/nginx-1.23.4/ssl/www.ysd-hsdy.com.cn.pem",
    [string]$SslCertificateKey = "F:/nginx-1.23.4/ssl/www.ysd-hsdy.com.cn.key",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$backendRepository = Split-Path $PSScriptRoot -Parent
if (-not $AdminRepository) {
    $AdminRepository = Join-Path $backendRepository "..\yingliu-b"
}
if (-not $H5Repository) {
    $H5Repository = Join-Path $backendRepository "..\yingliu-c"
}
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $backendRepository "output\releases"
}
function Resolve-RequiredPath {
    param([string]$Path, [string]$Description)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Description not found: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Invoke-NativeBuild {
    param([string]$WorkingDirectory, [scriptblock]$Command, [string]$Description)
    Push-Location $WorkingDirectory
    try {
        Write-Host "==> $Description" -ForegroundColor Cyan
        & $Command
        if ($LASTEXITCODE -ne 0) {
            throw "$Description failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    $utf8NoBom = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

$backendRepository = Resolve-RequiredPath $backendRepository "Backend repository"
$AdminRepository = Resolve-RequiredPath $AdminRepository "Admin frontend repository"
$H5Repository = Resolve-RequiredPath $H5Repository "H5 frontend repository"

if (-not $SkipBuild) {
    Assert-Command "node"
    Assert-Command "npm.cmd"
    Assert-Command "java"

    $ErrorActionPreference = "Continue"
    $javaVersion = & java -version 2>&1 | Out-String
    $javaExitCode = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($javaExitCode -ne 0 -or $javaVersion -notmatch 'version "17\.') {
        throw "Java 17 is required to build the backend. Detected: $javaVersion"
    }

    Invoke-NativeBuild $AdminRepository { & npm.cmd run build } "Building admin frontend"
    Invoke-NativeBuild $H5Repository { & npm.cmd run build } "Building H5 frontend"
    Invoke-NativeBuild $backendRepository { & .\mvnw.cmd -B -pl yingliu-admin -am package -DskipTests } "Building backend"
}

$backendJar = Resolve-RequiredPath (Join-Path $backendRepository "yingliu-admin\target\yingliu-admin-0.1.0-SNAPSHOT.jar") "Backend JAR"
$adminDist = Resolve-RequiredPath (Join-Path $AdminRepository "dist") "Admin build output"
$h5Dist = Resolve-RequiredPath (Join-Path $H5Repository "dist") "H5 build output"
$configTemplate = Resolve-RequiredPath (Join-Path $PSScriptRoot "config\yingliu-env.properties.example") "Backend configuration template"
$nginxTemplate = Resolve-RequiredPath (Join-Path $PSScriptRoot "nginx\yingliu.conf.template") "Nginx template"
$winSwVersion = "v2.12.0"
$winSwSha256 = "05B82D46AD331CC16BDC00DE5C6332C1EF818DF8CEEFCD49C726553209B3A0DA"
if ([string]::IsNullOrWhiteSpace($WinSWPath)) {
    $WinSWPath = Join-Path $env:TEMP "Yingliu-WinSW-$winSwVersion-x64.exe"
    if (-not (Test-Path -LiteralPath $WinSWPath)) {
        Write-Host "==> Downloading WinSW $winSwVersion x64 from the official release" -ForegroundColor Cyan
        Invoke-WebRequest -Uri "https://github.com/winsw/winsw/releases/download/$winSwVersion/WinSW-x64.exe" -OutFile $WinSWPath
    }
}
$WinSWPath = Resolve-RequiredPath $WinSWPath "WinSW x64 service wrapper"
$actualWinSwSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $WinSWPath).Hash
if ($actualWinSwSha256 -ne $winSwSha256) {
    throw "WinSW checksum mismatch. Expected $winSwSha256; got $actualWinSwSha256"
}
$bundleFfmpeg = -not [string]::IsNullOrWhiteSpace($FfmpegDirectory)
if ($bundleFfmpeg) {
    $FfmpegDirectory = Resolve-RequiredPath $FfmpegDirectory "FFmpeg distribution directory"
    Resolve-RequiredPath (Join-Path $FfmpegDirectory "bin\ffmpeg.exe") "FFmpeg executable" | Out-Null
    $releaseFfmpegPath = "tools/ffmpeg/bin/ffmpeg.exe"
}
else {
    if ([string]::IsNullOrWhiteSpace($FfmpegPath)) {
        $releaseFfmpegPath = $DeployRoot.Replace('\', '/').TrimEnd('/') + "/ffmpeg/ffmpeg-master-latest-win64-gpl-shared/bin/ffmpeg.exe"
    }
    else {
        $releaseFfmpegPath = $FfmpegPath.Replace('\', '/')
    }
}

$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
if (-not (Test-Path -LiteralPath $OutputRoot)) {
    New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
}
$OutputRoot = (Resolve-Path -LiteralPath $OutputRoot).Path
$releaseName = "yingliu-$Version"
$stageDirectory = Join-Path $OutputRoot $releaseName
$archivePath = Join-Path $OutputRoot "$releaseName.zip"

if (-not ([System.IO.Path]::GetFullPath($stageDirectory).StartsWith($OutputRoot, [System.StringComparison]::OrdinalIgnoreCase))) {
    throw "Refusing to stage outside the release output directory: $stageDirectory"
}

if (Test-Path -LiteralPath $stageDirectory) {
    Remove-Item -LiteralPath $stageDirectory -Recurse -Force
}
if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}

$apiConfigDirectory = New-Item -ItemType Directory -Path (Join-Path $stageDirectory "api\config") -Force
$apiDirectory = $apiConfigDirectory.Parent.FullName
$frontendDirectory = New-Item -ItemType Directory -Path (Join-Path $stageDirectory "frontend") -Force
$nginxDirectory = New-Item -ItemType Directory -Path (Join-Path $stageDirectory "nginx") -Force
New-Item -ItemType Directory -Path (Join-Path $stageDirectory "media") -Force | Out-Null

Copy-Item -LiteralPath $backendJar -Destination (Join-Path $apiDirectory "yingliu-api.jar")
$apiConfig = (Get-Content -Raw -LiteralPath $configTemplate).Replace('__FFMPEG_PATH__', $releaseFfmpegPath)
Write-Utf8NoBom -Path (Join-Path $apiConfigDirectory.FullName "yingliu-env.properties.example") -Content $apiConfig
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "start-yingliu-api.bat") -Destination $apiDirectory
Copy-Item -LiteralPath $WinSWPath -Destination (Join-Path $apiDirectory "YingliuApiService.exe")
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "windows-service\YingliuApiService.xml.template") -Destination $apiDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "windows-service\LICENSE-WinSW.txt") -Destination (Join-Path $apiDirectory "LICENSE-WinSW.txt")
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "deploy-windows-service.bat") -Destination $stageDirectory
if ($bundleFfmpeg) {
    New-Item -ItemType Directory -Path (Join-Path $apiDirectory "tools") -Force | Out-Null
    Copy-Item -LiteralPath $FfmpegDirectory -Destination (Join-Path $apiDirectory "tools\ffmpeg") -Recurse
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "verify-deployment.ps1") -Destination $stageDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "repair-video-transcodes.ps1") -Destination $stageDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "auto-repair-video-transcodes.ps1") -Destination $stageDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "README.md") -Destination $stageDirectory
Copy-Item -LiteralPath $adminDist -Destination (Join-Path $frontendDirectory.FullName "admin") -Recurse
Copy-Item -LiteralPath $h5Dist -Destination (Join-Path $frontendDirectory.FullName "h5") -Recurse

$normalizedDeployRoot = $DeployRoot.Replace('\', '/').TrimEnd('/')
$nginx = Get-Content -Raw -LiteralPath $nginxTemplate
$nginx = $nginx.Replace('__LISTEN_PORT__', [string]$ListenPort)
$nginx = $nginx.Replace('__PUBLIC_HOST__', $PublicHost)
$nginx = $nginx.Replace('__DEPLOY_ROOT__', $normalizedDeployRoot)
$nginx = $nginx.Replace('__SSL_CERTIFICATE__', $SslCertificate.Replace('\', '/'))
$nginx = $nginx.Replace('__SSL_CERTIFICATE_KEY__', $SslCertificateKey.Replace('\', '/'))
Write-Utf8NoBom -Path (Join-Path $nginxDirectory.FullName "yingliu.conf") -Content $nginx

$requiredReleaseFiles = @(
    "api\yingliu-api.jar",
    "api\YingliuApiService.exe",
    "api\YingliuApiService.xml.template",
    "api\LICENSE-WinSW.txt",
    "api\config\yingliu-env.properties.example",
    "frontend\admin\index.html",
    "frontend\h5\index.html",
    "nginx\yingliu.conf",
    "README.md",
    "deploy-windows-service.bat",
    "auto-repair-video-transcodes.ps1",
    "repair-video-transcodes.ps1",
    "verify-deployment.ps1"
)
if ($bundleFfmpeg) {
    $requiredReleaseFiles += "api\tools\ffmpeg\bin\ffmpeg.exe"
}
foreach ($relativePath in $requiredReleaseFiles) {
    Resolve-RequiredPath (Join-Path $stageDirectory $relativePath) "Release file" | Out-Null
}

Get-ChildItem -LiteralPath $stageDirectory -Recurse -File | ForEach-Object {
    $relativePath = $_.FullName.Substring($stageDirectory.Length + 1).Replace('\', '/')
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
    "$hash  $relativePath"
} | Set-Content -LiteralPath (Join-Path $stageDirectory "checksums.sha256") -Encoding ASCII

Compress-Archive -LiteralPath $stageDirectory -DestinationPath $archivePath -CompressionLevel Optimal

Write-Host "Release directory: $stageDirectory" -ForegroundColor Green
Write-Host "Release archive:   $archivePath" -ForegroundColor Green
