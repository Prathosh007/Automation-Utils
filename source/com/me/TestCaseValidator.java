package com.me;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.util.LogManager;

/**
 * Validator for test case JSON files to ensure they contain required fields
 * and proper structure before executing tests
 */
public class TestCaseValidator {
    private static final Logger LOGGER = LogManager.getLogger(TestCaseValidator.class, LogManager.LOG_TYPE.FW);
    
    // Required fields for a test case
    private static final Set<String> REQUIRED_TEST_CASE_FIELDS = new HashSet<String>() {{
        add("testcase_id");//No I18N
        add("operations");//No I18N
        add("expected_result");//No I18N
    }};
    
    // Common operation types
    private static final Map<String, Set<String>> REQUIRED_PARAMETERS = new HashMap<>();
    
    // Initialize required parameters map
    static {
        // File operations
        Set<String> exeInstallParams = new HashSet<>();
        exeInstallParams.add("product_name");//No I18N
        REQUIRED_PARAMETERS.put("exe_install", new HashSet<>(exeInstallParams));//No I18N
        
        Set<String> checkPresenceParams = new HashSet<>();
        checkPresenceParams.add("file_path");//No I18N
        checkPresenceParams.add("filename");//No I18N
        REQUIRED_PARAMETERS.put("check_presence", new HashSet<>(checkPresenceParams));//No I18N
        REQUIRED_PARAMETERS.put("verify_absence", new HashSet<>(checkPresenceParams));//No I18N
        
        Set<String> valueCheckParams = new HashSet<>();
        valueCheckParams.add("file_path");//No I18N
        valueCheckParams.add("filename");//No I18N
        valueCheckParams.add("value");//No I18N
        REQUIRED_PARAMETERS.put("value_should_be_present", new HashSet<>(valueCheckParams));//No I18N
        REQUIRED_PARAMETERS.put("value_should_be_removed", new HashSet<>(valueCheckParams));//No I18N
        
        // Process management
        Set<String> taskManagerParams = new HashSet<>();
        taskManagerParams.add("action");//No I18N
        taskManagerParams.add("process_name");//No I18N
        REQUIRED_PARAMETERS.put("task_manager", new HashSet<>(taskManagerParams));//No I18N
        
        // Service management
        Set<String> serviceParams = new HashSet<>();
        serviceParams.add("service_name");//No I18N
        serviceParams.add("action");//No I18N
        REQUIRED_PARAMETERS.put("service_actions", new HashSet<>(serviceParams));//No I18N
        
        // Command execution
        Set<String> runCommandParams = new HashSet<>();
        runCommandParams.add("command_to_run");//No I18N
        REQUIRED_PARAMETERS.put("run_command", new HashSet<>(runCommandParams));//No I18N
        
        Set<String> runBatParams = new HashSet<>();
        runBatParams.add("bat_file");//No I18N
        REQUIRED_PARAMETERS.put("run_bat", new HashSet<>(runBatParams));//No I18N
        
        // API testing
        Set<String> apiParams = new HashSet<>();
        apiParams.add("connection");//No I18N
        apiParams.add("apiToHit");//No I18N
        apiParams.add("http_method");//No I18N
        REQUIRED_PARAMETERS.put("api_case", new HashSet<>(apiParams));//No I18N
        
        // File editing
        Set<String> fileEditParams = new HashSet<>();
        fileEditParams.add("action");//No I18N
        fileEditParams.add("file_path");//No I18N
        fileEditParams.add("filename");//No I18N
        REQUIRED_PARAMETERS.put("file_edit", new HashSet<>(fileEditParams));//No I18N

        // Add wait_for_condition operation
        Set<String> waitForConditionParams = new HashSet<>();
        waitForConditionParams.add("condition_type");//No I18N
        REQUIRED_PARAMETERS.put("wait_for_condition", new HashSet<>(waitForConditionParams));//No I18N
    }
    
    private static final Set<String> PATH_PARAMETERS = new HashSet<String>() {{
        add("file_path");//No I18N
        add("bat_file_path");//No I18N
        add("install_path");//No I18N
        add("process_path");//No I18N
        add("directory_path");//No I18N
        add("output_path");//No I18N
        add("config_path");//No I18N
    }};

