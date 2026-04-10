# API Documentation

This document outlines the REST API endpoints for test case execution and status monitoring.

## Test Case APIs

## Test Execution APIs

### Execute Test Case from JSON

Execute a test case using JSON payload.

- **URL**: `/execute`
- **Method**: `POST`
- **Parameters**:
  - `testId` (query, required): Test unique identifier
- **Body**: Raw JSON representing the test case

**Response Codes**:
- `200`: Test case initiated successfully
- `412`: If testId is missing

**Example**:
```
POST /api/execute?testId=test789
{
  "testcase": {
    "steps": [...]
  }
}
```

### Execute Specific Test Case

Execute a specific test case using ID.

- **URL**: `/execute/{id}`
- **Method**: `POST`
- **Parameters**:
  - `id` (path, required): Test case ID
  - `testId` (query, required): Test unique identifier

**Response Codes**:
- `200`: Test case initiated successfully
- `412`: If testId or id is missing

**Example**:
```
POST /api/execute/tc123?testId=test456
```



### Check Test Case Status

Get the status of a test case execution.

- **URL**: `/testcases/status`
- **Method**: `GET`
- **Parameters**:
    - `testId` (query, required): The unique test ID
    - `testcaseId` (query, required): The test case ID

**Response Codes**:
- `200`: Validation completed
- `400`: Invalid JSON
- `500`: Server error

**Example**:
```
GET /api/testcases/status?testId=test123&testcaseId=tc456
```

### Get Note Value

Retrieves a value from GOAT that you saved as a note, using the specified key.

- **URL**: `/testcases/getValue`
- **Method**: `GET`
- **Parameters**:
    - `key` (query, required): Variable key to look up

**Response Codes**:
- `200`: Variable retrieved successfully
- `404`: Variable not found
- `500`: Server error

**Example**:
```
GET /api/testcases/getValue?key=sessionToken
```