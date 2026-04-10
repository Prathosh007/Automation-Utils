package com.me.api.service;

import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.me.Operation;
import com.me.api.util.TestCaseStatusUtil;
import com.me.util.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.api.bridge.GoatFrameworkBridge;
import com.me.api.error.ResourceNotFoundException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static com.me.testcases.ServerUtils.checkServerHome;
import static com.me.util.GOATCommonConstants.SERVERHOMEMAP;

/**
 * Service for test execution operations
 */
@Service
public class TestExecutionService {
    
    private static final Logger LOGGER = LogManager.getLogger(TestExecutionService.class.getName(), LogManager.LOG_TYPE.FW);

    
    @Autowired
    private GoatFrameworkBridge frameworkBridge;
    
    @Autowired
    private TestCaseService testCaseService;
    
    /**
     * Execute a test case by ID
     * 
     * @param testCaseId Test case ID
     * @return Execution result
     * @throws ResourceNotFoundException if test case not found
     */
    public JsonObject executeTestCase(String testUniqueId,String testCaseId) {

        LOGGER.info("Executing test case with ID: " + testCaseId);
        System.setProperty("unique.test.id",testCaseId);
        TestCaseStatusUtil.addResultInJson(testUniqueId,testCaseId,"In_Progress","","");

        // Check Server Home
        boolean isServerHomeSet = checkServerHome(testUniqueId);
        if (isServerHomeSet){
            LOGGER.info("Server home is set to testid of : "+testUniqueId+" with the value of :" + SERVERHOMEMAP.get(testUniqueId+"_ServerHome"));
        }

        // Get the test case first to verify it exists
        String testCaseJson = frameworkBridge.getTestCase(testCaseId);
        if (testCaseJson == null) {
            throw new ResourceNotFoundException("Test case", testCaseId);
        }
        
        try {
            // Execute the test case
            String resultJson = frameworkBridge.executeTestCase(testCaseJson);
            
            // Parse and return the result
            JsonObject result = safeParseJson(resultJson, testCaseId);
            LOGGER.log(Level.INFO, "Test execution result: " + result);

            moveResultsData(testUniqueId,testCaseId,result);

            LOGGER.log(Level.INFO,"Adding status in JSON");
            TestCaseStatusUtil.addResultInJson(testUniqueId,testCaseId,result.getAsJsonObject(testCaseId).get("status").getAsString(),result.getAsJsonObject(testCaseId).get("remarks").getAsString(),result.getAsJsonObject(testCaseId).get("error").getAsString());
            LOGGER.log(Level.INFO,"Added status in JSON successfully");
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing test case: " + testCaseId, e);
            
            // Create a result object with error details
            JsonObject errorResult = new JsonObject();
            JsonObject details = new JsonObject();
            details.addProperty("testcase_id", testCaseId);
            details.addProperty("status", "ERROR");
            details.addProperty("remarks", "Error executing test case: " + e.getMessage());
            errorResult.add(testCaseId, details);
            
            return errorResult;
        }
    }
    