    /**
     * Validate a test cases JSON object
     * 
     * @param jsonObject The JSON object to validate
     * @return A ValidationResult containing whether validation passed and any issues found
     */
    public static ValidationResult validate(JsonObject jsonObject) {
        ValidationResult result = new ValidationResult();
        
        if (jsonObject == null || jsonObject.size() == 0) {
            result.addError("JSON is empty or null");//No I18N
            return result;
        }
        
        // Check each test case
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String testCaseId = entry.getKey();
            JsonElement testCaseElement = entry.getValue();
            
            if (!testCaseElement.isJsonObject()) {
                result.addError("Test case " + testCaseId + " is not a valid JSON object");//No I18N
                continue;
            }
            
            JsonObject testCase = testCaseElement.getAsJsonObject();
            
            // Check required fields
            for (String requiredField : REQUIRED_TEST_CASE_FIELDS) {
                if (!testCase.has(requiredField)) {
                    result.addError("Test case " + testCaseId + " is missing required field: " + requiredField);//No I18N
                }
            }
            
            // Check operations array
            if (testCase.has("operations")) {//No I18N
                JsonElement operationsElement = testCase.get("operations");//No I18N
                
                if (!operationsElement.isJsonArray()) {
                    result.addError("Test case " + testCaseId + ": operations must be an array");//No I18N
                } else {
                    JsonArray operations = operationsElement.getAsJsonArray();
                    
                    if (operations.size() == 0) {
                        result.addError("Test case " + testCaseId + " has an empty operations array");//No I18N
                    } else {
                        // Check each operation
                        for (int i = 0; i < operations.size(); i++) {
                            JsonElement operationElement = operations.get(i);
                            
                            if (!operationElement.isJsonObject()) {
                                result.addError("Test case " + testCaseId + ": operation #" + (i+1) + " is not a valid JSON object");//No I18N
                                continue;
                            }
                            
                            JsonObject operation = operationElement.getAsJsonObject();
                            
                            // Check operation has operation_type
                            if (!operation.has("operation_type")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (i+1) + " is missing operation_type");//No I18N
                                continue;
                            }
                            
                            String operationType = operation.get("operation_type").getAsString();//No I18N
                            
                            // Check if parameters object exists
                            if (!operation.has("parameters")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (i+1) + //No I18N
                                               " (" + operationType + ") is missing parameters object");//No I18N
                                continue;
                            }
                            
                            JsonElement parametersElement = operation.get("parameters");//No I18N
                            if (!parametersElement.isJsonObject()) {
                                result.addError("Test case " + testCaseId + ": operation #" + (i+1) + //No I18N
                                               " has invalid parameters (not a JSON object)");//No I18N
                                continue;
                            }
                            
                            JsonObject parameters = parametersElement.getAsJsonObject();
                            
                            // Check required parameters for the operation type
                            if (REQUIRED_PARAMETERS.containsKey(operationType.toLowerCase())) {
                                Set<String> requiredParams = REQUIRED_PARAMETERS.get(operationType.toLowerCase());
                                for (String paramName : requiredParams) {
                                    if (!parameters.has(paramName)) {
                                        result.addError("Test case " + testCaseId + ": operation #" + (i+1) + //No I18N
                                                      " (" + operationType + ") is missing required parameter: " + paramName);//No I18N
                                    }
                                }
                            } else {
                                result.addWarning("Test case " + testCaseId + ": operation #" + (i+1) + //No I18N
                                                " has potentially unknown operation type: " + operationType);//No I18N
                            }
                            
                            // Specific validation for different operation types
                            validateSpecificOperationType(testCaseId, i, operationType, parameters, result);
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Perform additional validation specific to certain operation types
     */
    private static void validateSpecificOperationType(String testCaseId, int operationIndex, 
                                                     String operationType, JsonObject parameters, 
                                                     ValidationResult result) {
        switch (operationType.toLowerCase()) {
            case "task_manager"://No I18N
                if (parameters.has("action")) {//No I18N
                    String action = parameters.get("action").getAsString();//No I18N
                    if (!action.equals("verify_process") && !action.equals("kill_process")) {//No I18N
                        result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                         " has potentially invalid task_manager action: " + action);//No I18N
                    }
                }
                break;
                
            case "service_actions"://No I18N
                if (parameters.has("action")) {//No I18N
                    String action = parameters.get("action").getAsString();//No I18N
                    if (!action.equals("start") && !action.equals("stop") && //No I18N
                        !action.equals("restart") && !action.equals("status")) {//No I18N
                        result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                         " has potentially invalid service_actions action: " + action);//No I18N
                    }
                }
                break;
                
            case "file_edit":
                if (parameters.has("action")) {
                    String action = parameters.get("action").getAsString();
                    
                    // Check specific parameters based on action type
                    switch(action.toLowerCase()) {
                        case "update":
                            if (!parameters.has("key_to_update")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " file_edit with update action is missing key_to_update parameter");//No I18N
                            }
                            if (!parameters.has("new_value")) {
                                result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                                " file_edit with update action is missing new_value parameter");//No I18N
                            }
                            break;
                            
                        case "replace":
                            if (!parameters.has("replaced_value")) {
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " file_edit with replace action is missing replaced_value parameter");//No I18N
                            }
                            if (!parameters.has("new_value")) {
                                result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                                " file_edit with replace action is missing new_value parameter");//No I18N
                            }
                            break;
                            
                        case "insert":
                            if (!parameters.has("line") && !parameters.has("value_to_insert")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " file_edit with insert action is missing line and value_to_insert parameters");//No I18N
                            }
                            break;
                            
