[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$ConfigFile = (Join-Path $PSScriptRoot "api\config\yingliu-env.properties"),
    [string]$BaseUrl = "https://www.ysd-hsdy.com.cn:11443",
    [long[]]$VideoId,
    [string]$MysqlExe,
    [ValidateRange(1, 300)]
    [int]$PollSeconds = 5,
    [ValidateRange(1, 1440)]
    [int]$TimeoutMinutes = 30,
    [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Read-JavaProperties {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Configuration file not found: $Path"
    }
    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $values = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($resolvedPath)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $values[$name] = $value
    }
    return $values
}

function Require-Property {
    param([hashtable]$Properties, [string]$Name)
    $value = [string]$Properties[$Name]
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing property: $Name"
    }
    return $value
}

function Resolve-MySqlExe {
    param([string]$Requested)
    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($Requested)) { $candidates.Add($Requested) }
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($null -ne $command -and -not [string]::IsNullOrWhiteSpace($command.Source)) {
        $candidates.Add($command.Source)
    }
    $candidates.Add("C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe")
    $candidates.Add("C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe")
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "mysql.exe was not found. Pass -MysqlExe with its full path."
}

function ConvertFrom-TaskStatusLine {
    param([string]$Line)
    $columns = $Line -split "`t", 5
    if ($columns.Count -lt 5) { throw "Unexpected MySQL status row: $Line" }
    [pscustomobject]@{
        VideoId = [long]$columns[0]
        Status = $columns[1]
        RetryCount = [int]$columns[2]
        ErrorCode = $columns[3]
        ErrorMessage = $columns[4]
    }
}

$resolvedConfig = (Resolve-Path -LiteralPath $ConfigFile).Path
$properties = Read-JavaProperties $resolvedConfig

Write-Step "Validating transcode configuration and FFmpeg"
if ((Require-Property $properties "yingliu.worker.video-transcode.enabled").ToLowerInvariant() -ne "true") {
    throw "yingliu.worker.video-transcode.enabled must be true"
}
$ffmpeg = Require-Property $properties "yingliu.worker.video-transcode.ffmpeg-path"
if (-not [System.IO.Path]::IsPathRooted($ffmpeg)) {
    $apiDirectory = Split-Path (Split-Path $resolvedConfig -Parent) -Parent
    $ffmpeg = Join-Path $apiDirectory $ffmpeg
}
if (-not (Test-Path -LiteralPath $ffmpeg -PathType Leaf)) {
    throw "FFmpeg not found: $ffmpeg"
}
$ffmpeg = (Resolve-Path -LiteralPath $ffmpeg).Path
& $ffmpeg -version *> $null
if ($LASTEXITCODE -ne 0) {
    throw "FFmpeg failed with exit code $LASTEXITCODE`: $ffmpeg"
}
Write-Host "[OK] FFmpeg: $ffmpeg" -ForegroundColor Green

Write-Step "Resolving MySQL connection"
$jdbc = Require-Property $properties "spring.datasource.url"
if ($jdbc -notmatch "^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)") {
    throw "Unsupported MySQL JDBC URL: $jdbc"
}
$mysqlHost = $Matches.host
$mysqlPort = if ($Matches.port) { [int]$Matches.port } else { 3306 }
$mysqlDatabase = $Matches.database
$mysqlUser = Require-Property $properties "spring.datasource.username"
$mysqlPassword = Require-Property $properties "spring.datasource.password"
$mysql = Resolve-MySqlExe $MysqlExe
Write-Host "[OK] MySQL: $mysqlUser@$mysqlHost`:$mysqlPort/$mysqlDatabase" -ForegroundColor Green

