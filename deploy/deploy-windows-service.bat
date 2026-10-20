@echo off
setlocal EnableExtensions
title Yingliu Windows Service Deployment

set "RELEASE_ROOT=%~dp0"
for %%I in ("%RELEASE_ROOT%.") do set "RELEASE_ROOT=%%~fI"
set "APP_HOME=%RELEASE_ROOT%\api"
set "APP_JAR=%APP_HOME%\yingliu-api.jar"
set "CONFIG_FILE=%APP_HOME%\config\yingliu-env.properties"
set "SERVICE_ID=YingliuApi"
set "SERVICE_EXE=%APP_HOME%\YingliuApiService.exe"
set "SERVICE_XML=%APP_HOME%\YingliuApiService.xml"
set "SERVICE_TEMPLATE=%APP_HOME%\YingliuApiService.xml.template"
set "ACTION=%~1"
if not defined ACTION set "ACTION=deploy"

call :runAction
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" echo [FAILED] Command "%ACTION%" exited with code %RESULT%. See the error above.
rem A double-click has no arguments; keep its result visible without blocking scripted commands.
if "%~1"=="" (
    echo.
    pause
)
exit /b %RESULT%

:runAction
if /i "%ACTION%"=="help" goto help
if /i "%ACTION%"=="/?" goto help
if /i "%ACTION%"=="status" goto status
if /i "%ACTION%"=="check" goto preflight
if /i "%ACTION%"=="deploy" goto deploy
if /i "%ACTION%"=="install" goto deploy
if /i "%ACTION%"=="update" goto deploy
if /i "%ACTION%"=="start" goto start
if /i "%ACTION%"=="stop" goto stop
if /i "%ACTION%"=="restart" goto restart
if /i "%ACTION%"=="uninstall" goto uninstall
echo [FAILED] Unknown command: %ACTION%
call :help
exit /b 1

:preflight
call :preflightInternal || exit /b 1
echo [READY] Java, configuration, FFmpeg, WinSW, and service XML checks passed.
exit /b 0

:deploy
call :preflightInternal || exit /b 1
call :requireAdmin || exit /b 1
sc.exe query "%SERVICE_ID%" >nul 2>&1
if errorlevel 1 goto installService

echo [1/4] Stopping the existing %SERVICE_ID% service...
"%SERVICE_EXE%" stopwait "%SERVICE_XML%" >nul 2>&1
echo [2/4] Replacing the existing service registration...
"%SERVICE_EXE%" uninstall "%SERVICE_XML%"
if errorlevel 1 (
    echo [FAILED] Could not remove the existing service registration.
    exit /b 1
)

:installService
echo [3/4] Installing %SERVICE_ID% for automatic startup...
"%SERVICE_EXE%" install "%SERVICE_XML%"
if errorlevel 1 (
    echo [FAILED] WinSW could not install the Windows service.
    exit /b 1
)
echo [4/4] Starting the service and waiting for API health...
"%SERVICE_EXE%" start "%SERVICE_XML%"
if errorlevel 1 (
    echo [FAILED] The Windows service did not start. Check "%APP_HOME%\logs".
    exit /b 1
)
call :waitForHealth
if errorlevel 1 (
    echo [FAILED] The service started, but the API health endpoint did not become ready.
    echo Check "%APP_HOME%\logs" before retrying.
    exit /b 1
)
echo [OK] Yingliu API is running as the %SERVICE_ID% Windows service.
exit /b 0

:start
call :requireAdmin || exit /b 1
"%SERVICE_EXE%" start "%SERVICE_XML%"
if errorlevel 1 exit /b 1
call :waitForHealth
exit /b %ERRORLEVEL%

:stop
call :requireAdmin || exit /b 1
"%SERVICE_EXE%" stopwait "%SERVICE_XML%"
exit /b %ERRORLEVEL%

:restart
call :requireAdmin || exit /b 1
"%SERVICE_EXE%" restart "%SERVICE_XML%"
if errorlevel 1 exit /b 1
call :waitForHealth
exit /b %ERRORLEVEL%

