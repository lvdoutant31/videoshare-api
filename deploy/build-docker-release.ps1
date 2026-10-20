[CmdletBinding()]
param(
    [string]$Version = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$OutputRoot,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$backend = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$admin = (Resolve-Path (Join-Path $backend '..\yingliu-b')).Path
$h5 = (Resolve-Path (Join-Path $backend '..\yingliu-c')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $backend 'output\releases'
}
if ($Version -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') {
    throw 'Version may contain only letters, digits, dots, underscores, and hyphens.'
}

function Invoke-Checked {
    param([string]$Directory, [string]$Command, [string[]]$Arguments)
    Push-Location $Directory
    try {
        Write-Host "==> $Command $($Arguments -join ' ')" -ForegroundColor Cyan
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" }
    }
    finally { Pop-Location }
}

$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$docker = (Get-Command docker -ErrorAction Stop).Source
if (-not $SkipBuild) {
    Invoke-Checked $admin $npm @('run', 'build')
    Invoke-Checked $h5 $npm @('run', 'build')
    Invoke-Checked $backend (Join-Path $backend 'mvnw.cmd') @('-B', '-pl', 'yingliu-admin', '-am', 'package', '-DskipTests')
}

$image = "videoshare-api:$Version"
if ($SkipBuild) {
    Invoke-Checked $backend $docker @('tag', 'yingliu-api:local', $image)
}
else {
    Invoke-Checked $backend $docker @('build', '--tag', $image, '--file', 'docker/app/Dockerfile.runtime', 'yingliu-admin/target')
}
Invoke-Checked $backend $docker @('image', 'inspect', '--format', '{{.Id}}', 'nginx:stable-alpine3.20-perl')

$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
$OutputRoot = (Resolve-Path -LiteralPath $OutputRoot).Path
$releaseName = "videoshare-docker-$Version"
$stage = Join-Path $OutputRoot $releaseName
$archive = Join-Path $OutputRoot "$releaseName.zip"
if (Test-Path -LiteralPath $stage) { throw "Release directory already exists: $stage" }
if (Test-Path -LiteralPath $archive) { throw "Release archive already exists: $archive" }

New-Item -ItemType Directory -Path (Join-Path $stage 'docker-images'), (Join-Path $stage 'frontend\admin'),
    (Join-Path $stage 'frontend\h5'), (Join-Path $stage 'nginx'), (Join-Path $stage 'media') -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $admin 'dist') -Force | Copy-Item -Destination (Join-Path $stage 'frontend\admin') -Recurse
Get-ChildItem -LiteralPath (Join-Path $h5 'dist') -Force | Copy-Item -Destination (Join-Path $stage 'frontend\h5') -Recurse
Copy-Item -LiteralPath (Join-Path $backend 'docker\nginx\app.conf') -Destination (Join-Path $stage 'nginx\app.conf')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docker\deploy.sh') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docker\README.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docker\.env.example') -Destination $stage
(Get-Content -Raw (Join-Path $PSScriptRoot 'docker\compose.yml')).Replace('__VERSION__', $Version) |
    Set-Content -LiteralPath (Join-Path $stage 'compose.yml') -Encoding utf8

$imageArchive = Join-Path $stage 'docker-images\videoshare-images.tar'
Invoke-Checked $backend $docker @('save', '--output', $imageArchive, $image, 'nginx:stable-alpine3.20-perl')
$imageInfo = (& $docker image inspect $image | ConvertFrom-Json)[0]
$manifest = @(
    "version=$Version",
    "builtAt=$([DateTimeOffset]::Now.ToString('o'))",
    "platform=$($imageInfo.Os)/$($imageInfo.Architecture)",
    "apiImage=$image"
) -join "`n"
[System.IO.File]::WriteAllText((Join-Path $stage 'manifest.txt'), $manifest + "`n", [System.Text.UTF8Encoding]::new($false))

Get-ChildItem -LiteralPath $stage -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($stage.Length + 1).Replace('\', '/')
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
    "$hash  $relative"
} | Set-Content -LiteralPath (Join-Path $stage 'checksums.sha256') -Encoding ASCII

Compress-Archive -LiteralPath $stage -DestinationPath $archive -CompressionLevel Fastest
$archiveInfo = Get-Item -LiteralPath $archive
Write-Host "Release directory: $stage" -ForegroundColor Green
Write-Host "Release archive:   $archive ($([math]::Round($archiveInfo.Length / 1MB, 1)) MB)" -ForegroundColor Green
