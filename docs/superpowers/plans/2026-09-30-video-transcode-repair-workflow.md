# Video Transcode Repair Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one PowerShell command that validates production FFmpeg/configuration, safely repairs missing or retryable 720P transcode tasks for selected videos, waits for completion, and proves the public API now serves `/media/transcoded/720p/` URLs.

**Architecture:** A release-packaged PowerShell script reads the existing external Spring properties file, discovers the MySQL 8 client, and fetches video IDs from the public feed unless IDs are supplied explicitly. It is read-only by default; `-Apply` runs one ID-scoped transaction that inserts missing tasks with MySQL `UUID_SHORT()`, resets only failed/cancelled/stale tasks, then polls both the database and public video-detail endpoint until each video uses the successful rendition.

**Tech Stack:** Windows PowerShell 5.1+, MySQL 8 CLI, Spring Boot external properties, FFmpeg, existing public app API.

## Global Constraints

- Default mode is dry-run; database writes require an explicit `-Apply` switch.
- SQL mutations must be limited to validated positive `Int64` video IDs from `-VideoId` or the current public recommendation feed.
- Never print or pass the MySQL password on the command line; temporarily use `MYSQL_PWD` and restore its prior value in `finally`.
- Do not overwrite `yingliu-env.properties`, stop Java, restart services, or modify Nginx.
- Use definition `720P`, matching `VideoTranscodeService.DEFINITION`.
- Insert missing task IDs with MySQL `UUID_SHORT()`; do not synthesize Snowflake IDs in PowerShell.
- Reset only `FAILED`, `CANCELLED`, or `PROCESSING` tasks older than `processing-timeout`; do not reset valid `SUCCEEDED` tasks.
- Verification succeeds only when every target task is `SUCCEEDED` and `/api/v1/app/videos/{id}` returns a URL containing `/media/transcoded/720p/`.

---

### Task 1: Add the safe repair script

**Files:**
- Create: `deploy/repair-video-transcodes.ps1`

**Interfaces:**
- Consumes: `api/config/yingliu-env.properties`, MySQL `video`, `video_transcode`, and `media_resource` tables, `${BaseUrl}/videoshare/api/v1/app/feed/recommend`, and `${BaseUrl}/videoshare/api/v1/app/videos/{id}`.
- Produces: exit code `0` only for a successful dry-run or fully verified repair; non-zero for invalid configuration, FFmpeg failure, database failure, failed transcode, timeout, or public API fallback.

- [ ] **Step 1: Define parameters and strict execution defaults**

```powershell
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$ConfigFile = (Join-Path $PSScriptRoot 'api\config\yingliu-env.properties'),
    [string]$BaseUrl = 'https://www.ysd-hsdy.com.cn:11443',
    [long[]]$VideoId,
    [string]$MysqlExe,
    [int]$PollSeconds = 5,
    [int]$TimeoutMinutes = 30,
    [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
```

- [ ] **Step 2: Parse Java properties without rewriting the production file**

```powershell
function Read-JavaProperties {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Configuration file not found: $Path"
    }
    $values = @{}
    foreach ($line in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path))) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $values[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }
    return $values
}

function Require-Property {
    param([hashtable]$Properties, [string]$Name)
    $value = [string]$Properties[$Name]
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Missing property: $Name" }
    return $value
}
```

- [ ] **Step 3: Validate enabled state and the exact FFmpeg executable**

```powershell
$properties = Read-JavaProperties $ConfigFile
if ((Require-Property $properties 'yingliu.worker.video-transcode.enabled') -ne 'true') {
    throw 'yingliu.worker.video-transcode.enabled must be true'
}
$ffmpeg = Require-Property $properties 'yingliu.worker.video-transcode.ffmpeg-path'
if (-not [System.IO.Path]::IsPathRooted($ffmpeg)) {
    $ffmpeg = Join-Path (Split-Path (Split-Path (Resolve-Path $ConfigFile) -Parent) -Parent) $ffmpeg
}
if (-not (Test-Path -LiteralPath $ffmpeg -PathType Leaf)) { throw "FFmpeg not found: $ffmpeg" }
& $ffmpeg -version *> $null
if ($LASTEXITCODE -ne 0) { throw "FFmpeg failed with exit code $LASTEXITCODE: $ffmpeg" }
```

- [ ] **Step 4: Resolve MySQL connectivity from the JDBC URL and client installation**

