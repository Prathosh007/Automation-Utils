# File Folder Operations Reference 📁

[//]: # (> **Powerful File Management for Test Automation**)

This document describes the comprehensive file and folder operations available in the GOAT automation framework, allowing you to manipulate, verify, and control file system resources with precision.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Actions](#actions)
  - [Basic Actions](#basic-actions)
  - [File/Folder Existence](#filefolder-existence)
  - [File/Folder Properties](#filefolder-properties)
  - [Permission Actions](#permission-actions)
  - [Share Permission Actions](#share-permission-actions)
- [Common Parameters](#common-parameters)
- [Path Variables](#path-variables)
- [Examples](#examples)
- [Platform-Specific Behavior](#platform-specific-behavior)

## Overview

The file folder operation provides a powerful interface to perform common file system tasks like copying, moving, and deleting files/folders, as well as checking and modifying permissions and file properties across different operating systems.

## Quick Reference

| Icon | Action | Description |
|----|-----------|-------------|
| 📄 | `create` | Create files or folders |
| 📋 | `copy` | Copy files or folders |
| ✂️ | `move` | Move files or folders |
| 🗑️ | `delete` | Delete files or folders |
| 🔍 | `check_presence` | Verify file/folder exists |
| 🚫 | `verify_absence` | Verify file/folder doesn't exist |
| 📊 | `get_size` | Check file/folder size |
| 📊 | `check_size` | Verify file/folder size |
| 🕒 | `check_last_modified` | Check modification time |
| ➕ | `add_permission`      | Add file/folder permissions for a user      |
| 🔒 | `check_permission` | Verify file permissions |
| 🔐 | `set_permission` | Set file permissions |
| 🔓 | `remove_permission` | Remove file permissions |
| 🖧 | `set_share_permission` | Set Windows share permissions |
| 🖧 | `remove_share_permission` | Remove Windows share permissions |
| 🔄 | `rename` | Rename files or folders |

## Actions

All actions are performed using the `file_folder_operation` operation type with the appropriate `action` parameter.

### Basic Actions

### 📄 `create`
Creates a new file or folder at the specified path.

**Parameters:**
- `file_path`: Path where the file or folder should be created
- `type`: Type of item to create, must be either "file" or "folder"
- `size`: (optional): Size in MB for file creation (default: 0)
- `delete_if_exists` (optional): Set to "true" to delete existing files/folders before creation (default: false)

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "create",
    "file_path": "server_home/conf/custom/",
    "type": "folder",
    "delete_if_exists": "true"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "create",
    "file_path": "server_home/conf/custom/config.properties",
    "type": "file",
    "size": "10",
    "delete_if_exists": "false"
  }
}
```

### 📋 `copy`
Copies a file or directory from source to target.

**Parameters:**
- `source`: Source file/folder path
- `target`: Target file/folder path
- `overwrite` (optional): Set to "true" to replace existing files (default: false)

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "copy",
    "source": "server_home/conf/server.properties",
    "target": "server_home/backup/",
    "overwrite": "true"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "copy",
    "source": "D:/conf/",
    "target": "C:/backup/",
    "overwrite": "false"
  }
}
```

### ✂️ `move`
Moves a file or directory from source to target.

**Parameters:**
- `source`: Source file/folder path
- `target`: Target file/folder path
- `overwrite` (optional): Set to "true" to replace existing files (default: false)

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "move",
    "source": "server_home/logs/old_log.txt",
    "target": "server_home/archive/",
    "overwrite": "true"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "move",
    "source": "C:/temp/downloaded_files/",
    "target": "server_home/imports/",
    "overwrite": "false"
  }
}
```

### 🗑️ `delete`
Deletes a file or directory.

**Parameters:**
- `file_path`: File/folder path to delete

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "delete",
    "file_path": "server_home/temp/generated_report.pdf"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "delete",
    "file_path": "server_home/temp"
  }
}
```

### 🔄 `rename`
Renames a file or folder to a new name within the same parent directory.

**Parameters:**
- `source`: The source file/folder path to be renamed
- `target_name`: The new name for the file/folder (not the full path, just the name)

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "rename",
    "source": "server_home/logs/debug.log",
    "target_name": "debug_old.log"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "rename",
    "source": "server_home/conf/custom",
    "target_name": "custom_backup"
  }
}
```

### File/Folder Existence

### 🔍 `check_presence`
Verifies that a file or folder exists.

**Parameters:**
- `file_path`: Directory path (Name the folder)
- `filename`: Name of file to check

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_presence",
    "file_path": "server_home/conf/",
    "filename": "server.xml"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_presence",
    "file_path": "C:/Program Files/Application/"
  }
}
```

### 🚫 `verify_absence`
Verifies that a file or folder does not exist.

**Parameters:**
- `file_path`: Directory path (Name the folder)
- `filename`: Name of file to check absence of

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "verify_absence",
    "file_path": "server_home/temp/",
    "filename": "temp.bak"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "verify_absence",
    "file_path": "server_home/logs/"
  }
}
```

### File/Folder Properties

### 📊 `get_size` or `check_size`
Gets or verifies the size of a file/folder.

**Parameters:**
- `file_path`: File/folder path
- `expected_size` (optional): Expected size in MB
- `note`: Key name to store the size value for later use in test cases

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "get_size",
    "file_path": "server_home/logs/server.log",
    "note": "SERVER_LOG_SIZE"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_size",
    "file_path": "server_home/webapps/app.war",
    "expected_size": "45.2"
  }
}
```

### 🕒 `check_last_modified`
Checks when a file was last modified.

**Parameters:**
- `file_path`: File/folder path
- `expected_time`: Expected time in epoch milliseconds or formatted as "dd-MM-yyyy HH:mm"
- `comparison` (optional): Use "before" or "after" for relative time comparisons
- `note`: Key name to store the modification time for later use in test cases

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_last_modified",
    "file_path": "server_home/logs/server.log",
    "expected_time": "01-01-2023 14:30",
    "comparison": "after"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_last_modified",
    "file_path": "C:/data/reports/monthly.xlsx",
    "expected_time": "1672579800000",
    "note": "REPORT_MODIFIED_TIME"
  }
}
```

### Permission Actions

### 🔒 `check_permission`
Verifies file/folder permissions for a specific user.

**Parameters:**
- `path`: File/folder path
- `permissions`: Permission string containing any of "r" (read), "w" (write), "x" (execute)
- `user`: Username to check permissions for

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_permission",
    "path": "server_home/bin/",
    "permissions": "rwx",
    "user": "admin"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_permission",
    "path": "server_home/conf/secure.xml",
    "permissions": "r",
    "user": "guest"
  }
}
```

