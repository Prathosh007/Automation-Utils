# G.O.A.T Workflow Documentation

This document explains the complete workflow of the G.O.A.T (Generic Orchestrated Automated Testing) tool, from test case input to test result verification.

## Complete Workflow Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Test Case  │     │    JSON     │     │    LLAMA    │     │   Execute   │     │   Results   │
│    Input    │────►│ Conversion  │────►│  Processing │────►│    Tests    │────►│  & Reports  │
│  (Excel/CSV)│     │             │     │             │     │             │     │             │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                                                               ▲
                                                               │
                                                               ▼
                                                        ┌─────────────┐
                                                        │   Re-run    │
                                                        │   Failed    │
                                                        │    Tests    │
                                                        └─────────────┘
```

## 1. Test Case Input

The workflow begins with test cases defined in a structured format:

- **Input Formats**: Excel (.xlsx) or CSV files
- **Structure**: Test cases contain fields like:
  - `id`: Unique test case identifier
  - `testcase`: Description of what to test
  - `steps`: Steps to perform
  - `expectedResult`: Expected outcome

**Example Excel Format**:
| id    | testcase                      | steps                             | expectedResult                |
|-------|-------------------------------|-----------------------------------|------------------------------|
| TC001 | Install Product               | Run installer, verify files       | Installation successful      |
| TC002 | Verify Configuration Settings | Check config files for parameters | Settings properly configured |

**Files involved**:
- `manual_cases/testcases.xlsx` - Excel test case file
- `manual_cases/testcases.csv` - Alternative CSV format

**Commands**:
- GUI: Option 1 in main menu
- CLI: `goat.bat convert-testcases input.xlsx output.json`
- Batch: `convert_testcases.bat input.xlsx output.json`

## 2. Test Case Conversion

Converting human-readable test cases to structured JSON:

- **Process**: `TestCaseReader` and `TestCaseConverter` classes read the Excel/CSV input and convert to JSON format
- **Output**: Intermediate JSON file containing all test cases
- **Purpose**: Prepare data for LLM processing

**Example Output JSON**:
```json
{
  "TC001": {
    "id": "TC001",
    "testcase": "Install Product",
    "steps": "Run installer, verify files",
    "expectedResult": "Installation successful"
  },
  "TC002": {
    "id": "TC002",
    "testcase": "Verify Configuration Settings",
    "steps": "Check config files for parameters",
    "expectedResult": "Settings properly configured"
  }
}
```

**Files involved**:
- `manual_cases/testcases.json` - Intermediate JSON file

## 3. LLAMA API Processing

Processing test cases with a Large Language Model to extract operations:

- **Process**: `LLAMAClient` sends the intermediate JSON to a local LLAMA API server
- **API**: Uses a structured prompt to guide the LLM in extracting operations
- **Output**: Raw response from LLAMA and extracted operations JSON
- **Purpose**: Extract actionable operations from human-readable test descriptions

**Example Prompt Structure**:
- Instruction to extract operations from test cases
- Input format description
- Required output format with examples
- Clear constraints on valid operation types

**Files involved**:
- `manual_cases/raw_response.json` - Raw response from LLAMA API
- `manual_cases/extracted_response.json` - Extracted operations JSON

**Commands**:
- GUI: Option 2 in main menu
- CLI: `goat.bat test-llama input.json raw_output.json extracted_output.json`

## 4. Operation Extraction

The LLAMA API extracts structured operations for each test case:

- **Process**: LLAMA processes test descriptions and converts them to concrete operations
- **Operations Types**: 
  - `exe_install`: Install executable files
  - `check_presence`: Verify file existence
  - `verify_absence`: Verify file doesn't exist
  - `value_should_be_present`: Check value exists in file
  - `value_should_be_removed`: Verify value is absent from file

**Example Extracted JSON**:
```json
{
  "TC001": {
    "testcase_id": "TC001",
    "product_name": "ManageEngine_Endpoint_Central_Setup",
    "operations": [
      {
        "operation": "exe_install",
        "file_path": "",
        "filename": "",
        "value": "",
        "product_name": "ManageEngine_Endpoint_Central_Setup"
      },
      {
        "operation": "check_presence",
        "file_path": "server_home/conf",
        "filename": "product.conf",
        "value": "",
        "product_name": ""
      }
    ],
    "expected_result": "Installation and configuration should be successful."
  }
}
```

## 5. Test Execution

Executing the operations for each test case:

- **Process**: `ResponseHandler` and `TestExecutor` classes read the extracted operations and execute each one
- **Server Path Resolution**: `ServerUtils` dynamically resolves server paths (e.g., `server_home`)
- **Execution**: Each operation is executed and its results recorded
- **Output**: Test results with pass/fail status and details

**Files involved**:
- `logs/test_results.json` - Results of test execution

**Commands**:
- GUI: Option 3 in main menu
- CLI: `goat.bat test-workflow extracted_output.json results.json`
- Batch: `run_tests_only.bat extracted_output.json`

## 6. Results Generation

Collecting and storing test results:

- **Process**: `ReportGenerator` collects test execution results
- **Storage**: Results are saved to a JSON file with details of each test case
- **Information Captured**:
  - Test ID and description
  - Execution status (PASSED/FAILED)
  - Failed operations details
  - Execution timestamp

**Example Test Results JSON**:
```json
{
  "TC001": {
    "testcase_id": "TC001",
    "description": "Install Product",
    "result": "PASSED",
    "details": "Operation exe_install - SUCCESSFUL\nOperation check_presence - SUCCESSFUL\nTest case TC001: PASSED",
    "timestamp": "2023-09-28 15:23:42"
  },
  "TC002": {
    "testcase_id": "TC002",
    "description": "Verify Configuration Settings",
    "result": "FAILED",
    "details": "Operation check_presence - FAILED\nTest case TC002: FAILED",
    "failed_operations": [
      {
        "operation": "check_presence",
        "file_path": "server_home/conf",
        "file_name": "settings.conf",
        "value": ""
      }
    ],
    "timestamp": "2023-09-28 15:23:45"
  }
}
```

## 7. Report Visualization

Generating human-readable reports from test results:

- **Process**: `ReportGenerator` can create HTML reports from test results JSON
- **Features**:
  - Summary statistics (total tests, passed, failed)
  - Detailed test case information
  - Color-coded status indicators
  - Timestamp and execution details

**Files involved**:
- `logs/test_results.html` - HTML report of test execution

**Commands**:
- GUI: Option 9 in main menu > View test results report
- CLI: `java -cp %CLASSPATH% com.me.ReportGenerator results.json report.html`
- Batch: `generate_report.bat results.json report.html`

## 8. Logging

Detailed logging throughout the process:

- **Process**: `LogManager` records detailed logs of all operations
- **Features**:
  - Configurable logging levels (console vs file)
  - Timestamped log entries
  - Contextual information for debugging
  - Log file rotation for managing size

**Files involved**:
- `logs/goat_yyyyMMdd_HHmmss.log` - Timestamped log files

**Commands**:
- GUI: Option 9 in main menu > View logs
- CLI: `java -cp %CLASSPATH% com.me.util.LogFileViewer logs show-latest`
- View: `view_logs.bat`

## 9. Re-running Failed Tests

Efficient handling of test failures:

- **Process**: `ResultAnalyzer` identifies failed tests from previous runs
- **Extraction**: Failed tests are extracted to a separate JSON file
- **Re-execution**: Only failed tests are re-executed
- **Benefits**: Saves time by not running already passed tests

**Files involved**:
- `manual_cases/failed_testcases.json` - Extracted failed test cases

**Commands**:
- GUI: Option 5 in main menu
- CLI: `goat.bat rerun-failed results.json extracted.json failed.json`
- Batch: `rerun_failed_tests.bat results.json extracted.json failed.json`

## Complete Workflow Execution

Running the entire process from start to finish:

- **Process**: All steps executed in sequence
- **Input**: Excel/CSV test cases
- **Output**: Test results and HTML report

**Commands**:
- GUI: Option 4 in main menu
- CLI: `goat.bat convert-testcases input.xlsx intermediate.json && goat.bat test-llama intermediate.json raw.json extracted.json && goat.bat test-workflow extracted.json results.json`
- Batch: `run_complete_workflow.bat input.xlsx`

## Conclusion

The G.O.A.T tool provides a comprehensive, automated workflow for testing, from test case definition to execution and reporting. Its modular design allows for running the complete workflow or individual components as needed, with both GUI and CLI interfaces for flexibility.