:status
if not exist "%SERVICE_EXE%" (
    echo [FAILED] WinSW was not found at "%SERVICE_EXE%".
    exit /b 1
)
"%SERVICE_EXE%" status "%SERVICE_XML%"
exit /b %ERRORLEVEL%

:uninstall
call :requireAdmin || exit /b 1
if not exist "%SERVICE_EXE%" (
    echo [FAILED] WinSW was not found at "%SERVICE_EXE%".
    exit /b 1
)
"%SERVICE_EXE%" stopwait "%SERVICE_XML%" >nul 2>&1
"%SERVICE_EXE%" uninstall "%SERVICE_XML%"
exit /b %ERRORLEVEL%

:preflightInternal
call :checkFiles || exit /b 1
call :resolveJava || exit /b 1
call :checkConfig || exit /b 1
call :renderServiceConfig || exit /b 1
exit /b 0

:checkFiles
for %%F in ("%APP_JAR%" "%SERVICE_EXE%" "%SERVICE_TEMPLATE%") do (
    if not exist "%%~F" (
        echo [FAILED] Required release file is missing: "%%~F"
        exit /b 1
    )
)
if not exist "%CONFIG_FILE%" (
    echo [FAILED] Runtime configuration was not found: "%CONFIG_FILE%"
    echo Copy yingliu-env.properties.example to yingliu-env.properties and edit it first.
    exit /b 1
)
exit /b 0

:resolveJava
set "JAVA_EXE=%YINGLIU_JAVA%"
set "JAVA_EXE=%JAVA_EXE:"=%"
if defined JAVA_EXE if exist "%JAVA_EXE%" goto javaFound
set "JAVA_EXE=%APP_HOME%\runtime\jdk-17\bin\java.exe"
if exist "%JAVA_EXE%" goto javaFound
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    goto javaFound
)
for %%J in (java.exe) do if not defined JAVA_EXE if exist "%%~$PATH:J" set "JAVA_EXE=%%~$PATH:J"
if not defined JAVA_EXE (
    echo [FAILED] Java 17 was not found. Set YINGLIU_JAVA, install Java 17 under api\runtime\jdk-17, or set JAVA_HOME.
    exit /b 1
)

:javaFound
"%JAVA_EXE%" -version 2>&1 | findstr /C:"17." >nul
if errorlevel 1 (
    echo [FAILED] Java 17 is required. Resolved Java: "%JAVA_EXE%"
    exit /b 1
)
echo [OK] Java 17: "%JAVA_EXE%"
exit /b 0

:checkConfig
findstr /C:"CHANGE_ME" "%CONFIG_FILE%" >nul
if not errorlevel 1 (
    echo [FAILED] Runtime configuration still contains CHANGE_ME placeholders.
    exit /b 1
)
for %%K in (
    "spring.profiles.active"
    "server.port"
    "spring.datasource.url"
    "spring.datasource.username"
    "spring.datasource.password"
    "spring.data.redis.host"
    "spring.data.redis.port"
    "media.storage-type"
    "media.storage-root"
) do (
    findstr /B /I /C:"%%~K=" "%CONFIG_FILE%" >nul
    if errorlevel 1 (
        echo [FAILED] Runtime configuration is missing %%~K.
        exit /b 1
    )
)

set "FFMPEG_EXE="
for /f "tokens=1,* delims==" %%A in ('findstr /B /I /C:"yingliu.worker.video-transcode.ffmpeg-path=" "%CONFIG_FILE%"') do set "FFMPEG_EXE=%%B"
if not defined FFMPEG_EXE set "FFMPEG_EXE=tools\ffmpeg\bin\ffmpeg.exe"
if exist "%FFMPEG_EXE%" goto ffmpegFound
if exist "%APP_HOME%\%FFMPEG_EXE%" (
    set "FFMPEG_EXE=%APP_HOME%\%FFMPEG_EXE%"
    goto ffmpegFound
)
where.exe "%FFMPEG_EXE%" >nul 2>&1
if not errorlevel 1 goto ffmpegFound
echo [FAILED] FFmpeg could not be found: "%FFMPEG_EXE%"
exit /b 1

