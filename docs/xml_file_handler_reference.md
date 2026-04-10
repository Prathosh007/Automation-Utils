# XML File Edit Operations Reference 📄

This document describes XML file editing operations available in the automation framework, enabling you to verify, update, and manipulate XML files with precision using XPath expressions.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Value Verification Actions](#value-verification-actions)
    - [✅ `value_should_be_present`](#-value_should_be_present)
    - [❌ `value_should_be_removed`](#-value_should_be_removed)
- [Using Notes for Value Capture](#using-notes-for-value-capture)
- [Examples](#examples)

## Overview

The XML file edit operations provide functionality to verify the presence or absence of specific values within XML files using XPath expressions, with the ability to capture values for reuse in subsequent test steps.

## Quick Reference

| Icon | Action | Description |
|------|-----------|-------------|
| ✅ | `value_should_be_present` | Verify a value exists at a specific XPath location |
| ❌ | `value_should_be_removed` | Verify a value does not exist at a specific XPath location |

## Value Verification Actions

All actions are performed using the `file_edit` operation type with the appropriate `action` parameter.

### ✅ `value_should_be_present`

Verifies that a value exists at the specified XPath location in the XML file.

**Parameters:**
- `file_path`: Path to the XML file
- `filename`: Name of the XML file
- `path`: XPath expression to locate the element or attribute **(To generate XPath, you can use any AI tool in online And kindly refer the below example in the document)**
- `value`: Expected value to find (optional)
- `note`: Key name to store the found value for later use (optional)

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/",
    "filename": "server.xml",
    "path": "//Server/Service/Connector/@port",
    "value": "8080"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/",
    "filename": "web.xml",
    "path": "//web-app/display-name",
    "value": "My Application",
    "note": "APPLICATION_NAME"
  }
}
```

### ❌ `value_should_be_removed`

Verifies that a value does not exist at the specified XPath location in the XML file.

**Parameters:**
- `file_path`: Path to the XML file
- `filename`: Name of the XML file
- `path`: XPath expression to locate the element or attribute
- `value`: Value that should not exist (optional)

**Use Cases:**

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf/",
    "filename": "server.xml",
    "path": "//Server/Service/Engine/version"
  }
}
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf/",
    "filename": "context.xml",
    "path": "//Server/Service/Connector[@protocol=\"HTTP/1.1\"]/@driverClassName",
    "value": "oracle.jdbc.driver.OracleDriver"
  }
}
```

## Using Notes for Value Capture

You can use the `note` parameter to store values found in the XML file for later use in your test cases:

1. Add the `note` parameter to your operation to specify a variable name
2. The value found at the specified XPath will be stored in this variable
3. Reference the stored value in subsequent operations using `${VARIABLE_NAME}` syntax

**Example workflow:**

**Example XML File:**
> **Important:** Ask an AI tool to generate the XPath, Example for below xml (Need a xpath to get the redirectPort in connector tag where the protocol is AJP/1.3)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Server>
    <Service name="Catalina">
        <Connector port="8080" protocol="HTTP/1.1"
                   connectionTimeout="20000"
                   redirectPort="8443" />
        <Connector port="8009" protocol="AJP/1.3"
                   redirectPort="8443" />
        <Engine name="Catalina" defaultHost="localhost">
            <Host name="localhost"  appBase="webapps"
                  unpackWARs="true" autoDeploy="true">
                <Context path="" docBase="myApp"/>
            </Host>
        </Engine>
    </Service>
</Server>
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/",
    "filename": "server.xml",
    "path": "//Server/Service/Connector[@protocol=\"AJP/1.3\"]/@redirectPort",
    "note": "RE_PORT"
  }
}
```

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

### Check if Server Port is Set to 8080

**Example XML File:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Server>
    <Service name="Catalina">
        <Connector port="8080" protocol="HTTP/1.1"
                   connectionTimeout="20000"
                   redirectPort="8443" />
        <Connector port="8009" protocol="AJP/1.3"
                   redirectPort="8443" />
        <Engine name="Catalina" defaultHost="localhost">
            <Host name="localhost"  appBase="webapps"
                  unpackWARs="true" autoDeploy="true">
                <Context path="" docBase="myApp"/>
            </Host>
        </Engine>
    </Service>
</Server>
```

```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/",
    "filename": "server.xml",
    "path": "//Server/Service/Connector[@protocol=\"HTTP/1.1\"]/@port",
    "value": "8080"
  }
}
```

### Capture Application Name for Later Use
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/",
    "filename": "web.xml",
    "path": "//web-app/display-name",
    "value": "My Application",
    "note": "APP_NAME"
  }
}
```

### Verify Debug Filter is Removed
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf/",
    "filename": "web.xml",
    "path": "//web-app/filter[filter-name=\"debugFilter\"]"
  }
}
```

### Verify Specific Database Driver is Not Used
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_removed",
    "file_path": "server_home/conf/",
    "filename": "context.xml",
    "path": "//Context/Resource[@name=\"jdbc/AppDB\"]/@driverClassName",
    "value": "com.mysql.jdbc.Driver"
  }
}
```