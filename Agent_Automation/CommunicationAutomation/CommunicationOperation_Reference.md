# Communication Automation Framework - Complete Guide
## Overview

This document provides a comprehensive guide for automating UEMS Agent HTTPS traffic capture, validation, and failure simulation using JSON-based commands. The framework supports proxy management, traffic interception, request/response validation, and data interruption operations.

---

## Table of Contents

- [1. Supported Actions](#1-supported-actions)
- [2. JSON Command Structure](#2-json-command-structure)
- [3. Proxy Modes](#3-proxy-modes)
- [4. Validation Types](#4-validation-types)
- [5. Traffic Flow](#5-traffic-flow)
- [6. Quick Examples](#6-quick-examples)
- [7. Complete Test Example](#7-complete-test-example)
- [8. Command-Line Usage](#8-command-line-usage)
- [9. Output Format](#9-output-format)
- [10. Common Properties](#10-common-properties)
- [11. Action Reference — Detailed](#11-action-reference--detailed)
- [12. Best Practices](#12-best-practices)
- [13. Error Handling](#13-error-handling)
- [14. Common Scenarios — How-To Guide](#14-common-scenarios--how-to-guide)

---

## 1. Supported Actions
> All available communication actions you can perform, such as start/stop proxy, validate traffic, modify responses, and manage services.

| # | Action | Category | Description | Example |
|---|--------|----------|-------------|---------|
| 1.1 | `start_proxy` | Proxy | Start mitmproxy as background process | Start reverse proxy on 127.0.0.1:8080 |
| 1.2 | `stop_proxy` | Proxy | Stop the running proxy | Stop and clean up proxy process |
| 1.3 | `install_cert` | Setup | Install or remove mitmproxy CA cert | `cert_action: "install"` or `"remove"` |
| 1.4 | `remove_certificate` | Setup | Remove mitmproxy CA cert | Remove CA from Windows trust store |
| 1.5 | `load_client_cert` | Setup | Load agent mTLS certs from vault | Load PEM/DER certs for mTLS |
| 1.6 | `configure_proxy` | Setup | Enable/disable Windows system proxy | Set system-level proxy via netsh |
| 1.7 | `traffic_lock` | Traffic | Lock all agent traffic through proxy | Hosts file + port proxy + per-exe firewall + DNS flush |
| 1.8 | `traffic_unlock` | Traffic | Restore direct traffic flow | Undo everything `traffic_lock` did |
| 1.9 | `run_command` | Utility | Run an arbitrary shell command | Trigger cfgupdate.exe |
| 1.10 | `wait_for_request` | Wait | Block until matching request arrives | Wait for agentSlot request |
| 1.11 | `wait_for_interrupt` | Wait | Block until Ctrl+C (recording mode) | Record traffic until user stops |
| 1.12 | `load_traffic` | Capture | Load traffic from JSON file | Load captured traffic for offline validation |
| 1.13 | `capture_traffic` | Capture | Dump traffic to JSON file | Save traffic to Reports/ folder |
| 1.14 | `clear_captures` | Capture | Reset traffic buffer | Clear all captured flows |
| 1.15 | `switch_capture_file` | Capture | Switch live capture to a new file | Separate traffic per test phase |
| 1.16 | `validate_request` | Validation | Assert request URL, headers, body | Verify GET /agentSlot with Auth header |
| 1.17 | `validate_response` | Validation | Assert response status, headers, body | Verify 200 OK with XML Content-Type |
| 1.18 | `assert_request_count` | Validation | Verify captured request count | At least 3 requests captured |
| 1.19 | `assert_request_order` | Validation | Verify request sequence ordering | agentSlot before meta-data.xml |
| 1.20 | `modify_response` | Simulation | Inject/alter responses | Return 500 on meta-data.xml |
| 1.21 | `block_request` | Simulation | Block or delay requests | Block agentSlot, delay 5 seconds |

---

## 2. JSON Command Structure
> The standard JSON format used to define any communication operation with its required and optional fields.

Base structure for all communication operations:

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "action_name",
    "description": "Human-readable step description",
    "continueOnFailure": false
  }
}
```

**Key Fields:**
- **operation_type**: Must be `"communication_operation"` for all communication commands
- **parameters**: Object containing all action-specific fields
- **action**: The operation to perform (from Supported Actions list)
- **description**: Optional human-readable description of the step
- **continueOnFailure**: If `true`, engine continues to next step on failure (default: `false`)

**Note:** JSON keys support both `camelCase` and `snake_case`. For example, `listenPort` and `listen_port` are both accepted.

---

## 3. Proxy Modes
> The different proxy modes supported for intercepting agent traffic.

The `start_proxy` action supports multiple proxy modes depending on how traffic should be intercepted.

| # | Mode | Description | Usage |
|---|------|-------------|-------|
| 3.1 | `""` (empty/regular) | Regular forward proxy | Default — agent must be configured to use proxy |
| 3.2 | `reverse:https://host:port` | Reverse proxy to upstream server | Forward all traffic to a specific backend |
| 3.3 | `upstream:http://proxy:port` | Upstream proxy chaining | Chain through another proxy |

**Example — Reverse Proxy:**
```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "start_proxy",
    "listenPort": 8080,
    "mode": "reverse:https://server.example.com:443"
  }
}
```

---

## 4. Validation Types
> The different validation modes you can use when checking request/response values.

| # | Type | Behaviour | Example |
|---|------|-----------|---------|
| 4.1 | `contains` | Value contains expected string (default) | Body contains "ResourceId" |
| 4.2 | `exact` | Exact match | Header equals "application/xml" |
| 4.3 | `startswith` | Value starts with expected | Authorization starts with "Basic " |
| 4.4 | `endswith` | Value ends with expected | URL ends with ".xml" |
| 4.5 | `regex` | Regex pattern match | URL matches `agent.*Slot` |
| 4.6 | `notempty` | Value exists and is not empty | Header is present |
| 4.7 | `isempty` | Value is empty or missing | Header is absent |

---

## 5. Traffic Flow
> How the proxy captures and provides traffic data for validation.

```
UEMS Agent  ─── port 8383 ──→  [Per-exe firewall blocks agent .exe files → server_ip]
     │
     ├── Hosts file: <hostname> → 127.0.0.1
     └── Port proxy: 127.0.0.1:8383 → 127.0.0.1:8080
                                           │
                                    mitmproxy (reverse mode)
                                           │
                                    Real Server (HTTPS)
```

### Traffic Lock — Pipeline (5 steps)

The `traffic_lock` action performs all OS-level redirections:

| # | Step | Mechanism | Purpose |
|---|------|-----------|---------|
| 5.0 | Enable firewall | `netsh advfirewall set allprofiles state on` | Ensure Windows Firewall is active |
| 5.1 | Hosts file | `127.0.0.1 <hostname>` appended | DNS resolution returns localhost |
| 5.2 | Port proxy | `netsh interface portproxy` 8383→8080 | Agent's port redirects to proxy port |
| 5.3 | Per-exe block | `BlockUEMS_<exe>` — one rule per .exe in agent folder | Blocks agent exes from reaching server IP directly |
| 5.4 | DNS flush | `ipconfig /flushdns` | Clear stale DNS cache |
| 5.5 | Agent restart | `net stop` / `net start` agent services | Force re-resolve DNS through hosts file |

**Why this works:** The proxy executable is NOT in the agent folder, so per-exe firewall rules don't block it. The proxy can reach the server IP directly while agent executables cannot.

**All parameters are explicit** — hostname, server IP, ports, and the agent installation folder are provided in the JSON command. No registry auto-discovery.

---

## 6. Quick Examples
> Ready-to-use JSON samples for common communication tasks like starting proxy, validating traffic, and modifying responses.

### 6.1 Start Proxy

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "start_proxy",
    "listenPort": 8080,
    "liveCaptureFile": "Reports/live-capture.json",
    "description": "Start mitmproxy on port 8080"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"start_proxy"` |
| `listenPort` | Mandatory | Port number for the proxy to listen on (must be > 0) |
| `liveCaptureFile` | Optional | Path to live traffic output file (default: `Reports/live-capture.json`) |
| `description` | Optional | Human-readable step description |

### 6.2 Start Reverse Proxy with SSL and Auto Cert Loading

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "start_proxy",
    "listenHost": "127.0.0.1",
    "listenPort": 8080,
    "mode": "reverse:https://server.example.com:443",
    "sslInsecure": true,
    "autoLoadCert": true,
    "liveCaptureFile": "Reports/live-capture.json",
    "description": "Start reverse proxy with auto cert loading"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"start_proxy"` |
| `listenHost` | Optional | IP address to listen on (default: `127.0.0.1`) |
| `listenPort` | Mandatory | Port number for the proxy to listen on |
| `mode` | Optional | Proxy mode — `"reverse:https://host:port"` forwards to upstream |
| `sslInsecure` | Optional | Skip upstream SSL certificate verification |
| `autoLoadCert` | Optional | Auto-load agent client cert from vault on CSR detection |
| `liveCaptureFile` | Optional | Path to live traffic output file |
| `description` | Optional | Human-readable step description |

### 6.3 Stop Proxy

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "stop_proxy",
    "description": "Stop the proxy and clean up"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"stop_proxy"` |
| `description` | Optional | Human-readable step description |

### 6.4 Install Certificate

```json
{
  "action": "install_cert",
  "cert_action": "install",
  "description": "Install mitmproxy CA certificate"
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | `"install_cert"` |
| `cert_action` | **Mandatory** | `"install"` or `"remove"` |
| `cert_path` | Optional | Path to certificate file (auto-detected if empty) |
| `description` | Optional | Human-readable step description |

### 6.5 Remove Certificate

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "remove_certificate",
    "description": "Remove mitmproxy CA certificate"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"remove_certificate"` |
| `description` | Optional | Human-readable step description |

### 6.6 Wait for a Specific Request

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "wait_for_request",
    "urlPattern": "agentSlot",
    "timeout": 120,
    "captureFile": "Reports/live-capture.json",
    "description": "Wait for agentSlot request (file-polling mode)"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"wait_for_request"` |
| `urlPattern` | Mandatory | Regex to match request URL |
| `timeout` | Optional | Max wait time in seconds (default: 60) |
| `captureFile` | Optional | Live capture file path (enables file-polling mode) |
| `description` | Optional | Human-readable step description |

### 6.7 Load Traffic from File

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "load_traffic",
    "captureFile": "Reports/recorded-traffic.json",
    "description": "Load previously captured traffic for validation"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"load_traffic"` |
| `captureFile` | Mandatory | Path to the JSON traffic file to load |
| `description` | Optional | Human-readable step description |

### 6.8 Validate Request — Method Check

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "validate_request",
    "urlPattern": "agentSlot",
    "method": "GET",
    "captureFile": "Reports/live-capture.json",
    "description": "Verify agentSlot uses GET method"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"validate_request"` |
| `urlPattern` | Mandatory | Regex to match the request URL |
| `method` | Optional | Expected HTTP method (GET, POST) |
| `captureFile` | Optional | Path to capture file (auto-loads traffic if not already loaded) |
| `description` | Optional | Human-readable step description |

### 6.9 Validate Request — Header Check

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "validate_request",
    "urlPattern": "agentSlot",
    "headerName": "Authorization",
    "expectedValue": "Basic ",
    "validationType": "startswith",
    "captureFile": "Reports/live-capture.json",
    "description": "Verify agentSlot has Basic Authorization header"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"validate_request"` |
| `urlPattern` | Mandatory | Regex to match the request URL |
| `headerName` | Optional | Request header name to check |
| `expectedValue` | Optional | Expected value for header comparison |
| `validationType` | Optional | Validation mode — `"startswith"` checks prefix match |
| `captureFile` | Optional | Path to capture file (auto-loads traffic if not already loaded) |
| `description` | Optional | Human-readable step description |

### 6.10 Validate Request — Body Check

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "validate_request",
    "urlPattern": "endpointdlp",
    "bodyPattern": "ResourceId",
    "validationType": "contains",
    "captureFile": "Reports/live-capture.json",
    "description": "Verify endpointdlp request body contains ResourceId"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"validate_request"` |
| `urlPattern` | Mandatory | Regex to match the request URL |
| `bodyPattern` | Optional | String pattern to search in request body |
| `validationType` | Optional | Validation mode — `"contains"` checks substring match |
| `captureFile` | Optional | Path to capture file (auto-loads traffic if not already loaded) |
| `description` | Optional | Human-readable step description |

### 6.11 Validate Response — Status Code

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "validate_response",
    "urlPattern": "agentSlot",
    "expectedStatus": 200,
    "captureFile": "Reports/live-capture.json",
    "description": "Verify agentSlot returns 200 OK"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"validate_response"` |
| `urlPattern` | Mandatory | Regex to match the request URL |
| `expectedStatus` | Optional | Expected HTTP status code (e.g., 200) |
| `captureFile` | Optional | Path to capture file (auto-loads traffic if not already loaded) |
| `description` | Optional | Human-readable step description |

### 6.12 Validate Response — Header and Body

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "validate_response",
    "urlPattern": "meta-data\\.xml",
    "headerName": "Content-Type",
    "expectedValue": "application/xml",
    "validationType": "exact",
    "captureFile": "Reports/live-capture.json",
    "description": "Verify meta-data.xml Content-Type is XML"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"validate_response"` |
| `urlPattern` | Mandatory | Regex to match the request URL |
| `headerName` | Optional | Response header name to check |
| `expectedValue` | Optional | Expected header value |
| `validationType` | Optional | Validation mode — `"exact"` requires exact match |
| `captureFile` | Optional | Path to capture file (auto-loads traffic if not already loaded) |
| `description` | Optional | Human-readable step description |

### 6.13 Assert Request Count

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "assert_request_count",
    "urlPattern": "agentSlot",
    "minCount": 1,
    "captureFile": "Reports/live-capture.json",
    "description": "Verify at least one agentSlot request captured"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"assert_request_count"` |
| `urlPattern` | Mandatory | Regex to match request URLs |
| `minCount` | Optional | Minimum number of matching requests expected |
| `captureFile` | Optional | Path to capture file (auto-loads traffic if not already loaded) |
| `description` | Optional | Human-readable step description |

### 6.14 Assert Request Order

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "assert_request_order",
    "expectedOrder": ["agentSlot", "meta-data\\.xml", "agent-settings\\.xml"],
    "captureFile": "Reports/live-capture.json",
    "description": "Verify correct request sequence"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"assert_request_order"` |
| `expectedOrder` | Mandatory | Ordered list of URL regex patterns (minimum 2) |
| `captureFile` | Optional | Path to capture file (auto-loads traffic if not already loaded) |
| `description` | Optional | Human-readable step description |

### 6.15 Capture Traffic to File

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "capture_traffic",
    "captureFile": "Reports/recorded-traffic.json",
    "description": "Save captured traffic to file"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"capture_traffic"` |
| `captureFile` | Optional | Output file path (default: `Reports/traffic-capture.json`) |
| `description` | Optional | Human-readable step description |

### 6.16 Clear Captured Traffic

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "clear_captures",
    "description": "Reset traffic buffer"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"clear_captures"` |
| `description` | Optional | Human-readable step description |

### 6.17 Modify Response — Inject Error

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "modify_response",
    "urlPattern": "meta-data\\.xml",
    "injectStatus": 500,
    "injectBody": "Internal Server Error",
    "persistent": true,
    "description": "Return 500 error on meta-data.xml requests"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"modify_response"` |
| `urlPattern` | Mandatory | Regex to match request URLs |
| `injectStatus` | Optional | Override response status code (e.g., 500) |
| `injectBody` | Optional | Override response body content |
| `persistent` | Optional | `true` = apply to all future matches |
| `description` | Optional | Human-readable step description |

### 6.18 Block Request

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "block_request",
    "urlPattern": "agentSlot",
    "blockAction": "block",
    "persistent": true,
    "description": "Block all agentSlot requests"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"block_request"` |
| `urlPattern` | Mandatory | Regex to match request URLs |
| `blockAction` | Optional | Block mode — `"block"` drops the request entirely |
| `persistent` | Optional | `true` = apply to all future matches |
| `description` | Optional | Human-readable step description |

### 6.19 Delay Request

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "block_request",
    "urlPattern": ".*",
    "blockAction": "delay",
    "delayMs": 5000,
    "persistent": true,
    "description": "Add 5 second delay to all requests"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"block_request"` |
| `urlPattern` | Mandatory | Regex to match request URLs (`.*` = all) |
| `blockAction` | Optional | Block mode — `"delay"` adds latency |
| `delayMs` | Optional | Delay duration in milliseconds |
| `persistent` | Optional | `true` = apply to all future matches |
| `description` | Optional | Human-readable step description |

### 6.20 Configure System Proxy — Enable

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "configure_proxy",
    "proxyAction": "enable",
    "proxyHost": "127.0.0.1",
    "proxyPort": 8080,
    "description": "Enable Windows system proxy"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"configure_proxy"` |
| `proxyAction` | Mandatory | Proxy operation — `"enable"` to set system proxy |
| `proxyHost` | Optional | Proxy IP address (default: `127.0.0.1`) |
| `proxyPort` | Optional | Proxy port number (default: `8080`) |
| `description` | Optional | Human-readable step description |

### 6.21 Configure System Proxy — Disable

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "configure_proxy",
    "proxyAction": "disable",
    "description": "Disable Windows system proxy"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"configure_proxy"` |
| `proxyAction` | Mandatory | Proxy operation — `"disable"` to remove system proxy |
| `description` | Optional | Human-readable step description |

### 6.22 Load Client Certificate

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "load_client_cert",
    "description": "Load agent mTLS client certificate from vault"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"load_client_cert"` |
| `clientKeyPath` | Optional | Path to client private key (default: UEMS vault) |
| `clientCertPath` | Optional | Path to client certificate (default: UEMS vault) |
| `description` | Optional | Human-readable step description |

### 6.23 Wait for Interrupt (Recording Mode)

```json
{
  "operation_type": "communication_operation",
  "parameters": {
    "action": "wait_for_interrupt",
    "description": "Record traffic until Ctrl+C"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"wait_for_interrupt"` |
| `description` | Optional | Human-readable step description |

### 6.24 Traffic Lock — Redirect All Agent Traffic Through Proxy

```json
{
  "action": "traffic_lock",
  "hostname": "prathosh-w22-11",
  "server_ip": "172.24.148.221",
  "server_port": 8383,
  "proxy_port": 8080,
  "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
  "description": "Lock all port-8383 traffic through proxy"
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | `"traffic_lock"` |
| `hostname` | **Mandatory** | Real server hostname (added to hosts file as `127.0.0.1 <hostname>`) |
| `server_ip` | **Mandatory** | Real server IP (blocked by per-exe firewall rules) |
| `server_port` | **Mandatory** | Real server port to intercept (e.g. `8383`) |
| `proxy_port` | **Mandatory** | Proxy listen port to redirect to (e.g. `8080`) |
| `firewall_block_folder` | Optional | Agent installation folder — adds per-exe `BlockUEMS_<exe>` rules |
| `description` | Optional | Human-readable step description |

**What happens:**
1. Enables Windows Firewall on all profiles
2. Adds `127.0.0.1 <hostname>` to `C:\Windows\System32\drivers\etc\hosts`
3. Creates port proxy: `server_port` on localhost → `proxy_port` (agent → proxy)
4. Creates per-exe firewall rules (if `firewall_block_folder` specified) — blocks agent exes from reaching `server_ip:server_port`
5. Flushes DNS cache and restarts agent services

**Why the proxy is not blocked:** The proxy executable is NOT in the agent folder, so per-exe rules don't affect it. The proxy connects directly to `server_ip:server_port`.

### 6.25 Traffic Unlock — Restore Direct Traffic Flow

```json
{
  "action": "traffic_unlock",
  "server_port": 8383,
  "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
  "continueOnFailure": true,
  "description": "Unlock traffic - restore direct flow"
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | `"traffic_unlock"` |
| `server_port` | Recommended | Port to remove from port proxy (also read from stored context) |
| `firewall_block_folder` | Recommended | Folder to enumerate `.exe` for firewall rule cleanup (also read from stored context) |
| `continueOnFailure` | Optional | Recommended `true` for cleanup steps |
| `description` | Optional | Human-readable step description |

**What happens:**
1. Removes all hosts file entries tagged `# UEMS_TRAFFIC_LOCK`
2. Deletes port proxy (`server_port`)
3. Deletes all per-exe `BlockUEMS_*` firewall rules
4. Flushes DNS cache

### 6.26 Run Command — Execute Shell Command

```json
{
  "action": "run_command",
  "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
  "timeout": 30,
  "continueOnFailure": true,
  "description": "Trigger cfgupdate"
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | `"run_command"` |
| `command_line` | **Mandatory** | Shell command to execute |
| `timeout` | Optional | Timeout in seconds (default: `60`) |
| `working_dir` | Optional | Working directory for the command |
| `continueOnFailure` | Optional | Continue on non-zero exit code |
| `description` | Optional | Human-readable step description |

### 6.27 Switch Capture File — Separate Traffic Per Test Phase

```json
{
  "action": "switch_capture_file",
  "liveCaptureFile": "Reports/phase2-traffic.json",
  "description": "Switch live capture to phase 2 file"
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | `"switch_capture_file"` |
| `liveCaptureFile` | **Mandatory** | New live capture file path |
| `timeout` | Optional | Max wait for proxy acknowledgment (default: `10`s) |
| `description` | Optional | Human-readable step description |

> **Requires:** A background proxy started with `start_proxy`. Clears in-memory traffic flows automatically.

---

## 7. Complete Test Examples
> Full end-to-end workflows demonstrating setup, traffic capture, validation, and cleanup.

### 7.1 Single-Phase Test (Basic)

A standard workflow: install cert → start proxy → lock traffic → trigger agent → validate → cleanup.

```json
[
  {"id": "SETUP_001", "action": "install_certificate", "cert_action": "install",
   "description": "Install mitmproxy CA cert"},

  {"id": "SETUP_002", "action": "start_proxy", "listen_host": "127.0.0.1", "listen_port": 8080,
   "mode": "reverse:https://172.24.148.221:8383", "ssl_insecure": true,
   "description": "Start reverse proxy"},

  {"id": "SETUP_003", "action": "traffic_lock",
   "hostname": "selva-w11", "server_ip": "172.24.148.221",
   "server_port": 8383, "proxy_port": 8080,
   "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
   "continueOnFailure": true, "description": "Lock all port-8383 traffic through proxy"},

  {"id": "SETUP_004", "action": "clear_captures", "description": "Reset capture buffer"},

  {"id": "SETUP_005", "action": "run_command",
   "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
   "timeout": 30, "continueOnFailure": true, "description": "Trigger cfgupdate"},

  {"id": "WAIT_001", "action": "wait_for_request", "url_pattern": "agentSlot", "timeout": 180,
   "description": "Wait for agentSlot request"},

  {"id": "TC_001", "action": "validate_request", "url_pattern": "agentSlot", "method": "GET",
   "description": "agentSlot: method is GET"},

  {"id": "TC_002", "action": "validate_request", "url_pattern": "agentSlot",
   "header_name": "Authorization", "expected_value": "Basic ",
   "validation_type": "startswith", "description": "agentSlot: has Basic auth header"},

  {"id": "TC_003", "action": "validate_response", "url_pattern": "agentSlot",
   "expected_status": 200, "description": "agentSlot: response 200 OK"},

  {"id": "TC_004", "action": "assert_request_count", "url_pattern": "agentSlot", "min_count": 1,
   "continueOnFailure": true, "description": "At least 1 agentSlot request"},

  {"id": "SAVE_001", "action": "capture_traffic", "capture_file": "Reports/general-validation-traffic.json",
   "continueOnFailure": true, "description": "Save captured traffic"},

  {"id": "CLEANUP_001", "action": "traffic_unlock",
   "server_port": 8383,
   "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
   "continueOnFailure": true, "description": "Unlock traffic - restore direct flow"},
  {"id": "CLEANUP_002", "action": "run_command",
   "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
   "timeout": 30, "continueOnFailure": true, "description": "Restore agent config"},
  {"id": "CLEANUP_003", "action": "stop_proxy", "description": "Stop proxy"}
]
```

### 7.2 Multi-Phase Test (Using switch_capture_file)

Separate traffic into per-phase capture files. Lock traffic once, run multiple test phases.

```json
[
  {"id": "SETUP_001", "action": "install_certificate", "cert_action": "install",
   "description": "Install mitmproxy CA cert"},

  {"id": "SETUP_002", "action": "start_proxy", "listen_host": "127.0.0.1", "listen_port": 8080,
   "mode": "reverse:https://172.24.148.221:8383", "ssl_insecure": true,
   "live_capture_file": "Reports/phase1-traffic.json",
   "description": "Start reverse proxy with phase 1 capture"},

  {"id": "SETUP_003", "action": "traffic_lock",
   "hostname": "selva-w11", "server_ip": "172.24.148.221",
   "server_port": 8383, "proxy_port": 8080,
   "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
   "continueOnFailure": true, "description": "Lock all traffic through proxy"},

  {"id": "P1_001", "action": "clear_captures", "description": "Reset for phase 1"},
  {"id": "P1_002", "action": "run_command",
   "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
   "timeout": 30, "continueOnFailure": true, "description": "Phase 1: Trigger cfgupdate"},
  {"id": "P1_003", "action": "wait_for_request", "url_pattern": "agentSlot", "timeout": 180,
   "description": "Phase 1: Wait for agentSlot"},
  {"id": "P1_TC1", "action": "validate_request", "url_pattern": "agentSlot", "method": "GET",
   "description": "Phase 1: agentSlot is GET"},
  {"id": "P1_SAVE", "action": "capture_traffic", "capture_file": "Reports/phase1-traffic.json",
   "description": "Save phase 1 traffic"},

  {"id": "SWITCH", "action": "switch_capture_file",
   "liveCaptureFile": "Reports/phase2-traffic.json",
   "description": "Switch capture to phase 2 file"},

  {"id": "P2_001", "action": "clear_captures", "description": "Reset for phase 2"},
  {"id": "P2_002", "action": "run_command",
   "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
   "timeout": 30, "continueOnFailure": true, "description": "Phase 2: Trigger cfgupdate again"},
  {"id": "P2_003", "action": "wait_for_request", "url_pattern": "meta-data\\.xml", "timeout": 120,
   "continueOnFailure": true, "description": "Phase 2: Wait for meta-data.xml"},
  {"id": "P2_TC1", "action": "validate_request", "url_pattern": "meta-data\\.xml", "method": "GET",
   "continueOnFailure": true, "description": "Phase 2: meta-data.xml is GET"},
  {"id": "P2_SAVE", "action": "capture_traffic", "capture_file": "Reports/phase2-traffic.json",
   "description": "Save phase 2 traffic"},

  {"id": "CLEANUP_001", "action": "traffic_unlock",
   "server_port": 8383,
   "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
   "continueOnFailure": true, "description": "Unlock traffic"},
  {"id": "CLEANUP_002", "action": "run_command",
   "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
   "timeout": 30, "continueOnFailure": true, "description": "Restore agent config"},
  {"id": "CLEANUP_003", "action": "stop_proxy", "description": "Stop proxy"}
]
```

### Multi-Phase Flow Pattern

```
start_proxy (live_capture_file: phase1.json)
  └── traffic_lock (once for all phases)
        │
        ├── Phase 1: clear_captures → trigger → wait → validate → capture_traffic
        │
        ├── switch_capture_file (phase2.json)  ← clears in-memory flows
        │
        ├── Phase 2: clear_captures → trigger → wait → validate → capture_traffic
        │
        ├── switch_capture_file (phase3.json)  ← repeat as needed
        │
        └── Phase N: ...
  └── traffic_unlock (once, restores everything)
  └── stop_proxy
```

---

## 8. Command-Line Usage
> How to run or validate your communication test JSON files from the command line.

| # | Command | Purpose |
|---|---------|---------|
| 8.1 | `python main.py TestData\smoke_test.json` | Execute a test file |
| 8.2 | `python main.py TestData\general_validation.json --show-all` | Execute with verbose output |
| 8.3 | `CommunicationAutomation.exe TestData\smoke_test.json` | Run from standalone EXE |
| 8.4 | `run_capture.bat` | Quick-run recording session |
| 8.5 | `install_and_run.bat` | First-time setup and run |

**Run Modes:**
- **Test Mode** (default): `python main.py <test.json>` — Runs all commands sequentially, outputs GOAT-compatible results
- **Serve Mode** (internal): `python main.py --serve <host> <port> <mode> [...]` — Used internally by `start_proxy` to launch the background proxy process

---

## 9. Output Format
> The structure of results returned after execution, showing step status (PASSED/FAILED) and a summary.

The tool returns results in the following format:

```
SETUP_001|PASSED|Certificate installed successfully
SETUP_002|PASSED|Patched 6 values: DCNSLastAccessName: naveen-14097 -> 127.0.0.1
TC_001|PASSED|Request found: GET /agentSlot...
TC_002|PASSED|Header 'Authorization': Value starts with 'Basic '
TC_003|PASSED|Response validated: status=200
TC_004|FAILED|No matching request found for pattern: nonexistent
SUMMARY|Total:6|Passed:5|Failed:1
```

**Format Breakdown:**
- **Step ID** — Unique identifier from your command
- **Status** — PASSED or FAILED
- **Message** — Detailed result or error message
- **SUMMARY** — Total commands, passed count, failed count

**Exit Codes:**

| # | Code | Meaning |
|---|------|---------|
| 9.1 | `0` | All steps passed |
| 9.2 | `1` | One or more steps failed |
| 9.3 | `5` | Test file not found |
| 9.4 | `99` | Critical/unexpected error |

---

## 10. Common Properties
> A reference of all JSON properties shared across all actions and whether they are required.

### Global Properties (All Actions)

| # | Property | Required | Type | Description |
|---|----------|----------|------|-------------|
| 10.1 | `action` | **Required** | String | Action to perform (see Supported Actions) |
| 10.2 | `description` | Optional | String | Human-readable step description |
| 10.3 | `continueOnFailure` | Optional | Boolean | Continue on error (default: `false`) |
| 10.4 | `timeout` | Optional | Integer | Timeout in seconds (used by `wait_for_request`) |

### Validation Properties (validate_request, validate_response)

| # | Property | Required | Type | Description |
|---|----------|----------|------|-------------|
| 10.5 | `urlPattern` | **Required** | String | Regex to match the request URL |
| 10.6 | `method` | Optional | String | Expected HTTP method (GET, POST) |
| 10.7 | `headerName` | Optional | String | Name of the header to check |
| 10.8 | `expectedValue` | Optional | String | Expected value for header or URL check |
| 10.9 | `bodyPattern` | Optional | String | Pattern to check in request/response body |
| 10.10 | `validationType` | Optional | String | Validation mode (default: `contains`) |
| 10.11 | `caseSensitive` | Optional | Boolean | Case-sensitive matching (default: `false`) |
| 10.12 | `expectedStatus` | Optional | Integer | Expected HTTP status code (validate_response only) |

**Legend:**
- **Required** = Must be provided for the action to work
- **Optional** = Can be omitted; defaults will be used

**JSON Key Format:** Both `camelCase` and `snake_case` are accepted. For example, `urlPattern` and `url_pattern` are equivalent.

---

## 11. Action Reference — Detailed
> Comprehensive parameter reference for every supported action with required/optional fields and defaults.

### 11.1 start_proxy — Start mitmproxy

> Spawns mitmproxy as a detached background process. Writes traffic to a live capture JSON file.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `listenHost` | String | No | `"127.0.0.1"` | IP address to listen on |
| `listenPort` | Integer | **Yes** | `8080` | Port number (must be > 0) |
| `mode` | String | No | `""` (regular) | Proxy mode: `""`, `reverse:https://...`, `upstream:http://...` |
| `sslInsecure` | Boolean | No | `false` | Skip upstream SSL certificate verification |
| `autoLoadCert` | Boolean | No | `false` | Auto-load agent client cert from vault on CSR detection |
| `liveCaptureFile` | String | No | `"Reports/live-capture.json"` | Path to live traffic output file |

---

### 11.2 stop_proxy — Stop the proxy

> Stops the running proxy process using multiple fallback strategies.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| *(none specific)* | — | — | — | Automatically finds and kills proxy process |

**Kill Strategy:** In-process shutdown → PID file (`Reports/proxy.pid`) → Context PID → `taskkill /F /T`

---

### 11.3 install_cert — Install or remove CA certificate

> Install or remove the mitmproxy CA certificate from the Windows trust store. Use `cert_action` to specify direction.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `cert_action` | String | **Yes** | — | `"install"` or `"remove"` |
| `cert_path` | String | No | Auto-detected | Path to certificate file |

---

### 11.4 load_client_cert — Load mTLS client certificate

> Load agent client certificate + key from UEMS vault for mTLS forwarding.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `clientKeyPath` | String | No | UEMS vault `key.pem` | Path to client private key |
| `clientCertPath` | String | No | UEMS vault `client.pem` | Path to client certificate |

**Supported Formats:** PEM, DER, hex-encoded DER, base64. Outputs combined PEM to `Reports/agent_client_combined.pem`.

---

### 11.5 load_traffic — Load traffic from file

> Load previously captured traffic from a JSON file into memory for offline validation.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `captureFile` | String | **Yes** | — | Path to the JSON traffic capture file |

---

### 11.6 configure_proxy — System proxy settings

> Enable or disable Windows system-level proxy via `netsh winhttp`.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `proxyAction` | String | **Yes** | — | `"enable"` or `"disable"` |
| `proxyHost` | String | No | `"127.0.0.1"` | Proxy host address |
| `proxyPort` | Integer | No | `8080` | Proxy port number |
| `bypassList` | String | No | `""` | Comma-separated bypass list |

---

### 11.7 wait_for_request — Wait for HTTP request

> Block until a matching HTTP request arrives, with timeout. Supports in-process and file-polling modes.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `urlPattern` | String | **Yes** | — | Regex to match request URL |
| `method` | String | No | `""` (any) | Expected HTTP method |
| `timeout` | Integer | No | `60` | Timeout in seconds |
| `captureFile` | String | No | `""` | Path to live capture file (enables file-polling mode) |

**Modes:**
- **In-process mode:** Waits on the `TrafficStore` directly (when proxy runs in same process)
- **File-polling mode:** Re-reads the live capture JSON file every 3 seconds (when `captureFile` is set)

---

### 11.8 wait_for_interrupt — Recording mode

> Block indefinitely, recording traffic until the user presses Ctrl+C.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| *(none specific)* | — | — | — | Prints capture count every 10 seconds |

---

### 11.9 validate_request — Assert request properties

> Validate captured request URL, method, headers, or body content.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `urlPattern` | String | **Yes** | — | Regex to match request URL |
| `method` | String | No | `""` | Expected HTTP method |
| `headerName` | String | No | `""` | Request header name to check |
| `expectedValue` | String | No | `""` | Expected value (for header or URL check) |
| `bodyPattern` | String | No | `""` | Pattern to check in request body |
| `validationType` | String | No | `"contains"` | Validation mode (see [Section 4](#4-validation-types)) |
| `caseSensitive` | Boolean | No | `false` | Enable case-sensitive matching |
| `captureFile` | String | No | `""` | Path to capture file (auto-loads traffic if store is empty) |

**Validation Priority Order:**
1. **Header validation** — if `headerName` + `expectedValue` are both set
2. **Body validation** — if `bodyPattern` is set
3. **URL validation** — if `expectedValue` alone is set
4. **Existence check** — if no specific validation fields are set (just checks request exists)

---

### 11.10 validate_response — Assert response properties

> Validate response status code, headers, or body content for a captured request.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `urlPattern` | String | **Yes** | — | Regex to match request URL |
| `method` | String | No | `""` | Expected HTTP method |
| `expectedStatus` | Integer | No | `0` (skip) | Expected HTTP status code |
| `headerName` | String | No | `""` | Response header name to check |
| `expectedValue` | String | No | `""` | Expected value for header check |
| `bodyPattern` | String | No | `""` | Pattern to check in response body |
| `validationType` | String | No | `"contains"` | Validation mode (see [Section 4](#4-validation-types)) |
| `caseSensitive` | Boolean | No | `false` | Enable case-sensitive matching |
| `captureFile` | String | No | `""` | Path to capture file (auto-loads traffic if store is empty) |

**Validation Priority Order:**
1. **Status code** — if `expectedStatus > 0`
2. **Response header** — if `headerName` + `expectedValue` are both set
3. **Response body** — if `bodyPattern` is set
4. **Existence check** — if no specific validation fields are set

---

### 11.11 assert_request_count — Verify request count

> Verify the number of captured requests matching a URL pattern.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `urlPattern` | String | **Yes** | — | Regex to match request URL |
| `method` | String | No | `""` | Filter by HTTP method |
| `expectedCount` | Integer | No | `-1` (skip) | Exact expected count |
| `minCount` | Integer | No | `0` | Minimum expected count |
| `maxCount` | Integer | No | `0` | Maximum expected count |
| `captureFile` | String | No | `""` | Path to capture file (auto-loads traffic if store is empty) |

**Check Order:** Exact count (if ≥ 0) → Min count (if > 0) → Max count (if > 0)

---

### 11.12 assert_request_order — Verify request sequence

> Verify that captured requests arrived in a specific order.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `expectedOrder` | List[String] | **Yes** | `[]` | Ordered list of URL patterns (minimum 2) |
| `captureFile` | String | No | `""` | Path to capture file (auto-loads traffic if store is empty) |

**Behavior:** For each pattern, finds its first occurrence index in all captured flows and verifies indices are strictly ascending.

---

### 11.13 capture_traffic — Save traffic to file

> Dump all captured traffic from memory to a JSON file.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `captureFile` | String | No | `"Reports/traffic-capture.json"` | Output file path |

---

### 11.14 clear_captures — Reset traffic buffer

> Clear all captured traffic from memory.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| *(none specific)* | — | — | — | Succeeds even if no traffic store exists |

---

### 11.15 modify_response — Inject/alter responses

> Create a rule to modify responses for matching requests (one-shot or persistent).

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `urlPattern` | String | **Yes** | — | Regex to match request URL |
| `method` | String | No | `""` | Filter by HTTP method |
| `injectStatus` | Integer | No | `0` (unchanged) | Override response status code |
| `injectBody` | String | No | `""` | Override response body |
| `injectHeaders` | Object | No | `{}` | Headers to add/override |
| `persistent` | Boolean | No | `false` | `true` = apply to all future matches; `false` = one-shot |

**Requires:** Proxy must be running. Rules only work in the background proxy process.

---

### 11.16 block_request — Block or delay requests

> Block or add latency to matching requests.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `urlPattern` | String | **Yes** | — | Regex to match request URL |
| `method` | String | No | `""` | Filter by HTTP method |
| `blockAction` | String | No | `"block"` | `"block"` (drop request) or `"delay"` (add latency) |
| `delayMs` | Integer | No | `0` | Delay in milliseconds (for `"delay"` action) |
| `persistent` | Boolean | No | `false` | `true` = apply to all future matches; `false` = one-shot |

---

### 11.17 traffic_lock — Redirect all agent traffic through proxy

> Performs OS-level traffic lock: enable firewall → hosts file → port proxy → per-exe block → DNS flush → agent restart. All parameters are explicit (no auto-discovery).

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `hostname` | String | **Yes** | — | Real server hostname (added to hosts file) |
| `server_ip` | String | **Yes** | — | Real server IP (blocked by per-exe firewall rules) |
| `server_port` | Integer | **Yes** | — | Real server port to intercept (e.g. `8383`) |
| `proxy_port` | Integer | **Yes** | — | Proxy listen port to redirect to (e.g. `8080`) |
| `firewall_block_folder` | String | No | — | Agent installation folder — adds per-exe block rules |

**Execution Order:**
1. **Enable firewall** — `netsh advfirewall set allprofiles state on`
2. **Hosts file** — appends `127.0.0.1 <hostname>` with `# UEMS_TRAFFIC_LOCK` marker
3. **Port proxy** — `netsh interface portproxy add v4tov4 listenport=<server_port> listenaddress=127.0.0.1 connectport=<proxy_port> connectaddress=127.0.0.1`
4. **Per-exe firewall** — if `firewall_block_folder` specified, creates `BlockUEMS_<exe>` for each `.exe` found recursively
5. **DNS flush** — `ipconfig /flushdns`
6. **Agent service restart** — stops and starts agent services to clear in-process DNS cache

**Why the proxy is not blocked:** The proxy executable is NOT in the agent folder, so per-exe rules don't affect it. The proxy connects directly to `server_ip:server_port`.

**Context Output:** Stores `hostname`, `server_ip`, `server_port`, `proxy_port`, and `firewall_block_folder` in `context["traffic_lock"]`.

---

### 11.18 traffic_unlock — Restore direct traffic flow

> Undoes everything `traffic_lock` did. Can read stored context or accept explicit parameters.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `server_port` | Integer | Recommended | stored | Port to delete from port proxy |
| `firewall_block_folder` | String | Recommended | stored | Folder to enumerate `.exe` for rule cleanup |

**Execution Order:**
1. **Remove hosts entries** — deletes all lines tagged `# UEMS_TRAFFIC_LOCK`
2. **Remove port proxy** — `netsh interface portproxy delete v4tov4 listenport=<server_port>`
3. **Remove per-exe rules** — enumerates folder to derive `BlockUEMS_<exe>` names + sweeps remaining `BlockUEMS_*` rules
4. **DNS flush** — `ipconfig /flushdns`

---

### 11.19 run_command — Execute shell command

> Run an arbitrary command via `subprocess.run(shell=True)`. Reports PASSED if exit code is 0.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `command_line` | String | **Yes** | — | Shell command to execute |
| `timeout` | Integer | No | `60` | Timeout in seconds |
| `working_dir` | String | No | Current directory | Working directory for the command |

---

### 11.20 switch_capture_file — Switch live capture file

> Sends a file-based control signal to the running background proxy to switch its live capture output to a new JSON file. Existing captured flows are cleared automatically.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `liveCaptureFile` | String | **Yes** | — | New live capture file path |
| `timeout` | Integer | No | `10` | Max wait for proxy acknowledgment (seconds) |

**Mechanism:** Writes a control JSON to `Reports/proxy-control.json`, waits for the proxy to read it and write acknowledgment to `Reports/proxy-control-ack.json`.

**Requires:** A background proxy running (started with `start_proxy`).

---

## 12. Best Practices
> Recommended guidelines for writing reliable and maintainable communication test commands.

| # | Practice | Importance | Details |
|---|----------|-----------|---------|
| 12.1 | Always use unique step IDs | Critical | Enables tracking, debugging, and GOAT output mapping |
| 12.2 | Add descriptions to every step | High | Makes test files self-documenting |
| 12.3 | Follow Setup → Wait → Validate → Save → Cleanup pattern | High | Standard test flow ensures reliable execution |
| 12.4 | Use `continueOnFailure` on cleanup steps | High | Ensures cleanup runs even if tests fail |
| 12.5 | Set appropriate timeouts | High | `wait_for_request` default is 60s; agent may need 120s+ |
| 12.6 | Use file-polling mode for `wait_for_request` | High | Set `captureFile` for cross-process traffic access |
| 12.7 | Always stop proxy on cleanup | Critical | Prevents orphaned proxy processes |
| 12.9 | Always remove CA cert on cleanup | Important | Clean security state after testing |
| 12.10 | Use regex in `urlPattern` carefully | Medium | Escape dots: `meta-data\\.xml` not `meta-data.xml` |
| 12.11 | Update server hostname/IP per machine | Important | Test JSONs contain machine-specific values |
| 12.11 | Run as Administrator | Critical | Required for certificate and proxy operations |

---

## 13. Error Handling
> Common errors you may encounter and how to fix them.

| # | Error | Cause | Solution |
|---|-------|-------|----------|
| 13.1 | Access Denied | Not running as Administrator | Run terminal/cmd as Administrator |
| 13.2 | No matching request found | Request hasn't arrived yet or wrong pattern | Increase timeout or check `urlPattern` regex |
| 13.3 | Proxy already running | Previous proxy not stopped | Run `stop_proxy` first or check `Reports/proxy.pid` |
| 13.4 | Port already in use | Another process using the port | Change `listenPort` or kill conflicting process |
| 13.5 | Certificate not found | mitmproxy CA cert doesn't exist yet | Run mitmproxy once to generate certs, or provide `certPath` |
| 13.6 | Traffic file empty | Agent hasn't sent requests yet | Wait longer or trigger agent with `run_command` |
| 13.7 | Capture file not found | Wrong path to traffic JSON | Verify file path; check `Reports/` folder |
| 13.8 | mTLS cert conversion failed | Proprietary/encrypted vault format | Proxy continues without mTLS; manual cert conversion needed |
| 13.9 | Validation type mismatch | Wrong `validationType` used | Use: `contains`, `exact`, `startswith`, `endswith`, `regex` |
| 13.10 | traffic_lock: folder not found | Wrong `firewall_block_folder` path | Verify the agent installation folder exists |
| 13.11 | traffic_lock: No .exe files found | Wrong `firewall_block_folder` path | Check `C:\Program Files (x86)\ManageEngine\UEMS_Agent` exists and contains `.exe` files |
| 13.12 | traffic_lock: Firewall rule creation failed | Insufficient permissions or invalid exe path | Run as Administrator; check exe paths in the log |
| 13.13 | traffic_unlock: No lock parameters found | `traffic_lock` was not run first or explicit params missing | Pass `server_port` and `firewall_block_folder` explicitly |
| 13.14 | Agent traffic still bypasses proxy | Firewall rules not blocking the right exe/IP | Check logs for `[Firewall] Verified N BlockUEMS_* rule(s)`; verify `server_ip` is correct |
| 13.15 | switch_capture_file: No background proxy running | Proxy not started or PID stale | Ensure `start_proxy` ran before `switch_capture_file` |

---

## 14. Common Scenarios — How-To Guide
> Step-by-step walkthroughs for the most common tasks. Each scenario explains **what** you're doing, **why** each step is needed, and gives you a ready-to-use JSON file.

### 14.1 Quick Start — Your First Traffic Capture

**Goal:** Capture all HTTPS traffic the UEMS Agent sends to the server.

**What you need to know first:**
- The agent talks to a server (e.g. `myserver`) on port `8383` over HTTPS
- We intercept this by making the agent think `myserver` is at `127.0.0.1`, then routing that traffic through a local proxy
- The proxy forwards everything to the real server and records it

**Steps at a glance:**
```
Install CA cert  →  Start proxy  →  Lock traffic  →  Wait  →  Save  →  Cleanup
```

**Ready-to-use JSON** — save as `quick_start.json` and run with `CommunicationAutomation.exe quick_start.json`:

```json
[
  {
    "id": "STEP_01",
    "action": "install_cert",
    "cert_action": "install",
    "description": "Install the proxy's CA certificate so HTTPS works"
  },
  {
    "id": "STEP_02",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true,
    "description": "Start the proxy — it will forward traffic to the real server"
  },
  {
    "id": "STEP_03",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "description": "Redirect all agent traffic through the proxy"
  },
  {
    "id": "STEP_04",
    "action": "wait_for_request",
    "url_pattern": ".",
    "timeout": 120,
    "capture_file": "Reports/live-capture.json",
    "description": "Wait up to 2 minutes for any request to arrive"
  },
  {
    "id": "STEP_05",
    "action": "capture_traffic",
    "capture_file": "Reports/my-first-capture.json",
    "description": "Save all captured traffic to a file"
  },
  {
    "id": "CLEANUP_01",
    "action": "traffic_unlock",
    "server_port": 8383,
    "continueOnFailure": true,
    "description": "Undo all traffic redirection"
  },
  {
    "id": "CLEANUP_02",
    "action": "stop_proxy",
    "description": "Stop the proxy process"
  },
  {
    "id": "CLEANUP_03",
    "action": "install_cert",
    "cert_action": "remove",
    "continueOnFailure": true,
    "description": "Remove the proxy CA certificate"
  }
]
```

> **Before you run:** Replace `myserver` with your actual server hostname and `10.0.0.50` with its IP address. You can find these in the agent's `ServerInfo.props` or registry.

---

### 14.2 Fresh Agent Installation — Capture Setup Traffic

**Goal:** See what requests the agent makes during a fresh install (before any `.exe` files exist).

**Key difference:** Since the agent hasn't been installed yet, there are no `.exe` files to create firewall rules for. Simply omit `firewall_block_folder` — the framework will skip the firewall step.

```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install",
    "description": "Install proxy CA cert before agent installation"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true,
    "description": "Start proxy to capture install-time traffic"
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "description": "Lock traffic (no firewall needed — agent not installed yet)"
  },
  {
    "id": "RECORD",
    "action": "wait_for_interrupt",
    "description": "Now install the agent. Press Ctrl+C when installation finishes."
  },
  {
    "id": "SAVE",
    "action": "capture_traffic",
    "capture_file": "Reports/fresh-install-traffic.json",
    "continueOnFailure": true,
    "description": "Save all installation traffic"
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "continueOnFailure": true,
    "description": "Restore direct traffic flow"
  },
  {
    "id": "STOP",
    "action": "stop_proxy",
    "description": "Stop proxy"
  }
]
```

> **How to use:** Run this JSON, then install the agent while the `wait_for_interrupt` step is active. Press `Ctrl+C` once installation completes. The captured traffic will be saved to `Reports/fresh-install-traffic.json`.

---

### 14.3 Validate Agent Sends Correct Headers

**Goal:** Verify the agent includes the right `Authorization` header and uses the correct HTTP method.

```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent"
  },
  {
    "id": "TRIGGER",
    "action": "run_command",
    "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
    "timeout": 30,
    "continueOnFailure": true,
    "description": "Trigger the agent to phone home"
  },
  {
    "id": "WAIT",
    "action": "wait_for_request",
    "url_pattern": "agentSlot",
    "timeout": 180,
    "capture_file": "Reports/live-capture.json",
    "description": "Wait for the agentSlot request"
  },
  {
    "id": "CHECK_METHOD",
    "action": "validate_request",
    "url_pattern": "agentSlot",
    "method": "GET",
    "description": "agentSlot should use GET method"
  },
  {
    "id": "CHECK_AUTH",
    "action": "validate_request",
    "url_pattern": "agentSlot",
    "header_name": "Authorization",
    "expected_value": "Basic ",
    "validation_type": "startswith",
    "description": "agentSlot should have Basic auth header"
  },
  {
    "id": "CHECK_STATUS",
    "action": "validate_response",
    "url_pattern": "agentSlot",
    "expected_status": 200,
    "description": "Server should return 200 OK"
  },
  {
    "id": "SAVE",
    "action": "capture_traffic",
    "capture_file": "Reports/header-validation.json",
    "continueOnFailure": true
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "continueOnFailure": true
  },
  {
    "id": "STOP",
    "action": "stop_proxy"
  }
]
```

**Expected output:**
```
CHECK_METHOD|PASSED|Request found: GET https://myserver:8383/agentSlot...
CHECK_AUTH|PASSED|Header 'Authorization': Value starts with 'Basic '
CHECK_STATUS|PASSED|Response validated: status=200
```

---

### 14.4 Simulate Server Down — Test Agent Error Handling

**Goal:** Make the server return `500 Internal Server Error` on every request and observe how the agent handles it.

```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent"
  },
  {
    "id": "INJECT_500",
    "action": "modify_response",
    "url_pattern": ".*",
    "inject_status": 500,
    "inject_body": "Internal Server Error",
    "persistent": true,
    "description": "Return 500 on ALL requests"
  },
  {
    "id": "TRIGGER",
    "action": "run_command",
    "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
    "timeout": 30,
    "continueOnFailure": true,
    "description": "Trigger agent while server is 'down'"
  },
  {
    "id": "WAIT",
    "action": "wait_for_request",
    "url_pattern": ".",
    "timeout": 120,
    "capture_file": "Reports/live-capture.json"
  },
  {
    "id": "VERIFY_RETRY",
    "action": "assert_request_count",
    "url_pattern": "agentSlot",
    "min_count": 2,
    "continueOnFailure": true,
    "description": "Agent should retry after getting 500"
  },
  {
    "id": "SAVE",
    "action": "capture_traffic",
    "capture_file": "Reports/server-error-test.json",
    "continueOnFailure": true
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "continueOnFailure": true
  },
  {
    "id": "STOP",
    "action": "stop_proxy"
  }
]
```

> **What to look for:** Check the captured traffic to see how many retries the agent made, time between retries, and whether it uses exponential backoff.

---

### 14.5 Block a Specific Request

**Goal:** Completely block the `meta-data.xml` download while letting everything else pass through normally.

```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent"
  },
  {
    "id": "BLOCK_METADATA",
    "action": "block_request",
    "url_pattern": "meta-data\\.xml",
    "block_action": "block",
    "persistent": true,
    "description": "Drop all meta-data.xml requests"
  },
  {
    "id": "TRIGGER",
    "action": "run_command",
    "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
    "timeout": 30,
    "continueOnFailure": true
  },
  {
    "id": "WAIT",
    "action": "wait_for_request",
    "url_pattern": "agentSlot",
    "timeout": 180,
    "capture_file": "Reports/live-capture.json"
  },
  {
    "id": "CHECK_NO_METADATA",
    "action": "assert_request_count",
    "url_pattern": "meta-data\\.xml",
    "min_count": 0,
    "max_count": 0,
    "continueOnFailure": true,
    "description": "meta-data.xml should be blocked (zero captured)"
  },
  {
    "id": "CHECK_AGENTSLOT",
    "action": "assert_request_count",
    "url_pattern": "agentSlot",
    "min_count": 1,
    "description": "agentSlot should still work"
  },
  {
    "id": "SAVE",
    "action": "capture_traffic",
    "capture_file": "Reports/blocked-metadata-test.json",
    "continueOnFailure": true
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "continueOnFailure": true
  },
  {
    "id": "STOP",
    "action": "stop_proxy"
  }
]
```

---

### 14.6 Add Network Latency to All Requests

**Goal:** Simulate a slow network by adding 5-second delay to every request.

```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent"
  },
  {
    "id": "ADD_DELAY",
    "action": "block_request",
    "url_pattern": ".*",
    "block_action": "delay",
    "delay_ms": 5000,
    "persistent": true,
    "description": "Add 5 second delay to every request"
  },
  {
    "id": "RECORD",
    "action": "wait_for_interrupt",
    "description": "Observe agent behavior under latency. Press Ctrl+C to stop."
  },
  {
    "id": "SAVE",
    "action": "capture_traffic",
    "capture_file": "Reports/latency-test.json",
    "continueOnFailure": true
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "continueOnFailure": true
  },
  {
    "id": "STOP",
    "action": "stop_proxy"
  }
]
```

---

### 14.7 Record Traffic for Later Analysis

**Goal:** Just record everything the agent sends — analyze it later at your own pace.

```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true,
    "live_capture_file": "Reports/recording.json"
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent"
  },
  {
    "id": "RECORD",
    "action": "wait_for_interrupt",
    "description": "Recording... traffic is being saved live to Reports/recording.json. Press Ctrl+C when done."
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "continueOnFailure": true
  },
  {
    "id": "STOP",
    "action": "stop_proxy"
  }
]
```

**Then analyze the recording later:**

```json
[
  {
    "id": "LOAD",
    "action": "load_traffic",
    "capture_file": "Reports/recording.json",
    "description": "Load previously recorded traffic"
  },
  {
    "id": "CHECK_1",
    "action": "validate_request",
    "url_pattern": "agentSlot",
    "method": "GET",
    "description": "Was agentSlot a GET?"
  },
  {
    "id": "CHECK_2",
    "action": "validate_request",
    "url_pattern": "agentSlot",
    "header_name": "Authorization",
    "expected_value": "",
    "validation_type": "notempty",
    "description": "Did agentSlot have an Authorization header?"
  },
  {
    "id": "CHECK_3",
    "action": "assert_request_order",
    "expected_order": ["agentSlot", "meta-data\\.xml"],
    "description": "Did agentSlot come before meta-data.xml?"
  },
  {
    "id": "COUNT",
    "action": "assert_request_count",
    "url_pattern": ".",
    "min_count": 1,
    "description": "Total requests captured"
  }
]
```

> **Tip:** The offline validation JSON doesn't need any proxy, certificate, or traffic lock — it just reads the recorded file.

---

### 14.8 Two-Phase Test — Install + Post-Install Validation

**Goal:** Capture traffic during agent installation (Phase 1), then capture traffic from the running agent with full firewall protection (Phase 2).

**Phase 1 — During Installation** (save as `phase1_install.json`):
```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true,
    "live_capture_file": "Reports/install-phase.json"
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "description": "Lock traffic WITHOUT firewall (agent not yet installed)"
  },
  {
    "id": "WAIT",
    "action": "wait_for_interrupt",
    "description": "Install the agent now. Press Ctrl+C when installation completes."
  },
  {
    "id": "SAVE",
    "action": "capture_traffic",
    "capture_file": "Reports/install-phase.json",
    "continueOnFailure": true
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "continueOnFailure": true
  },
  {
    "id": "STOP",
    "action": "stop_proxy"
  }
]
```

**Phase 2 — After Installation** (save as `phase2_validate.json`):
```json
[
  {
    "id": "CERT",
    "action": "install_cert",
    "cert_action": "install"
  },
  {
    "id": "PROXY",
    "action": "start_proxy",
    "listen_port": 8080,
    "mode": "reverse:https://myserver:8383",
    "ssl_insecure": true
  },
  {
    "id": "LOCK",
    "action": "traffic_lock",
    "hostname": "myserver",
    "server_ip": "10.0.0.50",
    "server_port": 8383,
    "proxy_port": 8080,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "description": "Lock traffic WITH firewall (agent is now installed)"
  },
  {
    "id": "TRIGGER",
    "action": "run_command",
    "command_line": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
    "timeout": 30,
    "continueOnFailure": true
  },
  {
    "id": "WAIT",
    "action": "wait_for_request",
    "url_pattern": "agentSlot",
    "timeout": 180,
    "capture_file": "Reports/live-capture.json"
  },
  {
    "id": "TC_01",
    "action": "validate_request",
    "url_pattern": "agentSlot",
    "method": "GET"
  },
  {
    "id": "TC_02",
    "action": "validate_response",
    "url_pattern": "agentSlot",
    "expected_status": 200
  },
  {
    "id": "SAVE",
    "action": "capture_traffic",
    "capture_file": "Reports/post-install-traffic.json",
    "continueOnFailure": true
  },
  {
    "id": "UNLOCK",
    "action": "traffic_unlock",
    "server_port": 8383,
    "firewall_block_folder": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent",
    "continueOnFailure": true
  },
  {
    "id": "STOP",
    "action": "stop_proxy"
  },
  {
    "id": "REMOVE_CERT",
    "action": "install_cert",
    "cert_action": "remove",
    "continueOnFailure": true
  }
]
```

---

### 14.9 Verify Request Body Contains Expected Data

**Goal:** Confirm a POST request to `endpointdlp` includes the expected `ResourceId` field.

```json
[
  {
    "id": "LOAD",
    "action": "load_traffic",
    "capture_file": "Reports/recording.json"
  },
  {
    "id": "CHECK_BODY",
    "action": "validate_request",
    "url_pattern": "endpointdlp",
    "body_pattern": "ResourceId",
    "validation_type": "contains",
    "description": "endpointdlp body should contain ResourceId"
  },
  {
    "id": "CHECK_CONTENT_TYPE",
    "action": "validate_request",
    "url_pattern": "endpointdlp",
    "header_name": "Content-Type",
    "expected_value": "application/json",
    "validation_type": "contains",
    "description": "Content-Type should be JSON"
  }
]
```

> **Tip:** This works on already-recorded traffic (offline). No proxy or traffic lock needed.

---

### 14.10 Verify Request Ordering

**Goal:** Ensure the agent contacts `agentSlot` first, then gets `meta-data.xml`, then `agent-settings.xml`.

```json
[
  {
    "id": "LOAD",
    "action": "load_traffic",
    "capture_file": "Reports/recording.json"
  },
  {
    "id": "CHECK_ORDER",
    "action": "assert_request_order",
    "expected_order": [
      "agentSlot",
      "meta-data\\.xml",
      "agent-settings\\.xml"
    ],
    "description": "Requests should arrive in this order"
  }
]
```

---

### Scenario Cheat Sheet

| I want to... | Scenario | Key actions |
|---|---|---|
| Capture traffic for the first time | [14.1](#141-quick-start--your-first-traffic-capture) | `install_cert` → `start_proxy` → `traffic_lock` → `wait_for_request` → `capture_traffic` |
| Capture install-time traffic | [14.2](#142-fresh-agent-installation--capture-setup-traffic) | Same but omit `firewall_block_folder`, use `wait_for_interrupt` |
| Check if headers are correct | [14.3](#143-validate-agent-sends-correct-headers) | `validate_request` with `header_name` + `validation_type` |
| Test server failure handling | [14.4](#144-simulate-server-down--test-agent-error-handling) | `modify_response` with `inject_status: 500` |
| Block one specific request | [14.5](#145-block-a-specific-request) | `block_request` with `block_action: "block"` |
| Simulate slow network | [14.6](#146-add-network-latency-to-all-requests) | `block_request` with `block_action: "delay"` |
| Record now, validate later | [14.7](#147-record-traffic-for-later-analysis) | `wait_for_interrupt` to record, `load_traffic` to replay |
| Test install + post-install | [14.8](#148-two-phase-test--install--post-install-validation) | Two JSON files — Phase 1 (no firewall) + Phase 2 (with firewall) |
| Check request body content | [14.9](#149-verify-request-body-contains-expected-data) | `validate_request` with `body_pattern` |
| Verify request order | [14.10](#1410-verify-request-ordering) | `assert_request_order` with `expected_order` list |

---

### Before You Start — Checklist

- [ ] **Run as Administrator** — Required for certificate install, hosts file, firewall, and port proxy
- [ ] **Know your server hostname** — Check agent config or `ServerInfo.props`
- [ ] **Know your server IP** — Run `nslookup <hostname>` or check agent registry
- [ ] **Port 8080 is free** — Run `netstat -ano | findstr :8080` to check; pick a different port if occupied
- [ ] **Replace placeholder values** — Every example uses `myserver` / `10.0.0.50` — swap these with your actual values

---

## Quick Navigation Guide

**New here? Start with:** [Section 14 — Common Scenarios](#14-common-scenarios--how-to-guide) for step-by-step examples with explanations

**Getting Started:** Refer to [Section 1](#1-supported-actions), [Section 2](#2-json-command-structure), [Section 6](#6-quick-examples)

**Setting Up Proxy:** Refer to [Section 3](#3-proxy-modes), [Section 11.1](#111-start_proxy--start-mitmproxy), [Section 11.3](#113-install_cert--remove_certificate--manage-ca-certificate)

**Traffic Lock/Unlock:** Refer to [Section 5](#5-traffic-flow), [Section 11.17](#1117-traffic_lock--redirect-all-agent-traffic-through-proxy), [Section 11.18](#1118-traffic_unlock--restore-direct-traffic-flow)

**Multi-Phase Testing:** Refer to [Section 7.2](#72-multi-phase-test-using-switch_capture_file), [Section 11.20](#1120-switch_capture_file--switch-live-capture-file)

**Writing Validations:** Refer to [Section 4](#4-validation-types), [Section 11.9](#119-validate_request--assert-request-properties), [Section 11.10](#1110-validate_response--assert-response-properties)

**For Testing:** Refer to [Section 7](#7-complete-test-examples), [Section 8](#8-command-line-usage), [Section 9](#9-output-format)

**Failure Simulation:** Refer to [Section 11.15](#1115-modify_response--injectalter-responses), [Section 11.16](#1116-block_request--block-or-delay-requests), [Section 14.4](#144-simulate-server-down--test-agent-error-handling), [Section 14.5](#145-block-a-specific-request)

**For Troubleshooting:** Jump to [Section 13](#13-error-handling), [Section 12](#12-best-practices)

---


