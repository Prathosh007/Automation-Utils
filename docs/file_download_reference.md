# File Download Operations Reference 📥

This document provides a comprehensive guide for file download operations available in the GOAT automation framework. The file download functionality allows you to retrieve files from remote URLs with support for resumable downloads, retry mechanisms, and progress tracking.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Actions](#actions)
    - [Download Action](#download)
- [Common Parameters](#common-parameters)
- [Path Variables](#path-variables)
- [Examples](#examples)
- [Advanced Features](#advanced-features)

## Overview

The file download operation provides a robust interface to download files from HTTP/HTTPS URLs, with built-in support for download resumption, automatic retries, and progress tracking.

## Quick Reference

| Icon | Action | Description |
|----|-----------|-------------|
| 📥 | `download` | Download a file from a URL |

## Actions

All actions are performed using the `download_file` operation type.

### 📥 `download`
Downloads a file from a specified URL to a target location.

**Parameters:**
- `url`: URL of the file to download (required)
- `target_path`: Directory path where the file should be saved (optional, defaults tool downloads folder)
- `filename`: Custom filename to save the file as (optional, extracted from URL if not specified)
- `overwrite`: Whether to overwrite existing files (optional, defaults to "true")

**Use Cases:**
```json
{
  "operation_type": "download_file",
  "parameters": {
    "url": "https://example.com/files/document.pdf",
    "target_path": "server_home/downloads",
    "filename": "user_manual.pdf",
    "overwrite": "true"
  }
}
```
```json
{
  "operation_type": "download_file",
  "parameters": {
    "url": "https://example.com/files/installer.zip",
    "target_path": "C:/temp/downloads"
  }
}
```

## Common Parameters

These parameters apply to the download operation:

| Parameter | Description |
|-----------|-------------|
| `url` | URL of the file to download (required) |
| `target_path` | Directory path where the file should be saved (optional) |
| `filename` | Custom filename to save the file as (optional) |
| `overwrite` | Whether to overwrite existing files (optional, "true" or "false") |


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

### Download a File with Default Settings
```json
{
  "operation_type": "download_file",
  "parameters": {
    "url": "https://example.com/files/sample.zip"
  }
}
```

### Download a File to a Specific Directory
```json
{
  "operation_type": "download_file",
  "parameters": {
    "url": "https://example.com/files/sample.zip",
    "target_path": "server_home/downloads/archives"
  }
}
```

### Download a File with a Custom Name
```json
{
  "operation_type": "download_file",
  "parameters": {
    "url": "https://example.com/files/sample.zip",
    "filename": "custom_archive.zip"
  }
}
```

### Download Without Overwriting Existing Files
```json
{
  "operation_type": "download_file",
  "parameters": {
    "url": "https://example.com/files/sample.zip",
    "target_path": "server_home/downloads",
    "overwrite": "false"
  }
}
```

## Advanced Features

The file download operation includes several advanced features:

### Resumable Downloads
If a download is interrupted, the operation can resume from where it left off and the server supports resume capability.

### Automatic Retries
The download automatically retries up to 5 times with progressive back-off if network issues occur.

### Download Progress Tracking
Download progress is logged periodically, showing percentage complete and estimated time remaining for large files.

### SSL Support
HTTPS connections are fully supported with automatic SSL configuration.

### File Size Validation
The download validates that the complete file was received by comparing the downloaded size with the expected size from the server's Content-Length header.