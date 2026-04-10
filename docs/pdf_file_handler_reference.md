# PDF File Operations Reference 📄

This document describes the PDF file operations available in the GOAT automation framework, allowing you to search and verify content within PDF documents.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Actions](#actions)
    - [Value Presence](#value-presence)
    - [Value Absence](#value-absence)
- [Common Parameters](#common-parameters)
- [Examples](#examples)
- [Regex Usage](#regex-usage)

## Overview

The PDF file operation provides capabilities to verify the presence or absence of specific text or patterns within PDF documents, with support for exact text matching or regular expressions.

## Quick Reference

| Icon | Action | Description |
|------|--------|-------------|
| ✅ | `value_should_be_present` | Verify text or pattern exists in PDF |
| ❌ | `value_should_be_removed` | Verify text or pattern does not exist in PDF |

## Actions

All actions are performed using the `pdf_file_operation` operation type with the appropriate `action` parameter.

### Value Presence

#### ✅ `value_should_be_present`
Verifies that specific text or a pattern exists in a PDF file.

**Parameters:**
- `file_path`: Path to the PDF file
- `exact_value`: Exact text to search for (use this or `value`)
- `value`: Regular expression pattern to search for (use this or `exact_value`)
- `key` (optional): Key text to search for first, then check value in the same line
- `note` (optional): Key name to store the found value for later use in test cases

### Getting Regex Patterns
You can use any AI tool or online regex generator to create and test regular expressions.

***Avoiding Escape Characters:***  
It’s recommended to avoid escape characters like `\d`, `\w`, `\s`,....... These can behave unpredictably in some environments.

***Best Practice:***  
Before using regex in your code, always test it using an AI tool or online tester.
**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/reports/monthly_report.pdf",
    "exact_value": "Total Revenue: $45,000"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/reports/monthly_report.pdf",
    "key": "Invoice Number:",
    "value": "INV-[0-9]{6}",
    "note": "INVOICE_NUMBER"
  }
}
```

### Value Absence

#### ❌ `value_should_be_removed`
Verifies that specific text or a pattern does not exist in a PDF file.

**Parameters:**
- `file_path`: Path to the PDF file
- `exact_value`: Exact text that should not be present (use this or `value`)
- `value`: Regular expression pattern that should not match (use this or `exact_value`)
- `key` (optional): Key text to search for first, then check value absence in the same line

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/reports/redacted_report.pdf",
    "exact_value": "CONFIDENTIAL"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/reports/redacted_report.pdf",
    "key": "Credit Card:",
    "value": "[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}"
  }
}
```

## Common Parameters

These parameters apply to all actions:

| Parameter | Description |
|-----------|-------------|
| `action` | Specifies the PDF operation to perform (required) |
| `file_path` | Path to the PDF file (required) |
| `exact_value` | Exact text to search for (mutually exclusive with `value`) |
| `value` | Regular expression pattern to search for (mutually exclusive with `exact_value`) |
| `key` | Key text to narrow the search to specific lines |
| `note` | Key name to store action results for later use in test cases |


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


## Using Notes for Value Capture

The `note` parameter allows you to extract and store text values from PDFs for later use in your test cases:

1. Add the `note` parameter to your operation to specify a variable name
2. The matching text found in the PDF will be stored in this variable
3. Reference the stored value in subsequent operations using `${VARIABLE_NAME}` syntax

**Example workflow:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/reports/user_report.pdf",
    "key": "User ID:",
    "value": "[A-Z0-9]+",
    "note": "USER_ID"
  }
}

```

```json
{
  "operation_type": "api_case",
  "parameters": {
    "url": "http://localhost:8080/api/users/${USER_ID}",
    "method": "GET"
  }
}
```



## Examples

### Check for Exact Text in PDF


### Extract Invoice Number Using Regex and Save to Variable
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/invoices/latest.pdf",
    "key": "Invoice:",
    "value": "INV-[0-9]{6}",
    "note": "CURRENT_INVOICE_NUMBER"
  }
}
```

### Verify Sensitive Information is Removed
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/reports/public_version.pdf",
    "exact_value": "For Internal Use Only"
  }
}
```