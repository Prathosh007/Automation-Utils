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

---

## 1. Supported Actions
> All available communication actions you can perform, such as start/stop proxy, validate traffic, modify responses, and manage services.

| # | Action | Description | Example |
|---|--------|-------------|---------|
| 1.1 | `start_proxy` | Start mitmproxy on host:port | Start proxy on 127.0.0.1:8080 |
| 1.2 | `stop_proxy` | Stop the running proxy | Stop and clean up proxy process |
| 1.3 | `install_cert` | Install mitmproxy CA cert | Install CA into Windows trust store |
| 1.3b | `remove_certificate` | Remove mitmproxy CA cert | Remove CA from Windows trust store |
| 1.4 | `load_client_cert` | Load agent mTLS certs from vault | Load PEM/DER certs for mTLS |
| 1.5 | `configure_proxy` | Enable/disable Windows system proxy | Set system-level proxy via netsh |
| 1.6 | `wait_for_request` | Block until matching request arrives | Wait for agentSlot request |
| 1.7 | `wait_for_interrupt` | Block until Ctrl+C (recording mode) | Record traffic until user stops |
| 1.8 | `load_traffic` | Load traffic from JSON file | Load captured traffic for offline validation |
| 1.9 | `validate_request` | Assert request URL, headers, body | Verify GET /agentSlot with Auth header |
| 1.10 | `validate_response` | Assert response status, headers, body | Verify 200 OK with XML Content-Type |
| 1.11 | `assert_request_count` | Verify captured request count | At least 3 requests captured |
| 1.12 | `assert_request_order` | Verify request sequence ordering | agentSlot before meta-data.xml |
| 1.13 | `capture_traffic` | Dump traffic to JSON file | Save traffic to Reports/ folder |
| 1.14 | `clear_captures` | Reset traffic buffer | Clear all captured flows |
| 1.15 | `modify_response` | Inject/alter responses | Return 500 on meta-data.xml |
| 1.16 | `block_request` | Block or delay requests | Block agentSlot, delay 5 seconds |

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
UEMS Agent  --->  mitmproxy (background process)  --->  Real Server (HTTPS)
                        |
              Captures all traffic to live JSON file
              (Reports/live-capture.json)
                        |
              Test engine reads via file-polling
              (wait_for_request / load_traffic)
                        |
              Validates requests & responses
              (validate_request / validate_response / assert_*)
```

### Three-Layer OS Traffic Lock

The agent reads the real server address from the `meta-data.xml` response and reconnects directly. Three OS-level locks prevent this:

| # | Layer | Mechanism | Purpose |
|---|-------|-----------|---------|
| 5.1 | Hosts file | `127.0.0.1 <hostname>` | Redirect hostname to localhost |
| 5.2 | Port proxy | `127.0.0.1:8383 → 127.0.0.1:8080` | Forward agent's port to proxy |
| 5.3 | Firewall | Block outbound to real server IP | Block direct IP connections |

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
  "operation_type": "communication_operation",
  "parameters": {
    "action": "install_cert",
    "description": "Install mitmproxy CA certificate"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"install_cert"` |
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
    "captureFile": "Reports/live-capture.json",
    "description": "Reset traffic buffer and capture file"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | Action to perform — `"clear_captures"` |
| `captureFile` | Optional | Path to capture file on disk to clear (writes empty `[]`) |
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

---

## 7. Complete Test Example
> A full end-to-end workflow demonstrating setup, traffic capture, validation, and cleanup.

This is a full workflow demonstrating a typical agent traffic capture and validation scenario:

```json
[
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "install_cert",
      "description": "Install mitmproxy CA certificate"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "start_proxy",
      "listenPort": 8080,
      "mode": "reverse:https://real-server.example.com:443",
      "sslInsecure": true,
      "autoLoadCert": true,
      "liveCaptureFile": "Reports/live-capture.json",
      "description": "Start reverse proxy"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "wait_for_request",
      "urlPattern": "agentSlot",
      "timeout": 120,
      "captureFile": "Reports/live-capture.json",
      "description": "Wait for agentSlot request"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "validate_request",
      "urlPattern": "agentSlot",
      "method": "GET",
      "description": "Verify agentSlot uses GET method"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "validate_request",
      "urlPattern": "agentSlot",
      "headerName": "Authorization",
      "expectedValue": "Basic ",
      "validationType": "startswith",
      "description": "Verify Basic Auth header present"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "validate_response",
      "urlPattern": "agentSlot",
      "expectedStatus": 200,
      "description": "Verify agentSlot returns 200"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "validate_response",
      "urlPattern": "meta-data\\.xml",
      "headerName": "Content-Type",
      "expectedValue": "application/xml",
      "validationType": "exact",
      "description": "Verify meta-data.xml Content-Type"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "assert_request_count",
      "urlPattern": "agentSlot",
      "minCount": 1,
      "description": "At least 1 agentSlot request"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "assert_request_order",
      "expectedOrder": ["agentSlot", "meta-data\\.xml", "agent-settings\\.xml"],
      "description": "Verify request ordering"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "capture_traffic",
      "captureFile": "Reports/validation-traffic.json",
      "description": "Save captured traffic"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "stop_proxy",
      "description": "Stop proxy"
    }
  },
  {
    "operation_type": "communication_operation",
    "parameters": {
      "action": "remove_certificate",
      "description": "Remove CA certificate"
    }
  }
]
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

### 11.3 install_cert / remove_certificate — Manage CA certificate

> Install or remove the mitmproxy CA certificate from the Windows trust store.

**`install_cert`** — Installs the CA certificate into the Windows trust store.
**`remove_certificate`** — Removes the CA certificate from the Windows trust store.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `certPath` | String | No | `""` (auto-detect) | Path to certificate file |

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

> Clear all captured traffic from memory and optionally from the capture file on disk.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `captureFile` | String | No | `""` | Path to capture file to clear on disk (writes empty `[]`) |

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
| 13.6 | Traffic file empty | Agent hasn't sent requests yet | Wait longer or trigger agent |
| 13.9 | mTLS cert conversion failed | Proprietary/encrypted vault format | Proxy continues without mTLS; manual cert conversion needed |
| 13.7 | Capture file not found | Wrong path to traffic JSON | Verify file path; check `Reports/` folder |
| 13.12 | Validation type mismatch | Wrong `validationType` used | Use: `contains`, `exact`, `startswith`, `endswith`, `regex` |

---

## Quick Navigation Guide

**Getting Started:** Refer to [Section 1](#1-supported-actions), [Section 2](#2-json-command-structure), [Section 6](#6-quick-examples)

**Setting Up Proxy:** Refer to [Section 3](#3-proxy-modes), [Section 11.1](#111-start_proxy--start-mitmproxy), [Section 11.3](#113-install_cert--remove_certificate--manage-ca-certificate)

**Writing Validations:** Refer to [Section 4](#4-validation-types), [Section 11.12](#1112-validate_request--assert-request-properties), [Section 11.13](#1113-validate_response--assert-response-properties)

**For Testing:** Refer to [Section 7](#7-complete-test-example), [Section 8](#8-command-line-usage), [Section 9](#9-output-format)

**Failure Simulation:** Refer to [Section 11.18](#1118-modify_response--injectalter-responses), [Section 11.19](#1119-block_request--block-or-delay-requests)

**For Troubleshooting:** Jump to [Section 13](#13-error-handling), [Section 12](#12-best-practices)

---