    /**
     * Execute test case from JSON directly
     * 
     * @param testCaseJson Test case as JSON string
     * @return Execution result
     */
    public JsonObject executeTestCaseFromJson(String testId, String testCaseJson) {
        LOGGER.info("Executing test case from JSON");

        System.setProperty("unique.test.id", testId);
        String testcaseId = "unknown";

        try {
            // Check Server Home
            boolean isServerHomeSet = checkServerHome(testId);
            if (isServerHomeSet) {
                LOGGER.info("Server home is set to testid of : " + testId + " with the value of :" + SERVERHOMEMAP.get(testId + "_ServerHome"));
            }

            // Try to extract testcase_id for better error reporting
            try {
                LOGGER.info("Received test case JSON: " + testCaseJson);

                // Parse JSON directly without URL decoding
                JsonReader jsonReader = new JsonReader(new StringReader(testCaseJson));
                jsonReader.setLenient(true);
                JsonObject parsedJson = JsonParser.parseReader(jsonReader).getAsJsonObject();

                LOGGER.log(Level.INFO, "Parsed test case JSON: " + parsedJson);

                if (parsedJson.has("testcase_id")) {
                    testcaseId = parsedJson.get("testcase_id").getAsString();
                    LOGGER.log(Level.INFO, "Parsed testcase_id: " + testcaseId);
                } else if (parsedJson.has("test_id")) {
                    testcaseId = parsedJson.get("test_id").getAsString();
                    LOGGER.log(Level.INFO, "Parsed test_id: " + testcaseId);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not parse testcase_id from JSON", e);
            }

            TestCaseStatusUtil.addResultInJson(testId, testcaseId, "In_Progress", "", "");

            // Execute the test case
            String resultJson = frameworkBridge.executeTestCase(testCaseJson);

            // Parse and return the result - if parsing fails, handle gracefully
            JsonObject result = safeParseJson(resultJson, testcaseId);

            if (result != null) {
                moveResultsData(testId, testcaseId, result);
            } else {
                LOGGER.log(Level.WARNING, "Test execution returned null result");
            }

            LOGGER.log(Level.INFO, "Adding status in JSON");
            LOGGER.info("Result JSON for adding status: " + result.toString());
            TestCaseStatusUtil.addResultInJson(testId, testcaseId,
                    result.getAsJsonObject(testcaseId).get("status").getAsString(),
                    result.getAsJsonObject(testcaseId).get("remarks").getAsString(),
                    result.getAsJsonObject(testcaseId).get("error").getAsString());
            LOGGER.log(Level.INFO, "Added status in JSON successfully");

            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing test case JSON", e);
            TestCaseStatusUtil.addResultInJson(testId, testcaseId, "FAILURE",
                    "Error executing test case JSON: " + e,
                    "Error executing test case JSON: " + e);

            // Create a result object with error details
            JsonObject errorResult = new JsonObject();
            JsonObject details = new JsonObject();
            details.addProperty("testcase_id", testcaseId);
            details.addProperty("status", "ERROR");
            details.addProperty("remarks", "Error executing test case: " + e.getMessage());
            errorResult.add(testcaseId, details);

            return errorResult;
        }
    }

    
    /**
     * Safely parse a JSON string, with fallback for errors
     * 
     * @param jsonString The JSON string to parse
     * @param testcaseId The test case ID for error reporting
     * @return A JsonObject representing the result
     */
    private JsonObject safeParseJson(String jsonString, String testcaseId) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            LOGGER.warning("Empty result JSON for test case: " + testcaseId);
            
            // Create a minimal valid result
            JsonObject emptyResult = new JsonObject();
            JsonObject details = new JsonObject();
            details.addProperty("testcase_id", testcaseId);
            details.addProperty("status", "UNKNOWN");
            details.addProperty("remarks", "No result data returned from execution");
            details.addProperty("error", "No error data returned from execution");
            emptyResult.add(testcaseId, details);
            
            return emptyResult;
        }
        
        try {
            return JsonParser.parseString(jsonString).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse result JSON: " + jsonString, e);
            
            // Create a valid result object with the original string content
            JsonObject fallbackResult = new JsonObject();
            JsonObject details = new JsonObject();
            details.addProperty("testcase_id", testcaseId);
            details.addProperty("status", "UNKNOWN");
            details.addProperty("remarks", "Could not parse result: " + e.getMessage());
            details.addProperty("error", "Could not parse error: " + e.getMessage());
            details.addProperty("raw_result", jsonString);
            fallbackResult.add(testcaseId, details);
            
            return fallbackResult;
        }
    }

    private void moveResultsData(String uniqueTestId, String testCaseId,JsonObject resultJson) throws IOException {
        File testCasetReportFile = new File(TestCaseStatusUtil.getTestStatusFolder(uniqueTestId)+File.separator+"testcase_result_"+testCaseId+".json");
        if (testCasetReportFile.exists()){
            testCasetReportFile.delete();
        }
        testCasetReportFile.createNewFile();
        Files.write(testCasetReportFile.toPath(),resultJson.toString().getBytes(StandardCharsets.UTF_8),StandardOpenOption.TRUNCATE_EXISTING);
    }
}
