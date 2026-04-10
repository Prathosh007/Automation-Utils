package com.me;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.me.testcases.FileEditHandler;
import com.me.testcases.ServiceManagementHandler;
import com.me.testcases.TaskManagerHandler;
import com.me.util.LogManager;

/**
 * Represents an operation to be executed in a test sequence
 */
public class Operation {
    private static final Logger LOGGER = LogManager.getLogger(Operation.class, LogManager.LOG_TYPE.FW);
    
    private String operationType;
    private String filePath;
    private String fileName;
    private String value;
    private String productName;
    private Map<String, String> parameters;
    private String outputValue;  // Used to store output from operations
    private String remarks;      // Used to store detailed execution remarks
    private long executionStartTime;
    private long executionEndTime;
    
    /**
     * Default constructor needed for some frameworks
     */
    public Operation() {
        this.parameters = new HashMap<>();
    }
    
    /**
     * Constructor for base operation
     * 
     * @param operationType Type of operation
     */
    public Operation(String operationType) {
        this.operationType = operationType;
        this.parameters = new HashMap<>();
    }
    
    /**
     * Constructor with operation type and parameters
     * 
     * @param operationType Type of operation
     * @param parameters Map of parameters for the operation
     */
    public Operation(String operationType, Map<String, String> parameters) {
        this.operationType = operationType;
        this.parameters = parameters != null ? parameters : new HashMap<>();
    }

    /**
     * Get all parameter names
     *
     * @return Set of parameter names
     */
        public java.util.Set<String> getParameterNames() {
            return parameters != null ? parameters.keySet() : Collections.emptySet();
        }
    /**
     * Constructor with all individual fields
     * 
     * @param operationType Type of operation
     * @param filePath File path
     * @param fileName File name
     * @param value Value
     * @param productName Product name
     */
    public Operation(String operationType, String filePath, String fileName, String value, String productName) {
        this.operationType = operationType;
        this.filePath = filePath;
        this.fileName = fileName;
        this.value = value;
        this.productName = productName;
        this.parameters = new HashMap<>();
    }
    
    /**
     * Execute the operation using the appropriate handler
     * 
     * @return true if the operation executed successfully, false otherwise
     */
    public boolean execute() {
        LOGGER.info("Executing operation: " + operationType); //No I18N
        
        // Use OperationHandlerFactory to execute the operation
        boolean success = OperationHandlerFactory.executeOperation(this);
        
        if (success) {
            LOGGER.info("Operation executed successfully: " + operationType); //No I18N
        } else {
            LOGGER.warning("Operation execution failed: " + operationType); //No I18N
        }
        
        return success;
    }
    
