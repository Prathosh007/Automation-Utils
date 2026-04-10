package com.me;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.testcases.ApiCaseHandler;
import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Class for testing API endpoints
 */
public class ApiTester {
    private static final Logger LOGGER = LogManager.getLogger(ApiTester.class, LogManager.LOG_TYPE.FW);
    
    public static void main(String[] args) {
        ConsoleOut.println("\n====================================================");//NO I18N
        ConsoleOut.println("          G.O.A.T - API Test Execution               ");//NO I18N
        ConsoleOut.println("====================================================\n");//NO I18N
        
        try {
            if (args.length < 1) {
                ConsoleOut.println("Usage: java ApiTester <test_case_file>");//NO I18N
                System.exit(1);
            }
            
            String testFile = args[0];
            ConsoleOut.println("Reading test case from: " + testFile);//NO I18N
            
            // Read the test case file
            String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(testFile)));
            JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
            
            // Get first test case
            String testId = jsonObject.keySet().iterator().next();
            JsonObject testCase = jsonObject.getAsJsonObject(testId);
            
            ConsoleOut.println("\nExecuting test case: " + testId);//NO I18N
            ConsoleOut.println("Test description: " + testCase.get("expected_result").getAsString());//NO I18N
            ConsoleOut.println("\nExecution log will show detailed steps. Check framework logs for more details.");//NO I18N
            ConsoleOut.println("\n--- STARTING EXECUTION ---");//NO I18N
            
            // Get operations
            JsonArray operations = testCase.getAsJsonArray("operations");//NO I18N
            JsonObject operationJson = operations.get(0).getAsJsonObject();
            
            // Create operation object
            String operationType = operationJson.get("operation_type").getAsString();
            JsonObject parametersJson = operationJson.getAsJsonObject("parameters");//NO I18N
            
            // Convert parameters to a Map
            Map<String, String> parameters = new HashMap<>();
            for (String key : parametersJson.keySet()) {
                JsonElement element = parametersJson.get(key);
                
                // Handle different value types
                String value;
                if (element.isJsonPrimitive()) {
                    value = element.getAsString();
                } else {
                    // For non-primitive types like objects, convert to JSON string
                    value = element.toString();
                }
                parameters.put(key, value);
            }
            
            // Create the operation
            Operation operation = new Operation(operationType, parameters);
            
            // Execute the operation
            ConsoleOut.println("Executing API operation...");//NO I18N
            long startTime = System.currentTimeMillis();
            boolean result = ApiCaseHandler.executeOperation(operation);
            long endTime = System.currentTimeMillis();
            
            ConsoleOut.println("\n--- EXECUTION COMPLETE ---");//NO I18N
            ConsoleOut.println("Execution time: " + (endTime - startTime) + " ms");//NO I18N
            
            // Check result
            if (result) {
                ConsoleOut.println("\n=== API TEST PASSED ===");//NO I18N
                
                // Truncate the response if it's too long
                String response = operation.getOutputValue();
                String displayResponse = response.length() > 500 ? 
                    response.substring(0, 497) + "..." : 
                    response;
                ConsoleOut.println("Response: " + displayResponse);//NO I18N
                
                // Display cookies
                String cookiesJson = operation.getParameter("_cookies");//NO I18N
                if (cookiesJson != null && !cookiesJson.isEmpty() && !cookiesJson.equals("{}")) {
                    ConsoleOut.println("\n=== COOKIES ===");//NO I18N
                    try {
                        // Try to parse and format cookies
                        JsonObject cookiesObj = JsonParser.parseString(cookiesJson).getAsJsonObject();
                        for (String cookieName : cookiesObj.keySet()) {
                            ConsoleOut.println(cookieName + " = " + cookiesObj.get(cookieName).getAsString());
                        }
                    } catch (Exception e) {
                        // If can't parse JSON, just print raw
                        ConsoleOut.println(cookiesJson);
                    }
                } else {
                    ConsoleOut.println("\nNo cookies received.");//NO I18N
                }
            } else {
                ConsoleOut.println("\n=== API TEST FAILED ===");//NO I18N
                ConsoleOut.println("See logs for detailed error information.");//NO I18N
                
                // Still try to display the response for debugging
                String response = operation.getOutputValue();
                if (response != null && !response.isEmpty()) {
                    String displayResponse = response.length() > 500 ? 
                        response.substring(0, 497) + "..." : 
                        response;
                    ConsoleOut.println("\nError Response: " + displayResponse);//NO I18N
                }
                
                System.exit(1);
            }
            
