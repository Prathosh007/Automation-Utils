# Wait For Condition and Operation Retry Parameters Reference ⏱️

This document describes the retry mechanism parameters available for all operations in the GOAT automation framework, allowing you to control execution timing and retry behavior with precision.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Parameters](#parameters)
    - [max_wait_time](#max_wait_time)
    - [check_interval](#check_interval)
- [Examples](#examples)
- [Best Practices](#best-practices)

## Overview

All operations in the GOAT automation framework support built-in retry capabilities, allowing tests to handle transient failures and timing-dependent conditions automatically. The retry mechanism is controlled by two key parameters: `max_wait_time` and `check_interval`.

## Quick Reference

| Parameter | Default | Description |
|-----------|---------|-------------|
| `max_wait_time` | 0 (no retry) | Maximum time in seconds to keep attempting the operation |
| `check_interval` | 0 | Time in seconds to wait between retry attempts |

## Parameters

### max_wait_time

Specifies the maximum duration (in seconds) that the framework will continue attempting an operation before considering it failed.

**Behavior:**
- When set to `0` or not specified: The operation runs once with no retries
- When set to a positive value: The operation will be attempted repeatedly until it succeeds or the time limit is reached

**Usage:**
- Set higher values for operations that may need time to complete (e.g., service starts, file downloads)
- Set lower values or omit for fast operations that should either succeed immediately or fail

### check_interval

Specifies the time to wait (in seconds) between retry attempts when an operation fails.

**Behavior:**
- When set to `0` or not specified: The operation will retry immediately with no delay
- When set to a positive value: The framework will wait the specified time before the next retry attempt

**Usage:**
- Set higher values when retrying too quickly might cause resource contention
- Set lower values when rapid retries are acceptable and quick recovery is desired

## Examples

### Basic File Presence Check with Retry

```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_presence",
    "file_path": "server_home/logs/",
    "filename": "startup.log",
    "max_wait_time": "60",
    "check_interval": "5"
  }
}
```
This operation will check for the presence of `startup.log` and will retry every 5 seconds for up to 60 seconds total if the file isn't found immediately.

### Service Action with Extended Timeout

```json
{
  "operation_type": "service_actions",
  "parameters": {
    "action": "start",
    "service_name": "ApplicationService",
    "max_wait_time": "180",
    "check_interval": "10"
  }
}
```
This operation will attempt to start the service and will retry every 10 seconds for up to 3 minutes if the service fails to start immediately.

### API Test with Quick Retries

```json
{
  "operation_type": "api_case",
  "parameters": {
    "url": "https://api.example.com/status",
    "method": "GET",
    "expected_status": "200",
    "max_wait_time": "30",
    "check_interval": "2"
  }
}
```
This operation will check the API endpoint and retry every 2 seconds for up to 30 seconds if it doesn't return HTTP 200.

## Best Practices

1. **Match retry timing to operation type**:
    - Fast operations (file checks, simple commands): `max_wait_time` of 15-30s, `check_interval` of 1-3s
    - Medium operations (service starts, installations): `max_wait_time` of 60-180s, `check_interval` of 5-10s
    - Long-running operations (large file downloads): `max_wait_time` of 300-900s, `check_interval` of 15-30s

2. **Log monitoring**:
    - The framework logs each retry attempt with elapsed and remaining time
    - Final operation remarks contain a summary of retry attempts

3. **Platform considerations**:
    - Some operations may take longer on slower systems or under heavy load
    - Consider environment-specific retry values for consistent behavior across different systems

> **Note:** Setting appropriate retry parameters can significantly improve test reliability when dealing with asynchronous operations or operations with unpredictable timing.