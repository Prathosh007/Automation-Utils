# Registry Automation Framework - Complete Guide
## Overview

This guide helps you automate Windows Registry tasks using simple JSON commands. You can create, read, update, and delete registry keys and values — no manual registry editing needed.

---

## Table of Contents

- [1. Supported Actions](#1-supported-actions)
- [2. JSON Command Structure](#2-json-command-structure)
- [3. Registry Roots](#3-registry-roots)
- [4. Value Types](#4-value-types)
- [5. WOW Mode](#5-wow-mode)
- [6. Quick Examples](#6-quick-examples)
- [7. Doing Multiple Registry Actions in a Single JSON](#7-doing-multiple-registry-actions-in-a-single-json)
- [8. Command-Line Usage](#8-command-line-usage)
- [9. Output Format](#9-output-format)
- [10. Common Properties](#10-common-properties)
- [11. Best Practices](#11-best-practices)
- [12. Error Handling](#12-error-handling)

---

## 1. Supported Actions
> Here are all the things you can do with registry keys and values — like creating, reading, writing, deleting, and checking if they exist.

| # | Action | What It Does | Example |
|---|--------|-------------|---------|
| 1.1 | `add_key` | Creates a new folder (key) in the registry | Create `Software\MyApp` |
| 1.2 | `write_key` | Saves a value inside a registry key | Set `Version = 100` |
| 1.3 | `read_key` | Reads any value (auto-detects the type) | Read `AppName` |
| 1.4 | `read_string` | Reads a text (string) value only | Read `AppName` as text |
| 1.5 | `read_dword` | Reads a small number (32-bit integer) | Read `Version` as number |
| 1.6 | `read_qword` | Reads a large number (64-bit integer) | Read `LargeValue` as number |
| 1.7 | `delete_value` | Removes a single value from a key | Remove `Version` |
| 1.8 | `delete_key` | Removes an entire key and everything inside it | Remove `Software\MyApp` |
| 1.9 | `check_key_exists` | Checks if a key (folder) exists | Does `Software\MyApp` exist? |
| 1.10 | `check_value_exists` | Checks if a specific value exists inside a key | Does `AppName` exist? |
| 1.11 | `check_key_not_exist` | Checks if a key (folder) does NOT exist | Verify `Software\OldApp` was removed |
| 1.12 | `check_value_not_exist` | Checks if a specific value does NOT exist inside a key | Verify `OldSetting` was deleted |
| 1.13 | `list_subkeys` | Shows all sub-folders (child keys) inside a key | List folders under `Software` |
| 1.14 | `list_values` | Shows all values stored inside a key | List everything in `TestApp` |
| 1.15 | `get_value_type` | Tells you what type a value is (text, number, etc.) | Is `AppName` a string or number? |

---

## 2. JSON Command Structure
> Every registry command follows this simple JSON format. Just fill in the fields and run it.

Base structure for all registry operations:

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "action_name",
    "root": "HKEY_CURRENT_USER",
    "path": "Software\\MyApp",
    "key_name": "ValueName",
    "value": "ValueData",
    "value_type": "REG_SZ",
    "expected_value": "ExpectedData"
  }
}
```

**Key Fields Explained:**
- **action**: What you want to do — pick one from the [Supported Actions](#1-supported-actions) list (e.g., `add_key`, `write_key`, `read_key`)
- **root**: Where to start looking in the registry — think of it like a drive letter (e.g., `HKCU` for current user settings, `HKLM` for system-wide settings)
- **path**: The folder path inside the registry — like a file path on your computer (e.g., `Software\TestApp`)
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`). Not needed when you're working with the folder itself (like `add_key` or `delete_key`)
- **value**: The data you want to save — only needed when writing (e.g., `"MyApp"`, `"100"`)
- **value_type**: What kind of data it is — `REG_SZ` for text, `REG_DWORD` for numbers, etc. Only needed when writing
- **expected_value**: *(Optional)* The value you expect the operation to return. If provided, the tool compares the operation's output against this value and marks the step as FAILED if they don't match. Works with **any** action that produces output data (e.g., `read_key`, `read_dword`, `check_key_exists`, `list_values`, `get_value_type`, etc.)
- **wow_mode**: Choose `x86` for 32-bit, `x64` for 64-bit, or `BuildType` to auto-detect — controls which registry view to use on 64-bit Windows

---

## 3. Registry Roots
> The registry is organized into top-level sections called "roots" or "hives." Choose one based on where your settings live.

| # | Root | Short Form | When to Use |
|---|------|------------|-------------|
| 3.1 | `HKEY_LOCAL_MACHINE` | HKLM | Settings that apply to all users on the machine (system-wide) |
| 3.2 | `HKEY_CURRENT_USER` | HKCU | Settings for the currently logged-in user only |
| 3.3 | `HKEY_CLASSES_ROOT` | HKCR | File type associations (e.g., which app opens .txt files) |
| 3.4 | `HKEY_USERS` | HKU | Settings for all user profiles on the machine |
| 3.5 | `HKEY_CURRENT_CONFIG` | HKCC | Current hardware profile settings (rarely used) |

> **Tip:** Use the short form (`HKCU`, `HKLM`) in your JSON — it’s cleaner and easier to read.

---

## 4. Value Types
> When you save data in the registry, you need to tell it what kind of data it is. Here are the types you can use.

| # | Type | What It Stores | Example |
|---|------|---------------|---------|
| 4.1 | `REG_SZ` | Plain text (a simple string) | `"Hello World"` |
| 4.2 | `REG_DWORD` | A small number (up to ~4 billion) | `100` |
| 4.3 | `REG_QWORD` | A very large number | `5368709120` |
| 4.4 | `REG_BINARY` | Raw binary data (bytes) | `"01 FF A3"` |
| 4.5 | `REG_MULTI_SZ` | Multiple lines of text | `"Line1\nLine2"` |
| 4.6 | `REG_EXPAND_SZ` | Text with environment variables that get replaced | `"%TEMP%\file.txt"` |

> **Tip:** Most of the time you’ll use `REG_SZ` for text or `REG_DWORD` for numbers.

---

## 5. WOW Mode
> On 64-bit Windows, the registry has two separate views — one for 32-bit apps and one for 64-bit apps. This setting lets you pick which one to use.

**Why does this matter?**

A 32-bit application saves its settings in a different place than a 64-bit application, even if the path looks the same. For example:

- A **32-bit app** writes to: `HKLM\SOFTWARE\WOW6432Node\dcagent`
- A **64-bit app** looks at: `HKLM\SOFTWARE\dcagent`

If your tool and your app don’t match (one is 32-bit, the other is 64-bit), you’ll be looking in the wrong place!

**The fix:** Set `wow_mode` to tell the tool exactly which view to use:
- Set `wow_mode = x86` to always look in the 32-bit registry
- Set `wow_mode = x64` to always look in the 64-bit registry
- Set `wow_mode = BuildType` to auto-detect based on your tool’s architecture

| # | Mode | What It Does | When to Use |
|---|------|-------------|-------------|
| 5.1 | `BuildType` | Automatically matches your tool’s architecture (default) | Use this if you’re not sure — it’s the safe default |
| 5.2 | `x86` | Always reads/writes the 32-bit registry | When your target app is 32-bit |
| 5.3 | `x64` | Always reads/writes the 64-bit registry | When your target app is 64-bit |
| 5.4 | `Both` | Checks both 32-bit and 64-bit registries | When you need to search in both places |

---

## 6. Quick Examples
> Copy-paste these JSON samples to quickly perform common registry tasks. Each example shows the JSON and explains what each field does.

### 6.1 add_key

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "add_key",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

**Parameters Used:**
- **action**: `add_key` — Creates a new folder (key) in the registry
- **root**: Which part of the registry to use (`HKCU` = current user)
- **path**: The folder path to create (e.g., `Software\TestApp`)

### 6.2 write_key (String)

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "write_key",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName",
    "value": "MyApp",
    "value_type": "REG_SZ"
  }
}
```

**Parameters Used:**
- **action**: `write_key` — Saves a value inside a registry key
- **root**: Which part of the registry to use (`HKCU` = current user)
- **path**: The folder where the value will be stored
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`).
- **value_type**: What kind of data it is — `REG_SZ` for text, `REG_DWORD` for numbers, etc. Only needed when writing

### 6.3 write_key (DWORD)

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "write_key",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "Version",
    "value": "100",
    "value_type": "REG_DWORD"
  }
}
```

**Parameters Used:**
- **action**: `write_key` — Saves a value inside a registry key
- **root**: Which part of the registry to use (`HKCU` = current user)
- **path**: The folder where the value will be stored
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`)
- **value**: The data you want to save — only needed when writing (e.g., `"MyApp"`, `"100"`)
- **value_type**: What kind of data it is — `REG_SZ` for text, `REG_DWORD` for numbers, etc. Only needed when writing

### 6.4 read_key

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "read_key",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName"
  }
}
```

**Parameters Used:**
- **action**: `read_key` — Reads a value (works with any data type)
- **root**: Which part of the registry to look in
- **path**: The folder where the value lives
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`)