function Invoke-MySql {
    param([string]$Sql)
    $previousPassword = [Environment]::GetEnvironmentVariable("MYSQL_PWD", "Process")
    try {
        [Environment]::SetEnvironmentVariable("MYSQL_PWD", $mysqlPassword, "Process")
        $output = $Sql | & $mysql "--host=$mysqlHost" "--port=$mysqlPort" "--user=$mysqlUser" `
            "--database=$mysqlDatabase" --batch --raw --skip-column-names
        if ($LASTEXITCODE -ne 0) {
            throw "mysql.exe failed with exit code $LASTEXITCODE"
        }
        return @($output)
    }
    finally {
        [Environment]::SetEnvironmentVariable("MYSQL_PWD", $previousPassword, "Process")
    }
}

Write-Step "Resolving target videos"
$base = $BaseUrl.TrimEnd("/")
if ($null -eq $VideoId -or $VideoId.Count -eq 0) {
    $feedUri = "$base/videoshare/api/v1/app/feed/recommend?limit=20"
    $feed = Invoke-RestMethod -Uri $feedUri -Method Get -TimeoutSec 30
    $VideoId = @($feed.data.items |
        Where-Object { [string]$_.videoUrl -notmatch "/media/transcoded/720p/" } |
        ForEach-Object { [long]$_.id })
}
$VideoId = @($VideoId | Where-Object { $_ -gt 0 } | Sort-Object -Unique)
if ($VideoId.Count -eq 0) {
    Write-Host "[OK] No source-video URLs require repair." -ForegroundColor Green
    exit 0
}
$idList = $VideoId -join ","
Write-Host "Target video IDs: $idList"

$statusSql = @"
SELECT v.id, COALESCE(t.status,'MISSING'), COALESCE(t.retry_count,0),
       COALESCE(t.error_code,''), REPLACE(REPLACE(COALESCE(t.error_message,''), CHAR(13), ' '), CHAR(10), ' ')
FROM video v
LEFT JOIN video_transcode t ON t.video_id=v.id AND t.definition='720P'
WHERE v.id IN ($idList)
ORDER BY v.id;
"@

function Get-TaskStatuses {
    $lines = @(Invoke-MySql $statusSql | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
    return @($lines | ForEach-Object { ConvertFrom-TaskStatusLine ([string]$_) })
}

function Show-TaskStatuses {
    param([object[]]$Statuses)
    $Statuses | Format-Table VideoId, Status, RetryCount, ErrorCode, ErrorMessage -AutoSize | Out-Host
}

Write-Step "Reading current task status"
$before = @(Get-TaskStatuses)
Show-TaskStatuses $before
$foundIds = @($before | ForEach-Object { $_.VideoId })
$missingVideos = @($VideoId | Where-Object { $_ -notin $foundIds })
if ($missingVideos.Count -gt 0) {
    throw "Video rows were not found in the database: $($missingVideos -join ',')"
}

if (-not $Apply) {
    Write-Host "[DRY-RUN] No database rows were changed. Re-run with -Apply to repair these IDs." -ForegroundColor Yellow
    exit 0
}

if (-not $PSCmdlet.ShouldProcess("video_transcode rows for $idList", "Create or requeue 720P tasks")) {
    Write-Host "[CANCELLED] No database rows were changed." -ForegroundColor Yellow
    exit 0
}

Write-Step "Creating missing tasks and requeueing recoverable tasks"
$repairSql = @"
START TRANSACTION;
INSERT INTO video_transcode
    (id,video_id,media_id,definition,status,error_code,error_message,started_at,finished_at,retry_count,created_at,updated_at)
SELECT UUID_SHORT(),v.id,NULL,'720P','PENDING',NULL,NULL,NULL,NULL,0,NOW(3),NOW(3)
FROM video v
LEFT JOIN video_transcode t ON t.video_id=v.id AND t.definition='720P'
WHERE v.id IN ($idList) AND v.original_media_id IS NOT NULL AND v.deleted=0 AND t.id IS NULL;

UPDATE video_transcode
SET status='PENDING',media_id=NULL,error_code=NULL,error_message=NULL,
    started_at=NULL,finished_at=NULL,retry_count=retry_count+1,updated_at=NOW(3)
WHERE video_id IN ($idList) AND definition='720P'
  AND (status IN ('FAILED','CANCELLED')
       OR (status='PROCESSING' AND started_at < DATE_SUB(NOW(3), INTERVAL 15 MINUTE)));
COMMIT;
"@
Invoke-MySql $repairSql | Out-Null

Write-Step "Waiting for transcodes to complete"
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$succeeded = 0
do {
    $statuses = @(Get-TaskStatuses)
    Show-TaskStatuses $statuses
    $failed = @($statuses | Where-Object { $_.Status -eq "FAILED" })
    if ($failed.Count -gt 0) {
        $details = $failed | ForEach-Object { "$($_.VideoId):$($_.ErrorCode):$($_.ErrorMessage)" }
        throw "At least one transcode task failed: $($details -join ' | ')"
    }
    $succeeded = @($statuses | Where-Object { $_.Status -eq "SUCCEEDED" }).Count
    if ($succeeded -eq $VideoId.Count) { break }
    if ((Get-Date) -ge $deadline) { break }
    Start-Sleep -Seconds $PollSeconds
} while ($true)

if ($succeeded -ne $VideoId.Count) {
    throw "Timed out after $TimeoutMinutes minutes while waiting for transcodes"
}

Write-Step "Verifying public playback URLs"
foreach ($id in $VideoId) {
    $detailUri = "$base/videoshare/api/v1/app/videos/$id"
    $detail = Invoke-RestMethod -Uri $detailUri -Method Get -TimeoutSec 30
    $videoUrl = [string]$detail.data.videoUrl
    if ($videoUrl -notmatch "/media/transcoded/720p/") {
        throw "Video $id succeeded in the database but public API still returned: $videoUrl"
    }
    Write-Host "[OK] Video $id -> $videoUrl" -ForegroundColor Green
}

Write-Host "[COMPLETE] All selected videos now use 720P playback renditions." -ForegroundColor Green
