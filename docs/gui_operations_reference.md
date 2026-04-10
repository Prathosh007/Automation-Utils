# GUI Operations Reference

This document describes the GUI operation types available in the test automation framework.

## GUI Operation Types

All GUI operations use the `gui_operation` operation type with different action parameters.

### GUI Actions Keys

| Key               | Description                                                                          |
|-------------------|--------------------------------------------------------------------------------------|
| system_properties | Json array with key value pairs which are registered as system properties in gui jvm |
| main_args         | Json array with string which are passed as arguments for the bat                     |

#### Examples
```json
{
  "testcase_id": "testcaseId",
  "description": "To check DB migration from SQL DB using windows authentication",
  "system_properties":[
    {
      "key":"dbnameeditable",
      "value":"true"
    }
  ],
  "main_args":["dbnameeditable=true"],
  "operations": [
    "operation json array here"
  ]
}
```

### Common Parameters for All GUI Operations

| Parameter          | Description                                                                                                                                            |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app_type`         | Application type (`SGS`, `BACKUP`, `PPM`, `DB_MIGRATION`)                                                                                              |
| `component_id`     | ID of the component to interact with                                                                                                                   |
| `component_text`   | Text of the component (optional, for fallback identification)                                                                                          |
| `invoke_in_thread` | To invoke an operation in parallel in a separate thread                                                                                                |
| `component_reference_name` | To store a component and perform operations inside that specific component later (`Dialog`, `Popup`)                                                   |
| `root_component`   | To perform an operation inside a specific component other than the loaded frame <br/> Need to store the component using `component_reference_name` first |
| `product_name`     | Product name to update the service name by product name (optional)                                                                                     |
| `waitFor`          | Wait for the specified number of seconds after the operation is completed (optional)                                                                   |
| `server_home` | Path to the server home directory. If provided in the first operation of the test case, this value is set as the path for the GUI to initiate the bat. |

### Specific Actions

#### 1. click_button
Clicks a button in the GUI.

**Additional Parameters:**
- `blind_search`: `true` | `false` (optional) — Searches the container blindly for the component by text, without requiring a `component_id`
- `component_text`: Button text to match and verify

> **Note:** Blind search supports clicking `AbstractButton`, `JLabel` (with mouse listener), and `TextComponent` (with mouse listener) elements.

**Example With Component Id:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "click_button",
    "component_id": "nextButton",
    "component_text": "Next",
    "server_home": "C:\\Program Files\\ManageEngine\\SecureGatewayServer"
  }
}
```

**Example With Blind Search:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "click_button",
    "blind_search": true,
    "component_text": "Next"
  }
}
```

#### 2. enter_text
Enters text into a text field.

**Additional Parameters:**
- `text_to_enter`: Text to enter in the field

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "enter_text",
    "component_id": "serverNameField",
    "text_to_enter": "localhost"
  }
}
```

#### 3. select_checkbox
Selects or deselects a checkbox.

**Additional Parameters:**
- `select_state`: true/false (optional, defaults to toggle current state)

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "Backup",
    "action": "select_checkbox",
    "component_id": "enableBackupCheckbox",
    "select_state": true
  }
}
```

#### 4. select_dropdown
Selects an item from a dropdown.

**Additional Parameters:**
- `select_option`: Item to select from the dropdown (required)

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "PPM",
    "action": "select_dropdown",
    "component_id": "protocolDropdown",
    "select_option": "HTTPS"
  }
}
```

#### 5. get_text
Gets text from a component.

**Additional Parameters:**
- None specific to this action

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "DB",
    "action": "get_text",
    "component_id": "statusLabel"
  }
}
```

#### 6. verify_text
Verifies that a component contains specific text.

**Additional Parameters:**
- `expected_text`: Text that should be present in the component
- `blind_search`: To search the text blindly in the container without id (optional)

**Example With Component Id:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "verify_text",
    "component_id": "statusLabel",
    "expected_text": "Connection successful"
  }
}
```

**Example With Blind Search:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "verify_text",
    "blind_search": true,
    "expected_text": "Connection successful"
  }
}
```

#### 7. wait_for
Waits for a specific condition to be met, such as a progress bar reaching a value, a component becoming visible, a window appearing, or a component rendering/unrendering.

**Additional Parameters:**
- `type`: Type of wait condition (required)
- `component_id`: Component to interact with (required for some types)
- `expected_value`: Value to wait for (required) — meaning depends on `type`:
  - `PROGRESS_BAR` — Progress fill percentage (e.g., `100`)
  - `COMPONENT_VISIBILITY` — `1` (visible + enabled) or `2` (hidden + disabled)
  - `WINDOW` — Title of the window to wait for
  - `COMPONENT_RENDER` — `component_id` of the component to wait for
  - `COMPONENT_UNRENDER` — Waits until the component is no longer showing
- `timeout_s`: Timeout in seconds (optional, default: `60`)
- `verify_interval_s`: Interval in seconds between checks (optional, default: `10`)

**Supported Wait Types:**
```json
{
  "Type":[
    "PROGRESS_BAR",
    "COMPONENT_VISIBILITY",
    "COMPONENT_RENDER",
    "COMPONENT_UNRENDER",
    "WINDOW"
  ]
}
```

**Example — Wait for Progress Bar to reach 100%:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "wait_for",
    "type": "PROGRESS_BAR",
    "timeout_s": 60,
    "expected_value": 100,
    "verify_interval_s": 10
  }
}
```

