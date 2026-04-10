# CSV File Operations Reference 📊

This document describes the CSV file operations available in the automation framework, allowing you to search for and verify values within CSV files.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Actions](#actions)
    - [Value Presence](#value-presence)
    - [Value Absence](#value-absence)
- [Common Parameters](#common-parameters)
- [Examples](#examples)

## Overview

The CSV file operation provides an interface to verify the presence or absence of specific values in CSV files, making it easy to validate data during test automation.

## Quick Reference

| Icon | Action | Description |
|------|--------|-------------|
| ✅ | `value_should_be_present` | Verify a value exists in the CSV file |
| ❌ | `value_should_be_removed` | Verify a value does not exist in the CSV file |

## Actions

All actions are performed using the `csv_file_operation` operation type with the appropriate `action` parameter.

### Value Presence

#### ✅ `value_should_be_present`
Verifies that a specific value exists in the CSV file.

**Parameters:**
- `file_path`: Path to the CSV file
- `value`: Value to search for in the CSV file

**Returns:**
- Sets `found` parameter to "true" if the value was found, "false" otherwise

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/data/users.csv",
    "value": "johndoe@example.com"
  }
}
```

### Value Absence

#### ❌ `value_should_be_removed`
Verifies that a specific value does not exist in the CSV file.

**Parameters:**
- `file_path`: Path to the CSV file
- `value`: Value that should not be present in the CSV file

**Returns:**
- Sets `removed` parameter to "true" if the value was not found, "false" otherwise

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/data/archived_users.csv",
    "value": "activeuser@example.com"
  }
}
```

## Common Parameters

These parameters apply to both actions:

| Parameter | Description |
|-----------|-------------|
| `action` | Specifies the CSV file action to perform (required) |
| `file_path` | Path to the CSV file (required) |
| `value` | Value to search for or verify absence of (required) |


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

### Check If Email Address Exists in User List
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/exports/user_list.csv",
    "value": "admin@company.com"
  }
}
```

### Verify Deleted User Is Removed From Report
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/reports/active_users.csv",
    "value": "terminated_user"
  }
}
```

> **Note:** The framework automatically handles file existence and permission checks before performing operations