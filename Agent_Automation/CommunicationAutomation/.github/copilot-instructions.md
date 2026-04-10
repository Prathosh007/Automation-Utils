# CommunicationAutomation: AI Agent Guide

## Project Overview
This is a **MITM proxy-based HTTP test automation engine** that executes declarative test scenarios from JSON files. It intercepts, validates, and modifies network traffic for UEMS Agent testing. The system is mirrored from a C# .NET implementation and exchanges data via GOAT-compatible logging.

## Architecture: Command-Action Pattern

### Execution Flow
1. **TestEngine** loads JSON file (array or `{"commands": [...]}`)
2. **VariableContext** performs `{{stepId.property}}` substitution on all string fields
3. **Command.from_dict()** converts JSON dict → Command dataclass (camelCase→snake_case mapping)
4. **ActionRegistry.get_action()** retrieves handler by action name (case-insensitive)
5. **Action.execute()** runs business logic with shared `context` dict
6. **CommandResult** stored in VariableContext for downstream substitution

### Shared Context Dictionary
The `context` dict is passed through the entire test suite and holds:
- `background_proxy_pid`: PID of the background proxy process (set by `start_proxy`)
- `traffic_store`: TrafficStore instance (populated by `wait_for_request` file-polling or `load_traffic`)

**Key pattern**: The proxy runs as a detached background process. Traffic is shared via a live JSON capture file. Use `wait_for_request` (with `captureFile`) or `load_traffic` to populate the traffic store from that file.

## Command Structure & Field Mapping

Commands map JSON keys (camelCase) to Python fields (snake_case). Example:
```json
{
  "id": "CMD_001",
  "action": "modify_response",
  "urlPattern": ".*api.*",
  "continueOnFailure": true,
  "injectStatus": 403
}
```

Becomes:
```python
Command(
  id="CMD_001",
  action="modify_response",
  url_pattern=".*api.*",
  continue_on_failure=True,
  inject_status=403
)
```

**Mapping is bidirectional**: `models.py` defines the complete camelCase↔snake_case mapping in `Command.from_dict()`.

## Action Implementation Pattern

All actions inherit from **BaseAction** and must override `execute(command, context)`:

```python
class MyAction(BaseAction):
    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and command.my_field
    
    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)
        
        # Get shared state from context
        proxy_mgr = context.get("proxy_manager")
        
        try:
            # Business logic here
            self.handle_success(command, result, "What happened")
        except Exception as e:
            self.handle_failure(command, result, str(e))
        
        return result
```

**Helper methods**:
- `self.create_result(command)`: Pre-populate CommandResult with command ID/action
- `self.handle_success(cmd, result, remarks)`: Set `result.success=True`, emit OutputRemark, log
- `self.handle_failure(cmd, result, error)`: Set `result.success=False`, emit OutputRemark, log
- `self.logger.log_to_file()`: Write to `Logs/{test_id}.log`

## Proxy Management

### Background Process Model
`start_proxy` spawns a **detached background process** (`--serve` mode) via `subprocess.Popen` with `CREATE_NEW_PROCESS_GROUP | DETACHED_PROCESS | CREATE_NO_WINDOW`. The background process:
- Runs ProxyManager with mitmproxy in a daemon thread internally
- Writes all captured traffic to a live JSON file in real-time
- Writes its PID to `Reports/proxy.pid`
- Logs to `Reports/proxy.log`

`stop_proxy` terminates the background process using:
1. `taskkill /F /T /PID` (primary, kills process tree)
2. `CTRL_BREAK_EVENT` / `SIGTERM` signals (fallback)
3. Verifies process death; reports failure if it survives

### Response Modification Rules
Rules are stored in `ResponseRuleEngine` inside the background proxy process:
```python
# Rules only apply to traffic arriving AFTER the rule is added
# clear_captures does NOT clear rules
```

### TrafficStore & Live Capture
The background proxy writes traffic to a live JSON file. The test process reads it:
```python
# File-polling mode (wait_for_request with captureFile)
# Or explicit load: load_traffic with captureFile
store = context["traffic_store"]
# Find flows: store.get_matching_flows(url_pattern, method)
```

## Variable Substitution (Crucial for Test Linking)

VariableContext stores each command result with automatic property accessors:
```python
# After command "STEP_001" with CommandResult(success=True, data={"key": "value"})
# Subsequent commands can reference:
{{STEP_001}}              → CommandResult object (not directly usable in JSON)
{{STEP_001.success}}      → "True"
{{STEP_001.message}}      → "Custom message"
{{STEP_001.data}}         → "{'key': 'value'}"  (dict as string)
{{STEP_001.error}}        → "Error description"
{{STEP_001.duration}}     → Duration in seconds
```

Substitution happens **before** Command.from_dict(), so variables work in any string field (url_pattern, inject_body, etc.).

## Key Actions & Typical Workflow

