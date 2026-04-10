# XLSX File Operations Reference 📊

This document describes the XLSX file operations available in the automation framework, allowing you to search for and verify content within Excel files.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Actions](#actions)
    - [Value Verification Actions](#value-verification-actions)
- [Common Parameters](#common-parameters)
- [Examples](#examples)

## Overview

The XLSX file handler provides functionality to search for specific values within Excel spreadsheets and verify their presence or absence, which is useful for validating exported data or configuration stored in XLSX format.

## Quick Reference

| Icon | Action | Description |
|------|-----------|-------------|
| 🔍 | `value_should_be_present` | Verify a value exists in the XLSX file |
| 🚫 | `value_should_be_removed` | Verify a value does not exist in the XLSX file |

## Actions

All actions are performed using the `file_edit` operation type with the appropriate `action` parameter.

### Value Verification Actions

#### 🔍 `value_should_be_present`
Verifies that a specific value exists in the XLSX file.

**Parameters:**
- `file_path`: Path to the XLSX file
- `value`: The value to search for

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/reports/monthly_report.xlsx",
    "value": "Revenue: $125,000"
  }
}
```

#### 🚫 `value_should_be_removed`
Verifies that a specific value does not exist in the XLSX file.

**Parameters:**
- `file_path`: Path to the XLSX file
- `value`: The value to verify is absent

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/reports/filtered_data.xlsx",
    "value": "Confidential"
  }
}
```

## Common Parameters

These parameters apply to all XLSX operations:

| Parameter | Description |
|-----------|-------------|
| `action` | Specifies the XLSX action to perform (required) |
| `file_path` | Full path to the XLSX file (required) |
| `value` | The value to search for or verify absence (required) |


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

### Check if Value Exists in Report
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/exports/quarterly_report.xlsx",
    "value": "Total: 1,250,000"
  }
}
```

### Verify Sensitive Data Has Been Removed
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/exports/public_report.xlsx",
    "value": "SSN:"
  }
}
```

### Check for Product Information
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "C:/Reports/product_catalog.xlsx",
    "value": "Product ID: XYZ-123"
  }
}
```

> **Note:** The XLSX handler searches through all sheets, rows, and cells of the spreadsheet, regardless of formatting or cell type (string, numeric, date, formula results).