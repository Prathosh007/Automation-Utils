# JAR Operation Reference 📦

This document describes the JAR file operations available in the GOAT automation framework, allowing you to create, modify, examine, and verify JAR files as part of your test automation.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Actions](#actions)
    - [JAR Information](#jar-information)
    - [JAR Creation and Modification](#jar-creation-and-modification)
    - [JAR Extraction](#jar-extraction)
    - [JAR Verification](#jar-verification)
- [Common Parameters](#common-parameters)
- [Examples](#examples)

## Overview

The JAR operation provides a comprehensive interface to work with Java Archive (JAR) files, including analyzing manifests, extracting content, checking versions, and more.

## Quick Reference

| Icon | Action | Description |
|------|--------|-------------|
| 📄 | `create` | Create a new JAR file |
| 📋 | `add` | Add files to an existing JAR |
| 📦 | `extract` | Extract entire JAR contents to directory |
| 📝 | `update_manifest` | Update manifest attributes |
| 🔍 | `find_class` | Find a class file in JAR |
| 🔐 | `sign` | Check if a JAR file is signed |
| 📄 | `extract_file` | Extract a specific file from JAR |
| 📜 | `get_manifest` | Get manifest information |
| ✓ | `check_version` | Verify JAR version against expected |
| 🏷️ | `get_version` | Extract version information |

## Actions

All actions are performed using the `jar_operation` operation type with the appropriate `action` parameter.

### JAR Information

### 📜 `get_manifest`
Retrieves and analyzes the manifest from a JAR file.

**Parameters:**
- `jar_path`: Path to the JAR file

**Returns:**
- `has_manifest`: "true" if manifest exists
- `manifest_entries`: Number of entries in manifest
- `manifest_content`: Full manifest content
- `main_class`: Main class (if specified)
- `implementation_title`: Implementation title (if specified)
- `implementation_version`: Implementation version (if specified)
- `implementation_vendor`: Implementation vendor (if specified)
- `created_by`: Created by information (if specified)

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "get_manifest",
    "jar_path": "server_home/lib/server-core.jar"
  }
}
```

### 🏷️ `get_version`
Extracts version information from a JAR file's manifest.

**Parameters:**
- `jar_path`: Path to the JAR file
- `note` (optional): Variable name to store the version

**Returns:**
- `has_version`: "true" if version was found
- `version`: The version string found
- `jar_name`: Name of the JAR file
- `save_note`: The version value (stored in the variable specified in `note`)

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "get_version",
    "jar_path": "server_home/lib/product-core-4.1.2.jar",
    "note": "PRODUCT_VERSION"
  }
}
```

### ✓ `check_version`
Checks if a JAR file's version matches an expected version.

**Parameters:**
- `jar_path`: Path to the JAR file
- `expected_version`: Expected version to check against (optional)
- `version_attribute`: Manifest attribute to check (defaults to "Bundle-Version")
- `comparison_type`: Type of comparison ("exact", "minimum", "contains") (defaults to "exact")

**Returns:**
- `has_version`: "true" if version was found
- `actual_version`: The actual version found

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "check_version",
    "jar_path": "server_home/lib/api-client.jar",
    "expected_version": "2.0.26",
    "version_attribute": "Implementation-Version",
    "comparison_type": "minimum"
  }
}
```

### 🔍 `find_class`
Searches for a class in a JAR file.

**Parameters:**
- `jar_path`: Path to the JAR file
- `class_name`: Class name to search for

**Returns:**
- `found_count`: Number of matching classes found
- `found_classes`: List of found classes

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "find_class",
    "jar_path": "server_home/lib/utils.jar",
    "class_name": "LogManager.class"
  }
}
```
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "find_class",
    "jar_path": "server_home/lib/utils.jar",
    "class_name": "com/me/LogManager.class"
  }
}
```

### JAR Creation and Modification

### 📄 `create`
Creates a new JAR file.

**Parameters:**
- `jar_path`: Path where the JAR will be created
- `source_dir`: Directory containing files to include in the JAR
- `manifest_path` (optional): Path to manifest file

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "create",
    "jar_path": "server_home/lib/custom.jar",
    "source_dir": "server_home/temp/classes",
    "manifest_path": "server_home/temp/MANIFEST.MF"
  }
}
```

### 📋 `add`
Adds files to an existing JAR file.

**Parameters:**
- `jar_path`: Path to the JAR file
- `files`: Comma-separated list of files to add
- `base_dir`: Base directory for resolving relative paths
- `overwrite` (optional): Set to "true" to overwrite existing files (default: false)

**Returns:**
- `added_count`: Number of files added
- `skipped_count`: Number of files skipped
- `error_count`: Number of files with errors

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "add",
    "jar_path": "server_home/lib/extensions.jar",
    "files": "server_home/temp/NewClass.class,server_home/temp/resource.properties",
    "base_dir": "server_home/temp",
    "overwrite": "true"
  }
}
```

### 📝 `update_manifest`
Updates the manifest in a JAR file.

**Parameters:**
- `jar_path`: Path to the JAR file
- `attributes`: Comma-separated key:value pairs to add to the manifest
- `manifest_path` (optional): Path to a manifest file to use

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "update_manifest",
    "jar_path": "server_home/lib/app.jar",
    "attributes": "Bundle-Version:1.2.3,Implementation-Title:Custom App"
  }
}
```

