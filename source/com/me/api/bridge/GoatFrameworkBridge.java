package com.me.api.bridge;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// Add imports for the adapter classes
import com.me.api.adapter.TestExecutorAdapter;
import com.me.api.adapter.TestResultAdapter;
import com.me.api.adapter.TestValidatorAdapter;
import com.me.api.util.TestCaseJsonUtil;

/**
 * Bridge between Spring Boot API and G.O.A.T Framework
 */
@Component
public class GoatFrameworkBridge {
    private static final Logger logger = LoggerFactory.getLogger(GoatFrameworkBridge.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // Initialize the index when the bridge is created
    public GoatFrameworkBridge() {
        // Build the test case index on startup
        TestCaseJsonUtil.buildTestCaseIndex();
        logger.info("Test case index built successfully");
    }
    
    /**
     * Get all available test case IDs
     * 
     * @return List of test case IDs
     */
    public List<String> getTestCaseIds() {
        // Use the optimized utility to get all test case IDs without loading content
        Map<String, String> testCaseMap = TestCaseJsonUtil.getAllTestCaseIds();
        return new ArrayList<>(testCaseMap.keySet());
    }
    
    /**
     * Get test case details by ID
     * 
     * @param testCaseId The test case ID
     * @return Test case as JSON string or null if not found
     */
    public String getTestCase(String testCaseId) {
        // Use the optimized utility to get a test case by ID
        Optional<JsonObject> testCase = TestCaseJsonUtil.getTestCase(testCaseId);
        
        if (testCase.isPresent()) {
            return testCase.get().toString();
        } else {
            logger.error("Test case not found: " + testCaseId);
            return null;
        }
    }
    
    /**
     * Execute a test case
     * 
     * @param testCaseJson JSON string representing the test case
     * @return Execution results as JSON string
     */
    public String executeTestCase(String testCaseJson) {
        try {
            logger.info("Executing test case: " + (testCaseJson != null ? testCaseJson : "null"));
            
            if (testCaseJson == null || testCaseJson.trim().isEmpty()) {
                logger.error("Test case JSON is null or empty");
                return "{\"status\": \"error\", \"message\": \"Test case JSON is null or empty\"}";
            }
            
            // Use the executor adapter instead of direct execution
            TestExecutorAdapter executorAdapter = new TestExecutorAdapter();
            logger.info("Created TestExecutorAdapter instance");
            
            try {
//                String decodedTestCaseJson = URLDecoder.decode(testCaseJson,"UTF-8");
//                logger.info("Decoded test case JSON: " + decodedTestCaseJson);
                TestResultAdapter resultAdapter = executorAdapter.executeFromJson(testCaseJson);
                logger.info("Test execution completed successfully");
                
                // Return the JSON representation of the result
                String resultJson = resultAdapter.toJson();
                logger.info("Test result JSON: " + resultJson);
                return resultJson;
            } catch (Exception e) {
                logger.error("Error during test execution", e);
                
                // Parse the test ID for a properly formatted error response
                String testId = "unknown";
                try {
                    JsonObject jsonObj = JsonParser.parseString(testCaseJson).getAsJsonObject();
                    if (jsonObj.has("testcase_id")) {
                        testId = jsonObj.get("testcase_id").getAsString();
                    }
                } catch (Exception ex) {
                    // Ignore parsing exceptions
                }
                
                // Create a properly formatted error result
                JsonObject errorResult = new JsonObject();
                JsonObject details = new JsonObject();
                details.addProperty("testcase_id", testId);
                details.addProperty("status", "ERROR");
                details.addProperty("remarks", "Error executing test: " + e.getMessage());
                errorResult.add(testId, details);
                
                return errorResult.toString();
            }
        } catch (Exception e) {
            logger.error("Error executing test case", e);
            
            // Provide detailed error information in the response
            StringBuilder errorDetail = new StringBuilder();
            errorDetail.append(e.getMessage());
            
            Throwable cause = e.getCause();
            if (cause != null) {
                errorDetail.append(" - Caused by: ").append(cause.getMessage());
            }
            
            // Add stack trace information for detailed debugging
            errorDetail.append("\nStack trace: ");
            for (StackTraceElement element : e.getStackTrace()) {
                if (element.getClassName().startsWith("com.me")) {
                    errorDetail.append("\n  at ").append(element.toString());
                }
            }
            
            // Create a valid JSON error response
            JsonObject errorJson = new JsonObject();
            JsonObject errorDetails = new JsonObject();
            errorDetails.addProperty("testcase_id", "error");
            errorDetails.addProperty("status", "ERROR");
            errorDetails.addProperty("remarks", "Framework error: " + errorDetail.toString().replace("\"", "'").replace("\n", "\\n"));
            errorJson.add("error", errorDetails);
            
            return errorJson.toString();
        }
    }
    
    /**
     * Validate a test case
     * 
     * @param testCaseJson JSON string representing the test case
     * @return Validation results
     */
    public Map<String, Object> validateTestCase(String testCaseJson) {
        // Use the validator adapter
        TestValidatorAdapter validatorAdapter = new TestValidatorAdapter();
        return validatorAdapter.validate(testCaseJson);
    }
    
    /**
     * Reload the test case index
     * Use this after adding new test cases or modifying existing ones
     */
    public void reloadTestCaseIndex() {
        TestCaseJsonUtil.reloadIndex();
        logger.info("Test case index reloaded");
    }
}
