package com.me;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import com.me.util.LogManager;

/**
 * Handles the generation and management of test reports and results
 */
public class ReportGenerator {
    private static final Logger LOGGER = LogManager.getLogger(ReportGenerator.class, LogManager.LOG_TYPE.FW);
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//NO I18N
    private JsonObject testResults;
    private final String resultsPath;
    private final Properties testProperties;
    
    /**
     * Constructor for the ReportGenerator
     * @param resultsPath Path where test results will be saved
     * @param testProperties Additional properties for the test run
     */
    public ReportGenerator(String resultsPath, Properties testProperties) {
        this.resultsPath = resultsPath;
        this.testProperties = testProperties != null ? testProperties : new Properties();
        this.testResults = loadExistingResults(resultsPath);
        
        LOGGER.info("ReportGenerator initialized with results path: " + resultsPath);
    }
    
    /**
     * Load existing test results if available
     * @param resultsPath Path to the results file
     * @return JsonObject containing test results, or a new JsonObject if none exist
     */
    private JsonObject loadExistingResults(String resultsPath) {
        File resultsFile = new File(resultsPath);
        if (resultsFile.exists()) {
            try (FileReader reader = new FileReader(resultsFile)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not load existing results, creating new results file", e);
            }
        }
        return new JsonObject();
    }
    
    /**
     * Record the result of a test case
     * @param testId The test case identifier
     * @param testCase The test case data
     * @param operations The operations that were executed
     * @param testResult The result of the test execution
     */
    public void recordTestResult(String testId, JsonObject testCase, List<Operation> operations, String testResult) {
        JsonObject result = new JsonObject();
        result.addProperty("testcase_id", testId);//NO I18N
        
        // Add description from test case if available
        if (testCase.has("testcase_description")) {
            result.addProperty("description", testCase.get("testcase_description").getAsString());//NO I18N
        }
        
        // Process result string to determine status
        String testStatus = testResult.contains("PASSED") ? "PASSED" : "FAILED";//NO I18N
        result.addProperty("result", testStatus);//NO I18N
        result.addProperty("details", testResult);//NO I18N
        
        // Add timestamp
        result.addProperty("timestamp", dateFormat.format(new Date()));//NO I18N
        
        // Add test run information
        if (testProperties.containsKey("run_id")) {
            result.addProperty("run_id", testProperties.getProperty("run_id"));//NO I18N
        }
        
        // Record failed operations if any
        if (testStatus.equals("FAILED")) {//NO I18N
            JsonArray failedOps = new JsonArray();
            
            // Parse the result to find which operations failed
            String[] lines = testResult.split("\n");
            for (int i = 0; i < lines.length - 1; i++) { // Skip last line (summary)
                String line = lines[i];
                if (line.contains("FAILED")) {
                    // Extract operation type
                    String operationType = line.substring(
                        line.indexOf("Operation ") + 10, //NO I18N
                        line.indexOf(" - FAILED")//NO I18N
                    ).trim();
                    
                    // Find the corresponding operation in the list
                    for (Operation op : operations) {
                        if (op.getOperationType().equals(operationType)) {
                            JsonObject failedOp = new JsonObject();
                            failedOp.addProperty("operation", op.getOperationType());//NO I18N
                            failedOp.addProperty("file_path", op.getFilePath());//NO I18N
                            failedOp.addProperty("filename", op.getFileName());//NO I18N
                            failedOp.addProperty("value", op.getValue());//NO I18N
                            failedOps.add(failedOp);
                            break;
                        }
                    }
                }
            }
            
            if (failedOps.size() > 0) {
                result.add("failed_operations", failedOps);//NO I18N
            }
        }
        
        // Add or update test result in the results object
        testResults.add(testId, result);
    }
    
