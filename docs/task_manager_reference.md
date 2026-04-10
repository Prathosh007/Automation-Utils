# TaskManager Operation Reference

This document provides details about the `task_manager` operation type and how to use it in test cases.

## Overview

The `task_manager` operation allows you to interact with running processes on the system. It enables you to verify if specific processes are running, kill processes, and check process properties.


## Supported Actions

The `task_manager` operation supports the following actions:

1. `verify_process` - Check if a process is running
2. `kill_process` - Terminate a running process
3. `verify_process_property` - Verify or extract properties of a process

## Action: verify_process

Checks if a specified process is running.

### Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `action` | Must be set to `"verify_process"` | Yes | - |
| `process_name` | Name of the process to verify | Yes* | - |
| `process_path` | Path where the process executable is located | No | - |
| `expect` | Expected state of the process: `"processrunning"` or `"processnotrunning"` | No | `"processrunning"` |
| `port` | Port number to additionally verify the process is using | No | - |

*Either `process_name` or `process_path` must be provided.

### Example

```json
{
  "operation_type": "task_manager",
  "parameters": {
    "action": "verify_process",
    "process_name": "nginx.exe",
    "process_path": "server_home/nginx/",
    "expect": "processrunning"
  }
}
```

### Example with Port Verification

```json
{
  "operation_type": "task_manager",
  "parameters": {
    "action": "verify_process",
    "process_name": "postgres.exe",
    "port": "5432",
    "expect": "processrunning"
  }
}
```

## Action: kill_process

Terminates a running process.

### Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `action` | Must be set to `"kill_process"` | Yes | - |
| `process_name` | Name of the process to kill | Yes* | - |
| `process_path` | Path where the process executable is located | No | - |

*Either `process_name` or `process_path` must be provided.

### Example

```json
{
  "operation_type": "task_manager",
  "parameters": {
    "action": "kill_process",
    "process_name": "nginx.exe",
    "process_path": "server_home/nginx/"
  }
}
```

## Action: verify_process_property

Verifies or extracts properties of a process.

### Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `action` | Must be set to `"verify_process_property"` | Yes | - |
| `search_type` | Type of search criteria (`"pid"`, `"port"`, `"path"`, `"name"`, `"user"`) | Yes | - |
| `search_type_value` | Value for the search criteria | Yes | - |
| `expected_type` | Type of property to verify (`"pid"`, `"name"`, `"path"`, `"port"`, `"memory"`, `"threads"`, `"user"`, `"cpu"`, `"starttime"`, `"get_instance"`) | Yes | - |
| `expected_type_value` | Expected value of the property | No* | - |
| `comparison_operator` | For memory checks: `"greater_than"`, `"less_than"`, or `"equal"` | No | `"equal"` |
| `note` | Name of parameter to store the extracted property value | No* | - |

*Either `expected_type_value` or `note` must be provided.

### Examples

#### Check Process Memory Usage

```json
{
  "operation_type": "task_manager",
  "parameters": {
    "action": "verify_process_property",
    "search_type": "name",
    "search_type_value": "postgres.exe",
    "expected_type": "memory",
    "expected_type_value": "50 MB",
    "comparison_operator": "less_than"
  }
}
```

#### Extract Process PID to a Variable

```json
{
  "operation_type": "task_manager",
  "parameters": {
    "action": "verify_process_property",
    "search_type": "name",
    "search_type_value": "nginx.exe",
    "expected_type": "pid",
    "note": "nginx_pid"
  }
}
```

#### Verify Process Using Specific Port

```json
{
  "operation_type": "task_manager",
  "parameters": {
    "action": "verify_process_property",
    "search_type": "port",
    "search_type_value": "8080",
    "expected_type": "name",
    "expected_type_value": "java.exe"
  }
}
```

#### Check Number of Process Instances

```json
{
  "operation_type": "task_manager",
  "parameters": {
    "action": "verify_process_property",
    "search_type": "name",
    "search_type_value": "java.exe",
    "expected_type": "get_instance",
    "expected_type_value": "3"
  }
}
```

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


## Example Test Cases

### Verify Java Process is Running

```json
{
  "testcase_id": "verify_java_process",
  "description": "Verify that Java process is running",
  "reuse_installation": true,
  "operations": [
    {
      "operation_type": "task_manager",
      "parameters": {
        "action": "verify_process",
        "process_name": "java.exe",
        "expect": "processrunning"
      }
    }
  ],
  "expected_result": "Java process should be running"
}
```

### Verify Database Port is in Use

```json
{
  "testcase_id": "verify_database_port",
  "description": "Verify database is running on port 5432",
  "reuse_installation": true,
  "operations": [
    {
      "operation_type": "task_manager",
      "parameters": {
        "action": "verify_process",
        "process_name": "postgres.exe",
        "port": "5432",
        "expect": "processrunning"
      }
    }
  ],
  "expected_result": "Database should be running and listening on port 5432"
}
```

### Kill and Verify Process

```json
{
  "testcase_id": "kill_and_verify_process",
  "description": "Kill a process and verify it's not running anymore",
  "reuse_installation": true,
  "operations": [
    {
      "operation_type": "task_manager",
      "parameters": {
        "action": "kill_process",
        "process_name": "nginx.exe"
      }
    },
    {
      "operation_type": "task_manager",
      "parameters": {
        "action": "verify_process",
        "process_name": "nginx.exe",
        "expect": "processnotrunning"
      }
    }
  ],
  "expected_result": "Process should be killed successfully and not running afterwards"
}
```

### Extract Process Memory Usage

```json
{
  "testcase_id": "extract_process_memory",
  "description": "Extract memory usage of a process",
  "reuse_installation": true,
  "operations": [
    {
      "operation_type": "task_manager",
      "parameters": {
        "action": "verify_process_property",
        "search_type": "name",
        "search_type_value": "java.exe",
        "expected_type": "memory",
        "note": "java_memory"
      }
    }
  ],
  "expected_result": "Memory usage of Java process should be extracted and displayed"
}
```

### Count Running Instances

```json
{
  "testcase_id": "count_service_instances",
  "description": "Count number of running service instances",
  "reuse_installation": true,
  "operations": [
    {
      "operation_type": "task_manager",
      "parameters": {
        "action": "verify_process_property",
        "search_type": "name",
        "search_type_value": "svchost.exe",
        "expected_type": "get_instance",
        "note": "service_count"
      }
    }
  ],
  "expected_result": "Number of running service instances should be counted and displayed"
}
```

## Notes

1. For memory values, you can use formatted strings like `"50 MB"`, `"1.5 GB"`, etc.
2. Process verification is case-insensitive for process names.