    /**
     * Get a parameter value
     * 
     * @param paramName The name of the parameter to get
     * @return The parameter value as a string, or null if not found
     */
    public String getParameter(String paramName) {
        if (parameters == null || !parameters.containsKey(paramName)) {
            return null;
        }
        
        Object value = parameters.get(paramName);
        
        // Handle different types of parameter values
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return (String)value;
        } else {
            // Convert non-String values (like Double, Boolean, etc.) to String
            return String.valueOf(value);
        }
    }
    
    /**
     * Get a parameter value as an Object
     * 
     * @param paramName The name of the parameter to get
     * @return The raw parameter value, or null if not found
     */
    public Object getParameterObject(String paramName) {
        if (parameters == null || !parameters.containsKey(paramName)) {
            return null;
        }
        
        return parameters.get(paramName);
    }
    
    /**
     * Set a parameter value
     * 
     * @param key Parameter key
     * @param value Parameter value
     */
    public void setParameter(String key, String value) {
        parameters.put(key, value);
    }
    
    /**
     * Check if a parameter exists
     * 
     * @param paramName The parameter name to check
     * @return true if the parameter exists, false otherwise
     */
    public boolean hasParameter(String paramName) {
        return parameters != null && parameters.containsKey(paramName);
    }
    
    /**
     * Get the operation type
     * 
     * @return Operation type
     */
    public String getOperationType() {
        return operationType;
    }
    
    /**
     * Set the operation type
     * 
     * @param operationType Operation type
     */
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    
    /**
     * Get the file path
     * 
     * @return File path
     */
    public String getFilePath() {
        return filePath;
    }
    
    /**
     * Set the file path
     * 
     * @param filePath File path
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    /**
     * Get the file name
     * 
     * @return File name
     */
    public String getFileName() {
        return fileName;
    }
    
    /**
     * Set the file name
     * 
     * @param fileName File name
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    /**
     * Get the value
     * 
     * @return Value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Set the value
     * 
     * @param value Value
     */
    public void setValue(String value) {
        this.value = value;
    }
    
    /**
     * Get the product name
     * 
     * @return Product name
     */
    public String getProductName() {
        return productName;
    }
    
    /**
     * Set the product name
     * 
     * @param productName Product name
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    /**
     * Get all parameters
     * 
     * @return Map of parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }
    
    /**
     * Set all parameters
     * 
     * @param parameters Map of parameters
     */
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
    
    /**
     * Get the output value from operation execution
     * 
     * @return Output value
     */
    public String getOutputValue() {
        return outputValue;
    }
    
    /**
     * Set the output value from operation execution
     * 
     * @param outputValue Output value
     */
    public void setOutputValue(String outputValue) {
        this.outputValue = outputValue;
    }
    
    /**
     * Get detailed remarks about the operation execution
     * 
     * @return Remarks string
     */
    public String getRemarks() {
        return remarks != null ? remarks : getParameter("remarks");//No I18N
    }
    
    /**
     * Set detailed remarks about the operation execution
     * 
     * @param remarks Remarks string
     */
    public void setRemarks(String remarks) {
        this.remarks = remarks; 
    }
    
    /**
     * Get result details suitable for test reporting
     * 
     * @return Result details as a string
     */
    public String getResultDetails() {
        StringBuilder details = new StringBuilder();
        
        details.append("Operation type: ").append(operationType).append("\n");//No I18N
        
        // Add operation-specific details
        switch (operationType.toLowerCase()) {
            case "task_manager"://No I18N
                String action = getParameter("action");//No I18N
                String processName = getParameter("process_name");//No I18N
                String processPath = getParameter("process_path");//No I18N
                String expectation = getParameter("expect");//No I18N
                
                details.append("Action: ").append(action).append("\n");//No I18N
                details.append("Process: ").append(processName).append("\n");//No I18N
                details.append("Path: ").append(processPath).append("\n");//No I18N
                
                if (expectation != null && !expectation.isEmpty()) {
                    details.append("Expected: ").append(expectation).append("\n");//No I18N
                }
                
                // Add remarks if available
                String taskRemarks = getRemarks();
                if (taskRemarks != null && !taskRemarks.isEmpty()) {
                    details.append("\nExecution details:\n").append(taskRemarks);//No I18N
                }
                break;
                
            // Add cases for other operation types as needed
            default:
                // For other operations, just show parameters
                details.append("Parameters:\n");//No I18N
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    details.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
        }
        
        return details.toString();
    }
    
    /**
     * Start execution timing
     */
    public void startExecution() {
        this.executionStartTime = System.currentTimeMillis();
    }

    /**
     * End execution timing and set result
     * 
     * @param success Whether the operation was successful
     */
    public void endExecution(boolean success) {
        this.executionEndTime = System.currentTimeMillis();
    }

    /**
     * Get execution time in milliseconds
     */
    public long getExecutionTime() {
        return this.executionEndTime - this.executionStartTime;
    }
    
    @Override
    public String toString() {
        return "Operation [type=" + operationType + ", parameters=" + parameters + "]";//No I18N
    }

    // Existing fields
    private Map<String, String> noteParams;

    // Getter/setter for note parameters
    public void addNoteParam(String paramName, String extractionPattern) {
        if (noteParams == null) {
            noteParams = new HashMap<>();
        }
        noteParams.put(paramName, extractionPattern);
    }

    public Map<String, String> getNoteParams() {
        return noteParams != null ? noteParams : Collections.emptyMap();
    }

    public boolean hasNote() {
        // Check for note as a regular parameter
        if (hasParameter("note")) {
            return true;
        }

        // Check in the dedicated noteParams collection
        return noteParams != null && !noteParams.isEmpty();
    }

    /**
     * Get the note value
     *
     * @return The note value, or null if no note exists
     */
    public String getNote() {
        if (hasParameter("note")) {
            return getParameter("note");
        }

        if (noteParams != null && !noteParams.isEmpty()) {
            // Return the first note value if multiple exist
            return noteParams.values().iterator().next();
        }

        return null;
    }

        private String type;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

}