```powershell
function Resolve-MySqlExe {
    param([string]$Requested)
    $candidates = @(
        $Requested,
        (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source,
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    ) | Where-Object { $_ }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return (Resolve-Path $candidate).Path }
    }
    throw 'mysql.exe was not found; pass -MysqlExe with its full path'
}

$jdbc = Require-Property $properties 'spring.datasource.url'
if ($jdbc -notmatch '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)') {
    throw "Unsupported MySQL JDBC URL: $jdbc"
}
$mysqlHost = $Matches.host
$mysqlPort = if ($Matches.port) { [int]$Matches.port } else { 3306 }
$mysqlDatabase = $Matches.database
$mysqlUser = Require-Property $properties 'spring.datasource.username'
$mysqlPassword = Require-Property $properties 'spring.datasource.password'
$mysql = Resolve-MySqlExe $MysqlExe
```

- [ ] **Step 5: Add a password-safe MySQL query helper**

```powershell
function Invoke-MySql {
    param([string]$Sql)
    $previousPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $mysqlPassword, 'Process')
        $output = $Sql | & $mysql --host=$mysqlHost --port=$mysqlPort --user=$mysqlUser `
            --database=$mysqlDatabase --batch --raw --skip-column-names
        if ($LASTEXITCODE -ne 0) { throw "mysql.exe failed with exit code $LASTEXITCODE" }
        return @($output)
    }
    finally {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousPassword, 'Process')
    }
}
```

- [ ] **Step 6: Resolve and validate the target video IDs**

```powershell
if (-not $VideoId -or $VideoId.Count -eq 0) {
    $feedUri = "$($BaseUrl.TrimEnd('/'))/videoshare/api/v1/app/feed/recommend?limit=20"
    $feed = Invoke-RestMethod -Uri $feedUri -Method Get -TimeoutSec 30
    $VideoId = @($feed.data.items |
        Where-Object { $_.videoUrl -notmatch '/media/transcoded/720p/' } |
        ForEach-Object { [long]$_.id })
}
$VideoId = @($VideoId | Where-Object { $_ -gt 0 } | Sort-Object -Unique)
if ($VideoId.Count -eq 0) {
    Write-Host '[OK] No source-video URLs require repair.' -ForegroundColor Green
    exit 0
}
$idList = $VideoId -join ','
```

- [ ] **Step 7: Print a dry-run status table and stop unless `-Apply` is present**

```powershell
$statusSql = @"
SELECT v.id, COALESCE(t.status,'MISSING'), COALESCE(t.retry_count,0),
       COALESCE(t.error_code,''), COALESCE(t.error_message,'')
FROM video v
LEFT JOIN video_transcode t ON t.video_id=v.id AND t.definition='720P'
WHERE v.id IN ($idList)
ORDER BY v.id;
"@
$before = Invoke-MySql $statusSql
$before | ForEach-Object { Write-Host $_ }
if (-not $Apply) {
    Write-Host '[DRY-RUN] No database rows were changed. Re-run with -Apply to repair these IDs.' -ForegroundColor Yellow
    exit 0
}
```

- [ ] **Step 8: Insert missing tasks and reset only recoverable rows in one scoped transaction**

```powershell
$repairSql = @"
START TRANSACTION;
INSERT INTO video_transcode
    (id,video_id,media_id,definition,status,error_code,error_message,started_at,finished_at,retry_count,created_at,updated_at)
SELECT UUID_SHORT(),v.id,NULL,'720P','PENDING',NULL,NULL,NULL,NULL,0,NOW(3),NOW(3)
FROM video v
LEFT JOIN video_transcode t ON t.video_id=v.id AND t.definition='720P'
WHERE v.id IN ($idList) AND v.original_media_id IS NOT NULL AND t.id IS NULL;

UPDATE video_transcode
SET status='PENDING',media_id=NULL,error_code=NULL,error_message=NULL,
    started_at=NULL,finished_at=NULL,retry_count=retry_count+1,updated_at=NOW(3)
WHERE video_id IN ($idList) AND definition='720P'
  AND (status IN ('FAILED','CANCELLED')
       OR (status='PROCESSING' AND started_at < DATE_SUB(NOW(3), INTERVAL 15 MINUTE)));