**Example — Wait for a Window with title `Configure Database`:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "wait_for",
    "type": "WINDOW",
    "timeout_s": 60,
    "expected_value": "Configure Database",
    "verify_interval_s": 3
  }
}
```

**Example — Wait for Component to become Visible and Enabled:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "wait_for",
    "type": "COMPONENT_VISIBILITY",
    "component_id": "nextButton",
    "timeout_s": 60,
    "expected_value": 1,
    "verify_interval_s": 3
  }
}
```

**Example — Wait for Component with ID `okButton` to Render:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "wait_for",
    "type": "COMPONENT_RENDER",
    "timeout_s": 60,
    "expected_value": "okButton",
    "verify_interval_s": 3
  }
}
```

**Example — Wait for Component to Unrender (disappear):**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "wait_for",
    "type": "COMPONENT_UNRENDER",
    "component_id": "loadingSpinner",
    "timeout_s": 60,
    "expected_value": "loadingSpinner",
    "verify_interval_s": 3
  }
}
```
#### 8. detect_dialog
To get the Dialog windows.

**Additional Parameters:**
- `dialog_title`: Title of dialog window
- Use `component_reference_name` to store and later perform operations on the dialog

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "detect_dialog",
    "dialog_title": "Configuration"
  }
}
```

#### 9. get_current_window
To get the latest `JFrame` and update in fixture.<br>
>**Note** :<br>
>When new window other than `Initial Frame` is changed or hidden and new frame is shown , Need to update the window with this<br>
> The operation happening after this will be done on this `Captured Frame`


**Additional Parameters:**
- `window_title`: Title of dialog window

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "get_current_window",
    "window_title": "Configuration"
  }
}
```

#### 10. select_tab
Selects a tab by its title text in a `JTabbedPane` component.

**Additional Parameters:**
- `component_id`: ID of the `JTabbedPane` component (required)
- `tab_title`: Title of the tab to select (required)

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "select_tab",
    "component_id": "tab",
    "tab_title": "Restore"
  }
}
```

#### 11. is_showing
Checks whether a component is currently visible (showing) on the screen. Fails if the component is not visible.

**Additional Parameters:**
- `component_id`: ID of the component to check (required)

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "is_showing",
    "component_id": "statusPanel"
  }
}
```

#### 12. select_radio_button
Selects a radio button by matching its text.

**Additional Parameters:**
- `component_id`: ID of the radio button component (required)
- `radio_text`: Text of the radio button to match and select (required)

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "DB_MIGRATION",
    "action": "select_radio_button",
    "component_id": "sqlauthtype",
    "radio_text": "SQL Server Authentication"
  }
}
```

#### 13. clear_text_field
Clears the text from a text field component.

**Additional Parameters:**
- `component_id`: ID of the text field to clear (required)

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "clear_text_field",
    "component_id": "serverNameField"
  }
}
```

#### 14. file_chooser
Interacts with a file chooser dialog to enter a file name and click a button (e.g., Open, Save).

**Additional Parameters:**
- `file_name_text`: File name or path to enter in the file chooser text field (optional)
- `button_text`: Text of the button to click in the file chooser dialog (e.g., `"Open"`, `"Save"`) (optional)
- `base_dialog`: Reference name of the dialog containing the file chooser (optional, uses `root_component` or auto-detects if not provided)

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "file_chooser",
    "file_name_text": "C:\\certs\\server.cer",
    "button_text": "Open"
  }
}
```

#### 15. list_component
Prints the complete Swing component hierarchy of the current frame or root component to the log. Useful for debugging and identifying component IDs.

**Additional Parameters:**
- None

**Example:**
```json
{
  "operation_type": "gui_operation",
  "parameters": {
    "app_type": "SGS",
    "action": "list_component"
  }
}
```

> **Tip:** Use `list_component` during development to discover the `component_id` values available in the current frame.

---

## Executing Non-GUI Operations Within GUI Test Cases

The framework supports executing non-GUI operations (e.g., `file_folder_operation`, `database_operation`, etc.) in between GUI operations within the same test case. If an action does not match any GUI action, the framework automatically delegates it to the standard operation handler.

**Example:**
```json
{
  "operation_type": "file_folder_modification",
  "parameters": {
    "action": "value_should_be_present",
    "file_path": "server_home/conf/settings.txt",
    "value": "Configuration saved"
  }
}
```

> **Note:** Non-GUI operations use the standard `operation_type` (not `gui_operation`) and are handled transparently within the GUI test flow.

---

## Application Types

The framework supports different application types that determine which Java environment and frame to use:

### SGS
For Secure Gateway Server GUI operations:
- Uses SGS server home directory
- Runs Java executable from SGS JRE
- Initializes frame using SGS's ConfigurationFQDN class

### BACKUP
For Backup Server GUI operations:
- Gets server home from ServerUtils
- Runs Java executable from Backup server's JRE
- Initializes frame using Backup configuration class

### PPM
For Patch Manager GUI operations:
- Gets server home from ServerUtils
- Runs Java executable from PPM server's JRE
- Initializes frame using PPM configuration class

### DB_MIGRATION
For Database configuration GUI operations:
- Gets server home from ServerUtils
- Runs Java executable from DB server's JRE
- Initializes frame using DB configuration class
