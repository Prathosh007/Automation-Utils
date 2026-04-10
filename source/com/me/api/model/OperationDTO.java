package com.me.api.model;

import java.util.Map;
import java.util.HashMap;

/**
 * Data Transfer Object for operations within test cases
 */
public class OperationDTO {
    private String operationType;
    private Map<String, Object> parameters = new HashMap<>();
    
    public String getOperationType() {
        return operationType;
    }
    
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}
