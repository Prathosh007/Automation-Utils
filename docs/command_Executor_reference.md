# Command Executor Reference 🖥️

This document describes the command execution capabilities provided by the `CommandExecutor` class in the GOAT automation framework.

## Overview

The command executor allows you to run system commands (with security restrictions) as part of your test automation, capture their output, and extract values for use in subsequent test steps.

## Quick Reference

| Icon | Action         | Description                                 |
|------|----------------|---------------------------------------------|
| ▶️   | `run_command`  | Execute a system command and capture output |

## Actions

All actions are performed using the `run_command` operation type with the appropriate parameters.

### ▶️ `run_command`

Executes a system command and optionally extracts values from its output.

**Parameters:**
- `command_to_run`: The system command to execute (required, must be whitelisted)
- `command_type` (optional): Specify `"powershell"` for PowerShell commands on Windows
- `exact_value` (optional): String to search for an exact match in the output
- `value_to_search` (optional): Line substring to search for before applying regex
- `value` (optional): Regex pattern to extract value from output
- `note` (optional): Variable name to store extracted value for later use

### Getting Regex Patterns
You can use any AI tool or online regex generator to create and test regular expressions.

***Avoiding Escape Characters:***  
It’s recommended to avoid escape characters like `\d`, `\w`, `\s`,....... These can behave unpredictably in some environments.

***Best Practice:***  
Before using regex in your code, always test it using an AI tool or online tester.

**Use Cases:**

```json
{
  "operation_type": "run_command",
  "parameters": {
    "command_to_run": "ipconfig /all",
    "exact_value": "IPv4 Address",
    "note": "IPV4_LINE"
  }
}
```

```json
{
  "operation_type": "run_command",
  "parameters": {
    "command_to_run": "ping -n 2 localhost",
    "value_to_search": "Reply from",
    "value": "\\d+ms",
    "note": "PING_TIME"
  }
}
```

```json
{
  "operation_type": "run_command",
  "parameters": {
    "command_to_run": "dir C:\\Windows",
    "command_type": "cmd"
  }
}
```

## Security

> **Note:** Only commands matching a predefined whitelist of safe patterns are allowed. If your command is not permitted, contact GOAT administrator to update the whitelist.

The following commands have been added to the whitelist:
- `whoami`
- `systeminfo`
- `netstat -an`
- `ipconfig /all`
- `dir C:\Windows`
- `ls -la /tmp`
- `echo Hello, World!`
- `type config.txt`

## Output and Value Extraction

- The full command output is stored in the operation result.
- If `exact_value` is provided, the output is checked for an exact match.
- If `value_to_search` and `value` (regex) are provided, the output is searched line by line; the regex is applied to matching lines.
- If `note` is provided and a value is found, it is stored for use in later test steps.

## Examples

### Extract IPv4 Address Line

```json
{
  "operation_type": "run_command",
  "parameters": {
    "command_to_run": "ipconfig",
    "value_to_search": "IPv4 Address",
    "value": "\\d+\\.\\d+\\.\\d+\\.\\d+",
    "note": "IPV4_ADDR"
  }
}
```

### Run PowerShell Command

```json
{
  "operation_type": "run_command",
  "parameters": {
    "command_to_run": "Get-Process",
    "command_type": "powershell"
  }
}
```

### Check for System Info

```json
{
  "operation_type": "run_command",
  "parameters": {
    "command_to_run": "systeminfo",
    "exact_value": "OS Name"
  }
}
```

---

> **Note:** The framework automatically handles command execution for Windows systems. Output and extracted values can be referenced in subsequent operations using `${VARIABLE_NAME}` syntax.