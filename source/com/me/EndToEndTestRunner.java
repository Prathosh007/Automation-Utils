package com.me;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;


/**
 * End-to-end test runner that orchestrates the entire test process
 */
public class EndToEndTestRunner {
    private static final Logger LOGGER = LogManager.getLogger(EndToEndTestRunner.class, LogManager.LOG_TYPE.FW);
    
    private String inputFile;
    private String outputJsonFile;
    private String reportHtmlFile;
    private String reportTitle;
    
    public EndToEndTestRunner(String inputFile, String outputJsonFile, String reportHtmlFile) {
        this(inputFile, outputJsonFile, reportHtmlFile, "G.O.A.T Test Results");//NO I18N
    }
    
    public EndToEndTestRunner(String inputFile, String outputJsonFile, String reportHtmlFile, String reportTitle) {
        this.inputFile = inputFile;
        this.outputJsonFile = outputJsonFile;
        this.reportHtmlFile = reportHtmlFile;
        this.reportTitle = reportTitle != null ? reportTitle : "G.O.A.T Test Results";//NO I18N
    }
    
    /**
     * Run the end-to-end test process
     * 
     * @return true if successful, false otherwise
     */
    public boolean runTests() {
        try {
            LOGGER.info("Starting end-to-end test execution"); //No I18N
            LOGGER.info("Input file: " + inputFile); //No I18N
            LOGGER.info("Output JSON file: " + outputJsonFile); //No I18N
            LOGGER.info("Report HTML file: " + reportHtmlFile); //No I18N
            LOGGER.info("Report title: " + reportTitle); //No I18N
            
            // Step 1: Read and parse the input JSON file
            LOGGER.info("Reading input file..."); //No I18N
            if (!Files.exists(Paths.get(inputFile))) {
                LOGGER.severe("Input file does not exist: " + inputFile); //No I18N
                return false;
            }
            
            String jsonContent = new String(Files.readAllBytes(Paths.get(inputFile)));
            
            // Step 1.5: First validate JSON syntax (check for unescaped backslashes, etc.)
            LOGGER.info("Checking JSON syntax..."); //No I18N
            TestCaseValidator.ValidationResult syntaxResult = TestCaseValidator.validateJson(jsonContent);
            if (!syntaxResult.isValid()) {
                LOGGER.severe("JSON syntax validation failed!"); //No I18N
                for (String error : syntaxResult.getErrors()) {
                    LOGGER.severe("Error: " + error); //No I18N
                    ConsoleOut.println("Error: " + error); //No I18N
                }
                
                // Add wait prompt
                if (System.console() != null) {
                    ConsoleOut.print("\nJSON syntax validation failed. Continue anyway? (NOT RECOMMENDED) [y/N]: "); //No I18N
                    String response = System.console().readLine().trim().toLowerCase();
                    
                    if (!response.equals("y")) {
                        ConsoleOut.println("Test execution aborted."); //No I18N
                        return false;
                    }
                    ConsoleOut.println("Proceeding despite syntax errors..."); //No I18N
                } else {
                    return false;
                }
            }
            
            // Step 2: If syntax is valid, parse the JSON
            LOGGER.info("Parsing JSON..."); //No I18N
            JsonObject jsonObject;
            try {
                jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error parsing input JSON despite syntax validation", e); //No I18N
                ConsoleOut.println("Error parsing JSON: " + e.getMessage()); //No I18N
                return false;
            }
            
            // Step 3: Validate the test case structure
            LOGGER.info("Validating test case structure..."); //No I18N
            TestCaseValidator.ValidationResult validationResult = TestCaseValidator.validate(jsonObject);
            if (!validationResult.isValid() || validationResult.hasWarnings()) {
                ConsoleOut.println("\n=== JSON Validation Results ==="); //No I18N
                
                if (!validationResult.isValid()) {
                    ConsoleOut.println("\nERRORS (Must be fixed before proceeding):"); //No I18N
                    for (String error : validationResult.getErrors()) {
                        ConsoleOut.println("- " + error); //No I18N
                    }
                }
                
                if (validationResult.hasWarnings()) {
                    ConsoleOut.println("\nWARNINGS (Tests might still run, but could have issues):"); //No I18N
                    for (String warning : validationResult.getWarnings()) {
                        ConsoleOut.println("- " + warning); //No I18N
                    }
                }
                
                // Always wait for user confirmation when there are validation issues
                if (System.console() != null) {
                    if (!validationResult.isValid()) {
                        ConsoleOut.print("\nValidation failed. Would you like to proceed anyway? (NOT RECOMMENDED) [y/N]: "); //No I18N
                    } else {
                        ConsoleOut.print("\nWarnings found. Would you like to proceed? [Y/n]: "); //No I18N
                    }
                    
                    String response = System.console().readLine();
                    if (response == null) {
                        // Handle case when there's no console input available
                        ConsoleOut.println("No console input available. Proceeding with default action."); //No I18N
                    } else {
                        response = response.trim().toLowerCase();
                        if ((!validationResult.isValid() && !response.equals("y")) || 
                            (validationResult.isValid() && response.equals("n"))) {
                            ConsoleOut.println("Test execution aborted. Please fix the issues and try again."); //No I18N
                            return false;
                        } else {
                            ConsoleOut.println("Proceeding with execution..."); //No I18N
                        }
                    }
                } else if (!validationResult.isValid()) {
                    // Non-interactive mode and validation failed - abort
                    return false;
                }
            } else {
                LOGGER.info("Validation successful. No issues found."); //No I18N
            }
            
            // Step 4: Create directories for output files BEFORE executing tests
            LOGGER.info("Creating output directories..."); //No I18N
            try {
                // Create directories for output JSON and HTML report
                createDirectoryIfNotExists(outputJsonFile);
                createDirectoryIfNotExists(reportHtmlFile);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error creating directories: " + e.getMessage(), e); //No I18N
                ConsoleOut.println("Error creating output directories: " + e.getMessage()); //No I18N
                return false;
            }
            
            // Step 5: Execute the test cases
            LOGGER.info("Executing test cases..."); //No I18N
            
            // Execute tests directly
            JsonObject results = executeTests(jsonObject);
            if (results == null) {
                LOGGER.severe("Test execution failed"); //No I18N
                return false;
            }
            
            // Step 6: Save results to output file
            LOGGER.info("Saving test results..."); //No I18N
            try (FileWriter writer = new FileWriter(outputJsonFile)) {
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(results));
                LOGGER.info("Test results saved to " + outputJsonFile); //No I18N
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error saving results to JSON file", e); //No I18N
                return false;
            }
            
            // Step 7: Generate the HTML report
            LOGGER.info("Generating HTML report..."); //No I18N
            
            List<TestResult> resultsList = convertJsonToTestResults(results);
            boolean reportSuccess = HtmlReportGenerator.generateReport(resultsList, reportHtmlFile, reportTitle);
            
            if (!reportSuccess) {
                LOGGER.warning("HTML report generation failed"); //No I18N
                // Consider this a warning, not a failure
            } else {
                LOGGER.info("HTML report generated at " + reportHtmlFile); //No I18N
            }
            
            LOGGER.info("End-to-end test execution completed successfully"); //No I18N
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in end-to-end test execution", e); //No I18N
            ConsoleOut.println("Error: " + e.getMessage()); //No I18N
            return false;
        }
    }
    
    /**
     * Create directories for a file path if they don't exist
     * 
     * @param filePath Path to the file
     * @throws IOException If directory creation fails
     */
    private void createDirectoryIfNotExists(String filePath) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        
        if (parentDir != null && !parentDir.exists()) {
            LOGGER.info("Creating directory: " + parentDir.getAbsolutePath()); //No I18N
            boolean created = parentDir.mkdirs();
            
            if (!created) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath()); //No I18N
            }
        }
    }
    
    /**
     * Execute test cases from JSON
     * 
     * @param jsonObject The JSON object containing test cases
     * @return JsonObject with test results, or null if execution fails
     */
    private JsonObject executeTests(JsonObject jsonObject) {
        try {
            JsonObject resultsJson = new JsonObject();
            
            // Process each test case
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String testCaseId = entry.getKey();
                JsonObject testCase = entry.getValue().getAsJsonObject();
                
                LOGGER.info("Executing test case: " + testCaseId); //No I18N
                
                // Extract expected result and description
                String expectedResult = null;
                String description = null;
                
                if (testCase.has("expected_result")) {
                    expectedResult = testCase.get("expected_result").getAsString();
                }
                
                if (testCase.has("description")) {
                    description = testCase.get("description").getAsString();
                }
                
                // Process operations for this test case
                if (!testCase.has("operations")) {
                    LOGGER.warning("No operations found for test case: " + testCaseId); //No I18N
                    continue;
                }
                
                // Get the operations array
                JsonElement operationsElement = testCase.get("operations");
                if (!operationsElement.isJsonArray()) {
                    LOGGER.warning("Operations is not an array for test case: " + testCaseId); //No I18N
                    continue;
                }
                
                JsonObject resultJson = new JsonObject();
                resultJson.addProperty("testcase_id", testCaseId);//NO I18N
                if (description != null) {
                    resultJson.addProperty("description", description);//NO I18N
                }
                resultJson.addProperty("status", "PASSED");  // Initial status //No I18N
                resultJson.addProperty("execution_time", 0L);//NO I18N
                
                if (expectedResult != null) {
                    resultJson.addProperty("expected_result", expectedResult);//NO I18N
                }
                
                StringBuilder remarksBuilder = new StringBuilder();
                long totalExecutionTime = 0;
                boolean overallSuccess = true;
                
                // Process each operation in the array
                LOGGER.info("Test case " + testCaseId + " has " + operationsElement.getAsJsonArray().size() + " operations"); //No I18N
                
                for (int i = 0; i < operationsElement.getAsJsonArray().size(); i++) {
                    JsonObject operationJson = operationsElement.getAsJsonArray().get(i).getAsJsonObject();
                    String operationType = operationJson.get("operation_type").getAsString();//NO I18N
                    
                    LOGGER.info("Executing operation " + (i+1) + "/" + operationsElement.getAsJsonArray().size() + 
                               ": " + operationType); //No I18N
                    
                    // Execute the operation and get results
                    JsonObject opResultJson = executeOperation(operationJson, testCaseId, expectedResult);
                    boolean opSuccess = "PASSED".equals(opResultJson.get("status").getAsString()); //No I18N
                    long opExecutionTime = opResultJson.get("execution_time").getAsLong();
                    
                    // Add to total execution time
                    totalExecutionTime += opExecutionTime;
                    
                    // If this operation failed, mark the whole test case as failed
                    if (!opSuccess) {
                        overallSuccess = false;
                        resultJson.addProperty("status", "FAILED"); //No I18N
                        resultJson.addProperty("actual_result", "Operation " + (i+1) + " failed: " + operationType); //No I18N
                    }
                    
                    // Append operation details to remarks
                    remarksBuilder.append("Operation ").append(i+1).append(": ") //No I18N
                                 .append(operationType).append(" - ") //No I18N
                                 .append(opSuccess ? "PASSED" : "FAILED") //No I18N
                                 .append(" (").append(opExecutionTime).append(" ms)\n"); //No I18N
                    
                    if (opResultJson.has("remarks")) {
                        remarksBuilder.append(opResultJson.get("remarks").getAsString()).append("\n\n"); //No I18N
                    }
                }
                
                // Update the result with total execution time and remarks
                resultJson.addProperty("execution_time", totalExecutionTime);//NO I18N
                resultJson.addProperty("remarks", remarksBuilder.toString());//NO I18N
                
                // Store result in the results JSON
                resultsJson.add(testCaseId, resultJson);
                
                LOGGER.info("Test case " + testCaseId + " execution " + //NO I18N
                          (overallSuccess ? "successful" : "failed") + //NO I18N
                          " after " + totalExecutionTime + "ms"); //No I18N
            }
            
            return resultsJson;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing tests", e); //No I18N
            return null;
        }
    }
    
    /**
     * Execute a single operation and return result as JsonObject
     */
    private JsonObject executeOperation(JsonObject operationJson, String testCaseId, String expectedResult) {
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("testcase_id", testCaseId);//NO I18N
        
        try {
            String operationType = operationJson.get("operation_type").getAsString();//NO I18N
            JsonObject parameters = operationJson.getAsJsonObject("parameters");//NO I18N
            
            resultJson.addProperty("operation_type", operationType);//NO I18N
            
            // Convert JSON parameters to a Map<String, String>
            Map<String, String> params = new Gson().fromJson(parameters, Map.class);
            
            // Create and execute the operation
            Operation operation = new Operation(operationType, params);
            long startTime = System.currentTimeMillis();
            boolean success = operation.execute();
            long executionTime = System.currentTimeMillis() - startTime;
            
            resultJson.addProperty("status", success ? "PASSED" : "FAILED"); //No I18N
            resultJson.addProperty("execution_time", executionTime);//NO I18N
            
            if (operation.getOutputValue() != null) {
                resultJson.addProperty("actual_result", operation.getOutputValue());//NO I18N
            }
            
            if (operation.getRemarks() != null) {
                resultJson.addProperty("remarks", operation.getRemarks());//NO I18N
            }
            
            return resultJson;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing operation for test case: " + testCaseId, e); //No I18N
            
            resultJson.addProperty("status", "ERROR"); //No I18N
            resultJson.addProperty("execution_time", 0);//NO I18N
            resultJson.addProperty("remarks", "Exception: " + e.getMessage()); //No I18N
            
            return resultJson;
        }
    }
    
    /**
     * Convert JSON results to TestResult objects
     */
    private List<TestResult> convertJsonToTestResults(JsonObject resultsJson) {
        List<TestResult> resultsList = new ArrayList<>();
        
        for (String testCaseId : resultsJson.keySet()) {
            JsonObject resultObj = resultsJson.getAsJsonObject(testCaseId);
            TestResult result = new TestResult();
            result.setTestCaseId(testCaseId);
            
            if (resultObj.has("description")) {
                result.setDescription(resultObj.get("description").getAsString());
            }
            if (resultObj.has("operation_type")) {
                result.setOperationType(resultObj.get("operation_type").getAsString());
            }
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
                
            resultsList.add(result);
        }
        
        return resultsList;
    }
    
    /**
     * Run tests from a JSON file
     */
    public static Map<String, JsonObject> runTests(String jsonFilePath, String outputFilePath) {
        Map<String, JsonObject> results = new HashMap<>();
        
        try {
            LOGGER.info("Reading test cases from: " + jsonFilePath); //No I18N
            
            // Read the file content first
            String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
            
            // First validate JSON syntax
            LOGGER.info("Validating JSON syntax..."); //No I18N
            TestCaseValidator.ValidationResult syntaxResult = TestCaseValidator.validateJson(jsonContent);
            if (!syntaxResult.isValid()) {
                LOGGER.severe("JSON syntax validation failed!"); //No I18N
                for (String error : syntaxResult.getErrors()) {
                    LOGGER.severe("Error: " + error); //No I18N
                    ConsoleOut.println("Error: " + error); //No I18N
                }
                throw new Exception("JSON syntax validation failed: " + syntaxResult.getErrors().get(0));
            }
            
            // Parse JSON
            LOGGER.info("Parsing JSON..."); //No I18N
            JsonObject testCasesObj;
            try {
                testCasesObj = JsonParser.parseString(jsonContent).getAsJsonObject();
            } catch (Exception e) {
                LOGGER.severe("JSON parsing failed: " + e.getMessage()); //No I18N
                ConsoleOut.println("Error parsing JSON: " + e.getMessage()); //No I18N
                throw e;
            }
            
            // Validate test case structure
            TestCaseValidator.ValidationResult result = TestCaseValidator.validate(testCasesObj);
            if (!result.isValid()) {
                LOGGER.severe("Test case validation failed!"); //No I18N
                for (String error : result.getErrors()) {
                    LOGGER.severe("Error: " + error); //No I18N
                }
                throw new Exception("Test case validation failed: " + result.getErrors().get(0));
            } else if (result.hasWarnings()) {
                LOGGER.warning("Test case validation warnings:"); //No I18N
                for (String warning : result.getWarnings()) {
                    LOGGER.warning("Warning: " + warning); //No I18N
                }
            }
            
            // ...existing code to execute tests...
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing test cases: " + e.getMessage(), e); //No I18N
        }
        
        return results;
    }
    
    /**
     * Main method for command line execution
     * 
     * @param args Command line arguments: input_file output_json_file report_html_file [report_title]
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            ConsoleOut.println("Usage: java EndToEndTestRunner <input_file> <output_json_file> <report_html_file> [report_title]"); //No I18N
            System.exit(1);
        }
        
        String reportTitle = args.length > 3 ? args[3] : "G.O.A.T Test Results"; //No I18N
        EndToEndTestRunner runner = new EndToEndTestRunner(args[0], args[1], args[2], reportTitle);
        
        // Check if we're in a non-interactive environment and use an option flag
        boolean forceExecute = false;
        if (args.length > 4 && args[4].equals("--force")) { //No I18N
            forceExecute = true;
        }
        
        boolean success = runner.runTests();
        
        if (!success) {
           ConsoleOut.println("Test execution failed. Check logs for details."); //No I18N
            System.exit(1);
        }
        
        ConsoleOut.println("Test execution completed successfully."); //No I18N
        System.exit(0);
    }
}
