# G.O.A.T-LLM Quick Reference

This quick reference guide shows all supported operation types in the `goat-llm` custom model.

## Available Operations

| Operation Type | Purpose | Required Parameters |
|---------------|---------|-------------------|
| `exe_install` | Install software | `product_name` |
| `check_presence` | Check file exists | `file_path`, `filename` |
| `verify_absence` | Check file doesn't exist | `file_path`, `filename` |
| `value_should_be_present` | Check value exists in file | `file_path`, `filename`, `value` |
| `value_should_be_removed` | Check value absent from file | `file_path`, `filename`, `value` |
| `task_manager` | Manage processes | `action`, `process_name`, `process_path` |
| `api_case` | Test APIs | `connection`, `apiToHit`, `http_method`, `content_type`, `accepts_type`, [optional: `payload`], `expected_response` |
| `run_bat` | Run batch files | `bat_file`, `bat_file_path`, [optional: `arguments`] |
| `run_command` | Execute system commands | `command_to_run` |
| `file_edit` | Edit file contents | `action`, `file_name`, `file_path`, (action-specific params)* |
| `database_actions` | Run DB operations | `action`, `query` |
| `service_actions` | Control system services | `service_name`, `action` |
| `ppm_upgrade` | Perform PPM build upgrades | `base_build`, `ppm_builds` |

*File edit action-specific parameters:
- `update`: `key_to_update`, `new_value`
- `insert`: `value_to_insert`, `line`
- `insert_after`: `value_to_insert`, `after_which_text`
- `replace`: `new_value`, `replaced_value`

## Example Test Case Conversion

### Input:
```
Test case ID: TC001
Description: Verify the server is running after installation
Steps:
1. Install the product
2. Check the server_home/conf/product.conf file exists
3. Ensure the java process is running in server_home/bin
Expected Result: Server is running properly
```

### Expected Output:
```json
{
  "TC001": {
    "testcase_id": "TC001",
    "product_name": "ProductName",
    "reuse_installation": false,
    "operations": [
      {
        "operation_type": "exe_install",
        "parameters": {
          "product_name": "ProductName"
        }
      },
      {
        "operation_type": "check_presence",
        "parameters": {
          "file_path": "server_home/conf",
          "filename": "product.conf"
        }
      },
      {
        "operation_type": "task_manager",
        "parameters": {
          "action": "verify_process",
          "process_name": "java.exe",
          "process_path": "server_home/bin"
        }
      }
    ],
    "expected_result": "Server is running properly"
  }
}
```

## Testing the Model

Run this to see if the model responds correctly:

```
ollama run goat-llm "Convert this test case to operations: Test case ID TC099: Kill the java process in server_home/bin folder and then verify it's gone."
```

Should produce operations with task_manager kill_process followed by verify_process.