### ➕ `add_permission`

Adds specified permissions (R, W, X, RWX) for a user on a folder. On Windows, uses `icacls` to grant permissions. On Unix, uses `setfacl` or fallback to `chmod`/`chown`.

**Parameters:**
- `path`: Path to file or folder
- `permissions`: Permission string (R, W, X, RWX)
- `user`: Comma-separated list of usernames to grant access. To grant permission to a machine account, append `$` to the machine name (eg., `user1,user2,machine1$`).

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "add_permission",
    "path": "server_home/bin/run.sh",
    "permissions": "rwx",
    "user": "admin"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "add_permission",
    "path": "server_home/conf/",
    "permissions": "r",
    "user": "app_user"
  }
}
```

### 🔐 `set_permission`
Sets file/folder security permissions for a specific user. (removes existing permissions )

**Parameters:**
- `path`: File/folder path
- `permissions`: Permission string containing any of "r" (read), "w" (write), "x" (execute)
- `user`: Username to set permissions for specific user and remove existing permissions for all users.
- `enable_inheritance` (optional): Set to "true" to enable permission inheritance (Default false)

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "set_permission",
    "path": "server_home/bin",
    "permissions": "rwx",
    "user": "admin"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "set_permission",
    "path": "server_home/conf/",
    "permissions": "r",
    "user": "app_user",
    "enable_inheritance": "true"
  }
}
```

### 🔓 `remove_permission`
Removes specific permissions for a user.

