package com.me;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;

/**
 * Generator for HTML reports from test results
 */
public class HtmlReportGenerator {
    private static final Logger LOGGER = LogManager.getLogger(HtmlReportGenerator.class, LogManager.LOG_TYPE.FW);

    /**
     * Generate an HTML report from test results
     * 
     * @param results List of test results
     * @param outputPath Path to save the HTML report
     * @param reportTitle Title for the report
     * @return True if report was generated successfully, false otherwise
     */
    public static boolean generateReport(List<TestResult> results, String outputPath, String reportTitle) {
        if (results == null || results.isEmpty()) {
            LOGGER.warning("Cannot generate report with no results");//No I18N
            return false;
        }
        
        if (outputPath == null || outputPath.isEmpty()) {
            LOGGER.warning("Output path is required");//No I18N
            return false;
        }
        
        // Create parent directory if it doesn't exist
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // Count results by status
        int passedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int warningCount = 0;
        int errorCount = 0;
        long totalExecutionTime = 0; // Track total execution time
        
        for (TestResult result : results) {
            if (result == null || result.getStatus() == null) {
                continue;
            }
            
            // Add to total execution time
            totalExecutionTime += result.getExecutionTime();
            
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
        
        try (FileWriter writer = new FileWriter(outputPath)) {
            // Start HTML document
            writer.write("<!DOCTYPE html>\n");//No I18N
            writer.write("<html lang=\"en\">\n");//No I18N
            
            // Head section with styles
            writer.write("<head>\n");//No I18N
            writer.write("  <meta charset=\"UTF-8\">\n");//No I18N
            writer.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");//No I18N
            writer.write("  <title>" + (reportTitle != null ? reportTitle : "G.O.A.T Test Results") + "</title>\n");//No I18N
            writer.write(getStylesSection());
            writer.write("</head>\n");//No I18N
            
            // Body section
            writer.write("<body>\n");//No I18N
            writer.write("  <header>\n");//No I18N
            writer.write("    <div class=\"container\">\n");//No I18N
            writer.write("      <h1>" + (reportTitle != null ? reportTitle : "G.O.A.T Test Results") + "</h1>\n");//No I18N
            writer.write("    </div>\n");//No I18N
            writer.write("  </header>\n");//No I18N
            
            writer.write("  <div class=\"container\">\n");//No I18N
            
            // Summary section - pass totalExecutionTime as parameter
            writer.write(getSummarySection(results.size(), passedCount, failedCount, skippedCount, warningCount, errorCount, totalExecutionTime));
            
            // Results table
            writer.write("    <h2>Test Details</h2>\n");//No I18N
            writer.write("    <div class=\"filters\">\n");//No I18N
            writer.write("      <button onclick=\"filterByStatus('all')\" class=\"filter-btn active\">All</button>\n");//No I18N
            writer.write("      <button onclick=\"filterByStatus('passed')\" class=\"filter-btn\">Passed</button>\n");//No I18N
            writer.write("      <button onclick=\"filterByStatus('failed')\" class=\"filter-btn\">Failed</button>\n");//No I18N
            if (skippedCount > 0) {
                writer.write("      <button onclick=\"filterByStatus('skipped')\" class=\"filter-btn\">Skipped</button>\n");//No I18N
            }
            if (warningCount > 0) {
                writer.write("      <button onclick=\"filterByStatus('warning')\" class=\"filter-btn\">Warnings</button>\n");//No I18N
            }
            if (errorCount > 0) {
                writer.write("      <button onclick=\"filterByStatus('error')\" class=\"filter-btn\">Errors</button>\n");//No I18N
            }
            writer.write("    </div>\n");//No I18N
            writer.write(generateResultsTable(results));
            
            // Add timestamp
            writer.write("    <p><em>Report generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</em></p>\n");//No I18N
            
            writer.write("  </div>\n"); // End of container //No I18N
            
            // JavaScript for toggling details and filtering
            writer.write(getJavascriptSection());
            
            // End HTML document
            writer.write("</body>\n");//No I18N
            writer.write("</html>\n");//No I18N
            
            LOGGER.info("HTML report generated successfully at " + outputPath);//No I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error generating HTML report", e);//No I18N
            return false;
        }
    }
    
    /**
     * Get the CSS styles for the report
     * 
     * @return HTML string with styles
     */
    private static String getStylesSection() {
        return "  <style>\n" + //No I18N
               "    body { font-family: Arial, sans-serif; margin: 0; padding: 0; background: #f5f5f5; color: #333; }\n" + //No I18N
               "    .container { max-width: 1200px; margin: 0 auto; padding: 20px; }\n" + //No I18N
               "    header { background: #2c3e50; color: white; padding: 20px; margin-bottom: 20px; }\n" + //No I18N
               "    h1 { margin: 0; }\n" + //No I18N
               "    .summary { display: flex; flex-wrap: wrap; margin-bottom: 20px; }\n" + //No I18N
               "    .summary-item { flex: 1; min-width: 150px; padding: 15px; margin: 5px; background: white; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); text-align: center; }\n" + //No I18N
               "    .summary-item h3 { margin-top: 0; color: #555; }\n" + //No I18N
               "    .summary-item p { font-size: 24px; font-weight: bold; margin: 10px 0; }\n" + //No I18N
               "    .passed { color: #27ae60; }\n" + //No I18N
               "    .failed { color: #e74c3c; }\n" + //No I18N
               "    .skipped { color: #f39c12; }\n" + //No I18N
               "    .warning { color: #f39c12; }\n" + //No I18N
               "    .error { color: #c0392b; }\n" + //No I18N
               "    table { width: 100%; border-collapse: collapse; background: white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n" + //No I18N
               "    th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #ddd; }\n" + //No I18N
               "    th { background-color: #f2f2f2; }\n" + //No I18N
               "    tr:hover { background-color: #f5f5f5; }\n" + //No I18N
               "    .details { max-height: 300px; overflow-y: auto; white-space: pre-wrap; font-family: monospace; padding: 10px; background: #f8f8f8; border: 1px solid #ddd; }\n" + //No I18N
               "    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }\n" + //No I18N
               "    .badge-passed { background-color: #e8f5e9; color: #27ae60; }\n" + //No I18N
               "    .badge-failed { background-color: #ffeaea; color: #e74c3c; }\n" + //No I18N
               "    .badge-skipped { background-color: #fff8e1; color: #f39c12; }\n" + //No I18N
               "    .badge-warning { background-color: #fff8e1; color: #f39c12; }\n" + //No I18N
               "    .badge-error { background-color: #ffebee; color: #c0392b; }\n" + //No I18N
               "    .hidden { display: none; }\n" + //No I18N
               "    .expand-btn { cursor: pointer; background: none; border: none; color: #3498db; }\n" + //No I18N
               "    .filters { margin-bottom: 20px; }\n" + //No I18N
               "    .filter-btn { padding: 8px 16px; margin-right: 5px; background: #f2f2f2; border: 1px solid #ddd; cursor: pointer; }\n" + //No I18N
               "    .filter-btn:hover { background: #e9e9e9; }\n" + //No I18N
               "    .filter-btn.active { background: #2c3e50; color: white; }\n" + //No I18N
               "  </style>\n"; //No I18N
    }
    
    /**
     * Get the summary section HTML
     * 
     * @param totalCount Total number of tests
     * @param passedCount Number of passed tests
     * @param failedCount Number of failed tests
     * @param skippedCount Number of skipped tests
     * @param warningCount Number of warnings
     * @param errorCount Number of errors
     * @param totalExecutionTime Total execution time for all tests
     * @return HTML string for summary section
     */
    private static String getSummarySection(int totalCount, int passedCount, int failedCount, int skippedCount, int warningCount, int errorCount, long totalExecutionTime) {
        StringBuilder summary = new StringBuilder();
        summary.append("    <h2>Test Summary</h2>\n");//No I18N
        summary.append("    <div class=\"summary\">\n");//No I18N
        summary.append("      <div class=\"summary-item\">\n");//No I18N
        summary.append("        <h3>Total Tests</h3>\n");//No I18N
        summary.append("        <p>" + totalCount + "</p>\n");//No I18N
        summary.append("      </div>\n");//No I18N
        summary.append("      <div class=\"summary-item\">\n");//No I18N
        summary.append("        <h3>Passed</h3>\n");//No I18N
        summary.append("        <p class=\"passed\">" + passedCount + "</p>\n");//No I18N
        summary.append("      </div>\n");//No I18N
        summary.append("      <div class=\"summary-item\">\n");//No I18N
        summary.append("        <h3>Failed</h3>\n");//No I18N
        summary.append("        <p class=\"failed\">" + failedCount + "</p>\n");//No I18N
        summary.append("      </div>\n");//No I18N
        
        if (skippedCount > 0) {
            summary.append("      <div class=\"summary-item\">\n");//No I18N
            summary.append("        <h3>Skipped</h3>\n");//No I18N
            summary.append("        <p class=\"skipped\">" + skippedCount + "</p>\n");//No I18N
            summary.append("      </div>\n");//No I18N
        }
        
        if (warningCount > 0) {
            summary.append("      <div class=\"summary-item\">\n");//No I18N
            summary.append("        <h3>Warnings</h3>\n");//No I18N
            summary.append("        <p class=\"warning\">" + warningCount + "</p>\n");//No I18N
            summary.append("      </div>\n");//No I18N
        }
        
        if (errorCount > 0) {
            summary.append("      <div class=\"summary-item\">\n");//No I18N
            summary.append("        <h3>Errors</h3>\n");//No I18N
            summary.append("        <p class=\"error\">" + errorCount + "</p>\n");//No I18N
            summary.append("      </div>\n");//No I18N
        }
        
        // Add success rate
        float successRate = totalCount > 0 ? (float) passedCount / totalCount * 100 : 0;
        summary.append("      <div class=\"summary-item\">\n");//No I18N
        summary.append("        <h3>Success Rate</h3>\n");//No I18N
        summary.append("        <p" + (successRate >= 90 ? " class=\"passed\"" : (successRate >= 60 ? " class=\"warning\"" : " class=\"failed\"")) + ">" + //NO I18N
                   String.format("%.2f", successRate) + "%</p>\n");//No I18N
        summary.append("      </div>\n");//No I18N
        
        // Add total execution time
        summary.append("      <div class=\"summary-item\">\n");//No I18N
        summary.append("        <h3>Total Execution Time</h3>\n");//No I18N
        
        // Format the execution time nicely depending on the duration
        String formattedTime;
        if (totalExecutionTime < 1000) {
            formattedTime = totalExecutionTime + " ms";//NO I18N
        } else if (totalExecutionTime < 60000) {
            formattedTime = String.format("%.2f seconds", totalExecutionTime / 1000.0);//NO I18N
        } else {
            long minutes = totalExecutionTime / 60000;
            long seconds = (totalExecutionTime % 60000) / 1000;
            formattedTime = minutes + " min " + seconds + " sec";//NO I18N
        }
        
        summary.append("        <p>" + formattedTime + "</p>\n");//No I18N
        summary.append("      </div>\n");//No I18N
        
        summary.append("    </div>\n");//No I18N
        return summary.toString();
    }
    
    /**
     * Get the JavaScript section for interactivity
     * 
     * @return HTML string with JavaScript
     */
    private static String getJavascriptSection() {
        return "  <script>\n" + //No I18N
               "    function toggleDetails(index) {\n" + //No I18N
               "      const detailsElement = document.getElementById('details-' + index);\n" + //No I18N
               "      detailsElement.classList.toggle('hidden');\n" + //No I18N
               "      const button = detailsElement.previousElementSibling;\n" + //No I18N
               "      button.textContent = detailsElement.classList.contains('hidden') ? 'Show Details' : 'Hide Details';\n" + //No I18N
               "    }\n" + //No I18N
               "\n" + //No I18N
               "    function filterByStatus(status) {\n" + //No I18N
               "      // Update active button\n" + //No I18N
               "      const buttons = document.querySelectorAll('.filter-btn');\n" + //No I18N
               "      buttons.forEach(button => button.classList.remove('active'));\n" + //No I18N
               "      event.target.classList.add('active');\n" + //No I18N
               "      \n" + //No I18N
               "      // Filter rows\n" + //No I18N
               "      const rows = document.querySelectorAll('.result-row');\n" + //No I18N
               "      rows.forEach(row => {\n" + //No I18N
               "        if (status === 'all' || row.classList.contains(status)) {\n" + //No I18N
               "          row.style.display = '';\n" + //No I18N
               "        } else {\n" + //No I18N
               "          row.style.display = 'none';\n" + //No I18N
               "        }\n" + //No I18N
               "      });\n" + //No I18N
               "    }\n" + //No I18N
               "  </script>\n"; //No I18N
    }
    /**
     * Escape HTML special characters
     * 
     * @param input Input string
     * @return Escaped string
     */
    private static String escapeHtml(String input) {
        if (input == null) {
            return "";//No I18N
        }
        return input.replace("&", "&amp;")//No I18N
                   .replace("<", "&lt;")//No I18N
                   .replace(">", "&gt;")//No I18N
                   .replace("\"", "&quot;")//No I18N
                   .replace("'", "&#39;");//No I18N
    }

    /**
     * Main method for command-line execution
     * 
     * @param args Command line args: inputJsonFile outputHtmlFile [reportTitle]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            ConsoleOut.println("Usage: java com.me.HtmlReportGenerator <input_json_file> <output_html_file> [report_title]");//No I18N
            System.exit(1);
        }
        
        String inputFile = args[0];
        String outputFile = args[1];
        String reportTitle = args.length > 2 ? args[2] : "G.O.A.T Test Results";//No I18N
        
        ConsoleOut.println("G.O.A.T HTML Report Generator");//No I18N
        ConsoleOut.println("============================");//No I18N
        ConsoleOut.println("Input file: " + inputFile);//No I18N
        ConsoleOut.println("Output file: " + outputFile);//No I18N
        ConsoleOut.println("Report title: " + reportTitle);//No I18N
        ConsoleOut.println("");
        
        try {
            // Check if input file exists
            File input = new File(inputFile);
            if (!input.exists()) {
                ConsoleOut.println("Error: Input file does not exist: " + inputFile);//No I18N
                System.exit(1);
            }
            
            // Create output directory if it doesn't exist
            File outputFileObj = new File(outputFile);
            File parentDir = outputFileObj.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Read the JSON results from file
            String jsonContent = new String(Files.readAllBytes(Paths.get(inputFile)));
            
            // Parse the JSON and convert to test results
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            List<TestResult> results = new ArrayList<>();
            
            // Process each test case result
            for (String testCaseId : jsonObject.keySet()) {
                JsonObject resultJson = jsonObject.getAsJsonObject(testCaseId);
                
                TestResult result = new TestResult();
                result.setTestCaseId(testCaseId);
                
                // Extract fields from the JSON if they exist
                if (resultJson.has("operation_type")) {//No I18N
                    result.setOperationType(resultJson.get("operation_type").getAsString());//No I18N
                }
                
                if (resultJson.has("status")) {//No I18N
                    result.setStatus(resultJson.get("status").getAsString());//No I18N
                } else {
                    result.setStatus(TestResult.STATUS_SKIPPED);
                }
                
                if (resultJson.has("expected_result")) {//No I18N
                    result.setExpectedResult(resultJson.get("expected_result").getAsString());//No I18N
                }
                
                if (resultJson.has("actual_result")) {//No I18N
                    result.setActualResult(resultJson.get("actual_result").getAsString());//No I18N
                }
                
                if (resultJson.has("remarks")) {//No I18N
                    result.setRemarks(resultJson.get("remarks").getAsString());//No I18N
                }
                
                if (resultJson.has("execution_time")) {//No I18N
                    result.setExecutionTime(resultJson.get("execution_time").getAsLong());//No I18N
                }
                
                if (resultJson.has("timestamp")) {//No I18N
                    result.setTimestamp(resultJson.get("timestamp").getAsString());//No I18N
                }
                
                results.add(result);
            }
            
            // Generate HTML report
            if (generateReport(results, outputFile, reportTitle)) {
                ConsoleOut.println("HTML report generated successfully: " + outputFile);//No I18N
            } else {
                ConsoleOut.println("Failed to generate HTML report");//No I18N
                System.exit(1);
            }
            
        } catch (Exception e) {
            ConsoleOut.println("Error generating HTML report: " + e.getMessage());//No I18N
            LOGGER.log(Level.SEVERE, "Error generating HTML report", e);
            System.exit(1);
        }
    }

    private static String generateResultsTable(List<TestResult> results) {
        StringBuilder table = new StringBuilder();
        
        table.append("    <table id=\"results-table\">\n");//No I18N
        table.append("      <thead>\n");//No I18N
        table.append("        <tr>\n");//No I18N
        table.append("          <th>Test Case ID</th>\n");//No I18N
        table.append("          <th>Description</th>\n"); //No I18N
        table.append("          <th>Test Steps</th>\n"); // Changed from "Operation" to "Test Steps"//No I18N
        table.append("          <th>Expected Result</th>\n");//No I18N
        table.append("          <th>Status</th>\n");//No I18N
        table.append("          <th>Execution Time</th>\n");//No I18N
        table.append("          <th>Details</th>\n");//No I18N
        table.append("        </tr>\n");//No I18N
        table.append("      </thead>\n");//No I18N
        table.append("      <tbody>\n");//No I18N
        
        // Add each test result as a row
        int index = 0;
        for (TestResult result : results) {
            String statusClass = result.getStatus().toLowerCase();
            String badgeClass = "badge-" + statusClass;//No I18N
            
            table.append("        <tr class=\"result-row " + statusClass + "\">\n");//No I18N
            table.append("          <td>" + result.getTestCaseId() + "</td>\n");//No I18N
            table.append("          <td>" + (result.getDescription() != null ? escapeHtml(result.getDescription()) : "") + "</td>\n");//No I18N
            
            // Generate and display test steps instead of operation type
            table.append("          <td>" + generateTestSteps(result) + "</td>\n");//No I18N
            
            table.append("          <td>" + (result.getExpectedResult() != null ? escapeHtml(result.getExpectedResult()) : "") + "</td>\n");//No I18N
            table.append("          <td><span class=\"badge " + badgeClass + "\">" + result.getStatus() + "</span></td>\n");//No I18N
            table.append("          <td>" + result.getExecutionTime() + " ms</td>\n");//No I18N
            table.append("          <td>\n");//No I18N
            table.append("            <button class=\"expand-btn\" onclick=\"toggleDetails(" + index + ")\">Show Details</button>\n");//No I18N
            table.append("            <div id=\"details-" + index + "\" class=\"details hidden\">\n");//No I18N
            
            // Add expected result if available (still keep this in details for consistency)
            if (result.getExpectedResult() != null && !result.getExpectedResult().isEmpty()) {
                table.append("              <strong>Expected:</strong> " + escapeHtml(result.getExpectedResult()) + "\n");//No I18N
            }
            
            // Add actual result if available
            if (result.getActualResult() != null && !result.getActualResult().isEmpty()) {
                table.append("              <strong>Actual:</strong> " + escapeHtml(result.getActualResult()) + "\n");//No I18N
            }
            
            // Add remarks if available
            if (result.getRemarks() != null && !result.getRemarks().isEmpty()) {
                table.append("              <strong>Remarks:</strong>\n" + escapeHtml(result.getRemarks()) + "\n");//No I18N
            }
            
            table.append("            </div>\n");//No I18N
            table.append("          </td>\n");//No I18N
            table.append("        </tr>\n");//No I18N
            
            index++;
        }
        
        table.append("      </tbody>\n");//No I18N
        table.append("    </table>\n");//No I18N
        
        return table.toString();
    }

    /**
     * Generate human-readable test steps from a test result's operations
     * 
     * @param result The test result containing operations
     * @return HTML string with numbered test steps
     */
    private static String generateTestSteps(TestResult result) {
        if (result.getRemarks() == null || result.getRemarks().isEmpty()) {
            return "<em>No test steps available</em>";//No I18N
        }
        
        StringBuilder steps = new StringBuilder();
        steps.append("<ol style=\"margin: 0; padding-left: 20px;\">\n");//No I18N
        
        // Parse operations from the remarks field
        // Example format: "Operation 1: run_command - PASSED (100 ms)"
        String[] remarkLines = result.getRemarks().split("\n");
        int stepNumber = 1;
        
        for (String line : remarkLines) {
            if (line.startsWith("Operation ")) {
                String operationDescription = generateStepDescription(line, result);
                if (operationDescription != null) {
                    steps.append("  <li>").append(escapeHtml(operationDescription)).append("</li>\n");//No I18N
                    stepNumber++;
                }
            }
        }
        
        // If we couldn't extract steps, just show the operation type
        if (stepNumber == 1 && result.getOperationType() != null) {
            steps.append("  <li>").append(escapeHtml("Execute " + result.getOperationType())).append("</li>\n");//No I18N
        }
        
        steps.append("</ol>");//No I18N
        return steps.toString();
    }

    /**
     * Generate a human-readable description for a single operation
     * 
     * @param operationLine The line from remarks containing operation info
     * @param result The test result containing additional details
     * @return A human-readable description of the operation step
     */
    private static String generateStepDescription(String operationLine, TestResult result) {
        // Parse operation type from line like "Operation 1: run_command - PASSED (100 ms)"
        String[] parts = operationLine.split(":");
        if (parts.length < 2) {return null;}
        
        String operationInfo = parts[1].trim();
        String operationType = operationInfo.split(" - ")[0].trim();
        
        // Extract parameters from remarks or details
        String remarks = result.getRemarks();
        
        switch (operationType.toLowerCase()) {
            case "run_command"://No I18N
            // Try to extract command from remarks
            String command = extractParameter(remarks, "command_to_run");//No I18N
            if (command != null) {
                if (command.toLowerCase().startsWith("start ")) {//No I18N
                String appName = command.substring(6).trim();
                return "Run " + appName + " by command \"" + command + "\" in cmd";//No I18N
                } else {
                return "Execute command: \"" + command + "\"";//No I18N
                }
            }
            return "Execute system command";//No I18N
            
            case "task_manager"://No I18N
            // Extract action and process name
            String action = extractParameter(remarks, "action");//No I18N
            String processName = extractParameter(remarks, "process_name");//No I18N
            String expectation = extractParameter(remarks, "expect");//No I18N
            
            if (action != null && processName != null) {
                if (action.equals("verify_process")) {//No I18N
                boolean expectRunning = expectation == null || !expectation.equalsIgnoreCase("processNotRunning");//No I18N
                return "Check " + processName + (expectRunning ? //No I18N
                       " is successfully running in task manager" : //No I18N
                       " is not running in task manager");//No I18N
                } else if (action.equals("kill_process")) {//No I18N
                return "Kill process " + processName + " in task manager";//No I18N
                }
            }
            return "Perform task manager operation";//No I18N
            
            case "service_actions"://No I18N
            // Extract service name and action
            String serviceName = extractParameter(remarks, "service_name");//No I18N
            String serviceAction = extractParameter(remarks, "action");//No I18N
            String serviceExpect = extractParameter(remarks, "expect");//No I18N
            
            if (serviceName != null && serviceAction != null) {
                switch (serviceAction) {
                case "start"://No I18N
                    return "Start the " + serviceName + " service";//No I18N
                case "stop"://No I18N
                    return "Stop the " + serviceName + " service";//No I18N
                case "restart"://No I18N
                    return "Restart the " + serviceName + " service";//No I18N
                case "status"://No I18N
                    boolean expectRunning = serviceExpect == null || !serviceExpect.equalsIgnoreCase("serviceStopped");//No I18N
                    return "Verify " + serviceName + " service is " + //No I18N
                       (expectRunning ? "running" : "stopped");//No I18N
                }
            }
            return "Perform service operation";//No I18N
            
            case "check_presence"://No I18N
            String filePath = extractParameter(remarks, "file_path");//No I18N
            String filename = extractParameter(remarks, "filename");//No I18N
            
            if (filePath != null && filename != null) {
                return "Check file " + filename + " exists in " + filePath;//No I18N
            }
            return "Check file exists";//No I18N
            
            case "verify_absence"://No I18N
            String absFilePath = extractParameter(remarks, "file_path");//No I18N
            String absFilename = extractParameter(remarks, "filename");//No I18N
            
            if (absFilePath != null && absFilename != null) {
                return "Verify file " + absFilename + " does not exist in " + absFilePath;//No I18N
            }
            return "Verify file does not exist";//No I18N
            
            case "value_should_be_present"://No I18N
            String valueFilePath = extractParameter(remarks, "file_path");//No I18N
            String valueFilename = extractParameter(remarks, "filename");//No I18N
            String value = extractParameter(remarks, "value");//No I18N
            
            if (valueFilename != null && value != null) {
                return "Verify file " + valueFilename + " contains value: " + value;//No I18N
            }
            return "Check value exists in file";//No I18N
            
            case "exe_install"://No I18N
            String productName = extractParameter(remarks, "product_name");//No I18N
            if (productName != null) {
                return "Install " + productName + " application";//No I18N
            }
            return "Install application";//No I18N
            
            case "file_edit"://No I18N
            String editAction = extractParameter(remarks, "action");//No I18N
            String editFilename = extractParameter(remarks, "filename");//No I18N
            
            if (editAction != null && editFilename != null) {
                return "Edit file " + editFilename + " (" + editAction + " operation)";//No I18N
            }
            return "Edit file";//No I18N
            
            case "api_case"://No I18N
            String apiEndpoint = extractParameter(remarks, "apiToHit");//No I18N
            String method = extractParameter(remarks, "http_method");//No I18N
            
            if (apiEndpoint != null && method != null) {
                return "Make " + method + " request to " + apiEndpoint;//No I18N
            }
            return "Execute API request";//No I18N
            
            default:
            return "Execute " + operationType;//No I18N
        }
    }

    /**
     * Extract a parameter value from remarks text
     * 
     * @param remarks The remarks text to search in
     * @param paramName The parameter name to look for
     * @return The parameter value, or null if not found
     */
    private static String extractParameter(String remarks, String paramName) {
        if (remarks == null) {return null;}
        
        // Try to extract parameter from lines like "param_name: value"
        String pattern = "\\b" + paramName + "\\s*[=:]\\s*([^\\n]+)";//No I18N
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(remarks);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Also try with quotes: "param_name": "value"
        pattern = "\"" + paramName + "\"\\s*[=:]\\s*\"([^\"]+)\"";//No I18N
        regex = java.util.regex.Pattern.compile(pattern);
        matcher = regex.matcher(remarks);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
}
