# PPM Operations Documentation

This document describes PPM (Patch/Service Pack) operations supported by the GOAT framework, including installation and reverting patches for products.

## Overview

The framework supports the following PPM operations:

- **ppm_upgrade**: Downloads and installs a PPM/Service Pack to update a product
- **revert_ppm**: Reverts/uninstalls a previously applied PPM

## 1. ppm_upgrade Operation

The `ppm_upgrade` operation downloads and installs a PPM file to update a product.

### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| url | URL of the PPM file to download | Yes (Now we only support https://build.zohocorp.com/me/ to download the EXE file)     |

### Sample Usage

```json
{
  "Install_PPM": {
    "testcase_id": "Install_PPM",
    "description": "Install PPM update package to upgrade product",
    "reuse_installation": true,
    "operations": [
      {
        "operation_type": "ppm_upgrade",
        "parameters": {
          "url": "https://server/downloads/ProductName_SP1.ppm"
        }
      }
    ],
    "expected_result": "Service Pack should be installed successfully"
  }
}
```

### Implementation Details

> **Important:** Ensure the product service is stopped before applying a PPM to avoid conflicts.

The `ppm_upgrade` operation:

1. Downloads the PPM file from the specified URL
2. Executes the installation command
3. Monitors the output for success or failure messages
4. Returns the result of the installation


## 2. revert_ppm Operation

The `revert_ppm` operation uninstalls a previously applied PPM/Service Pack.

### Parameters

This operation doesn't require any specific parameters. It automatically identifies the most recently applied PPM version from the ppm history file.

### Sample Usage

```json
{
  "Revert_PPM": {
    "testcase_id": "Revert_PPM",
    "description": "Revert the last applied PPM update package",
    "reuse_installation": true,
    "operations": [
      {
        "operation_type": "revert_ppm",
        "parameters": {}
      }
    ],
    "expected_result": "PPM should be reverted successfully"
  }
}
```

### Implementation Details

> **Important:** Ensure the product service is stopped before reverting a PPM to avoid conflicts.

The `revert_ppm` operation:

1. Identifies the most recently applied PPM version from the history file
2. Constructs the command to uninstall that specific PPM version
3. Executes the uninstall command from the product's bin directory
4. Monitors the output for success or failure messages
5. Returns the result of the uninstallation

## Advanced Use Cases

### Combined PPM Operations

You can combine PPM operations to test upgrade and rollback scenarios:

```json
{
  "Upgrade_And_Rollback_Test": {
    "testcase_id": "Upgrade_And_Rollback_Test",
    "description": "Install PPM and then revert it",
    "reuse_installation": true,
    "operations": [
      {
        "operation_type": "ppm_upgrade",
        "parameters": {
          "url": "https://server/downloads/ProductName_SP1.ppm"
        }
      },
      {
        "operation_type": "service_actions",
        "parameters": {
          "service_name": "uems_service",
          "action": "start"
        }
      },
      {
        "operation_type": "service_actions",
        "parameters": {
          "service_name": "uems_service",
          "action": "stop"
        }
      },
      {
        "operation_type": "revert_ppm",
        "parameters": {}
      }
    ],
    "expected_result": "Service Pack should be installed and then successfully reverted"
  }
}
```

## Troubleshooting

Common error scenarios and solutions:

1. **PPM download failure**: Ensure the URL is correct and accessible
2. **Installation failures**: Check product status before installation
3. **Revert failures**: Verify the PPM file exists in the Patch directory
4. **Exit code errors**: Check product log files for detailed error information