**Parameters:**
- `path`: File/folder path
- `permissions`: Permission string containing any of "r" (read), "w" (write), "x" (execute)
- `user`: Username to remove permissions for
- `recursive` (optional): Set to "true" to apply recursively (default: false)
- `disable_inheritance` (optional): Set to "true" to disable permission inheritance (Windows only)

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "remove_permission",
    "path": "server_home/conf/sensitive.xml",
    "permissions": "w",
    "user": "guest"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "remove_permission",
    "path": "server_home/data/",
    "permissions": "rwx",
    "user": "temporary_user",
    "recursive": "true",
    "disable_inheritance": "true"
  }
}
```

### Share Permission Actions

### 🖧 `set_share_permission`
Sets Windows file share permissions for a folder and grants access to specified users.

**Parameters:**
- `path`: Folder path to share
- `share_name`: Name of the Windows share
- `permissions`: `"read"`, `"change"`, or `"full"` (share access level)
- `user`: Comma-separated list of usernames to grant access (If the user is domain then need to give the domain name too. Domain\\User). To grant permission to a machine account, append `$` to the machine name (e.g., `user1,user2,machine1$`).
- `remove_existing` (optional): `"true"` to remove existing share before creating (default: `"false"`)
-  The share path is stored in the value of `share_name` you used in the json and you use the path like ${`share_name`}.

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "set_share_permission",
    "path": "C:/shared/data",
    "share_name": "DataShare",
    "permissions": "full",
    "user": "admin,guest,machine1$",
    "remove_existing": "true"
  }
}
```

### 🖧 `remove_share_permission`
Removes share permissions for specified users or deletes the share.

**Parameters:**
- `share_name`: Name of the Windows share
- `user` (optional): Comma-separated list of usernames to remove access for. If omitted, removes the share entirely.

**Use Cases:**
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "remove_share_permission",
    "share_name": "DataShare",
    "user": "guest"
  }
}
```
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "remove_share_permission",
    "share_name": "DataShare"
  }
}
```

## Common Parameters

These parameters apply to multiple actions:

| Parameter | Description |
|-----------|-------------|
| `action` | Specifies the file/folder action to perform (required) |
| `file_path` | Directory path (used in most actions) |
| `filename` | Name of file/folder (used in presence/absence checks) |
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

## Examples

### Create a New Configuration File with Size
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "create",
    "file_path": "server_home/conf/custom/new_config.xml",
    "type": "file",
    "size": "5"
  }
}
```

### Create a New Directory Structure
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "create",
    "file_path": "server_home/data/reports/monthly/2023",
    "type": "folder"
  }
}
```

### Copy a Configuration File
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "copy",
    "source": "server_home/conf/original.xml",
    "target": "server_home/conf/custom.xml",
    "overwrite": "true"
  }
}
```

### Move Logs to Archive Folder
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "move",
    "source": "server_home/logs/old_logs/*.log",
    "target": "server_home/archive/",
    "overwrite": "true"
  }
}
```

### Delete Temporary Directory Recursively
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "delete",
    "path": "server_home/temp",
    "recursive": "true"
  }
}
```

### Check File Size and Save to Variable
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "get_size",
    "file_path": "server_home/logs/server.log",
    "note": "LOG_SIZE_MB"
  }
}
```

### Check Last Modified is After a Specific Time
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_last_modified",
    "file_path": "server_home/logs/server.log",
    "expected_time": "01-01-2023 12:00",
    "comparison": "after"
  }
}
```

### Set Executable Permission
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "set_permission",
    "path": "server_home/bin/run.sh",
    "permissions": "x",
    "user": "admin"
  }
}
```

### Check File Existence
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_presence",
    "file_path": "server_home/conf/",
    "filename": "server.xml"
  }
}
```

### Remove Write Permission
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "remove_permission",
    "path": "server_home/conf/server.xml",
    "permissions": "w",
    "user": "guest",
    "recursive": "false"
  }
}
```

### Add Read Permission Recursively
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "add_permission",
    "path": "server_home/data/",
    "permissions": "r",
    "user": "app_user",
    "recursive": "true"
  }
}
```

### Set Windows Share Permission
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "set_share_permission",
    "path": "C:/shared/data",
    "share_name": "DataShare",
    "permissions": "full",
    "user": "admin,guest",
    "remove_existing": "true"
  }
}
```

### Remove Windows Share Permission for User
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "remove_share_permission",
    "share_name": "DataShare",
    "user": "guest"
  }
}
```
### Copy the file from one machine to another machine by share path
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "copy",
    "source": "${DataShare}/data/file.txt",
    "target": "C:/local/data/file.txt"
  }
}
```

### Remove Windows Share Entirely
```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "remove_share_permission",
    "share_name": "DataShare"
  }
}
```

[//]: # (## Platform-Specific Behavior)

[//]: # ()
[//]: # (| Platform | Behavior |)

[//]: # (|----------|----------|)

[//]: # (| Windows | Permission operations use `icacls` with Windows-specific flags |)

[//]: # (| Unix/Linux | Permission operations use POSIX permissions via Java API or fallback to `chmod`/`setfacl` |)