            ConsoleOut.println("\nDetailed logs available in logs/framework directory");//NO I18N
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing API test", e);//NO I18N
            ConsoleOut.println("\n=== API TEST EXECUTION ERROR ===");//NO I18N
            ConsoleOut.println("Error: " + e.getMessage());//NO I18N
            LOGGER.log(Level.SEVERE, "Exception details:", e);//NO I18N
            System.exit(1);
        }
    }

    /**
     * Test an API endpoint
     * 
     * @param testCase The API test case
     * @return Test result
     */
    public TestResult testApi(ApiTestCase testCase) {
        LOGGER.info("Testing API: " + testCase.getEndpoint()); //NO I18N
        
        TestResult result = new TestResult();
        result.setTestCaseId(testCase.getId());
        result.setDescription(testCase.getDescription());
        
        try {
            // Create URL from endpoint
            URL url = new URL(testCase.getEndpoint());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod(testCase.getMethod());
            connection.setConnectTimeout(30000); // 30 seconds timeout
            connection.setReadTimeout(60000);    // 60 seconds read timeout
            
            // Add headers
            for (Map.Entry<String, String> header : testCase.getHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            
            // Handle request body for POST/PUT requests
            if (testCase.getBody() != null && !testCase.getBody().isEmpty() && 
                (testCase.getMethod().equalsIgnoreCase("POST") || testCase.getMethod().equalsIgnoreCase("PUT"))) { //NO I18N
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = testCase.getBody().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }
            
            // Get response code
            int responseCode = connection.getResponseCode();
            
            // Read response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode < 400 ? connection.getInputStream() : connection.getErrorStream(), 
                            StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine);
                }
            }
            
            // Set result properties
            result.setStatus(responseCode >= 200 && responseCode < 300 ? TestResult.STATUS_PASSED : TestResult.STATUS_FAILED); 
            result.setActualResult(response.toString());
            
            StringBuilder remarks = new StringBuilder();
            remarks.append("HTTP Method: ").append(testCase.getMethod()).append("\n"); //NO I18N
            remarks.append("Response Code: ").append(responseCode).append("\n"); //NO I18N
            remarks.append("Response Body: ").append(response.toString()).append("\n"); //NO I18N
            
            // Check if response contains expected content
            if (testCase.getExpectedContent() != null && !testCase.getExpectedContent().isEmpty()) {
                boolean contentMatch = response.toString().contains(testCase.getExpectedContent());
                remarks.append("Expected Content Found: ").append(contentMatch).append("\n"); //NO I18N
                
                // Override status based on content expectation
                if (!contentMatch) {
                    result.setStatus(TestResult.STATUS_FAILED); 
                    remarks.append("Expected to find: ").append(testCase.getExpectedContent()).append("\n"); //NO I18N
                }
            }
            
            result.setRemarks(remarks.toString());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error testing API", e); //NO I18N
            result.setStatus(TestResult.STATUS_ERROR); 
            result.setActualResult("Exception: " + e.getMessage()); //NO I18N
            
            StringBuilder remarks = new StringBuilder();
            remarks.append("Error: ").append(e.getMessage()).append("\n"); //NO I18N
            remarks.append("Stack Trace: \n"); //NO I18N
            for (StackTraceElement element : e.getStackTrace()) {
                remarks.append("    at ").append(element.toString()).append("\n"); //NO I18N
            }
            result.setRemarks(remarks.toString());
        }
        
        return result;
    }

    /**
     * Create an ApiTestCase from an Operation
     * 
     * @param op The operation containing API parameters
     * @return An ApiTestCase object
     */
    public static ApiTestCase createFromOperation(Operation op) {
        ApiTestCase testCase = new ApiTestCase();
        
        testCase.setId(op.getParameter("testcase_id") != null ? op.getParameter("testcase_id") : "API_TEST"); //NO I18N
        testCase.setDescription(op.getParameter("description")); //NO I18N
        
        // Build endpoint URL from connection and apiToHit
        String connection = op.getParameter("connection"); //NO I18N
        String apiToHit = op.getParameter("apiToHit"); //NO I18N
        String protocol = connection.contains(":") ? "https" : "http"; //NO I18N
        
        if (!connection.startsWith("http")) { //NO I18N
            testCase.setEndpoint(protocol + "://" + connection + apiToHit); //NO I18N
        } else {
            testCase.setEndpoint(connection + apiToHit); 
        }
        
        testCase.setMethod(op.getParameter("http_method")); //NO I18N
        testCase.setBody(op.getParameter("payload")); //NO I18N
        testCase.setExpectedContent(op.getParameter("expected_response")); //NO I18N
        
        // Set headers
        if (op.getParameter("content_type") != null) { //NO I18N
            testCase.addHeader("Content-Type", op.getParameter("content_type")); //NO I18N
        }
        
        if (op.getParameter("accepts_type") != null) { //NO I18N
            testCase.addHeader("Accept", op.getParameter("accepts_type")); //NO I18N
        }
        
        return testCase;
    }
}
