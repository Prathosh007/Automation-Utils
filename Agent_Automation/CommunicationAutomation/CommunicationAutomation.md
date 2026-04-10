# CommunicationAutomation - UEMS Agent Traffic Capture & Validation

A Python-based MITM proxy framework for intercepting, capturing, and validating UEMS agent HTTPS communication using **mitmproxy**. JSON-driven, GOAT-server-compatible.

> **Status**: Active Development

---

## How It Works

```
UEMS Agent  --->  mitmproxy (background process)  --->  Real Server (HTTPS)
                        |
              Captures all traffic to live JSON file
              Validates requests & responses (file-polling)
```

**Flow:**
1. Install mitmproxy CA certificate into Windows trust store
2. Patch Windows Registry to redirect agent traffic to local proxy
3. Lock OS-level routing (hosts file + firewall + port proxy) to prevent bypass
4. Start mitmproxy as a detached background process forwarding to the real server
5. Trigger `cfgupdate.exe` to force agent config refresh
6. Capture/validate all HTTP traffic (file-polling mode reads the live capture JSON)
7. Cleanup: restore all OS changes and stop proxy (via `taskkill`)

---

## Architecture

### JSON-Driven Test Engine
All test scenarios are defined as JSON arrays. Each step is a **command** with an `action` type. The engine loads the JSON, executes each command sequentially, and outputs GOAT-compatible results.

```json
[
    {"id": "STEP_001", "action": "start_proxy", "listen_port": 8080, "description": "Start proxy"},
    {"id": "STEP_002", "action": "validate_request", "url_pattern": "agentSlot", "method": "GET"},
    {"id": "STEP_003", "action": "stop_proxy", "description": "Stop proxy"}
]
```

### Three-Layer OS Traffic Lock
The agent reads the real server address from the `meta-data.xml` response and reconnects directly. Three OS-level locks prevent this:

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| Hosts file | `127.0.0.1 <hostname>` | Redirect hostname to localhost |
| Port proxy | `127.0.0.1:8383 -> 127.0.0.1:8080` | Forward agent's port to proxy |
| Firewall | Block outbound to real server IP | Block direct IP connections |

### Proxy Layer
- **ProxyManager**: Runs mitmproxy `DumpMaster` in a **detached background process** (`--serve` mode). The background process writes traffic to a live JSON capture file in real-time.
- **UEMSAddon**: Intercepts `request()`, `response()`, `error()` hooks. Auto-detects CSR servlet responses and can automatically load client certificates from the agent vault for mTLS.
- **TrafficStore**: Thread-safe storage with wait/notify for captured flows. Supports live capture file output so traffic is accessible across processes.
- **ResponseRuleEngine**: Modify/block matching responses
- **ServiceDiscovery**: Discovers UEMS Agent install path and vault from Windows service registry

---

## Project Structure

```
CommunicationAutomation/
├── main.py                     # Entry point: python main.py <test.json> [--show-all]
│                               #   Also: python main.py --serve <host> <port> <mode> [...]
│                               #   (--serve is used internally by start_proxy)
├── requirements.txt            # mitmproxy>=10.0
├── run_capture.bat             # Quick-run recording session
├── build.bat                   # PyInstaller build for standalone EXE
├── CommunicationAutomation.spec # PyInstaller spec (onefile, mitmproxy_rs safe)
│
├── core/
│   ├── models.py               # Command, CommandResult, TestResult dataclasses
│   ├── action_registry.py      # Maps action names -> handler instances
│   ├── base_action.py          # Abstract base with execute/validate/handle_*
│   └── test_engine.py          # Loads JSON, runs commands, GOAT output
│
├── actions/                    # One file per action type (19 actions)
│   ├── start_proxy.py          # Spawn background mitmproxy process (--serve mode)
│   ├── stop_proxy.py           # Stop proxy (taskkill + signal fallbacks)
│   ├── install_certificate.py  # Install/remove mitmproxy CA cert
│   ├── load_client_cert.py     # Load agent mTLS certs from vault
│   ├── load_traffic.py         # Load captured traffic from JSON file
│   ├── patch_registry.py       # Patch/restore registry server values
│   ├── run_command.py          # Run arbitrary commands
│   ├── manage_service.py       # Windows service management
│   ├── configure_proxy.py      # Enable/disable Windows system proxy (netsh)
│   ├── wait_for_request.py     # Block until matching request arrives (file-polling)
│   ├── wait_for_interrupt.py   # Block until Ctrl+C (recording mode)
│   ├── validate_request.py     # Assert request properties
│   ├── validate_response.py    # Assert response properties
│   ├── assert_request_count.py # Verify request counts
│   ├── assert_request_order.py # Verify request ordering
│   ├── capture_traffic.py      # Dump traffic to JSON file
│   ├── clear_captures.py       # Reset traffic buffer
│   ├── modify_response.py      # Inject/alter responses
│   └── block_request.py        # Block or delay requests
│
├── proxy/                      # mitmproxy integration (ProxyManager, UEMSAddon, TrafficStore, ResponseRuleEngine)
├── utils/                      # Logger, pattern matcher, variable context, service discovery
├── TestData/                   # Test scenario JSON files
├── Logs/                       # Execution logs
└── Reports/                    # Captured traffic dumps
```

