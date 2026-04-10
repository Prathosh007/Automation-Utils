package com.me.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.adventnet.mfw.ConsoleOut;

/**
 * Utility for validating JSON responses against expected criteria
 */
public class JsonResponseValidator {
    private static final Logger LOGGER = LogManager.getLogger(JsonResponseValidator.class, LogManager.LOG_TYPE.FW);
    
    /**
     * Validate that a JSON response contains the expected fields and values
     * 
     * @param jsonResponse The JSON response to validate
     * @param expectedFields Map of field paths to expected values
     * @return ValidationResult with details about validation
     */
    public static ValidationResult validate(String jsonResponse, List<ValidationRule> expectedRules) {
        ValidationResult result = new ValidationResult();
        
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            result.addError("JSON response is null or empty"); //NO I18N
            return result;
        }
        
        if (expectedRules == null || expectedRules.isEmpty()) {
            result.addWarning("No validation rules provided"); //NO I18N
            return result;
        }
        
        try {
            // Parse the JSON response
            JsonElement jsonElement = JsonParser.parseString(jsonResponse);
            
            // Apply each validation rule
            for (ValidationRule rule : expectedRules) {
                try {
                    boolean ruleResult = applyRule(jsonElement, rule);
                    if (ruleResult) {
                        result.addSuccess(rule.getDescription());
                    } else {
                        result.addFailure(rule.getDescription());
                    }
                } catch (Exception e) {
                    result.addError("Error applying rule: " + rule.getDescription() + " - " + e.getMessage()); //NO I18N
                    LOGGER.log(Level.WARNING, "Error applying validation rule", e); //NO I18N
                }
            }
            
        } catch (JsonSyntaxException e) {
            result.addError("Invalid JSON syntax: " + e.getMessage()); //NO I18N
            LOGGER.log(Level.WARNING, "JSON syntax error", e); //NO I18N
        } catch (Exception e) {
            result.addError("Error validating JSON: " + e.getMessage()); //NO I18N
            LOGGER.log(Level.WARNING, "Error validating JSON", e); //NO I18N
        }
        
