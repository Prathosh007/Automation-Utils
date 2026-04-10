package com.me;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.GsonBuilder;
import com.me.util.LogManager;

/**
 * Processor for handling test cases individually through the LLM
 */
public class TestCaseProcessor {
    private static final Logger LOGGER = LogManager.getLogger(TestCaseProcessor.class, LogManager.LOG_TYPE.FW);
    
    // Number of threads for parallel processing
    private static final int MAX_CONCURRENT_REQUESTS = 3;
    
    /**
     * Process test cases individually and aggregate results
     * 
     * @param inputJsonFilePath Path to the input JSON file containing multiple test cases
     * @param rawOutputDirPath Directory to store raw LLM responses
     * @param extractedOutputFilePath Path to the final aggregated output file
     * @throws IOException If there's an error reading or writing files
     */
    public static void processTestCases(String inputJsonFilePath, String rawOutputDirPath, 
                                       String extractedOutputFilePath) throws IOException {
        LOGGER.info("Processing test cases from: " + inputJsonFilePath);//NO I18N
        
        // Create output directory if it doesn't exist
        new File(rawOutputDirPath).mkdirs();
        
        // Read the input JSON
        String jsonContent = readFile(inputJsonFilePath);
        JsonElement jsonElement = JsonParser.parseString(jsonContent);
        
        // Check if we have an array or a single object
        List<JsonObject> testCases = new ArrayList<>();
        
        if (jsonElement.isJsonArray()) {
            // Array of test cases
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            for (JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    testCases.add(element.getAsJsonObject());
                }
            }
        } else if (jsonElement.isJsonObject()) {
            // Check if it's a JSON with a test_cases array
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            if (jsonObject.has("test_cases") && jsonObject.get("test_cases").isJsonArray()) {//NO I18N
                JsonArray jsonArray = jsonObject.getAsJsonArray("test_cases");//NO I18N
                for (JsonElement element : jsonArray) {
                    if (element.isJsonObject()) {
                        testCases.add(element.getAsJsonObject());
                    }
                }
            } else {
                // Just a single test case
                testCases.add(jsonObject);
            }
        }
        
        LOGGER.info("Found " + testCases.size() + " test cases to process");//NO I18N
        
        if (testCases.isEmpty()) {
            LOGGER.warning("No test cases found in input JSON");//NO I18N
            return;
        }
        
        // Create a thread pool for parallel processing
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        
        // Create a map to hold results
        Map<String, JsonObject> resultMap = new ConcurrentHashMap<>();
        
