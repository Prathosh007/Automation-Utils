# Machine Operations Reference 🖥️

This document describes the machine operations available in the GOAT automation framework, allowing you to perform various system management tasks on Windows machines.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Operation Details](#operation-details)
    - [get_machine_spec](#get_machine_spec)
    - [restart](#restart)
    - [rename](#rename)
    - [connect_network](#connect_network)
    - [disconnect_network](#disconnect_network)
    - [change_domain](#change_domain)
    - [change_datetime](#change_datetime)
    - [change_browser](#change_browser)
    - [change_timezone](#change_timezone)

## Overview

The GOAT automation framework provides various machine operations to manage and control Windows systems. These operations allow you to perform tasks like retrieving system information, restarting machines, changing network settings, and modifying system configurations.

## Quick Reference

| Action | Description |
|--------|-------------|
| `get_machine_spec` | Retrieves specified system information from the machine |
| `restart` | Restarts the machine |
| `rename` | Changes the computer name |
| `connect_network` | Enables network connectivity by allowing firewall outbound connections |
| `disconnect_network` | Disables network connectivity by blocking firewall outbound connections |
| `change_domain` | Changes domain membership or joins a workgroup |
| `change_datetime` | Changes system date and time |
| `change_browser` | Changes the default browser |
| `change_timezone` | Changes the system timezone |

## Operation Details

### get_machine_spec

# Information Types Reference Table

Below is a comprehensive table of all information types available through the `get_machine_spec` operation:

| Information Type         | Description                                               | Example Value                        |
|-------------------------|-----------------------------------------------------------|--------------------------------------|
| `computer name`         | Machine's hostname                                        | DESKTOP-ABC123                      |
| `domain name`           | Domain the machine is joined to                           | example.com                          |
| `fqdn`                  | Fully qualified domain name                               | desktop-abc123.example.com           |
| `os name`               | Operating system name                                     | Microsoft Windows 10 Pro             |
| `os version`            | Operating system version                                  | 10.0.19045                           |
| `architecture`          | OS architecture                                           | 64-bit                               |
| `time zone`             | System time zone                                          | Eastern Standard Time                |
| `language`              | System language                                           | en-US                                |
| `logged user`           | Currently logged in username                              | john.doe                             |
| `mac address`           | MAC address of active network adapter                     | 00:1A:2B:3C:4D:5E                    |
| `service tag`           | System serial number/service tag                          | ABC123XYZ                            |
| `admin$ share`          | ADMIN$ share status                                       | Enabled                              |
| `bios mode`             | BIOS firmware type                                        | UEFI                                 |
| `bios version`          | SMBIOS BIOS version                                       | 1.23.4                               |
| `current time`          | Current date and time                                     | 01/15/2024 14:30:45                  |
| `day light saving time` | Daylight saving time name                                 | Eastern Daylight Time                |
| `is day light saving`   | Daylight saving time status                               | True                                 |
| `local ip address`      | Local IP addresses                                        | 192.168.1.100                        |
| `last ip address list`  | IP addresses list                                         | 192.168.1.100,10.0.0.15              |
| `local domain type`     | Domain role                                               | 1 (Member Workstation)               |
| `local language`        | Two-letter ISO language name                              | en                                   |
| `local machine uuid`    | Machine's UUID                                            | 12345678-ABCD-1234-EFGH-123456789012 |
| `local operating system`| OS with architecture                                      | Microsoft Windows 10 Pro (64-bit)    |
| `local service pack`    | Windows version with architecture                         | Windows 10 Version 22H2 (64-bit)     |
| `local time zone offset`| Timezone offset in minutes                                | -300                                 |
| `machine type`          | Device form factor                                        | Laptop                               |
| `standard time`         | Standard time name                                        | Eastern Standard Time                |
| `system spec name`      | Computer system name                                      | Dell XPS 15 9570                     |
| `tpm status`            | TPM ready status                                          | True                                 |
| `tpm version`           | TPM specification version (For Workstation Machines Only) | 2.0                           |
| `utc offset`            | UTC offset                                                | -05:00                               |
| `utc time`              | Current UTC time                                          | 01/15/2024 19:30:45                  |
| `os full name`          | Full OS name                                              | Microsoft Windows 10 Pro 22H2        |
| `os last bootup time`   | Last OS start time                                        | 01/12/2024 08:15:30                  |
| `os install date`       | OS installation date                                      | 06/15/2023 10:20:45                  |
| `identifying number`    | System identifying number                                 | ABC123456789                         |
| `wmi service`           | WMI service status                                        | Running                              |
| `av guid name`          | Installed antivirus product                               | Windows Defender                     |

## Usage Example

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "get_machine_spec",
    "info_type": "computer name",
    "note": "systemHostname"
  }
}
```

#### Examples

Get Computer Name:
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

Get Operating System Details:
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

### restart

Restarts the target machine using the Windows shutdown command with restart flag.

#### Parameters

None required.

#### Example

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "restart"
  }
}
```

### rename

Renames the computer to the specified name using Windows Management Instrumentation (WMI).

#### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `new_name` | The new name for the computer | Yes |

#### Example

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "rename",
    "new_name": "NEWCOMPUTER01"
  }
}
```

[//]: # (### connect_network)

[//]: # ()
[//]: # (Enables network connectivity by configuring the Windows Firewall to allow outbound connections.)

[//]: # ()
[//]: # (#### Parameters)

[//]: # ()
[//]: # (None required.)

[//]: # ()
[//]: # (#### Example)

[//]: # ()
[//]: # (```json)

[//]: # ({)

[//]: # (  "operation_type": "machine_operation",)

[//]: # (  "parameters": {)

[//]: # (    "action": "connect_network")

[//]: # (  })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (### disconnect_network)

[//]: # ()
[//]: # (Disables network connectivity by blocking all outbound connections through the Windows Firewall.)

[//]: # ()
[//]: # (#### Parameters)

[//]: # ()
[//]: # (None required.)

[//]: # ()
[//]: # (#### Example)

[//]: # ()
[//]: # (```json)

[//]: # ({)

[//]: # (  "operation_type": "machine_operation",)

[//]: # (  "parameters": {)

[//]: # (    "action": "disconnect_network")

[//]: # (  })

[//]: # (})

[//]: # (```)

### change_domain

Changes the domain membership of the machine or joins a workgroup.

#### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `domain` | The domain to join (leave empty to join a workgroup) | Conditional |
| `user` | Username with domain join permissions | Yes |
| `password` | Password for the specified user | Yes |
| `do_restart` | Whether to restart after domain change (true/false) | No |
| `workgroup` | The workgroup to join (only when leaving a domain) | Conditional |

#### Examples

Join Domain:
```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "change_domain",
    "domain": "example.com",
    "user": "admin",
    "password": "SecurePassword123",
    "do_restart": "true"
  }
}
```

Join Workgroup:
```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "change_domain",
    "workgroup": "WORKGROUP",
    "user": "admin",
    "password": "SecurePassword123",
    "do_restart": "true"
  }
}
```

### change_datetime

Changes the system date and time.

#### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `date` | Date in format dd-MM-yyyy | Yes |
| `time` | Time in format HH:mm:ss | Yes |

#### Example

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "change_datetime",
    "date": "25-12-2023",
    "time": "13:30:00"
  }
}
```

[//]: # (### change_browser)

[//]: # ()
[//]: # (Changes the default browser by modifying Windows registry associations.)

[//]: # ()
[//]: # (#### Parameters)

[//]: # ()
[//]: # (| Parameter | Description | Required |)

[//]: # (|-----------|-------------|----------|)

[//]: # (| `browser_path` | Browser name to set as default | Yes |)

[//]: # ()
[//]: # (#### Example)

[//]: # ()
[//]: # (```json)

[//]: # ({)

[//]: # (  "operation_type": "machine_operation",)

[//]: # (  "parameters": {)

[//]: # (    "action": "change_browser",)

[//]: # (    "browser_path": "Google Chrome")

[//]: # (  })

[//]: # (})

[//]: # (```)

### change_timezone

Changes the system timezone.

#### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `timezone_id` | The timezone ID to set | Yes |

#### Example

```json
{
  "operation_type": "machine_operation",
  "parameters": {
    "action": "change_timezone",
    "timezone_id": "Eastern Standard Time"
  }
}
```
