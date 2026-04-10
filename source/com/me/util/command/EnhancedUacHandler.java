package com.me.util.command;

import com.me.util.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.util.GOATCommonConstants.AUTOIT_EXE_PATH;
import static com.me.util.GOATCommonConstants.UAC_SCRIPT_PATH;

/**
 * Enhanced UAC handler that uses AutoIt to detect and handle UAC popups
 * during command execution.
 */
public class EnhancedUacHandler {
    private static final Logger LOGGER = LogManager.getLogger(EnhancedUacHandler.class, LogManager.LOG_TYPE.FW);
    private static final Map<Long, Boolean> uacDetectionResults = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean autoItAvailable = false;
    private static boolean initialized = false;

    // Map to store AutoIt processes for termination
    private static final Map<Long, Process> autoItProcesses = new java.util.concurrent.ConcurrentHashMap<>();

    // Initialize with the AutoIt script
    static {
        try {
            initialize();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize EnhancedUacHandler", e);
        }
    }

    /**
     * Initialize the UAC handler by verifying AutoIt is available and creating the UAC handling script
     */
    private static synchronized void initialize() {
        if (initialized) {
            return;
        }

        LOGGER.info("Initializing Enhanced UAC handler");
        autoItAvailable = checkAutoItAvailability();

        if (autoItAvailable) {
            createUacScript();
        }

        initialized = true;
    }

    /**
     * Check if AutoIt is available on the system
     *
     * @return true if AutoIt is available, false otherwise
     */
    public static boolean isAutoItAvailable() {
        if (!initialized) {
            initialize();
        }
        return autoItAvailable;
    }

    /**
     * Execute a command with UAC handling
     *
     * @param command Command to execute
     * @param params Parameters for command substitution (can be null)
     * @param timeoutSeconds Timeout in seconds
     * @return UacAwareCommandResult containing command result and UAC detection info
     */
    public static UacAwareCommandResult execute(String command, Map<String, String> params, int timeoutSeconds) {
        if (params != null && !params.isEmpty()) {
            // Placeholder for parameter substitution method call
            // Would typically use CommandExecutor.substituteParams() but it's private
            // For now, we'll just log this case
            LOGGER.info("Command parameters provided but not used directly in EnhancedUacHandler");
        }

        return executeWithUacDetection(command, timeoutSeconds);
    }

    /**
     * Execute a batch file with UAC handling
     *
     * @param batchFilePath Path to the batch file
     * @param timeoutSeconds Timeout in seconds
     * @return UacAwareCommandResult containing command result and UAC detection info
     */
    public static UacAwareCommandResult executeBatchFile(String batchFilePath, int timeoutSeconds) {
        return executeWithUacDetection(batchFilePath, timeoutSeconds);
    }

    /**
     * Execute a command with active UAC detection
     *
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @return UacAwareCommandResult containing command result and UAC detection info
     */
    private static UacAwareCommandResult executeWithUacDetection(String command, int timeoutSeconds) {
        if (!autoItAvailable) {
            LOGGER.warning("AutoIt not available. Falling back to standard execution without UAC detection");
            CommandResult result = CommandExecutor.execute(command, timeoutSeconds);
            return new UacAwareCommandResult(result, false);
        }

        LOGGER.info("Executing command with active UAC detection: " + command);

        // Generate a unique ID for this execution
        long executionId = System.currentTimeMillis();
        uacDetectionResults.put(executionId, false);

        // Create an executor for parallel tasks
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Start UAC detection in a separate thread
            Future<?> uacDetectionTask = executor.submit(() -> detectAndHandleUac(executionId, timeoutSeconds));

            // Execute the actual command
            LOGGER.fine("Starting command execution");
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<String> outputLines = new ArrayList<>();
            boolean completed = false;

            // Read process output in a separate thread
            Future<List<String>> outputTask = executor.submit(() -> {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                        LOGGER.finest("Process output: " + line);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error reading process output: " + e.getMessage());
                }
                return lines;
            });

            // Wait for the process to complete
            try {
                completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.warning("Process wait was interrupted: " + e.getMessage());
            }

            // Explicitly terminate the AutoIt process
            terminateUACAutoItProcess(executionId);

            executor.shutdownNow();
            LOGGER.info("UAC detection task shutdown: " + executor.isShutdown());
            boolean isCancelled = uacDetectionTask.cancel(true);
            LOGGER.info("UAC detection task cancelled: " + isCancelled);

