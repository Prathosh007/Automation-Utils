@echo off
setlocal EnableDelayedExpansion
title CommunicationAutomation - Auto Setup
cd /d "%~dp0"

echo.
echo ============================================
echo  CommunicationAutomation - Auto Setup
echo ============================================
echo.

:: --- Refresh PATH so py is available after install ---
set "PATH=%LOCALAPPDATA%\Programs\Python\Python312;%LOCALAPPDATA%\Programs\Python\Python312\Scripts;%PATH%"
set "PATH=%APPDATA%\Python\Python312\Scripts;%PATH%"

:: --- Check if Python already installed ---
echo [1/4] Checking Python...
py --version 2>nul
if %errorlevel% neq 0 (
    echo   Python not found. Downloading...
    set PYTHON_URL=https://www.python.org/ftp/python/3.12.9/python-3.12.9-amd64.exe
    set PYTHON_INSTALLER=%TEMP%\python-3.12.9-amd64.exe
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%PYTHON_URL%' -OutFile '%PYTHON_INSTALLER%' -UseBasicParsing"
    "%PYTHON_INSTALLER%" /quiet InstallAllUsers=0 PrependPath=1 Include_pip=1 Include_launcher=1
    echo   Python installed.
) else (
    echo   Python already installed.
)

:: --- Create virtual environment ---
echo.
echo [2/4] Creating virtual environment...
if exist ".venv" (
    echo   .venv already exists, skipping.
) else (
    py -m venv .venv
    echo   Created .venv
)

:: --- Upgrade pip first ---
echo.
echo [3/4] Installing dependencies...
echo   Upgrading pip...
.venv\Scripts\python.exe -m pip install --upgrade pip

echo   Installing mitmproxy (this takes 1-2 minutes)...
echo   (Showing full output to help diagnose any errors)
echo.
.venv\Scripts\pip.exe install mitmproxy --verbose 2>&1
set PIP_EXIT=%errorlevel%

if %PIP_EXIT% neq 0 (
    echo.
    echo ============================================
    echo  pip install failed. Trying fallback...
    echo ============================================
    echo   Trying: pip install mitmproxy --only-binary :all:
    .venv\Scripts\pip.exe install mitmproxy --only-binary :all:
    set PIP_EXIT=%errorlevel%
)

if %PIP_EXIT% neq 0 (
    echo.
    echo ============================================
    echo  INSTALL FAILED - please share the output above
    echo ============================================
    pause
    exit /b 1
)

echo.
echo   mitmproxy installed successfully.

:: --- Run demo ---
echo.
echo [4/4] Running smoke test demo...
echo ============================================
.venv\Scripts\python.exe main.py TestData\smoke_test.json --show-all
echo ============================================

echo.
echo Done! Check Logs\ and Reports\ folders for output.
echo.
pause
