package com.me;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.me.util.LogManager;

/**
 * Central manager for test results and reporting
 */
public class TestResultManager {
    private static final Logger LOGGER = LogManager.getLogger(TestResultManager.class, LogManager.LOG_TYPE.FW);
    private static final Map<String, TestResult> RESULTS = new HashMap<>();
    private static final List<TestResult> RESULTLIST = new ArrayList<>();
    
    // Statistical counters
    private static int passedCount = 0;
    private static int failedCount = 0;
    private static int skippedCount = 0;
    private static int warningCount = 0;
    private static int errorCount = 0;
    
    // Report file paths
    private static final String DEFAULT_REPORT_DIR = "logs" + File.separator + "reports"; //No I18N
    private static final String JSON_REPORT_FILENAME = "test_results.json";//No I18N
    private static final String HTML_REPORT_FILENAME = "test_results.html";//No I18N
    
    /**
     * Add a test result to the manager
     * 
     * @param testCaseId The test case ID
     * @param result The test result object
     */
    public static void addResult(String testCaseId, TestResult result) {
        if (testCaseId == null || testCaseId.isEmpty()) {
            LOGGER.warning("Cannot add result with null or empty test case ID");//No I18N
            return;
        }
        
        if (result == null) {
            LOGGER.warning("Cannot add null result for test case " + testCaseId);//No I18N
            return;
        }
        
        // Ensure the test case ID in the result matches the provided ID
        if (result.getTestCaseId() == null || !result.getTestCaseId().equals(testCaseId)) {
            result.setTestCaseId(testCaseId);
        }
        
        // Store the result
        RESULTS.put(testCaseId, result);
        RESULTLIST.add(result);
        
        // Update statistics
        updateStatistics(result);
        
        LOGGER.info("Added result for test case " + testCaseId + ": " + result.getStatus());//No I18N
    }
    
    /**
     * Update the statistical counters based on result status
     * 
     * @param result The test result object
     */
    private static void updateStatistics(TestResult result) {
        if (result == null || result.getStatus() == null) {return;}
        
        switch (result.getStatus()) {
            case TestResult.STATUS_PASSED:
                passedCount++;
                break;
            case TestResult.STATUS_FAILED:
                failedCount++;
                break;
            case TestResult.STATUS_SKIPPED:
                skippedCount++;
                break;
            case TestResult.STATUS_WARNING:
                warningCount++;
                break;
            case TestResult.STATUS_ERROR:
                errorCount++;
                break;
        }
    }
    
    /**
     * Get a specific test result by ID
     * 
     * @param testCaseId The test case ID
     * @return The test result or null if not found
     */
    public static TestResult getResult(String testCaseId) {
        return RESULTS.get(testCaseId);
    }
    
    /**
     * Get all test results as a map
     * 
     * @return Map of test case IDs to test results
     */
    public static Map<String, TestResult> getAllResults() {
        return new HashMap<>(RESULTS);
    }
    
    /**
     * Get all test results as a list in the order they were added
     * 
     * @return List of test results
     */
    public static List<TestResult> getResultList() {
        return new ArrayList<>(RESULTLIST);
    }
    
    /**
     * Reset all results and statistics
     */
    public static void reset() {
        RESULTS.clear();
        RESULTLIST.clear();
        passedCount = 0;
        failedCount = 0;
        skippedCount = 0;
        warningCount = 0;
        errorCount = 0;
    }
    
    /**
     * Get the count of tests with a specific status
     * 
     * @param status The status to count
     * @return Number of tests with that status
     */
    public static int getStatusCount(String status) {
        if (status == null) {return 0;}
        
        switch (status) {
            case TestResult.STATUS_PASSED:  return passedCount;
            case TestResult.STATUS_FAILED:  return failedCount;
            case TestResult.STATUS_SKIPPED: return skippedCount;
            case TestResult.STATUS_WARNING: return warningCount;
            case TestResult.STATUS_ERROR:   return errorCount;
            default: return 0;
        }
    }
    
    /**
     * Get the total number of test results
     * 
     * @return Total number of test results
     */
    public static int getTotalCount() {
        return RESULTLIST.size();
    }
    
