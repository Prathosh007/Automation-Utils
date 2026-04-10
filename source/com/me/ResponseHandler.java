package com.me;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

import com.me.util.LogManager;

/**
 * Handles processing of LLAMA response and test execution
 */
public class ResponseHandler {
    private static final Logger LOGGER = LogManager.getLogger(ResponseHandler.class.getName(), LogManager.LOG_TYPE.FW);
    
    /**
     * Handle the JSON response from LLAMA
     * @param jsonFilePath Path to the JSON response file
     */
    public static void handleResponse(String jsonFilePath) throws IOException {
        handleResponse(jsonFilePath, null);
    }
    
    /**
     * Handle the JSON response from LLAMA with test properties
     * @param jsonFilePath Path to the JSON response file
     * @param testProperties Properties for the test run
     */
    public static void handleResponse(String jsonFilePath, Properties testProperties) throws IOException {
        LOGGER.info("Processing response from file: " + jsonFilePath);//No I18N
        
        // Create a copy of the file for backup and audit
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());//No I18N
        String backupFilePath = "logs/test_execution_" + timestamp + ".json";//No I18N
        
        try {
            Files.copy(new File(jsonFilePath).toPath(), new File(backupFilePath).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Created backup of input file at: " + backupFilePath);//No I18N
        } catch (Exception e) {
            LOGGER.warning("Failed to create backup of input file: " + e.getMessage());//No I18N
        }
        
        // Get the results path from properties or use default
        String resultsPath = testProperties != null ? 
            testProperties.getProperty("resultsPath", "logs/test_results.json") : //No I18N
            "logs/test_results.json";//No I18N
        
        // Create report generator to manage test results
        ReportGenerator reportGenerator = new ReportGenerator(resultsPath, testProperties);
        
        // Track which products have been installed in this test run
        Map<String, Boolean> productInstalled = new HashMap<>();
        
        try {
            // Log the input JSON file size and path
            File inputFile = new File(jsonFilePath);
            LOGGER.info("Reading test cases from: " + inputFile.getAbsolutePath() + " (Size: " + //No I18N
                      inputFile.length() + " bytes)");//No I18N
            
            // Read the JSON file
            JsonObject jsonResponse;
            try {
                jsonResponse = readJsonFile(jsonFilePath);
                LOGGER.info("Successfully parsed JSON input with " + jsonResponse.size() + " test cases");//No I18N
            } catch (Exception e) {
                LOGGER.severe("Failed to parse JSON input file: " + e.getMessage());//No I18N
                throw new IOException("Failed to parse JSON input file: " + e.getMessage(), e);//No I18N
            }
            
            // Sort test cases by their ID to ensure proper execution order
            List<String> sortedTestIds = new ArrayList<>(jsonResponse.keySet());
            Collections.sort(sortedTestIds);
            
            LOGGER.info("Preparing to execute " + sortedTestIds.size() + " test cases in sorted order");//No I18N
            
            // Process test cases in order
            for (String testId : sortedTestIds) {
                // Skip entries that are not test cases
                if (testId.equals("test_cases")) {//No I18N
                    LOGGER.info("Skipping 'test_cases' key which is not a test case");//No I18N
                    continue;
                }
                
                // Get test case and validate
                JsonElement testCaseElement = jsonResponse.get(testId);
                if (!testCaseElement.isJsonObject()) {
                    LOGGER.warning("Test case " + testId + " is not a valid JSON object - skipping");//No I18N
                    continue;
                }
                
                JsonObject testCase = testCaseElement.getAsJsonObject();
                
                LOGGER.info("Processing test case: " + testId);//No I18N
                
                try {
                    // Create a test-specific logger for this test case
                    Logger testLogger = LogManager.getLogger(testId, LogManager.LOG_TYPE.FW);
                    testLogger.info("Starting test case: " + testId);//No I18N
                    
                    // Check if the test requires an existing installation
                    boolean reuseInstallation = false;
                    String productName = "";
                    
                    if (testCase.has("reuse_installation")) {//No I18N
                        reuseInstallation = testCase.get("reuse_installation").getAsBoolean();//No I18N
                        testLogger.info("Test case has reuse_installation: " + reuseInstallation);//No I18N
                    }
                    
                    if (testCase.has("product_name")) {//No I18N
                        productName = testCase.get("product_name").getAsString();//No I18N
                        testLogger.info("Test case has product_name: " + productName);//No I18N
                    }
                    
                    // Check if we need to reuse installation but product hasn't been installed
                    if (reuseInstallation && !productName.isEmpty() && 
                            (!productInstalled.containsKey(productName) || !productInstalled.get(productName))) {
                        testLogger.warning("Test case " + testId + " requires reuse_installation but product " + //No I18N
                                    productName + " has not been installed yet. Will perform installation first.");//No I18N
                                    
                        addInstallationOperation(testCase, productName);
                    }
                    
                    // Process operations
                    List<Operation> operations = new ArrayList<>();
                    
                    if (testCase.has("operations")) {
                        // Multiple operations in an array
                        JsonArray opsArray = testCase.getAsJsonArray("operations");//No I18N
                        testLogger.info("Found " + opsArray.size() + " operations in test case");//No I18N
                        
                        for (JsonElement opElement : opsArray) {
                            JsonObject op = opElement.getAsJsonObject();
                            Operation operation = createOperationFromJson(op);
                            operations.add(operation);
                            testLogger.info("Added operation: " + operation.getOperationType());//No I18N
                        }
                    } else if (testCase.has("operation")) {
                        // Single operation directly in the test case
                        testLogger.info("Found single operation in test case");//No I18N
                        Operation operation = createOperationFromJson(testCase);
                        operations.add(operation);
                    } else {
                        testLogger.warning("Test case " + testId + " has no operations defined");//No I18N
                    }
                    
                    // Execute test operations
                    testLogger.info("Executing " + operations.size() + " operations for test case " + testId);//No I18N
                    String result = TestExecutor.executeTestSequence(testId, operations);
                    testLogger.info("Test result: " + result);//No I18N
                    
                    // Update installation state if this was an installation test
                    if (!productName.isEmpty() && result.contains("PASSED")) {//No I18N
                        // Check if we performed an installation operation
                        for (Operation op : operations) {
                            if ("exe_install".equals(op.getOperationType())) {//No I18N
                                testLogger.info("Marking product as installed: " + productName);//No I18N
                                productInstalled.put(productName, true);
                                break;
                            }
                        }
                    }
                    
                    // Record test result
                    reportGenerator.recordTestResult(testId, testCase, operations, result);
                    testLogger.info("Completed test case: " + testId);//No I18N
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error executing test case " + testId, e);//No I18N
                }
            }
            
            // Save test results
            reportGenerator.saveTestResults();
            
            // Generate HTML report if specified in properties
            if (testProperties != null && "true".equalsIgnoreCase(testProperties.getProperty("generateHtml", "false"))) {//No I18N
                String htmlPath = testProperties.getProperty("htmlReportPath", "logs/test_report.html");//No I18N
                reportGenerator.generateHtmlReport(htmlPath);
            }
            
            // Log summary
            LOGGER.info(reportGenerator.getSummary());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing JSON response", e);//No I18N
            throw new IOException("Error processing JSON response: " + e.getMessage(), e);//No I18N
        }
    }
    
