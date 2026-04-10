# GOAT Tool Installation and Upgrade Guide

## Installation Steps
1. Download the GOAT package and extract it to a location.
2. Ensure you have administrator privileges for the installation

## Starting GOAT Server
1. Open Command Prompt as Administrator
2. Navigate to the `product_package/bin` directory
3. Run `start_goat_server.bat`
4. The script will:
    - Check if server is already running
    - Set up auto-start on system boot
    - Display Java version information
    - Launch the API Explorer in your browser
    - Store logs in `product_package/logs` directory

## Stopping GOAT Server
1. Open Command Prompt as Administrator
2. Navigate to the `product_package/bin` directory
3. Run `stop_goat_server.bat`
4. The script will:
    - Find and terminate the GOAT server process
    - Remove the auto-start scheduled task

## Restarting GOAT Server
1. First stop the server using `stop_goat_server.bat`
2. Then start the server using `start_goat_server.bat`

## Build Upgrade Steps
1. Stop the running GOAT server using `stop_goat_server.bat`
2. Download the latest GOAT build package.
3. Back up your existing server directory if necessary. (`product_package/ServerDirectory`)
4. Extract the new build package into the existing GOAT tool directory, overwriting old GOAT tool files.
5. Start the server using `start_goat_server.bat`

## Auto-Start Configuration
- The server is configured to start automatically on system boot
- It waits 20 seconds after system boot, plus an additional 60-second delay for settling up the environment by system services.
- Startup logs are saved to `product_package/logs/autostart.log`