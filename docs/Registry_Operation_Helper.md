# Registry Automation Framework - Complete Guide
## Overview

This document provides a comprehensive guide for automating Windows Registry operations using JSON-based commands. The framework supports all major registry operations including create, read, update, delete operations.

---

## Table of Contents

- [1. Supported Actions](#1-supported-actions)
- [2. JSON Command Structure](#2-json-command-structure)
- [3. Registry Roots](#3-registry-roots)
- [4. Value Types](#4-value-types)
- [5. WOW64 Mode](#5-wow64-mode)
- [6. Quick Examples](#6-quick-examples)
- [7. Complete Test Example](#7-complete-test-example)
- [8. Command-Line Usage](#8-command-line-usage)
- [9. Output Format](#9-output-format)
- [10. Common Properties](#10-common-properties)
- [11. Best Practices](#11-best-practices)
- [12. Error Handling](#12-error-handling)

---

## 1. Supported Actions
> All available registry actions you can perform, such as create, read, write, delete, and check registry keys and values.

| # | Action | Description | Example |
|---|--------|-------------|---------|
| 1.1 | `add_key` | Create registry key | Create Software\\MyApp |
| 1.2 | `write_key` | Write/create value | Set Version=100 |
| 1.3 | `read_key` | Read any value | Read AppName |
| 1.4 | `read_string` | Read string value | Read REG_SZ |
| 1.5 | `read_dword` | Read DWORD | Read 32-bit integer |
| 1.6 | `read_qword` | Read QWORD | Read 64-bit integer |
| 1.7 | `delete_value` | Delete value | Delete Version |
| 1.8 | `delete_key` | Delete key | Delete Software\\MyApp |
| 1.9 | `check_key_exists` | Check key exists | Verify key exists |
| 1.10 | `check_value_exists` | Check value exists | Verify value exists |
| 1.11 | `list_subkeys` | List subkeys | List all child keys |
| 1.12 | `list_values` | List values | List all values in key |
| 1.13 | `get_value_type` | Get value type | Get REG_SZ/REG_DWORD |

---

## 2. JSON Command Structure
> The standard JSON format used to define any registry operation with its required and optional fields.

Base structure for all registry operations:

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "action_name",
    "root": "HKEY_CURRENT_USER",
    "path": "Software\\MyApp",
    "key_name": "ValueName",
    "value": "ValueData",
    "value_type": "REG_SZ"
  }
}
```

**Key Fields:**
- **action**: The operation to perform (from Supported Actions list)
- **root**: Registry hive location (HKCU, HKLM, etc.)
- **path**: Full path to the registry key
- **key_name**: Specific value name within the key (optional for some actions)
- **value**: Data to write to the registry (for write operations)
- **value_type**: Data type of the value (REG_SZ, REG_DWORD, etc.)
- **wow_mode**: Architecture mode for 32-bit/64-bit operations

---

## 3. Registry Roots
> The top-level registry hives (like HKLM, HKCU) that serve as starting points for all registry paths.

| # | Root | Short Form | Usage |
|---|------|------------|-------|
| 3.1 | `HKEY_LOCAL_MACHINE` | HKLM | System-wide settings, drivers, services |
| 3.2 | `HKEY_CURRENT_USER` | HKCU | Current user profile settings |
| 3.3 | `HKEY_CLASSES_ROOT` | HKCR | File associations and COM settings |
| 3.4 | `HKEY_USERS` | HKU | All user profiles |
| 3.5 | `HKEY_CURRENT_CONFIG` | HKCC | Hardware profile settings |

---

## 4. Value Types
> The different data types (string, integer, binary, etc.) you can store in a registry value.

| # | Type | Description | Example |
|---|------|-------------|---------|
| 4.1 | `REG_SZ` | String text | "Hello World" |
| 4.2 | `REG_DWORD` | 32-bit integer | 100 |
| 4.3 | `REG_QWORD` | 64-bit integer | 5368709120 |
| 4.4 | `REG_BINARY` | Binary data | "01 FF A3" |
| 4.5 | `REG_MULTI_SZ` | Multiple strings | "Line1\nLine2" |
| 4.6 | `REG_EXPAND_SZ` | Expandable string | "%TEMP%\file.txt" |

---

## 5. WOW64 Mode
> Controls whether registry operations target the 32-bit or 64-bit registry view on a 64-bit Windows system.

wowMode is used to explicitly control whether a registry operation should access the 32-bit or 64-bit registry view on a 64-bit Windows system. Without it, Windows automatically redirects access based on the process architecture (x86/x64), which can cause inconsistent results.

**Example:**
If a 32-bit Agent writes to:
`HKLM\SOFTWARE\WOW6432Node\dcagent`

A 64-bit registry utility using default mode will check:
`HKLM\SOFTWARE\dcagent` ❌ (wrong location)

But if we set:
`wowMode = use32`

It correctly accesses the 32-bit registry view ✅

So wowMode ensures consistent and correct registry access regardless of how the utility is built.


| # | Mode | Description | Usage |
|---|------|-------------|-------|
| 5.1 | `BuildType` | Match build architecture (default) | Default behavior |
| 5.2 | `x86` | 32-bit view (WOW6432Node) | Force 32-bit access |
| 5.3 | `x64` | 64-bit view (native) | Force 64-bit access |
| 5.4 | `Both` | Access both 32-bit and 64-bit | Dual access |

---

## 6. Quick Examples
> Ready-to-use JSON samples for common registry tasks like creating keys, writing values, reading, and deleting.

### 6.1 Create Key

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "add_key",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

### 6.2 Write String Value

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "write_key",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName",
    "value": "MyApp",
    "value_type": "REG_SZ"
  }
}
```

### 6.3 Write DWORD Value

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "write_key",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "Version",
    "value": "100",
    "value_type": "REG_DWORD"
  }
}
```

### 6.4 Read Value

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "read_key",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName"
  }
}
```

### 6.5 Check Value Exists

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "check_value_exists",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName"
  }
}
```

### 6.6 List All Values

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "list_values",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

### 6.7 Delete Value

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "delete_value",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "Version"
  }
}
```

### 6.8 Delete Entire Key

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "delete_key",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

---

## 7. Complete Test Example
> A full end-to-end workflow that creates, writes, reads, checks, lists, and deletes registry entries in sequence.

This is a full 9-step workflow demonstrating registry operations:

```json
[
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "add_key",
      "root": "HKEY_CURRENT_USER",
      "path": "Software\\TestApp"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "write_key",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "AppName",
      "value": "MyTestApplication",
      "value_type": "REG_SZ"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "write_key",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "Version",
      "value": "100",
      "value_type": "REG_DWORD"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "read_key",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "AppName"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "read_dword",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "Version"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "check_value_exists",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "AppName"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "list_values",
      "root": "HKCU",
      "path": "Software\\TestApp"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "delete_value",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "Version"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "delete_key",
      "root": "HKCU",
      "path": "Software\\TestApp"
    }
  }
]
```

---

## 8. Command-Line Usage
> How to run or validate your registry JSON test files from the command line.

| # | Command | Purpose |
|---|---------|---------|
| 8.1 | `RegistryUtils.exe test.json` | Execute the test file |
| 8.2 | `RegistryUtils.exe validate test.json` | Validate syntax without executing |

---

## 9. Output Format
> The structure of results returned after execution, showing step status (PASSED/FAILED) and a summary.

The tool returns results in the following format:

```
step-001|PASSED|Registry key created successfully
step-002|PASSED|Value written: AppName = MyTestApplication
step-003|FAILED|Access denied
SUMMARY|Total:3|Passed:2|Failed:1
```

**Format Breakdown:**
- **Step ID** - Unique identifier from your command
- **Status** - PASSED or FAILED
- **Message** - Detailed result or error message
- **SUMMARY** - Total commands, passed count, failed count

---

## 10. Common Properties
> A reference of all JSON properties (action, root, path, key_name, value, etc.) and whether they are required.

| # | Property | Required | Type | Description |
|---|----------|----------|------|-------------|
| 10.1 | `action` | Required | String | Action to perform (see Supported Actions) |
| 10.2 | `root` | Required | String | Registry root (HKCU/HKLM/HKCR/HKU/HKCC) |
| 10.3 | `path` | Required | String | Full path to registry key |
| 10.4 | `key_name` | Conditionally Required | String | Value name (required for value operations) |
| 10.5 | `value` | Conditionally Required | String/Number | Value data (required for write operations) |
| 10.6 | `value_type` | Conditionally Required | String | REG_SZ/REG_DWORD/REG_QWORD/REG_BINARY/etc |
| 10.7 | `wow_mode` | Required | String | BuildType/x86/x64/Both (optional) |
| 10.8 | `description` | Optional | String | Optional step description |
| 10.9 | `continueOnFailure` | Optional | Boolean | Continue on error (default: false) |

**Legend:**
- Required = Must be provided for all operations
- Conditionally Required = Required depending on the action type
- Optional = Can be omitted

---

## 11. Best Practices
> Recommended guidelines for writing reliable and maintainable registry test commands.

| # | Practice | Importance | Details |
|---|----------|-----------|---------|
| 11.1 | Always use unique IDs | Critical | Enables tracking and debugging |
| 11.2 | Use short form registry roots | High | HKCU/HKLM for cleaner JSON |
| 11.3 | Specify value_type explicitly | High | Ensures correct data handling |
| 11.4 | Test with validate first | High | Catch syntax errors early |
| 11.5 | Use continueOnFailure carefully | Medium | Prevents cascading failures |
| 11.6 | Document test scenarios | Medium | Add descriptions to steps |
| 11.7 | Clean up test data | Important | Delete keys/values after testing |

---

## 12. Error Handling
> Common errors you may encounter (access denied, key not found, etc.) and how to fix them.

| # | Error | Cause | Solution |
|---|-------|-------|----------|
| 12.1 | Access Denied | Insufficient privileges | Run as Administrator for HKLM |
| 12.2 | Key Not Found | Path doesn't exist | Verify key path exists first |
| 12.3 | Invalid Value Type | Mismatched data format | Match value_type to data type |
| 12.4 | WOW64 Issues | Architecture mismatch | Use appropriate wow_mode |

---

## Quick Navigation Guide



**For Testing:** Refer to [Section 7](#7-complete-test-example), [Section 8](#8-command-line-usage), [Section 9](#9-output-format), [Section 11](#11-best-practices)

**For Troubleshooting:** Jump to [Section 12](#12-error-handling)

---




