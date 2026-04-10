package com.me.testcases;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.io.IOException;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;

import static com.me.testcases.DataBaseOperationHandler.checkValueInDatabase;
import static com.me.testcases.DataBaseOperationHandler.saveNote;
import static com.me.testcases.FileReaderUtil.*;

/**
 * Handler for wait_for_condition operations that polls for a specific condition to be met
 * with configurable timeout and check interval
 */
public class WaitForConditionHandler {
    private static final Logger LOGGER = LogManager.getLogger(WaitForConditionHandler.class, LogManager.LOG_TYPE.FW);
    
    // Default values
    private static final long DEFAULT_MAX_WAIT_TIME = 300; // 5 minutes in seconds
    private static final long DEFAULT_CHECK_INTERVAL = 5;  // 5 seconds
    
    /**
     * Execute a wait_for_condition operation based on the specified condition type
     * 
     * @param op The operation to execute
     * @return true if the condition was met within the timeout, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.severe("Operation is null"); //No I18N
            return false;
        }
        
        String conditionType = op.getParameter("condition_type"); //No I18N
        long maxWaitTime = getParameterAsLong(op, "max_wait_time", DEFAULT_MAX_WAIT_TIME); //No I18N
        long checkInterval = getParameterAsLong(op, "check_interval", DEFAULT_CHECK_INTERVAL); //No I18N
        
        if (conditionType == null || conditionType.isEmpty()) {
            LOGGER.severe("condition_type is required for wait_for_condition operation"); //No I18N
            op.setRemarks("Error: condition_type is required"); //No I18N
            return false;
        }
        
        LOGGER.info("Executing wait_for_condition operation: " + conditionType + 
                ", max_wait_time=" + maxWaitTime + "s, check_interval=" + checkInterval + "s"); //No I18N
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (maxWaitTime * 1000);
        boolean conditionMet = false;
        StringBuilder remarkBuilder = new StringBuilder();

        LOGGER.info("Wait for condition: " + conditionType); //No I18N
        LOGGER.info("Max wait time: " + maxWaitTime + " seconds"); //No I18N
        LOGGER.info("Check interval: " + checkInterval + " seconds"); //No I18N
        
        // Loop until condition is met, timeout expires, or an error occurs
        while (System.currentTimeMillis() < endTime) {
            try {
                // Check the condition based on condition_type
                switch (conditionType.toLowerCase()) {
                    case "file_exists":
                        conditionMet = checkFileExists(op, remarkBuilder);
                        break;
                    case "file_contains":
                        conditionMet = checkFileContains(op, remarkBuilder);
                        break;
                    case "processrunning":
                        conditionMet = checkProcessRunning(op, remarkBuilder);
                        break;
                    case "processnotrunning":
                        conditionMet = checkProcessNotRunning(op, remarkBuilder);
                        break;
                    case "service_running":
                        conditionMet = checkServiceRunning(op, remarkBuilder);
                        break;
                    case "service_stopped":
                        conditionMet = checkServiceStopped(op, remarkBuilder);
                        break;
                    case "task_manager":
                        conditionMet = waitForTaskManagerCondition(op, maxWaitTime * 1000, checkInterval * 1000);
                        break;
                    case "verify_value_presence_in_database":
                        String value = checkValueInDatabase(op);
                        if (value != null) {
                            op.setRemarks("Value '" + value + "' was found as expected."); //NO I18N
                            if (op.hasNote()){
                                saveNote(op, value);
                            }
                            return value.equals(op.getParameter("value"));
                        } else {
                            op.setRemarks("Value was not found when it should be present."); //NO I18N
                            return false;
                        }
                    case "verify_value_absence_in_database":
                        String absenceValue = checkValueInDatabase(op);
                        if (absenceValue == null) {
                            op.setRemarks("Value is absent as expected."); //NO I18N
                            return true;
                        } else {
                            op.setRemarks("Value '" + absenceValue + "' was found when it should be absent."); //NO I18N
                            return false;
                        }
                    default:
                        LOGGER.warning("Unknown condition type: " + conditionType);
                        remarkBuilder.append("Error: Unknown condition type: ").append(conditionType).append("\n"); //No I18N
                        op.setRemarks(remarkBuilder.toString());
                        return false;
                }
                
                // If condition is met, we can exit the loop
                if (conditionMet) {
                    break;
                }
                
                // Sleep for the check interval before trying again
                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                long remainingSeconds = maxWaitTime - elapsedSeconds;
                
                if (remainingSeconds <= 0) {
                    break;
                }
                
                remarkBuilder.append("Condition not met. Waiting ").append(checkInterval)//NO I18N
                           .append(" seconds... (").append(elapsedSeconds)//NO I18N
                           .append("s elapsed, ").append(remainingSeconds)//NO I18N
                           .append("s remaining)\n"); //No I18N
                
                LOGGER.info("Condition not met. Waiting " + checkInterval + //NO I18N
                          " seconds... (" + elapsedSeconds + "s elapsed, " + //NO I18N
                          remainingSeconds + "s remaining)"); //No I18N
                
                Thread.sleep(checkInterval * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Wait operation interrupted", e);//NO I18N
                remarkBuilder.append("Error: Wait operation interrupted\n"); //No I18N
                op.setRemarks(remarkBuilder.toString());
                currentTime = null;
                return false;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during wait condition check", e);
                remarkBuilder.append("Error during wait condition check: ")//NO I18N
                           .append(e.getMessage()).append("\n"); //No I18N
                op.setRemarks(remarkBuilder.toString());
                currentTime = null;
                return false;
            }
        }
        currentTime = null;
        
        // Check final result
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (conditionMet) {
            remarkBuilder.append("\nCondition met after ").append(elapsedTime / 1000)//NO I18N
                       .append(" seconds\n"); //No I18N
            
            // Add success message if provided
            String successMessage = op.getParameter("success_message"); //No I18N
            if (successMessage != null && !successMessage.isEmpty()) {
                remarkBuilder.append(successMessage).append("\n"); //No I18N
            }
            
            LOGGER.info("Condition met after " + (elapsedTime / 1000) + " seconds"); //No I18N
        } else {
            remarkBuilder.append("\nTimeout: Condition not met after ")//NO I18N
                       .append(maxWaitTime).append(" seconds\n"); //No I18N
            LOGGER.warning("Timeout: Condition not met after " + maxWaitTime + " seconds"); //No I18N
        }
        
        op.setRemarks(remarkBuilder.toString());
        return conditionMet;
    }
    
    /**
     * Wait for a task manager condition to be met
     * 
     * @param operation The operation with parameters
     * @param timeoutMs Timeout in milliseconds
     * @param pollingIntervalMs Polling interval in milliseconds
     * @return true if condition is met within timeout, false otherwise
     */
    private static boolean waitForTaskManagerCondition(Operation operation, long timeoutMs, long pollingIntervalMs) {
        String action = operation.getParameter("action"); // NO I18N
        
        if (action == null || action.isEmpty()) {
            operation.setRemarks("No action specified for task_manager condition"); // NO I18N
            return false;
        }
        
        // Currently only support verify_process action
        if (!action.equals("verify_process")) { // NO I18N
            operation.setRemarks("Unsupported task manager action: " + action + 
                               ". Only verify_process is supported."); // NO I18N
            return false;
        }
        
        String processName = operation.getParameter("process_name"); // NO I18N
        String processPath = operation.getParameter("process_path"); // NO I18N
        if (processPath.contains("server_home")) { // NO I18N
            processPath = resolvePath(processPath);
        }
        String portStr = operation.getParameter("port"); // NO I18N
        String expectation = operation.getParameter("expect"); // NO I18N
        
        // Default expectation is processRunning if not specified
        if (expectation == null || expectation.isEmpty()) {
            expectation = "processRunning"; // NO I18N
        }
        
        if (processName == null || processName.isEmpty()) {
            operation.setRemarks("No process name specified for verify_process action"); // NO I18N
            return false;
        }
        
        LOGGER.info("Waiting for task manager condition: " + action + 
                   ", process: " + processName + 
                   (processPath != null ? ", path: " + processPath : "") + 
                   (portStr != null ? ", port: " + portStr : "") + 
                   ", expect: " + expectation); // NO I18N
        
        long startTime = System.currentTimeMillis();
        boolean conditionMet = false;
        StringBuilder remarkBuilder = new StringBuilder();
        
        remarkBuilder.append("Waiting for condition: Task manager process " + // NO I18N
                            (expectation.equals("processRunning") ? "running" : "not running") + // NO I18N
                            " - " + processName + // NO I18N
                            (processPath != null ? " in path " + processPath : "") + // NO I18N
                            (portStr != null ? " using port " + portStr : "") + "\n\n"); // NO I18N
        
        // Loop until condition is met or timeout
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Create a mock operation to pass to TaskManagerHandler
            Operation taskManagerOp = new Operation("task_manager");
            taskManagerOp.setParameter("action", "verify_process");
            taskManagerOp.setParameter("process_name", processName);
            taskManagerOp.setParameter("expect", expectation);
            
            if (processPath != null && !processPath.isEmpty()) {
                taskManagerOp.setParameter("process_path", processPath); //No I18N
            }
            
            if (portStr != null && !portStr.isEmpty()) {
                taskManagerOp.setParameter("port", portStr); //No I18N
            }
            
            boolean isMetNow = TaskManagerHandler.executeOperation(taskManagerOp);
            
            if (isMetNow) {
                conditionMet = true;
                remarkBuilder.append("Condition met after " + // NO I18N
                                  (System.currentTimeMillis() - startTime) + " ms\n\n"); // NO I18N
                
                // Append TaskManagerHandler's remarks for additional details
                if (taskManagerOp.getRemarks() != null) {
                    remarkBuilder.append("Task Manager details:\n" + // NO I18N
                                      taskManagerOp.getRemarks() + "\n"); // NO I18N
                }
                
                break;
            }
            
            // Sleep before trying again
            try {
                Thread.sleep(pollingIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Wait interrupted: " + e.getMessage()); // NO I18N
                break;
            }
        }
        
        // If condition wasn't met, add timeout message
        if (!conditionMet) {
            remarkBuilder.append("Condition not met after " + timeoutMs + " ms (timeout)\n"); // NO I18N
            remarkBuilder.append("Last check result: condition not met\n"); // NO I18N
        }
        
        operation.setRemarks(remarkBuilder.toString());
        return conditionMet;
    }
    