        return result;
    }
    
    /**
     * Apply a validation rule to a JSON element
     * 
     * @param jsonElement The JSON element to validate
     * @param rule The validation rule to apply
     * @return true if the rule passes, false otherwise
     */
    private static boolean applyRule(JsonElement jsonElement, ValidationRule rule) {
        String path = rule.getPath();
        String operator = rule.getOperator();
        String expectedValue = rule.getExpectedValue();
        
        // Get the value at the specified path
        JsonElement valueElement = getValueAtPath(jsonElement, path);
        
        // If the path doesn't exist and it's required, fail
        if (valueElement == null) {
            return "optional".equals(rule.getRequirement()); //NO I18N
        }
        
        // Apply the operator to compare the value
        switch (operator) {
            case "equals": //NO I18N
                return equals(valueElement, expectedValue);
                
            case "contains": //NO I18N
                return contains(valueElement, expectedValue);
                
            case "startsWith": //NO I18N
                return startsWith(valueElement, expectedValue);
                
            case "endsWith": //NO I18N
                return endsWith(valueElement, expectedValue);
                
            case "matches": //NO I18N
                return matches(valueElement, expectedValue);
                
            case "exists": //NO I18N
                return true; // We already know it exists if we got here
                
            case "not_exists": //NO I18N
                return false; // This should never happen because we check existence above
                
            case "greater_than": //NO I18N
                return greaterThan(valueElement, expectedValue);
                
            case "less_than": //NO I18N
                return lessThan(valueElement, expectedValue);
                
            default:
                LOGGER.warning("Unknown operator: " + operator); //NO I18N
                return false;
        }
    }
    
    /**
     * Get the JSON element at the specified path
     * 
     * @param jsonElement The JSON element to search in
     * @param path The path to the desired element (dot notation for objects, [index] for arrays)
     * @return The JSON element at the path, or null if not found
     */
    private static JsonElement getValueAtPath(JsonElement jsonElement, String path) {
        if (jsonElement == null || path == null || path.isEmpty()) {
            return null;
        }
        
        // Split the path into segments
        String[] segments = path.split("\\.");
        JsonElement current = jsonElement;
        
        for (String segment : segments) {
            if (current == null) {
                return null;
            }
            
            // Check for array access
            Matcher arrayMatcher = Pattern.compile("(.+)\\[(\\d+)\\]").matcher(segment);
            if (arrayMatcher.matches()) {
                String arrayName = arrayMatcher.group(1);
                int index = Integer.parseInt(arrayMatcher.group(2));
                
                // Get the array first
                if (current.isJsonObject()) {
                    current = current.getAsJsonObject().get(arrayName);
                } else {
                    return null;
                }
                
                // Then get the element at the index
                if (current != null && current.isJsonArray() && index < current.getAsJsonArray().size()) {
                    current = current.getAsJsonArray().get(index);
                } else {
                    return null;
                }
            } else {
                // Regular object property access
                if (current.isJsonObject()) {
                    current = current.getAsJsonObject().get(segment);
                } else {
                    return null;
                }
            }
        }
        
        return current;
    }
    
    // Comparison methods
    private static boolean equals(JsonElement element, String expected) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString().equals(expected);
            } else if (primitive.isNumber()) {
                try {
                    // Compare as number if expected is a number
                    return primitive.getAsDouble() == Double.parseDouble(expected);
                } catch (NumberFormatException e) {
                    return primitive.getAsString().equals(expected);
                }
            } else if (primitive.isBoolean()) {
                return primitive.getAsBoolean() == Boolean.parseBoolean(expected);
            }
        }
        return element.toString().equals(expected);
    }
    
    private static boolean contains(JsonElement element, String expected) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString().contains(expected);
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() && 
                    item.getAsString().equals(expected)) {
                    return true;
                }
            }
        }
        return element.toString().contains(expected);
    }
    
    private static boolean startsWith(JsonElement element, String expected) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString().startsWith(expected);
        }
        return false;
    }
    
    private static boolean endsWith(JsonElement element, String expected) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString().endsWith(expected);
        }
        return false;
    }
    
    private static boolean matches(JsonElement element, String regex) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString().matches(regex);
        }
        return false;
    }
    
    private static boolean greaterThan(JsonElement element, String expected) {
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsDouble() > Double.parseDouble(expected);
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Non-numeric value in numeric comparison: " + expected); //NO I18N
        }
        return false;
    }
    
    private static boolean lessThan(JsonElement element, String expected) {
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsDouble() < Double.parseDouble(expected);
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Non-numeric value in numeric comparison: " + expected); //NO I18N
        }
        return false;
    }
    
    /**
     * Validation rule for JSON response validation
     */
    public static class ValidationRule {
        private String path;
        private String operator;
        private String expectedValue;
        private String description;
        private String requirement = "required"; // required or optional //NO I18N
        
        public ValidationRule(String path, String operator, String expectedValue, String description) {
            this.path = path;
            this.operator = operator;
            this.expectedValue = expectedValue;
            this.description = description;
        }
        
        public String getPath() {
            return path;
        }
        
        public String getOperator() {
            return operator;
        }
        
        public String getExpectedValue() {
            return expectedValue;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getRequirement() {
            return requirement;
        }
        
        public void setRequirement(String requirement) {
            this.requirement = requirement;
        }
    }
    
    /**
     * Result of JSON validation
     */
    public static class ValidationResult {
        private List<String> successes = new ArrayList<>();
        private List<String> failures = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        
        public void addSuccess(String message) {
            successes.add(message);
        }
        
        public void addFailure(String message) {
            failures.add(message);
        }
        
        public void addWarning(String message) {
            warnings.add(message);
        }
        
        public void addError(String message) {
            errors.add(message);
        }
        
        public List<String> getSuccesses() {
            return successes;
        }
        
        public List<String> getFailures() {
            return failures;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public boolean isValid() {
            return failures.isEmpty() && errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            
            sb.append("Validation Summary:\n"); //NO I18N
            sb.append("- Passed: ").append(successes.size()).append("\n"); //NO I18N
            sb.append("- Failed: ").append(failures.size()).append("\n"); //NO I18N
            sb.append("- Warnings: ").append(warnings.size()).append("\n"); //NO I18N
            sb.append("- Errors: ").append(errors.size()).append("\n"); //NO I18N
            
            return sb.toString();
        }
    }
    
    // Test method
    public static void main(String[] args) {
        if (args.length < 1) {
            ConsoleOut.println("Usage: JsonResponseValidator <json-file>"); //NO I18N
            return;
        }
        
        try {
            // Test code here
            ConsoleOut.println("Testing JSON validation"); //NO I18N
        } catch (Exception e) {
            ConsoleOut.println("Error: " + e.getMessage()); //NO I18N
        }
    }
}
