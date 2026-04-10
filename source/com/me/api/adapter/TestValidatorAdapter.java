package com.me.api.adapter;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.TestCaseValidator;
import com.me.TestCaseValidator.ValidationResult;

/**
 * Adapter for TestCaseValidator to avoid direct modification of core classes.
 */
public class TestValidatorAdapter {
    
    /**
     * Validate a test case JSON string
     * 
     * @param testCaseJson JSON string to validate
     * @return Map containing validation results
     */
    public Map<String, Object> validate(String testCaseJson) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Parse the JSON
            JsonObject jsonObject = JsonParser.parseString("{\"test\": " + testCaseJson + "}").getAsJsonObject();
            
            // Use the core validator
            ValidationResult validationResult = TestCaseValidator.validate(jsonObject);
            
            // Convert the result to a Map for API use
            result.put("valid", validationResult.isValid());
            result.put("errors", validationResult.getErrors());
            result.put("warnings", validationResult.getWarnings());
            
        } catch (Exception e) {
            result.put("valid", false);
            result.put("errors", java.util.Collections.singletonList("JSON parsing error: " + e.getMessage()));
        }
        
        return result;
    }
    
    /**
     * Validate JSON syntax only
     * 
     * @param jsonContent JSON string to validate for syntax
     * @return Map containing validation results
     */
    public Map<String, Object> validateSyntax(String jsonContent) {
        Map<String, Object> result = new HashMap<>();
        
        ValidationResult syntaxResult = TestCaseValidator.validateJson(jsonContent);
        
        result.put("syntaxValid", syntaxResult.isValid());
        if (!syntaxResult.isValid()) {
            result.put("syntaxErrors", syntaxResult.getErrors());
        }
        
        return result;
    }
}
