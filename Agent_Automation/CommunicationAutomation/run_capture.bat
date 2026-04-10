@echo off
setlocal EnableDelayedExpansion
title UEMS Agent Traffic Capture
cd /d "%~dp0"

echo.
echo ============================================================
echo   UEMS Agent Traffic Capture (mTLS aware)
echo   Real server : https://10.69.73.19:8383
echo   Proxy       : https://127.0.0.1:8080 (mitmproxy reverse)
echo   Trigger     : cfgupdate.exe
echo ============================================================
echo.

:: Must run as admin (cert install)
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Please right-click and "Run as Administrator"
    pause
    exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
    echo ERROR: Run install_and_run.bat first.
    pause
    exit /b 1
)

echo Running capture automation... Press Ctrl+C to stop recording.
echo.

.venv\Scripts\python.exe main.py TestData\record_traffic.json --show-all

echo.

:: Show captured requests summary
if exist "Reports\recorded-traffic.json" (
    echo ============================================================
    echo   CAPTURED REQUESTS:
    echo ============================================================
    .venv\Scripts\python.exe -c "import json;flows=json.load(open('Reports/recorded-traffic.json'));print(f'  Total flows: {len(flows)}');[print(f'  [{i+1:02d}] {f[\"request\"][\"method\"]:6} {f[\"request\"][\"url\"]} -> HTTP {f[\"response\"][\"status\"]}') for i,f in enumerate(flows)]"
    echo.
    echo   Full dump: Reports\recorded-traffic.json
) else (
    echo   No traffic captured.
)

echo.
pause
