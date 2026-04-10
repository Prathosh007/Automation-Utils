package com.me.api.service;

import com.me.util.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.api.bridge.GoatFrameworkBridge;
import com.me.api.error.ResourceNotFoundException;
import com.me.api.model.TestCaseDTO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Service for test case operations
 */
@Service
public class TestCaseService {
    
    private static final Logger LOGGER = LogManager.getLogger(TestCaseService.class.getName(), LogManager.LOG_TYPE.FW);
    
    @Autowired
    private GoatFrameworkBridge frameworkBridge;
    
    /**
     * Get all test case IDs
     * 
     * @return List of test case IDs
     */
    public List<String> getAllTestCaseIds() {
        LOGGER.info("Getting all test case IDs");
        return frameworkBridge.getTestCaseIds();
    }
    
    /**
     * Get a test case by ID
     * 
     * @param id Test case ID
     * @return Test case data
     * @throws ResourceNotFoundException if test case not found
     */
    public JsonObject getTestCaseById(String id) {
        LOGGER.info("Getting test case with ID: " + id);
        
        String testCaseJson = frameworkBridge.getTestCase(id);
        if (testCaseJson == null) {
            throw new ResourceNotFoundException("Test case", id);
        }
        
        return JsonParser.parseString(testCaseJson).getAsJsonObject();
    }
    
    /**
     * Validate a test case
     * 
     * @param testCaseJson Test case JSON
     * @return Validation results
     */
    public Object validateTestCase(String testCaseJson) {
        LOGGER.info("Validating test case");
        return frameworkBridge.validateTestCase(testCaseJson);
    }
}
