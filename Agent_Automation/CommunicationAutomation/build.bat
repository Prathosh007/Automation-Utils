@echo off
setlocal
title CommunicationAutomation - Build EXE
cd /d "%~dp0"

echo.
echo ============================================
echo  CommunicationAutomation - Build EXE
echo ============================================
echo.

:: Check venv exists
if not exist ".venv\Scripts\python.exe" (
    echo ERROR: Virtual environment not found.
    echo Run install_and_run.bat first to set up the environment.
    pause
    exit /b 1
)

:: Install PyInstaller into venv
echo [1/3] Installing PyInstaller...
.venv\Scripts\pip.exe install pyinstaller==6.11.1 --quiet
if %errorlevel% neq 0 (
    echo ERROR: Failed to install PyInstaller.
    pause
    exit /b 1
)
echo   PyInstaller ready.

:: Clean previous build
echo.
echo [2/3] Cleaning previous build...
if exist "build" rmdir /s /q build
if exist "dist\CommunicationAutomation.exe" del /q "dist\CommunicationAutomation.exe"
echo   Clean done.

:: Build EXE
echo.
echo [3/3] Building single EXE (this takes 1-2 minutes)...
echo.
.venv\Scripts\pyinstaller.exe CommunicationAutomation.spec --noconfirm

if %errorlevel% neq 0 (
    echo.
    echo ============================================
    echo  BUILD FAILED - check output above
    echo ============================================
    pause
    exit /b 1
)

echo.
echo ============================================
echo  BUILD SUCCESSFUL
echo ============================================
echo.
echo  EXE location:
echo  dist\CommunicationAutomation.exe
echo.
echo  Test the EXE:
echo  dist\CommunicationAutomation.exe TestData\smoke_test.json --show-all
echo.
pause
