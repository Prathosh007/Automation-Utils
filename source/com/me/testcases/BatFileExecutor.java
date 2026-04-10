package com.me.testcases;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;
import com.me.util.command.EnhancedUacHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static com.me.testcases.DataBaseOperationHandler.saveNote;
import static com.me.util.command.EnhancedUacHandler.terminateUACAutoItProcess;

/**
 * Handler for BAT file operations with enhanced multi-threading support
 */
public class BatFileExecutor {
    private static final Logger LOGGER = LogManager.getLogger(BatFileExecutor.class, LogManager.LOG_TYPE.FW);
    private static final int PROCESS_TIMEOUT_SECONDS = 1800; // 30 minutes
    private static boolean uacHandlerAvailable = false;

    // Static block to check for UAC handler availability
    static {
        try {
            Class.forName("com.me.util.command.EnhancedUacHandler");
            try {
                Class<?> uacHandlerClass = Class.forName("com.me.util.command.EnhancedUacHandler");
                java.lang.reflect.Method availableMethod = uacHandlerClass.getMethod("isAutoItAvailable");
                uacHandlerAvailable = (Boolean) availableMethod.invoke(null);
                LOGGER.info("Enhanced UAC handling " + (uacHandlerAvailable ? "available" : "NOT AVAILABLE"));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error checking AutoIt availability", e);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.info("EnhancedUacHandler not available");
        }
    }

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
                case "execute":
                    success = executeBatFile(file, operation, remarkBuilder);
                    break;
                case "execute_and_get_value":
                    success = executeBatAndGetValue(file, operation, remarkBuilder);
                    break;
                case "execute_interactive":
                    success = executeInteractiveBat(file, operation, remarkBuilder);
                    break;
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
            operation.setRemarks(remarkBuilder + "\nError: " + errorMsg);
            return false;
        }
    }

    /**
     * Start UAC detection and handling in a separate thread
     *
     * @param executor The executor service to use for the thread
     * @param processId Unique ID for the process
     * @param timeout Timeout in seconds
     * @param startSignal CountDownLatch to synchronize start of UAC handling
     * @return Future for the UAC handling task
     */
    private static Future<?> startUacHandler(ExecutorService executor, long processId, int timeout, CountDownLatch startSignal) {
        if (!uacHandlerAvailable) {
            LOGGER.fine("Enhanced UAC handling not available");
            return null;
        }

        LOGGER.info("Starting Confirmation dialog detection thread with timeout: " + timeout + " seconds");
        return executor.submit(() -> {
            try {
                // Wait for the signal that batch execution has started
                startSignal.await();

                // Call EnhancedUacHandler's detectAndHandleUac method with timeout parameter
                LOGGER.info("Confirmation dialog detection started for process ID: " + processId);
                EnhancedUacHandler.detectAndHandleUac(processId, timeout);
                LOGGER.info("Confirmation dialog detection thread completed");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in Confirmation dialog detection thread: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Execute a BAT file and extract a value from the output
     *
     * @param file BAT file to execute
     * @param operation Operation containing parameters
     * @param remarks StringBuilder to add remarks
     * @return true if successful, false otherwise
     */
    public static boolean executeBatAndGetValue(File file, Operation operation, StringBuilder remarks) {
        String args = operation.getParameter("args");
        String workingDirPath = operation.getParameter("working_dir");
        String timeoutStr = operation.getParameter("timeout");
        String outputSearchText = operation.getParameter("output_search_text");
        String outputValuePattern = operation.getParameter("output_value_pattern");
        String expectedExitCodeStr = operation.getParameter("exit_code");

        // Handle Clone_Primary_Server.bat special case
        boolean isClonePrimary = file.getName().equalsIgnoreCase("Clone_Primary_Server.bat");
        String originalContent = null;
        if (isClonePrimary) {
            try {
                originalContent = prepareClonePrimaryBat(file, operation, remarks);
                if (originalContent == null) {
                    return false; // Error already logged in prepareClonePrimaryBat
                }
            } catch (Exception e) {
                remarks.append("FAILED: Could not modify Clone_Primary_Server.bat. Reason: ").append(e.getMessage()).append("\n");
                operation.setParameter("execution_status", "FAILED");
                return false;
            }
        }

        // Setup working directory
        File workingDir = setupWorkingDirectory(workingDirPath, file, remarks);

        // Setup timeout
        int timeout = setupTimeout(timeoutStr, remarks);

        // Setup command
        List<String> command = setupCommand(file, args);

        remarks.append("BAT Execution\n");
        remarks.append("Target file: ").append(file.getAbsolutePath()).append("\n");
        remarks.append("Working directory: ").append(workingDir.getAbsolutePath()).append("\n");
        remarks.append("Command: ").append(command).append("\n");

        // Create thread synchronization objects
        ExecutorService executor = Executors.newFixedThreadPool(2);
        long processId = System.currentTimeMillis(); // Unique ID for this process execution

        try {
            // Execute batch file with UAC handling
            ExecutionContext context = executeBatchWithUac(executor, processId, command, workingDir, timeout, outputSearchText, outputValuePattern, remarks);

            // Process execution results
            boolean success = processExecutionResults(context, operation, outputSearchText, outputValuePattern, expectedExitCodeStr, remarks);

            if (operation.hasNote() && context.result != null) {
                saveNote(operation, context.result.extractedValue);
            }
            terminateUACAutoItProcess(processId);

            return success;
        } catch (Exception e) {
            remarks.append("Verification Result: FAILED\n");
            remarks.append("Reason for Failure: Exception occurred - ").append(e.getMessage()).append("\n");
            operation.setParameter("execution_status", "FAILED");
            terminateUACAutoItProcess(processId);
            return false;
        } finally {
            // Cleanup resources
            terminateUACAutoItProcess(processId);
            handleCleanup(executor, processId, file, isClonePrimary, originalContent, remarks);
        }
    }

    /**
     * Prepare the Clone_Primary_Server.bat file by modifying its content
     *
     * @param file The bat file to prepare
     * @param operation The operation parameters
     * @param remarks StringBuilder to add remarks
     * @return The original content of the file for later restoration
     * @throws IOException If file operations fail
     */
    private static String prepareClonePrimaryBat(File file, Operation operation, StringBuilder remarks) throws IOException {
        String originalContent = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        List<String> lines = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> filtered = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().equalsIgnoreCase("CMD")) {
                filtered.add(line);
            }
        }
        java.nio.file.Files.write(file.toPath(), filtered, StandardCharsets.UTF_8);
        return originalContent;
    }

    /**
     * Setup the working directory for batch execution
     *
     * @param workingDirPath Path to the working directory
     * @param file The bat file being executed
     * @param remarks StringBuilder to add remarks
     * @return The working directory
     */
    private static File setupWorkingDirectory(String workingDirPath, File file, StringBuilder remarks) {
        File workingDir = (workingDirPath != null && !workingDirPath.isEmpty()) ?
                new File(workingDirPath) : file.getParentFile();
        if (workingDir == null || !workingDir.exists() || !workingDir.isDirectory()) {
            workingDir = new File(System.getProperty("user.dir"));
            remarks.append("Working directory not valid, using current directory instead: ").append(workingDir.getAbsolutePath()).append("\n");
        }
        return workingDir;
    }

    /**
     * Setup the timeout value
     *
     * @param timeoutStr Timeout as string
     * @param remarks StringBuilder to add remarks
     * @return Timeout in seconds
     */
    private static int setupTimeout(String timeoutStr, StringBuilder remarks) {
        int timeout = PROCESS_TIMEOUT_SECONDS;
        if (timeoutStr != null && !timeoutStr.isEmpty()) {
            try {
                timeout = Integer.parseInt(timeoutStr);
                if (timeout <= 0) timeout = PROCESS_TIMEOUT_SECONDS;
            } catch (NumberFormatException e) {
                remarks.append("Invalid timeout value: ").append(timeoutStr).append(", using default: ").append(PROCESS_TIMEOUT_SECONDS).append("\n");
            }
        }
        return timeout;
    }

    /**
     * Setup the command to execute
     *
     * @param file The bat file to execute
     * @param args Command line arguments
     * @return List containing the command and its arguments
     */
    private static List<String> setupCommand(File file, String args) {
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add(file.getAbsolutePath());
        if (args != null && !args.isEmpty()) {
            String[] argArray = parseCommandLineArgs(args);
            Collections.addAll(command, argArray);
        }
        return command;
    }

    /**
     * Class to hold execution context and results
     */
    static class ExecutionContext {
        ProcessBuilder processBuilder;
        AtomicReference<BatExecutionResult> resultRef = new AtomicReference<>(null);
        AtomicBoolean processComplete = new AtomicBoolean(false);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>(null);
        BatExecutionResult result;
        int exitValue = 1; // Default to error
    }

    /**
     * Execute batch file with UAC handling in parallel threads
     *
     * @param executor The executor service
     * @param processId Unique ID for this process execution
     * @param command The command to execute
     * @param workingDir Working directory
     * @param timeout Timeout in seconds
     * @param outputSearchText Text to search for in output
     * @param outputValuePattern Pattern to extract values from output
     * @param remarks StringBuilder to add remarks
     * @return ExecutionContext containing execution results
     * @throws Exception If execution fails
     */
    private static ExecutionContext executeBatchWithUac(ExecutorService executor, long processId,
                                                      List<String> command, File workingDir, int timeout,
                                                      String outputSearchText, String outputValuePattern,
                                                      StringBuilder remarks) throws Exception {
        ExecutionContext context = new ExecutionContext();
        context.processBuilder = new ProcessBuilder(command);
        context.processBuilder.directory(workingDir);
        context.processBuilder.redirectErrorStream(true);

        // Create synchronization object
        CountDownLatch processStartSignal = new CountDownLatch(1);

        // Start UAC handling thread first, but it will wait for the process to start
        Future<?> uacHandlerFuture = startUacHandler(executor, processId, timeout, processStartSignal);
        LOGGER.info("UAC handling prepared for executeBatAndGetValue with process ID: " + processId);

        // Start batch file execution thread
        Future<Boolean> batchFuture = executor.submit(() -> {
            try {
                Process process = context.processBuilder.start();

                // Signal that process has started so UAC handling can begin
                processStartSignal.countDown();
                LOGGER.info("Process started, signaled UAC handler");

                // Process the output - keeping this code unchanged as requested
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String output = bufferedReader.lines().collect(Collectors.joining("\n"));
                LOGGER.info("Process output: " + output);
                if (output.isEmpty()) {
                    bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    output = bufferedReader.lines().collect(Collectors.joining("\n"));
                    LOGGER.info("Process error output: " + output);
                }

                // Wait for process completion with timeout
                boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

                if (!completed) {
                    process.destroyForcibly();
                    throw new TimeoutException("Process timed out after " + timeout + " seconds");
                }

                // Process completed successfully, check output
                context.processComplete.set(true);
                BatExecutionResult result = checkProcessOutput(output, outputSearchText, outputValuePattern, LOGGER, remarks);
                context.resultRef.set(result);
                terminateUACAutoItProcess(processId);

                return true;
            } catch (Exception e) {
                terminateUACAutoItProcess(processId);
                context.exceptionRef.set(e);
                return false;
            } finally {
                terminateUACAutoItProcess(processId);
            }
        });

        // Wait for both threads to complete or timeout
        try {
            if (uacHandlerFuture != null) {
                // No need to get result from UAC handler, just wait for it
                uacHandlerFuture.get(timeout, TimeUnit.SECONDS);
            }

            // Wait for batch execution to complete
            boolean batchSuccess = batchFuture.get(timeout, TimeUnit.SECONDS);

            if (!batchSuccess) {
                if (context.exceptionRef.get() != null) {
                    throw context.exceptionRef.get();
                }
                remarks.append("Verification Result: FAILED\n");
                remarks.append("Batch execution failed without exception\n");
                throw new Exception("Batch execution failed");
            }

        } catch (TimeoutException e) {
            remarks.append("Verification Result: FAILED\n");
            remarks.append("Reason for Failure: Process timed out after ").append(timeout).append(" seconds\n");

            // Cancel all threads
            if (uacHandlerFuture != null) {
                uacHandlerFuture.cancel(true);
            }
            batchFuture.cancel(true);

            terminateUACAutoItProcess(processId);
            throw e;
        }

        // Get the execution result
        context.result = context.resultRef.get();
        if (context.result == null) {
            remarks.append("Verification Result: FAILED\n");
            remarks.append("Reason for Failure: No result from batch execution\n");
            throw new Exception("No result from batch execution");
        }
        terminateUACAutoItProcess(processId);
        return context;
    }

    /**
     * Process execution results and update operation parameters
     *
     * @param context Execution context with results
     * @param operation Operation to update
     * @param outputSearchText Text to search for in output
     * @param outputValuePattern Pattern to extract values from output
     * @param expectedExitCodeStr Expected exit code as string
     * @param remarks StringBuilder to add remarks
     * @return true if execution was successful, false otherwise
     */
    private static boolean processExecutionResults(ExecutionContext context, Operation operation,
                                                 String outputSearchText, String outputValuePattern,
                                                 String expectedExitCodeStr, StringBuilder remarks) {
        BatExecutionResult result = context.result;

        // Set operation parameters from execution results
        operation.setParameter("output", result.output);
        operation.setParameter("output_matched_line", result.matchedLine);
        operation.setParameter("Found_output_value", result.extractedValue);

        int exitValue = context.exitValue; // Default to error if not set

        remarks.append("Comparison Type: BAT Output Search & Value Extraction\n");
        remarks.append("Expected Output Search Text: ").append(outputSearchText != null ? outputSearchText : "none").append("\n");
        remarks.append("Actual Output Search Text Found: ").append(result.found ? "YES" : "NO").append("\n");
        remarks.append("Expected Exit Code: ").append(expectedExitCodeStr != null ? expectedExitCodeStr : "none").append("\n");
        remarks.append("Actual Exit Code: ").append(exitValue).append("\n");
        remarks.append("Extracted Value: ").append(result.extractedValue != null ? result.extractedValue : "none").append("\n");

        return evaluateExecutionResults(result, operation, exitValue, outputSearchText, expectedExitCodeStr, remarks);
    }

    /**
     * Evaluate execution results and determine success or failure
     *
     * @param result Execution result
     * @param operation Operation to update
     * @param exitValue Process exit value
     * @param outputSearchText Text to search for in output
     * @param expectedExitCodeStr Expected exit code as string
     * @param remarks StringBuilder to add remarks
     * @return true if execution was successful, false otherwise
     */
    private static boolean evaluateExecutionResults(BatExecutionResult result, Operation operation,
                                                  int exitValue, String outputSearchText,
                                                  String expectedExitCodeStr, StringBuilder remarks) {
        boolean passed = false;

        if (expectedExitCodeStr != null && !expectedExitCodeStr.isEmpty()) {
            try {
                int expectedExitCode = Integer.parseInt(expectedExitCodeStr);
                if (result.found && exitValue == expectedExitCode) {
                    remarks.append("Verification Result: PASSED\n");
                    remarks.append("Output search text found and exit code matched.\n");
                    operation.setParameter("execution_status", "PASSED");
                    passed = true;
                } else {
                    remarks.append("Verification Result: FAILED\n");
                    if (!result.found) {
                        remarks.append("Reason for Failure: Output search text '").append(outputSearchText).append("' not found.\n");
                    }
                    if (exitValue != expectedExitCode) {
                        remarks.append("Reason for Failure: Exit code mismatch. Expected ").append(expectedExitCode)
                                .append(", got ").append(exitValue).append("\n");
                    }
                    operation.setParameter("execution_status", "FAILED");
                }
            } catch (NumberFormatException e) {
                remarks.append("Verification Result: FAILED\n");
                remarks.append("Reason for Failure: Invalid expected exit code: ").append(expectedExitCodeStr).append("\n");
                operation.setParameter("execution_status", "FAILED");
            }
        } else if (outputSearchText != null && !outputSearchText.isEmpty()) {
            if (result.found) {
                remarks.append("Verification Result: PASSED\n");
                remarks.append("Output search text found in output.\n");
                operation.setParameter("execution_status", "PASSED");
                passed = true;
            } else {
                remarks.append("Verification Result: FAILED\n");
                remarks.append("Reason for Failure: Output search text '").append(outputSearchText).append("' not found.\n");
                operation.setParameter("execution_status", "FAILED");
            }
        } else if (exitValue == 0) {
            remarks.append("Verification Result: PASSED\n");
            remarks.append("Exit code matched (0).\n");
            operation.setParameter("execution_status", "PASSED");
            passed = true;
        } else {
            remarks.append("Verification Result: FAILED\n");
            remarks.append("Reason for Failure: Exit code mismatch. Expected 0, got ").append(exitValue).append("\n");
            operation.setParameter("execution_status", "FAILED");
        }

        return passed;
    }

    /**
     * Handle cleanup of resources
     *
     * @param executor The executor service to shutdown
     * @param processId Process ID for UAC handling
     * @param file The bat file that was executed
     * @param isClonePrimary Whether this is the Clone_Primary_Server.bat file
     * @param originalContent Original content of the file to restore
     * @param remarks StringBuilder to add remarks
     */
    private static void handleCleanup(ExecutorService executor, long processId, File file,
                                     boolean isClonePrimary, String originalContent, StringBuilder remarks) {
        // Terminate UAC handling process
        terminateUACAutoItProcess(processId);

        // Shutdown executor service
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warning("Error shutting down executor: " + e.getMessage());
        }

        // Restore original content for Clone_Primary_Server.bat if needed
        if (isClonePrimary && originalContent != null) {
            try {
                java.nio.file.Files.write(file.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                remarks.append("Failed to restore original content of Clone_Primary_Server.bat: ").append(e.getMessage()).append("\n");
            }
        }
    }

    /**
     * Execute a BAT file with UAC handling using multi-threading
     */
    private static boolean executeBatFile(File file, Operation operation, StringBuilder remarks) {
        // Extract operation parameters
        String args = operation.getParameter("args");
        String workingDirPath = operation.getParameter("working_dir");
        String timeoutStr = operation.getParameter("timeout"); // in seconds
        String expectedExitCodeStr = operation.getParameter("exit_code"); // Expected exit code

        // Setup working directory
        File workingDir = setupWorkingDirectory(workingDirPath, file, remarks);

        // Setup timeout
        int timeout = setupTimeout(timeoutStr, remarks);

        // Setup command
        List<String> command = setupCommand(file, args);

        LOGGER.info("Executing command: " + command);
        remarks.append("Executing BAT file: ").append(file.getAbsolutePath()).append("\n");
        remarks.append("Working directory: ").append(workingDir.getAbsolutePath()).append("\n");
        remarks.append("Command: ").append(command).append("\n");

        // Create thread synchronization objects
        ExecutorService executor = Executors.newFixedThreadPool(2);
        long processId = System.currentTimeMillis(); // Unique ID for this process execution

        try {
            // Execute batch file with UAC handling
            SimpleBatExecutionContext context = executeSimpleBatWithUac(executor, processId, command, workingDir, timeout);

            // Process and evaluate results
            return processSimpleBatResults(context, operation, expectedExitCodeStr, remarks);
        } catch (Exception e) {
            remarks.append("Execution failed with exception: ").append(e.getMessage()).append("\n");
            operation.setParameter("execution_status", "FAILED");
            return false;
        } finally {
            // Cleanup resources
            cleanupResources(executor, processId);
        }
    }

    /**
     * Class to hold simple batch execution context and results
     */
    static class SimpleBatExecutionContext {
        List<String> outputLines = new ArrayList<>();
        int exitValue = 1; // Default to error
        boolean completed = false;
        Exception exception = null;
    }

    /**
     * Execute a simple batch file with UAC handling in parallel threads
     *
     * @param executor The executor service
     * @param processId Unique ID for this process execution
     * @param command The command to execute
     * @param workingDir Working directory
     * @param timeout Timeout in seconds
     * @return SimpleBatExecutionContext containing execution results
     * @throws Exception If execution fails
     */
    private static SimpleBatExecutionContext executeSimpleBatWithUac(
            ExecutorService executor, long processId, List<String> command, File workingDir, int timeout) throws Exception {

        SimpleBatExecutionContext context = new SimpleBatExecutionContext();
        CountDownLatch processStartSignal = new CountDownLatch(1);

        // Atomic references for thread-safe communication
        AtomicReference<List<String>> outputLinesRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Exception> exceptionRef = new AtomicReference<>(null);
        AtomicInteger exitValueRef = new AtomicInteger(-1);
        AtomicBoolean completedRef = new AtomicBoolean(false);

        // Start UAC handling thread first, but it will wait for the process to start
        Future<?> uacHandlerFuture = startUacHandler(executor, processId, timeout, processStartSignal);
        LOGGER.info("UAC handling prepared for batch execution with process ID: " + processId);

        // Start batch file execution thread
        Future<Boolean> batchFuture = executor.submit(() -> {
            try {
                // Start process
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(workingDir);
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();

                // Signal that the process has started so UAC handling can begin
                processStartSignal.countDown();
                LOGGER.info("Process started, signaled UAC handler");

                // Capture output
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                        LOGGER.info("Execute Action BAT Output: " + line);
                    }
                }

                // Store output lines
                outputLinesRef.set(lines);

                // Wait for process completion with timeout
                boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
                completedRef.set(completed);

                if (completed) {
                    exitValueRef.set(process.exitValue());
                } else {
                    process.destroyForcibly();
                }

                return completed;
            } catch (Exception e) {
                exceptionRef.set(e);
                return false;
            }
        });

        // Wait for batch execution thread to complete
        try {
            boolean batchSuccess = batchFuture.get(timeout, TimeUnit.SECONDS);

            // Cancel UAC handling if process completed
            if (uacHandlerFuture != null) {
                uacHandlerFuture.cancel(true);
            }

            // Check for batch execution failure
            if (!batchSuccess) {
                if (exceptionRef.get() != null) {
                    throw exceptionRef.get();
                }
                if (!completedRef.get()) {
                    throw new TimeoutException("Process timed out after " + timeout + " seconds");
                }
            }

            // Populate execution context with results
            context.outputLines = outputLinesRef.get();
            context.exitValue = exitValueRef.get();
            context.completed = completedRef.get();

            return context;
        } catch (Exception e) {
            // Cancel threads and cleanup on error
            if (uacHandlerFuture != null) {
                uacHandlerFuture.cancel(true);
            }
            batchFuture.cancel(true);
            terminateUACAutoItProcess(processId);

            context.exception = e;
            throw e;
        }
    }

    /**
     * Process and evaluate batch execution results with exit code handling
     *
     * @param context Execution context with results
     * @param operation Operation to update
     * @param expectedExitCodeStr Expected exit code as string (may be null)
     * @param remarks StringBuilder to add remarks
     * @return true if execution was successful, false otherwise
     */
    private static boolean processSimpleBatResults(SimpleBatExecutionContext context, Operation operation,
                                                 String expectedExitCodeStr, StringBuilder remarks) {
        // If there was an exception during execution
        if (context.exception != null) {
            remarks.append("Process timed out or failed with error: ").append(context.exception.getMessage()).append("\n");
            operation.setParameter("execution_status", "FAILED");
            return false;
        }

        // If process didn't complete within timeout
        if (!context.completed) {
            remarks.append("Process timed out\n");
            operation.setParameter("execution_status", "FAILED");
            return false;
        }

        // Store output in operation parameter
        String output = context.outputLines.toString();
        operation.setParameter("output", output);

        // Store exit code
        int exitValue = context.exitValue;
        operation.setParameter("exit_code", String.valueOf(exitValue));

        // Expected exit code is 0 for success by default
        int expectedExitCode = 0;
        if (expectedExitCodeStr != null && !expectedExitCodeStr.isEmpty()) {
            try {
                expectedExitCode = Integer.parseInt(expectedExitCodeStr);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid expected exit code: " + expectedExitCodeStr + ", using default: 0");
            }
        }

        // Compare actual with expected exit code
        remarks.append("Expected Exit Code: ").append(expectedExitCode).append("\n");
        remarks.append("Actual Exit Code: ").append(exitValue).append("\n");

        if (exitValue == expectedExitCode) {
            operation.setParameter("execution_status", "PASSED");
            remarks.append("Verification Result: PASSED\n");
            remarks.append("Exit code matched expected value.\n");
            return true;
        } else {
            operation.setParameter("execution_status", "FAILED");
            remarks.append("Verification Result: FAILED\n");
            remarks.append("Reason for Failure: Exit code mismatch. Expected ").append(expectedExitCode)
                  .append(", got ").append(exitValue).append("\n");
            return false;
        }
    }

    /**
     * Clean up resources after batch execution
     *
     * @param executor The executor service to shutdown
     * @param processId Process ID for UAC handling
     */
    private static void cleanupResources(ExecutorService executor, long processId) {
        // Terminate UAC handling process
        terminateUACAutoItProcess(processId);

        // Shutdown executor service
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warning("Error shutting down executor: " + e.getMessage());
        }
    }

    /**
     * Execute a BAT file interactively, handling runtime prompts and UAC dialogs using multi-threading
     *
     * @param file BAT file to execute
     * @param operation Operation containing parameters
     * @param remarks StringBuilder to add remarks
     * @return true if successful, false otherwise
     */
    public static boolean executeInteractiveBat(File file, Operation operation, StringBuilder remarks) {
        // Extract operation parameters
        String args = operation.getParameter("args");
        String workingDirPath = operation.getParameter("working_dir");
        String timeoutStr = operation.getParameter("timeout");
        String promptResponses = operation.getParameter("prompt_responses");
        String outputSearchText = operation.getParameter("output_search_text");
        String expectedExitCodeStr = operation.getParameter("exit_code"); // Get expected exit code

        LOGGER.info("Starting interactive bat execution: " + file.getAbsolutePath());

        // Setup working directory
        File workingDir = setupWorkingDirectory(workingDirPath, file, remarks);

        // Setup timeout
        int timeout = setupTimeout(timeoutStr, remarks);

        // Setup prompt responses
        List<Map.Entry<String, String>> promptResponseList = parsePromptResponses(promptResponses);
        LOGGER.info("Prompt responses list: " + promptResponseList);

        // Setup command
        List<String> command = setupCommand(file, args);

        LOGGER.info("Executing command: " + command);
        remarks.append("Interactive BAT Execution\n");
        remarks.append("Target file: ").append(file.getAbsolutePath()).append("\n");
        remarks.append("Working directory: ").append(workingDir.getAbsolutePath()).append("\n");
        remarks.append("Command: ").append(command).append("\n");

        // Create thread synchronization objects
        ExecutorService executor = Executors.newFixedThreadPool(2);
        long processId = System.currentTimeMillis(); // Unique ID for this process execution

        try {
            // Execute interactive batch file with UAC handling
            InteractiveBatExecutionContext context = executeInteractiveBatWithUac(
                    executor, processId, command, workingDir, timeout,
                    outputSearchText, promptResponseList);

            // Process and evaluate results
            return processInteractiveBatResults(
                    context, operation, outputSearchText, expectedExitCodeStr, remarks);
        } catch (Exception e) {
            remarks.append("Verification Result: FAILED\n");
            remarks.append("Reason for Failure: Exception occurred - ").append(e.getMessage()).append("\n");
            operation.setParameter("execution_status", "FAILED");
            return false;
        } finally {
            // Cleanup resources
            cleanupResources(executor, processId);
        }
    }

    /**
     * Parse prompt responses string into a list of key-value pairs
     *
     * @param promptResponses String containing prompt responses in format "prompt1=response1;prompt2=response2"
     * @return List of prompt-response pairs
     */
    private static List<Map.Entry<String, String>> parsePromptResponses(String promptResponses) {
        List<Map.Entry<String, String>> promptResponseList = new ArrayList<>();
        if (promptResponses != null && !promptResponses.isEmpty()) {
            for (String pair : promptResponses.split(";")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    promptResponseList.add(new AbstractMap.SimpleEntry<>(parts[0], parts[1]));
                }
            }
        }
        return promptResponseList;
    }

    /**
     * Class to hold interactive batch execution context and results
     */
    static class InteractiveBatExecutionContext {
        String output = "";
        int exitValue = -1; // Default to error
        int responseCount = 0;
        boolean outputStringFound = false;
        Exception exception = null;
        String foundOutput = "";
    }

    /**
     * Execute an interactive batch file with UAC handling in parallel threads
     *
     * @param executor The executor service
     * @param processId Unique ID for this process execution
     * @param command The command to execute
     * @param workingDir Working directory
     * @param timeout Timeout in seconds
     * @param outputSearchText Text to search for in output
     * @param promptResponseList List of prompt-response pairs
     * @return InteractiveBatExecutionContext containing execution results
     * @throws Exception If execution fails
     */
    private static InteractiveBatExecutionContext executeInteractiveBatWithUac(
            ExecutorService executor, long processId, List<String> command,
            File workingDir, int timeout, String outputSearchText,
            List<Map.Entry<String, String>> promptResponseList) throws Exception {

        InteractiveBatExecutionContext context = new InteractiveBatExecutionContext();
        CountDownLatch processStartSignal = new CountDownLatch(1);

        // Atomic references for thread-safe communication
        AtomicReference<String> outputRef = new AtomicReference<>("");
        AtomicReference<Exception> exceptionRef = new AtomicReference<>(null);
        AtomicInteger exitValueRef = new AtomicInteger(1);
        AtomicInteger responseCountRef = new AtomicInteger(0);
        AtomicBoolean outputStringFoundRef = new AtomicBoolean(false);

        // Start UAC handling thread
        Future<?> uacHandlerFuture = startUacHandler(executor, processId, timeout, processStartSignal);
        LOGGER.info("UAC handling prepared for interactive batch execution with process ID: " + processId);

        // Start batch file execution and prompt handling thread
        Future<Boolean> batchFuture = executor.submit(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(workingDir);
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();

                // Signal that the process has started
                processStartSignal.countDown();
                LOGGER.info("Interactive process started, signaled UAC handler");

                StringBuilder outputBuilder = new StringBuilder();
                StringBuilder promptBuffer = new StringBuilder();
                int promptIndex = 0;
                int responseCount = 0;

                try (
                        InputStream inputStream = process.getInputStream();
                        OutputStream outputStream = process.getOutputStream()
                ) {
                    byte[] buffer = new byte[1024];
                    long endTime = System.currentTimeMillis() + (timeout * 1000L);

                    // Setup process monitoring future
                    ExecutorService processMonitor = Executors.newSingleThreadExecutor();
                    Future<Boolean> processFuture = processMonitor.submit(() -> {
                        try {
                            return process.waitFor(timeout, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Error in process monitor", e);
                            return false;
                        }
                    });

                    // Main processing loop
                    while (!processFuture.isDone() && System.currentTimeMillis() < endTime) {
                        if (inputStream.available() > 0) {
                            int bytesRead = inputStream.read(buffer);
                            if (bytesRead > 0) {
                                String output = new String(buffer, 0, bytesRead);
                                LOGGER.info("Interactive BAT Output: " + output);
                                LOGGER.log(Level.INFO, "Console Output: " + output);
                                outputBuilder.append(output);
                                promptBuffer.append(output);

                                if (outputSearchText != null && output.contains(outputSearchText)) {
                                    outputStringFoundRef.set(true);
                                    context.foundOutput = output;
                                    LOGGER.info("Found expected output text: " + outputSearchText);
                                }

                                // Check for prompts and send responses
                                if (promptIndex < promptResponseList.size()) {
                                    String prompt = promptResponseList.get(promptIndex).getKey();
                                    if (promptBuffer.toString().contains(prompt)) {
                                        String response = promptResponseList.get(promptIndex).getValue() + "\n";
                                        outputStream.write(response.getBytes());
                                        outputStream.flush();
                                        responseCount++;
                                        promptIndex++;
                                        LOGGER.info("Responded to prompt: " + prompt + " with: " + response.trim());
                                        LOGGER.log(Level.INFO, "Sent Response: " + response.trim());

                                        // Clear buffer after response
                                        promptBuffer.setLength(0);
                                    }
                                }
                            }
                        } else {
                            Thread.sleep(100); // Prevent CPU thrashing
                        }
                    }

                    // Drain any remaining output after process completes
                    while (inputStream.available() > 0) {
                        LOGGER.info("Draining remaining output");
                        int bytesRead = inputStream.read(buffer);
                        if (bytesRead > 0) {
                            String output = new String(buffer, 0, bytesRead);
                            LOGGER.info("Drained Output: " + output);
                            LOGGER.log(Level.INFO, "Drained Console Output: " + output);

                            if (outputSearchText != null && output.contains(outputSearchText)) {
                                outputStringFoundRef.set(true);
                                context.foundOutput = output;
                                LOGGER.info("Found expected output text: " + outputSearchText);
                            }
                            outputBuilder.append(output);
                        }
                    }

                    // Shutdown the process monitor
                    terminateUACAutoItProcess(processId);
                    processMonitor.shutdownNow();

                    // Get exit value
                    try {
                        exitValueRef.set(process.exitValue());
                    } catch (IllegalThreadStateException e) {
                        process.destroyForcibly();
                        LOGGER.warning("Process did not exit normally, forced termination");
                    }

                    // Store results in atomic references
                    outputRef.set(outputBuilder.toString());
                    responseCountRef.set(responseCount);

                    return true;
                }
            } catch (Exception e) {
                exceptionRef.set(e);
                LOGGER.log(Level.SEVERE, "Error in interactive batch execution", e);
                return false;
            }
        });

        // Wait for both threads to complete or timeout
        try {
            if (uacHandlerFuture != null) {
                uacHandlerFuture.get(timeout, TimeUnit.SECONDS);
            }

            boolean batchSuccess = batchFuture.get(timeout, TimeUnit.SECONDS);

            if (!batchSuccess) {
                if (exceptionRef.get() != null) {
                    throw exceptionRef.get();
                }
                throw new Exception("Batch execution failed without detailed error");
            }
        } catch (TimeoutException e) {
            LOGGER.warning("Process timed out after " + timeout + " seconds");
            if (uacHandlerFuture != null) {
                uacHandlerFuture.cancel(true);
            }
            batchFuture.cancel(true);
            terminateUACAutoItProcess(processId);
            throw e;
        } catch (Exception e) {
            terminateUACAutoItProcess(processId);
            throw e;
        } finally {
            terminateUACAutoItProcess(processId);
        }

        // Final check: if not found in any line, check overall output
        if (!outputStringFoundRef.get()) {
            LOGGER.info("Output search text not found in individual lines, checking overall output");
            String normOutput = normalizeForSearch(outputRef.get());
            String normSearch = normalizeForSearch(outputSearchText);
            if (normOutput.contains(normSearch)) {
                LOGGER.info("Output search text found in overall output content");
                outputStringFoundRef.set(true);
                LOGGER.info("Found output search text in overall output content: " + outputSearchText);
            }
        }

        // Populate the context with results
        LOGGER.info("Interactive batch execution completed");
        context.output = outputRef.get();
        LOGGER.info("Full Output: " + context.output);
        context.exitValue = exitValueRef.get();
        context.responseCount = responseCountRef.get();
        context.outputStringFound = outputStringFoundRef.get();
        context.exception = exceptionRef.get();

        return context;
    }

    private static String normalizeForSearch(String s) {
        if (s == null) return "";
        // Normalize line endings, collapse whitespace, trim
        return s.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n+", "\n")
                .trim();
    }


    /**
     * Check if a buffer contains a prompt that expects input on the same line
     */
    private static boolean isSameLinePrompt(String buffer, String promptKey) {
        // Direct matches
        if (buffer.endsWith(promptKey) ||
                buffer.endsWith(promptKey + " ") ||
                buffer.endsWith(promptKey + ": ")) {
            return true;
        }

        // Check for SET /P pattern (most common same-line prompt in batch files)
        if (buffer.toLowerCase().contains("set /p") || buffer.toLowerCase().contains("set/p")) {
            // Look for common prompt endings
            if (buffer.endsWith(": ") || buffer.endsWith("? ") ||
                    buffer.endsWith("> ") || buffer.endsWith("= ")) {
                return true;
            }
        }

        // Check if buffer contains the prompt and appears to be waiting for input
        if (buffer.contains(promptKey)) {
            // Common patterns that indicate input is expected
            if (buffer.contains("Enter") || buffer.contains("enter") ||
                    buffer.contains("input") || buffer.contains("option")) {
                return true;
            }

            // Check for typical prompt endings after the key
            int keyIndex = buffer.indexOf(promptKey);
            String afterPrompt = buffer.substring(keyIndex + promptKey.length());
            if (afterPrompt.trim().isEmpty() ||
                    afterPrompt.trim().endsWith(":") ||
                    afterPrompt.trim().endsWith("?") ||
                    afterPrompt.trim().endsWith(">")) {
                return true;
            }
        }

        return false;
    }


    /**
     * Check for common prompt patterns in a buffer
     */
    private static boolean isCommonPromptPattern(String buffer) {
        String trimmed = buffer.trim();

        // Common prompt endings
        if (trimmed.endsWith(": ") || trimmed.endsWith("? ") ||
                trimmed.endsWith("> ") || trimmed.endsWith("= ")) {
            return true;
        }

        // Common prompt phrases
        String lowerBuffer = buffer.toLowerCase();
        if ((lowerBuffer.contains("enter") || lowerBuffer.contains("type") ||
                lowerBuffer.contains("input") || lowerBuffer.contains("select")) &&
                (lowerBuffer.contains("option") || lowerBuffer.contains("choice") ||
                        lowerBuffer.contains("value") || lowerBuffer.contains("number"))) {
            return true;
        }

        return false;
    }

    /**
     * Check for partial matches with any prompt in the list
     */
    private static boolean hasPartialPromptMatch(String buffer,
                                                 List<Map.Entry<String, String>> promptList,
                                                 int currentIndex) {
        if (currentIndex >= promptList.size()) return false;

        String promptKey = promptList.get(currentIndex).getKey();
        String bufferLower = buffer.toLowerCase();
        String promptLower = promptKey.toLowerCase();

        // Extract significant words from the prompt
        String[] words = promptLower.split("\\s+");
        for (String word : words) {
            // Skip short/common words
            if (word.length() > 3 && !word.equals("the") && !word.equals("and") &&
                    !word.equals("for") && !word.equals("your")) {
                if (bufferLower.contains(word)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check for full-line prompts and respond if found
     */
    private static void checkAndRespondToPrompt(String line,
                                                List<Map.Entry<String, String>> promptList,
                                                int currentIndex,
                                                OutputStream outputStream,
                                                AtomicInteger responseCount) throws IOException {
        if (currentIndex >= promptList.size()) return;

        for (int i = currentIndex; i < promptList.size(); i++) {
            String promptKey = promptList.get(i).getKey();
            if (line.contains(promptKey)) {
                String response = promptList.get(i).getValue() + "\n";
                outputStream.write(response.getBytes());
                outputStream.flush();
                responseCount.incrementAndGet();
                LOGGER.info("Responded to prompt: " + promptKey + " with: " + response.trim());
                break;
            }
        }
    }



    /**
     * Process and evaluate interactive batch execution results
     *
     * @param context Execution context with results
     * @param operation Operation to update
     * @param outputSearchText Text that was searched for in output
     * @param expectedExitCodeStr Expected exit code as string (may be null)
     * @param remarks StringBuilder to add remarks
     * @return true if execution was successful, false otherwise
     */
    private static boolean processInteractiveBatResults(
            InteractiveBatExecutionContext context, Operation operation,
            String outputSearchText, String expectedExitCodeStr, StringBuilder remarks) {

        // Store results in operation parameters
        operation.setParameter("output", context.output);
        operation.setParameter("responses_sent", String.valueOf(context.responseCount));
        operation.setParameter("exit_code", String.valueOf(context.exitValue));

        // Expected exit code is 0 for success by default
        int expectedExitCode = 0;
        if (expectedExitCodeStr != null && !expectedExitCodeStr.isEmpty()) {
            try {
                expectedExitCode = Integer.parseInt(expectedExitCodeStr);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid expected exit code: " + expectedExitCodeStr + ", using default: 0");
            }
        }

        // Add information to remarks
        remarks.append("Comparison Type: Interactive BAT Execution\n");
        remarks.append("Expected Output Search Text: ").append(outputSearchText != null ? outputSearchText : "none").append("\n");
        remarks.append("Actual Output Search Text Found in line: ").append(context.foundOutput ).append("\n");
        remarks.append("Expected Exit Code: ").append(expectedExitCode).append("\n");
        remarks.append("Actual Exit Code: ").append(context.exitValue).append("\n");
        remarks.append("Responses Sent: ").append(context.responseCount).append("\n");

        boolean exitCodeMatched = (context.exitValue == expectedExitCode);
        boolean outputMatched = (outputSearchText == null || context.outputStringFound);

        if (exitCodeMatched && outputMatched) {
            remarks.append("Verification Result: PASSED\n");
            if (outputSearchText != null) {
                remarks.append("Output search text found and exit code matched.\n");
            } else {
                remarks.append("Exit code matched.\n");
            }
            operation.setParameter("execution_status", "PASSED");
            return true;
        } else {
            remarks.append("Verification Result: FAILED\n");
            if (!exitCodeMatched) {
                remarks.append("Reason for Failure: Exit code mismatch. Expected ").append(expectedExitCode)
                      .append(", got ").append(context.exitValue).append("\n");
            }
            if (!context.outputStringFound && outputSearchText != null) {
                remarks.append("Reason for Failure: Output search text '").append(outputSearchText).append("' not found.\n");
            }
            operation.setParameter("execution_status", "FAILED");
            return false;
        }
    }

    static class NewThreadImpl implements Callable<BatExecutionResult> {
        private final Process process;
        private final String outputSearchText;
        private final String outputValuePattern;
        private final Logger logger;
        private final StringBuilder remarks;

        NewThreadImpl(Process process, String outputSearchText, String outputValuePattern, Logger logger, StringBuilder remarks) {
            this.process = process;
            this.outputSearchText = outputSearchText;
            this.outputValuePattern = outputValuePattern;
            this.logger = logger;
            this.remarks = remarks;
        }

        @Override
        public BatExecutionResult call() throws Exception {
            LOGGER.info("Kindly wait for the process to complete......");
            LOGGER.info("If you are not given the timeout, the process will wait for up to 30 minutes. If it completes earlier, the output will be returned immediately.");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = bufferedReader.lines().collect(Collectors.joining("\n"));
            LOGGER.info("Process output: " + output);
            if (output.isEmpty()) {
                bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                output = bufferedReader.lines().collect(Collectors.joining("\n"));
                LOGGER.info("Process error output: " + output);
            }
            return checkProcessOutput(output, outputSearchText, outputValuePattern, logger, remarks);
        }
    }

    public static class BatExecutionResult {
        public final String output;
        public final boolean found;
        public final String matchedLine;
        public final String extractedValue;

        public BatExecutionResult(String output, boolean found, String matchedLine, String extractedValue) {
            this.output = output;
            this.found = found;
            this.matchedLine = matchedLine;
            this.extractedValue = extractedValue;
        }
    }

    private static BatExecutionResult checkProcessOutput(
            String output, String outputSearchText, String outputValuePattern, Logger logger, StringBuilder remarks) {
        boolean found = false;
        String matchedLine = null;
        String extractedValue = null;

        if (outputSearchText != null && !outputSearchText.isEmpty()) {
            for (String line : output.split("\n")) {
                if (line.contains(outputSearchText)) {
                    found = true;
                    matchedLine = line;
                    LOGGER.info("Found output search text: " + outputSearchText + " in line: " + line);
                    if (outputValuePattern != null && !outputValuePattern.isEmpty()) {
                        try {
                            Pattern pattern = Pattern.compile(outputValuePattern);
                            Matcher matcher = pattern.matcher(line);
                            if (matcher.find()) {
                                extractedValue = matcher.group();
                                LOGGER.info("Extracted value using pattern: " + outputValuePattern + " is: " + extractedValue);
                            } else {
                                remarks.append("No match found for output value pattern: ").append(outputValuePattern).append("\n");
                                LOGGER.warning("No match found for output value pattern: " + outputValuePattern);
                            }
                        } catch (PatternSyntaxException e) {
                            LOGGER.warning("Invalid output value pattern: " + e.getMessage());
                            remarks.append("Invalid output value pattern: ").append(e.getMessage()).append("\n");
                        }
                    }
                    if (extractedValue == null) {
                        extractedValue = outputSearchText;
                    }
                    break;
                }
            }
            // Final check: normalize and search in overall output
            if (!found) {
                String normOutput = normalizeForSearch(output);
                String normSearch = normalizeForSearch(outputSearchText);
                if (normOutput.contains(normSearch)) {
                    found = true;
                    matchedLine = outputSearchText;
                    extractedValue = outputSearchText;
                    logger.info("Found output search text in normalized overall output content: " + outputSearchText);
                }
            }
        }
        return new BatExecutionResult(output, found, matchedLine, extractedValue);
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
}
