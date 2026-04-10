package com.me.api.controller;

import com.me.ResolveOperationParameters;
import com.me.api.model.TestCaseDTO;
import com.me.api.model.GoatApiResponse;
import com.me.api.service.TestCaseService;
import com.me.api.service.TestExecutionService;

// Import Swagger annotations properly
import com.me.api.util.TestCaseStatusUtil;
import com.me.util.LogManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST Controller for test case management operations
 */
@RestController
@RequestMapping("/testcases")
@Tag(name = "Test Cases", description = "Test case management endpoints")
public class TestCaseController {
    
    private static final Logger LOGGER = LogManager.getLogger(TestCaseController.class.getName(), LogManager.LOG_TYPE.FW);
    
    @Autowired
    private TestCaseService testCaseService;
    
    @Autowired
    private TestExecutionService testExecutionService;
    
    /**
     * Get a list of all test case IDs
     */
    @GetMapping
    @Operation(summary = "Get all test cases", description = "Returns a list of all available test case IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved test case list"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> getAllTestCases() {
        try {
            List<String> testCaseIds = testCaseService.getAllTestCaseIds();
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Test cases retrieved successfully", testCaseIds));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving test cases", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoatApiResponse<>(false, "Error retrieving test cases: " + e.getMessage(), null));
        }
    }
    
    /**
     * Get a specific test case by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get test case by ID", description = "Returns details of a specific test case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved test case"),
        @ApiResponse(responseCode = "404", description = "Test case not found"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> getTestCaseById(
            @Parameter(description = "Test Case ID", required = true)
            @PathVariable String id) {
        try {
            Object testCase = testCaseService.getTestCaseById(id);
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Test case retrieved successfully", testCase));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving test case: " + id, e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoatApiResponse<>(false, "Error retrieving test case: " + e.getMessage(), null));
        }
    }
    
    /**
     * Execute a test case
     */
    @PostMapping("/{id}/execute")
    @Operation(summary = "Execute test case", description = "Executes a test case by its ID and returns execution results")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test case executed successfully"),
        @ApiResponse(responseCode = "404", description = "Test case not found"),
        @ApiResponse(responseCode = "500", description = "Error executing test case")
    })
    public ResponseEntity<?> executeTestCase(
            @Parameter(description = "Test Case ID", required = true)
            @PathVariable String id,@Parameter(description = "Test Id" , required = true) @RequestParam(name = "testID") String testID) {
        try {
            Object result = testExecutionService.executeTestCase(id,testID);
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Test case executed successfully", result));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing test case: " + id, e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoatApiResponse<>(false, "Error executing test case: " + e.getMessage(), null));
        }
    }
    
    /**
     * Create a new test case
     */
    @PostMapping
    @Operation(summary = "Create test case", description = "Creates a new test case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "501", description = "Not implemented yet")
    })
    public ResponseEntity<?> createTestCase(@RequestBody TestCaseDTO testCase) {
        // Implementation for creating a test case
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
            .body(new GoatApiResponse<>(false, "Create test case endpoint not implemented yet", null));
    }
    
    /**
     * Validate a test case
     */
    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate test case", description = "Validates a test case JSON without executing it")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid JSON"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> validateTestCase(@RequestBody String testCaseJson) {
        try {
            Object validationResult = testCaseService.validateTestCase(testCaseJson);
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Test case validation completed", validationResult));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error validating test case", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoatApiResponse<>(false, "Error validating test case: " + e.getMessage(), null));
        }
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Check status", description = "Get the status of the test case execution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "400", description = "Invalid JSON"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> getStatus(@Parameter(description = "Test ID",required = true) @RequestParam(name = "testId") String testID, @Parameter(description = "Test Case ID",required = true) @RequestParam(name = "testcaseId") String testcaseId) {
        try {
            return ResponseEntity.ok(new GoatApiResponse<>(true, "test result ", TestCaseStatusUtil.getResultJson(testID,testcaseId)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error while getting test report", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GoatApiResponse<>(false, "Error while getting test result: " + e.getMessage(), null));
        }
    }



    @GetMapping(value = "/getValue")
    @Operation(summary = "Get variable value", description = "Gets a value from the variable manager by key")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Variable retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Variable not found"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> getVariableValue(
            @Parameter(description = "Variable key", required = true)
            @RequestParam(name = "key") String key) {
        try {
            String value = ResolveOperationParameters.VariableManager.getVariable(key);
            if (value == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new GoatApiResponse<>(false, "Variable not found: " + key, null));
            }
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Variable retrieved successfully", value));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving variable: " + key, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GoatApiResponse<>(false, "Error retrieving variable: " + e.getMessage(), null));
        }
    }

}
