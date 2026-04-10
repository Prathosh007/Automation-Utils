package com.me.api.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.TestCase;
import com.me.TestExecutor;
import com.me.TestResult;
import com.me.Operation;
import com.me.TestCaseReader;
import com.me.api.model.TestResultDTO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.me.api.adapter.TestCaseAdapter;
import com.me.util.LogManager;

/**
 * Adapter for TestExecutor to avoid direct modification of core classes.
 * Handles execution of test cases through the API.
 */
public class TestExecutorAdapter {
    
    private static final Logger LOGGER = LogManager.getLogger(TestExecutorAdapter.class.getName(), LogManager.LOG_TYPE.FW);
    private TestExecutor coreExecutor;
    
    /**
     * Create a new adapter with a fresh executor instance
     */
    public TestExecutorAdapter() {
        this.coreExecutor = new TestExecutor();
    }
    
    /**
     * Execute a test case from a JSON string
     * 
     * @param testCaseJson JSON string representing the test case
     * @return A result adapter wrapping the execution result
     * @throws Exception if execution fails
     */
    public TestResultAdapter executeFromJson(String testCaseJson) throws Exception {
        LOGGER.log(Level.INFO, "Executing test case from JSON: {0}", testCaseJson);
        
        // Create temporary file for test case
        Path tempFile = Files.createTempFile("api_testcase_", ".json");
        Path resultsFile = Files.createTempFile("api_results_", ".json");
        
        LOGGER.log(Level.INFO, "Created temporary files: testCase={0}, results={1}", new Object[]{tempFile, resultsFile});
        
        try {
            // Write the JSON to a temporary file
            JsonElement jsonElement;
            try {
                if (testCaseJson == null || testCaseJson.trim().isEmpty()) {
                    throw new IllegalArgumentException("Test case JSON is null or empty");
                }
                
                LOGGER.log(Level.INFO, "Parsing JSON string");
                jsonElement = JsonParser.parseString(testCaseJson);
                if (!jsonElement.isJsonObject()) {
                    throw new IllegalArgumentException("Invalid JSON: Expected a JSON object but got " + 
                        (jsonElement.isJsonPrimitive() ? "a primitive value" : 
                        (jsonElement.isJsonArray() ? "an array" : "null")));
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error parsing JSON", e);
                throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
            }
            
            JsonObject testCaseObj = jsonElement.getAsJsonObject();
            LOGGER.log(Level.INFO, "Successfully parsed JSON object with keys: {0}", testCaseObj.keySet());
            
            // Write the JSON to a temporary file - wrap in a container object if not already
            String fullJson;
            if (testCaseObj.has("testcase_id") || testCaseObj.has("test_id")) {
                // This appears to be a properly formatted test case - wrap it in a container
                String testId = testCaseObj.has("testcase_id") ? 
                    testCaseObj.get("testcase_id").getAsString() : 
                    testCaseObj.get("test_id").getAsString();
                    
                LOGGER.log(Level.INFO, "Found testcase_id/test_id: {0}", testId);
                fullJson = "{\"" + testId + "\": " + testCaseJson + "}";
            } else {
                // Check if this JSON is already wrapped in a container
                boolean alreadyContained = false;
                for (Map.Entry<String, JsonElement> entry : testCaseObj.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        JsonObject innerObj = entry.getValue().getAsJsonObject();
                        if (innerObj != null && (innerObj.has("testcase_id") || innerObj.has("operations"))) {
                            alreadyContained = true;
                            LOGGER.log(Level.INFO, "JSON already contains test case structure under key: {0}", entry.getKey());
                            break;
                        }
                    }
                }
                
                if (alreadyContained) {
                    fullJson = testCaseJson;
                } else {
                    // No identifiable structure, use generic container
                    LOGGER.log(Level.INFO, "No test case structure found, wrapping with generic container");
                    fullJson = "{\"test\": " + testCaseJson + "}";
                }
            }
            
            LOGGER.log(Level.INFO, "Writing JSON to file: {0}", tempFile);
            Files.write(tempFile, fullJson.getBytes());
            
            // Try different approaches to execute the test case
            boolean success = false;
            
            // Approach 1: Try to find executeFromFile method using reflection
            try {
                LOGGER.log(Level.INFO, "Trying executeFromFile via reflection");
                Method executeFromFileMethod = TestExecutor.class.getMethod("executeFromFile", String.class, String.class);
                success = (boolean) executeFromFileMethod.invoke(coreExecutor, tempFile.toString(), resultsFile.toString());
                LOGGER.log(Level.INFO, "executeFromFile returned: {0}", success);
            } catch (NoSuchMethodException e) {
                LOGGER.log(Level.INFO, "executeFromFile method not found, trying alternative approach", e);
                
                // Approach 2: Parse the JSON, create TestCase object, and use whatever execution method is available
                JsonObject jsonObj = JsonParser.parseString(fullJson).getAsJsonObject();
                
                // Get first entry from the JSON object
                if (jsonObj.size() == 0) {
                    throw new IllegalArgumentException("Empty JSON object");
                }
                
                String firstKey = jsonObj.keySet().iterator().next();
                LOGGER.log(Level.INFO, "Using first key from JSON: {0}", firstKey);
                
                JsonElement testCaseElement = jsonObj.get(firstKey);
                if (testCaseElement == null || !testCaseElement.isJsonObject()) {
                    LOGGER.log(Level.SEVERE, "Invalid test case structure: element under key {0} is not an object", firstKey);
                    throw new IllegalArgumentException("Invalid test case structure");
                }
                
                JsonObject testCaseObjInner = testCaseElement.getAsJsonObject();
                
                // Get the test case ID
                String testCaseId = firstKey;
                if (testCaseObjInner.has("testcase_id")) {
                    testCaseId = testCaseObjInner.get("testcase_id").getAsString();
                    LOGGER.log(Level.INFO, "Using testcase_id from inner object: {0}", testCaseId);
                }
                
                // Find a suitable method to execute test case
                TestResult result = null;
                
                try {
                    // Try to create a TestCase using available methods
                    LOGGER.log(Level.INFO, "Creating TestCase from JSON");
                    TestCase testCase = createTestCaseFromJson(testCaseObjInner, testCaseId);
                    
                    if (testCase == null) {
                        throw new NullPointerException("Failed to create TestCase object from JSON");
                    }
                    
                    LOGGER.log(Level.INFO, "Created test case with ID: {0}", testCase.getId());
                    
                    // Try to find execute(TestCase) method
                    try {
                        LOGGER.log(Level.INFO, "Trying execute(TestCase) method");
                        Method executeMethod = TestExecutor.class.getMethod("execute", TestCase.class);
                        result = (TestResult) executeMethod.invoke(coreExecutor, testCase);
                        success = (result != null);
                        LOGGER.log(Level.INFO, "execute(TestCase) returned result: {0}", (result != null));
                    } catch (NoSuchMethodException ex) {
                        // Try more generic execute() methods that might be available
                        try {
                            LOGGER.log(Level.INFO, "Trying run(String) method");
                            Method runMethod = TestExecutor.class.getMethod("run", String.class);
                            result = (TestResult) runMethod.invoke(coreExecutor, testCaseId);
                            success = (result != null);
                            LOGGER.log(Level.INFO, "run(String) returned result: {0}", (result != null));
                        } catch (NoSuchMethodException ex2) {
                            // Last resort: try to execute manually
                            LOGGER.log(Level.INFO, "Trying manual test execution");
                            result = executeTestCaseManually(testCase);
                            success = (result != null);
                            LOGGER.log(Level.INFO, "Manual execution returned result: {0}", (result != null));
                        }
                    }
                    
                    // If we got a result, write it to the result file
                    if (result != null) {
                        // Write result to file
                        JsonObject resultJson = new JsonObject();
                        JsonObject testResultJson = new JsonObject();
                        testResultJson.addProperty("testcase_id", result.getTestCaseId());
                        testResultJson.addProperty("status", result.getStatus());
                        testResultJson.addProperty("expected_result", result.getExpectedResult());
                        testResultJson.addProperty("actual_result", result.getActualResult());
                        testResultJson.addProperty("remarks", result.getRemarks());
                        testResultJson.addProperty("error", result.getError());
                        testResultJson.addProperty("execution_time", result.getExecutionTime());
                        
                        resultJson.add(testCaseId, testResultJson);
                        
                        String resultStr = new GsonBuilder().setPrettyPrinting().create().toJson(resultJson);
                        LOGGER.log(Level.INFO, "Writing result to file: {0}", resultStr);
                        
                        // Write to the result file
                        Files.write(resultsFile, resultStr.getBytes());
                    } else {
                        LOGGER.log(Level.WARNING, "Result object is null after execution");
                    }
                } catch (Exception ex) {
                    // If all approaches fail, throw the exception
                    LOGGER.log(Level.SEVERE, "Failed to execute test case", ex);
                    throw new RuntimeException("Failed to execute test case: " + ex.getMessage(), ex);
                }
            }
            
            if (!success && !Files.exists(resultsFile)) {
                LOGGER.log(Level.SEVERE, "Test execution failed - no results generated");
                throw new RuntimeException("Test execution failed - no results generated");
            }
            
            // Read the results
            String resultJson = new String(Files.readAllBytes(resultsFile));
            LOGGER.log(Level.INFO, "Read result JSON: {0}", resultJson);
            TestResult coreResult = parseResultJson(resultJson);
            
            return new TestResultAdapter(coreResult);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception during test execution", e);
            throw e;
        } finally {
            // Clean up temporary files
            try {
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(resultsFile);
                LOGGER.log(Level.INFO, "Cleaned up temporary files");
            } catch (IOException e) {
                // Just log, don't throw
                LOGGER.log(Level.WARNING, "Failed to delete temporary files", e);
            }
        }
    }
    
