package com.me;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Standardized test result class for storing and reporting test execution results
 */
public class TestResult {
    // Status constants
    public static final String STATUS_PASSED = "PASSED"; //No I18N
    public static final String STATUS_FAILED = "FAILED"; //No I18N
    public static final String STATUS_SKIPPED = "SKIPPED"; //No I18N
    public static final String STATUS_WARNING = "WARNING"; //No I18N
    public static final String STATUS_ERROR = "ERROR"; //No I18N
    
    private String testCaseId;           // ID of the test case
    private String description;          // Description of the test case
    private String operationType;        // Type of operation executed
    private String status;               // Test status (PASSED, FAILED, etc.)
    private String expectedResult;       // What was expected
    private String actualResult;         // What actually happened
    private String remarks;              // Detailed remarks about execution
    private long executionTime;          // Time taken to execute in milliseconds
    private String timestamp;            // When the test was executed
    private Map<String, Object> details; // Additional test-specific details
    private String error;
    
    /**
     * Default constructor
     */
    public TestResult() {
        this.status = STATUS_SKIPPED; // Default status is SKIPPED
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.details = new HashMap<>();
    }
    
    /**
     * Constructor with basic information
     * 
     * @param testCaseId ID of the test case
     * @param operationType Type of operation executed
     * @param status Test status (PASSED, FAILED, etc.)
     */
    public TestResult(String testCaseId, String operationType, String status) {
        this();
        this.testCaseId = testCaseId;
        this.operationType = operationType;
        this.status = status;
    }
    
    // Getters and setters
    
    public String getTestCaseId() {
        return testCaseId;
    }
    
    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }
    
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getOperationType() {
        return operationType;
    }
    
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getExpectedResult() {
        return expectedResult;
    }
    
    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }
    
    public String getActualResult() {
        return actualResult;
    }
    
    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
    }
    
    public String getRemarks() {
        return remarks;
    }
    
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
    
    public long getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * Add a detail key-value pair
     * 
     * @param key Detail key
     * @param value Detail value
     */
    public void addDetail(String key, Object value) {
        this.details.put(key, value);
    }
    
    /**
     * Convert this result to a JsonObject for reporting/storage
     * 
     * @return JsonObject representation of this result
     */
    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        
        // Add all non-null fields to the JSON object
        if (testCaseId != null) {
            jsonObject.addProperty("testcase_id", testCaseId); //No I18N
        }
        if (operationType != null) {
            jsonObject.addProperty("operation_type", operationType); //No I18N
        }
        if (status != null) {
            jsonObject.addProperty("status", status); //No I18N
        }
        if (expectedResult != null) {
            jsonObject.addProperty("expected_result", expectedResult); //No I18N
        }
        if (actualResult != null) {
            jsonObject.addProperty("actual_result", actualResult); //No I18N
        }
        if (description != null) {
            jsonObject.addProperty("description", description); //No I18N
        }
        if (remarks != null) {
            jsonObject.addProperty("remarks", remarks); //No I18N
        } else {
            jsonObject.addProperty("remarks", ""); //No I18N
        }

        if (error != null) {
            jsonObject.addProperty("error", error); //No I18N
        } else {
            jsonObject.addProperty("error", ""); //No I18N
        }
        
        jsonObject.addProperty("execution_time", executionTime); //No I18N
        
        // Add current timestamp if not already set
        if (timestamp == null) {
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        jsonObject.addProperty("timestamp", timestamp); //No I18N
        
        // Add details if any
        if (!details.isEmpty()) {
            jsonObject.add("details", new Gson().toJsonTree(details));//NO I18N
        }
        
        return jsonObject;
    }
    
    /**
     * Convert this result to a JSON string
     * 
     * @param pretty Whether to pretty-print the JSON
     * @return JSON string representation of this result
     */
    public String toJson(boolean pretty) {
        GsonBuilder builder = new GsonBuilder();
        if (pretty) {
            builder.setPrettyPrinting();
        }
        return builder.create().toJson(toJsonObject());
    }
    
    /**
     * Set status as PASSED with execution time
     * 
     * @param executionTime Time taken in milliseconds
     */
    public void markPassed(long executionTime) {
        this.status = STATUS_PASSED;
        this.executionTime = executionTime;
    }
    
    /**
     * Set status as FAILED with execution time and reason
     * 
     * @param executionTime Time taken in milliseconds
     * @param actualResult What actually happened
     * @param remarks Detailed remarks about the failure
     */
    public void markFailed(long executionTime, String actualResult, String remarks) {
        this.status = STATUS_FAILED;
        this.executionTime = executionTime;
        this.actualResult = actualResult;
        this.remarks = remarks;
    }
    
    /**
     * Create a TestResult from an Operation
     * 
     * @param testCaseId Test case ID
     * @param description Test case description
     * @param op The operation that was executed
     * @param success Whether the operation was successful
     * @param executionTime Time taken in milliseconds
     * @return TestResult object
     */
    public static TestResult fromOperation(String testCaseId, String description, Operation op, boolean success, long executionTime) {
        TestResult result = new TestResult();
        
        result.setTestCaseId(testCaseId);
        result.setDescription(description);
        result.setOperationType(op.getOperationType());
        result.setStatus(success ? STATUS_PASSED : STATUS_FAILED);
        result.setExecutionTime(executionTime);
        
        // Copy relevant data from the operation
        result.setRemarks(op.getRemarks());
        result.setActualResult(op.getOutputValue());
        
        // Add operation parameters to details
        for (Map.Entry<String, String> entry : op.getParameters().entrySet()) {
            // Skip internal parameters that start with underscore
            if (!entry.getKey().startsWith("_")) {
                result.addDetail(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }
    
    @Override
    public String toString() {
        return "TestResult{" +//NO I18N
                "testCaseId='" + testCaseId + '\'' +//NO I18N
                ", operationType='" + operationType + '\'' +//NO I18N
                ", status='" + status + '\'' +//NO I18N
                ", executionTime=" + executionTime +//NO I18N
                ", remarks='" + (remarks != null ? remarks.substring(0, Math.min(50, remarks.length())) + "..." : "null") + '\'' +//NO I18N
                ", error='" + (error != null ? error : "null") + '\'' +//NO I18N
                '}';
    }
}
