# BATCH File Execution Reference 🖥️

This document describes the BAT file operations available in the GOAT automation framework, allowing you to execute Windows batch files with various options for automation testing.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Operations](#operations)
  - [Execute](#execute)
  - [Execute and Get Value](#execute-and-get-value)
  - [Execute Interactive](#execute-interactive)
- [Special Handling: Clone Primary Server BAT](#special-handling-clone-primary-server-bat)
- [Timeout Handling](#timeout-handling)
- [Common Parameters](#common-parameters)
- [Path Variables](#path-variables)
- [Examples](#examples)

## Overview

The BAT file operations provide functionality for executing Windows batch files in your test automation workflows, including capturing output values, handling interactive prompts, and robust timeout/error handling.

## Quick Reference

| Icon | Action | Description |
|------|--------|-------------|
| ▶️ | `execute` | Execute a batch file |
| 🔍 | `execute_and_get_value` | Execute a batch file and extract values from output |
| 💬 | `execute_interactive` | Execute a batch file with interactive prompt handling |

## Operations

All BAT file operations use the operation type `run_bat` with the appropriate `action` parameter.

### ▶️ Execute

Executes a batch file with specified parameters.

**Parameters:**
- `file_path`: Path to the batch file to execute (required)
- `args`: Command-line arguments to pass to the batch file (optional)
- `working_dir`: Directory to run the batch file from (optional)
- `timeout`: Maximum seconds to wait for completion (optional, default: 1800)

**Example:**
```json
{
  "operation_type": "run_bat",
  "parameters": {
    "action": "execute",
    "file_path": "server_home/scripts/start_service.bat",
    "working_dir": "server_home/scripts/"
  }
}
```

### 🔍 Execute and Get Value

Executes a batch file and extracts a specific value from its output.

**Parameters:**
- `file_path`: Path to the batch file to execute (required)
- `args`: Command-line arguments to pass to the batch file (optional)
- `working_dir`: Directory to run the batch file from (optional)
- `timeout`: Maximum seconds to wait for completion (optional, default: 1800)
- `output_search_text`: Text to verify in the output (required)
- `output_value_pattern`: Regex pattern to extract value from the output line (optional)
- `note`: Key name to store the extracted value (optional)

**Example:**
```json
{
  "operation_type": "run_bat",
  "parameters": {
    "action": "execute_and_get_value",
    "file_path": "server_home/scripts/get_version.bat",
    "output_search_text": "Version:",
    "output_value_pattern": "([0-9]+(?:\\.[0-9]+)+)",
    "note": "APP_VERSION"
  }
}
```

### Getting Regex Patterns
You can use any AI tool or online regex generator to create and test regular expressions.

***Best Practice:***  
Before using regex in your code, always test it using an AI tool or online tester.

***Avoiding Escape Characters:***  
It’s recommended to avoid escape characters like `\d`, `\w`, `\s`,....... These can behave unpredictably in some environments.

### 💬 Execute Interactive

Executes a batch file and handles interactive prompts by providing automated responses.

**Parameters:**
- `file_path`: Path to the batch file to execute (required)
- `args`: Command-line arguments to pass to the batch file (optional)
- `working_dir`: Directory to run the batch file from (optional)
- `timeout`: Maximum seconds to wait for completion (optional, default: 1800)
- `prompt_responses`: Semicolon-separated list of prompt=response pairs (required)
- `output_search_text`: Text to search for in the output to verify success (optional)

**Example:**
```json
{
  "operation_type": "run_bat",
  "parameters": {
    "action": "execute_interactive",
    "file_path": "server_home/scripts/configure_service.bat",
    "prompt_responses": "Enter username=admin;Enter password=secret123;Confirm (Y/N)=Y",
    "output_search_text": "Configuration completed successfully"
  }
}
```

## Special Handling: Clone Primary Server BAT

> **Important:**  
> The framework includes special handling for `Clone_Primary_Server.bat` due to a known product issue where the file contains a problematic `CMD` line.

- **Before execution:**  
  The framework reads the original content and removes any line that is exactly `CMD` (case-insensitive) from the batch file.
- **After execution:**  
  The original content is restored to ensure the product is not affected for future operations.

This ensures reliable automation and avoids failures specific to this batch file.

## Timeout Handling

- The default timeout for all BAT file operations is **1800 seconds (30 minutes)**.
- You can override this by specifying the `timeout` parameter (in seconds).
- If the process does not complete within the timeout, it is forcibly terminated and the operation is marked as failed.
- All output and error streams are handled and closed properly on timeout.

---

### ⚠️ Example: Clone Primary Server BAT with Timeout

If you run `Clone_Primary_Server.bat` with a timeout of 50 seconds:

- **Expected:** If the batch file completes within 50 seconds, the operation passes.
- **Timeout:** If the batch file does not finish within 50 seconds, the operation fails, and the process is forcibly terminated.
- **Java Limitation:** Due to a known Java limitation, forcibly closing the process's output stream during an I/O operation may not immediately kill the child thread that is reading the output. This is especially noticeable with `Clone_Primary_Server.bat`, which may have a dynamic input stream that causes the output-listening thread to hang.
- **Result:** The framework will log that the process timed out and was killed, but the output-listening thread may still write to the log after the timeout due to this limitation. This does not affect the test result, which is still marked as failed if the timeout is exceeded.

> **Important:** Always ensure your batch file completes within the specified timeout. (default: 1800 seconds.//30 minutes)

## Common Parameters

| Parameter | Description |
|-----------|-------------|
| `action` | Specifies the BAT file operation to perform (required) |
| `file_path` | Path to the BAT file to execute (required) |
| `args` | Command-line arguments to pass to the BAT file (optional) |
| `working_dir` | Working directory for execution (optional) |
| `timeout` | Maximum execution time in seconds (optional, default: 1800) |

## Path Variables

The framework automatically resolves these path variables:

| Variable      | Description                               |
|---------------|-------------------------------------------|
| `server_home` | Server installation directory             |
| `agent_home`  | Agent installation directory              |
| `ds_home`     | Distribution Server home directory        |
| `sgs_home`    | Site Gateway Server home directory        |
| `vmp_home`    | Vulnerability Manager Plus home directory |
| `pmp_home`    | Patch Manager Plus home directory         |
| `msp_home`    | MSP home directory                        |
| `mdm_home`    | Mobile Device Manager home directory      |
| `ss_home`     | Summary Server home directory             |
| `tool_home`   | GOAT tool installed directory             |

## Examples

### Basic Execution
```json
{
  "operation_type": "run_bat",
  "parameters": {
    "action": "execute",
    "file_path": "server_home/bin/start_server.bat",
    "args": "-debug -verbose"
  }
}
```

### Extract Version Number
```json
{
  "operation_type": "run_bat",
  "parameters": {
    "action": "execute_and_get_value",
    "file_path": "server_home/scripts/get_version.bat",
    "working_dir": "server_home/scripts/",
    "output_search_text": "Product Version:",
    "output_value_pattern": "([0-9]+(?:\\.[0-9]+)+)",
    "note": "PRODUCT_VERSION"
  }
}
```

### Execute Installation with Interactive Prompts
```json
{
  "operation_type": "run_bat",
  "parameters": {
    "action": "execute_interactive",
    "file_path": "server_home/installers/setup.bat",
    "working_dir": "server_home/installers/",
    "timeout": "600",
    "prompt_responses": "Accept License (Y/N)=Y;Installation Path=D:\\Program Files\\Application;Configure service (Y/N)=Y;Service Name=AppService;Start service now (Y/N)=Y",
    "output_search_text": "Installation completed successfully"
  }
}
```

---

> **Note:**  
> The framework handles output capturing, process timeout, error handling, and special product-specific batch file issues automatically. On non-Windows platforms, batch file execution may not be supported.
```
This version documents the new timeout default, the special handling for `Clone_Primary_Server.bat`, and all other recent changes.