COMMIT;
"@
Invoke-MySql $repairSql | Out-Null
```

- [ ] **Step 9: Poll task status and fail immediately on a terminal transcode error**

```powershell
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
do {
    Start-Sleep -Seconds $PollSeconds
    $rows = Invoke-MySql $statusSql
    $rows | ForEach-Object { Write-Host $_ }
    if ($rows -match "`tFAILED`t") { throw 'At least one transcode task failed; see error columns above' }
    $succeeded = @($rows | Where-Object { $_ -match "`tSUCCEEDED`t" }).Count
    if ($succeeded -eq $VideoId.Count) { break }
} while ((Get-Date) -lt $deadline)
if ($succeeded -ne $VideoId.Count) { throw "Timed out after $TimeoutMinutes minutes" }
```

- [ ] **Step 10: Verify every public video detail uses the generated rendition**

```powershell
foreach ($id in $VideoId) {
    $detailUri = "$($BaseUrl.TrimEnd('/'))/videoshare/api/v1/app/videos/$id"
    $detail = Invoke-RestMethod -Uri $detailUri -Method Get -TimeoutSec 30
    $videoUrl = [string]$detail.data.videoUrl
    if ($videoUrl -notmatch '/media/transcoded/720p/') {
        throw "Video $id succeeded in the database but public API still returned: $videoUrl"
    }
    Write-Host "[OK] Video $id -> $videoUrl" -ForegroundColor Green
}
Write-Host '[COMPLETE] All selected videos now use 720P playback renditions.' -ForegroundColor Green
```

- [ ] **Step 11: Parse-check the script**

Run:

```powershell
$tokens=$null; $errors=$null
[System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path '.\deploy\repair-video-transcodes.ps1'),
  [ref]$tokens,
  [ref]$errors
) | Out-Null
if ($errors.Count) { $errors; exit 1 }
```

Expected: exit code `0` and no parser diagnostics.

---

### Task 2: Package and document the workflow

**Files:**
- Modify: `deploy/build-release.ps1`
- Modify: `deploy/README.md`

**Interfaces:**
- Consumes: `deploy/repair-video-transcodes.ps1` from Task 1.
- Produces: `repair-video-transcodes.ps1` at the release root and documented dry-run/apply commands.

- [ ] **Step 1: Copy the script into every release and require it in package validation**

Add beside `verify-deployment.ps1`:

```powershell
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'repair-video-transcodes.ps1') -Destination $stageDirectory
```

Add to `$requiredReleaseFiles`:

```powershell
'repair-video-transcodes.ps1'
```

- [ ] **Step 2: Document dry-run and apply commands**

Add this exact section to `deploy/README.md`:

````markdown
## 修复并验证视频转码

先预检，不修改数据库：

```powershell
.\repair-video-transcodes.ps1 -BaseUrl https://www.ysd-hsdy.com.cn:11443
```

确认列表后执行修复并等待验证：

```powershell
.\repair-video-transcodes.ps1 -BaseUrl https://www.ysd-hsdy.com.cn:11443 -Apply
```

也可以只处理指定视频：

```powershell
.\repair-video-transcodes.ps1 -VideoId 2104853417371922433 -Apply
```
````

- [ ] **Step 3: Build a release without rebuilding unchanged application artifacts**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\build-release.ps1 `
  -Version 20260930-transcode-workflow `
  -FfmpegPath F:/videoshare/ffmpeg/ffmpeg-master-latest-win64-gpl-shared/bin/ffmpeg.exe `
  -SkipBuild
```

Expected: a release directory and ZIP under `output/releases/`.

- [ ] **Step 4: Verify package contents and hashes**

Run:

```powershell
$release = Resolve-Path '.\output\releases\yingliu-20260930-transcode-workflow'
Test-Path (Join-Path $release 'repair-video-transcodes.ps1')
Get-FileHash '.\output\releases\yingliu-20260930-transcode-workflow.zip' -Algorithm SHA256
```

Expected: `True`, followed by a SHA-256 hash.

---

## Self-Review

- Spec coverage: dry-run, FFmpeg/config checks, target discovery, missing-task backfill, retry, polling, public URL verification, packaging, and usage documentation are covered.
- Placeholder scan: no `TODO`, `TBD`, unspecified error handling, or deferred implementation steps remain.
- Type consistency: `VideoId` is `long[]`; database IDs are validated positive `Int64`; definition and URL verification consistently use `720P` and `/media/transcoded/720p/`.
- Safety check: no process stop/restart, no configuration rewrite, no unscoped table mutation, and no command-line password exposure.
