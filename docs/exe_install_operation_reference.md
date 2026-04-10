# EXE Install Action Reference 📦

This document describes the powerful EXE installation action available in the GOAT automation framework, allowing you to automate the installation and setup of Windows applications with precision and reliability.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Parameters](#parameters)
- [Product Configuration File](#product-configuration-file)
- [Use Cases](#use-cases)
    - [Basic Installation](#basic-installation)
    - [Custom Installation Path](#custom-installation-path)
    - [Product-Specific Installation](#product-specific-installation)
- [AutoIT Script Execution](#autoit-script-execution)
- [Precondition Handling](#precondition-handling)
- [Platform-Specific Behavior](#platform-specific-behavior)

## Overview

The EXE Install action provides an automated way to install Windows executable applications using AutoIT scripts, custom paths, and automatic cleanup of previous installations.

## Quick Reference

| Parameter | Required | Description                                                                                                      |
|-----------|----------|------------------------------------------------------------------------------------------------------------------|
| `product_name` | Yes | Product type to install (e.g., "endpointcentral")                                                                |
| `url` | Yes | URL to download the installer from  |
| `installer_name` | No | Name of the installer file (derived from URL if not provided)                                                    |
| `install_path` | No | Path to install the product (auto-generated if not provided)                                                     |
| `setup_name` | No | Name of the setup used by installer (from product config if not provided)                                        |
| `base_dir` | No | Base directory for operations (default: GOAT home directory)                                                     |
| `download_dir` | No | Directory for downloaded installers (default: ../../downloads)                                                   |
| `scripts_dir` | No | Directory containing AutoIT scripts (default: ../AutoIT/scripts)                                                 |
| `autoit_dir` | No | Directory containing AutoIT executable (default: ../AutoIT)                                                      |
| `logs_dir` | No | Directory for log files (default: ../log/autoit)                                                                 |

## Product Configuration File

The installation process relies on the `product-setup.json` configuration file, which defines properties for supported products:

### Location

The file is located at `product_package/conf/product-setup.json`.

### Structure

Each product entry contains essential information for installation and uninstallation:

```json
"productName": {
  "serviceName": "service_name",
  "displayName": "Product Display Name",
  "uninstallScript": "If you have uninstall script, provide the path here",
  "scriptFile": "ProductInstallScript.au3",
  "setupName": "ManageEngine_Product_Setup",
  "depended_product": ["product1", "product2"],
  "registry_code": "REGISTRY-UUID-CODE1",
}
```

### Array Parameters for Uninstallation

#### `depended_product` Array
This array contains all dependent product names that need to be uninstalled during the cleanup process:
- **Include services from dependent products that must be removed or uninstalled before the given product installation.**
- The framework will attempt to stop all the dependent services and uninstall them before proceeding with the given product installation.

### Adding New Products

To add support for additional products:

1. Add a new entry in `product-setup.json` following the same structure as existing entries.
2. Ensure the `depended_product` list includes all product that need to be stopped or uninstalled before installing the new product.
3. If the depended product is not already present in the `product-setup.json`, you must add it as well.
4. Ensure your AutoIT script exists in the scripts directory.
5. **Important**: The `product_name` parameter in your operation JSON must match exactly the key used in the `product-setup.json` file and kindly ensure the dependent products and product name are correctly defined.

```json
{
  "operation_type": "exe_install",
  "parameters": {
    "product_name": "newProduct",
    "url": "https://downloads.example.com/newProduct.exe"
  }
}
```

### Best Practices

- **DO NOT** modify existing product entries unless you're certain of the changes
- Keep keys consistent (product name should match in depended_product and product_name)
- The framework will handle case sensitivity, but it's best to be consistent
- When adding new products, ensure you include all dependent product names in the `depended_product` array
- Include registry code in the `registry_code` key for proper uninstallation
- The input JSON `product_name` parameter must match the key name in `product-setup.json`

## Parameters

### Required Parameters

#### `product_name`
The type of product to install. This must match one of the supported product types in the configuration file.

#### `url`
The URL from which to download the installer file. The framework will automatically download the installer before proceeding with installation.

### Optional Parameters

#### `installer_name`
The name of the installer file. If not provided, it will be derived from the URL.

#### `install_path`
The path where the product will be installed. If not provided, a default path will be generated:
- For server products: `ServerDirectory/<timestamp>`
- For non-server products: `<base_dir>/installed/<product_type>`

#### `setup_name`
The name of the setup used by the installer. If not provided, it will be taken from the product configuration.

#### `base_dir`, `download_dir`, `scripts_dir`, `autoit_dir`, `logs_dir`
Various directory paths used during the installation process. If not provided, default values will be used.


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




## Use Cases

### Basic Installation

Install a product with minimal configuration, using defaults for paths and other options:

```json
{
  "operation_type": "exe_install",
  "parameters": {
    "product_name": "endpointcentral",
    "url": "https://downloads.example.com/endpointcentral_12.0.0.exe"
  }
}
```

### Custom Installation Path

Install a product in a specific directory:

```json
{
  "operation_type": "exe_install",
  "parameters": {
    "product_name": "endpointcentral",
    "url": "https://downloads.example.com/endpointcentral_12.0.0.exe",
    "install_path": "D:/Applications/EndpointCentral"
  }
}
```

### Product-Specific Installation

Install a specific product with custom settings:

```json
{
  "operation_type": "exe_install",
  "parameters": {
    "product_name": "patch_manager",
    "url": "https://downloads.example.com/patchmanager_10.1.2.exe",
    "installer_name": "patchmanager_10.1.2.exe",
    "install_path": "E:/ManageEngine/PatchManager",
    "setup_name": "ManageEngine Patch Manager Plus",
    "download_dir": "D:/temp/downloads",
    "logs_dir": "D:/temp/logs"
  }
}
```

## AutoIT Script Execution

The action runs AutoIT scripts with the following argument order:

```
AutoIt3.exe <script_path> <installer_path> <install_path> <log_file_path> <setup_name>
```

Where:
- `<script_path>`: Path to the AutoIT script for the specific product
- `<installer_path>`: Path to the downloaded installer executable
- `<install_path>`: Target installation directory
- `<log_file_path>`: Path where installation logs will be written
- `<setup_name>`: Name of the setup program used by the installer

## Precondition Handling

The EXE Install action automatically handles preconditions before installation:

1. **Previous Installation Check**: Checks if the product is already installed by:
    - Looking for a running service associated with the product in the `product-setup.json`
    - Checking for registry entries associated with the product in the `product-setup.json`

2. **Cleanup**: If a previous installation is found:
    - Stops the service if running
    - Runs the product's uninstall script
    - Removes registry entries
    - Verifies successful removal

This ensures a clean installation environment.

> **Note:** This action is only available on Windows platforms and requires proper setup of AutoIT scripts and directories. A maximum of 5 server folders are maintained in the server directory (default DIR) in `tool_home`. If the server folder count exceeds this limit (5), the oldest server folder will be removed automatically.