    /**
     * Save the test results to the specified file
     */
    public void saveTestResults() {
        try {
            // Create directory if it doesn't exist
            File resultsFile = new File(resultsPath);
            File parent = resultsFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            // Write results with pretty printing
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(resultsPath)) {
                writer.write(gson.toJson(testResults));
            }
            
            LOGGER.info("Test results saved to: " + resultsPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save test results", e);
        }
    }
    
    /**
     * Get a summary of the test results
     * @return A string containing the test summary
     */
    public String getSummary() {
        int totalTests = testResults.size();
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        
        for (Map.Entry<String, JsonElement> entry : testResults.entrySet()) {
            JsonObject testCase = entry.getValue().getAsJsonObject();
            if (testCase.has("result")) {
                String result = testCase.get("result").getAsString();
                if ("PASSED".equalsIgnoreCase(result)) {
                    passed++;
                } else if ("FAILED".equalsIgnoreCase(result)) {
                    failed++;
                } else if ("SKIPPED".equalsIgnoreCase(result)) {
                    skipped++;
                }
            }
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Test Results Summary:\n");//NO I18N
        summary.append("-------------------\n");//NO I18N
        summary.append("Total Tests: ").append(totalTests).append("\n");//NO I18N
        summary.append("Passed: ").append(passed).append("\n");//NO I18N
        summary.append("Failed: ").append(failed).append("\n");//NO I18N
        summary.append("Skipped: ").append(skipped).append("\n");//NO I18N
        summary.append("-------------------\n");//NO I18N
        summary.append("Results saved to: ").append(resultsPath).append("\n");//NO I18N
        
        return summary.toString();
    }
    
    /**
     * Check if there are any failed tests
     * @return true if there are failed tests, false otherwise
     */
    public boolean hasFailedTests() {
        for (Map.Entry<String, JsonElement> entry : testResults.entrySet()) {
            JsonObject testCase = entry.getValue().getAsJsonObject();
            if (testCase.has("result") && 
                "FAILED".equalsIgnoreCase(testCase.get("result").getAsString())) {//NO I18N
                return true;
            }
        }
        return false;
    }
    
    /**
     * Generate an HTML report from the test results
     * @param outputPath Path to save the HTML report
     */
    public void generateHtmlReport(String outputPath) {
        try {
            StringBuilder html = new StringBuilder();
            html.append("<html><head>");//NO I18N
            html.append("<title>GOAT Test Report</title>");//NO I18N
            html.append("<style>");//NO I18N
            html.append("body {font-family: Arial, sans-serif; margin: 20px;}");//NO I18N
            html.append("table {border-collapse: collapse; width: 100%;}");//NO I18N
            html.append("th, td {border: 1px solid #ddd; padding: 8px; text-align: left;}");//NO I18N
            html.append("th {background-color: #f2f2f2;}");//NO I18N
            html.append(".passed {background-color: #dff0d8; color: #3c763d;}");//NO I18N
            html.append(".failed {background-color: #f2dede; color: #a94442;}");//NO I18N
            html.append(".skipped {background-color: #fcf8e3; color: #8a6d3b;}");//NO I18N
            html.append("</style>");//NO I18N
            html.append("</head><body>");//NO I18N
            
            // Add summary
            html.append("<h1>GOAT Test Report</h1>");//NO I18N
            html.append("<p>Generated on: ").append(dateFormat.format(new Date())).append("</p>");//NO I18N
            
            // Count results
            int totalTests = testResults.size();
            int passed = 0;
            int failed = 0;
            int skipped = 0;
            
            for (Map.Entry<String, JsonElement> entry : testResults.entrySet()) {
                JsonObject testCase = entry.getValue().getAsJsonObject();
                if (testCase.has("result")) {
                    String result = testCase.get("result").getAsString();
                    if ("PASSED".equalsIgnoreCase(result)) {
                        passed++;
                    } else if ("FAILED".equalsIgnoreCase(result)) {
                        failed++;
                    } else if ("SKIPPED".equalsIgnoreCase(result)) {
                        skipped++;
                    }
                }
            }
            
            // Add summary table
            html.append("<h2>Summary</h2>");//NO I18N
            html.append("<table>");//NO I18N
            html.append("<tr><th>Total Tests</th><th>Passed</th><th>Failed</th><th>Skipped</th></tr>");//NO I18N
            html.append("<tr>");//NO I18N
            html.append("<td>").append(totalTests).append("</td>");//NO I18N
            html.append("<td class='passed'>").append(passed).append("</td>");//NO I18N
            html.append("<td class='failed'>").append(failed).append("</td>");//NO I18N
            html.append("<td class='skipped'>").append(skipped).append("</td>");//NO I18N
            html.append("</tr>");//NO I18N
            html.append("</table>");//NO I18N
            
            // Add details table
            html.append("<h2>Details</h2>");//NO I18N
            html.append("<table>");//NO I18N
            html.append("<tr><th>ID</th><th>Description</th><th>Result</th><th>Timestamp</th></tr>");//NO I18N
            
            for (Map.Entry<String, JsonElement> entry : testResults.entrySet()) {
                String testId = entry.getKey();
                JsonObject testCase = entry.getValue().getAsJsonObject();
                
                String result = testCase.has("result") ? testCase.get("result").getAsString() : "UNKNOWN";//NO I18N
                String description = testCase.has("description") ? testCase.get("description").getAsString() : "";//NO I18N
                String timestamp = testCase.has("timestamp") ? testCase.get("timestamp").getAsString() : "";//NO I18N
                
                String resultClass = "PASSED".equalsIgnoreCase(result) ? "passed" : //NO I18N
                                     "FAILED".equalsIgnoreCase(result) ? "failed" : "skipped";//NO I18N
                
                html.append("<tr>");//NO I18N
                html.append("<td>").append(testId).append("</td>");//NO I18N
                html.append("<td>").append(description).append("</td>");//NO I18N
                html.append("<td class='").append(resultClass).append("'>").append(result).append("</td>");//NO I18N
                html.append("<td>").append(timestamp).append("</td>");//NO I18N
                html.append("</tr>");//NO I18N
            }
            
            html.append("</table>");//NO I18N
            html.append("</body></html>");//NO I18N
            
            // Write the HTML file
            try (FileWriter writer = new FileWriter(outputPath)) {
                writer.write(html.toString());
            }
            
            LOGGER.info("HTML report generated: " + outputPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to generate HTML report", e);
        }
    }
    
    /**
     * Get the test results object
     * @return The JsonObject containing test results
     */
    public JsonObject getTestResults() {
        return testResults;
    }
    
    public static String generateHtmlReport(List<TestResult> results, String title) {
        StringBuilder html = new StringBuilder();
        
        // HTML header
        html.append("<!DOCTYPE html>\n"); //No I18N
        html.append("<html>\n<head>\n"); //No I18N
        html.append("  <title>").append(title).append("</title>\n"); //No I18N
        html.append("  <style>\n"); //No I18N
        html.append("    body { font-family: Arial, sans-serif; margin: 20px; }\n"); //No I18N
        html.append("    .passed { color: green; }\n"); //No I18N
        html.append("    .failed { color: red; }\n"); //No I18N
        html.append("    .skipped { color: orange; }\n"); //No I18N
        html.append("    table { border-collapse: collapse; width: 100%; }\n"); //No I18N
        html.append("    th, td { border: 1px solid #ddd; padding: 8px; }\n"); //No I18N
        html.append("    th { background-color: #f2f2f2; }\n"); //No I18N
        html.append("    tr:nth-child(even) { background-color: #f9f9f9; }\n"); //No I18N
        html.append("  </style>\n"); //No I18N
        html.append("</head>\n<body>\n"); //No I18N
        
        // ...existing code...
        
        // Footer
        html.append("</body>\n</html>"); //No I18N
        
        return html.toString();
    }
}
