# Text File Operations Reference

This document describes the text file operations available in the framework, focusing on operations that check for presence or absence of values in text files.

## Table of Contents

- [Overview](#overview)
- [Actions](#actions)
  - [value_should_be_present](#value_should_be_present)
  - [value_should_be_removed](#value_should_be_removed)
- [Using Regex for Value Extraction](#using-regex-for-value-extraction)
- [Reusing Extracted Values](#reusing-extracted-values)
- [Examples](#examples)

## Overview

Text file operations allow you to check for the presence or absence of values in text files, and optionally extract values using regex patterns for later use in your test flows.

## Actions

### value_should_be_present

Verifies that a specific value exists in a text file and optionally extracts data using regex.

**Parameters:**
- `file_path`: Path to the text file
- `value`: Text to search for in the file
- `regex` (optional): Regular expression pattern to extract data from the matching line
- `note` (optional): Variable name to store the extracted value for later use

**Example:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/application.txt",
    "value": "Server started on port",
    "regex": "[0-9]+",
    "note": "SERVER_PORT"
  }
}
```

### value_should_be_removed

Verifies that a specific value does NOT exist in a text file.

**Parameters:**
- `file_path`: Path to the text file
- `value`: Text to confirm is not present in the file

**Example:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf/error.txt",
    "value": "Connection refused"
  }
}
```

### Getting Regex Patterns
You can use any AI tool or online regex generator to create and test regular expressions.

***Avoiding Escape Characters:***  
It’s recommended to avoid escape characters like `\d`, `\w`, `\s`,....... These can behave unpredictably in some environments.

***Best Practice:***  
Before using regex in your code, always test it using an AI tool or online tester.

The extraction process works as follows:

1. The file is searched for lines containing the `value` parameter text
2. When a matching line is found, the `regex` pattern is applied to that line
3. The extracted value is stored and can be referenced in later steps

This combination of `value` and `regex` allows for powerful data extraction and validation in your test automation workflows.

## Reusing Extracted Values

When using the `note` parameter, extracted values are stored for later use in subsequent operations:

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/startup.txt",
    "value": "Server started on port",
    "regex": "[0-9]+",
    "note": "SERVER_PORT"
  }
}
```

The extracted value stored in `SERVER_PORT` can be referenced in later operations using the `${SERVER_PORT}` syntax, making it easy to create dynamic test flows based on values found in text files.


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

### Extract Database Connection Information

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/database.txt",
    "value": "Database connection established on port",
    "regex": "[0-9]+",
    "note": "DB_PORT"
  }
}
```

### Verify Error Is Not Present

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf/system.txt",
    "value": "Failed to initialize"
  }
}
```

### Extract API Key from Text File

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/api.txt",
    "value": "Generated API key:",
    "regex": "[a-zA-Z0-9]{32}",
    "note": "API_KEY"
  }
}
```

### Extract IP Address

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/network.txt",
    "value": "Server bound to address",
    "regex": "[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+",
    "note": "SERVER_IP"
  }
}
```