### 6.4.1 read_key with expected_value

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "read_key",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName",
    "expected_value": "MyTestApplication"
  }
}
```

**Parameters Used:**
- **action**: `read_key` — Reads a value and validates it against the expected value
- **root**: Which part of the registry to look in
- **path**: The folder where the value lives
- **key_name**: The name of the value to read
- **expected_value**: *(Optional)* The value you expect — if the actual value doesn't match, the step FAILS with a mismatch message

### 6.5 read_string

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "read_string",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName"
  }
}
```

**Parameters Used:**
- **action**: `read_string` — Reads a text value (only works with `REG_SZ` type)
- **root**: Which part of the registry to look in
- **path**: The folder where the value lives
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`)

### 6.6 read_dword

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "read_dword",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "Version"
  }
}
```

**Parameters Used:**
- **action**: `read_dword` — Reads a number value (only works with `REG_DWORD` type)
- **root**: Which part of the registry to look in
- **path**: The folder where the value lives
- **key_name**: The name of the number value you want to read (e.g., `Version`)

### 6.7 read_qword

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "read_qword",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "LargeValue"
  }
}
```

**Parameters Used:**
- **action**: `read_qword` — Reads a large number value (only works with `REG_QWORD` type)
- **root**: Which part of the registry to look in
- **path**: The folder where the value lives
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`)

