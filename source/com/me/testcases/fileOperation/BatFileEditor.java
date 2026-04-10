package com.me.testcases.fileOperation;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Handler for BAT file operations
 */
public class BatFileEditor {
    private static final Logger LOGGER = LogManager.getLogger(BatFileEditor.class, LogManager.LOG_TYPE.FW);
    private static final int PROCESS_TIMEOUT_SECONDS = 60;

    /**
     * Execute a BAT file operation
     *
     * @param operation The operation containing parameters
     * @return true if operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation operation) {
        if (operation == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        operation = ResolveOperationParameters.resolveOperationParameters(operation);

        String action = operation.getParameter("action");
        String filePath = operation.getParameter("file_path");

        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for BAT file operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for BAT file operation");
            return false;
        }

        // Create file object
        File file = new File(filePath);

        // For create action, we don't need the file to exist
        if (!file.exists() && !action.equalsIgnoreCase("create")) {
            String errorMsg = "BAT file not found: " + file.getAbsolutePath();
            LOGGER.warning(errorMsg);
            operation.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("BAT File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            boolean success;

            switch (action.toLowerCase()) {
                case "create":
                    success = createBatFile(file, operation, remarkBuilder);
                    break;
//                case "execute":
//                    success = executeBatFile(file, operation, remarkBuilder);
//                    break;
                case "read":
                    success = readBatFile(file, operation, remarkBuilder);
                    break;
                case "modify":
                    success = modifyBatFile(file, operation, remarkBuilder);
                    break;
                case "analyze":
                    success = analyzeBatFile(file, operation, remarkBuilder);
                    break;
                case "append":
                    success = appendToBatFile(file, operation, remarkBuilder);
                    break;
                case "value_should_be_present":
                    success = checkValuePresent(file, operation, remarkBuilder);
                    break;
                case "value_should_be_removed":
                    success = checkValueRemoved(file, operation, remarkBuilder);
                    break;
                case "get_value":
                    success = getValue(file, operation, remarkBuilder);
                    break;
                case "remove_value":
                    success = removeValue(file, operation, remarkBuilder);
                    break;
//                case "execute_and_get_value":
//                    success = executeBatAndGetValue(file, operation, remarkBuilder);
//                    checkNote(success, operation, remarkBuilder);
//                    break;
//                case "execute_interactive":
//                    success = executeInteractiveBat(file, operation, remarkBuilder);
//                    break;
                default:
                    String errorMsg = "Unsupported action for BAT file: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing BAT file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }

    /**
     * Check if a specific value is present in the BAT file
     */
    private static boolean checkValuePresent(File file, Operation operation, StringBuilder remarks) throws IOException {
        String key = operation.getParameter("key");
        String value = operation.getParameter("value");
        String searchType = operation.getParameter("search_type"); // exact, contains, regex

        if (key == null || key.isEmpty()) {
            remarks.append("Error: key parameter is required for value_should_be_present action");
            return false;
        }

        // Read file content
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");

        // Default to contains search if not specified
        if (searchType == null || searchType.isEmpty()) {
            searchType = "contains";
        }

        boolean found = false;
        String matchedLine = null;

        for (String line : lines) {
            // Skip comments and empty lines
            if (line.trim().isEmpty() || line.trim().startsWith("::") || line.trim().startsWith("REM ")) {
                continue;
            }

            switch (searchType.toLowerCase()) {
                case "exact":
                    if (value != null && !value.isEmpty()) {
                        if (line.matches("(?i)set\\s+\"?" + Pattern.quote(key) + "\"?\\s*=\\s*\"?" + Pattern.quote(value) + "\"?")) {
                            found = true;
                            matchedLine = line;
                        }
                    } else if (line.matches("(?i).*" + Pattern.quote(key) + ".*")) {
                        found = true;
                        matchedLine = line;
                    }
                    break;

                case "regex":
                    try {
                        Pattern pattern = Pattern.compile(key, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            if (value != null && !value.isEmpty()) {
                                Pattern valuePattern = Pattern.compile(value, Pattern.CASE_INSENSITIVE);
                                if (line.matches("(?i)set\\s+.*=\\s*.*") && valuePattern.matcher(line.substring(line.indexOf('=') + 1).trim()).find()) {
                                    found = true;
                                    matchedLine = line;
                                }
                            } else {
                                found = true;
                                matchedLine = line;
                            }
                        }
                    } catch (PatternSyntaxException e) {
                        remarks.append("Error: Invalid regex pattern: ").append(e.getMessage());
                        return false;
                    }
                    break;

                case "contains":
                default:
                    if (line.toLowerCase().contains(key.toLowerCase())) {
                        if (value != null && !value.isEmpty()) {
                            int equalsPos = line.indexOf('=');
                            if (equalsPos >= 0 &&
                                    line.substring(equalsPos + 1).trim().toLowerCase().contains(value.toLowerCase())) {
                                found = true;
                                matchedLine = line;
                            }
                        } else {
                            found = true;
                            matchedLine = line;
                        }
                    }
                    break;
            }

            if (found) break;
        }

        operation.setParameter("found", String.valueOf(found));
        if (matchedLine != null) {
            operation.setParameter("matched_line", matchedLine);
        }

        if (found) {
            remarks.append("Value '").append(key).append("'");
            if (value != null && !value.isEmpty()) {
                remarks.append(" with value '").append(value).append("'");
            }
            remarks.append(" was found in the BAT file");
            return true;
        } else {
            remarks.append("Value '").append(key).append("'");
            if (value != null && !value.isEmpty()) {
                remarks.append(" with value '").append(value).append("'");
            }
            remarks.append(" was NOT found in the BAT file");
            return false;
        }
    }