    /**
     * Check if a file exists, with support for wildcards
     */
    private static boolean checkFileExists(Operation op, StringBuilder remarks) {
        String filePath = op.getParameter("file_path"); //No I18N
        if (filePath.contains("server_home")) { //No I18N
            filePath = resolvePath(filePath);
        }
        String filename = op.getParameter("filename"); //No I18N

        // Resolve variable references in file path and filename
        filePath = ResolveOperationParameters.resolveVariableReferences(filePath);
        filename = ResolveOperationParameters.resolveVariableReferences(filename);
        
        if (filePath == null || filePath.isEmpty() || filename == null || filename.isEmpty()) {
            remarks.append("Error: file_path and filename parameters are required\n"); //No I18N
            return false;
        }
        
        // Resolve placeholders like server_home in the file path
        filePath = resolvePath(filePath);
        remarks.append("Using resolved path: ").append(filePath).append("\n"); //No I18N
        
        // Normalize paths to use proper system separators
        filePath = filePath.replace('\\', File.separatorChar).replace('/', File.separatorChar);
        
        // Check if the filename contains wildcards
        if (filename.contains("*") || filename.contains("?")) {
            // Handle wildcard pattern
            return checkFileExistsWithWildcard(filePath, filename, remarks);
        } else {
            // Simple file check
            File file = new File(filePath, filename);
            boolean exists = file.exists();
            
            remarks.append("Checking file: ").append(file.getAbsolutePath()).append("\n"); //No I18N
            remarks.append("File exists: ").append(exists).append("\n"); //No I18N
            
            return exists;
        }
    }
    
