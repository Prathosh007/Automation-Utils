# Writing Test JSON Files — Complete Reference

This guide covers everything needed to write test JSON files for CommunicationAutomation. Each JSON file defines a complete test scenario as a sequence of commands executed top-to-bottom.

---

## Table of Contents

- [JSON Structure](#json-structure)
- [Command Format](#command-format)
- [Common Fields](#common-fields)
- [Variable Substitution](#variable-substitution)
- [Action Reference](#action-reference)
  - [start_proxy](#start_proxy)
  - [stop_proxy](#stop_proxy)
  - [configure_proxy](#configure_proxy)
  - [install_certificate](#install_certificate)
  - [load_client_cert](#load_client_cert)
  - [manage_service](#manage_service)
  - [run_command](#run_command)
  - [patch_registry](#patch_registry)
  - [load_traffic](#load_traffic)
  - [wait_for_request](#wait_for_request)
  - [wait_for_interrupt](#wait_for_interrupt)
  - [validate_request](#validate_request)
  - [validate_response](#validate_response)
  - [modify_response](#modify_response)
  - [block_request](#block_request)
  - [assert_request_count](#assert_request_count)
  - [assert_request_order](#assert_request_order)
  - [capture_traffic](#capture_traffic)
  - [clear_captures](#clear_captures)
- [Validation Types](#validation-types)
- [Test Flow Patterns](#test-flow-patterns)
- [Naming Conventions](#naming-conventions)
- [Machine-Specific Values](#machine-specific-values)
- [Complete Examples](#complete-examples)
- [Troubleshooting](#troubleshooting)

---

## JSON Structure

A test file is either a **flat array** of commands or a **wrapped object**:

```json
// Flat array (recommended)
[
    {"id": "STEP_001", "action": "start_proxy", ...},
    {"id": "STEP_002", "action": "validate_request", ...}
]

// Wrapped object
{
    "commands": [
        {"id": "STEP_001", "action": "start_proxy", ...},
        {"id": "STEP_002", "action": "validate_request", ...}
    ]
}
```

Commands execute **sequentially, top-to-bottom**. If any command fails and `continueOnFailure` is not `true`, execution **stops immediately** — remaining commands (including cleanup) will not run.

---

## Command Format

Every command is a JSON object with at minimum `action`. All fields use **camelCase** in JSON.

```json
{
    "id": "TC_001",
    "action": "validate_request",
    "urlPattern": "agentSlot",
    "method": "GET",
    "continueOnFailure": true,
    "description": "Verify agentSlot is a GET request"
}
```

### Field Name Mapping (JSON → Python)

JSON files use **camelCase**. The engine maps them to Python **snake_case** automatically. Both forms are accepted, but **use camelCase** in JSON for consistency.

| JSON (camelCase) | Python (snake_case) |
|------------------|---------------------|
| `continueOnFailure` | `continue_on_failure` |
| `listenHost` | `listen_host` |
| `listenPort` | `listen_port` |
| `upstreamProxy` | `upstream_proxy` |
| `sslInsecure` | `ssl_insecure` |
| `certAction` | `cert_action` |
| `certPath` | `cert_path` |
| `clientCertPath` | `client_cert_path` |
| `clientKeyPath` | `client_key_path` |
| `serviceName` | `service_name` |
| `serviceAction` | `service_action` |
| `commandLine` | `command_line` |
| `workingDir` | `working_dir` |
| `registryKey` | `registry_key` |
| `registryAction` | `registry_action` |
| `oldValues` | `old_values` |
| `newValues` | `new_values` |
| `proxyAction` | `proxy_action` |
| `proxyHost` | `proxy_host` |
| `proxyPort` | `proxy_port` |
| `bypassList` | `bypass_list` |
| `urlPattern` | `url_pattern` |
| `headerName` | `header_name` |
| `bodyPattern` | `body_pattern` |
| `expectedValue` | `expected_value` |
| `expectedStatus` | `expected_status` |
| `validationType` | `validation_type` |
| `caseSensitive` | `case_sensitive` |
| `minCount` | `min_count` |
| `maxCount` | `max_count` |
| `expectedCount` | `expected_count` |
| `expectedOrder` | `expected_order` |
| `injectStatus` | `inject_status` |
| `injectBody` | `inject_body` |
| `injectHeaders` | `inject_headers` |
| `blockAction` | `block_action` |
| `delayMs` | `delay_ms` |
| `captureFile` | `capture_file` |
| `captureFormat` | `capture_format` |
| `autoLoadCert` | `auto_load_cert` |
| `liveCaptureFile` | `live_capture_file` |

---

## Common Fields

These fields work on **every** command:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | `""` | Unique step identifier. Used in output, logging, and variable substitution. **Always set this.** |
| `action` | string | — | **Required.** The action to execute (case-insensitive). |
| `description` | string | `""` | Human-readable description. Appears in logs and output remarks. |
| `continueOnFailure` | boolean | `false` | If `true`, test continues even if this step fails. **Critical for cleanup steps.** |
| `timeout` | integer | varies | Timeout in seconds. Meaning depends on the action. |

### When to use `continueOnFailure: true`

| Scenario | Use? | Why |
|----------|------|-----|
| Setup steps (hosts file, firewall, port proxy) | ✅ Yes | These are secondary; failure shouldn't block the whole test |
| Cleanup steps | ✅ **Always** | Cleanup must run regardless; you don't want to leave system dirty |
| Test case assertions that must not block others | ✅ Yes | Run all TCs even if one fails |
| Critical setup (start_proxy, patch_registry, install_cert) | ❌ No | If these fail, nothing downstream will work |
| Wait steps that are required for test cases | ❌ No | If the request never arrives, validations will fail anyway |

---

## Variable Substitution

After each command executes, its result is stored. Subsequent commands can reference it using `{{stepId.property}}` in any string field.

### Available Properties

| Variable | Type | Description |
|----------|------|-------------|
| `{{STEP_ID.success}}` | `"True"` / `"False"` | Whether the step succeeded |
| `{{STEP_ID.message}}` | string | Result message |
| `{{STEP_ID.error}}` | string | Error message (only if failed) |
| `{{STEP_ID.data}}` | string | Action-specific data (dict-as-string, file path, count, etc.) |
| `{{STEP_ID.duration}}` | string | Execution duration |
| `{{STEP_ID.commandid}}` | string | The command ID itself |
| `{{STEP_ID.action}}` | string | The action name |

### Example: Using step results in later steps

```json
[
    {"id": "FIND_SVC", "action": "manage_service", "serviceName": "UEMS", "serviceAction": "find"},
    {"id": "STOP_SVC", "action": "manage_service", "serviceName": "{{FIND_SVC.data}}", "serviceAction": "stop"}
]
```

### Rules

- Substitution happens **before** the command is parsed — it works in any string field
- Unresolved variables (e.g., referencing a step that hasn't run) are left as-is: `{{UNKNOWN.data}}`
- Lookup is **case-insensitive** as a fallback
- Non-string fields (integers, booleans, arrays) are **not** substituted

---

## Action Reference

### start_proxy

Spawns a **detached background process** running `--serve` mode. The background process writes captured traffic to a live JSON file in real-time. Use `stop_proxy` to terminate it. Must be called before any traffic-related actions.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `listenHost` | string | `"127.0.0.1"` | IP address to listen on |
| `listenPort` | integer | `8080` | Port to listen on. **Required > 0.** |
| `mode` | string | `""` | Proxy mode. Use `"reverse:https://SERVER_IP:PORT"` for reverse proxy |
| `sslInsecure` | boolean | `false` | Skip upstream SSL certificate verification |
| `upstreamProxy` | string | `""` | Chain through another proxy (e.g., `"http://proxy:3128"`) |
| `autoLoadCert` | boolean | `false` | Auto-detect CSR servlet responses and load agent client certs from vault for mTLS |
| `liveCaptureFile` | string | `"Reports/live-capture.json"` | Path for real-time traffic capture file (used by `wait_for_request` file-polling) |

**Stores in context:** `background_proxy_pid`
**Result data:** `{"pid": <int>, "live_capture_file": "<path>"}`
**PID file:** `Reports/proxy.pid`
**Logs:** `Reports/proxy.log` (structured), `Reports/proxy_stdout.log` (stdout/stderr)

```json
{"id": "SETUP_001", "action": "start_proxy",
 "listenHost": "127.0.0.1", "listenPort": 8080,
 "mode": "reverse:https://10.69.73.19:8383",
 "sslInsecure": true,
 "description": "Start reverse proxy forwarding to real server"}
```

```json
{"id": "SETUP_002", "action": "start_proxy",
 "listenHost": "0.0.0.0", "listenPort": 8080,
 "mode": "reverse:https://172.24.148.221:8383",
 "sslInsecure": true,
 "autoLoadCert": true,
 "liveCaptureFile": "Reports/csr-capture-traffic.json",
 "description": "Start reverse proxy with CSR auto-cert-loading"}
```

**Modes:**
- **Reverse proxy** (most common): `"mode": "reverse:https://SERVER_IP:PORT"` — all traffic is forwarded to the specified server
- **Regular proxy** (no mode): acts as a forward HTTP/S proxy — clients must be configured to use it
- **Upstream proxy**: combine with `upstreamProxy` to chain through a corporate proxy

> If `start_proxy` is called while a background proxy is already running (PID file exists and process alive), it will **fail** instead of starting a duplicate.

---

### stop_proxy

Stops the background proxy process. Uses a multi-attempt kill strategy:

1. **In-process** — If a `proxy_manager` exists in context (legacy in-process mode), calls `pm.stop()`
2. **PID file** — Reads `Reports/proxy.pid` to find the background process
3. **Context PID** — Falls back to `context["background_proxy_pid"]`

**Kill strategy (Windows):**
1. `taskkill /F /T /PID` — primary, kills entire process tree
2. `CTRL_BREAK_EVENT` signal — fallback
3. `SIGTERM` — calls `TerminateProcess()` on Windows
4. Final `taskkill` retry if still alive
5. **Verifies** the process is dead — reports failure if it survives

Cleans up `Reports/proxy.pid` after stopping.

```json
{"id": "CLEANUP_LAST", "action": "stop_proxy", "description": "Stop proxy"}
```

No additional fields. Succeeds if proxy is already stopped.

---

### load_traffic

Load previously captured traffic from a JSON file into the traffic store. This allows validation actions (`validate_request`, `validate_response`, `assert_*`) to run in a **separate execution** from the capture session.

If no traffic store exists in context (proxy not running), one is created automatically.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `captureFile` | string | — | **Required.** Path to the JSON capture file to load |

**Result data:** `{"file": "<path>", "loaded": <int>, "total": <int>}`

```json
{"id": "LOAD_001", "action": "load_traffic",
 "captureFile": "Reports/csr-capture-traffic.json",
 "description": "Load captured CSR traffic for validation"}
```

The capture file format matches what `capture_traffic` and the live capture file produce — a JSON array of flow objects. Malformed entries are skipped with a log warning.

**Use cases:**
- Validate traffic from a live capture file written by a background proxy
- Re-run validation against previously captured traffic
- Run capture and validation as separate test executions

---

### configure_proxy

Enables or disables the **Windows system proxy** (via `netsh winhttp`). This is separate from the MITM proxy itself.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `proxyAction` | string | — | **Required:** `"enable"` or `"disable"` |
| `proxyHost` | string | `"127.0.0.1"` | Proxy host for system settings |
| `proxyPort` | integer | `8080` | Proxy port for system settings |
| `bypassList` | string | `""` | Semicolon-separated bypass list |

```json
{"id": "SYS_001", "action": "configure_proxy",
 "proxyAction": "enable", "proxyHost": "127.0.0.1", "proxyPort": 8080,
 "bypassList": "localhost;*.local",
 "description": "Enable Windows system proxy"}
```

---

### install_certificate

Installs or removes the mitmproxy CA certificate from the Windows trust store. Required for HTTPS interception.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `certAction` | string | — | **Required:** `"install"` or `"remove"` |
| `certPath` | string | `""` | Path to certificate file (uses mitmproxy default if empty) |

```json
{"id": "CERT_001", "action": "install_certificate", "certAction": "install",
 "description": "Install mitmproxy CA cert"}
```

```json
{"id": "CERT_002", "action": "install_certificate", "certAction": "remove",
 "description": "Remove mitmproxy CA cert"}
```

---

### load_client_cert

Loads the UEMS agent's client certificate and private key from the vault for mTLS forwarding. Call **before** `start_proxy`.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `clientCertPath` | string | Agent vault default | Path to client certificate |
| `clientKeyPath` | string | Agent vault default | Path to private key |

```json
{"id": "CERT_003", "action": "load_client_cert",
 "description": "Load agent mTLS cert from vault"}
```

Default paths point to `C:\Program Files (x86)\ManageEngine\UEMS_Agent\vault\ME_UEMS\storage\`. The action auto-detects PEM, DER, hex-encoded, and base64 formats.

> **Note:** Agent vault files are in a proprietary encrypted format. The action succeeds with a warning if format conversion fails, so the proxy can still start without mTLS forwarding.

---

### manage_service

Start, stop, restart, query status, or find a Windows service.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `serviceName` | string | — | **Required.** Service name or keyword (e.g., `"UEMS"`, `"ManageEngine"`) |
| `serviceAction` | string | — | **Required:** `"start"`, `"stop"`, `"restart"`, `"status"`, or `"find"` |

```json
{"id": "SVC_001", "action": "manage_service",
 "serviceName": "ManageEngine UEMS - Agent",
 "serviceAction": "restart",
 "description": "Restart UEMS agent service"}
```

**Auto-discovery:** If the exact service name isn't found, it searches all services for keyword matches (UEMS, ManageEngine, DesktopCentral, Zoho), preferring services with "agent" in the name.

```json
// Find service first, then use the resolved name
{"id": "FIND_SVC", "action": "manage_service", "serviceName": "UEMS", "serviceAction": "find"},
{"id": "STOP_SVC", "action": "manage_service", "serviceName": "{{FIND_SVC.data}}", "serviceAction": "stop"}
```

---

### run_command

Run any shell command. Used for OS-level setup/cleanup (hosts file, firewall, port proxy, cfgupdate, etc.).

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `commandLine` | string | — | **Required.** Command to execute (runs in `cmd /c` shell) |
| `timeout` | integer | `60` | Max seconds to wait for command to finish |
| `workingDir` | string | `""` | Working directory (optional) |

**Result data:** `{"exit_code": 0, "output": "..."}`

```json
// Trigger agent config refresh
{"id": "TRIGGER", "action": "run_command",
 "commandLine": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
 "timeout": 30, "continueOnFailure": true,
 "description": "Trigger cfgupdate"}

// Add hosts file entry
{"id": "HOSTS", "action": "run_command",
 "commandLine": "powershell -Command \"Add-Content 'C:\\Windows\\System32\\drivers\\etc\\hosts' '`n127.0.0.1 server-hostname'\"",
 "continueOnFailure": true,
 "description": "Redirect server hostname via hosts file"}

// Add firewall block rule
{"id": "FW", "action": "run_command",
 "commandLine": "netsh advfirewall firewall add rule name=\"BlockUEMS\" dir=out action=block protocol=TCP remoteip=10.69.73.19 remoteport=8383",
 "continueOnFailure": true,
 "description": "Block direct server access"}

// Add port proxy
{"id": "PORTPROXY", "action": "run_command",
 "commandLine": "netsh interface portproxy add v4tov4 listenport=8383 listenaddress=127.0.0.1 connectport=8080 connectaddress=127.0.0.1",
 "continueOnFailure": true,
 "description": "Forward agent port to proxy"}

// Flush DNS
{"id": "DNS", "action": "run_command",
 "commandLine": "ipconfig /flushdns",
 "continueOnFailure": true}
```

---

### patch_registry

Bulk find-and-replace on all string values under a Windows registry key. The agent stores its server info in the registry; this patches it to redirect to the proxy.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `registryKey` | string | — | **Required.** Full registry key path |
| `registryAction` | string | — | **Required:** `"patch"` or `"restore"` |
| `oldValues` | array | `[]` | Strings to find (patch mode only) |
| `newValues` | array | `[]` | Replacement strings (same length as oldValues) |

**Patch** replaces every occurrence of each `oldValues[i]` with `newValues[i]` across all `REG_SZ`/`REG_EXPAND_SZ` values under the key. **Restore** reverts to original values (stored automatically during patch).

```json
// Patch: redirect agent from real server to proxy
{"id": "REG_PATCH", "action": "patch_registry",
 "registryKey": "HKLM\\SOFTWARE\\WOW6432Node\\AdventNet\\DesktopCentral\\DCAgent\\ServerInfo",
 "registryAction": "patch",
 "oldValues": ["server-hostname", "10.69.73.19", "8383"],
 "newValues": ["127.0.0.1", "127.0.0.1", "8080"],
 "description": "Redirect agent to proxy"}

// Restore: revert to original values
{"id": "REG_RESTORE", "action": "patch_registry",
 "registryKey": "HKLM\\SOFTWARE\\WOW6432Node\\AdventNet\\DesktopCentral\\DCAgent\\ServerInfo",
 "registryAction": "restore", "continueOnFailure": true,
 "description": "Restore original registry values"}
```

**Important:**
- `oldValues` and `newValues` must be the **same length** (each pair is a find→replace)
- Replacement is **case-insensitive**
- Only `REG_SZ` and `REG_EXPAND_SZ` values are patched
- Uses the 32-bit registry view (`KEY_WOW64_32KEY`)
- Supports `HKLM`, `HKEY_LOCAL_MACHINE`, `HKCU`, `HKEY_CURRENT_USER` prefixes

---

### wait_for_request

Blocks execution until a matching HTTP request arrives. Works in two modes:

1. **In-process** — Polls the in-memory `TrafficStore` (when proxy runs in the same process)
2. **File-polling** — If `captureFile` is set *or* no in-memory store exists, periodically re-reads the live capture JSON written by the background proxy. Once found, **all** flows are loaded into `context["traffic_store"]` so subsequent `validate_request` / `validate_response` / `assert_*` actions work transparently.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `urlPattern` | string | — | **Required.** Regex pattern to match the request URL |
| `method` | string | `""` | Optional HTTP method filter (GET, POST, etc.) |
| `timeout` | integer | `60` | Max seconds to wait |
| `captureFile` | string | `""` | Live capture JSON file to poll (enables file-polling mode) |

```json
{"id": "WAIT_001", "action": "wait_for_request",
 "urlPattern": "agentSlot", "timeout": 180,
 "description": "Wait for agentSlot (up to 3 min)"}

{"id": "WAIT_002", "action": "wait_for_request",
 "urlPattern": "meta-data\\.xml", "timeout": 60,
 "continueOnFailure": true,
 "description": "Wait for meta-data.xml"}

{"id": "WAIT_003", "action": "wait_for_request",
 "urlPattern": "agentSlot", "timeout": 300,
 "captureFile": "Reports/csr-capture-traffic.json",
 "description": "Wait for agentSlot in live capture file (file-polling mode)"}
```

**Tip:** Set `timeout` generously. After cfgupdate, traffic usually appears within 1-3 minutes, but can take longer.

**File-polling details:** Polls every 3 seconds. Only scans new flows since last poll for efficiency. On match, loads all flows into the traffic store for subsequent validation.

---

### wait_for_interrupt

Blocks indefinitely until the user presses **Ctrl+C**. Used for interactive recording sessions.

```json
{"id": "RECORD", "action": "wait_for_interrupt",
 "description": "Recording traffic... Press Ctrl+C to stop"}
```

No additional fields. Prints a progress line every 10 seconds showing captured request count. Always succeeds on interrupt so cleanup steps run.

---

### validate_request

Assert properties of a **captured HTTP request**. The most-used test action.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `urlPattern` | string | — | **Required.** Regex to find the matching request |
| `method` | string | `""` | Optional method filter (also acts as assertion) |
| `headerName` | string | `""` | Request header to validate |
| `expectedValue` | string | `""` | Expected value for header or URL |
| `bodyPattern` | string | `""` | Pattern to match in request body |
| `validationType` | string | `"contains"` | How to compare (see [Validation Types](#validation-types)) |
| `caseSensitive` | boolean | `false` | Case-sensitive comparison |

**What gets validated depends on which fields you set:**

| Fields Set | What's Checked |
|------------|----------------|
| `urlPattern` + `method` only | Request exists with that method ✅ |
| `headerName` + `expectedValue` | The specified request header matches the expected value |
| `bodyPattern` | Request body matches the pattern |
| `expectedValue` (no `headerName`) | The request URL matches the expected value |
| None of the above | Request simply exists |

#### Examples

```json
// Check method
{"id": "TC_001", "action": "validate_request",
 "urlPattern": "agentSlot", "method": "GET",
 "description": "agentSlot: method is GET"}

// Check header value
{"id": "TC_002", "action": "validate_request",
 "urlPattern": "agentSlot",
 "headerName": "Authorization", "expectedValue": "Basic ",
 "validationType": "startswith",
 "description": "agentSlot: has Basic auth"}

// Check header exists (non-empty)
{"id": "TC_003", "action": "validate_request",
 "urlPattern": "meta-data\\.xml",
 "headerName": "If-Modified-Since", "expectedValue": "",
 "validationType": "notempty",
 "description": "meta-data.xml: If-Modified-Since header present"}

// Check header exact value (case-sensitive)
{"id": "TC_004", "action": "validate_request",
 "urlPattern": "agentSlot",
 "headerName": "User-Agent", "expectedValue": "DesktopCentral Agent",
 "validationType": "exact", "caseSensitive": true,
 "description": "agentSlot: User-Agent is exact"}

// Check URL contains a parameter
{"id": "TC_005", "action": "validate_request",
 "urlPattern": "agentSlot",
 "expectedValue": "actionToCall=AgentOfset",
 "validationType": "contains",
 "description": "agentSlot: URL has actionToCall param"}

// Check URL with regex
{"id": "TC_006", "action": "validate_request",
 "urlPattern": "agentSlot",
 "expectedValue": "resourceId=\\d+",
 "validationType": "regex",
 "description": "agentSlot: resourceId is numeric"}

// Check request body
{"id": "TC_007", "action": "validate_request",
 "urlPattern": "endpointdlp",
 "bodyPattern": "ResourceId",
 "validationType": "contains",
 "description": "endpointdlp: body contains ResourceId"}

// Check body with regex
{"id": "TC_008", "action": "validate_request",
 "urlPattern": "endpointdlp",
 "bodyPattern": "ImmediatePostCount.*\\d+",
 "validationType": "regex",
 "description": "endpointdlp: ImmediatePostCount has numeric value"}
```

---

### validate_response

Assert properties of a **captured HTTP response**.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `urlPattern` | string | — | **Required.** Regex to find the matching request/response |
| `method` | string | `""` | Optional method filter |
| `expectedStatus` | integer | `0` | Expected HTTP status code (200, 404, etc.) |
| `headerName` | string | `""` | Response header to validate |
| `expectedValue` | string | `""` | Expected value for header or body |
| `bodyPattern` | string | `""` | Pattern to match in response body |
| `validationType` | string | `"contains"` | How to compare |
| `caseSensitive` | boolean | `false` | Case-sensitive comparison |

**Validation priority:**
1. `expectedStatus` is always checked first (if > 0) — **but does not prevent further checks**
2. `headerName` + `expectedValue` → validate response header → return
3. `bodyPattern` or `expectedValue` (no `headerName`) → validate response body
4. If only `expectedStatus` was set, returns after status check

#### Examples

```json
// Check status code
{"id": "TC_010", "action": "validate_response",
 "urlPattern": "agentSlot", "expectedStatus": 200,
 "description": "agentSlot: response 200 OK"}

// Check response header
{"id": "TC_011", "action": "validate_response",
 "urlPattern": "meta-data\\.xml",
 "headerName": "Content-Type", "expectedValue": "application/xml",
 "validationType": "exact",
 "description": "meta-data.xml: Content-Type is XML"}

// Check response body contains string
{"id": "TC_012", "action": "validate_response",
 "urlPattern": "meta-data\\.xml",
 "bodyPattern": "DCServerInfo",
 "validationType": "contains",
 "description": "meta-data.xml: body has DCServerInfo"}

// Check body with regex
{"id": "TC_013", "action": "validate_response",
 "urlPattern": "meta-data\\.xml",
 "bodyPattern": "ProductCodes.*DCEE",
 "validationType": "regex",
 "description": "meta-data.xml: ProductCodes contains DCEE"}

// Status + body check (both are validated)
{"id": "TC_014", "action": "validate_response",
 "urlPattern": "agentSlot", "expectedStatus": 200,
 "bodyPattern": "RESOURCE_ID", "validationType": "contains",
 "description": "agentSlot: 200 OK and body has RESOURCE_ID"}

// Verify a 404 is returned
{"id": "TC_015", "action": "validate_response",
 "urlPattern": "boundary-list\\.xml", "expectedStatus": 404,
 "continueOnFailure": true,
 "description": "boundary-list.xml: expected 404"}
```

---

### modify_response

Add a response injection rule. When the proxy sees a matching request, it alters the response before sending it to the client.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `urlPattern` | string | — | **Required.** Regex to match request URL |
| `method` | string | `""` | Optional method filter |
| `injectStatus` | integer | `0` | Status code to inject (overrides real response) |
| `injectBody` | string | `""` | Response body to inject |
| `injectHeaders` | object | `{}` | Headers to inject/override `{"Header-Name": "value"}` |
| `persistent` | boolean | `false` | `true` = stays active; `false` = removed after first match |

> **Must call `start_proxy` first.** Rules only apply to traffic arriving **after** the rule is added.

```json
// Return 500 error on meta-data.xml
{"id": "INJECT_001", "action": "modify_response",
 "urlPattern": "meta-data\\.xml",
 "injectStatus": 500, "injectBody": "Internal Server Error",
 "persistent": true,
 "description": "Force 500 on meta-data.xml"}

// Inject custom JSON response
{"id": "INJECT_002", "action": "modify_response",
 "urlPattern": "agent-settings\\.xml",
 "injectStatus": 200,
 "injectBody": "{\"error\": \"test\"}",
 "injectHeaders": {"Content-Type": "application/json"},
 "persistent": true,
 "description": "Replace agent-settings with custom JSON"}

// One-shot: modify only the first matching request
{"id": "INJECT_003", "action": "modify_response",
 "urlPattern": "agentSlot",
 "injectStatus": 503,
 "persistent": false,
 "description": "Return 503 on first agentSlot only"}
```

---

### block_request

Block or delay requests matching a URL pattern.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `urlPattern` | string | — | **Required.** Regex to match request URL |
| `method` | string | `""` | Optional method filter |
| `blockAction` | string | `"block"` | `"block"` = drop request; anything else = delay mode |
| `delayMs` | integer | `0` | Milliseconds to delay (in delay mode) |
| `persistent` | boolean | `false` | Whether rule persists after first match |

```json
// Block all agentSlot requests
{"id": "BLOCK_001", "action": "block_request",
 "urlPattern": "agentSlot", "blockAction": "block",
 "persistent": true,
 "description": "Block all agentSlot requests"}

// Add 5-second delay to all requests
{"id": "DELAY_001", "action": "block_request",
 "urlPattern": ".*", "blockAction": "delay", "delayMs": 5000,
 "persistent": true,
 "description": "Delay all requests by 5 seconds"}
```

---

### assert_request_count

Assert that the number of captured requests matching a pattern meets count constraints.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `urlPattern` | string | — | **Required.** Regex to match request URLs |
| `method` | string | `""` | Optional method filter |
| `expectedCount` | integer | `-1` | Exact count expected (checked if ≥ 0) |
| `minCount` | integer | `0` | Minimum threshold (checked if > 0) |
| `maxCount` | integer | `0` | Maximum threshold (checked if > 0) |

You can combine `minCount` and `maxCount` for a range check. `expectedCount` is mutually exclusive (use one approach).

```json
// Exact count
{"id": "COUNT_001", "action": "assert_request_count",
 "urlPattern": "agentSlot", "expectedCount": 1,
 "description": "Exactly 1 agentSlot request"}

// Minimum threshold
{"id": "COUNT_002", "action": "assert_request_count",
 "urlPattern": ".*", "minCount": 10,
 "description": "At least 10 total requests"}

// Range
{"id": "COUNT_003", "action": "assert_request_count",
 "urlPattern": "applicationcontrol", "minCount": 1, "maxCount": 5,
 "description": "Between 1-5 applicationcontrol requests"}

// Zero requests (verify something was NOT requested)
{"id": "COUNT_004", "action": "assert_request_count",
 "urlPattern": "unwanted-endpoint", "expectedCount": 0,
 "description": "No requests to unwanted-endpoint"}
```

**Check order:** `expectedCount` → `minCount` → `maxCount`. First failure short-circuits.

---

### assert_request_order

Verify that captured requests matching a sequence of URL patterns arrived in the correct order.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `expectedOrder` | array | — | **Required.** List of URL regex patterns (minimum 2). Must appear in this order. |

```json
{"id": "ORDER_001", "action": "assert_request_order",
 "expectedOrder": ["agentSlot", "meta-data\\.xml", "agent-settings\\.xml"],
 "description": "Verify request sequence"}
```

**How it works:** For each pattern, finds the **first** occurrence in the captured flow list. Checks that these indices are strictly ascending.

---

### capture_traffic

Dump all captured HTTP traffic to a JSON file.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `captureFile` | string | `"Reports/traffic-capture.json"` | Output file path |

```json
{"id": "SAVE_001", "action": "capture_traffic",
 "captureFile": "Reports/my-test-traffic.json",
 "continueOnFailure": true,
 "description": "Save captured traffic"}
```

Creates parent directories automatically. Always use `continueOnFailure: true` for captures.

---

### clear_captures

Reset the traffic capture buffer. Call this **before** triggering agent activity to get a clean slate.

```json
{"id": "RESET", "action": "clear_captures",
 "description": "Reset capture buffer"}
```

No additional fields. Only clears the in-memory flow buffer. Does **not** clear response modification rules.

---

## Validation Types

Used with `validationType` in `validate_request` and `validate_response`:

| Type | Behavior | Example Match |
|------|----------|---------------|
| `contains` | Value contains expected substring (default) | `"Hello World"` contains `"World"` ✅ |
| `exact` | Full string equality | `"Hello"` == `"Hello"` ✅ |
| `startswith` | Starts with expected prefix | `"Basic abc123"` starts with `"Basic "` ✅ |
| `endswith` | Ends with expected suffix | `"image/png"` ends with `"/png"` ✅ |
| `regex` | Python regex pattern match | `"resourceId=42"` matches `"resourceId=\\d+"` ✅ |
| `notempty` | Value exists and is not empty | Header present with any value ✅ |
| `isempty` | Value is empty or missing | Header not present or blank ✅ |

### Case Sensitivity

- Default: `caseSensitive: false` — all comparisons are case-insensitive
- Set `caseSensitive: true` for exact case matching (e.g., `User-Agent` header value)

---

## Test Flow Patterns

### Standard Validation Test

The recommended pattern for a full validation test:

```
    SETUP         Environment preparation (cert, registry, proxy, OS locks, trigger)
       ↓
    WAIT          Wait for expected traffic to arrive
       ↓
    TEST CASES    Validate requests, responses, counts, order
       ↓
    SAVE          Capture traffic to file
       ↓
    CLEANUP       Reverse all OS changes, restore registry, stop proxy
```

### Minimal Template

```json
[
    // ─── SETUP ────────────────────────────────────────────────
    {"id": "SETUP_001", "action": "install_certificate", "certAction": "install"},
    {"id": "SETUP_002", "action": "patch_registry",
     "registryKey": "HKLM\\SOFTWARE\\WOW6432Node\\AdventNet\\DesktopCentral\\DCAgent\\ServerInfo",
     "registryAction": "patch",
     "oldValues": ["<SERVER_HOSTNAME>", "<SERVER_IP>", "8383"],
     "newValues": ["127.0.0.1", "127.0.0.1", "8080"]},
    {"id": "SETUP_003", "action": "start_proxy", "listenPort": 8080,
     "mode": "reverse:https://<SERVER_IP>:8383", "sslInsecure": true},
    {"id": "SETUP_004", "action": "run_command",
     "commandLine": "powershell -Command \"Add-Content 'C:\\Windows\\System32\\drivers\\etc\\hosts' '`n127.0.0.1 <SERVER_HOSTNAME>'\"",
     "continueOnFailure": true},
    {"id": "SETUP_005", "action": "run_command",
     "commandLine": "netsh interface portproxy add v4tov4 listenport=8383 listenaddress=127.0.0.1 connectport=8080 connectaddress=127.0.0.1",
     "continueOnFailure": true},
    {"id": "SETUP_006", "action": "run_command",
     "commandLine": "netsh advfirewall firewall add rule name=\"BlockUEMSDirectServer\" dir=out action=block protocol=TCP remoteip=<SERVER_IP> remoteport=8383",
     "continueOnFailure": true},
    {"id": "SETUP_007", "action": "run_command", "commandLine": "ipconfig /flushdns", "continueOnFailure": true},
    {"id": "SETUP_008", "action": "clear_captures"},
    {"id": "SETUP_009", "action": "run_command",
     "commandLine": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
     "timeout": 30, "continueOnFailure": true},

    // ─── WAIT ─────────────────────────────────────────────────
    {"id": "WAIT_001", "action": "wait_for_request", "urlPattern": "agentSlot", "timeout": 180},
    {"id": "WAIT_002", "action": "wait_for_request", "urlPattern": "meta-data\\.xml", "timeout": 60, "continueOnFailure": true},

    // ─── TEST CASES ───────────────────────────────────────────
    {"id": "TC_001", "action": "validate_request", "urlPattern": "agentSlot", "method": "GET", "continueOnFailure": true},
    {"id": "TC_002", "action": "validate_response", "urlPattern": "agentSlot", "expectedStatus": 200, "continueOnFailure": true},
    // ... add more TCs here ...

    // ─── SAVE ─────────────────────────────────────────────────
    {"id": "SAVE_001", "action": "capture_traffic", "captureFile": "Reports/test-traffic.json", "continueOnFailure": true},

    // ─── CLEANUP ──────────────────────────────────────────────
    {"id": "CLEANUP_001", "action": "run_command",
     "commandLine": "netsh advfirewall firewall delete rule name=\"BlockUEMSDirectServer\"", "continueOnFailure": true},
    {"id": "CLEANUP_002", "action": "run_command",
     "commandLine": "netsh interface portproxy delete v4tov4 listenport=8383 listenaddress=127.0.0.1", "continueOnFailure": true},
    {"id": "CLEANUP_003", "action": "run_command",
     "commandLine": "powershell -Command \"(Get-Content 'C:\\Windows\\System32\\drivers\\etc\\hosts') | Where-Object { $_ -notmatch '127\\.0\\.0\\.1 <SERVER_HOSTNAME>' } | Set-Content 'C:\\Windows\\System32\\drivers\\etc\\hosts'\"",
     "continueOnFailure": true},
    {"id": "CLEANUP_004", "action": "run_command", "commandLine": "ipconfig /flushdns", "continueOnFailure": true},
    {"id": "CLEANUP_005", "action": "patch_registry",
     "registryKey": "HKLM\\SOFTWARE\\WOW6432Node\\AdventNet\\DesktopCentral\\DCAgent\\ServerInfo",
     "registryAction": "restore", "continueOnFailure": true},
    {"id": "CLEANUP_006", "action": "run_command",
     "commandLine": "\"C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\cfgupdate.exe\"",
     "timeout": 30, "continueOnFailure": true},
    {"id": "CLEANUP_007", "action": "stop_proxy"}
]
```

### Recording Session Template

For capturing traffic manually:

```json
[
    {"id": "REC_001", "action": "install_certificate", "certAction": "install"},
    {"id": "REC_002", "action": "patch_registry", "registryAction": "patch", "...": "..."},
    {"id": "REC_003", "action": "start_proxy", "listenPort": 8080, "mode": "reverse:https://<SERVER_IP>:8383", "sslInsecure": true},
    // ... OS locks ...
    {"id": "REC_008", "action": "clear_captures"},
    {"id": "REC_009", "action": "run_command", "commandLine": "\"...\\cfgupdate.exe\"", "timeout": 30, "continueOnFailure": true},
    {"id": "REC_010", "action": "wait_for_interrupt", "description": "Recording... Ctrl+C to stop"},
    {"id": "REC_011", "action": "capture_traffic", "captureFile": "Reports/recorded-traffic.json", "continueOnFailure": true},
    // ... cleanup ...
]
```

### Failure Simulation Template

For testing agent resilience:

```json
[
    // Setup (same as standard)
    {"id": "SETUP_001", "action": "start_proxy", "listenPort": 8080, "mode": "reverse:https://<SERVER_IP>:8383", "sslInsecure": true},
    
    // Inject failures BEFORE triggering agent
    {"id": "INJECT_001", "action": "modify_response",
     "urlPattern": "meta-data\\.xml", "injectStatus": 500,
     "injectBody": "Internal Server Error", "persistent": true,
     "description": "Force 500 on meta-data.xml"},

    {"id": "INJECT_002", "action": "block_request",
     "urlPattern": "agent-settings", "blockAction": "delay", "delayMs": 10000,
     "persistent": true,
     "description": "Delay agent-settings by 10 seconds"},

    {"id": "SETUP_008", "action": "clear_captures"},
    {"id": "SETUP_009", "action": "run_command", "commandLine": "\"...\\cfgupdate.exe\"", "timeout": 30, "continueOnFailure": true},

    // Wait and validate agent behavior under failure
    {"id": "WAIT_001", "action": "wait_for_request", "urlPattern": "agentSlot", "timeout": 180},

    // Did the agent retry after 500?
    {"id": "TC_001", "action": "assert_request_count",
     "urlPattern": "meta-data\\.xml", "minCount": 2,
     "continueOnFailure": true,
     "description": "Agent retried meta-data.xml after 500"},

    // Cleanup
    {"id": "SAVE_001", "action": "capture_traffic", "captureFile": "Reports/failure-sim-traffic.json", "continueOnFailure": true},
    {"id": "CLEANUP_001", "action": "stop_proxy"}
]
```

### Quick Smoke Test Template

For verifying basic proxy functionality:

```json
[
    {"id": "SMOKE_001", "action": "start_proxy", "listenPort": 8080},
    {"id": "SMOKE_002", "action": "clear_captures"},
    {"id": "SMOKE_003", "action": "assert_request_count", "urlPattern": ".*", "expectedCount": 0},
    {"id": "SMOKE_004", "action": "capture_traffic", "captureFile": "Reports/smoke.json"},
    {"id": "SMOKE_005", "action": "stop_proxy"}
]
```

---

## Naming Conventions

### Command IDs

Use a consistent prefix that reflects the purpose:

| Prefix | Purpose | Example |
|--------|---------|---------|
| `SETUP_NNN` | Environment setup (cert, registry, proxy, OS locks) | `SETUP_001` |
| `WAIT_NNN` | Traffic wait steps | `WAIT_001` |
| `TC_NNN` | Test case assertions | `TC_001`, `TC_100` |
| `SAVE_NNN` | Traffic capture/export | `SAVE_001` |
| `CLEANUP_NNN` | Environment teardown | `CLEANUP_001` |
| `SMOKE_NNN` | Smoke test steps | `SMOKE_001` |
| `REC_NNN` | Recording session steps | `REC_001` |
| `INJECT_NNN` | Response injection rules | `INJECT_001` |
| `BLOCK_NNN` | Request blocking rules | `BLOCK_001` |

### Test Case Numbering

For comprehensive validation files, group TCs by endpoint using hundreds:

| Range | Endpoint |
|-------|----------|
| `TC_1xx` | agentSlot |
| `TC_2xx` | endpointdlp |
| `TC_3xx` | meta-data.xml |
| `TC_4xx` | resource.xml |
| `TC_5xx` | log-upload-settings.json |
| `TC_6xx` | customer-meta-data.json |
| `TC_7xx` | dlp-globalsetting.json |
| `TC_8xx` | agent-settings.xml |
| `TC_9xx` | agent-security-setting.json |
| `TC_10xx` | edr-meta-data.json |
| `TC_11xx` | agentcrashreport |
| `TC_12xx` | applicationcontrol (store apps) |
| `TC_13xx` | applicationcontrol (installed apps) |
| `TC_14xx` | discovery-and-remediation |
| `TC_15xx` | 404 / boundary responses |
| `TC_16xx` | Request count assertions |
| `TC_17xx` | Request ordering assertions |

---

## Machine-Specific Values

These values change per machine and must be updated in every test JSON:

| Placeholder | Where to find it | Example |
|-------------|-----------------|---------|
| `<SERVER_HOSTNAME>` | Registry: `DCNSLastAccessName` under `ServerInfo` key | `naveen-14097` |
| `<SERVER_IP>` | Registry: `DCIPAddress` under `ServerInfo` key, or `nslookup <hostname>` | `10.69.73.19` |
| `<SERVER_PORT>` | Registry: `DCHTTPSPort` under `ServerInfo` key (usually `8383`) | `8383` |

**Quick check:** Open `regedit` and navigate to:
```
HKEY_LOCAL_MACHINE\SOFTWARE\WOW6432Node\AdventNet\DesktopCentral\DCAgent\ServerInfo
```

These values appear in:
- `patch_registry` → `oldValues` array
- `start_proxy` → `mode` field (`reverse:https://<IP>:<PORT>`)
- `run_command` → hosts file, firewall, port proxy commands

---

## Complete Examples — Existing Test Files

The `TestData/` folder contains ready-to-use test JSON files. Here is what each one does:

### CSR / mTLS Test Suite (split execution)

These three files work **together** as a split-execution CSR test:

| File | Commands | Purpose |
|------|----------|--------|
| `csr_capture_test.json` | 2 | **Start recording.** Installs mitmproxy CA cert and starts a reverse proxy with `autoLoadCert: true` and `liveCaptureFile: Reports/csr-capture-traffic.json`. Run this first, then let the agent communicate. |
| `csr_validate_test.json` | 9 | **Offline validation.** Polls the live capture file for the CSR request, validates request method/body, response status/body, request counts, and ordering (CSR before mTLS). Run while the proxy is still running or after `load_traffic`. |
| `csr_stop_proxy.json` | 2 | **Teardown.** Stops the background proxy and removes the mitmproxy CA cert. Run last. |

**Usage:**
```batch
CommunicationAutomation.exe csr_capture_test.json
:: Wait for agent CSR traffic...
CommunicationAutomation.exe csr_validate_test.json
CommunicationAutomation.exe csr_stop_proxy.json
```

### CSR End-to-End Tests (single execution)

| File | Commands | Purpose |
|------|----------|--------|
| `csr_cert_load_test.json` | 13 | Full CSR + mTLS test in one file. Waits for CSR request, validates 200 response, hot-loads signed client cert, then waits for and validates post-CSR mTLS traffic (`agentAuthPropsRequest`). Captures 10 min of additional traffic. |
| `csr_validation_test.json` | 16 | Deep CSR request/response validation. 10 assertions on `ClientCSRSigningServlet`: method, Content-Type, User-Agent, CSR PEM markers in body, URL path, response status/body, signed cert, and exact request count. |

### General Agent Validation

| File | Commands | Purpose |
|------|----------|--------|
| `general_validation.json` | 30 | Full-stack integration test with environment lockdown. Patches registry, adds hosts/portproxy/firewall rules, triggers cfgupdate, validates `agentSlot` and `meta-data.xml` (method, auth headers, User-Agent, URL params, status, counts, order). Full cleanup. |
| `validate_traffic.json` | 68 | **Comprehensive validation** — the largest test. Full environment lockdown, then 50+ assertions across 15 endpoint groups including `agentSlot`, `endpointdlp`, `meta-data.xml`, `resources/*.xml`, `log-upload-settings.json`, `customer-meta-data.json`, `dlp-globalsetting.json`, `agent-settings.xml`, `agent-security-setting.json`, `edr-meta-data.json`, `agentcrashreport`, `applicationcontrol`, `discovery-and-remediation`, `boundary-list.xml`, `acp-global-settings.json`. Full cleanup. |

### Utility / Reference Tests

| File | Commands | Purpose |
|------|----------|--------|
| `smoke_test.json` | 7 | Quick smoke test for proxy lifecycle. Starts proxy, adds a response rule, asserts zero traffic, dumps empty capture, stops proxy. Validates the engine runs end-to-end without real agent traffic. |
| `sample_test.json` | 20 | Reference test demonstrating most action types. Injects 500 on `meta-data.xml`, validates `agentSlot` requests, checks counts/order, exercises cert install/removal. |
| `record_traffic.json` | 18 | Interactive recording session. Full environment lockdown, triggers agent, then blocks on `wait_for_interrupt` (Ctrl+C). On interrupt, dumps traffic and restores environment. |

### Choosing the Right Test File

| Scenario | File(s) |
|----------|--------|
| **Quick check** — proxy starts and stops | `smoke_test.json` |
| **Learn the framework** — see all action types | `sample_test.json` |
| **Record traffic** for later analysis | `record_traffic.json` |
| **CSR/mTLS flow** — split execution | `csr_capture_test.json` → `csr_validate_test.json` → `csr_stop_proxy.json` |
| **CSR/mTLS flow** — single execution | `csr_cert_load_test.json` or `csr_validation_test.json` |
| **Full agent validation** — all endpoints | `validate_traffic.json` |
| **Standard validation** — common endpoints only | `general_validation.json` |

---

## Troubleshooting

### Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Action 'xyz' is not supported` | Typo in action name | Check spelling; action names are case-insensitive |
| `No traffic store` | `start_proxy` not called or failed | Verify proxy started before traffic actions |
| `No matching flow found` | URL pattern doesn't match | Check regex; use `.*` wildcards liberally |
| `Timeout waiting for request` | Agent traffic hasn't arrived yet | Increase `timeout`; verify cfgupdate ran |
| Cleanup didn't run | A command failed without `continueOnFailure` | Add `continueOnFailure: true` to non-critical steps |
| Hosts file entry persists | Test aborted before cleanup | Manually edit `C:\Windows\System32\drivers\etc\hosts` |
| Registry not restored | Test aborted before cleanup | Run a standalone restore JSON |
| Variable `{{X.data}}` not substituted | Step ID typo or step didn't execute | Verify step IDs match exactly |
| Port 8080 in use | Previous proxy instance not stopped | Run `netstat -ano | findstr 8080` and kill the process |

### URL Pattern Tips

- Patterns are **Python regex** — escape dots: `meta-data\\.xml`
- Use `.*` for flexible matching: `agentSlot.*AgentOfset`
- Test patterns at https://regex101.com (select Python flavor)
- An empty pattern matches everything

### Running Tests

```batch
:: Run with full output (PASSED + FAILED)
CommunicationAutomation.exe TestData\my_test.json --show-all

:: Run with failures only (default)
CommunicationAutomation.exe TestData\my_test.json

:: From source
python main.py TestData\my_test.json --show-all
```

### Exit Codes

| Code | Meaning |
|------|---------|
| `0` | All tests passed |
| `1` | One or more tests failed |
| `5` | Test file not found |
| `99` | Critical unhandled error |