    /**
     * Generate a JSON report of all test results
     * 
     * @return JSON string containing all test results
     */
    public static String generateJsonReport() {
        JsonObject reportObj = new JsonObject();
        
        // Add summary information
        JsonObject summaryObj = new JsonObject();
        summaryObj.addProperty("total", getTotalCount());//No I18N
        summaryObj.addProperty("passed", passedCount);//No I18N
        summaryObj.addProperty("failed", failedCount);//No I18N
        summaryObj.addProperty("skipped", skippedCount);//No I18N
        summaryObj.addProperty("warning", warningCount);//No I18N
        summaryObj.addProperty("error", errorCount);//No I18N
        summaryObj.addProperty("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));//No I18N
        
        reportObj.add("summary", summaryObj);//No I18N
        
        // Add test results
        JsonObject resultsObj = new JsonObject();
        for (Map.Entry<String, TestResult> entry : RESULTS.entrySet()) {
            resultsObj.add(entry.getKey(), entry.getValue().toJsonObject());
        }
        
        reportObj.add("results", resultsObj);//No I18N
        
        // Pretty print the JSON
        return new GsonBuilder().setPrettyPrinting().create().toJson(reportObj);
    }
    
    /**
     * Save the JSON report to a file
     * 
     * @param directory Directory to save the report in (if null, uses default)
     * @return Path to the saved file, or null if save failed
     */
    public static String saveJsonReport(String directory) {
        String reportDir = directory != null ? directory : DEFAULT_REPORT_DIR;
        
        try {
            // Create the directory if it doesn't exist
            File dir = new File(reportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String filePath = reportDir + File.separator + JSON_REPORT_FILENAME;
            File reportFile = new File(filePath);
            
            // Generate and save the JSON report
            String jsonReport = generateJsonReport();
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write(jsonReport);
            }
            
            LOGGER.info("JSON report saved to " + reportFile.getAbsolutePath());
            return reportFile.getAbsolutePath();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving JSON report", e);//No I18N
            return null;
        }
    }
    
    /**
     * Generate an HTML report for better visualization
     * 
     * @param directory Directory to save the report in (if null, uses default)
     * @return Path to the saved file, or null if save failed
     */
    public static String generateHtmlReport(String directory) {
        String reportDir = directory != null ? directory : DEFAULT_REPORT_DIR;
        
        try {
            // Create the directory if it doesn't exist
            File dir = new File(reportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String filePath = reportDir + File.separator + HTML_REPORT_FILENAME;
            File reportFile = new File(filePath);
            
            // Build the HTML content
            StringBuilder html = new StringBuilder();
            
            // Start HTML document
            html.append("<!DOCTYPE html>\n");//No I18N
            html.append("<html lang=\"en\">\n");//No I18N
            
            // Head section
            html.append("<head>\n");//No I18N//No I18N
            html.append("  <meta charset=\"UTF-8\">\n");//NO I18N
            html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");//No I18N
            html.append("  <title>G.O.A.T Test Results</title>\n");//No I18N
            html.append("  <style>\n");//No I18N
            html.append("    body { font-family: Arial, sans-serif; margin: 0; padding: 0; background: #f5f5f5; color: #333; }\n");//No I18N
            html.append("    .container { max-width: 1200px; margin: 0 auto; padding: 20px; }\n");//No I18N
            html.append("    header { background: #2c3e50; color: white; padding: 20px; margin-bottom: 20px; }\n");//No I18N
            html.append("    h1 { margin: 0; }\n");//No I18N
            html.append("    .summary { display: flex; flex-wrap: wrap; margin-bottom: 20px; }\n");//No I18N
            html.append("    .summary-item { flex: 1; min-width: 150px; padding: 15px; margin: 5px; background: white; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); text-align: center; }\n");//No I18N
            html.append("    .summary-item h3 { margin-top: 0; color: #555; }\n");//No I18N
            html.append("    .summary-item p { font-size: 24px; font-weight: bold; margin: 10px 0; }\n");//No I18N
            html.append("    .passed { color: #27ae60; }\n");//No I18N
            html.append("    .failed { color: #e74c3c; }\n");//No I18N
            html.append("    .skipped { color: #f39c12; }\n");//No I18N
            html.append("    .warning { color: #f39c12; }\n");//No I18N
            html.append("    .error { color: #c0392b; }\n");//No I18N
            html.append("    table { width: 100%; border-collapse: collapse; background: white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");//No I18N
            html.append("    th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #ddd; }\n");//No I18N
            html.append("    th { background-color: #f2f2f2; }\n");//No I18N
            html.append("    tr:hover { background-color: #f5f5f5; }\n");//No I18N
            html.append("    .details { max-height: 300px; overflow-y: auto; white-space: pre-wrap; font-family: monospace; padding: 10px; background: #f8f8f8; border: 1px solid #ddd; }\n");//No I18N
            html.append("    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }\n");//No I18N
            html.append("    .badge-passed { background-color: #e8f5e9; color: #27ae60; }\n");//No I18N
            html.append("    .badge-failed { background-color: #ffeaea; color: #e74c3c; }\n");//No I18N
            html.append("    .badge-skipped { background-color: #fff8e1; color: #f39c12; }\n");//No I18N
            html.append("    .badge-warning { background-color: #fff8e1; color: #f39c12; }\n");//No I18N
            html.append("    .badge-error { background-color: #ffebee; color: #c0392b; }\n");//No I18N
            html.append("    .hidden { display: none; }\n");//No I18N
            html.append("    .expand-btn { cursor: pointer; background: none; border: none; color: #3498db; }\n");//No I18N
            html.append("  </style>\n");//No I18N
            html.append("</head>\n");//No I18N
            
            // Body section
            html.append("<body>\n");//No I18N
            html.append("  <header>\n");//No I18N
            html.append("    <div class=\"container\">\n");//No I18N
            html.append("      <h1>G.O.A.T Test Results</h1>\n");//No I18N
            html.append("    </div>\n");//No I18N
            html.append("  </header>\n");//No I18N
            
            html.append("  <div class=\"container\">\n");//No I18N
            
            // Summary section
            html.append("    <h2>Test Summary</h2>\n");//No I18N
            html.append("    <div class=\"summary\">\n");//No I18N
            html.append("      <div class=\"summary-item\">\n");//No I18N
            html.append("        <h3>Total Tests</h3>\n");//No I18N
            html.append("        <p>" + getTotalCount() + "</p>\n");//No I18N
            html.append("      </div>\n");//No I18N
            html.append("      <div class=\"summary-item\">\n");//No I18N
            html.append("        <h3>Passed</h3>\n");//No I18N
            html.append("        <p class=\"passed\">" + passedCount + "</p>\n");//No I18N
            html.append("      </div>\n");//No I18N
            html.append("      <div class=\"summary-item\">\n");//No I18N
            html.append("        <h3>Failed</h3>\n");//No I18N
            html.append("        <p class=\"failed\">" + failedCount + "</p>\n");//No I18N
            html.append("      </div>\n");//No I18N
            html.append("      <div class=\"summary-item\">\n");//No I18N
            html.append("        <h3>Skipped</h3>\n");//No I18N
            html.append("        <p class=\"skipped\">" + skippedCount + "</p>\n");//No I18N
            html.append("      </div>\n");//No I18N
            if (warningCount > 0 || errorCount > 0) {
                html.append("      <div class=\"summary-item\">\n");//No I18N
                html.append("        <h3>Warnings</h3>\n");//No I18N
                html.append("        <p class=\"warning\">" + warningCount + "</p>\n");//No I18N
                html.append("      </div>\n");//No I18N
                html.append("      <div class=\"summary-item\">\n");//No I18N
                html.append("        <h3>Errors</h3>\n");//No I18N
                html.append("        <p class=\"error\">" + errorCount + "</p>\n");//No I18N
            }
            html.append("    </div>\n");//No I18N
            
            // Results table
            html.append("    <h2>Test Details</h2>\n");//No I18N
            html.append("    <table>\n");//No I18N
            html.append("      <thead>\n");//No I18N
            html.append("        <tr>\n");//No I18N
            html.append("          <th>Test Case ID</th>\n");//No I18N
            html.append("          <th>Operation</th>\n");//No I18N
            html.append("          <th>Status</th>\n");//No I18N
            html.append("          <th>Execution Time</th>\n");//No I18N
            html.append("          <th>Details</th>\n");//No I18N
            html.append("        </tr>\n");//No I18N
            html.append("      </thead>\n");//No I18N
            html.append("      <tbody>\n");//No I18N
            
            // Add each test result as a row
            int index = 0;
            for (TestResult result : RESULTLIST) {
                String badgeClass = "badge-" + result.getStatus().toLowerCase();//No I18N
                
                html.append("        <tr>\n");//No I18N
                html.append("          <td>" + result.getTestCaseId() + "</td>\n");//No I18N
                html.append("          <td>" + result.getOperationType() + "</td>\n");//No I18N
                html.append("          <td><span class=\"badge " + badgeClass + "\">" + result.getStatus() + "</span></td>\n");//No I18N
                html.append("          <td>" + result.getExecutionTime() + " ms</td>\n");//No I18N
                html.append("          <td>\n");//No I18N
                html.append("            <button class=\"expand-btn\" onclick=\"toggleDetails(" + index + ")\">Show Details</button>\n");//No I18N
                html.append("            <div id=\"details-" + index + "\" class=\"details hidden\">\n");//No I18N
                
                // Add expected result if available
                if (result.getExpectedResult() != null && !result.getExpectedResult().isEmpty()) {
                    html.append("              <strong>Expected:</strong> " + escapeHtml(result.getExpectedResult()) + "\n");//No I18N
                }
                
                // Add actual result if available
                if (result.getActualResult() != null && !result.getActualResult().isEmpty()) {
                    html.append("              <strong>Actual:</strong> " + escapeHtml(result.getActualResult()) + "\n");//No I18N
                }
                
                // Add remarks if available
                if (result.getRemarks() != null && !result.getRemarks().isEmpty()) {
                    html.append("              <strong>Remarks:</strong>\n" + escapeHtml(result.getRemarks()) + "\n");//No I18N
                }
                
                html.append("            </div>\n");//No I18N
                html.append("          </td>\n");//No I18N
                html.append("        </tr>\n");//No I18N
                
                index++;
            }
            
            html.append("      </tbody>\n");//No I18N
            html.append("    </table>\n");//No I18N
            
            // Add timestamp
            html.append("    <p><em>Report generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</em></p>\n");//No I18N
            
            html.append("  </div>\n"); // End of container//No I18N
            
            // JavaScript for toggling details
            html.append("  <script>\n");//No I18N
            html.append("    function toggleDetails(index) {\n");//No I18N
            html.append("      const detailsElement = document.getElementById('details-' + index);\n");//No I18N
            html.append("      detailsElement.classList.toggle('hidden');\n");//No I18N
            html.append("      const button = detailsElement.previousElementSibling;\n");//No I18N
            html.append("      button.textContent = detailsElement.classList.contains('hidden') ? 'Show Details' : 'Hide Details';\n");//No I18N
            html.append("    }\n");//No I18N
            html.append("  </script>\n");//No I18N
            
            // End HTML document
            html.append("</body>\n");//No I18N
            html.append("</html>\n");//No I18N
            
            // Write HTML to file
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write(html.toString());
            }
            
            LOGGER.info("HTML report saved to " + reportFile.getAbsolutePath());//No I18N
            return reportFile.getAbsolutePath();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving HTML report", e);//No I18N
            return null;
        }
    }
    
    /**
     * Escape HTML special characters
     * 
     * @param input Input string
     * @return Escaped string
     */
    private static String escapeHtml(String input) {
        if (input == null){ return "";}
        return input.replace("&", "&amp;")//No I18N
                   .replace("<", "&lt;")//No I18N
                   .replace(">", "&gt;")//No I18N
                   .replace("\"", "&quot;")//No I18N
                   .replace("'", "&#39;");//No I18N
    }
    
    /**
     * Get failed test cases for rerun
     * 
     * @return JSON containing only failed test cases
     */
    public static JsonObject getFailedTestCases() {
        JsonObject failedTests = new JsonObject();
        
        for (Map.Entry<String, TestResult> entry : RESULTS.entrySet()) {
            if (TestResult.STATUS_FAILED.equals(entry.getValue().getStatus())) {
                failedTests.add(entry.getKey(), entry.getValue().toJsonObject());
            }
        }
        
        return failedTests;
    }
    
    /**
     * Save failed test cases to a file for rerun
     * 
     * @param directory Directory to save the file in (if null, uses default)
     * @return Path to the saved file, or null if save failed
     */
    public static String saveFailedTestCases(String directory) {
        String reportDir = directory != null ? directory : DEFAULT_REPORT_DIR;
        
        try {
            // Create the directory if it doesn't exist
            File dir = new File(reportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String filePath = reportDir + File.separator + "failed_tests.json";//No I18N
            File reportFile = new File(filePath);
            
            // Generate and save the JSON with failed tests only
            JsonObject failedTests = getFailedTestCases();
            String jsonFailedTests = new GsonBuilder().setPrettyPrinting().create().toJson(failedTests);
            
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write(jsonFailedTests);
            }
            
            LOGGER.info("Failed tests saved to " + reportFile.getAbsolutePath());//No I18N
            return reportFile.getAbsolutePath();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving failed tests", e);//No I18N
            return null;
        }
    }
}
