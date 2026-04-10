# Configuration File Verification Reference 📝

## Overview

The ConfigFileHandler provides tools to verify configuration values in different types of configuration files (.properties, .ini, .conf, .key), handling different formats like key=value and key:value pairs.

## Quick Reference

| Icon | Action | Description |
|------|--------|-------------|
| ✓ | `value_should_be_present` | Verify a key has a specific value |
| ✗ | `value_should_be_removed` | Verify a key or value doesn't exist |
| ⚙️ | `update` | Update or add a key-value pair |
| 🔄 | `replace` | Replace a key name, preserving its value |
| ❌ | `delete` | Remove a key-value pair |

## Verification Actions

### Value Presence

#### ✓ `value_should_be_present`
Verifies that a specific configuration key has the expected value.

**Parameters:**
- `file_path`: Directory path where the file is located
- `filename`: Name of the configuration file
- `key`: Configuration key to check
- `value`: Expected value for the key.(If you want to get the value in the key then dont give the value parameter it will automatically store in the note value)
- `note` (optional): Key name to store the found value for later use

**Use Cases:**
**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "/path/to/conf",
    "filename": "application.properties",
    "key": "port.number",
    "value": "8080"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "/path/to/conf",
    "filename": "settings.ini",
    "key": "debug_mode",
    "value": "true"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "/path/to/conf",
    "filename": "server.conf",
    "key": "max_connections",
    "note": "SERVER_MAX_CONNECTIONS"
  }
}
```

### Value Absence

#### ✗ `value_should_be_removed`
Verifies that a specific configuration key doesn't exist or doesn't have a particular value.

**Parameters:**
- `file_path`: Directory path where the file is located or entire file path
- `filename`: Name of the configuration file
- `key`: Configuration key to check for absence
- `value` (optional): If specified, checks that the key doesn't have this specific value

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "/path/to/conf",
    "filename": "security.properties",
    "key": "debug_mode"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "/path/to/conf",
    "filename": "settings.ini",
    "key": "allow_remote_access",
    "value": "true"
  }
}
```

### Key Update

#### ⚙️ `update`
Updates a key's value in a configuration file, or adds the key if it doesn't exist.

**Parameters:**
- `file_path`: Directory path where the file is located or entire file path
- `filename`: Name of the configuration file
- `key_to_update`: Configuration key to update
- `new_value`: New value to set for the key
- `section` (optional): For INI files, the section containing the key

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "update",
    "file_path": "server_home/conf/server.properties",
    "key_to_update": "max_connections",
    "new_value": "200"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "update",
    "file_path": "server_home/conf/server.properties",
    "key_to_update": "max_connections",
    "new_value": "200"
  }
}
```
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "update",
    "file_path": "server_home/conf",
    "filename": "settings.ini",
    "key_to_update": "timeout",
    "new_value": "30"
  }
}
```
### Key Replacement
#### 🔄 `replace`
Replaces a key while preserving its value in a configuration file.

**Parameters:**
- `file_path`: Directory path where the file is located or entire file path
- `filename`: Name of the configuration file
- `key_to_replace`: Original configuration key
- `new_key`: New name for the key (value is preserved)

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "replace",
    "file_path": "server_home/conf",
    "filename": "application.properties",
    "key_to_replace": "jdbc.url",
    "new_key": "database.url"
  }
}
```

### Key Deletion
#### ❌ `delete`
Removes a key-value pair from a configuration file.

**Parameters:**
- `file_path`: Directory path where the file is located
- `filename`: Name of the configuration file
- `key_to_delete`: Configuration key to remove

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "delete",
    "file_path": "server_home/conf",
    "filename": "application.properties",
    "key_to_delete": "debug_mode"
  }
}
```


## Common Parameters

| Parameter | Description |
|-----------|-------------|
| `file_path` | Directory path where the configuration file is located |
| `filename` | Name of the configuration file |
| `key` | Configuration key to check |
| `value` | Expected value (for presence) or unwanted value (for absence) |
| `note` | Name to store values for later use in test cases |


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


## Supported File Formats

The handler supports multiple configuration file formats:

| Extension | Format | Handling |
|-----------|--------|----------|
| `.properties`, `.props`, `.prop` | Java Properties | key=value format |
| `.ini` | INI | key=value format |
| `.conf` | Configuration | key=value or key:value formats |

## Using Notes for Value Capture

You can use the `note` parameter to store configuration values for later use in your test cases:

1. Add the `note` parameter to your operation to specify a variable name
2. The value found for the specified key will be stored in this variable
3. Reference the stored value in subsequent operations using `${VARIABLE_NAME}` syntax

**Example workflow:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "application.properties",
    "key": "server.port",
    "note": "SERVER_PORT"
  }
}
```
```json
{
  "operation_type": "api_case",
  "parameters": {
    "url": "http://localhost:${SERVER_PORT}/api/status",
    "method": "GET"
  }
}
```


## Examples

### Verify Database Connection String
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "database.properties",
    "key": "db.url",
    "value": "jdbc:mysql://localhost:3306/appdb"
  }
}
```

### Verify Debug Mode is Disabled
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf",
    "filename": "app.ini",
    "key": "debug",
    "value": "true"
  }
}
```

### Store Server Port for Later Use
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "server.conf",
    "key": "http.port",
    "note": "SERVER_PORT"
  }
}
```

### Check Security Setting
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf",
    "filename": "security.properties",
    "key": "password.strength",
    "value": "high"
  }
}
```
### Change the value of a specific key value
````json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "update",
    "file_path": "docs/",
    "filename": "config_file_handler_reference.md",
    "key_to_update": "old_setting",
    "new_value": "new_value"
  }
}
````

### Replace a Key Name
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "replace",
    "file_path": "server_home/conf",
    "filename": "application.properties",
    "key_to_replace": "old_key_name",
    "new_key": "new_key_name"
  }
}
```

### Delete a Key
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "delete",
    "file_path": "server_home/conf",
    "filename": "application.properties",
    "key_to_delete": "unwanted_key"
  }
}
```