    /**
     * Check if a specific value is removed/absent from the BAT file
     */
    private static boolean checkValueRemoved(File file, Operation operation, StringBuilder remarks) throws IOException {
        // Inverse of checkValuePresent
        boolean isPresent = checkValuePresent(file, operation, new StringBuilder());

        String key = operation.getParameter("key");
        String value = operation.getParameter("value");

        if (isPresent) {
            remarks.append("Value '").append(key).append("'");
            if (value != null && !value.isEmpty()) {
                remarks.append(" with value '").append(value).append("'");
            }
            remarks.append(" was found in the BAT file but should be removed");
            return false;
        } else {
            remarks.append("Value '").append(key).append("'");
            if (value != null && !value.isEmpty()) {
                remarks.append(" with value '").append(value).append("'");
            }
            remarks.append(" is correctly absent from the BAT file");
            return true;
        }
    }

    /**
     * Get a value from the BAT file
     */
    private static boolean getValue(File file, Operation operation, StringBuilder remarks) throws IOException {
        String key = operation.getParameter("key");
        String defaultValue = operation.getParameter("default_value");

        if (key == null || key.isEmpty()) {
            remarks.append("Error: key parameter is required for get_value action");
            return false;
        }

        // Read file content
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");

        String foundValue = null;
        String matchedLine = null;

        // Look for variable assignments
        Pattern pattern = Pattern.compile("(?i)set\\s+\"?(" + Pattern.quote(key) + ")\"?\\s*=\\s*\"?([^\"]*)\"?");

        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("::") || line.trim().startsWith("REM ")) {
                continue;
            }

            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                foundValue = matcher.group(2).trim();
                matchedLine = line;
                break;
            }
        }

        if (foundValue != null) {
            operation.setParameter("value", foundValue);
            operation.setParameter("found", "true");
            operation.setParameter("matched_line", matchedLine);

            remarks.append("Value for key '").append(key).append("' was found: '").append(foundValue).append("'");
            return true;
        } else {
            operation.setParameter("found", "false");
            if (defaultValue != null) {
                operation.setParameter("value", defaultValue);
                remarks.append("Key '").append(key).append("' was not found, using default value: '").append(defaultValue).append("'");
            } else {
                operation.setParameter("value", "");
                remarks.append("Key '").append(key).append("' was not found in the BAT file");
            }
            return defaultValue != null;
        }
    }

    /**
     * Remove a value from the BAT file
     */
    private static boolean removeValue(File file, Operation operation, StringBuilder remarks) throws IOException {
        String key = operation.getParameter("key");
        String backupPath = operation.getParameter("backup_path");

        if (key == null || key.isEmpty()) {
            remarks.append("Error: key parameter is required for remove_value action");
            return false;
        }

        // Read file content
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String originalContent = content;
        String[] lines = content.split("\\r?\\n");

        StringBuilder newContent = new StringBuilder();
        int removedCount = 0;
        List<String> removedLines = new ArrayList<>();

        Pattern pattern = Pattern.compile("(?i)set\\s+\"?(" + Pattern.quote(key) + ")\"?\\s*=.*");

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                removedCount++;
                removedLines.add(line);
                // Skip this line
            } else {
                newContent.append(line).append("\r\n");
            }
        }

        // Create backup if requested
        if (backupPath != null && !backupPath.isEmpty()) {
            File backupFile = new File(backupPath);
            if (backupFile.getParentFile() != null && !backupFile.getParentFile().exists()) {
                backupFile.getParentFile().mkdirs();
            }
            Files.write(backupFile.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));
            remarks.append("Created backup at: ").append(backupFile.getAbsolutePath()).append("\n");
        }

        if (removedCount > 0) {
            Files.write(file.toPath(), newContent.toString().getBytes(StandardCharsets.UTF_8));

            operation.setParameter("removed_count", String.valueOf(removedCount));
            operation.setParameter("removed_lines", String.join("\n", removedLines));

            remarks.append("Removed ").append(removedCount).append(" occurrences of key '").append(key).append("' from the BAT file");
            return true;
        } else {
            remarks.append("Key '").append(key).append("' was not found in the BAT file, no changes made");
            operation.setParameter("removed_count", "0");
            return true;
        }
    }



    /**
     * Create a new BAT file
     */
    private static boolean createBatFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String content = operation.getParameter("content");
        String template = operation.getParameter("template");

        // Create parent directories if they don't exist
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        StringBuilder batContent = new StringBuilder();

        // Add standard header for better compatibility
        batContent.append("@echo off\r\n");
        batContent.append("setlocal enabledelayedexpansion\r\n\r\n");

        // Add content from template if provided
        if (template != null && !template.isEmpty()) {
            switch (template.toLowerCase()) {
                case "simple":
                    batContent.append(":: Simple batch script template\r\n");
                    batContent.append(":: Created: ").append(new Date()).append("\r\n\r\n");
                    batContent.append("echo Starting script execution...\r\n\r\n");
                    batContent.append(":: Your commands here\r\n\r\n");
                    batContent.append("echo Script completed.\r\n");
                    batContent.append("exit /b 0\r\n");
                    break;

                case "advanced":
                    batContent.append(":: Advanced batch script template\r\n");
                    batContent.append(":: Created: ").append(new Date()).append("\r\n\r\n");
                    batContent.append("title Advanced Batch Script\r\n\r\n");

                    batContent.append(":: Error handling\r\n");
                    batContent.append("set \"ERROR_FILE=%temp%\\batch_error.txt\"\r\n");
                    batContent.append("set \"ERROR_OCCURRED=false\"\r\n\r\n");

                    batContent.append("echo [%date% %time%] Script started > \"%ERROR_FILE%\"\r\n\r\n");

                    batContent.append(":: Parameters handling\r\n");
                    batContent.append("if \"%~1\"==\"\" (\r\n");
                    batContent.append("    echo No parameters provided.\r\n");
                    batContent.append("    echo Usage: %~nx0 [parameter]\r\n");
                    batContent.append(") else (\r\n");
                    batContent.append("    echo Parameter received: %~1\r\n");
                    batContent.append(")\r\n\r\n");

                    batContent.append(":: Main script\r\n");
                    batContent.append("echo Starting main process...\r\n\r\n");
                    batContent.append(":: Your commands here\r\n\r\n");

                    batContent.append(":: Error checking\r\n");
                    batContent.append("if !ERRORLEVEL! NEQ 0 (\r\n");
                    batContent.append("    set \"ERROR_OCCURRED=true\"\r\n");
                    batContent.append("    echo [%date% %time%] Error occurred: !ERRORLEVEL! >> \"%ERROR_FILE%\"\r\n");
                    batContent.append(")\r\n\r\n");

                    batContent.append(":: Cleanup\r\n");
                    batContent.append("if \"%ERROR_OCCURRED%\"==\"true\" (\r\n");
                    batContent.append("    echo Errors occurred during execution. Check %ERROR_FILE% for details.\r\n");
                    batContent.append("    exit /b 1\r\n");
                    batContent.append(") else (\r\n");
                    batContent.append("    echo Script completed successfully.\r\n");
                    batContent.append("    del \"%ERROR_FILE%\" 2>nul\r\n");
                    batContent.append("    exit /b 0\r\n");
                    batContent.append(")\r\n");
                    break;

                case "service":
                    batContent.append(":: Service management batch script template\r\n");
                    batContent.append(":: Created: ").append(new Date()).append("\r\n\r\n");

                    batContent.append(":: Service name to manage\r\n");
                    batContent.append("set SERVICE_NAME=YourServiceName\r\n\r\n");

                    batContent.append(":: Command parameter\r\n");
                    batContent.append("if \"%~1\"==\"\" goto :usage\r\n\r\n");

                    batContent.append("if /i \"%~1\"==\"status\" goto :status\r\n");
                    batContent.append("if /i \"%~1\"==\"start\" goto :start\r\n");
                    batContent.append("if /i \"%~1\"==\"stop\" goto :stop\r\n");
                    batContent.append("if /i \"%~1\"==\"restart\" goto :restart\r\n");
                    batContent.append("goto :usage\r\n\r\n");

                    batContent.append(":status\r\n");
                    batContent.append("sc query %SERVICE_NAME%\r\n");
                    batContent.append("goto :eof\r\n\r\n");

                    batContent.append(":start\r\n");
                    batContent.append("echo Starting %SERVICE_NAME% service...\r\n");
                    batContent.append("sc start %SERVICE_NAME%\r\n");
                    batContent.append("goto :eof\r\n\r\n");

                    batContent.append(":stop\r\n");
                    batContent.append("echo Stopping %SERVICE_NAME% service...\r\n");
                    batContent.append("sc stop %SERVICE_NAME%\r\n");
                    batContent.append("goto :eof\r\n\r\n");

                    batContent.append(":restart\r\n");
                    batContent.append("echo Restarting %SERVICE_NAME% service...\r\n");
                    batContent.append("sc stop %SERVICE_NAME%\r\n");
                    batContent.append("timeout /t 5\r\n");
                    batContent.append("sc start %SERVICE_NAME%\r\n");
                    batContent.append("goto :eof\r\n\r\n");

                    batContent.append(":usage\r\n");
                    batContent.append("echo Usage: %~nx0 [status^|start^|stop^|restart]\r\n");
                    batContent.append("exit /b 1\r\n");
                    break;

                case "backup":
                    batContent.append(":: Backup script template\r\n");
                    batContent.append(":: Created: ").append(new Date()).append("\r\n\r\n");

                    batContent.append(":: Configuration\r\n");
                    batContent.append("set SOURCE_DIR=C:\\Data\r\n");
                    batContent.append("set BACKUP_DIR=D:\\Backups\r\n");
                    batContent.append("set BACKUP_NAME=Backup_%date:~10,4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%\r\n");
                    batContent.append("set BACKUP_NAME=%BACKUP_NAME: =0%\r\n\r\n");

                    batContent.append("echo Starting backup process: %date% %time%\r\n");
                    batContent.append("echo Source: %SOURCE_DIR%\r\n");
                    batContent.append("echo Destination: %BACKUP_DIR%\\%BACKUP_NAME%\r\n\r\n");

                    batContent.append(":: Create backup directory\r\n");
                    batContent.append("if not exist \"%BACKUP_DIR%\" mkdir \"%BACKUP_DIR%\"\r\n");
                    batContent.append("if not exist \"%BACKUP_DIR%\\%BACKUP_NAME%\" mkdir \"%BACKUP_DIR%\\%BACKUP_NAME%\"\r\n\r\n");

                    batContent.append(":: Copy files\r\n");
                    batContent.append("echo Copying files...\r\n");
                    batContent.append("xcopy \"%SOURCE_DIR%\\*.*\" \"%BACKUP_DIR%\\%BACKUP_NAME%\" /E /C /I /H /Y\r\n\r\n");

                    batContent.append(":: Check for errors\r\n");
                    batContent.append("if %ERRORLEVEL% NEQ 0 (\r\n");
                    batContent.append("    echo Error occurred during backup.\r\n");
                    batContent.append("    exit /b %ERRORLEVEL%\r\n");
                    batContent.append(")\r\n\r\n");

                    batContent.append("echo Backup completed successfully: %date% %time%\r\n");
                    batContent.append("exit /b 0\r\n");
                    break;

                default:
                    batContent.append(":: Basic batch script\r\n");
                    batContent.append(":: Created: ").append(new Date()).append("\r\n\r\n");
                    batContent.append("echo Hello, World!\r\n");
                    batContent.append("pause\r\n");
            }
        }

        // Add custom content if provided
        if (content != null && !content.isEmpty()) {
            // If we already have template content, add a separator
            if (template != null && !template.isEmpty()) {
                batContent.append("\r\n:: Custom content\r\n");
            }

            // Ensure proper line endings for Windows
            content = content.replace("\n", "\r\n").replace("\r\r\n", "\r\n");
            batContent.append(content);

            // Ensure the file ends with a newline
            if (!content.endsWith("\r\n")) {
                batContent.append("\r\n");
            }
        }

        // Save to file
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(batContent.toString());
        }

        remarks.append("Created BAT file at: ").append(file.getAbsolutePath());
        if (template != null && !template.isEmpty()) {
            remarks.append("\nUsing template: ").append(template);
        }

        return true;
    }


    public static boolean executeBatAndGetValue(File file, Operation operation, StringBuilder remarks) throws IOException, InterruptedException {
        String args = operation.getParameter("args");
        String workingDirPath = operation.getParameter("working_dir");
        String timeoutStr = operation.getParameter("timeout"); // in seconds
        boolean captureOutput = Boolean.parseBoolean(operation.getParameter("capture_output"));

        // Parameters for output content extraction
        String outputSearchText = operation.getParameter("output_search_text");
        String outputValuePattern = operation.getParameter("output_value_pattern");
        String outputValueDirect = operation.getParameter("output_value_direct");
//        String outputValueParameter = operation.getParameter("output_value_parameter");

        // Set working directory and process timeout (existing code)
        File workingDir = (workingDirPath != null && !workingDirPath.isEmpty()) ?
                new File(workingDirPath) : file.getParentFile();

        if (workingDir == null || !workingDir.exists() || !workingDir.isDirectory()) {
            workingDir = new File(System.getProperty("user.dir"));
            remarks.append("Specified working directory does not exist, using current directory instead.\n");
        }

        // Set timeout
        int timeout = PROCESS_TIMEOUT_SECONDS;
        if (timeoutStr != null && !timeoutStr.isEmpty()) {
            try {
                timeout = Integer.parseInt(timeoutStr);
                if (timeout <= 0) {
                    timeout = PROCESS_TIMEOUT_SECONDS;
                }
            } catch (NumberFormatException e) {
                remarks.append("Invalid timeout value, using default.\n");
            }
        }

        // Build and execute command (existing code)
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add(file.getAbsolutePath());

        // Add arguments if provided
        if (args != null && !args.isEmpty()) {
            String[] argArray = parseCommandLineArgs(args);
            Collections.addAll(command, argArray);
        }

        LOGGER.info("Executing command: " + command);
        remarks.append("Executing BAT file: ").append(file.getAbsolutePath()).append("\n");
        remarks.append("Working directory: ").append(workingDir.getAbsolutePath()).append("\n");
        remarks.append("Command: ").append(command).append("\n");

        // Start process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder outputBuilder = new StringBuilder();

        // Always capture output if search text is specified
        boolean shouldCaptureOutput = captureOutput || outputSearchText != null;

        if (shouldCaptureOutput) {
            // Capture output with timeout
            ExecutorService outputReaderService = Executors.newSingleThreadExecutor();
            Future<?> outputFuture = outputReaderService.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.info("BAT output: " + line);
                        outputBuilder.append(line).append("\n");
                    }
                } catch (IOException e) {
                    LOGGER.warning("Error reading process output: " + e.getMessage());
                }
            });

            // Set read timeout (use half of main timeout or 5 seconds, whichever is smaller)
            int readTimeout = Math.min(5, Math.max(1, timeout / 2));
            try {
                outputFuture.get(readTimeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOGGER.warning("Output reading timed out after " + readTimeout + " seconds");
                outputFuture.cancel(true);
            } catch (ExecutionException e) {
                LOGGER.warning("Error in output reader: " + e.getCause().getMessage());
            } finally {
                outputReaderService.shutdownNow();
            }
        }

        // Wait for process to complete
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            remarks.append("Process timed out after ").append(timeout).append(" seconds\n");
            operation.setParameter("execution_status", "TIMEOUT");