    /**
     * Create an Operation object from JSON
     */
    private static Operation createOperationFromJson(JsonObject json) {
        // Check for the new format first
        if (json.has("operation_type") && json.has("parameters")) {//No I18N
            // New format with parameters object
            String operationType = json.get("operation_type").getAsString();//No I18N
            Map<String, String> parameters = new HashMap<>();
            
            JsonObject paramsObj = json.getAsJsonObject("parameters");//No I18N
            for (Map.Entry<String, JsonElement> entry : paramsObj.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    parameters.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            
            return new Operation(operationType, parameters);
        }
        
        // Legacy format - flatten structure
        String operationType = json.has("operation") ? json.get("operation").getAsString() : //No I18N
                               (json.has("operation_type") ? json.get("operation_type").getAsString() : "");//No I18N
        String filePath = json.has("file_path") ? json.get("file_path").getAsString() : "";//No I18N
        String fileName = json.has("filename") ? json.get("filename").getAsString() : //No I18N
                         (json.has("filename") ? json.get("filename").getAsString() : "");//No I18N
        String value = json.has("value") ? json.get("value").getAsString() : "";//No I18N
        String productName = json.has("product_name") ? json.get("product_name").getAsString() : "";//No I18N
        
        return new Operation(operationType, filePath, fileName, value, productName);
    }
    
    /**
     * Add an installation operation to a test case that needs it
     */
    private static void addInstallationOperation(JsonObject testCase, String productName) {
        if (testCase.has("operations") && testCase.get("operations").isJsonArray()) {//No I18N
            JsonArray operations = testCase.getAsJsonArray("operations");//No I18N
            
            // Check if there's already an install operation
            boolean hasInstallOp = false;
            for (JsonElement op : operations) {
                if (op.isJsonObject()) {
                    JsonObject opObj = op.getAsJsonObject();
                    String opType = opObj.has("operation_type") ? //No I18N
                                   opObj.get("operation_type").getAsString() ://No I18N
                                   (opObj.has("operation") ? opObj.get("operation").getAsString() : "");//No I18N
                    
                    if ("exe_install".equals(opType)) {
                        hasInstallOp = true;
                        break;
                    }
                }
            }
            
            // If no install operation exists, add one
            if (!hasInstallOp) {
                // Create new operation in the new format
                JsonObject installOp = new JsonObject();
                installOp.addProperty("operation_type", "exe_install");//No I18N
                
                JsonObject params = new JsonObject();
                params.addProperty("product_name", productName);//No I18N
                installOp.add("parameters", params);//No I18N
                
                // Create a new array with the install operation first
                JsonArray newOps = new JsonArray();
                newOps.add(installOp);
                
                // Add all original operations
                for (JsonElement op : operations) {
                    newOps.add(op);
                }
                
                // Replace the operations array
                testCase.add("operations", newOps);//No I18N
                
                // Set reuse_installation to false as we're installing
                testCase.addProperty("reuse_installation", false);//No I18N
                
                LOGGER.info("Added installation operation for product: " + productName);//No I18N
            }
        }
    }
    
    /**
     * Read a JSON file into a JsonObject
     */
    private static JsonObject readJsonFile(String filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.severe("Error reading JSON file: " + e.getMessage());//No I18N
            throw new IOException("Error reading JSON file: " + e.getMessage(), e);//No I18N
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            LOGGER.info("Usage: java ResponseHandler <responseFilePath> [reportPath]");//No I18N
            return;
        }

        String responseFilePath = args[0];
        
        // Optional report path argument
        String reportPath = args.length > 1 ? args[1] : "logs/test_results.json";//No I18N
        
        // Setup properties if report path specified
        Properties props = new Properties();
        props.setProperty("resultsPath", reportPath);//No I18N

        try {
            
            LOGGER.info("ResponseHandler starting with file: " + responseFilePath);//No I18N
            LOGGER.info("Results will be saved to: " + reportPath);//No I18N
            
            // Reset installed products tracker before each run
            handleResponse(responseFilePath, props);
            
            LOGGER.info("ResponseHandler completed successfully");//No I18N
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error: Unable to handle response.", e);//No I18N
            LOGGER.severe("Error: " + e.getMessage());//NO I18N
            System.exit(1);
        }
    }
}