    /**
     * Execute a test case using the adapter
     * 
     * @param testCase The test case adapter to execute
     * @return A result adapter with the execution results
     */
    public TestResultAdapter execute(TestCaseAdapter testCase) {
        try {
            // Try to use reflection to find the appropriate execute method
            TestResult result = null;
            
            // Try to find execute(TestCase) method
            try {
                Method executeMethod = TestExecutor.class.getMethod("execute", TestCase.class);
                result = (TestResult) executeMethod.invoke(coreExecutor, testCase.getCoreTestCase());
            } catch (NoSuchMethodException e) {
                // Try other execution methods that might be available
                try {
                    Method runMethod = TestExecutor.class.getMethod("run", String.class);
                    result = (TestResult) runMethod.invoke(coreExecutor, testCase.getId());
                } catch (NoSuchMethodException e2) {
                    // Last resort: execute manually
                    result = executeTestCaseManually(testCase.getCoreTestCase());
                }
            }
            
            return new TestResultAdapter(result);
        } catch (Exception e) {
            // Create an error result
            TestResult errorResult = new TestResult();
            errorResult.setTestCaseId(testCase.getId());
            errorResult.setStatus("ERROR");
            errorResult.setRemarks("Execution failed: " + e.getMessage());
            
            return new TestResultAdapter(errorResult);
        }
    }
    
