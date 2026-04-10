# Machine Operation Reference: get_machine_spec 🖥️

This document describes the `get_machine_spec` action available in the GOAT automation framework, allowing you to retrieve system information from machines.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Operation Details](#operation-details)
- [Parameters](#parameters)
- [Examples](#examples)

## Overview

The `get_machine_spec` action allows you to retrieve various system information from the machine, including computer name, domain information, OS details, hardware information, and user settings.

## Quick Reference

| Operation Type | Action | Description |
|----------------|--------|-------------|
| `machine_operation` | `get_machine_spec` | Retrieves specified system information from the machine |

## Operation Details

The `get_machine_spec` action executes PowerShell commands to retrieve requested information about the machine. The action returns the information in the operation remarks and can be used to validate system configuration or capture details for reporting. Additionally, it saves the retrieved value in the `note` field under the specified key name. So you can use this note in later by ${Key name}.

## Parameters

| Parameter | Description                                                | Required |
|-----------|------------------------------------------------------------|----------|
| `info_type` | The type of information to retrieve                        | Yes      |
| `note` | The value to be saved under the specified key name in GOAT | Yes      |
### Supported Information Types

| info_type | Description                         |
|-----------|-------------------------------------|
| `computer name` | Retrieves the machine's hostname    |
| `domain name` | Retrieves the domain the machine is joined to |
| `fqdn` | Retrieves the fqdn                  |
| `os name` | Retrieves the operating system name |
| `os version` | Retrieves the operating system version |
| `architecture` | Retrieves the OS architecture       |
| `time zone` | Retrieves the system time zone      |
| `language` | Retrieves the system language |
| `logged user` | Retrieves the currently logged in username |
| `mac address` | Retrieves the MAC address of the active network adapter |
| `service tag` | Retrieves the system serial number/service tag |

## Examples

### Get Computer Name

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "get_machine_spec",
    "info_type": "computer name",
    "note": "testMachineName"
  }
}
```

### Get Operating System Details

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "get_machine_spec",
    "info_type": "os name",
    "note": "testOSName"
  }
}
```

### Get System Architecture

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "get_machine_spec",
    "info_type": "architecture",
    "note": "testArchitecture"
  }
}
```

### Get Service Tag

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "get_machine_spec",
    "info_type": "service tag",
    "note": "testServiceTag"
  }
}
```