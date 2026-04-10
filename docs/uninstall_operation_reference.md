# UnInstall Operation Reference 🗑️

This document describes the uninstall operation available in the GOAT automation framework, allowing you to automate product uninstallation processes using AutoIT scripts.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Operation Details](#operation-details)
- [Parameters](#parameters)
- [Path Variables](#path-variables)
- [Examples](#examples)

## Overview

The uninstall operation provides a streamlined interface to execute AutoIT scripts that handle product uninstallation processes. This operation runs the specified AutoIT script with provided arguments and captures both the exit code and output for verification.

## Quick Reference

| Operation Type | Description |
|----------------|-------------|
| `uninstall`    | Executes an AutoIT script to uninstall software |

## Operation Details

The uninstall operation executes an AutoIT script with specified arguments to automate software uninstallation.

## Parameters

| Parameter | Description                                                                       | Required |
|-----------|-----------------------------------------------------------------------------------|----------|
| `autoit_path` | Path to the directory containing the AutoIT executable. The executable file must be named `AutoIt3.exe`. | Yes |
| `args`        | Comma-separated arguments to pass to the AutoIT script.                                                | Yes |

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

### Basic Uninstall Operation

```json
{
  "operation_type": "uninstall",
  "parameters": {
    "autoit_path": "tool_home/AutoIT/",
    "args": "tool_home/AutoIT/scripts/UninstallScript.au3, product_name=ServiceDesk"
  }
}
```

### Uninstall with Additional Options

```json
{
  "operation_type": "uninstall",
  "parameters": {
    "autoit_path": "tool_home/AutoIT/",
    "args": "tool_home/AutoIT/scripts/UninstallScript.au3, product_name=PatchManager, version=10.1.2, remove_data=true, log_level=verbose"
  }
}
```

### Uninstall with Custom Path

```json
{
  "operation_type": "uninstall",
  "parameters": {
    "autoit_path": "C:/Automation/Scripts/AutoIT/",
    "args": "C:/Automation/Scripts/AutoIT/scripts/CustomUninstall.au3, app_path=C:/Program Files/MyApp, remove_registry=true"
  }
}
```

### Handling Exit Codes

The operation automatically handles exit codes from the AutoIT script:
- Exit code 0: Operation successful
- Non-zero exit code: Operation failed, logs details and path to AutoIT log file

> **Note:**  
> This operation can be used to run any AutoIT script. An exit code of `0` indicates success.