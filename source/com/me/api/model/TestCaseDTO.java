package com.me.api.model;

import java.util.List;
import java.util.ArrayList;

/**
 * Data Transfer Object for test cases
 */
public class TestCaseDTO {
    private String id;
    private String description;
    private String productName;
    private boolean reuseInstallation;
    private List<OperationDTO> operations = new ArrayList<>();
    private String expectedResult;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public boolean isReuseInstallation() {
        return reuseInstallation;
    }
    
    public void setReuseInstallation(boolean reuseInstallation) {
        this.reuseInstallation = reuseInstallation;
    }
    
    public List<OperationDTO> getOperations() {
        return operations;
    }
    
    public void setOperations(List<OperationDTO> operations) {
        this.operations = operations;
    }
    
    public String getExpectedResult() {
        return expectedResult;
    }
    
    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }
}