### 6.8 delete_value

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "delete_value",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "Version"
  }
}
```

**Parameters Used:**
- **action**: `delete_value` — Removes a single value from a registry key (the key itself stays)
- **root**: Which part of the registry to look in
- **path**: The folder that contains the value
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`)

### 6.9 delete_key

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "delete_key",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

**Parameters Used:**
- **action**: `delete_key` — Deletes the entire folder (key) and everything inside it
- **root**: Which part of the registry to look in
- **path**: The folder you want to completely remove

### 6.10 check_key_exists

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "check_key_exists",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

**Parameters Used:**
- **action**: `check_key_exists` — Checks if a folder (key) exists in the registry (returns true/false)
- **root**: Which part of the registry to check
- **path**: The folder path to look for

### 6.11 check_value_exists

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "check_value_exists",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName"
  }
}
```

**Parameters Used:**
- **action**: `check_value_exists` — Checks if a specific value exists inside a key (returns true/false)
- **root**: Which part of the registry to check
- **path**: The folder to look inside
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`)

### 6.12 check_key_not_exist

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "check_key_not_exist",
    "root": "HKCU",
    "path": "Software\\OldApp"
  }
}
```

**Parameters Used:**
- **action**: `check_key_not_exist` — Verifies that a folder (key) does NOT exist in the registry (PASSED if absent, FAILED if present)
- **root**: Which part of the registry to check
- **path**: The folder path that should not exist

### 6.13 check_value_not_exist

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "check_value_not_exist",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "OldSetting"
  }
}
```

**Parameters Used:**
- **action**: `check_value_not_exist` — Verifies that a specific value does NOT exist inside a key (PASSED if absent, FAILED if present)
- **root**: Which part of the registry to check
- **path**: The folder to look inside
- **key_name**: The name of the value that should not exist

### 6.14 list_subkeys

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "list_subkeys",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

**Parameters Used:**
- **action**: `list_subkeys` — Shows all sub-folders (child keys) inside a key
- **root**: Which part of the registry to look in
- **path**: The folder whose sub-folders you want to list

### 6.15 list_values

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "list_values",
    "root": "HKCU",
    "path": "Software\\TestApp"
  }
}
```

**Parameters Used:**
- **action**: `list_values` — Shows all values stored inside a key
- **root**: Which part of the registry to look in
- **path**: The folder whose values you want to list

### 6.16 get_value_type

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "get_value_type",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName"
  }
}
```

**Parameters Used:**
- **action**: `get_value_type` — Tells you what type of data a value holds (text, number, binary, etc.)
- **root**: Which part of the registry to look in
- **path**: The folder that contains the value
- **key_name**: The name of the specific value inside that folder you want to work with (e.g., `AppName`, `Version`)

