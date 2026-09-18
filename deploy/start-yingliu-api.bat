@echo off
setlocal EnableExtensions
title Yingliu API - Startup

set "APP_HOME=%~dp0"
cd /d "%APP_HOME%"

set "APP_JAR=%APP_HOME%yingliu-api.jar"
set "CONFIG_FILE=%APP_HOME%config\yingliu-env.properties"
set "CONFIG_URI=%CONFIG_FILE:\=/%"
set "JAVA_EXE=%YINGLIU_JAVA%"

echo ============================================================
echo                   Yingliu API Launcher
echo ============================================================
echo.
echo [1/5] Locating the private Java 17 runtime...

if not defined JAVA_EXE set "JAVA_EXE=%APP_HOME%runtime\jdk-17\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE="
if not defined JAVA_EXE if exist "%APP_HOME%runtime" (
    for /r "%APP_HOME%runtime" %%F in (java.exe) do if not defined JAVA_EXE if exist "%%~fF" set "JAVA_EXE=%%~fF"
)

if not defined JAVA_EXE (
    echo [FAILED] Java 17 was not found under "%APP_HOME%runtime".
    echo Extract the portable Java 17 runtime into "%APP_HOME%runtime".
    pause
    exit /b 1
)
echo [OK] "%JAVA_EXE%"

echo.
echo [2/5] Checking the application package...

if not exist "%APP_JAR%" (
    echo [FAILED] Application JAR was not found:
    echo         "%APP_JAR%"
    pause
    exit /b 1
)
for %%F in ("%APP_JAR%") do echo [OK] %%~nxF  ^(%%~zF bytes^)

echo.
echo [3/5] Verifying the Java version...
"%JAVA_EXE%" -version
if errorlevel 1 (
    echo [FAILED] Java could not be started.
    pause
    exit /b 1
)
"%JAVA_EXE%" -version 2>&1 | findstr /C:"17." >nul
if errorlevel 1 (
    echo [FAILED] This application requires Java 17.
    echo The existing system JDK 8 has not been changed.
    pause
    exit /b 1
)
echo [OK] Java 17 is ready. System JAVA_HOME and PATH are unchanged.

echo.
echo [4/5] Checking the deployment configuration and port...
if not exist "%CONFIG_FILE%" (
    echo [FAILED] Configuration file was not found:
    echo          "%CONFIG_FILE%"
    echo Put yingliu-env.properties under "%APP_HOME%config" and edit it.
    pause
    exit /b 1
)

findstr /C:"CHANGE_ME" "%CONFIG_FILE%" >nul
if not errorlevel 1 (
    echo [FAILED] Configuration still contains CHANGE_ME placeholders:
    findstr /N /C:"CHANGE_ME" "%CONFIG_FILE%"
    pause
    exit /b 1
)

set "CONFIG_ERROR=0"
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
        echo [FAILED] Missing required property: %%~K
        set "CONFIG_ERROR=1"
    )
)
if "%CONFIG_ERROR%"=="1" (
    pause
    exit /b 1
)

set "SPRING_PROFILE=prod"
set "SERVER_PORT=8080"
for /f "tokens=1,* delims==" %%A in ('findstr /B /I /C:"spring.profiles.active=" "%CONFIG_FILE%"') do set "SPRING_PROFILE=%%B"
for /f "tokens=1,* delims==" %%A in ('findstr /B /I /C:"server.port=" "%CONFIG_FILE%"') do set "SERVER_PORT=%%B"
echo [OK] Loaded config\yingliu-env.properties.

if not defined YINGLIU_JAVA_XMS set "YINGLIU_JAVA_XMS=512m"
if not defined YINGLIU_JAVA_XMX set "YINGLIU_JAVA_XMX=2g"

echo       Profile : %SPRING_PROFILE%
echo       Port    : %SERVER_PORT%
echo       Heap    : %YINGLIU_JAVA_XMS% - %YINGLIU_JAVA_XMX%

powershell.exe -NoProfile -Command "if (Get-NetTCPConnection -LocalPort %SERVER_PORT% -State Listen -ErrorAction SilentlyContinue) { exit 1 }"
if errorlevel 1 (
    echo [FAILED] Port %SERVER_PORT% is already in use. The API was not started.
    pause
    exit /b 1
)
echo [OK] Port %SERVER_PORT% is available.

if /i "%~1"=="--check" (
    echo.
    echo [READY] All startup checks passed. The API was not started.
    exit /b 0
)

echo.
echo [5/5] Starting Yingliu API...
echo ============================================================
echo [RUNNING] Live application logs appear below.
echo [STOP]    Press Ctrl+C in this window to stop the API.
echo [HEALTH]  http://127.0.0.1:%SERVER_PORT%/videoshare/health
echo ============================================================
echo.
title Yingliu API - Running on port %SERVER_PORT%

"%JAVA_EXE%" -Xms%YINGLIU_JAVA_XMS% -Xmx%YINGLIU_JAVA_XMX% -jar "%APP_JAR%" "--spring.config.additional-location=file:%CONFIG_URI%"
set "APP_EXIT_CODE=%ERRORLEVEL%"

title Yingliu API - Stopped
echo.
echo ============================================================
echo [STOPPED] Yingliu API exited with code %APP_EXIT_CODE%.
echo ============================================================
pause
exit /b %APP_EXIT_CODE%
