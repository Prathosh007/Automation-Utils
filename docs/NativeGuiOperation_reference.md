# Native GUI Operation - Complete Guide
## Overview

This document provides a comprehensive guide for automating native Windows GUI operations using JSON-based commands. The framework supports various GUI actions including clicking, text entry, waiting, reading, grid validation, and more using the FlaUI framework.

---

## Table of Contents

- [1. Supported Actions](#1-supported-actions)
- [2. JSON Command Structure](#2-json-command-structure)
- [3. Element Identification (Spy Mode)](#3-element-identification-spy-mode)
- [4. Common Properties](#4-common-properties)
- [5. Quick Examples](#5-quick-examples)
- [6. Grid Validation](#6-grid-validation)
- [7. Validation Types](#7-validation-types)
- [8. Complete Test Example](#8-complete-test-example)
- [9. Command-Line Usage](#9-command-line-usage)
- [10. Output Format](#10-output-format)
- [11. Best Practices](#11-best-practices)
- [12. Error Handling](#12-error-handling)

---

## 1. Supported Actions
> All available GUI actions you can perform, such as click, enter text, wait, read text, and more.

| # | Action | Description | Example |
|---|--------|-------------|---------|
| 1.1 | `ClickButton` | Click a button element | Click Login button |
| 1.2 | `Click` | Generic click on any element | Click any control |
| 1.3 | `DoubleClick` | Double-click an element | Open file item |
| 1.4 | `RightClick` | Right-click an element | Open context menu |
| 1.5 | `EnterText` | Enter text into a field | Type username |
| 1.6 | `Wait` | Wait for element to appear | Wait for dialog |
| 1.7 | `ReadText` | Read text from element | Read status label |
| 1.8 | `Screenshot` | Capture screenshot | Save current state |
| 1.9 | `Select` | Select dropdown item | Choose from combo box |
| 1.10 | `Toggle` | Toggle checkbox state | Enable/disable option |
| 1.11 | `ReadGrid` | Read and validate grid data | Validate table content |
| 1.12 | `close_all` | Close all instances of a process | Close troubleshooting tool |

---

## 2. JSON Command Structure
> The standard JSON format used to define any native GUI operation with its required and optional fields.

Base structure for all native GUI operations:

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "ClickButton",
    "automationId": "btnLogin",
    "name": "Login",
    "description": "Click the login button"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform (from Supported Actions list) |
| `automationId` | Mandatory | Element's automation ID from spy mode (preferred identifier) |
| `name` | Optional | Element's display name as fallback identifier |
| `description` | Optional | Human-readable step description |
| `continueOnFailure` | Optional | Continue test if this step fails (default: false) |
| `target_app_exe` | Optional | Path to application executable to launch (passed as CLI argument) |

---

## 3. Element Identification (Spy Mode)
> How to use spy mode to inspect GUI elements and extract their automation properties.

### 3.1 Running Spy Mode

```bash
# Global spy mode (inspect any window)
Native_GUI_Utils.exe spy

# App-specific spy mode
Native_GUI_Utils.exe "C:\MyApp\app.exe" spy
```

### 3.2 Spy Mode Workflow

1. Run the spy command in Command Prompt/PowerShell
2. Spy mode attaches to desktop root for global automation
3. Message appears: `Click target (or type 'exit' + Enter to quit)...`
4. Click on the GUI element you want to inspect
5. Wait 10 seconds for spy to capture element properties
6. Properties are displayed in console output
7. Type `exit` and press Enter to quit

### 3.3 Example Spy Mode Output

```
[LOG] Attached to desktop root for global automation.
Automatic-spy mode: click your target, then wait 10 seconds...
Click target (or type 'exit' + Enter to quit)...

 Capturing in 3.2.1.
[LOG] X=2260,Y=219,Name='8125',AutomationId='3',Type=Text
Click target (or type 'exit' + Enter to quit)...

 Capturing in 3.2.1.
[LOG] X=2131,Y=227,Name='',AutomationId='4',Type=Button
```

### 3.4 Spy Output → JSON Mapping

| Spy Output | JSON Field | Description |
|------------|------------|-------------|
| `AutomationId='3'` | `"automationId": "3"` | Preferred identifier (most stable) |
| `Name='8125'` | `"name": "8125"` | Fallback identifier |

### 3.5 Using Spy Output in JSON

**Spy shows:** `[LOG] X=2260,Y=219,Name='8125',AutomationId='3',Type=Text`

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "EnterText",
    "automationId": "3",
    "name": "8125",
    "text": "username_value",
    "description": "Enter username from spy mode identified element"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `text` | Mandatory | Text to enter into the field |
| `description` | Optional | Human-readable step description |

**Spy shows:** `[LOG] X=2131,Y=227,Name='',AutomationId='4',Type=Button`

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "ClickButton",
    "automationId": "4",
    "description": "Click login button from spy mode identified element"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `description` | Optional | Human-readable step description |

### 3.6 Common Element Types from Spy Mode

| Type | Description | Typical Action |
|------|-------------|----------------|
| `Text` | Read-only text or label | ReadText |
| `Button` | Clickable button | ClickButton, Click |
| `Edit` | Text input field | EnterText |
| `ComboBox` | Dropdown list | Select |
| `CheckBox` | Checkbox control | Toggle |
| `TreeItem` | Tree node item | RightClick, DoubleClick |
| `DataGrid` | Table/grid control | ReadGrid |
| `List` | List control | Select |

### 3.7 Tips for Using Spy Mode

| Tip | Benefit |
|-----|---------|
| Always prefer `automationId` over `name` | More stable across UI updates |
| Record multiple elements in sequence | Build complete test workflow |
| Test identified elements immediately | Verify correctness before finalizing |

---

## 4. Common Properties
> A reference of all JSON properties and whether they are required.

| # | Property | Required | Type | Description |
|---|----------|----------|------|-------------|
| 4.1 | `action` | Required | String | Action to perform (see Supported Actions) |
| 4.2 | `automationId` | Conditionally Required | String | Element's AutomationId (from spy mode, preferred) |
| 4.3 | `name` | Conditionally Required | String | Element's display Name (from spy mode, fallback) |
| 4.4 | `text` | Conditionally Required | String | Text to enter (for EnterText, Select actions) |
| 4.5 | `timeout` | Optional | Number | Wait timeout in seconds (for Wait action, default: 30) |
| 4.6 | `expectedValue` | Optional | String | Expected text value (for ReadText validation) |
| 4.7 | `continueOnFailure` | Optional | Boolean | Continue on failure (default: false) |
| 4.8 | `takeScreenshotOnFailure` | Optional | Boolean | Capture screenshot on failure (default: true) |
| 4.9 | `matchPartial` | Optional | Boolean | Use partial name matching (default: false) |
| 4.10 | `description` | Optional | String | Human-readable step description |
| 4.11 | `target_app_exe` | Optional | String | Path to application executable to launch |

**Legend:**
- Required = Must be provided for all operations
- Conditionally Required = Required depending on the action type
- Optional = Can be omitted

---

## 5. Quick Examples
> Ready-to-use JSON samples for common GUI actions.

### 5.1 Click Button

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "ClickButton",
    "automationId": "btnLogin",
    "name": "Login",
    "description": "Click login button"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `description` | Optional | Human-readable step description |

### 5.2 Enter Text

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "EnterText",
    "automationId": "txtUsername",
    "name": "Username",
    "text": "administrator",
    "description": "Enter username into the field"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `text` | Mandatory | Text to enter into the field |
| `description` | Optional | Human-readable step description |

### 5.3 Wait for Element

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "Wait",
    "automationId": "statusLabel",
    "name": "Processing Complete",
    "timeout": 30,
    "matchPartial": true,
    "continueOnFailure": true,
    "description": "Wait for process completion"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Automation ID of element to wait for |
| `name` | Optional | Element's display name as fallback identifier |
| `timeout` | Optional | Wait timeout in seconds (default: 30) |
| `matchPartial` | Optional | Use partial name matching (default: false) |
| `continueOnFailure` | Optional | Continue on failure (default: false) |
| `description` | Optional | Human-readable step description |

### 5.4 Read Text and Validate

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "ReadText",
    "automationId": "lblStatus",
    "name": "Status",
    "expectedValue": "Connected",
    "validationType": "exact",
    "description": "Read connection status"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `expectedValue` | Optional | Expected text value for validation |
| `validationType` | Optional | Validation type: exact, contains, regex, etc. |
| `description` | Optional | Human-readable step description |

### 5.5 Double Click

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "DoubleClick",
    "automationId": "ListViewSubItem-0",
    "name": "ManageEngine UEMS - Agent",
    "continueOnFailure": false,
    "description": "Double-click to open item"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `continueOnFailure` | Optional | Continue on failure (default: false) |
| `description` | Optional | Human-readable step description |

### 5.6 Right Click

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "RightClick",
    "automationId": "treeNode",
    "name": "Server Node",
    "description": "Right-click server node"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `description` | Optional | Human-readable step description |

### 5.7 Screenshot

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "Screenshot",
    "filePath": "Screenshots/login_complete.png",
    "description": "Capture login completion state"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `filePath` | Mandatory | File path to save screenshot |
| `description` | Optional | Human-readable step description |

### 5.8 Select from Dropdown

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "Select",
    "automationId": "cmbRegion",
    "name": "Region Selector",
    "text": "North America",
    "description": "Select region from dropdown"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `text` | Mandatory | Dropdown item text to select |
| `description` | Optional | Human-readable step description |

### 5.9 Toggle Checkbox

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "Toggle",
    "automationId": "chkEnableLogging",
    "name": "Enable Logging",
    "checked": true,
    "description": "Enable logging feature"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `checked` | Mandatory | Desired checkbox state (true or false) |
| `description` | Optional | Human-readable step description |

### 5.10 Generic Click

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "Click",
    "automationId": "UninstallBtn",
    "name": "Uninstall",
    "continueOnFailure": false,
    "description": "Click to uninstall"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `continueOnFailure` | Optional | Continue on failure (default: false) |
| `description` | Optional | Human-readable step description |

### 5.11 Read Grid - Column Validation

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "ReadGrid",
    "automationId": "dataGrid1",
    "columnValidations": {
      "1": ["Success"],
      "2": ["TCP - Success Websocket - Success"]
    },
    "validationType": "exact",
    "description": "Validate RDS connectivity"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Grid element's automation ID from spy mode |
| `columnValidations` | Mandatory | Column index to expected values mapping |
| `validationType` | Optional | Validation type: exact, contains, regex, etc. |
| `description` | Optional | Human-readable step description |

### 5.12 Read Grid - Key-Value Pair Validation

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "ReadGrid",
    "automationId": "dataGrid1",
    "keyValuePairs": {
      "Proxy Server Name": "proxy.company.com",
      "Proxy user Name": "admin",
      "Proxy Enabled": "true"
    },
    "keyColumn": 0,
    "valueColumn": 1,
    "validationType": "exact",
    "description": "Validate system information"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `automationId` | Mandatory | Grid element's automation ID from spy mode |
| `keyValuePairs` | Mandatory | Key-value pairs to validate in grid |
| `keyColumn` | Mandatory | Column index for key lookup (zero-based) |
| `valueColumn` | Mandatory | Column index for value comparison (zero-based) |
| `validationType` | Optional | Validation type: exact, contains, regex, etc. |
| `description` | Optional | Human-readable step description |

### 5.13 Close All Instances

```json
{
  "operation_type": "native_gui_operation",
  "parameters": {
    "action": "close_all",
    "processName": "agent_troubleshooting_tool",
    "description": "Close all instances of troubleshooting tool"
  }
}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform |
| `processName` | Mandatory | Process name to close all instances of |
| `description` | Optional | Human-readable step description |

---

## 6. Grid Validation
> Configuration details for validating grid/table data.

### 6.1 keyValuePairs Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `keyColumn` | Number | Yes | Column index for key values (zero-based) |
| `valueColumn` | Number | Yes | Column index for value comparison (zero-based) |
| `validationType` | String | Yes | Type of validation (exact, contains, regex, etc.) |
| `continueOnFailure` | Boolean | No | Continue on validation failure |
| `caseSensitive` | Boolean | No | Case-sensitive comparison |

### 6.2 columnValidations Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `row` | Number | Yes | Row index (zero-based) |
| `column` | Number | Yes | Column index (zero-based) |
| `expectedValue` | String | Yes | Expected cell value |
| `validationType` | String | Yes | Type of validation |
| `caseSensitive` | Boolean | No | Case-sensitive comparison |

---

## 7. Validation Types
> All supported validation types for ReadText and ReadGrid actions.

| Type | Description | Example |
|------|-------------|---------|
| `exact` | Exact string match | "Success" |
| `contains` | Contains substring | "Success" |
| `startsWith` | Starts with string | "http" |
| `endsWith` | Ends with string | ".com" |
| `regex` | Regular expression | "^\\d{3}-\\d{2}$" |
| `oneOf` | One of multiple values | ["Success","Pending"] |
| `notEmpty` | Not empty value | N/A |
| `isEmpty` | Empty value | N/A |
| `numericEquals` | Numeric equality | "100" |
| `numericLessThan` | Less than number | "100" |
| `numericGreaterThan` | Greater than number | "0" |
| `numericBetween` | Between range | "10,100" |
| `isNumeric` | Valid number format | N/A |
| `length` | Exact length | "10" |
| `minLength` | Minimum length | "5" |
| `maxLength` | Maximum length | "50" |
| `isDate` | Valid date format | N/A |
| `isUrl` | Valid URL format | N/A |
| `isEmail` | Valid email format | N/A |

---

## 8. Complete Test Example
> A full end-to-end workflow demonstrating GUI automation with multiple steps.

### 8.1 Uninstall Agent from Control Panel

```json
[
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "EnterText",
      "name": "Search Box",
      "automationId": "SearchEditBox",
      "description": "Entering agent name in search box",
      "text": "UEMS-Agent"
    }
  },
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "DoubleClick",
      "automationId": "ListViewSubItem-0",
      "name": "ManageEngine UEMS - Agent",
      "continueOnFailure": false
    }
  },
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "Wait",
      "automationId": "UninstallBtn",
      "name": "Uninstall",
      "timeout": 30,
      "description": "Wait for uninstall button to appear"
    }
  },
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "Click",
      "automationId": "UninstallBtn",
      "name": "Uninstall",
      "continueOnFailure": false,
      "description": "Click to uninstall Agent"
    }
  },
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "Wait",
      "automationId": "SuccessUninstall",
      "name": "Agent successfully uninstalled",
      "timeout": 420,
      "description": "Wait for uninstallation completion"
    }
  },
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "ReadText",
      "automationId": "SuccessUninstall",
      "name": "Agent successfully uninstalled",
      "expectedValue": "Agent successfully uninstalled",
      "description": "Read uninstallation status"
    }
  }
]
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Mandatory | GUI action to perform (EnterText, DoubleClick, Wait, Click, ReadText) |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `text` | Mandatory | Text to enter (for EnterText action) |
| `timeout` | Optional | Wait timeout in seconds (default: 30) |
| `expectedValue` | Optional | Expected text value for validation (for ReadText action) |
| `continueOnFailure` | Optional | Continue on failure (default: false) |
| `description` | Optional | Human-readable step description |

