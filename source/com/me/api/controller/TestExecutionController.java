package com.me.api.controller;

import com.me.api.model.GoatApiResponse;
import com.me.api.service.TestExecutionService;

import com.me.util.LogManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.GOATCommonConstants.TEST_SETUP_DETAILES;

/**
 * REST Controller for test execution operations
 */
@RestController
@RequestMapping("/execute")
public class TestExecutionController {
    
    private static final Logger LOGGER = LogManager.getLogger(TestExecutionController.class.getName(), LogManager.LOG_TYPE.API);
    
    // Maximum time to wait for test execution (in seconds)
    private static final int MAX_EXECUTION_WAIT_TIME = 120;
    
    @Autowired
    private TestExecutionService testExecutionService;
    
    /**
     * Execute a test case from JSON
     */
    @PostMapping
    public ResponseEntity<?> executeTestCase(@RequestBody String testCaseJson, @Parameter(description = "Test Unique Id", required = true) @RequestParam(name = "testId") String testUniqueId, HttpServletRequest request) {
        LOGGER.log(Level.INFO, "Starting test execution from direct JSON");

        if (testUniqueId == null || testUniqueId.isEmpty()){
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body("Test Unique ID is required as testId");
        }

        // Copy headers to a map before starting the thread
        Map<String, String> headers = new java.util.HashMap<>();
        Enumeration<String> headerNamesEnum = request.getHeaderNames();
        while (headerNamesEnum.hasMoreElements()) {
            String headerName = headerNamesEnum.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LOGGER.log(Level.INFO, "Executing test case in separate thread");
                    LOGGER.log(Level.INFO, "API called: /execute?testId=" + testUniqueId);

                    // Log headers from the copied map
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        LOGGER.log(Level.INFO, "Header: " + entry.getKey() + " = " + entry.getValue());
                    }
                    // LOGGER.log(Level.INFO, "Request Body: " + testCaseJson);

                    JsonObject result = testExecutionService.executeTestCaseFromJson(testUniqueId, testCaseJson);

                    LOGGER.log(Level.INFO, "API Response: " + result.toString());

                    LOGGER.log(Level.INFO, "Test execution completed successfully in thread");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error during test execution in thread", e);
                    throw new CompletionException(e);
                }
            }
        }).start();
//        try {
            // Use CompletableFuture to handle the test execution asynchronously with a timeout
//            CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
//
//            });

            // Wait for the result with a timeout
//            JsonObject result = future.get(MAX_EXECUTION_WAIT_TIME, TimeUnit.SECONDS);
//            LOGGER.log(Level.INFO, "Test execution completed within timeout period");
//
//            // Determine if the test passed based on result content
//            boolean isSuccess = isTestSuccessful(result);
//            String message = isSuccess ?
//                "Test case executed successfully" :
//                "Test case execution completed with issues";
//
//            LOGGER.log(Level.INFO, "Test result: {0}, success: {1}", new Object[] {message, isSuccess});

            // Always return 200 OK with the result
//            return ResponseEntity.ok(new GoatApiResponse<>(true, "Testcase initiated", null));

//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Unexpected error processing test case", e);
//            return ResponseEntity
//                .status(HttpStatus.OK) // Return 200 even on errors, but with an error message and success=false
//                .body(new GoatApiResponse<>(false, "Unexpected error: " + e.getMessage(), null));
//        }
        // Always return 200 OK with the result
//        return ResponseEntity.ok().contentType(new MediaType(MediaType.APPLICATION_JSON, java.nio.charset.StandardCharsets.UTF_8)).body(new GoatApiResponse<>(true, "Testcase initiated", null));
        return ResponseEntity.ok().contentType(new MediaType(MediaType.APPLICATION_JSON, java.nio.charset.StandardCharsets.UTF_8)).body(new GoatApiResponse<>(true, "Testcase initiated", null));
    }

    /**
     * Execute a test case from JSON
     */
    @PostMapping("/{id}")
    @Operation(
            summary = "Execute specific test case",
            description = "Execute test using path variable and query param"
    )
    public ResponseEntity<?> executeTestCaseFromID(@Parameter(description = "Test Case Id" , required = true) @PathVariable(name = "id") String testCaseId,@Parameter(description = "Test Unique Id",required = true) @RequestParam(name = "testId") String testUniqueId) {
        LOGGER.log(Level.INFO, "Starting test execution from direct JSON");

        if (testCaseId == null || testCaseId.isEmpty()){
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body("Test Unique ID is required as testId");
        }

        if (testUniqueId == null || testUniqueId.isEmpty()){
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body("Test Case ID is required as id");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LOGGER.log(Level.INFO, "Executing test case in separate thread");
                    LOGGER.log(Level.INFO, "API called: /execute/" + testCaseId + "?testId=" + testUniqueId);
                    JsonObject result = testExecutionService.executeTestCase(testUniqueId,testCaseId);

                    LOGGER.log(Level.INFO, "API Response: " + result.toString());

                    LOGGER.log(Level.INFO, "Test execution completed successfully in thread");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error during test execution in thread", e);
                    throw new CompletionException(e);
                }
            }
        }).start();