---

## Supported Actions

| Action | Purpose |
|--------|---------|
| `install_certificate` | Install/remove mitmproxy CA cert in Windows trust store |
| `start_proxy` | Start mitmproxy on host:port (reverse/regular/upstream mode) |
| `stop_proxy` | Stop the proxy gracefully |
| `patch_registry` | Patch/restore registry server values |
| `run_command` | Run arbitrary commands (cfgupdate, netsh, powershell) |
| `manage_service` | Windows service start/stop/restart |
| `wait_for_request` | Block until matching request arrives (with timeout) |
| `wait_for_interrupt` | Block until Ctrl+C — recording mode |
| `validate_request` | Assert request URL, headers, body, method |
| `validate_response` | Assert response status, headers, body |
| `assert_request_count` | Verify captured request count (min/max/exact) |
| `assert_request_order` | Verify request sequence ordering |
| `modify_response` | Inject/alter response for matching requests |
| `block_request` | Block or delay matching requests |
| `capture_traffic` | Dump captured traffic to JSON file |
| `clear_captures` | Reset traffic buffer |

---

## Validation Reference

### validate_request — Check what the agent sent

```json
{"action": "validate_request", "url_pattern": "agentSlot", "method": "GET"}
{"action": "validate_request", "url_pattern": "agentSlot", "header_name": "Authorization", "expected_value": "Basic ", "validation_type": "startswith"}
{"action": "validate_request", "url_pattern": "agentSlot", "expected_value": "actionToCall=AgentOfset", "validation_type": "contains"}
{"action": "validate_request", "url_pattern": "endpointdlp", "body_pattern": "ResourceId", "validation_type": "contains"}
```

| Field | Purpose |
|-------|---------|
| `url_pattern` | Regex to match the request URL (required) |
| `method` | Expected HTTP method (GET, POST) |
| `expected_value` | Check URL content |
| `header_name` + `expected_value` | Check a specific request header |
| `body_pattern` | Check request body content |

### validate_response — Check what the server replied

```json
{"action": "validate_response", "url_pattern": "agentSlot", "expected_status": 200}
{"action": "validate_response", "url_pattern": "meta-data\\.xml", "header_name": "Content-Type", "expected_value": "application/xml", "validation_type": "exact"}
{"action": "validate_response", "url_pattern": "meta-data\\.xml", "body_pattern": "DCServerInfo", "validation_type": "contains"}
```

| Field | Purpose |
|-------|---------|
| `url_pattern` | Regex to match the request URL (required) |
| `expected_status` | Expected HTTP status code (200, 404, etc.) |
| `header_name` + `expected_value` | Check a specific response header |
| `body_pattern` | Check response body content |

### assert_request_count — How many requests

```json
{"action": "assert_request_count", "url_pattern": "agentSlot", "min_count": 1}
{"action": "assert_request_count", "url_pattern": ".*", "min_count": 10, "max_count": 30}
```

| Field | Purpose |
|-------|---------|
| `min_count` | Minimum expected count |
| `max_count` | Maximum expected count |
| `expected_count` | Exact expected count |

### assert_request_order — Verify sequence

```json
{"action": "assert_request_order", "expected_order": ["agentSlot", "meta-data\\.xml", "agent-settings\\.xml"]}
```

### Validation Types

| Type | Behaviour |
|------|-----------|
| `contains` | Value contains expected string (default) |
| `exact` | Exact match |
| `startswith` | Value starts with expected |
| `endswith` | Value ends with expected |
| `regex` | Regex pattern match |
| `notempty` | Value exists and is not empty |
| `isempty` | Value is empty or missing |

