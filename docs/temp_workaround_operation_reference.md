# Temp Workaround Operations Reference 📦

> ⚠️ **Temporary Workaround Notice**
> 
> This document describes temporary workaround operations handling in the GOAT automation framework. These operations are interim solutions and will be removed in future versions once the actual use cases are achieved through proper implementation.
>

---

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Operations](#operations)
  - [Get Old Backup By Time](#-get-old-backup-by-time)
  - [Get Backup File Name](#-get-backup-file-name)
- [Folder/File Naming Pattern](#folderfile-naming-pattern)
- [Time Window Logic](#time-window-logic)
- [Saved Information](#saved-information)
- [Examples](#examples)

## Overview

> **⚠️ Temporary Implementation**: These operations serve as temporary workarounds and will be deprecated once proper backup management functionality is implemented.

**Supported Operations:**
- Finding existing 7z backup files by modification time
- Finding newly created backup folders and their corresponding 7z archives

## Quick Reference

| Icon | Action | Description | Status |
|------|--------|-------------|--------|
| 🔍 | `get_old_backup_by_time` | Search for existing 7z files by modification time | ⚠️ Temporary |
| 📁 | `get_backup_file_name` | Wait for new backup folder and 7z file creation | ⚠️ Temporary |

## Operations

All this operations use the operation type `temp_workaround_operation` with the appropriate `action` parameter.

> **Note**: The `temp_workaround_operation` operation type indicates these are temporary solutions.

### 🔍 Get Old Backup By Time

> ⚠️ **Temporary Workaround**: This operation will be removed once proper backup file management is implemented.

Searches for existing 7z backup files matching the naming pattern with optional time filtering.

**Parameters:**
- `action`: Must be `get_old_backup_by_time` (required)
- `folder_to_search`: Path to folder containing 7z files (required)
- `time_to_check_before`: Time window in minutes to filter files by last modified time (optional, default: 0)
- `note`: If provided, saves file path and name to database (optional)

**Behavior:**
- Searches for `.7z` files in the specified folder
- Filters files by naming pattern
- If `time_to_check_before` is specified, only includes files modified within that time window
- Saves file path and name to database if `note` parameter is provided (appends `_path` and `_name` suffixes)

**Return Value:**
- `true`: Matching 7z file found
- `false`: No matching file found or error occurred

**Examples:**

```json
{
  "operation_type": "temp_workaround_operation",
  "parameters": {
    "action": "get_old_backup_by_time",
    "folder_to_search": "tool_home/backups",
    "time_to_check_before": "60",
    "note": "backup_info"
  }
}
```

### 📁 Get Backup File Name

> ⚠️ **Temporary Workaround**: This operation will be removed once proper backup file management is implemented.

Searches for a newly created backup folder matching the naming pattern (within ±4 minutes) and waits for its corresponding 7z archive file to be created.

**Parameters:**
- `action`: Any value other than `get_old_backup_by_time` or omitted
- `folder_to_search`: Path to folder containing backup folders (required)
- `folder_timeout`: Maximum seconds to wait for folder creation (optional, default: 300)
- `zip_timeout`: Maximum seconds to wait for 7z file creation (optional, default: 300)
- `check_interval`: Interval in seconds between checks (optional, default: 10)
- `note`: If provided, saves file path and name to database (optional)

**Behavior:**
1. Polls for a folder matching the naming pattern created within ±4 minutes of current time
2. Once folder is found, constructs expected 7z filename (`<foldername>.7z`)
3. Waits for the 7z file to be created
4. Saves file path and name to database if `note` parameter is provided (appends `_path` and `_name` suffixes)

**Return Value:**
- `true`: Both folder and 7z file found successfully
- `false`: Timeout expired or error occurred

**Examples:**

```json
{
  "operation_type": "temp_workaround_operation",
  "parameters": {
    "action": "get_backup_file_name",
    "folder_to_search": "D:/Backups",
    "folder_timeout": "600",
    "zip_timeout": "600",
    "check_interval": "15",
    "note": "daily_backup"
  }
}
```

## Folder/File Naming Pattern

The handler recognizes backup folders and files matching the pattern:
```
\d+-[A-Za-z]+-\d{2}-\d{4}-\d{2}-\d{2}
```

**Example:** `114254001-Nov-14-2025-13-17`

**Pattern Breakdown:**
- `\d+`: One or more digits (e.g., 114254001)
- `-[A-Za-z]+`: Hyphen followed by alphabetic characters (e.g., Nov)
- `-\d{2}`: Hyphen and 2 digits (day)
- `-\d{4}`: Hyphen and 4 digits (year)
- `-\d{2}`: Hyphen and 2 digits (hour)
- `-\d{2}`: Hyphen and 2 digits (minute)

## Time Window Logic

### For `get_old_backup_by_time`:
- Uses last modified time of the file
- Checks if file was modified within the specified minutes from current time
- Formula: `(now - lastModified) / (60 * 1000) <= minutes`

### For `get_backup_file_name`:
- Uses creation time of the folder
- Checks if folder was created within ±4 minutes of current time
- Formula: `abs(Duration.between(now, created).toMinutes()) <= 4`

## Saved Information

When `note` parameter is provided, the following information is saved to the database:

| Key | Description |
|-----|-------------|
| `<note>_path` | Parent directory path of the 7z file |
| `<note>_name` | Name of the 7z file (including `.7z` extension) |

## Examples

> **Reminder**: All examples use `temp_workaround_operation` as the operation type, indicating temporary implementation.

### Search for Recent Backup File
```json
{
  "operation_type": "temp_workaround_operation",
  "parameters": {
    "action": "get_old_backup_by_time",
    "folder_to_search": "server_home/backups/archives",
    "time_to_check_before": "30",
    "note": "recent_backup"
  }
}
```

### Wait for New Backup Creation
```json
{
  "operation_type": "temp_workaround_operation",
  "parameters": {
    "action": "get_backup_file_name",
    "folder_to_search": "~/server_backups",
    "folder_timeout": "1200",
    "zip_timeout": "1800",
    "check_interval": "20",
    "note": "automated_backup"
  }
}
```

### Find Backup Without Saving to Database
```json
{
  "operation_type": "temp_workaround_operation",
  "parameters": {
    "action": "get_old_backup_by_time",
    "folder_to_search": "D:/BackupArchives",
    "time_to_check_before": "180"
  }
}
```

---

## Migration Notice

**For Developers**: When the proper backup management implementation is complete:
1. Update test cases to use the new implementation
2. Remove references to `temp_workaround_operation`
3. Update any automation scripts relying on these operations