//            return false;
        }

        int exitValue = process.exitValue();
        operation.setParameter("exit_code", String.valueOf(exitValue));

        // Process output content
        String output = outputBuilder.toString();
        if (!output.isEmpty()) {
            operation.setParameter("output", output);
            remarks.append("Process output:\n").append(output).append("\n");

            // Search for specific content in output if requested
            if (outputSearchText != null && !outputSearchText.isEmpty()) {
                boolean found = false;
                String matchedLine = null;
                String extractedValue = null;

                // Process each line for matching
                String[] lines = output.split("\\n");
                for (String line : lines) {
                    LOGGER.info("Checking line: " + line);
                    if (line.contains(outputSearchText)) {
                        LOGGER.info("Found search text in output: " + line);
                        found = true;
                        matchedLine = line;

                        // Extract value using regex pattern if provided
                        if (outputValuePattern != null && !outputValuePattern.isEmpty()) {
                            try {
                                LOGGER.info("Using regex pattern for extraction: " + outputValuePattern);
                                Pattern pattern = Pattern.compile(outputValuePattern);
                                Matcher matcher = pattern.matcher(line);
                                if (matcher.find()) {
                                    // Extract the first group or full match
                                    extractedValue = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
                                    LOGGER.info("Extracted value using regex: " + extractedValue);
                                }
                            } catch (PatternSyntaxException e) {
                                remarks.append("Invalid output value pattern: ").append(e.getMessage()).append("\n");
                                LOGGER.warning("Invalid output value pattern: " + e.getMessage());
                            }
                        }else {
                            LOGGER.info("No regex pattern specified for extraction");
                            LOGGER.info("extractedValue = "+extractedValue+", outputValueDirect = "+outputValueDirect+ ", matchedLine = "+matchedLine);
                        }

                        // If regex extraction failed or wasn't specified, use direct value if provided
                        if (extractedValue == null && outputValueDirect != null && !matchedLine.contains(outputValueDirect)) {
                            extractedValue = outputValueDirect;
                            remarks.append("Using direct value: ").append(outputValueDirect).append("\n");
                            LOGGER.info("Using direct value: " + extractedValue);
                        }else if (extractedValue == null && outputValueDirect != null) {
                            // If no extraction was done, use the matched line as the value
                            extractedValue = outputValueDirect;
                            remarks.append("Using matched line as value: ").append(extractedValue).append("\n");
                            LOGGER.info("Using matched line as value: " + extractedValue);

                        }

                        break;
                    }
                }

                // Store results
                operation.setParameter("output_search_found", String.valueOf(found));
                if (found) {
                    if (matchedLine != null) {
                        operation.setParameter("output_matched_line", matchedLine);
                        LOGGER.info("Matched line: " + matchedLine);
                    }
                    if (extractedValue != null) {
                        operation.setParameter("Found_output_value", extractedValue);
                        remarks.append("Extracted value from output: '").append(extractedValue)
                                .append("' (stored in parameter: ").append("Found_output_value").append(")\n");
                        remarks.append("Exit code: ").append(exitValue).append("\n");
                        LOGGER.info("Extracted value: " + extractedValue);
                        return true; // Indicate success
                    }
                } else {
                    remarks.append("Search text '").append(outputSearchText)
                            .append("' was not found in the output\n");
                    LOGGER.info("Search text '" + outputSearchText + "' was not found in the output");
                }
            }
        }