### 6.17 Using expected_value with different actions

> The `expected_value` parameter is **optional** and works with **any** action. When provided, the tool compares the operation's output data against this value after a successful operation. If they don't match, the step is marked as FAILED.

**What gets compared for each action:**

| Action | Output Data (compared against `expected_value`) |
|--------|--------------------------------------------------|
| `read_key` | The value read from the registry |
| `read_string` | The string value read |
| `read_dword` | The DWORD value as a string (e.g., `"100"`) |
| `read_qword` | The QWORD value as a string (e.g., `"5368709120"`) |
| `write_key` | The value that was written |
| `check_key_exists` | `"true"` or `"false"` |
| `check_value_exists` | `"true"` or `"false"` |
| `check_key_not_exist` | `"true"` or `"false"` |
| `check_value_not_exist` | `"true"` or `"false"` |
| `list_subkeys` | Comma-separated list of subkey names |
| `list_values` | Comma-separated list of value names |
| `get_value_type` | The type name (e.g., `"REG_SZ"`, `"REG_DWORD"`) |

**Examples:**

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "read_dword",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "Version",
    "expected_value": "100",
    "description": "Verify Version is 100"
  }
}
```

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "get_value_type",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "key_name": "AppName",
    "expected_value": "REG_SZ",
    "description": "Verify AppName is a string type"
  }
}
```

```json
{
  "operation_type": "registry_operation",
  "parameters": {
    "action": "check_key_exists",
    "root": "HKCU",
    "path": "Software\\TestApp",
    "expected_value": "true",
    "description": "Confirm TestApp key exists"
  }
}
```

---

## 7. Doing Multiple Registry Actions in a Single JSON
> Instead of running one command at a time, you can put multiple commands in one JSON file and they will run one after another automatically.

Just wrap your commands in a JSON array (square brackets `[]`). They run in order from top to bottom.

**Structure:**

```json
[
  { "operation_type": "registry_operation", "parameters": { ... } },
  { "operation_type": "registry_operation", "parameters": { ... } },
  { "operation_type": "registry_operation", "parameters": { ... } }
]
```


### 7.1 Example: Full Lifecycle (Create → Write → Read → Verify → Cleanup)

This 9-step example creates a key, writes values, reads them back, verifies existence, lists values, and cleans up — all in a single JSON file:

```json
[
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "add_key",
      "root": "HKCU",
      "path": "Software\\TestApp"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "write_key",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "AppName",
      "value": "MyTestApplication",
      "value_type": "REG_SZ"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "write_key",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "Version",
      "value": "100",
      "value_type": "REG_DWORD"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "read_key",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "AppName"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "read_dword",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "Version"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "check_value_exists",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "AppName"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "list_values",
      "root": "HKCU",
      "path": "Software\\TestApp"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "delete_value",
      "root": "HKCU",
      "path": "Software\\TestApp",
      "key_name": "Version"
    }
  },
  {
    "operation_type": "registry_operation",
    "parameters": {
      "action": "delete_key",
      "root": "HKCU",
      "path": "Software\\TestApp"
    }
  }
]
```

| Step | Action | Purpose |
|------|--------|---------|
| 1 | `add_key` | Create the `Software\TestApp` key |
| 2 | `write_key` | Write string value `AppName = MyTestApplication` |
| 3 | `write_key` | Write DWORD value `Version = 100` |
| 4 | `read_key` | Read back the `AppName` value |
| 5 | `read_dword` | Read back the `Version` DWORD value |
| 6 | `check_value_exists` | Verify `AppName` value exists |
| 7 | `list_values` | List all values under the key |
| 8 | `delete_value` | Delete the `Version` value |
| 9 | `delete_key` | Delete the entire `Software\TestApp` key |

---

## 8. Command-Line Usage
> How to run your JSON file from the command line.

| # | Command | What It Does |
|---|---------|-------------|
| 8.1 | `RegistryUtils.exe test.json` | Runs all the commands in your JSON file |
| 8.2 | `RegistryUtils.exe validate test.json` | Checks your JSON for errors without actually running it (safe to test) |

---

## 9. Output Format
> After running your commands, the tool shows you the results in this format so you can see what passed and what failed.

The tool returns results like this:

```
step-001|PASSED|Registry key created successfully
step-002|PASSED|Value written: AppName = MyTestApplication
step-003|FAILED|Access denied
SUMMARY|Total:3|Passed:2|Failed:1
```