    /**
     * Create TestCase from JSON using available methods
     */
    private TestCase createTestCaseFromJson(JsonObject testCaseJson, String testCaseId) throws Exception {
        // Try to use TestCaseReader if available
        try {
            Class<?> readerClass = Class.forName("com.me.TestCaseReader");
            Method readMethod = readerClass.getMethod("readFromJson", JsonObject.class, String.class);
            return (TestCase) readMethod.invoke(null, testCaseJson, testCaseId);
        } catch (Exception e) {
            // If TestCaseReader doesn't have the method, try to create TestCase manually
            // Import TestCaseAdapter
            
            // Create test case via adapter
            TestCaseAdapter adapter = new TestCaseAdapter();
            adapter.setId(testCaseId);
            TestCase testCase = adapter.getCoreTestCase();
            
            // Use reflection to set properties
            if (testCaseJson.has("description")) {
                setProperty(testCase, "setDescription", testCaseJson.get("description").getAsString());
            }
            
            if (testCaseJson.has("product_name")) {
                setProperty(testCase, "setProductName", testCaseJson.get("product_name").getAsString());
            }
            
//            if (testCaseJson.has("reuse_installation")) {
//                setProperty(testCase, "setReuseInstallation", testCaseJson.get("reuse_installation").getAsBoolean());
//            }
            
            if (testCaseJson.has("expected_result")) {
                setProperty(testCase, "setExpectedResult", testCaseJson.get("expected_result").getAsString());
            }
            
            // Handle operations
            if (testCaseJson.has("operations") && testCaseJson.get("operations").isJsonArray()) {
                for (JsonElement opElement : testCaseJson.getAsJsonArray("operations")) {
                    JsonObject opJson = opElement.getAsJsonObject();
                    String opType = opJson.get("operation_type").getAsString();
                    Operation operation = new Operation(opType);
                    
                    // Add parameters
                    if (opJson.has("parameters") && opJson.get("parameters").isJsonObject()) {
                        JsonObject paramsJson = opJson.get("parameters").getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : paramsJson.entrySet()) {
                            String value = entry.getValue().isJsonPrimitive() ? 
                                entry.getValue().getAsString() : entry.getValue().toString();
                            operation.setParameter(entry.getKey(), value);
                        }
                    }
                    
                    // Add operation to test case
                    Method addOpMethod = findAddOperationMethod(testCase);
                    if (addOpMethod != null) {
                        if (addOpMethod.getParameterCount() == 1 && 
                            addOpMethod.getParameterTypes()[0] == Operation.class) {
                            addOpMethod.invoke(testCase, operation);
                        }
                    }
                }
            }
            
            return testCase;
        }
    }
    
    /**
     * Execute a test case manually if no method is available
     */
    private TestResult executeTestCaseManually(TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestCaseId(testCase.getId());
        result.setExpectedResult(testCase.getExpectedResult());
        
        long startTime = System.currentTimeMillis();
        boolean success = true;
        StringBuilder remarks = new StringBuilder();
        
        // Execute each operation
        if (testCase.getOperations() != null) {
            for (Operation op : testCase.getOperations()) {
                remarks.append("Executing ").append(op.getOperationType()).append("\n");
                boolean opSuccess = op.execute();
                
                if (!opSuccess) {
                    success = false;
                    remarks.append("Operation failed: ").append(op.getRemarks()).append("\n");
                } else {
                    remarks.append("Operation succeeded\n");
                }
            }
        }
        
        result.setStatus(success ? "PASSED" : "FAILED");
        result.setRemarks(remarks.toString());
        result.setExecutionTime(System.currentTimeMillis() - startTime);
        
        return result;
    }
    
    /**
     * Find the addOperation method in TestCase class
     */
    private Method findAddOperationMethod(TestCase testCase) {
        Method[] methods = testCase.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName().equals("addOperation")) {
                return method;
            }
        }
        return null;
    }
    
    /**
     * Set a property on the test case using reflection
     */
    private void setProperty(TestCase testCase, String setter, Object value) {
        try {
            for (Method method : testCase.getClass().getMethods()) {
                if (method.getName().equals(setter) && method.getParameterCount() == 1) {
                    method.invoke(testCase, value);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting property " + setter + ": " + e.getMessage());
        }
    }
    
    /**
     * Parse result JSON into a TestResult object
     */
    private TestResult parseResultJson(String resultJson) {
        try {
            LOGGER.log(Level.INFO, "Parsing result JSON: {0}", resultJson);
            
            // Try to parse the JSON
            JsonElement jsonElement = JsonParser.parseString(resultJson);
            
            // Handle all possible JSON element types
            if (jsonElement.isJsonObject()) {
                JsonObject jsonObj = jsonElement.getAsJsonObject();
                
                // If empty, create a default error result
                if (jsonObj.size() == 0) {
                    TestResult emptyResult = new TestResult();
                    emptyResult.setTestCaseId("unknown");
                    emptyResult.setStatus("ERROR");
                    emptyResult.setRemarks("Empty result JSON object");
                    emptyResult.setExpectedResult("N/A");
                    emptyResult.setActualResult("N/A");
                    return emptyResult;
                }
                
                // Try to detect whether this is a direct test result or wrapped result
                if (jsonObj.has("testcase_id") && jsonObj.has("status")) {
                    // This appears to be a direct result without wrapping
                    LOGGER.log(Level.INFO, "Detected direct test result format");
                    
                    TestResult result = new TestResult();
                    
                    // Extract fields directly
                    if (jsonObj.has("testcase_id")) {
                        result.setTestCaseId(jsonObj.get("testcase_id").getAsString());
                    }
                    if (jsonObj.has("status")) {
                        result.setStatus(jsonObj.get("status").getAsString());
                    }
                    if (jsonObj.has("expected_result")) {
                        result.setExpectedResult(jsonObj.get("expected_result").getAsString());
                    }
                    if (jsonObj.has("actual_result")) {
                        result.setActualResult(jsonObj.get("actual_result").getAsString());
                    }
                    if (jsonObj.has("remarks")) {
                        result.setRemarks(jsonObj.get("remarks").getAsString());
                    }
                    if (jsonObj.has("execution_time")) {
                        result.setExecutionTime(jsonObj.get("execution_time").getAsLong());
                    }
                    
                    return result;
                }
                
                // It's a wrapped result - get the first test case result (there should be only one)
                String testCaseId = jsonObj.keySet().iterator().next();
                JsonElement resultElement = jsonObj.get(testCaseId);
                
                // Check if the result element is an object as expected
                if (resultElement.isJsonObject()) {
                    JsonObject resultObj = resultElement.getAsJsonObject();
                    
                    TestResult result = new TestResult();
                    result.setTestCaseId(testCaseId);
                    
                    if (resultObj.has("status")) {
                        result.setStatus(resultObj.get("status").getAsString());
                    }
                    if (resultObj.has("expected_result")) {
                        result.setExpectedResult(resultObj.get("expected_result").getAsString());
                    }
                    if (resultObj.has("actual_result")) {
                        result.setActualResult(resultObj.get("actual_result").getAsString());
                    }
                    if (resultObj.has("remarks")) {
                        result.setRemarks(resultObj.get("remarks").getAsString());
                    }
                    if (resultObj.has("execution_time")) {
                        result.setExecutionTime(resultObj.get("execution_time").getAsLong());
                    }
                    
                    return result;
                } else {
                    // Handle case where the value isn't a nested object
                    TestResult simpleResult = new TestResult();
                    simpleResult.setTestCaseId(testCaseId);
                    simpleResult.setStatus("UNKNOWN");
                    simpleResult.setRemarks("Unexpected result format: top-level value is not an object");
                    simpleResult.setExpectedResult("N/A");
                    simpleResult.setActualResult(resultElement.toString());
                    return simpleResult;
                }
            } else if (jsonElement.isJsonPrimitive()) {
                // Handle case where the result is a primitive (string, number, etc.)
                String primitiveValue = jsonElement.getAsString();
                
                TestResult primitiveResult = new TestResult();
                primitiveResult.setTestCaseId("unknown");
                primitiveResult.setStatus(primitiveValue.toLowerCase().contains("pass") ? "PASSED" : 
                                         (primitiveValue.toLowerCase().contains("fail") ? "FAILED" : "UNKNOWN"));
                primitiveResult.setRemarks("Received primitive result: " + primitiveValue);
                primitiveResult.setExpectedResult("N/A");
                primitiveResult.setActualResult(primitiveValue);
                return primitiveResult;
            } else if (jsonElement.isJsonArray()) {
                // Handle case where the result is an array
                TestResult arrayResult = new TestResult();
                arrayResult.setTestCaseId("unknown");
                arrayResult.setStatus("UNKNOWN");
                arrayResult.setRemarks("Received array result - cannot parse as test result");
                arrayResult.setExpectedResult("N/A");
                arrayResult.setActualResult(jsonElement.toString());
                return arrayResult;
            } else {
                // Handle other cases (null, etc.)
                TestResult nullResult = new TestResult();
                nullResult.setTestCaseId("unknown");
                nullResult.setStatus("UNKNOWN");
                nullResult.setRemarks("Received null or invalid JSON result");
                nullResult.setExpectedResult("N/A");
                nullResult.setActualResult("null");
                return nullResult;
            }
        } catch (Exception e) {
            // If parsing fails, create a generic error result
            LOGGER.log(Level.SEVERE, "Failed to parse result JSON", e);
            LOGGER.log(Level.INFO, "Raw JSON that caused the error: {0}", resultJson);
            
            TestResult errorResult = new TestResult();
            errorResult.setTestCaseId("unknown");
            errorResult.setStatus("ERROR");
            errorResult.setRemarks("Failed to parse result JSON: " + e.getMessage());
            errorResult.setExpectedResult("N/A");
            errorResult.setActualResult("N/A");
            
            // Attempt to save the original result string
            if (resultJson != null && !resultJson.trim().isEmpty()) {
                errorResult.setRemarks(errorResult.getRemarks() + "\nRaw result: " + 
                    (resultJson.length() > 100 ? resultJson.substring(0, 100) + "..." : resultJson));
            }
            
            return errorResult;
        }
    }
    
    /**
     * Validate that JSON represents a proper test case
     * 
     * @param jsonObject The JSON object to validate
     * @return true if valid, false if not
     */
    private boolean isValidTestCaseStructure(JsonObject jsonObject) {
        // Check basic test case structure
        if (jsonObject == null) return false;
        
        // Check for required fields
        boolean hasTestcaseId = jsonObject.has("testcase_id") && jsonObject.get("testcase_id").isJsonPrimitive();
        boolean hasOperations = jsonObject.has("operations") && jsonObject.get("operations").isJsonArray();
        
        return hasTestcaseId && hasOperations;
    }
}