            // Get output
            try {
                outputLines = outputTask.get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warning("Error getting process output: " + e.getMessage());
            }

            // Get exit code or force termination if timed out
            int exitCode;
            if (completed) {
                exitCode = process.exitValue();
            } else {
                LOGGER.warning("Command execution timed out");
                process.destroyForcibly();
                exitCode = -1;
            }

            // Check if UAC was detected
            boolean uacDetected = uacDetectionResults.getOrDefault(executionId, false);
            if (uacDetected) {
                LOGGER.info("UAC prompt was detected and handled during execution");
            }

            // Remove from the map to free memory
            uacDetectionResults.remove(executionId);

            // Create and return result
            String output = String.join("\n", outputLines);
            CommandResult cmdResult;
            if (completed) {
                cmdResult = new CommandResult(command, output, exitCode == 0, exitCode);
            } else {
                cmdResult = CommandResult.timeout(command);
            }

            return new UacAwareCommandResult(cmdResult, uacDetected);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in UAC-aware execution: " + e.getMessage(), e);
            // Make sure to terminate the AutoIt process even if an exception occurs
            terminateUACAutoItProcess(executionId);
            return new UacAwareCommandResult(CommandResult.error(command, e), false);
        } finally {
            // Clean up threads
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.warning("Executor termination interrupted: " + e.getMessage());
            }
        }
    }

    /**
     * Detect and handle UAC dialog using AutoIt
     *
     * @param executionId Unique ID for this execution
     * @param timeoutSeconds Timeout in seconds
     */
    public static void detectAndHandleUac(long executionId, int timeoutSeconds) {
        try {
            LOGGER.fine("Starting Confirmation dialog detection for execution ID: " + executionId + " with timeout: " + timeoutSeconds + "s");

            // Run the AutoIt script to detect and handle Confirmation dialog
            ProcessBuilder pb = new ProcessBuilder(
                    AUTOIT_EXE_PATH,
                    UAC_SCRIPT_PATH,
                    String.valueOf(executionId),
                    String.valueOf(timeoutSeconds)
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Store the process in the map for later termination
            autoItProcesses.put(executionId, process);

            // Read output to detect if Confirmation dialog was found
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.finest("Confirmation detection output: " + line);
                    if (line.contains("CONFIRMATION_DETECTED")) {
                        uacDetectionResults.put(executionId, true);
                        LOGGER.info("Confirmation dialog detected and handled for execution ID: " + executionId);
                    }
                }
            }

            // Wait for the script to complete or be terminated
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            LOGGER.fine("Confirmation detection completed for execution ID: " + executionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error in Confirmation dialog detection: " + e.getMessage(), e);
        } finally {
            // Remove the process from the map after it completes
            terminateUACAutoItProcess(executionId);
        }
    }

    /**
     * Terminate AutoIt process for the given execution ID
     *
     * @param executionId Unique ID for the execution
     */
    public static void terminateUACAutoItProcess(long executionId) {
        Process process = autoItProcesses.remove(executionId);
        if (process != null) {
            try {
                // Check if process is still alive before destroying
                if (process.isAlive()) {
                    LOGGER.info("Terminating AutoIt process for execution ID: " + executionId);
                    process.destroyForcibly();

                    // For Windows, also try taskkill to ensure it's gone
                    try {
                        // Get the PID if possible
                        long pid = process.pid();
                        String killCommand = "taskkill /F /PID " + pid;
                        Runtime.getRuntime().exec(killCommand);
                        LOGGER.fine("Executed taskkill for AutoIt process: " + pid);
                    } catch (Exception e) {
                        LOGGER.fine("Could not get PID for AutoIt process, relying on destroyForcibly()");
                    }

                    // Wait briefly to ensure termination
                    boolean terminated = process.waitFor(2, TimeUnit.SECONDS);
                    LOGGER.info("AutoIt process terminated successfully: " + terminated);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error terminating AutoIt process: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Detect and handle UAC dialog using AutoIt (legacy method signature)
     *
     * @param executionId Unique ID for this execution
     */
    public static void detectAndHandleUac(long executionId) {
        // Default to 30 minutes timeout
        detectAndHandleUac(executionId, 1800);
    }

    /**
     * Check if AutoIt is available on the system
     *
     * @return true if AutoIt is available, false otherwise
     */
    private static boolean checkAutoItAvailability() {
        File autoItFile = new File(AUTOIT_EXE_PATH);
        boolean available = autoItFile.exists() && autoItFile.canExecute();
        LOGGER.info("AutoIt availability check: " + (available ? "AVAILABLE" : "NOT AVAILABLE") +
                   " at path: " + AUTOIT_EXE_PATH);
        return available;
    }

    /**
     * Create the AutoIt script for confirmation popup detection and handling
     */
    private static void createUacScript() {
        try {
            String script =
                "; AutoIt script to detect and handle Confirmation dialogs\n" +
                "#include <AutoItConstants.au3>\n\n" +
                "; Get execution ID and timeout from command line parameters\n" +
                "$executionId = $CmdLine[1]\n" +
                "$timeout = $CmdLine[2] ; timeout in seconds\n" +
                "ConsoleWrite(\"Starting Confirmation dialog detection for ID: \" & $executionId & \" with timeout: \" & $timeout & \" seconds\" & @CRLF)\n\n" +
                "; Function to detect and handle Confirmation dialog\n" +
                "Func HandleConfirmationDialog()\n" +
                "    ; Window detection parameters\n" +
                "    Local $confirmTitle = \"Confirmation\"\n" +
                "    Local $endTime = TimerInit() + ($timeout * 1000)\n\n" +
                "    ; Keep trying until timeout\n" +
                "    While TimerDiff(TimerInit()) < ($timeout * 1000)\n" +
                "        ; Check if Confirmation window exists\n" +
                "        Local $hWnd = WinGetHandle($confirmTitle, \"\")\n" +
                "        If @error Then\n" +
                "            ; Confirmation dialog not found, wait briefly and try again\n" +
                "            Sleep(500)\n" +
                "            ContinueLoop\n" +
                "        EndIf\n\n" +
                "        ; Confirmation dialog found\n" +
                "        ConsoleWrite(\"CONFIRMATION_DETECTED\" & @CRLF)\n" +
                "        \n" +
                "        ; Restore if minimized\n" +
                "        WinSetState($confirmTitle, \"\", @SW_RESTORE)\n\n" +
                "        ; Bring to front\n" +
                "        WinActivate($confirmTitle)\n\n" +
                "        ; Wait until it's active\n" +
                "        WinWaitActive($confirmTitle, \"\", 5)\n\n" +
                "        ; Add a small delay to let UI stabilize\n" +
                "        Sleep(500)\n\n" +
                "        ; Optional: Force focus on the Yes/OK button\n" +
                "        ControlFocus($confirmTitle, \"\", \"[CLASS:Button; INSTANCE:1]\")\n\n" +
                "        ; Perform the click\n" +
                "        ControlClick($confirmTitle, \"\", \"[CLASS:Button; INSTANCE:1]\")\n\n" +
                "        ; Log success and return\n" +
                "        ConsoleWrite(\"Confirmation dialog handled successfully\" & @CRLF)\n" +
                "        Return True\n" +
                "    WEnd\n\n" +
                "    ; Confirmation dialog not found after timeout\n" +
                "    ConsoleWrite(\"No Confirmation dialog detected within timeout period\" & @CRLF)\n" +
                "    Return False\n" +
                "EndFunc\n\n" +
                "; Main execution\n" +
                "HandleConfirmationDialog()\n" +
                "Exit\n";

            // Write script to temporary file
            java.nio.file.Path scriptPath = java.nio.file.Paths.get(UAC_SCRIPT_PATH);
            java.nio.file.Files.write(scriptPath, script.getBytes());

            LOGGER.info("Created Confirmation handling script at: " + UAC_SCRIPT_PATH);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating Confirmation handling script", e);
            autoItAvailable = false;
        }
    }

    /**
     * Class to hold command execution result with UAC detection information
     */
    public static class UacAwareCommandResult extends CommandResult {
        private final boolean uacDetected;

        /**
         * Constructor
         *
         * @param baseResult Base command result
         * @param uacDetected Whether UAC was detected
         */
        public UacAwareCommandResult(CommandResult baseResult, boolean uacDetected) {
            super(baseResult.getCommand(), baseResult.getOutput(), baseResult.isSuccess(), baseResult.getExitCode());
            this.uacDetected = uacDetected;
        }

        /**
         * Check if UAC was detected during execution
         *
         * @return true if UAC was detected, false otherwise
         */
        public boolean wasUacDetected() {
            return uacDetected;
        }
    }
}
