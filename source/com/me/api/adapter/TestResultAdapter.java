package com.me.api.adapter;

import com.me.TestResult;
import com.me.api.model.TestResultDTO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.util.LogManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter for TestResult to avoid direct modification of core classes.
 */
public class TestResultAdapter {
    
    private static final Logger LOGGER = LogManager.getLogger(TestResultAdapter.class.getName(), LogManager.LOG_TYPE.FW);
    private TestResult coreResult;
    
    /**
     * Create a new adapter wrapping a core TestResult
     * 
     * @param coreResult The core TestResult to adapt
     */
    public TestResultAdapter(TestResult coreResult) {
        this.coreResult = coreResult != null ? coreResult : new TestResult();
        
        // Ensure the test case ID is never null
        if (this.coreResult.getTestCaseId() == null) {
            this.coreResult.setTestCaseId("unknown");
        }
    }
    
    /**
     * Create a default adapter with empty result
     */
    public TestResultAdapter() {
        this.coreResult = new TestResult();
        this.coreResult.setTestCaseId("unknown");
    }
    
    /**
     * Convert to a DTO for API responses
     * 
     * @return A TestResultDTO representing this result
     */
    public TestResultDTO toDTO() {
        TestResultDTO dto = new TestResultDTO();
        dto.setTestCaseId(coreResult.getTestCaseId());
        dto.setStatus(coreResult.getStatus());
        dto.setExpectedResult(coreResult.getExpectedResult());
        dto.setActualResult(coreResult.getActualResult());
        dto.setRemarks(coreResult.getRemarks());
        dto.setExecutionTime(coreResult.getExecutionTime());
        return dto;
    }
    
    /**
     * Convert to a JSON string
     * 
     * @return JSON representation of the test result
     */
    public String toJson() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n")
              .append("  \"").append(coreResult.getTestCaseId()).append("\": {\n")
              .append("    \"testcase_id\": \"").append(coreResult.getTestCaseId()).append("\",\n")
              .append("    \"status\": \"").append(coreResult.getStatus() != null ? coreResult.getStatus() : "UNKNOWN").append("\",\n");
            
            if (coreResult.getExpectedResult() != null) {
                sb.append("    \"expected_result\": \"").append(escapeJson(coreResult.getExpectedResult())).append("\",\n");
            } else {
                sb.append("    \"expected_result\": \"\",\n");
            }
            
            if (coreResult.getActualResult() != null) {
                sb.append("    \"actual_result\": \"").append(escapeJson(coreResult.getActualResult())).append("\",\n");
            } else {
                sb.append("    \"actual_result\": \"\",\n");
            }
            
            if (coreResult.getRemarks() != null) {
                sb.append("    \"remarks\": \"").append(escapeJson(coreResult.getRemarks())).append("\",\n");
            } else {
                sb.append("    \"remarks\": \"\",\n");
            }

            if (coreResult.getError() != null) {
                sb.append("    \"error\": \"").append(escapeJson(coreResult.getError())).append("\",\n");
            } else {
                sb.append("    \"error\": \"\",\n");
            }
            
            sb.append("    \"execution_time\": ").append(coreResult.getExecutionTime()).append("\n")
              .append("  }\n")
              .append("}");
            
            return sb.toString();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating JSON from TestResult", e);
            // Return a minimal valid JSON in case of error
            return "{\"" + coreResult.getTestCaseId() + "\": {\"status\": \"ERROR\", \"remarks\": \"Error generating result JSON\"}}";
        }
    }
    
    /**
     * Convert the result to a JsonObject
     * 
     * @return The result as a JsonObject
     */
    public JsonObject toJsonObject() {
        try {
            String jsonStr = toJson();
            return JsonParser.parseString(jsonStr).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error converting result to JsonObject", e);
            
            // Create a minimal valid JSON object in case of error
            JsonObject errorObject = new JsonObject();
            JsonObject resultDetails = new JsonObject();
            resultDetails.addProperty("status", "ERROR");
            resultDetails.addProperty("remarks", "Error generating result JSON: " + e.getMessage());
            errorObject.add(coreResult.getTestCaseId() != null ? coreResult.getTestCaseId() : "unknown", resultDetails);
            
            return errorObject;
        }
    }
    
    /**
     * Get the wrapped core TestResult
     * 
     * @return The core TestResult object
     */
    public TestResult getCoreResult() {
        return coreResult;
    }
    
    /**
     * Escape special characters in JSON strings
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    // Delegate methods to the core result
    public String getTestCaseId() {
        return coreResult.getTestCaseId();
    }
    
    public String getStatus() {
        return coreResult.getStatus();
    }
    
    public boolean isSuccessful() {
        String status = getStatus();
        return status != null && (status.equalsIgnoreCase("PASSED") || status.equalsIgnoreCase("SUCCESS"));
    }
    
    // Additional delegate methods can be added as needed...
}