### Common Options

| Field | Purpose |
|-------|---------|
| `continueOnFailure` | Don't stop if this step fails |
| `case_sensitive` | Exact case matching (default: false) |
| `timeout` | Wait time in seconds (for wait_for_request) |

---

## Test Cases

### Test Data Files

| File | Purpose | Steps |
|------|---------|-------|
| `smoke_test.json` | Basic proxy start/stop test | 3 |
| `sample_test.json` | Sample test with proxy and validation | varies |
| `record_traffic.json` | Record all agent traffic until Ctrl+C | 18 |
| `general_validation.json` | General validation — basic checks on key endpoints | 10 TCs |
| `validate_traffic.json` | Comprehensive validation — all endpoints | ~60 TCs |
| `csr_capture_test.json` | Start reverse proxy for CSR capture with auto cert loading | 2 |
| `csr_cert_load_test.json` | CSR flow with manual client cert load | varies |
| `csr_validate_test.json` | Validate CSR-related traffic | varies |
| `csr_validation_test.json` | Extended CSR validation scenarios | varies |
| `csr_stop_proxy.json` | Stop proxy and cleanup after CSR capture | 2 |

### Test Flow Pattern

Every test JSON follows this pattern:

```
Setup    →  Install cert, patch registry, start proxy, OS lock, trigger agent
Wait     →  Wait for expected requests to arrive
Validate →  Run test cases (validate_request, validate_response, assert_*)
Save     →  Capture traffic to Reports/
Cleanup  →  Remove OS lock, restore registry, stop proxy
```

### general_validation.json — 10 Test Cases

| TC | Action | What it validates |
|----|--------|-------------------|
| TC_001 | validate_request | agentSlot method is GET |
| TC_002 | validate_request | agentSlot has Authorization: Basic header |
| TC_003 | validate_response | agentSlot response is 200 OK |
| TC_004 | validate_request | meta-data.xml method is GET |
| TC_005 | validate_response | meta-data.xml response is 200 OK |
| TC_006 | validate_response | meta-data.xml Content-Type is XML |
| TC_007 | validate_response | meta-data.xml body has DCServerInfo |
| TC_008 | validate_response | agent-settings.xml response is 200 OK |
| TC_009 | assert_request_count | At least 3 total requests captured |
| TC_010 | assert_request_order | agentSlot comes before meta-data.xml |

### validate_traffic.json — Comprehensive (~60 Test Cases)

Covers all 14 agent endpoints:

| Group | Endpoint | Checks |
|-------|----------|--------|
| TC_1xx | agentSlot | GET method, URL params, User-Agent, Authorization, status, body, X-dc-header |
| TC_2xx | endpointdlp | POST method, ResourceId, ImmediatePostCount, status |
| TC_3xx | meta-data.xml | GET, URL path, If-Modified-Since, Content-Type, DCServerInfo, ProductCodes |
| TC_4xx | resource.xml | GET, status, IS_DELETED, RESOURCE_ID |
| TC_5xx | log-upload-settings.json | GET, status, Content-Type, validLogFileFormats |
| TC_6xx | customer-meta-data.json | Status, customermetadataparams |
| TC_7xx | dlp-globalsetting.json | Status, labelInfo |
| TC_8xx | agent-settings.xml | Status, Content-Type, AgentProtectionSettings |
| TC_9xx | agent-security-setting.json | Status, SECRET_KEY |
| TC_10xx | edr-meta-data.json | Status, EDRGlobalMeta |
| TC_11xx | agentcrashreport | POST, CRU_REPORT, AGENT_VERSION, response "success" |
| TC_12xx | appctrl store apps | POST, ASA array, status |
| TC_13xx | appctrl installed apps | POST, AIA array, status |
| TC_14xx | discovery-and-remediation | POST, AU array, status |
| TC_15xx | 404 responses | boundary-list.xml, acp-global-settings.json |
| TC_16xx | Request counts | agentSlot, meta-data, applicationcontrol, endpointdlp |
| TC_17xx | Request ordering | agentSlot -> meta-data.xml -> agent-settings.xml |

---

## Setup & Run

### First Time Setup
```batch
:: Run as Administrator
install_and_run.bat
```

### Record Traffic
```batch
:: Run as Administrator
run_capture.bat
:: Press Ctrl+C to stop recording. Output: Reports/recorded-traffic.json
```

