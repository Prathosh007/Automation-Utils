# Archive Operations Reference 📦

> **Powerful Archive Management for Test Automation**

This document describes the archive operations available in the GOAT automation framework, allowing you to work with 7z archives efficiently.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Operations](#operations)
    - [Extract](#extract)
    - [Create](#create)
    - [List](#list)
    - [Add](#add)
    - [Delete](#delete)
    - [Update](#update)
- [Common Parameters](#common-parameters)
- [Path Variables](#path-variables)
- [Examples](#examples)

## Overview

The archive operations provide a powerful interface to work with 7z archives. You can create, extract, list, update, and modify archives with ease, including support for password-protected archives.

**Supported Archive Format:**
- 7z
- zip

## Quick Reference

| Icon | Action             | Description                           |
|------|--------------------|---------------------------------------|
| 📤 | `extract`          | Extract files from archive            |
| 📥 | `create`           | Create new archive                    |
| 📋 | `list`             | List archive contents                 |
| ➕ | `add`              | Add files to existing archive         |
| ❌ | `delete`           | Delete files from archive             |
| 🔍 | `zip_contains`     | Checks if a file or folder exists     |
| 🔍 | `zip_not_contains` | Checks if a file or folder not exists |
| 🔄 | `update`           | Update files in archive               |

## Operations

All archive operations use the operation type `zip_operation` with the appropriate `action` parameter.

### 📤 Extract

Extracts files from an archive to a target directory.

**Parameters:**
- `archive_path`: Path to the 7z archive file
- `target_dir`: Directory to extract files to (optional, defaults to archive's parent directory)
- `files_to_extract`: Comma-separated list of files or folders to extract (optional)
- `file_to_extract`: Single file to extract (optional alternative to `files_to_extract`)
- `password`: Password for encrypted archives (optional)

**Examples:**

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "extract",
    "archive_path": "server_home/archives/data.7z",
    "target_dir": "server_home/extracted/"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "extract",
    "archive_path": "server_home/archives/data.7z",
    "files_to_extract": "config/settings.xml,logs/error.log",
    "password": "secure123"
  }
}
```

### 📥 Create

Creates a new 7z archive from files or directories.

**Parameters:**
- `archive_path`: Path where the new archive will be created
- `source_path`: Path to the source files or directory
- `source_files`: Comma-separated list of specific files to include (optional)
- `exclude_files`: Comma-separated list of files or folders to exclude (optional)

**Examples:**

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "create",
    "archive_path": "server_home/backups/config_backup.7z",
    "source_path": "server_home/conf/",
    "exclude_files": "old_config.xml,temp/"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "create",
    "archive_path": "server_home/backups/logs.7z",
    "source_path": "server_home/logs/",
    "source_files": "error.log,server.log,access.log"
  }
}
```

### 📋 List

Lists the contents of an archive file.

**Parameters:**
- `archive_path`: Path to the 7z archive file

**Examples:**

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "list",
    "archive_path": "server_home/archives/data.7z"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "list",
    "archive_path": "server_home/backups/system_backup.7z"
  }
}
```

### ➕ Add

Adds files to an existing archive.

**Parameters:**
- `archive_path`: Path to the existing 7z archive file
- `source_path`: Path to the source files or directory
- `source_files`: Comma-separated list of specific files to add (optional)

**Examples:**

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "add",
    "archive_path": "server_home/archives/data.7z",
    "source_path": "server_home/new_data/"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "add",
    "archive_path": "server_home/backups/config.7z",
    "source_path": "server_home/conf/",
    "source_files": "server.xml,web.xml"
  }
}
```

### ❌ Delete

Deletes files or folders from an archive.

**Parameters:**
- `archive_path`: Path to the 7z archive file
- `file_to_delete`: Comma-separated list of files or folders to delete from the archive
- `password`: Password for encrypted archives (optional)

**Examples:**

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "delete",
    "archive_path": "server_home/archives/data.7z",
    "file_to_delete": "temp/cache.dat,logs/debug.log",
    "password": "secure123"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "delete",
    "archive_path": "server_home/backups/full_system.7z",
    "file_to_delete": "temp/,logs/debug/,cache/"
  }
}
```

### 🔍 Zip Contains
Checks if a file or folder exists inside a 7z archive.

**Parameters:**
- `archive_path`: Path to the 7z archive file
- `file_path`: Path inside the archive to check (optional)
- `filename`: Name of the file to check (optional)
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "zip_contains",
    "archive_path": "server_home/archives/data.7z",
    "file_path": "config/",
    "filename": "settings.xml"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "zip_contains",
    "archive_path": "server_home/backups/system_backup.7z",
    "filename": "readme.txt"
  }
}
```

### 🔍 Zip Not Contains
Checks if a file or folder does not exist inside a 7z archive.

**Parameters:**
- `archive_path`: Path to the 7z archive file
- `file_path`: Path inside the archive to check (optional)
- `filename`: Name of the file to check (optional)

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "zip_not_contains",
    "archive_path": "server_home/archives/data.7z",
    "file_path": "config/",
    "filename": "settings.xml"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "zip_not_contains",
    "archive_path": "server_home/backups/system_backup.7z",
    "filename": "readme.txt"
  }
}
```


### 🔄 Update

Updates files or folders in an archive with newer versions.

**Parameters:**
- `archive_path`: Path to the 7z archive file
- `file_to_update`: Name of file or folder in the archive to update
- `source_path`: Path to the new file or folder

**Examples:**

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "update",
    "archive_path": "server_home/archives/data.7z",
    "file_to_update": "settings.xml",
    "source_path": "server_home/conf/settings.xml"
  }
}
```

```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "update",
    "archive_path": "server_home/backups/config_backup.7z",
    "file_to_update": "conf",
    "source_path": "server_home/conf"
  }
}
```

## Common Parameters

These parameters are used across multiple operations:

| Parameter | Description |
|-----------|-------------|
| `action` | Specifies the archive operation to perform (required) |
| `archive_path` | Path to the 7z archive file (required) |
| `source_path` | Path to source files/folders (required for create/add/update) |

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

### Extract Specific Files with Password
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "extract",
    "archive_path": "server_home/backups/secure_backup.7z",
    "files_to_extract": "config/database.properties,config/security.xml",
    "password": "secure_password"
  }
}
```

### Create Archive with Selected Files
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "create",
    "archive_path": "server_home/backups/logs_archive.7z",
    "source_path": "server_home/logs/",
    "source_files": "server.log,access.log,error.log"
  }
}
```

### List Archive Contents
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "list",
    "archive_path": "server_home/backups/system_backup.7z"
  }
}
```

### Add New Files to Archive
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "add",
    "archive_path": "server_home/backups/config.7z",
    "source_path": "server_home/conf/new_features/"
  }
}
```

### Delete Multiple Items
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "delete",
    "archive_path": "server_home/backups/full_system.7z",
    "file_to_delete": "temp/,logs/debug/,cache/"
  }
}
```

### Update Configuration Folder
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "update",
    "archive_path": "server_home/backups/system.7z",
    "file_to_update": "conf",
    "source_path": "server_home/conf"
  }
}
```
### Check if File Exists in Archive
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "zip_contains",
    "archive_path": "server_home/archives/data.7z",
    "file_path": "config/",
    "filename": "settings.xml"
  }
}
```
### Check if File Does Not Exist in Archive
```json
{
  "operation_type": "zip_operation",
  "parameters": {
    "action": "zip_not_contains",
    "archive_path": "server_home/backups/system_backup.7z",
    "filename": "readme.txt"
  }
}
```