**How to read it:**
- **Step ID** — Which step ran (step-001, step-002, etc.)
- **Status** — `PASSED` means it worked, `FAILED` means something went wrong
- **Message** — A short note explaining what happened or what went wrong
- **SUMMARY** — A quick count at the end: how many total, how many passed, how many failed

---

## 10. Common Properties
> A quick reference of all the fields you can use in your JSON commands and when each one is needed.

| # | Property | When to Use | Type | What It Does |
|---|----------|-------------|------|--------------|
| 10.1 | `action` | Always required | String | Tells the tool what to do (e.g., `read_key`, `write_key`, `delete_key`) |
| 10.2 | `root` | Always required | String | Which top-level registry section to use (`HKCU`, `HKLM`, etc.) |
| 10.3 | `path` | Always required | String | The folder path inside the registry (e.g., `Software\TestApp`) |
| 10.4 | `key_name` | Needed for value actions | String | The name of the value inside the folder (e.g., `AppName`, `Version`) |
| 10.5 | `value` | Needed for write actions | String/Number | The data you want to save (e.g., `"MyApp"`, `"100"`) |
| 10.6 | `value_type` | Needed for write actions | String | The kind of data: `REG_SZ` (text), `REG_DWORD` (number), etc. |
| 10.7 | `wow_mode` | Optional | String | Which registry view to use: `BuildType` (auto), `x86` (32-bit), `x64` (64-bit), `Both` |
| 10.8 | `expected_value` | Optional | String | The expected output value — if provided, the tool compares the operation's result against this and FAILS on mismatch. Works with any action (see [Section 6.17](#617-using-expected_value-with-different-actions)) |
| 10.9 | `description` | Optional | String | A note for yourself to describe what this step does |
| 10.10 | `continueOnFailure` | Optional | Boolean | Set to `true` to keep going even if this step fails (default: `false` = stop on error) |

**When is a field needed?**
- **Always required** = You must include it in every command
- **Needed for value actions** = Required when you’re working with a specific value (like `read_key`, `write_key`, `delete_value`) — not needed for key-level actions (like `add_key`, `delete_key`)
- **Needed for write actions** = Only required when saving data (`write_key`)
- **Optional** = You can leave it out if you don’t need it

---

## 11. Best Practices
> Follow these tips to keep your registry commands reliable and easy to maintain.

| # | Tip | How Important | Why |
|---|-----|--------------|-----|
| 11.1 | Give each step a unique ID | Critical | So you can easily find which step passed or failed |
| 11.2 | Use short registry root names | High | `HKCU` is cleaner than `HKEY_CURRENT_USER` |
| 11.3 | Always specify `value_type` when writing | High | Avoids confusion about whether data is text or a number |
| 11.4 | Run `validate` before executing | High | Catches typos and errors in your JSON before it touches the registry |
| 11.5 | Use `continueOnFailure` carefully | Medium | If one step fails, later steps might not make sense — only skip errors when you’re sure it’s safe |
| 11.6 | Add `description` to your steps | Medium | Helps others (and future you) understand what each step is for |
| 11.7 | Always clean up after testing | Important | Delete test keys and values when done so you don’t leave junk in the registry |

---

## 12. Error Handling
> If something goes wrong, here are the most common errors and how to fix them.

| # | Error | What Happened | How to Fix It |
|---|-------|-------------|---------------|
| 12.1 | Access Denied | You don’t have permission to access that part of the registry | Run the tool as **Administrator** (especially for `HKLM`) |
| 12.2 | Key Not Found | The folder path you specified doesn’t exist | Double-check the `path` — make sure the key was created first with `add_key` |
| 12.3 | Invalid Value Type | The data you’re trying to write doesn’t match the `value_type` | If your value is text, use `REG_SZ`. If it’s a number, use `REG_DWORD` |
| 12.4 | WOW64 Issues | You’re reading from the wrong registry view (32-bit vs 64-bit) | Set `wow_mode` to `x86` or `x64` to match where the data was originally written |

---

## Quick Navigation Guide



**For Testing:** Refer to [Section 7](#7-doing-multiple-registry-actions-in-a-single-json), [Section 8](#8-command-line-usage), [Section 9](#9-output-format), [Section 11](#11-best-practices)

**For Troubleshooting:** Jump to [Section 12](#12-error-handling)

---




