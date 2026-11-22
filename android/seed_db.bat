@echo off
setlocal

set "PKG=com.gestorfinances.app.debug"
set "ACTIVITY=com.gestorfinances.app.MainActivity"
set "DB_NAME=gestor-finances.db"
set "REPO_ROOT=%~dp0.."
set "DEMO_DB=%TEMP%\summa-portfolio-demo.db"
set "DEVICE_DB=/data/local/tmp/summa-portfolio-demo.db"

if defined ANDROID_HOME set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo Android Debug Bridge was not found.
    echo Set ANDROID_HOME or ANDROID_SDK_ROOT to your Android SDK directory.
    exit /b 1
)

where python >nul 2>nul
if not errorlevel 1 (
    set "PYTHON=python"
) else (
    where py >nul 2>nul
    if errorlevel 1 (
        echo Python 3 was not found on PATH.
        exit /b 1
    )
    set "PYTHON=py -3"
)

echo.
echo WARNING: this replaces all data in %PKG% on the connected device.
echo The release package com.gestorfinances.app is never targeted by this script.
set /p "CONFIRM=Type DEBUG to load the synthetic portfolio dataset: "
if /I not "%CONFIRM%"=="DEBUG" (
    echo Cancelled without changing app data.
    exit /b 1
)

%PYTHON% "%REPO_ROOT%\tools\create_portfolio_demo_db.py" "%DEMO_DB%"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

"%ADB%" shell am force-stop %PKG%
"%ADB%" shell pm clear %PKG%
if %ERRORLEVEL% neq 0 goto :failed

rem Launch once so Android creates the private application and database directories.
"%ADB%" shell am start -n %PKG%/%ACTIVITY%
if %ERRORLEVEL% neq 0 goto :failed
timeout /t 3 /nobreak >nul
"%ADB%" shell am force-stop %PKG%

"%ADB%" push "%DEMO_DB%" %DEVICE_DB%
if %ERRORLEVEL% neq 0 goto :failed
"%ADB%" shell chmod 644 %DEVICE_DB%
"%ADB%" shell run-as %PKG% rm -f databases/%DB_NAME%-wal databases/%DB_NAME%-shm databases/%DB_NAME%-journal
"%ADB%" shell run-as %PKG% cp %DEVICE_DB% databases/%DB_NAME%
if %ERRORLEVEL% neq 0 goto :failed

"%ADB%" shell rm -f %DEVICE_DB%
del /q "%DEMO_DB%" >nul 2>nul
"%ADB%" shell am start -n %PKG%/%ACTIVITY%

echo Synthetic portfolio data loaded into the debug app only.
exit /b 0

:failed
"%ADB%" shell rm -f %DEVICE_DB% >nul 2>nul
del /q "%DEMO_DB%" >nul 2>nul
echo Failed to load the synthetic debug database.
exit /b 1