        // Submit test case processing tasks
        List<Future<ProcessResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < testCases.size(); i++) {
            JsonObject testCase = testCases.get(i);
            String testCaseId = getTestCaseId(testCase, i);
            
            // Create temp files for this test case
            String testCaseFilePath = rawOutputDirPath + File.separator + "test_case_" + testCaseId + ".json";//NO I18N
            String rawOutputFilePath = rawOutputDirPath + File.separator + "raw_output_" + testCaseId + ".json";//NO I18N
            String extractedFilePath = rawOutputDirPath + File.separator + "extracted_" + testCaseId + ".json";//NO I18N
            
            // Write test case to file
            try (FileWriter writer = new FileWriter(testCaseFilePath)) {
                writer.write(testCase.toString());
            }
            
            // Submit task to thread pool
            futures.add(executor.submit(() -> 
                processIndividualTestCase(testCaseId, testCaseFilePath, rawOutputFilePath, extractedFilePath)
            ));
        }
        
        // Collect all results
        for (Future<ProcessResult> future : futures) {
            try {
                ProcessResult result = future.get();
                if (result.isSuccess() && result.getExtractedJson() != null) {
                    resultMap.put(result.getTestCaseId(), result.getExtractedJson());
                } else {
                    LOGGER.warning("Failed to process test case " + result.getTestCaseId() + ": " + result.getErrorMessage());//NO I18N
                }
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.SEVERE, "Error waiting for test case processing", e);//NO I18N
            }
        }
        
        // Shut down the executor
        executor.shutdown();
        
        // Combine all results into a single JSON
        JsonObject combinedResults = new JsonObject();
        
        for (Map.Entry<String, JsonObject> entry : resultMap.entrySet()) {
            combinedResults.add(entry.getKey(), entry.getValue());
        }
        
        // Pretty print the combined results
        String formattedJson = new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(combinedResults);
        
        // Write to the final output file
        try (FileWriter writer = new FileWriter(extractedOutputFilePath)) {
            writer.write(formattedJson);
        }
        
        LOGGER.info("Successfully processed " + resultMap.size() + " test cases. Combined results saved to: " + extractedOutputFilePath);//NO I18N
    }
    
    /**
     * Process an individual test case through the LLM
     */
    private static ProcessResult processIndividualTestCase(String testCaseId, String testCaseFilePath, 
                                                          String rawOutputFilePath, String extractedFilePath) {
        LOGGER.info("Processing test case: " + testCaseId);//NO I18N
        
        try {
            // Call LLAMAClient to process this single test case
            LLAMAClient.sendJsonToLLAMA(testCaseFilePath, rawOutputFilePath, extractedFilePath);
            
            // Read the extracted output
            String extractedContent = readFile(extractedFilePath);
            JsonObject extractedJson = JsonParser.parseString(extractedContent).getAsJsonObject();
            
            // Check that the response contains the test case ID
            if (!extractedJson.has(testCaseId)) {
                // If the response doesn't use the test case ID as key, restructure it
                JsonObject properStructure = new JsonObject();
                properStructure.add(testCaseId, extractedJson);
                
                // Save the restructured json
                try (FileWriter writer = new FileWriter(extractedFilePath)) {
                    writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(properStructure));
                }
                
                return new ProcessResult(testCaseId, properStructure.getAsJsonObject(testCaseId), true, null);
            }
            
            return new ProcessResult(testCaseId, extractedJson.getAsJsonObject(testCaseId), true, null);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing test case " + testCaseId, e);//NO I18N
            return new ProcessResult(testCaseId, null, false, e.getMessage());
        }
    }
    
    /**
     * Extract a test case ID from a test case JSON object
     */
    private static String getTestCaseId(JsonObject testCase, int index) {
        // Try several common ID fields
        if (testCase.has("id")) {
            return testCase.get("id").getAsString();//NO I18N
        } else if (testCase.has("testcase_id")) {//NO I18N
            return testCase.get("testcase_id").getAsString();//NO I18N
        } else if (testCase.has("test_id")) {//NO I18N
            return testCase.get("test_id").getAsString();//NO I18N
        } else if (testCase.has("testId")) {//NO I18N
            return testCase.get("testId").getAsString();//NO I18N
        }
        
        // If no ID found, use the index as fallback
        return "TC" + String.format("%03d", index + 1);//NO I18N
    }
    
    /**
     * Read the content of a file
     */
    private static String readFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");//NO I18N
            }
        }
        return content.toString();
    }
    
    /**
     * Inner class to hold results from individual test case processing
     */
    private static class ProcessResult {
        private final String testCaseId;
        private final JsonObject extractedJson;
        private final boolean success;
        private final String errorMessage;
        
        public ProcessResult(String testCaseId, JsonObject extractedJson, boolean success, String errorMessage) {
            this.testCaseId = testCaseId;
            this.extractedJson = extractedJson;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public String getTestCaseId() { return testCaseId; }
        public JsonObject getExtractedJson() { return extractedJson; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    public TestResult processTestCase(TestCase testCase) {
        LOGGER.info("Processing test case: " + testCase.getId()); //No I18N
        
        TestResult result = new TestResult();
        result.setTestCaseId(testCase.getId());
        result.setDescription(testCase.getDescription());
        result.setExpectedResult(testCase.getExpectedResult());
        
        // Set default status
        result.setStatus(TestResult.STATUS_PASSED); 
        
        // Process operations
        long startTime = System.currentTimeMillis();
        StringBuilder remarksBuilder = new StringBuilder();
        
        try {
            List<Operation> operations = testCase.getOperations();
            if (operations == null || operations.isEmpty()) {
                LOGGER.warning("No operations found for test case: " + testCase.getId()); //No I18N
                result.setStatus(TestResult.STATUS_SKIPPED); 
                result.setRemarks("No operations found"); //No I18N
                return result;
            }
            
            // Execute each operation
            for (int i = 0; i < operations.size(); i++) {
                Operation operation = operations.get(i);
                
                // Get operation type 
                String operationType = operation.getOperationType();
                if (operationType == null) {
                    LOGGER.warning("Operation #" + (i+1) + " has no operation_type"); //No I18N
                    remarksBuilder.append("Operation #").append(i+1).append(" has no operation_type\n"); //No I18N
                    continue;
                }
                
                // Operation is already constructed and has its parameters
                
                LOGGER.info("Executing operation #" + (i+1) + " (" + operationType + ")"); //No I18N
                
                long opStartTime = System.currentTimeMillis();
                boolean success = operation.execute();
                long opDuration = System.currentTimeMillis() - opStartTime;
                
                // Record operation result
                remarksBuilder.append("Operation #").append(i+1).append(": ") //No I18N
                           .append(operationType).append(" - ") //No I18N
                           .append(success ? "SUCCESS" : "FAILED") //No I18N
                           .append(" (").append(opDuration).append(" ms)\n"); //No I18N
                
                if (operation.getRemarks() != null) {
                    remarksBuilder.append(operation.getRemarks()).append("\n"); //No I18N
                }
                
                // If any operation fails, the test case fails
                if (!success) {
                    result.setStatus(TestResult.STATUS_FAILED); 
                    
                    if (operation.getOutputValue() != null) {
                        result.setActualResult(operation.getOutputValue());
                    } else {
                        result.setActualResult("Operation #" + (i+1) + " (" + operationType + ") failed"); //No I18N
                    }
                    
                    // We could break here to stop on first failure, or continue to execute all operations
                    // For now, we'll continue to execute all operations
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing test case: " + testCase.getId(), e); //No I18N
            result.setStatus(TestResult.STATUS_ERROR); 
            result.setActualResult("Exception: " + e.getMessage()); //No I18N
            remarksBuilder.append("Error: ").append(e.getMessage()).append("\n"); //No I18N
        }
        
        // Record execution time and remarks
        long executionTime = System.currentTimeMillis() - startTime;
        result.setExecutionTime(executionTime);
        result.setRemarks(remarksBuilder.toString());
        
        LOGGER.info("Test case " + testCase.getId() + " processed in " + executionTime + "ms with status: " + result.getStatus()); //No I18N
        
        return result;
    }
    
    /**
     * Extract operation type from operation JSON
     */
    private String getOperationTypeFromJson(JsonObject operationJson) {
        if (operationJson == null) {
            return null;
        }
        
        if (operationJson.has("operation_type") && !operationJson.get("operation_type").isJsonNull()) { //No I18N
            return operationJson.get("operation_type").getAsString(); //No I18N
        }
        
        return null;
    }
    
    /**
     * Extract parameters from operation JSON
     */
    private Map<String, String> getParametersFromJson(JsonObject operationJson) {
        Map<String, String> params = new HashMap<>();
        
        if (operationJson == null || !operationJson.has("parameters")) { //No I18N
            return params;
        }
        
        JsonElement paramsElement = operationJson.get("parameters"); //No I18N
        if (!paramsElement.isJsonObject()) {
            return params;
        }
        
        JsonObject paramsJson = paramsElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : paramsJson.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            
            if (!value.isJsonNull()) {
                params.put(key, value.getAsString());
            }
        }
        
        return params;
    }
    
    /**
     * Process a batch of test cases
     * 
     * @param testCases List of test cases to process
     * @return List of test results
     */
    public List<TestResult> processBatch(List<TestCase> testCases) {
        List<TestResult> results = new ArrayList<>();
        
        if (testCases == null || testCases.isEmpty()) {
            LOGGER.warning("No test cases provided for batch processing"); //No I18N
            return results;
        }
        
        LOGGER.info("Processing batch of " + testCases.size() + " test cases"); //No I18N
        
        for (TestCase testCase : testCases) {
            TestResult result = processTestCase(testCase);
            results.add(result);
        }
        
        return results;
    }
}
