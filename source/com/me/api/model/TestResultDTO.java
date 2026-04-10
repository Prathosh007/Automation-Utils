package com.me.api.model;

/**
 * Data Transfer Object for test results
 */
public class TestResultDTO {
    private String testCaseId;
    private String status;
    private String expectedResult;
    private String actualResult;
    private String remarks;
    private long executionTime;
    
    public String getTestCaseId() {
        return testCaseId;
    }
    
    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
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
}
