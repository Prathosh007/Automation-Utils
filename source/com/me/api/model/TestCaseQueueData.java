package com.me.api.model;

public class TestCaseQueueData {
    String testUniqueId;
    String testCaseJson;
    String testCaseId;
    INITIATED_TYPE initiatedType;

    public enum INITIATED_TYPE{
        EXECUTE_WITH_JSON,
        EXECUTE_WITH_SPECIFIC_TESTCASE
    }

    public String getTestUniqueId() {
        return testUniqueId;
    }

    public void setTestUniqueId(String testUniqueId) {
        this.testUniqueId = testUniqueId;
    }

    public String getTestCaseJson() {
        return testCaseJson;
    }

    public void setTestCaseJson(String testCaseJson) {
        this.testCaseJson = testCaseJson;
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public INITIATED_TYPE getInitiatedType() {
        return initiatedType;
    }

    public void setInitiatedType(INITIATED_TYPE initiatedType) {
        this.initiatedType = initiatedType;
    }
}
