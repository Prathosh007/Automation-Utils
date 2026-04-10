# G.O.A.T REST API Endpoints

This document provides a list of available endpoints for the G.O.A.T REST API. All endpoints are relative to the base URL: `http://localhost:9295/api`

## System Endpoints

### 1. Health Check
- **URL:** `/health`
- **Method:** `GET`
- **Description:** Check if the API server is running
- **Example:** `curl http://localhost:9295/api/health`

### 2. System Information
- **URL:** `/info`
- **Method:** `GET`
- **Description:** Get system information including version, Java version, uptime
- **Example:** `curl http://localhost:9295/api/info`

## Test Case Endpoints

### 1. List All Test Cases
- **URL:** `/testcases`
- **Method:** `GET`
- **Description:** Get a list of all available test case IDs
- **Example:** `curl http://localhost:9295/api/testcases`

### 2. Get Test Case by ID
- **URL:** `/testcases/{id}`
- **Method:** `GET`
- **Description:** Get a specific test case by ID
- **Example:** `curl http://localhost:9295/api/testcases/FILE_TEST_01`

### 3. Execute Test Case by ID
- **URL:** `/testcases/{id}/execute`
- **Method:** `POST`
- **Description:** Execute a specific test case by ID
- **Example:** `curl -X POST http://localhost:9295/api/testcases/FILE_TEST_01/execute`

### 4. Validate Test Case
- **URL:** `/testcases/validate`
- **Method:** `POST`
- **Description:** Validate a test case JSON without executing it
- **Content-Type:** `application/json`
- **Example:**
  ```bash
  curl -X POST -H "Content-Type: application/json" \
    -d '{"testcase_id": "TEST_01", "description": "Test example", "operations": [...]}' \
    http://localhost:9295/api/testcases/validate
  ```

## Report Endpoints

### 1. List Reports
- **URL:** `/reports`
- **Method:** `GET`
- **Description:** Get a list of all available test reports
- **Example:** `curl http://localhost:9295/api/reports`

### 2. Get Report by ID
- **URL:** `/reports/{id}`
- **Method:** `GET` 
- **Description:** Get a specific test report by ID
- **Example:** `curl http://localhost:9295/api/reports/report_20250331_123456`

### 3. Download HTML Report
- **URL:** `/reports/{id}/html`
- **Method:** `GET`
- **Description:** Download an HTML report for a specific test run
- **Example:** `curl -o report.html http://localhost:9295/api/reports/report_20250331_123456/html`