//        if (exitValue == 0) {
//            remarks.append("BAT file executed successfully");
//            operation.setParameter("execution_status", "SUCCESS");
//            return true;
//        } else {
//            remarks.append("BAT file execution failed with exit code ").append(exitValue);
//            operation.setParameter("execution_status", "FAILURE");
//            return false;
//        }
        return false; // Return false to indicate the method is not complete yet
    }


    /**
     * Parse command line arguments respecting quotes
     */
    private static String[] parseCommandLineArgs(String args) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';

        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);

            if (c == '"' || c == '\'') {
                if (inQuotes && c == quoteChar) {
                    // End of quoted string
                    inQuotes = false;
                } else if (!inQuotes) {
                    // Start of quoted string
                    inQuotes = true;
                    quoteChar = c;
                } else {
                    // Quote character inside different quote type
                    current.append(c);
                }
            } else if (Character.isWhitespace(c) && !inQuotes) {
                // Space outside of quotes means end of argument
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                // Regular character, add to current argument
                current.append(c);
            }
        }

        // Add the last argument if there is one
        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result.toArray(new String[0]);
    }

    /**
     * Read a BAT file
     */
    private static boolean readBatFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        // Read file content
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        operation.setParameter("content", content);

        // Count lines
        String[] lines = content.split("\\r?\\n");
        operation.setParameter("line_count", String.valueOf(lines.length));

        remarks.append("Read ").append(file.length()).append(" bytes from BAT file\n");
        remarks.append("File contains ").append(lines.length).append(" lines");

        return true;
    }

    /**
     * Modify a BAT file
     */
    private static boolean modifyBatFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String searchStr = operation.getParameter("search");
        String replaceStr = operation.getParameter("replace");
        String modifyTypeStr = operation.getParameter("modify_type"); // line, regex, variable
        String backupPath = operation.getParameter("backup_path");

        if (searchStr == null || searchStr.isEmpty()) {
            remarks.append("Search string not provided");
            return false;
        }

        if (replaceStr == null) {
            replaceStr = ""; // Allow empty replacement to delete content
        }

        // Read file content
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String originalContent = content;

        // Determine modification type
        String modifyType = modifyTypeStr != null ? modifyTypeStr.toLowerCase() : "line";
        int modificationCount = 0;

        switch (modifyType) {
            case "line":
                // Replace entire lines that contain the search string
                StringBuilder newContent = new StringBuilder();
                String[] lines = content.split("\\r?\\n");

                for (String line : lines) {
                    if (line.contains(searchStr)) {
                        newContent.append(replaceStr).append("\r\n");
                        modificationCount++;
                    } else {
                        newContent.append(line).append("\r\n");
                    }
                }

                content = newContent.toString();
                break;

            case "regex":
                // Replace using regular expressions
                try {
                    LOGGER.info("Regex pattern: '" + searchStr + "'");
                    LOGGER.info("Replacement string: '" + replaceStr + "'");

                    // If we want to match the literal string that looks like a regex pattern
                    String escapedSearchStr = Pattern.quote(searchStr);
                    LOGGER.info("Escaped search pattern: '" + escapedSearchStr + "'");

                    Pattern pattern = Pattern.compile(escapedSearchStr);
                    Matcher matcher = pattern.matcher(content);

                    StringBuffer sb = new StringBuffer();
                    modificationCount = 0;

                    while (matcher.find()) {
                        String matchText = matcher.group(0);
                        LOGGER.info("Found match: '" + matchText + "'");

                        String replacement = Matcher.quoteReplacement(replaceStr);
                        LOGGER.info("Using replacement (after escaping): '" + replacement + "'");

                        matcher.appendReplacement(sb, replacement);
                        modificationCount++;
                    }
                    matcher.appendTail(sb);

                    if (modificationCount > 0) {
                        LOGGER.info("Completed " + modificationCount + " replacements");
                        content = sb.toString();
                    } else {
                        LOGGER.warning("No matches found for pattern: " + searchStr);
                    }
                } catch (PatternSyntaxException e) {
                    LOGGER.warning("Invalid regex pattern: " + e.getMessage());
                    remarks.append("Error: Invalid regex pattern: ").append(e.getMessage());
                    return false;
                }
                break;

            case "variable":
                // For variable type modification with support for /p and other parameters
                Pattern varPattern = Pattern.compile("(set\\s+(?:/\\w+\\s+)?\"?" + Pattern.quote(searchStr) + "\"?\\s*=\\s*\")([^\"]*)(\")");
                Matcher varMatcher = varPattern.matcher(content);

                StringBuffer sb = new StringBuffer();
                boolean found = false;

                while (varMatcher.find()) {
                    found = true;
                    String fullMatch = varMatcher.group(0);
                    String group1 = varMatcher.group(1);
                    String group2 = varMatcher.group(2);
                    String group3 = varMatcher.group(3);

                    LOGGER.info("Full match: [" + fullMatch + "]");
                    LOGGER.info("Group 1: [" + group1 + "]");
                    LOGGER.info("Group 2: [" + group2 + "]");
                    LOGGER.info("Group 3: [" + group3 + "]");

                    varMatcher.appendReplacement(sb, Matcher.quoteReplacement(group1 + replaceStr + group3));
                    modificationCount++;
                }
                varMatcher.appendTail(sb);

                if (!found) {
                    LOGGER.warning("No match found for search string: " + searchStr);
                    remarks.append("No match found for variable: " + searchStr);
                }

                content = sb.toString();
                break;

            default:
                remarks.append("Unknown modification type: ").append(modifyType).append(", using line modification");
                // Fall back to line modification
                StringBuilder defaultContent = new StringBuilder();
                String[] defaultLines = content.split("\\r?\\n");

                for (String line : defaultLines) {
                    if (line.contains(searchStr)) {
                        defaultContent.append(replaceStr).append("\r\n");
                        modificationCount++;
                    } else {
                        defaultContent.append(line).append("\r\n");
                    }
                }

                content = defaultContent.toString();
        }

        // Create backup if requested
        if (backupPath != null && !backupPath.isEmpty()) {
            File backupFile = new File(backupPath);
            if (backupFile.getParentFile() != null && !backupFile.getParentFile().exists()) {
                backupFile.getParentFile().mkdirs();
            }

            Files.write(backupFile.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));
            remarks.append("Created backup at: ").append(backupFile.getAbsolutePath()).append("\n");
        }

        // Write modified content if changes were made
        if (!originalContent.equals(content)) {
            if (modificationCount == 0) {
                remarks.append("No Search value found in bat file So No modifications were made to the BAT file");
                operation.setParameter("modifications", "0");
                return false;
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            remarks.append("Modified ").append(modificationCount).append(" occurrences in BAT file");
            operation.setParameter("modifications", String.valueOf(modificationCount));
            return true;
        } else {
            remarks.append("No modifications were made to the BAT file");
            operation.setParameter("modifications", "0");
            return false;
        }
    }

    /**
     * Analyze a BAT file
     */
    private static boolean analyzeBatFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");

        // Basic statistics
        int commentCount = 0;
        int commandCount = 0;
        int variableCount = 0;
        int labelCount = 0;
        int gotoCount = 0;
        int ifCount = 0;
        int forCount = 0;
        int callCount = 0;

        // Command frequency map
        Map<String, Integer> commandFrequency = new HashMap<>();

        // Variables found
        Set<String> variables = new HashSet<>();

        // Labels found
        Set<String> labels = new HashSet<>();

        // Analyze each line
        for (String line : lines) {
            line = line.trim();

            // Skip empty lines
            if (line.isEmpty()) {
                continue;
            }

            // Comments
            if (line.startsWith("::") || line.startsWith("REM ") || line.startsWith("rem ")) {
                commentCount++;
                continue;
            }

            // Labels
            if (line.startsWith(":")) {
                labelCount++;
                String labelName = line.substring(1).trim();
                if (!labelName.isEmpty()) {
                    labels.add(labelName);
                }
                continue;
            }

            // Commands
            commandCount++;

            // Analyze command type
            String lowerLine = line.toLowerCase();

            // Extract command
            String command = lowerLine;
            int firstSpace = lowerLine.indexOf(' ');
            if (firstSpace > 0) {
                command = lowerLine.substring(0, firstSpace);
            }

            // Update command frequency
            commandFrequency.put(command, commandFrequency.getOrDefault(command, 0) + 1);

            // Count specific command types
            if (lowerLine.startsWith("goto ") || lowerLine.equals("goto")) {
                gotoCount++;
            } else if (lowerLine.startsWith("if ")) {
                ifCount++;
            } else if (lowerLine.startsWith("for ")) {
                forCount++;
            } else if (lowerLine.startsWith("call ")) {
                callCount++;
            } else if (lowerLine.startsWith("set ")) {
                variableCount++;

                // Extract variable name
                Pattern varPattern = Pattern.compile("set\\s+\"?(\\w+)\"?\\s*=");
                Matcher matcher = varPattern.matcher(line);
                if (matcher.find()) {
                    variables.add(matcher.group(1));
                }
            }
        }

        // Calculate code to comment ratio
        double codeToCommentRatio = commentCount > 0 ? (double) commandCount / commentCount : commandCount;

        // Store analysis results in operation parameters
        operation.setParameter("line_count", String.valueOf(lines.length));
        operation.setParameter("comment_count", String.valueOf(commentCount));
        operation.setParameter("command_count", String.valueOf(commandCount));
        operation.setParameter("variable_count", String.valueOf(variables.size()));
        operation.setParameter("label_count", String.valueOf(labels.size()));
        operation.setParameter("goto_count", String.valueOf(gotoCount));
        operation.setParameter("if_count", String.valueOf(ifCount));
        operation.setParameter("for_count", String.valueOf(forCount));
        operation.setParameter("call_count", String.valueOf(callCount));
        operation.setParameter("code_comment_ratio", String.format("%.2f", codeToCommentRatio));

        // Store list of variables and labels
        operation.setParameter("variables", String.join(", ", variables));
        operation.setParameter("labels", String.join(", ", labels));

        // Get top 5 most used commands
        List<Map.Entry<String, Integer>> sortedCommands = new ArrayList<>(commandFrequency.entrySet());
        sortedCommands.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        StringBuilder topCommands = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Integer> entry : sortedCommands) {
            if (count++ >= 5) break;
            if (topCommands.length() > 0) topCommands.append(", ");
            topCommands.append(entry.getKey()).append(" (").append(entry.getValue()).append(")");
        }
        operation.setParameter("top_commands", topCommands.toString());

        // Build remarks
        remarks.append("BAT File Analysis:\n");
        remarks.append("- Total lines: ").append(lines.length).append("\n");
        remarks.append("- Comments: ").append(commentCount).append("\n");
        remarks.append("- Commands: ").append(commandCount).append("\n");
        remarks.append("- Variables defined: ").append(variables.size()).append("\n");
        remarks.append("- Labels: ").append(labels.size()).append("\n");
        remarks.append("\nControl structures:\n");
        remarks.append("- IF statements: ").append(ifCount).append("\n");
        remarks.append("- FOR loops: ").append(forCount).append("\n");
        remarks.append("- GOTO statements: ").append(gotoCount).append("\n");
        remarks.append("- CALL statements: ").append(callCount).append("\n");
        remarks.append("\nCode to comment ratio: ").append(String.format("%.2f", codeToCommentRatio)).append("\n");
        remarks.append("\nMost frequently used commands:\n");
        remarks.append(topCommands);

        return true;
    }

    /**
     * Append content to a BAT file
     */
    private static boolean appendToBatFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String content = operation.getParameter("content");
        boolean withSeparator = Boolean.parseBoolean(operation.getParameter("with_separator"));

        if (content == null || content.isEmpty()) {
            remarks.append("No content provided to append");
            return false;
        }

        // Ensure proper line endings for Windows
        content = content.replace("\n", "\r\n").replace("\r\r\n", "\r\n");

        // Read existing file content
        String existingContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        // Create StringBuilder for appending
        StringBuilder newContent = new StringBuilder(existingContent);

        // Add separator if requested and file is not empty
        if (withSeparator && !existingContent.trim().isEmpty()) {
            if (!existingContent.endsWith("\r\n")) {
                newContent.append("\r\n");
            }
            newContent.append("\r\n:: ").append(repeatString("=", 50)).append("\r\n");
            newContent.append(":: Appended on: ").append(new Date()).append("\r\n");
            newContent.append(":: ").append(repeatString("=", 50)).append("\r\n\r\n");
        } else if (!existingContent.endsWith("\r\n") && !existingContent.isEmpty()) {
            newContent.append("\r\n");
        }

        // Append new content
        newContent.append(content);

        // Ensure the file ends with a newline
        if (!newContent.toString().endsWith("\r\n")) {
            newContent.append("\r\n");
        }

        // Write back to file
        Files.write(file.toPath(), newContent.toString().getBytes(StandardCharsets.UTF_8));

        remarks.append("Appended ").append(content.length()).append(" characters to BAT file");

        return true;
    }
    /**
     * Helper method to repeat a string multiple times
     */
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}