:ffmpegFound
powershell.exe -NoProfile -Command "try { & $env:FFMPEG_EXE -version *> $null; exit $LASTEXITCODE } catch { exit 1 }"
if errorlevel 1 (
    echo [FAILED] FFmpeg could not be started: "%FFMPEG_EXE%"
    exit /b 1
)
for /f "tokens=1,* delims==" %%A in ('findstr /B /I /C:"server.port=" "%CONFIG_FILE%"') do set "SERVER_PORT=%%B"
echo [OK] Runtime configuration and FFmpeg are ready. Port: %SERVER_PORT%
exit /b 0

:renderServiceConfig
if not defined YINGLIU_JAVA_XMS set "YINGLIU_JAVA_XMS=512m"
if not defined YINGLIU_JAVA_XMX set "YINGLIU_JAVA_XMX=2g"
set "YINGLIU_SERVICE_JAVA=%JAVA_EXE%"
set "YINGLIU_SERVICE_TEMPLATE=%SERVICE_TEMPLATE%"
set "YINGLIU_SERVICE_XML=%SERVICE_XML%"
powershell.exe -NoProfile -Command "$ErrorActionPreference='Stop'; $xml=Get-Content -Raw -LiteralPath $env:YINGLIU_SERVICE_TEMPLATE; $xml=$xml.Replace('__JAVA_EXE__',[System.Security.SecurityElement]::Escape($env:YINGLIU_SERVICE_JAVA)).Replace('__JAVA_XMS__',[System.Security.SecurityElement]::Escape($env:YINGLIU_JAVA_XMS)).Replace('__JAVA_XMX__',[System.Security.SecurityElement]::Escape($env:YINGLIU_JAVA_XMX)); [xml]$parsed=$xml; [System.IO.File]::WriteAllText($env:YINGLIU_SERVICE_XML,$xml,[System.Text.UTF8Encoding]::new($false))"
if errorlevel 1 (
    echo [FAILED] Could not generate a valid WinSW service configuration.
    exit /b 1
)
exit /b 0

:requireAdmin
net.exe session >nul 2>&1
if errorlevel 1 (
    echo [FAILED] Reopen this BAT with "Run as administrator" to manage Windows services.
    exit /b 1
)
exit /b 0

:waitForHealth
if not defined SERVER_PORT for /f "tokens=1,* delims==" %%A in ('findstr /B /I /C:"server.port=" "%CONFIG_FILE%"') do set "SERVER_PORT=%%B"
set "YINGLIU_HEALTH_URL=http://127.0.0.1:%SERVER_PORT%/videoshare/health"
for /l %%I in (1,1,60) do (
    powershell.exe -NoProfile -Command "try { $r=Invoke-WebRequest -UseBasicParsing -Uri $env:YINGLIU_HEALTH_URL -TimeoutSec 3; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) { exit 0 } } catch { } ; exit 1" >nul 2>&1
    if not errorlevel 1 exit /b 0
    timeout.exe /t 3 /nobreak >nul
)
exit /b 1

:help
echo Usage: deploy-windows-service.bat [deploy^|start^|stop^|restart^|status^|uninstall^|check]
echo.
echo deploy   Validate this release, install or update the YingliuApi service, start it, and wait for health.
echo start    Start the installed service and wait for API health.
echo stop     Stop the installed service.
echo restart  Restart the installed service and wait for API health.
echo status   Show the Windows service status.
echo uninstall Stop and remove the service registration.
echo check    Validate Java, config, FFmpeg, WinSW, and generated service XML without changing service state.
echo.
echo Run deploy, start, stop, restart, and uninstall from an elevated command prompt.
exit /b 0