    /**
     * Check if a file exists using wildcard pattern
     */
    private static boolean checkFileExistsWithWildcard(String directoryPath, String pattern, StringBuilder remarks) {
        try {
            File directory = new File(directoryPath);
            if (!directory.exists() || !directory.isDirectory()) {
                remarks.append("Directory does not exist: ").append(directoryPath).append("\n"); //No I18N
                return false;
            }
            
            remarks.append("Checking directory: ").append(directoryPath).append("\n"); //No I18N
            remarks.append("Using pattern: ").append(pattern).append("\n"); //No I18N
            
            // Create glob pattern
            Path dirPath = FileSystems.getDefault().getPath(directoryPath);
            String globPattern = "glob:" + pattern;//NO I18N
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(globPattern);
            
            // Search for matching files
            try (Stream<Path> paths = Files.list(dirPath)) {
                boolean found = paths.anyMatch(path -> 
                    pathMatcher.matches(path.getFileName()));
                
                if (found) {
                    remarks.append("Found file matching pattern: ").append(pattern).append("\n"); //No I18N
                } else {
                    remarks.append("No files found matching pattern: ").append(pattern).append("\n"); //No I18N
                }
                
                return found;
            }
        } catch (IOException e) {
            remarks.append("Error checking files with pattern: ").append(e.getMessage()).append("\n"); //No I18N
            LOGGER.log(Level.WARNING, "Error checking files with pattern", e);
            return false;
        }
    }
    