//        try {
//            // Use CompletableFuture to handle the test execution asynchronously with a timeout
//            CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
//
//            });
//
//            // Wait for the result with a timeout
//            JsonObject result = future.get(MAX_EXECUTION_WAIT_TIME, TimeUnit.SECONDS);
//            LOGGER.log(Level.INFO, "Test execution completed within timeout period");
//
//            // Determine if the test passed based on result content
//            boolean isSuccess = isTestSuccessful(result);
//            String message = isSuccess ?
//                    "Test case executed successfully" :
//                    "Test case execution completed with issues";
//
//            LOGGER.log(Level.INFO, "Test result: {0}, success: {1}", new Object[] {message, isSuccess});
//
//            // Always return 200 OK with the result
//            return ResponseEntity.ok(new GoatApiResponse<>(isSuccess, message, result));
//
//        } catch (TimeoutException e) {
//            LOGGER.log(Level.SEVERE, "Test execution timed out after " + MAX_EXECUTION_WAIT_TIME + " seconds", e);
//            return ResponseEntity
//                    .status(HttpStatus.REQUEST_TIMEOUT)
//                    .body(new GoatApiResponse<>(false, "Test execution timed out after " + MAX_EXECUTION_WAIT_TIME + " seconds", null));
//        } catch (ExecutionException e) {
//            LOGGER.log(Level.SEVERE, "Error during test execution", e.getCause() != null ? e.getCause() : e);
//            Throwable cause = e.getCause() != null ? e.getCause() : e;
//            return ResponseEntity
//                    .status(HttpStatus.OK) // Return 200 even on errors, but with an error message and success=false
//                    .body(new GoatApiResponse<>(false, "Error executing test case: " + cause.getMessage(), null));
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Unexpected error processing test case", e);
//            return ResponseEntity
//                    .status(HttpStatus.OK) // Return 200 even on errors, but with an error message and success=false
//                    .body(new GoatApiResponse<>(false, "Unexpected error: " + e.getMessage(), null));
//        }

        // Always return 200 OK with the result
        return ResponseEntity.ok(new GoatApiResponse<>(true, "Testcase initiated", null));
    }
    
    /**
     * Helper method to determine if a test result indicates success
     */
    private boolean isTestSuccessful(JsonObject jsonResult) {
        try {
            // Check first-level keys (should be the test case ID)
            for (String key : jsonResult.keySet()) {
                JsonElement element = jsonResult.get(key);
                if (element.isJsonObject()) {
                    JsonObject testResult = element.getAsJsonObject();
                    // Check if the status field indicates success
                    if (testResult.has("status")) {
                        String status = testResult.get("status").getAsString();
                        return "PASSED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
                    }
                }
            }
            
            // If we can't determine the result, default to success since we got a result object
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error determining test success, defaulting to true", e);
            return true;
        }
    }
    
    /**
     * Execute a test case with parameters for batch execution
     */
    @PostMapping("/batch")
    public ResponseEntity<?> executeBatchTest(@RequestBody Map<String, Object> batchParams) {
        // Implementation for batch execution
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
            .body(new GoatApiResponse<>(false, "Batch execution endpoint not implemented yet", null));
    }

    /**
     * Update service configuration
     */
    @PostMapping("/set-service-name")
    @Operation(
            summary = "Update service configuration",
            description = "Updates the service name in configuration file"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service configuration updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid service name"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> updateServiceConfig(
            @Parameter(description = "Service Name", required = true)
            @RequestParam(name = "service_name") String serviceName) {

        LOGGER.log(Level.INFO, "Updating service configuration with service name: {0}", serviceName);

        if (serviceName == null || serviceName.trim().isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new GoatApiResponse<>(false, "Service name cannot be empty", null));
        }

        try {
            // Save service name to configuration file
            boolean saved = saveServiceConfiguration(serviceName);

            if (saved) {
                return ResponseEntity.ok(new GoatApiResponse<>(true,
                        "Service configuration updated successfully", serviceName));
            } else {
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new GoatApiResponse<>(false, "Failed to update service configuration", null));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating service configuration", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GoatApiResponse<>(false, "Error updating service configuration: " + e.getMessage(), null));
        }
    }

    /**
     * Saves the service name to the configuration file
     *
     * @param serviceName the service name to save
     * @return true if saved successfully, false otherwise
     */
    public boolean saveServiceConfiguration(String serviceName) {
        Path configPath = Paths.get(getToolServerHome()+TEST_SETUP_DETAILES);
        try {
            // Read all lines from the file
            List<String> lines = Files.readAllLines(configPath);
            List<String> updatedLines = new ArrayList<>();
            boolean serviceNameUpdated = false;

            // Update the service_name line
            for (String line : lines) {
                if (line.startsWith("service_name =")) {
                    LOGGER.info("Updating existing service_name in config");
                    updatedLines.add("service_name = \"" + serviceName + "\"");
                    serviceNameUpdated = true;
                } else {
                    updatedLines.add(line);
                }
            }

            // If service_name wasn't found, add it
            if (!serviceNameUpdated) {
                LOGGER.info("service_name not found in config, adding it");
                updatedLines.add("service_name = \"" + serviceName + "\"");
            }

            // Write the updated content back to the file
            Files.write(configPath, updatedLines);
            LOGGER.log(Level.INFO, "Service configuration updated successfully: {0}", serviceName);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to update service configuration", e);
            return false;
        }
    }

}
