# G.O.A.T REST API Testing Scripts

This document provides PowerShell and Batch scripts to test various endpoints of the G.O.A.T REST API.

## PowerShell Script

```powershell
# Test G.O.A.T REST API
$baseUrl = "http://localhost:9295/api"

Write-Host "Testing G.O.A.T REST API..." -ForegroundColor Cyan

# 1. Test health endpoint
Write-Host "`n1. Testing health endpoint..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
Write-Host "Health Status: $($health.data.status)" -ForegroundColor Green

# 2. Test info endpoint
Write-Host "`n2. Testing info endpoint..." -ForegroundColor Yellow
$info = Invoke-RestMethod -Uri "$baseUrl/info" -Method Get
Write-Host "G.O.A.T Version: $($info.data.version)" -ForegroundColor Green
Write-Host "Java Version: $($info.data.javaVersion)" -ForegroundColor Green

# 3. Test getting all test case IDs
Write-Host "`n3. Testing testcases endpoint..." -ForegroundColor Yellow
$testcases = Invoke-RestMethod -Uri "$baseUrl/testcases" -Method Get
Write-Host "Found $($testcases.data.Count) test cases:" -ForegroundColor Green
$testcases.data | ForEach-Object { Write-Host "  - $_" }

# If test cases were found, test getting one by ID and executing it
if ($testcases.data.Count -gt 0) {
    $testId = $testcases.data[0]
    
    # 4. Test getting a specific test case
    Write-Host "`n4. Testing get test case by ID ($testId)..." -ForegroundColor Yellow
    $testcase = Invoke-RestMethod -Uri "$baseUrl/testcases/$testId" -Method Get
    Write-Host "Test case description: $($testcase.data.description)" -ForegroundColor Green
    
    # 5. Test executing a test case
    Write-Host "`n5. Testing execute test case by ID ($testId)..." -ForegroundColor Yellow
    $result = Invoke-RestMethod -Uri "$baseUrl/testcases/$testId/execute" -Method Post
    Write-Host "Execution status: $($result.data.status)" -ForegroundColor Green
    Write-Host "Execution time: $($result.data.execution_time) ms" -ForegroundColor Green
}

# 6. Test validating a test case
Write-Host "`n6. Testing validate test case endpoint..." -ForegroundColor Yellow
$testJson = @"
{
  "testcase_id": "API_TEST_01",
  "description": "Test API validation",
  "reuse_installation": true,
  "operations": [
    {
      "operation_type": "check_presence",
      "parameters": {
        "file_path": "D:/git/g.o.a.t/tests/testFiles",
        "filename": "config.properties"
      }
    }
  ],
  "expected_result": "File presence check successful"
}
"@

try {
    $validation = Invoke-RestMethod -Uri "$baseUrl/testcases/validate" -Method Post -Body $testJson -ContentType "application/json"
    Write-Host "Validation result: $($validation.data.valid)" -ForegroundColor Green
    if ($validation.data.warnings.Count -gt 0) {
        Write-Host "Warnings:" -ForegroundColor Yellow
        $validation.data.warnings | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    }
} catch {
    Write-Host "Validation failed: $_" -ForegroundColor Red
}

Write-Host "`nAPI Testing Complete" -ForegroundColor Cyan
```

## Batch Script

```batch
@echo off
echo Testing G.O.A.T REST API...
echo.

set BASE_URL=http://localhost:9295/api

REM 1. Test health endpoint
echo 1. Testing health endpoint...
curl -s %BASE_URL%/health
echo.

REM 2. Test info endpoint
echo 2. Testing info endpoint...
curl -s %BASE_URL%/info
echo.

REM 3. Test getting all test case IDs
echo 3. Testing testcases endpoint...
curl -s %BASE_URL%/testcases
echo.

REM 4. Test getting a specific test case (FILE_TEST_01)
echo 4. Testing get test case by ID (FILE_TEST_01)...
curl -s %BASE_URL%/testcases/FILE_TEST_01
echo.

REM 5. Test executing a test case
echo 5. Testing execute test case by ID (FILE_TEST_01)...
curl -s -X POST %BASE_URL%/testcases/FILE_TEST_01/execute
echo.

REM 6. Test validating a test case
echo 6. Testing validate test case endpoint...
curl -s -X POST -H "Content-Type: application/json" -d "{\"testcase_id\":\"API_TEST_01\",\"description\":\"Test API validation\",\"operations\":[{\"operation_type\":\"check_presence\",\"parameters\":{\"file_path\":\"D:/git/g.o.a.t/tests/testFiles\",\"filename\":\"config.properties\"}}]}" %BASE_URL%/testcases/validate
echo.

echo.
echo API Testing Complete
```