    /**
     * Check if a file contains specific content
     */
    private static boolean checkFileContains(Operation op, StringBuilder remarks) {
        String filePath = op.getParameter("file_path"); //No I18N
        if (filePath.contains("server_home")) { //No I18N
            filePath = resolvePath(filePath);
        }
        String filename = op.getParameter("filename"); //No I18N
        String content = op.getParameter("content"); //No I18N
        String theSearchValue = "";

        if (filePath == null || filePath.isEmpty() || 
            filename == null || filename.isEmpty() || 
            content == null || content.isEmpty()) {
            remarks.append("Error: file_path, filename and content parameters are required\n"); //No I18N
            return false;
        }
        
        // Normalize paths to use proper system separators
        filePath = filePath.replace('\\', File.separatorChar).replace('/', File.separatorChar);
        
        File file = new File(filePath, filename);
        if (!file.exists()) {
            remarks.append("File does not exist: ").append(file.getAbsolutePath()).append("\n"); //No I18N
            return false;
        }

        try {
            if (currentTime == null || currentTime.isEmpty()) {
                currentTime = buildDateTime(); //NO I18N
            }
//                currentTime = "[02:05:27:267]|[04-18-2025]"; //NO I18N
            theSearchValue = extractValueFromLog(op,String.valueOf(file), currentTime, op.getParameter("content"), op.hasParameter("note") ? op.getParameter("note") : ""); //NO I18N
        }catch (Exception e){
            LOGGER.log(Level.WARNING,"Error extracting value from log", e); //No I18N
        }

        if (theSearchValue == null || theSearchValue.isEmpty()) {
            try {
                if (!filename.endsWith(".log") && !filename.endsWith(".txt")) { //No I18N
                    String fileContent = new String(Files.readAllBytes(file.toPath()));
                    boolean contains = fileContent.contains(content);

                    remarks.append("Checking file: ").append(file.getAbsolutePath()).append("\n"); //No I18N
                    remarks.append("File contains the specified content: ").append(contains).append("\n"); //No I18N

                    return contains;
                }else {
                    return false;
                }
            } catch (IOException e) {
                remarks.append("Error reading file: ").append(e.getMessage()).append("\n"); //No I18N
                LOGGER.log(Level.WARNING, "Error reading file", e);
                return false;
            }
        }else {
            saveNote(op, theSearchValue);
            return true;
        }
    }
    
