# Service Management Handler Reference 🔧

This document describes the Windows service management capabilities provided by the `ServiceManagementHandler` class in the GOAT automation framework.

## Overview

The service management handler allows you to control Windows services (start, stop, restart) and query service properties like status, startup type, description, and logon account as part of your test automation.

## Quick Reference

| Icon | Action            | Description                                    |
|------|-------------------|------------------------------------------------|
| ▶️   | `start`           | Start a Windows service                        |
| ⏹️   | `stop`            | Stop a Windows service                         |
| 🔄   | `restart`         | Restart a Windows service (stop then start)    |
| 📊   | `status`          | Check the current status of a service          |
| 🚀   | `set_startup`     | Change service startup type                    |
| 📝   | `set_description` | Set service description                        |
| 📖   | `get_description` | Retrieve service description                   |
| 🔍   | `get_startup`     | Retrieve service startup type                  |
| 👤   | `get_logon`       | Retrieve service logon account                 |

## Actions

All actions are performed using the `service_actions` operation type with the appropriate parameters.

### Required Parameters

- `service_name`: The name of the Windows service (required)
- `action`: The service action to perform (required)

### ▶️ `start`

Starts a Windows service.

**Parameters:**
- `service_name`: Service name to start (required)
- `action`: `"start"`

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "start"
  }
}
```

---

### ⏹️ `stop`

Stops a Windows service.

**Parameters:**
- `service_name`: Service name to stop (required)
- `action`: `"stop"`

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "stop"
  }
}
```

---

### 🔄 `restart`

Restarts a Windows service (stops and then starts it).

**Parameters:**
- `service_name`: Service name to restart (required)
- `action`: `"restart"`

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "restart"
  }
}
```

---

### 📊 `status`

Checks the current status of a service and optionally verifies expected state.

**Parameters:**
- `service_name`: Service name to check (required)
- `action`: `"status"`
- `expect` (optional): Expected state - `"serviceRunning"` or `"serviceStopped"`
- `note` (optional): Variable name to store the current status

**Possible Status Values:**
- `RUNNING` - Service is running
- `STOPPED` - Service is stopped
- `START_PENDING` - Service is starting
- `STOP_PENDING` - Service is stopping
- `TRANSITIONAL` - Service is in another transitional state
- `UNKNOWN` - Status could not be determined

**Examples:**

Check service status:
```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "status"
  }
}
```

Check and verify service is running:
```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "status",
    "expect": "serviceRunning"
  }
}
```

Store status in variable:
```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "status",
    "note": "SPOOLER_STATUS"
  }
}
```

---

### 🚀 `set_startup`

Changes the startup type of a service.

**Parameters:**
- `service_name`: Service name (required)
- `action`: `"set_startup"`
- `startup_type`: One of the following (required):
    - `"auto"` or `"Automatic"` - Automatic startup
    - `"demand"` or `"Manual"` - Manual startup
    - `"disabled"` or `"Disabled"` - Disabled
    - `"delayed-auto"` or `"Delayed-Auto"` - Automatic (Delayed Start)

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "set_startup",
    "startup_type": "auto"
  }
}
```

---

### 📝 `set_description`

Sets or updates the description of a service.

**Parameters:**
- `service_name`: Service name (required)
- `action`: `"set_description"`
- `description`: The description text (optional, empty string if not provided)

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "set_description",
    "description": "Manages print jobs"
  }
}
```

---

### 📖 `get_description`

Retrieves the description of a service.

**Parameters:**
- `service_name`: Service name (required)
- `action`: `"get_description"`
- `note` (optional): Variable name to store the description

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "get_description",
    "note": "SPOOLER_DESC"
  }
}
```

---

### 🔍 `get_startup`

Retrieves the startup type of a service.

**Parameters:**
- `service_name`: Service name (required)
- `action`: `"get_startup"`
- `note` (optional): Variable name to store the startup type

**Possible Return Values:**
- `AUTO` - Automatic startup
- `DELAYED-AUTO` - Automatic (Delayed Start)
- `DEMAND` - Manual startup
- `DISABLED` - Disabled
- `BOOT` - Boot start
- `SYSTEM` - System start
- `UNKNOWN` - Could not be determined

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "get_startup",
    "note": "SPOOLER_STARTUP"
  }
}
```

---

### 👤 `get_logon`

Retrieves the logon account (SERVICE\_START\_NAME) for a service.

**Parameters:**
- `service_name`: Service name (required)
- `action`: `"get_logon"`
- `note` (optional): Variable name to store the logon account

**Example:**

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "service_name": "Spooler",
    "action": "get_logon",
    "note": "SPOOLER_LOGON"
  }
}
```

---

## Complete Use Cases

### Example 1: Restart Service and Verify Status

```json
[
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "Spooler",
      "action": "restart"
    }
  },
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "Spooler",
      "action": "status",
      "expect": "serviceRunning"
    }
  }
]
```

### Example 2: Get Service Configuration

```json
[
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "Spooler",
      "action": "get_startup",
      "note": "STARTUP_TYPE"
    }
  },
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "Spooler",
      "action": "get_description",
      "note": "SERVICE_DESC"
    }
  },
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "Spooler",
      "action": "get_logon",
      "note": "LOGON_ACCOUNT"
    }
  }
]
```

### Example 3: Configure Service Settings

```json
[
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "MyService",
      "action": "stop"
    }
  },
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "MyService",
      "action": "set_startup",
      "startup_type": "Manual"
    }
  },
  {
    "operation_type": "service_actions",
    "parameters": {
      "service_name": "MyService",
      "action": "set_description",
      "description": "Custom service for automation testing"
    }
  }
]
```

---