### 8.2 Troubleshooting Tool with Target App

```json
[
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "target_app_exe": "C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent\\bin\\agent_troubleshooting_tool.exe",
      "action": "Wait",
      "name": "Start Troubleshooting",
      "automationId": "button1",
      "timeout": 10,
      "description": "Wait for troubleshooting tool to load"
    }
  },
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "ClickButton",
      "name": "Start Troubleshooting",
      "automationId": "button1",
      "description": "Start the troubleshooting process"
    }
  },
  {
    "operation_type": "native_gui_operation",
    "parameters": {
      "action": "Wait",
      "automationId": "troubleshoot_complete",
      "name": "Diagnosis Completed",
      "timeout": 180,
      "description": "Wait for diagnosis to complete"
    }
  }
]
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `target_app_exe` | Optional | Path to application executable to launch (first step only) |
| `action` | Mandatory | GUI action to perform (Wait, ClickButton) |
| `automationId` | Mandatory | Element's automation ID from spy mode |
| `name` | Optional | Element's display name as fallback identifier |
| `timeout` | Optional | Wait timeout in seconds (default: 30) |
| `description` | Optional | Human-readable step description |

> **Note:** `target_app_exe` is only needed in the first operation to launch the application. Subsequent operations interact with the already-launched application without needing it again.

---

## 9. Command-Line Usage
> How the GUI EXE is invoked internally by the framework.

| # | Command | Purpose |
|---|---------|---------|
| 9.1 | `Native_GUI_Utils.exe commands.json` | Execute commands from JSON file |
| 9.2 | `Native_GUI_Utils.exe "C:\app.exe" commands.json` | Launch app and execute commands |
| 9.3 | `Native_GUI_Utils.exe spy` | Spy mode to inspect elements |
| 9.4 | `Native_GUI_Utils.exe "C:\app.exe" spy` | Spy mode for specific app |
| 9.5 | `Native_GUI_Utils.exe explore [app.exe]` | Explore application structure |
| 9.6 | `Native_GUI_Utils.exe validate commands.json` | Validate JSON without execution |

---

## 10. Output Format
> The structure of results returned after execution.

```
step-001|PASSED|Login successful
step-002|FAILED|Element not found: Submit button
SUMMARY|Total:3|Passed:2|Failed:1
```

**Format Breakdown:**
- **Step ID** - Unique identifier from your command
- **Status** - PASSED or FAILED
- **Message** - Detailed result or error message
- **SUMMARY** - Total commands, passed count, failed count

---

## 11. Best Practices
> Recommended guidelines for writing reliable GUI test operations.

| # | Practice | Importance | Details |
|---|----------|-----------|---------|
| 11.1 | Prefer automationId over name | High | AutomationId is more stable across UI updates |
| 11.3 | Add Wait steps before actions | High | Ensure elements are loaded before interacting |
| 11.4 | Set appropriate timeouts | High | Configure realistic wait times for operations |
| 11.5 | Use continueOnFailure carefully | High | Only continue on non-critical failures |
| 11.6 | Use spy mode for element IDs | High | Always identify elements via spy mode first |
| 11.7 | Add meaningful descriptions | Medium | Include clear descriptions for each step |
| 11.8 | Capture screenshots on failure | Medium | Enable screenshots for debugging |
| 11.9 | Use one operation per step | Medium | New format: each step is a separate operation |
| 11.10 | Test incrementally | Medium | Start with basic steps, add complexity gradually |

---

## 12. Error Handling
> Common errors and their solutions.

| # | Error | Cause | Solution |
|---|-------|-------|----------|
| 12.1 | Element Not Found | Incorrect automation ID | Verify using spy mode |
| 12.2 | Timeout Exceeded | Element not loaded in time | Increase timeout, add Wait step |
| 12.3 | Action is required | Missing action field | Ensure `action` is specified in parameters |
| 12.4 | Boolean parse error | String "false" instead of false | Framework handles type conversion automatically |
| 12.5 | Application Not Found | Invalid target_app_exe path | Verify application path exists |
| 12.6 | Grid Validation Failed | Unexpected grid content | Review grid structure and validation rules |
| 12.7 | Text Validation Failed | Unexpected text content | Verify expected values and case sensitivity |

---

## Quick Navigation Guide

**For Getting Started:** Refer to [Section 2](#2-json-command-structure), [Section 3](#3-element-identification-spy-mode), [Section 5](#5-quick-examples)

**For Testing:** Refer to [Section 8](#8-complete-test-example), [Section 9](#9-command-line-usage), [Section 11](#11-best-practices)

**For Grid Operations:** Jump to [Section 5.13](#513-read-grid---column-validation), [Section 6](#6-grid-validation)

**For Troubleshooting:** Go to [Section 12](#12-error-handling)

---