    /**
     * Check if a process is running
     */
    private static boolean checkProcessRunning(Operation op, StringBuilder remarks) {
        String processName = op.getParameter("process_name"); //No I18N
        String processPath = op.getParameter("process_path"); // Optional //No I18N
        if (processPath.contains("server_home")) { //No I18N
            processPath = resolvePath(processPath);
        }
        
        if (processName == null || processName.isEmpty()) {
            remarks.append("Error: process_name parameter is required\n"); //No I18N
            return false;
        }
        
        // Create a mock operation to pass to TaskManagerHandler
        Operation taskManagerOp = new Operation("task_manager"); //No I18N
        taskManagerOp.setParameter("action", "verify_process"); //No I18N
        taskManagerOp.setParameter("process_name", processName); //No I18N
        taskManagerOp.setParameter("expect", "processRunning"); //No I18N
        
        if (processPath != null && !processPath.isEmpty()) {
            taskManagerOp.setParameter("process_path", processPath); //No I18N
        }
        
        boolean isRunning = TaskManagerHandler.executeOperation(taskManagerOp);
        
        remarks.append("Checking if process is running: ").append(processName).append("\n"); //No I18N
        if (processPath != null && !processPath.isEmpty()) {
            remarks.append("At path: ").append(processPath).append("\n"); //No I18N
        }
        remarks.append("Process is running: ").append(isRunning).append("\n"); //No I18N
        
        if (taskManagerOp.getRemarks() != null) {
            remarks.append("Details: ").append(taskManagerOp.getRemarks()).append("\n"); //No I18N
        }
        
        return isRunning;
    }
    
    /**
     * Check if a process is not running
     */
    private static boolean checkProcessNotRunning(Operation op, StringBuilder remarks) {
        String processName = op.getParameter("process_name"); //No I18N
        String processPath = op.getParameter("process_path"); // Optional //No I18N
        if (processPath.contains("server_home")) { //No I18N
            processPath = resolvePath(processPath);
        }
        
        if (processName == null || processName.isEmpty()) {
            remarks.append("Error: process_name parameter is required\n"); //No I18N
            return false;
        }
        
        // Create a mock operation to pass to TaskManagerHandler
        Operation taskManagerOp = new Operation("task_manager"); //No I18N
        taskManagerOp.setParameter("action", "verify_process"); //No I18N
        taskManagerOp.setParameter("process_name", processName); //No I18N
        taskManagerOp.setParameter("expect", "processNotRunning"); //No I18N
        
        if (processPath != null && !processPath.isEmpty()) {
            taskManagerOp.setParameter("process_path", processPath); //No I18N
        }
        
        boolean isNotRunning = TaskManagerHandler.executeOperation(taskManagerOp);
        
        remarks.append("Checking if process is not running: ").append(processName).append("\n"); //No I18N
        if (processPath != null && !processPath.isEmpty()) {
            remarks.append("At path: ").append(processPath).append("\n"); //No I18N
        }
        remarks.append("Process is not running: ").append(isNotRunning).append("\n"); //No I18N
        
        if (taskManagerOp.getRemarks() != null) {
            remarks.append("Details: ").append(taskManagerOp.getRemarks()).append("\n"); //No I18N
        }
        
        return isNotRunning;
    }
    
    /**
     * Check if a service is running
     */
    private static boolean checkServiceRunning(Operation op, StringBuilder remarks) {
        String serviceName = op.getParameter("service_name"); //No I18N
        
        if (serviceName == null || serviceName.isEmpty()) {
            remarks.append("Error: service_name parameter is required\n"); //No I18N
            return false;
        }
        
        // Create a mock operation to pass to ServiceManagementHandler
        Operation serviceOp = new Operation("service_actions"); //No I18N
        serviceOp.setParameter("action", "status"); //No I18N
        serviceOp.setParameter("service_name", serviceName); //No I18N
        serviceOp.setParameter("expect", "serviceRunning"); //No I18N
        
        boolean isRunning = ServiceManagementHandler.executeOperation(serviceOp);
        
        remarks.append("Checking if service is running: ").append(serviceName).append("\n"); //No I18N
        remarks.append("Service is running: ").append(isRunning).append("\n"); //No I18N
        
        if (serviceOp.getRemarks() != null) {
            remarks.append("Details: ").append(serviceOp.getRemarks()).append("\n"); //No I18N
        }
        
        return isRunning;
    }
    
