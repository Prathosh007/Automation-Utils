# JSON File Handler Reference 📝

## Overview

The JSONFileHandler provides tools to verify and manipulate JSON configuration values, supporting simple path navigation for value verification.

## Quick Reference

| Icon | Action | Description |
|------|--------|-------------|
| ✓ | `value_should_be_present` | Verify a JSON path has a specific value |
| ✗ | `value_should_be_removed` | Verify a JSON path or value doesn't exist |
| 🆕 | `create` | Create a new JSON file |
| 📝 | `write` | Write/merge content to a JSON file |
| 🔄 | `update` | Update a specific value at a JSON path |
| 🗑️ | `remove` | Remove a property from a JSON object |

## Verification Actions

### Value Presence

#### ✓ `value_should_be_present`
Verifies that a specific JSON path has the expected value.

**Parameters:**
- `file_path`: Directory path where the file is located
- `filename`: Name of the JSON file
- `path`: JSON path to check (using dot notation, e.g., "user.name" or "users[0].address")
- `value`: Expected value for the path
- `note` (optional): Key name to store the found value for later use

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "server-config.json",
    "path": "server.port",
    "value": "8080"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "app-settings.json",
    "path": "logging.level",
    "value": "DEBUG",
    "note": "LOG_LEVEL"
  }
}
```

### Value Absence

#### ✗ `value_should_be_removed`
Verifies that a specific JSON path doesn't exist or doesn't have a particular value.

**Parameters:**
- `file_path`: Directory path where the file is located
- `filename`: Name of the JSON file
- `path`: JSON path to check for absence
- `value` (optional): If specified, checks that the path doesn't have this specific value

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf",
    "filename": "security-config.json",
    "path": "security.debugMode"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf",
    "filename": "network-config.json",
    "path": "network.allowRemoteAccess",
    "value": "true"
  }
}
```

## Modification Actions

### Create JSON File

#### 🆕 `create`
Creates a new JSON file with specified content.

**Parameters:**
- `file_path`: Directory path where the file should be created
- `filename`: Name of the JSON file to create
- `content` (optional): Initial JSON content for the file
- `overwrite` (optional): Set to "true" to overwrite existing file

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "create",
    "file_path": "server_home/conf",
    "filename": "new-config.json",
    "content": "{ \"server\": { \"port\": 8080 } }"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "create",
    "file_path": "server_home/conf",
    "filename": "empty-config.json",
    "overwrite": "true"
  }
}
```

### Write JSON Content

#### 📝 `write`
Writes or merges content to a JSON file, with support for path-specific updates.

**Parameters:**
- `file_path`: Path to the target JSON file
- `content`: JSON content to write
- `path` (optional): Specific JSON path where content should be written
- `merge_mode` (optional): Set to "append" to add new properties without replacing existing ones
- `backup` (optional): Set to "true" to create a backup before modifying

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "write",
    "file_path": "server_home/conf/server-config.json",
    "content": "{ \"logging\": { \"level\": \"DEBUG\" } }",
    "backup": "true"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "write",
    "file_path": "server_home/conf/app-settings.json",
    "path": "security.ssl",
    "content": "{ \"enabled\": true, \"port\": 443 }",
    "merge_mode": "append"
  }
}
```

### Update JSON Value

#### 🔄 `update`
Updates a specific value at a JSON path.

**Parameters:**
- `file_path`: Path to the target JSON file
- `path`: JSON path to update
- `new_value`: Value to set at the specified path
- `backup` (optional): Set to "true" to create a backup before modifying

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "update",
    "file_path": "server_home/conf/server-config.json",
    "path": "server.port",
    "new_value": "9090"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "update",
    "file_path": "server_home/conf/security.json",
    "path": "logging.retention",
    "new_value": "30",
    "backup": "true"
  }
}
```

### Remove JSON Property

#### 🗑️ `remove`
Removes a key from a JSON object.

**Parameters:**
- `file_path`: Path to the target JSON file
- `path` (optional): JSON path to the parent object
- `key_name`: Name of the key to remove
- `backup` (optional): Set to "true" to create a backup before modifying

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "remove",
    "file_path": "server_home/conf/security.json",
    "key_name": "debugMode"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "remove",
    "file_path": "server_home/conf/logging.json",
    "path": "loggers.console",
    "key_name": "verbose",
    "backup": "true"
  }
}
```

## Path Navigation

The handler supports JSON path navigation using dot notation:

| Path Format | Example         | Description |
|-------------|-----------------|-------------|
| Simple property | `user.name`     | Access nested object properties |
| Array index | `users[0].name` | Access array elements by index |
| Root node | `${name}`       | Reference the root of the JSON document |


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

The `note` parameter allows you to store JSON values for later use in your test cases:

1. Add the `note` parameter to your operation to specify a variable name
2. The value found at the specified JSON path will be stored in this variable
3. Reference the stored value in subsequent operations using `${VARIABLE_NAME}` syntax

**Example workflow:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "api-config.json",
    "path": "endpoints.auth.port",
    "note": "AUTH_PORT"
  }
}
```
```json
{
"operation_type": "api_case",
"parameters": {
"url": "http://localhost:${AUTH_PORT}/api/login",
"method": "POST"
}
}
```

## Examples

### Verify Server Port
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "application.json",
    "path": "server.http.port",
    "value": "[0-9]+"
  }
}
```

### Ensure Debug Mode is Disabled
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf",
    "filename": "settings.json",
    "path": "application.debug",
    "value": "true"
  }
}
```

### Store Authentication Type for Later Use
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "security.json",
    "path": "authentication.type",
    "note": "AUTH_TYPE"
  }
}
```