# Automation-Utils

> **End-to-end automation suite for UEMS Agent testing** — traffic interception, GUI validation, registry operations, API testing, and AI-assisted analysis — all driven by JSON test definitions.

![Python](https://img.shields.io/badge/Python-3.10+-3776AB?logo=python&logoColor=white)
![C#](https://img.shields.io/badge/C%23-.NET%208-512BD4?logo=dotnet&logoColor=white)
![C++](https://img.shields.io/badge/C++-Win32-00599C?logo=cplusplus&logoColor=white)
![Java](https://img.shields.io/badge/Java-Spring%20Boot-6DB33F?logo=spring&logoColor=white)
![mitmproxy](https://img.shields.io/badge/mitmproxy-10.0+-4B8BBE)
![FlaUI](https://img.shields.io/badge/FlaUI-4.0-blue)
![Platform](https://img.shields.io/badge/Platform-Windows-0078D6?logo=windows&logoColor=white)
![License](https://img.shields.io/badge/License-Proprietary-red)

---

## Table of Contents

- [Overview](#overview)
- [Repository Structure](#repository-structure)
- [Components](#components)
  - [CommunicationAutomation (Python)](#1-communicationautomation--python-mitm-proxy-framework)
  - [Agent GUI Utils (C#)](#2-agent-gui-utils--c-windows-gui-automation)
  - [Registry Automation (C++)](#3-registry-automation--c-windows-registry-operations)
  - [G.O.A.T Server (Java)](#4-goat-server--java-test-orchestration--api)
  - [API Explorer (Web)](#5-api-explorer--web-ui)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [JSON Test Format](#json-test-format)
- [Supported Actions](#supported-actions)
- [Utilities & Helpers](#utilities--helpers)
- [Documentation](#documentation)
- [Tech Stack](#tech-stack)
- [GitHub Repository Workflow](#github-repository-workflow)
- [License & Code Protection](#license--code-protection)

---

## Overview

### The Core Idea

> **Write test cases in plain language → Convert to JSON → Every util executes automatically.**

This framework treats **JSON as a universal automation layer**. Instead of writing scripts for each test scenario, you describe *what* to test in a simple JSON file — and the engine figures out *how* to execute it by dispatching to the right utility.

```
┌─────────────────────┐       ┌──────────────────┐       ┌─────────────────────────┐
│  Natural Language    │       │   JSON Test       │       │   Automation Engine      │
│  Test Case           │──────▶│   Definition      │──────▶│   (picks the right util) │
│                      │       │                   │       │                           │
│  "Verify agent       │       │  { "action":      │       │   ✓ start_proxy           │
│   connects to server │       │    "validate_     │       │   ✓ validate_request      │
│   with auth header"  │       │    request",      │       │   ✓ assert_request_count  │
│                      │       │    "url_pattern": │       │   ✓ capture_traffic       │
│                      │       │    "agentSlot",   │       │   ✓ readgrid (GUI)        │
│                      │       │    "header_name": │       │   ✓ registry_operation    │
│                      │       │    "Authorization"│       │   ✓ ... 180+ more utils   │
│                      │       │  }                │       │                           │
└─────────────────────┘       └──────────────────┘       └─────────────────────────┘
```

### How It Works

1. **Describe** your test case — what to install, what to click, what traffic to validate
2. **Write JSON** — each step is a command with an `action` and its parameters
3. **Run** — the engine reads the JSON, dispatches each step to the correct utility (Python/C#/C++/Java), and collects results
4. **Every util is JSON-triggered** — no manual scripting needed per test case

```json
[
    {"action": "exe_install",          "comment": "Install the agent"},
    {"action": "start_proxy",          "comment": "Start traffic interception"},
    {"action": "wait_for_request",     "comment": "Wait for agent check-in"},
    {"action": "validate_request",     "comment": "Verify auth header is correct"},
    {"action": "readgrid",             "comment": "Read agent status from GUI table"},
    {"action": "registry_operation",   "comment": "Verify registry keys were written"},
    {"action": "capture_traffic",      "comment": "Save all captured traffic"},
    {"action": "stop_proxy",           "comment": "Cleanup"}
]
```

> **One JSON file** can orchestrate Python proxy utils, C# GUI utils, C++ registry utils, and Java operation handlers — all in a single test flow.

### What This Repo Provides

This is a **multi-language automation toolkit** with **191 utilities** for testing UEMS (Unified Endpoint Management & Security) Agents. It covers the full testing lifecycle:

| Capability | Component | Language | Triggered Via |
|-----------|-----------|----------|---------------|
| HTTPS traffic interception & validation | CommunicationAutomation | Python | JSON commands |
| Windows GUI automation & UI testing | Agent GUI Utils | C# (.NET 8) | JSON commands |
| Windows Registry read/write/patch | Registry Automation | C++ (Win32) | JSON commands |
| Test orchestration, execution & reporting | G.O.A.T Server | Java (Spring Boot) | JSON test cases |
| AI-assisted test analysis | LLM Integration | Java + Ollama | JSON input/output |
| Interactive API exploration | API Explorer | HTML/CSS/JS | REST API |

All components share a common design: **JSON-driven test definitions** processed by a registry-based action engine.

---

## What I Built — Complete Utils Summary

> **187 files** across **5 languages** — every utility hand-crafted for UEMS Agent automation.

### At a Glance

| Category | Count | Language | Description |
|----------|------:|----------|-------------|
| Communication Actions | 23 | Python | Proxy, traffic, validation, OS-level locking |
| Proxy Layer | 5 | Python | mitmproxy integration, traffic store, rule engine |
| Python Utilities | 4 | Python | Logging, pattern matching, variable context, service discovery |
| GUI Actions | 18 | C# | Click, type, read, grid, screenshot, app lifecycle |
| GUI Utilities & Helpers | 6 | C# | FlaUI automation base, Win32 interop, validation |
| Registry Utils | 4 | C++ | WOW64-aware registry read/write/delete with JSON input |
| Operation Handlers | 25 | Java | Install, uninstall, service, DB, API, machine ops |
| File Operation Handlers | 15 | Java | JSON, XML, CSV, XLSX, PDF, ZIP, text, config, cert |
| Java Utilities | 23 | Java | HTTP client, command execution, process scanner, UAC handling |
| REST API Layer | 40 | Java | Spring Boot controllers, services, adapters, DTOs |
| Core Framework | 24 | Java | Test engine, result analysis, LLM integration, reporting |
| Web Components | 4 | HTML/JS/CSS | Interactive API explorer UI |
| **Total** | **191** | | |

---

### Python — Communication Automation Utils (32 files)

<details>
<summary><b>23 Action Handlers</b> — traffic interception, validation, OS-level control</summary>

| # | Action | File | What It Does |
|---|--------|------|-------------|
| 1 | `start_proxy` | `start_proxy.py` | Spawn mitmproxy as a background process that survives parent exit |
| 2 | `stop_proxy` | `stop_proxy.py` | Stop proxy gracefully (in-process or via taskkill) |
| 3 | `install_certificate` | `install_certificate.py` | Install/remove mitmproxy CA cert in Windows trust store |
| 4 | `install_cert` | `install_cert.py` | Combined install CA cert action |
| 5 | `remove_certificate` | `remove_certificate.py` | Remove CA cert from trust store |
| 6 | `load_client_cert` | `load_client_cert.py` | Load agent mTLS cert + key, combine into PEM, hot-reload into proxy |
| 7 | `configure_proxy` | `configure_proxy.py` | Configure Windows system proxy via netsh/registry |
| 8 | `traffic_lock` | `traffic_lock.py` | OS-level lock: hosts file + port proxy + per-exe firewall + DNS flush |
| 9 | `traffic_unlock` | `traffic_lock.py` | Reverse all OS-level redirections |
| 10 | `run_command` | `run_command.py` | Run arbitrary executables and capture output |
| 11 | `manage_service` | `manage_service.py` | Start/stop/restart Windows services with auto-discovery |
| 12 | `wait_for_request` | `wait_for_request.py` | Block until matching HTTP request arrives (in-process + file-polling) |
| 13 | `wait_for_interrupt` | `wait_for_interrupt.py` | Block until Ctrl+C — recording mode |
| 14 | `load_traffic` | `load_traffic.py` | Load captured traffic from JSON file into store |
| 15 | `capture_traffic` | `capture_traffic.py` | Dump captured traffic to JSON file |
| 16 | `clear_captures` | `clear_captures.py` | Reset traffic buffer and capture file |
| 17 | `switch_capture_file` | `switch_capture_file.py` | Switch live capture to a new file mid-test |
| 18 | `validate_request` | `validate_request.py` | Assert request URL, method, headers, body |
| 19 | `validate_response` | `validate_response.py` | Assert response status, headers, body |
| 20 | `assert_request_count` | `assert_request_count.py` | Verify captured request count (min/max/exact) |
| 21 | `assert_request_order` | `assert_request_order.py` | Verify request sequence ordering |
| 22 | `modify_response` | `modify_response.py` | Inject status/body/headers or search-replace on responses |
| 23 | `block_request` | `block_request.py` | Block or delay matching requests |

</details>

<details>
<summary><b>5 Proxy Layer Components</b> — mitmproxy integration & traffic management</summary>

| Component | File | What It Does |
|-----------|------|-------------|
| `ProxyManager` | `proxy_manager.py` | Manages mitmproxy DumpMaster lifecycle in background daemon thread |
| `UEMSAddon` | `addon.py` | mitmproxy addon: captures traffic, applies rules, auto-detects CSR for mTLS |
| `TrafficStore` | `traffic_store.py` | Thread-safe flow storage with wait/notify and live JSON file output |
| `ResponseRuleEngine` | `response_rules.py` | Rule engine for response modification/blocking (one-shot + persistent) |
| `CertificateManager` | `certificate_manager.py` | Windows certutil wrapper for CA cert install/remove |

</details>

<details>
<summary><b>4 Python Utilities</b></summary>

| Utility | File | What It Does |
|---------|------|-------------|
| `Logger` | `logger.py` | File logging + GOAT-compatible console output format |
| `PatternMatcher` | `pattern_matcher.py` | Regex matching + 7 validation types (contains, exact, regex, etc.) |
| `VariableContext` | `variable_context.py` | `{{stepId.property}}` substitution engine across commands |
| `ServiceDiscovery` | `service_discovery.py` | Discover UEMS Agent install path & vault from Windows service registry |

</details>

---

### C# — GUI Automation Utils (24 files)

<details>
<summary><b>18 GUI Action Handlers</b> — full Windows desktop automation</summary>

| # | Action | File | What It Does |
|---|--------|------|-------------|
| 1 | `click` | `ClickAction.cs` | Click UI element by AutomationId/Name with Win32 fallback |
| 2 | `rightclick` | `RightClickAction.cs` | Right-click context menu |
| 3 | `doubleclick` | `DoubleClickAction.cs` | Double-click action |
| 4 | `entertext` | `EnterTextAction.cs` | Type text into input fields |
| 5 | `readtext` | `ReadTextAction.cs` | Read text with regex validation support |
| 6 | `readgrid` | `ReadGridAction.cs` | Read DataGrid with cell/column/row validation |
| 7 | `readtooltip` | `ReadToolTipAction.cs` | Extract tooltip content by hover |
| 8 | `readstatusbyrow` | `ReadStatusByRowAction.cs` | Read status from specific grid row |
| 9 | `toggle` | `ToggleAction.cs` | Toggle checkboxes on/off |
| 10 | `select` | `SelectAction.cs` | Select from combo box / dropdown |
| 11 | `screenshot` | `ScreenshotAction.cs` | Capture screenshots (on-demand or on-failure) |
| 12 | `wait` | `WaitAction.cs` | Wait for elements to appear with timeout |
| 13 | `checkappinstalled` | `CheckAppInstalledAction.cs` | Verify app installation via registry |
| 14 | `checkservice` | `CheckServiceAction.cs` | Verify Windows service status |
| 15 | `open` | `AppOpenAction.cs` | Launch applications |
| 16 | `close` | `AppCloseAction.cs` | Close application window by name |
| 17 | `close_all` | `AppCloseAllAction.cs` | Kill all instances of a process |
| 18 | `BaseAction` | `BaseAction.cs` | Abstract base: element finding, window targeting, logging |

</details>

<details>
<summary><b>6 GUI Utilities & Helpers</b></summary>

| Utility | File | What It Does |
|---------|------|-------------|
| `GuiAutomatorBase` | `GuiAutomatorBase.cs` | Core FlaUI/UIA3 engine: app launch, attach, element search, Win32 interop |
| `Win32ElementInfo` | `Win32ElementInfo.cs` | Win32 API fallback for elevated/protected windows (bypasses UIA errors) |
| `ConsoleCapture` | `ConsoleCapture.cs` | Redirect Console.Out for capturing launch output |
| `ControlTypeJsonConverter` | `ControlTypeJsonConverter.cs` | JSON converter for FlaUI ControlType enum |
| `Logger` | `Logger.cs` | Timestamped file-based execution logging |
| `ValidationHelper` | `ValidationHelper.cs` | Grid validation: cell-level, column-wise, key-value pairs |

</details>

---

### C++ — Registry Automation Utils (4 files)

| Component | File | What It Does |
|-----------|------|-------------|
| `RegistryAutomation` | `RegistryAutomation.cpp` | Entry point — JSON input → batch registry operations |
| `RegistryWrapper` | `RegistryWrapper.h/.cpp` | JSON-driven registry operations with structured results & logging |
| `MEUtil::Registry` | `RegistryUtil.h` | Low-level Win32 API: read/write/delete keys, WOW64 32/64-bit selection |

---

### Java — G.O.A.T Framework Utils (131 files)

<details>
<summary><b>25 Operation Handlers</b> — core automation dispatchers</summary>

| # | Handler | File | What It Does |
|---|---------|------|-------------|
| 1 | `ExeInstall` | `ExeInstall.java` | Download and install EXE products with AutoIT UAC handling |
| 2 | `UnInstallProduct` | `UnInstallProduct.java` | Uninstall products via AutoIT scripts |
| 3 | `InstallPPM` | `InstallPPM.java` | Download and install PPM/service pack updates |
| 4 | `RevertPPM` | `RevertPPM.java` | Revert/uninstall a PPM update |
| 5 | `BatFileExecutor` | `BatFileExecutor.java` | Execute BAT files with multi-threading and UAC |
| 6 | `CommandExecutor` | `CommandExecutor.java` | Execute system commands (`run_command`) |
| 7 | `TaskManagerHandler` | `TaskManagerHandler.java` | Process scanning, killing, task management |
| 8 | `ServiceManagementHandler` | `ServiceManagementHandler.java` | Windows service start/stop/restart/status |
| 9 | `MachineOperation` | `MachineOperation.java` | Machine-level operations via command framework |
| 10 | `DataBaseOperationHandler` | `DataBaseOperationHandler.java` | DB queries with encrypted connections |
| 11 | `MSSQLMigration` | `MSSQLMigration.java` | MSSQL database migration with XML config editing |
| 12 | `ApiCaseHandler` | `ApiCaseHandler.java` | Execute and validate API test cases |
| 13 | `FileEditHandler` | `FileEditHandler.java` | Dispatch file editing to format-specific handlers |
| 14 | `FileFolderHandler` | `FileFolderHandler.java` | File/folder CRUD: create, delete, copy, move, permissions |
| 15 | `GuiOperation` | `GuiOperation.java` | GUI automation via AssertJ Swing |
| 16 | `NativeGUIOperationHandler` | `NativeGUIOperationHandler.java` | Bridge to C# Agent_GUI_Utils.exe for native GUI testing |
| 17 | `RegistryOperation` | `RegistryOperation.java` | Bridge to C++ RegistryAutomation.exe |
| 18 | `AgentCommunicationOperation` | `AgentCommunicationOperation.java` | Bridge to Python CommunicationAutomation.exe |
| 19 | `WaitForConditionHandler` | `WaitForConditionHandler.java` | Poll for file/DB/log conditions with configurable interval |
| 20 | `Base64EncodeDecoder` | `Base64EncodeDecoder.java` | Base64 encode/decode on strings and files |
| 21 | `BatchFileExecutor` | `BatchFileExecutor.java` | Legacy batch file executor |
| 22 | `FileReader` | `FileReader.java` | Enhanced file reader for various formats |
| 23 | `FileReaderUtil` | `FileReaderUtil.java` | File/folder presence checks and reads |
| 24 | `MachineOperationHandler` | `MachineOperationHandler.java` | Hostname, IP, specs retrieval |
| 25 | `ServerUtils` | `ServerUtils.java` | Server home discovery and network utilities |

</details>

<details>
<summary><b>15 File Operation Handlers</b> — every file format covered</summary>

| # | Handler | File | Formats & Operations |
|---|---------|------|---------------------|
| 1 | `JSONFileHandler` | `JSONFileHandler.java` | Read, write, update, validate JSON (Jackson) |
| 2 | `XMLFileHandler` | `XMLFileHandler.java` | Read, edit, validate XML (DOM parser) |
| 3 | `CSVFileHandler` | `CSVFileHandler.java` | Read and validate CSV files |
| 4 | `XLSXFileHandler` | `XLSXFileHandler.java` | Read and validate Excel spreadsheets (Apache POI) |
| 5 | `TextFileHandler` | `TextFileHandler.java` | Read, write, search, edit text files with regex |
| 6 | `PDFFileHandler` | `PDFFileHandler.java` | Read and search PDFs (Apache PDFBox) |
| 7 | `ConfigFileHandler` | `ConfigFileHandler.java` | Edit .conf, .ini, .properties files |
| 8 | `ArchiveHandler` | `ArchiveHandler.java` | Compress/extract ZIP and 7z archives |
| 9 | `JarFileHandler` | `JarFileHandler.java` | Extract, list, verify JAR signatures, check manifest |
| 10 | `CertificateFileHandler` | `GetValueFromCertificatefile.java` | Extract values from certificates (certutil + regex) |
| 11 | `FileFolderHandler` | `FileFolderHandler.java` | Create, delete, copy, move, rename, permissions, timestamps |
| 12 | `UnAuthFileDownload` | `UnAuthFileDownload.java` | Download files from URLs without auth |
| 13 | `RoboCopyHandler` | `RoboCopyHandler.java` | Robocopy between directories/machines |
| 14 | `BatFileEditor` | `BatFileEditor.java` | Edit .bat files (find/replace, append, insert) |
| 15 | `TempWorkaroundOperation` | `TempWorkaroundOperation.java` | File age checks, pattern-based temp operations |

</details>

<details>
<summary><b>23 Java Utilities</b> — command execution, HTTP, process management</summary>

| # | Utility | File | What It Does |
|---|---------|------|-------------|
| 1 | `API` | `API.java` | HTTP REST client (GET/POST) with auth & file writing |
| 2 | `CommandExecutor` | `command/CommandExecutor.java` | Core command runner with timeout and output parsing |
| 3 | `CommandRegistry` | `command/CommandRegistry.java` | Registry for command definitions loaded from JSON config |
| 4 | `CommandResult` | `command/CommandResult.java` | Value object: output, exit code, timeout state |
| 5 | `CommandDefinition` | `command/CommandDefinition.java` | Storable command with type, timeout, UAC flag |
| 6 | `CommandOutputProcessor` | `command/CommandOutputProcessor.java` | Interface for processing command output |
| 7 | `CommandType` | `command/CommandType.java` | Enum: CMD or POWERSHELL |
| 8 | `EnhancedUacHandler` | `command/EnhancedUacHandler.java` | Windows UAC prompt handling via AutoIT |
| 9 | `CommonUtill` | `CommonUtill.java` | Path resolution, JAR loading, server home lookup |
| 10 | `DownloadFile` | `DownloadFile.java` | HTTP file download with token-based auth |
| 11 | `ProcessScanner` | `ProcessScanner.java` | Scan running processes, run CMD commands |
| 12 | `LogManager` | `LogManager.java` | Centralized logger with log-type separation |
| 13 | `LockFileUtil` | `LockFileUtil.java` | File-based locking (FileChannel/FileLock) |
| 14 | `JsonResponseValidator` | `JsonResponseValidator.java` | Validate JSON responses (path, value, regex) |
| 15 | `PropertiesUtil` | `PropertiesUtil.java` | Load/save .properties files |
| 16 | `StringUtil` | `StringUtil.java` | String manipulation and regex helpers |
| 17 | `ServerUtils` | `ServerUtils.java` | Server URL building and connectivity checks |
| 18 | `Uninstall` | `Uninstall.java` | Product uninstall via AutoIT with UAC |
| 19 | `GOATCommonConstants` | `GOATCommonConstants.java` | Global constants: paths, exe locations, tool configs |
| 20 | `LoginTestConstants` | `LoginTestConstants.java` | Auth testing constants (cookies, URLs) |
| 21 | `ModelTestUtility` | `ModelTestUtility.java` | LLM model testing via HTTP |
| 22 | `CommandProcessor` | `CommandProcessor.java` | Legacy command execution wrapper |
| 23 | `LoggingConfig` | `LoggingConfig.java` | Logging system configuration |

</details>

<details>
<summary><b>40 REST API Layer Components</b> — Spring Boot API for test orchestration</summary>

| Category | Files | What They Do |
|----------|------:|-------------|
| Controllers | 6 | REST endpoints: test cases, execution, reports, files, system health |
| Services | 4 | Business logic: CRUD, async execution queue, report generation |
| Adapters | 5 | Bridge between API DTOs and core framework objects |
| Models/DTOs | 7 | Request/response models with custom Jackson serialization |
| Config | 6 | CORS, Jackson, Swagger, validation, static resources |
| Error Handling | 3 | Global exception handler with consistent error responses |
| Bridge | 1 | `GoatFrameworkBridge` connects Spring layer to core GOAT engine |
| Swagger | 1 | Optional Swagger/OpenAPI support with reflection-based detection |
| Utilities | 3 | JSON parsing, test status tracking, constants |
| Application | 1 | `GoatApiApplication` — Spring Boot main class |
| **Subtotal** | **37** | |

</details>

<details>
<summary><b>24 Core Framework Components</b> — test engine, analysis, reporting, LLM</summary>

| Component | File | What It Does |
|-----------|------|-------------|
| `TestExecutor` | `TestExecutor.java` | Core engine — iterates operations and manages results |
| `OperationHandlerFactory` | `OperationHandlerFactory.java` | Dispatches 20+ operation types to correct handler |
| `TestCaseReader` | `TestCaseReader.java` | Read test cases from XLSX, CSV, JSON |
| `TestCaseConverter` | `TestCaseConverter.java` | Convert between XLSX/CSV and JSON formats |
| `TestCaseValidator` | `TestCaseValidator.java` | Validate test case structure and required fields |
| `TestCaseProcessor` | `TestCaseProcessor.java` | Process test cases through the LLM pipeline |
| `TestResultManager` | `TestResultManager.java` | Persist and aggregate test results |
| `ResultAnalyzer` | `ResultAnalyzer.java` | Produce statistics: pass/fail/skip counts, success rate |
| `ResultComparator` | `ResultComparator.java` | Compare actual vs expected results |
| `ReportGenerator` | `ReportGenerator.java` | Generate test reports and results files |
| `HtmlReportGenerator` | `HtmlReportGenerator.java` | HTML-formatted test reports |
| `GuiTestBatGenerator` | `GuiTestBatGenerator.java` | Generate .bat files for GUI test execution |
| `LLAMAClient` | `LLAMAClient.java` | Ollama/LLM API client for AI-powered analysis |
| `ResponseHandler` | `ResponseHandler.java` | Parse LLM responses and drive test execution |
| `ResolveOperationParameters` | `ResolveOperationParameters.java` | Variable resolution and parameter substitution |
| `EndToEndTestRunner` | `EndToEndTestRunner.java` | Orchestrate multi-test-case E2E suites |
| `ApiTester` | `ApiTester.java` | Execute and validate REST API test cases |
| `GOATMain` | `GOATMain.java` | Application entry point with PID management |
| `GOATMenu` | `GOATMenu.java` | Interactive console menu |
| `Operation` | `Operation.java` | Operation model with parameters |
| `TestCase` | `TestCase.java` | Test case model with operations list |
| `TestResult` | `TestResult.java` | Standardized result (PASSED/FAILED/SKIPPED/WARNING) |
| `AnalysisResult` | `AnalysisResult.java` | Analysis output model |
| `ApiTestCase` | `ApiTestCase.java` | API test case model |

</details>

---

### Web — API Explorer (4 files)

| Component | File | What It Does |
|-----------|------|-------------|
| Explorer UI | `explorer.html` | Interactive API testing interface with sidebar navigation |
| JavaScript | `api-explorer.js` | Endpoint execution, request building, live response display |
| Styles | `styles.css` | Explorer UI styling |
| Static Index | `index.html` | Static file server landing page |

---

## Repository Structure

```
Automation-Utils/
│
├── Agent_Automation/
│   ├── CommunicationAutomation/     # Python MITM proxy framework
│   │   ├── main.py                  # Entry point (test runner + serve mode)
│   │   ├── core/                    # Test engine, action registry, models
│   │   ├── actions/                 # 21 action handlers (proxy, traffic, validation)
│   │   ├── proxy/                   # mitmproxy integration layer
│   │   ├── utils/                   # Logger, pattern matcher, variable context
│   │   └── tests/                   # Unit tests (pytest, 25+ test modules)
│   │
│   ├── Agent_GUI_Utils/             # C# FlaUI-based GUI automation
│   │   ├── Program.cs               # Entry (test suite / spy / explore / validate)
│   │   ├── Core/                    # TestEngine, ActionRegistry, IAction
│   │   ├── Actions/                 # 18 GUI action handlers
│   │   ├── Utils/                   # GuiAutomatorBase (FlaUI + Win32 API)
│   │   ├── Model/                   # Command, TestResult, VariableContext
│   │   └── Helpers/                 # Validation helpers
│   │
│   └── RegistryAutomation/          # C++ Win32 registry utility
│       ├── RegistryUtil.h           # Low-level registry API (WOW64-aware)
│       ├── RegistryWrapper.h/.cpp   # JSON-driven registry operations
│       └── RegistryAutomation.cpp   # CLI entry point
│
├── source/
│   ├── com/me/                      # G.O.A.T Java test framework
│   │   ├── GOATMain.java            # Application launcher
│   │   ├── OperationHandlerFactory  # 20+ operation dispatchers
│   │   ├── LLAMAClient.java         # Ollama/LLM integration for AI analysis
│   │   ├── testcases/               # Operation handlers (file, service, DB, etc.)
│   │   ├── api/                     # Spring Boot REST API server
│   │   └── util/                    # Shared utilities
│   └── web/
│       └── api-explorer/            # Interactive API testing UI
│
└── docs/                            # 35+ reference guides
```

---

## Components

### 1. CommunicationAutomation — Python MITM Proxy Framework

Intercepts, captures, and validates all HTTPS communication between the UEMS Agent and its server using **mitmproxy**.

```
UEMS Agent  ──►  mitmproxy (reverse proxy)  ──►  Real Server
                      │
              ┌───────┴───────┐
              │  Traffic Store │ ──► Live JSON capture file
              │  Rule Engine   │ ──► Response modification/blocking
              │  CSR Detector  │ ──► Auto mTLS certificate loading
              └───────────────┘
```

**Key capabilities:**
- **Three-layer OS traffic lock** — Hosts file redirect + port proxy + per-exe firewall rules prevent any bypass
- **Live traffic capture** — Real-time JSON file output readable across processes
- **Auto mTLS handling** — Detects CSR servlet responses and auto-loads client certificates from the agent vault
- **Response manipulation** — Inject custom status codes, headers, bodies, or perform search-and-replace on real responses
- **Request blocking/delaying** — Simulate network failures and latency
- **Variable substitution** — Reference results from earlier steps using `{{stepId.property}}` syntax
- **21 built-in actions** — From `start_proxy` to `assert_request_order`

**Run a test:**
```bash
python main.py TestData/general_validation.json
```

**Build standalone EXE:**
```bash
build.bat    # Produces dist/CommunicationAutomation.exe (PyInstaller onefile)
```

---

### 2. Agent GUI Utils — C# Windows GUI Automation

Automates Windows desktop application testing using **FlaUI** (UI Automation 3) with Win32 API interop.

**Modes:**
| Mode | Usage | Purpose |
|------|-------|---------|
| Test Suite | `Agent_GUI_Utils.exe test.json` | Execute GUI test scenarios |
| Spy | `Agent_GUI_Utils.exe spy [pid]` | Inspect UI element tree |
| Explore | `Agent_GUI_Utils.exe explore [pid]` | Interactive element discovery |
| Validate | `Agent_GUI_Utils.exe validate test.json` | Dry-run validation |

**18 GUI Actions:**

| Action | Description |
|--------|-------------|
| `click` / `clickbutton` | Click a UI element by AutomationId, Name, or ControlType |
| `rightclick` | Right-click context menu |
| `doubleclick` | Double-click action |
| `entertext` | Type text into input fields |
| `readtext` | Read text from UI elements |
| `readgrid` | Read DataGrid with cell-level validation |
| `readtooltip` | Extract tooltip content |
| `readstatusbyrow` | Read status from specific grid rows |
| `toggle` | Toggle checkboxes and toggle buttons |
| `select` | Select items from combo boxes / lists |
| `screenshot` | Capture screenshots (on-demand or on-failure) |
| `wait` | Wait for elements or fixed duration |
| `checkappinstalled` | Verify application installation via registry |
| `checkservice` | Verify Windows service status |
| `open` | Launch applications |
| `close` / `close_all` / `closeall` | Close application windows |

**Features:**
- Multi-window support — target actions to specific windows by name, title, or AutomationId
- Grid validation — column-wise, key-value pair, and cell-level validations
- Variable context — pass data between steps using `{{stepId.property}}`
- Auto-screenshot on failure for debugging
- Self-contained single-file publish (x86/x64)

---

### 3. Registry Automation — C++ Windows Registry Operations

Native C++ utility for **WOW64-aware** Windows Registry operations, driven by JSON input.

**Capabilities:**
- Read / Write / Delete registry keys and values
- Support for all value types (REG_SZ, REG_DWORD, REG_BINARY, REG_MULTI_SZ, etc.)
- **WOW64 mode selection** — Target 32-bit, 64-bit, or native registry views explicitly
- Batch operations from JSON definition files
- Detailed error codes and logging

---

### 4. G.O.A.T Server — Java Test Orchestration & API

**G.O.A.T** (Go-to Automation Tester) is the central Java test framework with a Spring Boot REST API.

**20+ Operation Handlers:**

| Category | Operations |
|----------|-----------|
| Installation | `exe_install`, `uninstall`, `ppm_upgrade`, `revert_ppm` |
| File Operations | `file_folder_operation`, `file_folder_modification`, `download_file`, `jar_operation`, `zip_operation` |
| Execution | `run_bat`, `run_command`, `task_manager` |
| Services | `service_actions`, `machine_operation` |
| Database | `db_operation`, `mssql_migration` |
| Security | `certificate_operation`, `registry_operation` |
| Communication | `communication_operation` (invokes CommunicationAutomation) |
| GUI | `native_gui_operation` (invokes Agent GUI Utils) |
| API | `api_case` |
| AI Analysis | LLM-powered test result analysis via Ollama (Llama 3.1 / custom model) |

**REST API endpoints:**
- `GET /api/health` — Health check
- `GET /api/testcases` — List all test cases
- `POST /api/testcases/{id}/execute` — Execute a test case
- `GET /api/reports` — List execution reports
- `GET /api/reports/{id}` — Get report details

---

### 5. API Explorer — Web UI

Interactive browser-based tool for testing G.O.A.T API endpoints. Provides a sidebar with categorized endpoints, request builders, and live response viewing.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        G.O.A.T Server (Java)                     │
│              Test Orchestration · REST API · Reports              │
│                    LLM Analysis (Ollama)                          │
├──────────┬───────────────────┬───────────────┬───────────────────┤
│          │                   │               │                   │
│  ┌───────▼────────┐  ┌──────▼──────┐  ┌─────▼──────┐  ┌────────▼───────┐
│  │ Communication   │  │  Agent GUI  │  │  Registry  │  │  20+ Operation │
│  │ Automation (Py) │  │  Utils (C#) │  │  Auto(C++) │  │  Handlers      │
│  ├─────────────────┤  ├─────────────┤  ├────────────┤  ├────────────────┤
│  │ mitmproxy       │  │ FlaUI       │  │ Win32 API  │  │ File, Service, │
│  │ Traffic Store   │  │ UIA3        │  │ WOW64      │  │ DB, Install,   │
│  │ Rule Engine     │  │ Win32 API   │  │ JSON-driven│  │ Certificate... │
│  │ CSR Auto-load   │  │ Grid/Table  │  │            │  │                │
│  └─────────────────┘  └─────────────┘  └────────────┘  └────────────────┘
│                                                                          │
│                    JSON-Driven Test Definitions                           │
└──────────────────────────────────────────────────────────────────────────┘
```

### Design Patterns (shared across components)

| Pattern | Description |
|---------|-------------|
| **Action Registry** | Map of action names → handler instances (case-insensitive lookup) |
| **BaseAction** | Abstract base with `execute()`, `validate()`, `handle_success/failure()` |
| **Command Model** | Unified data class for all action parameters |
| **TestEngine** | Loads JSON, iterates commands, collects results |
| **Variable Context** | `{{stepId.property}}` substitution across steps |

---

## Quick Start

### Prerequisites
- **Windows 10/11** or Windows Server
- **Python 3.10+** (for CommunicationAutomation)
- **.NET 8 SDK** (for Agent GUI Utils)
- **Java 17+** (for G.O.A.T)
- **Visual Studio 2022** (for Registry Automation C++ build)

### CommunicationAutomation (Python)

```bash
cd Agent_Automation/CommunicationAutomation

# Setup
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt    # mitmproxy>=10.0

# Run a test
python main.py TestData/general_validation.json

# Build standalone EXE
build.bat
```

### Agent GUI Utils (C#)

```bash
cd Agent_Automation/Agent_GUI_Utils

# Build (self-contained single-file)
dotnet build -c Release -p:Platform=x64

# Run
bin\x64\Release\net8.0-windows\win-x64\publish\Agent_GUI_Utils.exe test.json

# Spy mode (inspect UI elements)
Agent_GUI_Utils.exe spy
```

### Registry Automation (C++)

```bash
cd Agent_Automation/RegistryAutomation

# Build with Visual Studio
msbuild RegistryAutomation.sln /p:Configuration=Release

# Run
Release\RegistryAutomation.exe operations.json
```

---

## JSON Test Format

All components use the same JSON-driven pattern:

```json
[
    {
        "id": "STEP_001",
        "action": "start_proxy",
        "listen_port": 8080,
        "mode": "reverse:https://server:8383",
        "ssl_insecure": true,
        "description": "Start reverse proxy"
    },
    {
        "id": "STEP_002",
        "action": "wait_for_request",
        "url_pattern": "agentSlot",
        "method": "GET",
        "timeout": 120,
        "description": "Wait for agent check-in"
    },
    {
        "id": "STEP_003",
        "action": "validate_response",
        "url_pattern": "agentSlot",
        "expected_status": 200,
        "description": "Verify server responded OK"
    }
]
```

**Variable substitution** — reference results from previous steps:
```json
{
    "id": "STEP_004",
    "action": "validate_request",
    "expected_value": "{{STEP_002.data}}",
    "description": "Use captured data from step 2"
}
```

---

## Supported Actions

### Automation Utils — Communication (21 Actions)

| # | Action | Category | Description |
|---|--------|----------|-------------|
| 1 | `start_proxy` | Proxy | Start mitmproxy as background process (reverse/regular/upstream) |
| 2 | `stop_proxy` | Proxy | Stop proxy gracefully |
| 3 | `install_certificate` | Setup | Install mitmproxy CA cert into Windows trust store |
| 4 | `install_cert` | Setup | Install/remove CA cert (combined action) |
| 5 | `remove_certificate` | Setup | Remove CA cert from trust store |
| 6 | `load_client_cert` | Setup | Load agent mTLS certificates from vault |
| 7 | `configure_proxy` | Setup | Enable/disable Windows system proxy via netsh |
| 8 | `traffic_lock` | Traffic | OS-level lock: hosts + port proxy + firewall + DNS flush |
| 9 | `traffic_unlock` | Traffic | Reverse all OS-level redirections |
| 10 | `run_command` | Utility | Execute arbitrary shell commands |
| 11 | `manage_service` | Utility | Windows service start/stop/restart/status |
| 12 | `wait_for_request` | Wait | Block until matching HTTP request arrives |
| 13 | `wait_for_interrupt` | Wait | Block until Ctrl+C (recording mode) |
| 14 | `load_traffic` | Capture | Load previously captured traffic from JSON |
| 15 | `capture_traffic` | Capture | Dump current traffic to JSON file |
| 16 | `clear_captures` | Capture | Reset the traffic buffer |
| 17 | `switch_capture_file` | Capture | Switch live capture to a new file |
| 18 | `validate_request` | Validation | Assert request URL, method, headers, body |
| 19 | `validate_response` | Validation | Assert response status, headers, body |
| 20 | `assert_request_count` | Validation | Verify request count (min/max/exact) |
| 21 | `assert_request_order` | Validation | Verify request sequence ordering |

### GUI Automation (18 Actions)

| Action | Description |
|--------|-------------|
| `click` / `rightclick` / `doubleclick` | Mouse interactions |
| `entertext` | Keyboard input |
| `readtext` / `readgrid` / `readtooltip` / `readstatusbyrow` | Data extraction |
| `toggle` / `select` | State changes |
| `screenshot` | Screen capture |
| `wait` | Timing control |
| `checkappinstalled` / `checkservice` | System verification |
| `open` / `close` / `close_all` | Application lifecycle |

### Validation Types

| Type | Behavior |
|------|----------|
| `contains` | Value contains expected string (default) |
| `exact` | Exact string match |
| `startswith` | Value starts with expected |
| `endswith` | Value ends with expected |
| `regex` | Regular expression match |
| `notempty` | Value exists and is non-empty |
| `isempty` | Value is empty or missing |

---

## Utilities & Helpers

### Python Utils
| Utility | Purpose |
|---------|---------|
| `Logger` | File + console logging with GOAT-compatible output format |
| `PatternMatcher` | Regex matching + multi-type validation engine |
| `VariableContext` | `{{step.prop}}` substitution across commands |
| `ServiceDiscovery` | Discover UEMS Agent install path from Windows service registry |

### C# Utils
| Utility | Purpose |
|---------|---------|
| `GuiAutomatorBase` | FlaUI + Win32 API wrapper for window management |
| `Logger` | Test execution logging |
| `ValidationHelper` | Grid and cell validation logic |
| `ConsoleCapture` | Capture stdout during app launch |

### Java Operation Handlers (20+)
| Category | Handlers |
|----------|----------|
| File I/O | JSON, XML, CSV, XLSX, PDF, Text, Config, Archive (ZIP) |
| Installation | EXE install, Uninstall, PPM upgrade/revert |
| Infrastructure | Service management, Machine operations, Task manager |
| Database | DB operations, MSSQL migration |
| Security | Certificate operations, Registry operations |
| Network | File download, API testing |
| AI | LLM-powered analysis via Ollama (Llama 3.1 / custom GOAT model) |

---

## Documentation

The `docs/` folder contains **35+ reference guides** covering every operation handler:

| Guide | Description |
|-------|-------------|
| [api_documentation.md](docs/api_documentation.md) | REST API reference |
| [gui_operations_reference.md](docs/gui_operations_reference.md) | GUI automation guide |
| [NativeGuiOperation_reference.md](docs/NativeGuiOperation_reference.md) | Native GUI operations |
| [Registry_Operation_Helper.md](docs/Registry_Operation_Helper.md) | Registry operations |
| [service_management_reference.md](docs/service_management_reference.md) | Windows service management |
| [database_operation_handler_reference.md](docs/database_operation_handler_reference.md) | Database operations |
| [json_file_handler_reference.md](docs/json_file_handler_reference.md) | JSON file operations |
| [xml_file_handler_reference.md](docs/xml_file_handler_reference.md) | XML file operations |
| [certificate_file_operation_reference.md](docs/certificate_file_operation_reference.md) | Certificate operations |
| [ollama_model_setup.md](docs/ollama_model_setup.md) | LLM model setup guide |
| [workflow.md](docs/workflow.md) | End-to-end test workflow |
| [GOAT_Installation_And_Upgrade_Guide.md](docs/GOAT_Installation_And_Upgrade_Guide.md) | Installation guide |

> See [`docs/`](docs/) for the full list of references.

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Traffic Interception | mitmproxy | 10.0+ |
| GUI Automation | FlaUI (UIA3) | 4.0 |
| GUI Runtime | .NET | 8.0 |
| Registry Operations | Win32 C++ | MSVC 2022 |
| Test Orchestration | Java + Spring Boot | 17+ / 3.x |
| AI Analysis | Ollama (Llama 3.1) | Local |
| API Explorer | HTML/CSS/JavaScript | — |
| Build (Python) | PyInstaller | 6.11 |
| Build (C#) | dotnet publish | Single-file, self-contained |

---

## Exit Codes

All test runners use consistent exit codes:

| Code | Meaning |
|------|---------|
| `0` | All tests passed |
| `1` | One or more tests failed |
| `5` | Test file not found |
| `99` | Critical unhandled error |

---

## GitHub Repository Workflow

### Branching Strategy

```
main              ← stable, production-ready code (protected branch)
│
├── dev           ← active development integration branch
│   ├── feature/proxy-auto-mtls      ← new features
│   ├── feature/gui-grid-validation
│   ├── bugfix/traffic-lock-cleanup   ← bug fixes
│   └── refactor/action-registry      ← refactors
│
└── release/v1.2  ← release candidates (tagged after QA)
```

| Branch | Purpose | Merges Into |
|--------|---------|-------------|
| `main` | Stable releases only | — |
| `dev` | Integration branch for active work | `main` (via PR) |
| `feature/*` | New features | `dev` (via PR) |
| `bugfix/*` | Bug fixes | `dev` (via PR) |
| `release/v*` | Release candidates | `main` (via PR + tag) |

### Recommended GitHub Settings

1. **Protect `main` branch:**
   - Go to **Settings → Branches → Add rule** for `main`
   - Enable: *Require pull request before merging*
   - Enable: *Require approvals (1+)*
   - Enable: *Do not allow deletions*

2. **Keep repository Private:**
   - Go to **Settings → General → Danger Zone → Change visibility**
   - Set to **Private** — only invited collaborators can view or clone

3. **Disable forking:**
   - Go to **Settings → General**
   - Uncheck **Allow forking** — prevents anyone from copying your repo

### Release Workflow

```bash
# 1. Create a feature branch
git checkout dev
git pull origin dev
git checkout -b feature/my-new-feature

# 2. Work on your changes, commit frequently
git add .
git commit -m "feat: add new validation action"

# 3. Push and create a Pull Request to dev
git push origin feature/my-new-feature
# → Open PR: feature/my-new-feature → dev

# 4. After PR is approved and merged to dev, create release
git checkout dev
git pull origin dev
git checkout -b release/v1.2

# 5. Tag and merge to main
git tag -a v1.2 -m "Release v1.2 — proxy auto-mTLS support"
git push origin release/v1.2 --tags
# → Open PR: release/v1.2 → main
```

### Commit Message Convention

```
feat:     new feature              feat: add traffic_lock action
fix:      bug fix                  fix: proxy not stopping on timeout
refactor: code refactoring         refactor: extract pattern matcher
test:     adding/updating tests    test: add proxy start unit tests
docs:     documentation changes    docs: update action reference table
build:    build system changes     build: update PyInstaller spec
```

### Key Files at Repository Root

```
Automation-Utils/
├── README.md          ← you are here
├── LICENSE            ← proprietary license (All Rights Reserved)
├── .gitignore         ← ignore build outputs, secrets, IDE files
├── Agent_Automation/  ← all automation tools
├── source/            ← G.O.A.T Java framework
└── docs/              ← reference documentation
```

---

## License & Code Protection

> **This repository is proprietary. All rights reserved.**

```
Copyright (c) 2024-2026 Prathosh007. All Rights Reserved.
```

**No permission** is granted to copy, modify, distribute, or use this code without explicit written consent from the author.

### How Your Code Is Protected

| Protection Layer | How |
|-----------------|-----|
| **Proprietary License** | `LICENSE` file explicitly prohibits copying, modification, and distribution |
| **Private Repository** | Set repo to Private in GitHub Settings → only invited users can see it |
| **Forking Disabled** | Uncheck "Allow forking" in Settings → prevents one-click copies |
| **Branch Protection** | Protected `main` branch prevents unauthorized changes |
| **No Open-Source License** | Without MIT/Apache/GPL, no one has legal permission to use the code |

### What This Means

- **Viewing ≠ Permission** — even if someone can see the repo, they cannot legally use, copy, or redistribute the code
- **GitHub ToS** — GitHub's Terms of Service allow others to *view* and *fork* public repos, but forking does NOT grant a license to use the code. Your `LICENSE` file overrides any implied permission
- **If you want maximum protection** — keep the repository **Private**

### For Collaborators

If you want to grant specific people access:
1. Go to **Settings → Collaborators → Add people**
2. Invite by GitHub username
3. They get read/write access but are still bound by the LICENSE terms

---

*Built by [@Prathosh007](https://github.com/Prathosh007)*