                        case "insert_after"://No I18N
                            if (!parameters.has("after_which_text")) {
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " file_edit with insert_after action is missing after_which_text parameter");//No I18N
                            }
                            if (!parameters.has("value_to_insert")) {
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " file_edit with insert_after action is missing value_to_insert parameter");//No I18N
                            }
                            break;
                            
                        default:
                            result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                            " has potentially invalid file_edit action: " + action);//No I18N
                    }
                }
                break;
                
            case "api_case":
                // API specific validations
                if (parameters.has("http_method")) {//No I18N
                    String method = parameters.get("http_method").getAsString();
                    if (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")) {//No I18N
                        if (!parameters.has("payload")) {//No I18N
                            result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                            " API operation with " + method + " method should have a payload parameter");//No I18N
                        }
                    }
                }
                break;

            case "wait_for_condition":
                if (parameters.has("condition_type")) {//No I18N
                    String conditionType = parameters.get("condition_type").getAsString();//No I18N
                    
                    // Check required parameters based on condition type
                    switch(conditionType.toLowerCase()) {
                        case "file_exists":
                            if (!parameters.has("file_path")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with file_exists condition is missing file_path parameter");//No I18N
                            }
                            if (!parameters.has("filename")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with file_exists condition is missing filename parameter");//No I18N
                            }
                            break;
                            
                        case "file_contains":
                            if (!parameters.has("file_path")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with file_contains condition is missing file_path parameter");//No I18N
                            }
                            if (!parameters.has("filename")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with file_contains condition is missing filename parameter");//No I18N
                            }
                            if (!parameters.has("content")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with file_contains condition is missing content parameter");//No I18N
                            }
                            break;
                            
                        case "processrunning":
                        case "processnotrunning":
                            if (!parameters.has("process_name")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with " + conditionType + " condition is missing process_name parameter");//No I18N
                            }
                            break;
                            
                        case "service_running":
                        case "service_stopped":
                            if (!parameters.has("service_name")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with " + conditionType + " condition is missing service_name parameter");//No I18N
                            }
                            break;
                            
                        case "task_manager":
                            if (!parameters.has("action")) {//No I18N
                                result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                              " wait_for_condition with task_manager condition is missing action parameter");//No I18N
                            } else if (parameters.get("action").getAsString().equals("verify_process")) {//No I18N
                                if (!parameters.has("process_name")) {//No I18N
                                    result.addError("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                                  " wait_for_condition with task_manager verify_process action is missing process_name parameter");//No I18N
                                }
                            }
                            break;
                            
                        default:
                            result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                            " has potentially unknown condition type: " + conditionType);//No I18N
                    }
                    
                    // Check for optional recommended parameters
                    if (!parameters.has("max_wait_time")) {//No I18N
                        result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                        " wait_for_condition is missing optional max_wait_time parameter (will use default)");//No I18N
                    }
                    if (!parameters.has("check_interval")) {//No I18N
                        result.addWarning("Test case " + testCaseId + ": operation #" + (operationIndex+1) + //No I18N
                                        " wait_for_condition is missing optional check_interval parameter (will use default)");//No I18N
                    }
                }
                break;
        }
    }
    
    /**
     * Validate the JSON content for syntax errors before parsing
     * 
     * @param jsonContent The raw JSON string to validate
     * @return A ValidationResult containing any syntax errors found
     */
    public static ValidationResult validateJson(String jsonContent) {
        ValidationResult result = new ValidationResult();
        
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            result.addError("JSON content is empty or null");//No I18N
            return result;
        }
        
        // Check for unescaped backslashes in file paths
        checkForUnescapedBackslashes(jsonContent, result);
        
        // Check for JSON structure errors (basic check before parsing)
        checkJSONStructure(jsonContent, result);
        
        return result;
    }

    /**
     * Check for basic JSON structural issues
     * 
     * @param jsonContent The raw JSON string to check
     * @param result The ValidationResult to add errors to
     */
    private static void checkJSONStructure(String jsonContent, ValidationResult result) {
        // Check basic structure issues that would cause parse errors
        
        // 1. Check for matching braces
        int braceBalance = 0;
        int squareBraceBalance = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < jsonContent.length(); i++) {
            char c = jsonContent.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            
            if (c == '"' && !escaped) {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') {braceBalance++;}
                if (c == '}') {braceBalance--;}
                if (c == '[') {squareBraceBalance++;}
                if (c == ']') {squareBraceBalance--;}
            }
        }
        
        if (braceBalance != 0) {
            result.addError("JSON has unbalanced braces: " + (braceBalance > 0 ? "missing " + braceBalance + " closing }" : "has " + (-braceBalance) + " extra }"));//No I18N
        }
        
        if (squareBraceBalance != 0) {
            result.addError("JSON has unbalanced square brackets: " + (squareBraceBalance > 0 ? "missing " + squareBraceBalance + " closing ]" : "has " + (-squareBraceBalance) + " extra ]"));//No I18N
        }
        
        // 2. Try a partial parse to find where syntax errors might be
        if (result.isValid()) {
            try {
                JsonParser.parseString(jsonContent);
                // If we get here, parsing succeeded
            } catch (Exception e) {
                // Extract line and position from error message if possible
                String message = e.getMessage();
                result.addError("JSON syntax error: " + message);//No I18N
            }
        }
    }

    /**
     * Check for unescaped backslash characters that would cause JSON parse errors
     * 
     * @param jsonContent The raw JSON string to check
     * @param result The ValidationResult to add errors to
     */
    private static void checkForUnescapedBackslashes(String jsonContent, ValidationResult result) {
        // First, check for file paths with single backslashes (most common error)
        // Look for pattern like: "file_path": "C:\path\to\file"
        for (String paramName : PATH_PARAMETERS) {
            Pattern pattern = Pattern.compile("\"" + paramName + "\"\\s*:\\s*\"([^\"]*\\\\[^\"]*)\"");//No I18N
            Matcher matcher = pattern.matcher(jsonContent);
            
            while (matcher.find()) {
                String path = matcher.group(1);
                
                // Check if the path contains unescaped backslashes
                if (path.matches(".*\\\\(?![\"\\\\bfnrt/]).*")) {//No I18N
                    result.addError("Invalid JSON: Unescaped backslash in " + paramName + " parameter: \"" + //No I18N
                                  path + "\". JSON requires double backslashes (\\\\) or forward slashes (/) in file paths.");//No I18N
                    // No fix suggestions or automatic corrections
                }
            }
        }
        
        // Also check for general invalid escape sequences
        Pattern invalidEscapePattern = Pattern.compile("\\\\(?![\"\\\\bfnrt/]|u[0-9a-fA-F]{4})");//No I18N
        Matcher escapeMatcher = invalidEscapePattern.matcher(jsonContent);
        
        if (escapeMatcher.find()) {
            int pos = escapeMatcher.start();
            
            // Find line and column number
            int lineNumber = 1;
            int columnNumber = 1;
            for (int i = 0; i < pos; i++) {
                if (jsonContent.charAt(i) == '\n') {
                    lineNumber++;
                    columnNumber = 1;
                } else {
                    columnNumber++;
                }
            }
            
            // Extract the problematic line for context
            String[] lines = jsonContent.split("\n");
            String problemLine = lineNumber <= lines.length ? lines[lineNumber - 1] : "";
            
            result.addError("Invalid JSON escape sequence at line " + lineNumber + ", column " + columnNumber + ":");//No I18N
            result.addError("    " + problemLine);
            result.addError("    " + createRepeatedString(" ", columnNumber - 1) + "^");
        }
    }

    /**
     * Creates a string by repeating a character n times
     * Compatible replacement for String.repeat() which requires Java 11+
     * 
     * @param str String to repeat
     * @param count Number of times to repeat
     * @return String with repeated content
     */
    private static String createRepeatedString(String str, int count) {
        if (count <= 0){ return "";}
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // For backward compatibility - alias to validateJson
    public static ValidationResult validateJsonSyntax(String jsonContent) {
        return validateJson(jsonContent);
    }

    /**
     * Class to hold validation results
     */
    public static class ValidationResult {
        private boolean valid = true;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
        public void addError(String message) {
            errors.add(message);
            valid = false;
        }
        
        public void addWarning(String message) {
            warnings.add(message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            
            if (isValid()) {
                sb.append("Validation passed. ");//No I18N
            } else {
                sb.append("Validation failed with ").append(errors.size()).append(" errors. ");//No I18N
            }
            
            if (hasWarnings()) {
                sb.append("Found ").append(warnings.size()).append(" warnings.");//No I18N
            }
            
            return sb.toString();
        }
    }

    /**
     * Main method for testing the validator
     * 
     * @param args Command line arguments (file path to validate)
     */
//     public static void main(String[] args) {
//         if (args.length < 1) {
//             System.out.println("Usage: java TestCaseValidator <json_file_path>");//No I18N
//             return;
//         }
        
//         String filePath = args[0];
//         System.out.println("Validating JSON file: " + filePath);//No I18N
        
//         try {
//             // First check for JSON syntax errors
//             String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
//             ValidationResult syntaxResult = validateJsonSyntax(jsonContent);
            
//             if (!syntaxResult.isValid()) {
//                 System.out.println("JSON syntax validation failed!");//No I18N
//                 System.out.println("Errors:");//No I18N
//                 for (String error : syntaxResult.getErrors()) {
//                     System.out.println("  - " + error);
//                 }
//                 return;
//             }
            
//             // If syntax is valid, parse and validate the structure
//             JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
//             ValidationResult result = validate(jsonObject);
            
//             if (result.isValid()) {
//                 System.out.println("Validation passed!");//No I18N
//                 if (result.hasWarnings()) {
//                     System.out.println("Warnings:");//No I18N
//                     for (String warning : result.getWarnings()) {
//                         System.out.println("  - " + warning);
//                     }
//                 }
//             } else {
//                 System.out.println("Validation failed!");//No I18N
//                 System.out.println("Errors:");//No I18N
//                 for (String error : result.getErrors()) {
//                     System.out.println("  - " + error);
//                 }
//                 if (result.hasWarnings()) {
//                     System.out.println("Warnings:");//No I18N
//                     for (String warning : result.getWarnings()) {
//                         System.out.println("  - " + warning);
//                     }
//                 }
//             }
//         } catch (Exception e) {
//             System.out.println("Error validating file: " + e.getMessage());//No I18N
//             e.printStackTrace();
//         }
//     }
}