    /**
     * Check if a service is stopped
     */
    private static boolean checkServiceStopped(Operation op, StringBuilder remarks) {
        String serviceName = op.getParameter("service_name"); //No I18N
        
        if (serviceName == null || serviceName.isEmpty()) {
            remarks.append("Error: service_name parameter is required\n"); //No I18N
            return false;
        }
        
        // Create a mock operation to pass to ServiceManagementHandler
        Operation serviceOp = new Operation("service_actions"); //No I18N
        serviceOp.setParameter("action", "status"); //No I18N
        serviceOp.setParameter("service_name", serviceName); //No I18N
        serviceOp.setParameter("expect", "serviceStopped"); //No I18N
        
        boolean isStopped = ServiceManagementHandler.executeOperation(serviceOp);
        
        remarks.append("Checking if service is stopped: ").append(serviceName).append("\n"); //No I18N
        remarks.append("Service is stopped: ").append(isStopped).append("\n"); //No I18N
        
        if (serviceOp.getRemarks() != null) {
            remarks.append("Details: ").append(serviceOp.getRemarks()).append("\n"); //No I18N
        }
        
        return isStopped;
    }
    
    /**
     * Get a parameter value as a long, with a default if not present or invalid
     */
    private static long getParameterAsLong(Operation op, String paramName, long defaultValue) {
        Object value = op.getParameterObject(paramName); 
        
        // If value is null or empty string, return default
        if (value == null || (value instanceof String && ((String)value).isEmpty())) {
            return defaultValue;
        }
        
        // Handle numeric types directly
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        
        // Try to parse as string
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            try {
                // Try parsing as double and then convert to long
                return (long)Double.parseDouble(value.toString());
            } catch (NumberFormatException e2) {
                LOGGER.warning("Invalid " + paramName + " value: " + value + ". Using default: " + defaultValue); //No I18N
                return defaultValue;
            }
        }
    }
    
    /**
     * Resolve placeholders in path strings like server_home
     * 
     * @param path Path that may contain placeholders
     * @return Resolved path with placeholders replaced with actual paths
     */
    private static String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        
        // Handle the server_home placeholder
        if (path.contains("server_home")) { //No I18N
            // First, ensure we're using the test context for server.home

//            String serverHome = System.getProperty("server.home"); //No I18N

            // If still null, fall back to product server home directly
//            if (serverHome == null) {
            String serverHome = ServerUtils.getProductServerHome();
//                ServerUtils.setupServerHomeProperty("test"); //No I18N
//
//            }

            // Get the server home from system property (set up by ServerUtils)
            
            LOGGER.info("Resolved server_home to: " + serverHome); //No I18N
            
            // Replace server_home with actual path
            path = path.replace("server_home", serverHome); //No I18N
        }
        
        // Could handle other path placeholders here as needed
        
        return path;
    }
    
    /**
     * Main method for standalone testing
     */
    // public static void main(String[] args) {
    //     System.out.println("WaitForConditionHandler Test"); //No I18N
        
    //     // Test waiting for a file to exist
    //     Operation op = new Operation("wait_for_condition"); //No I18N
    //     op.setParameter("condition_type", "file_exists"); //No I18N
    //     op.setParameter("file_path", "."); //No I18N
    //     op.setParameter("filename", "test_file.txt"); //No I18N
    //     op.setParameter("max_wait_time", "30"); //No I18N
    //     op.setParameter("check_interval", "2"); //No I18N
        
    //     System.out.println("Waiting for file to exist. Please create a file named 'test_file.txt' in the current directory."); //No I18N
    //     boolean result = executeOperation(op);
    //     System.out.println("Result: " + result); //No I18N
    //     System.out.println("Remarks: " + op.getRemarks()); //No I18N
    // }
}