### Run Validation Tests
```batch
:: General (10 test cases)
.venv\Scripts\python.exe main.py TestData\general_validation.json --show-all

:: Comprehensive (60 test cases)
.venv\Scripts\python.exe main.py TestData\validate_traffic.json --show-all
```

### Build Standalone EXE
```batch
build.bat
:: Output: dist\CommunicationAutomation.exe (single file, ~26 MB)
:: Uses PyInstaller 6.11.1 + CommunicationAutomation.spec
:: Pinned to 6.11.1 — later versions crash with mitmproxy_rs Rust extensions (0xC0000142)
```

---

## Output Format (GOAT Server Compatible)

```
SETUP_001|PASSED|Certificate installed successfully
SETUP_002|PASSED|Patched 6 values: DCNSLastAccessName: naveen-14097 -> 127.0.0.1
TC_001|PASSED|Request found: GET /agentSlot...
TC_002|PASSED|Header 'Authorization': Value starts with 'Basic '
TC_003|PASSED|Response validated: status=200
SUMMARY|Total:25|Passed:25|Failed:0
```

Exit codes: `0` success, `1` failure, `5` file not found, `99` critical error

---

## Future Scope — Data Interruption & Failure Simulation

The framework has `modify_response` and `block_request` actions that enable **data interruption** — altering or blocking live traffic to simulate failures and test agent resilience.

### Already Available

```json
// Return 500 error on meta-data.xml
{"action": "modify_response", "url_pattern": "meta-data\\.xml", "inject_status": 500, "inject_body": "Internal Server Error", "persistent": true}

// Block all agentSlot requests
{"action": "block_request", "url_pattern": "agentSlot", "block_action": "block", "persistent": true}

// Add 5 second delay to all requests
{"action": "block_request", "url_pattern": ".*", "block_action": "delay", "delay_ms": 5000, "persistent": true}
```

### Failure Simulation Scenarios

**Server Error Responses:**
- 500 on agentSlot → Does the agent retry? How many times?
- 503 on all endpoints → Does the agent back off?
- 401 Unauthorized → Does the agent re-authenticate?
- 404 on config endpoints → Does the agent use cached config?

**Response Body Manipulation:**
- Change server address in meta-data.xml DCServerInfo → Does the agent follow it?
- Empty response body on agent-settings.xml → Does the agent crash?
- Malformed XML/JSON → Does the agent handle parse errors?
- IS_DELETED=true in resource.xml → Does the agent uninstall?
- Modified SECRET_KEY → Does auth break?
- Different RESOURCE_ID → Does the agent accept it?

**Network Failure Simulation:**
- Block ALL requests → Agent retry behavior, timeout, caching
- Delay all requests 10-30s → Does the agent timeout?
- Intermittent: block 60s, unblock, block → Does the agent recover?
- Selective endpoint failure → Does the agent continue with other operations?

**Data Integrity & Security:**
- Inject malicious content in config fields → Injection vulnerability?
- Oversized response (10MB+) → Memory handling?
- Wrong Content-Type header → Does the agent validate before parsing?

### Future Enhancements

| Enhancement | Description |
|-------------|-------------|
| Conditional rules | Apply rules on Nth request (fail on 3rd attempt) |
| Response body patching | Modify specific XML/JSON fields without replacing entire body |
| Rule scheduling | Activate/deactivate rules after N seconds |
| Connection drop | Close TCP mid-transfer (partial response) |
| SSL/TLS error injection | Invalid certificates, TLS handshake failures |
| Bandwidth throttling | Limit transfer speed (10 KB/s) |
| Request replay | Re-send captured requests for regression testing |
| Response recording + replay | Mock server mode without live server |

---

## Known Limitations

1. **mTLS forwarding (partial)** — The framework now supports mTLS via `load_client_cert` and auto-cert-loading (CSR detection). PEM, DER, hex-encoded, and base64 formats are handled. However, agent vault files in proprietary encrypted format may still fail conversion — the proxy continues without mTLS in that case.
2. **cfgupdate timing** — Traffic appears within 1-3 minutes after cfgupdate, but full refresh cycle is 90 minutes.
3. **Machine-specific config** — Server hostname, IP, and port in test JSONs must be updated per machine.
4. **Background proxy IPC** — The background proxy writes traffic to a live JSON file. The test process reads it via file-polling (`wait_for_request`, `load_traffic`). Response modification rules only work in the background proxy process.
