package com.me;

import com.me.util.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TestCase {
    private static final Logger LOGGER = LogManager.getLogger(TestCase.class.getName(), LogManager.LOG_TYPE.FW);
    
    private String id;
    private String description;
    private String testcase;
    private String steps;
    private String expectedResult;
    private String filePath;
    private String fileName;
    private String operation;
    private String value;
    private String testResult;
    private String productName;
    private List<Operation> operations;

    public TestCase(String id, String testcase, String steps, String expectedResult) {
        this.id = id;
        this.testcase = testcase;
        this.steps = steps;
        this.expectedResult = expectedResult;
        this.productName = ""; // Initialize with empty string
        this.operations = new ArrayList<>();
    }

    // Add new constructor for 5 parameters
    public TestCase(String id, String productName, String testcase, String steps, String expectedResult) {
        this.id = id;
        this.productName = productName;
        this.testcase = testcase;
        this.steps = steps;
        this.expectedResult = expectedResult;
        this.operations = new ArrayList<>();
        
        // Initialize other fields if needed
        this.filePath = "";
        this.fileName = "";
        this.operation = "";
        this.value = "";
        this.testResult = "";
    }

    public TestCase(String id, String description, String productName, String filePath, String fileName, String operation, String expectedResult, String value, String testResult) {
        this.id = id;
        this.description = description;
        this.productName = productName;
        this.filePath = filePath;
        this.fileName = fileName;
        this.operation = operation;
        this.expectedResult = expectedResult;
        this.value = value;
        this.testResult = testResult;
        this.operations = new ArrayList<>();
        
        // Add the operation to the list
        addOperation(operation, filePath, fileName, value, productName);
    }

    public void addOperation(String operationType, String filePath, String fileName, String value, String productName) {
        Operation operation = new Operation(operationType, filePath, fileName, value, productName);
        this.operations.add(operation);
        LOGGER.info("Added operation: " + operation + " to test case: " + id);
    }
    
    // Add setter methods
    public void setProductName(String productName) {
        this.productName = productName;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
    
    public void setValue(String value) {
        this.value = value;
    }

    // Existing getter methods
    public List<Operation> getOperations() {
        return operations;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }
    public String getTestcase() {
        return testcase;
    }

    public String getSteps() {
        return steps;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getOperation() {
        return operation;
    }

    public String getValue() {
        return value;
    }

    public String getTestResult() {
        return testResult;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setTestResult(String testResult) {
        this.testResult = testResult;
    }

    public String getName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getName'");//NO I18N
    }
}