| Action | Purpose | Key Fields |
|--------|---------|-----------|
| `start_proxy` | Spawn background mitmproxy process | listen_host, listen_port, ssl_insecure, mode, auto_load_cert, live_capture_file |
| `stop_proxy` | Kill background proxy (taskkill + signals) | — |
| `configure_proxy` | Set system proxy (via `netsh winhttp`) | proxy_action, proxy_host, proxy_port, bypass_list |
| `load_client_cert` | Load agent mTLS cert from vault | client_cert_path, client_key_path |
| `load_traffic` | Load captured traffic from JSON file | capture_file |
| `modify_response` | Add URL-matched response injection rule | url_pattern, inject_status, inject_body, inject_headers, persistent |
| `block_request` | Block/delay requests matching URL | url_pattern, method, block_action, delay_ms |
| `wait_for_request` | Wait for request (in-process or file-polling) | url_pattern, timeout, method, capture_file |
| `validate_request` | Assert request captured (exact match or regex) | url_pattern, method, header_name, body_pattern, validation_type, case_sensitive |
| `validate_response` | Assert response fields match expected | url_pattern, expected_status, expected_value, validation_type |
| `assert_request_count` | Assert count of matching requests | url_pattern, min_count, max_count, expected_count |
| `assert_request_order` | Assert requests matched in order | expected_order (list of URL patterns) |
| `capture_traffic` | Write all captured flows to JSON file | capture_file, capture_format |
| `clear_captures` | Clear traffic buffer (not rules) | — |
| `install_certificate` | Add/remove mitmproxy cert to Windows store | cert_path, cert_action ("install"/"remove") |
| `manage_service` | Windows service start/stop/restart/status/find | service_name, service_action |
| `run_command` | Run arbitrary shell command | command_line, timeout, working_dir |
| `patch_registry` | Bulk find-and-replace registry values | registry_key, registry_action, old_values, new_values |
| `wait_for_interrupt` | Block until Ctrl+C (recording mode) | — |

**Typical sequence**:
1. `start_proxy` → `configure_proxy` (optional)
2. `modify_response` / `block_request` (set rules)
3. `clear_captures` (reset buffer before test)
4. `wait_for_request` / External trigger
5. `validate_request` / `validate_response` / `assert_request_count`
6. `capture_traffic` (log results)
7. `stop_proxy`

## Validation & Error Handling

### Validation Types (case_sensitive applies to most)
- `exact`: Full string match
- `contains`: Substring match
- `regex`: Regex pattern match
- `startswith` / `endswith`: Prefix/suffix match

### continue_on_failure Field
If `true`, test continues even if command fails. Controls overall test result (success = all non-skipped commands succeeded).

### Return Codes (exit()  in main.py)
- `0`: All tests passed
- `1`: One or more tests failed
- `5`: Test file not found
- `99`: Critical unhandled error

## Logging & Output

### Three Log Channels
1. **File log** (`Logs/{test_id}.log`): Detailed execution trace, set via `logger.log_to_file()`
2. **OutputRemark**: GOAT-compatible console output, emitted by `handle_success()` / `handle_failure()`
3. **Reports**: `Reports/` directory stores captures, results, screenshots

### Logger Flags
```python
Logger.show_failures_only = False  # (default True) show PASSED remarks with --show-all flag
Logger.output_file_not_found(path)
Logger.output_critical_error(msg)
Logger.output_remark(command_id, success, message)
```

## Development Guidelines

### Adding a New Action
1. Create `actions/my_action.py` extending BaseAction
2. Implement `execute()` and optionally `validate_command()`
3. Register in `ActionRegistry.__init__()`: `self._actions["my_action"] = MyAction()`
4. Test with sample JSON in `TestData/`

### Modifying Commands
- Update `Command` dataclass in `core/models.py`
- Ensure `from_dict()` mapping includes both camelCase and snake_case keys
- Test field conversion with `Command.from_dict({"camelCaseKey": value})`

### Testing Locally
```bash
python main.py TestData/smoke_test.json
python main.py TestData/smoke_test.json --show-all  # Include PASSED remarks
```

## Common Pitfalls

- **Background proxy not stopping**: `stop_proxy` uses `taskkill /F /T` as primary kill method. If it still hangs, manually run `taskkill /F /PID <pid>` using the PID from `Reports/proxy.pid`
- **Process hangs after test**: `main.py` uses `os._exit()` to force termination; if running from source and it hangs, check for non-daemon threads
- **Variable substitution fails silently**: Unresolved `{{vars}}` returned as-is in JSON string; verify step IDs match
- **Response rules not applied**: Rules live in the background proxy process; they only affect traffic arriving after the rule is added
- **Port conflicts**: 8080 default; change `listen_port` if occupied
- **Traffic store empty**: Use `wait_for_request` with `captureFile` or `load_traffic` to populate from the live capture file
- **clear_captures does not clear rules**: It only clears the in-memory flow buffer