### 🔐 `sign`
Check if a JAR file is signed

**Parameters:**
- `jar_path`: Path to the JAR file

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "sign",
    "jar_path": "server_home/lib/security.jar"
  }
}
```

### JAR Extraction

### 📦 `extract`
Extracts the entire contents of a JAR file.

**Parameters:**
- `jar_path`: Path to the JAR file
- `output_dir`: Directory where contents will be extracted (optional, defaults to same name as JAR)

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "extract",
    "jar_path": "server_home/lib/framework.jar",
    "output_dir": "server_home/temp/framework"
  }
}
```

### 📄 `extract_file`
Extracts a specific file from a JAR.

**Parameters:**
- `jar_path`: Path to the JAR file
- `package_path`: Path to the file inside the JAR
- `output_path`: Path where the file will be extracted

**Use Case:**
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "extract_file",
    "jar_path": "server_home/lib/config.jar",
    "package_path": "META-INF/conf/settings.properties",
    "output_path": "server_home/temp/settings.properties"
  }
}
```

## Common Parameters

These parameters apply to multiple actions:

| Parameter | Description |
|-----------|-------------|
| `jar_path` | Path to the JAR file (required for all actions) |
| `action` | Specifies the JAR action to perform (required) |
| `note` | Variable name to store action results for later use |


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

### Get JAR Version and Store as Variable
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "get_version",
    "jar_path": "server_home/lib/product-core.jar",
    "note": "PRODUCT_VERSION"
  }
}
```

### Verify JAR Version Meets Minimum Requirement
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "check_version",
    "jar_path": "server_home/lib/security.jar",
    "expected_version": "2.0.0",
    "comparison_type": "minimum"
  }
}
```

### Extract Configuration File from JAR
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "extract_file",
    "jar_path": "server_home/lib/product.jar",
    "package_path": "com/me/config/default.properties",
    "output_path": "server_home/temp/extracted_config.properties"
  }
}
```

### Check Manifest for Main Class
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "get_manifest",
    "jar_path": "server_home/lib/starter.jar"
  }
}
```

### Create a New JAR with Custom Files
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "create",
    "jar_path": "server_home/lib/custom-plugin.jar",
    "source_dir": "server_home/temp/plugin",
    "manifest_path": "server_home/temp/plugin/META-INF/MANIFEST.MF"
  }
}
```

### Add Files to an Existing JAR
```json
{
  "operation_type": "jar_operation",
  "parameters": {
    "action": "add",
    "jar_path": "server_home/lib/extensions.jar",
    "files": "server_home/temp/NewClass.class,server_home/temp/resource.properties",
    "base_dir": "server_home/temp",
    "overwrite": "true"
  }
}
```
