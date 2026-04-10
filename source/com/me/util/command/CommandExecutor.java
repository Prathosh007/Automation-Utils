package com.me.util.command;

import com.me.util.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core command execution utility that provides standardized methods for executing
 * system commands with timeout capability and output parsing.
 */
public class CommandExecutor {
    private static final Logger LOGGER = LogManager.getLogger(CommandExecutor.class, LogManager.LOG_TYPE.FW);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    // Flag to determine if UAC handling should be enabled by default
    private static final boolean ENABLE_DEFAULT_UAC_HANDLING = true;

    // Initialize handlers
    static {
        try {
            // Initialize EnhancedUacHandler
            boolean autoItAvailable = EnhancedUacHandler.isAutoItAvailable();
            LOGGER.info("Enhanced UAC handling " + (autoItAvailable ? "available" : "not available (AutoIT not found)"));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error initializing UAC handler", e);
        }
    }

    /**
     * Execute a command with the default timeout
     *
     * @param command The command to execute
     * @return CommandResult object containing execution results
     */
    public static CommandResult execute(String command) {
        LOGGER.fine("Executing command with default timeout: " + command);
        return execute(command, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a command with parameter substitution
     *
     * @param command The command template with placeholders
     * @param params Map of parameter names and values to substitute
     * @return CommandResult object containing execution results
     */
    public static CommandResult execute(String command, Map<String, String> params) {
        LOGGER.fine("Executing command with parameters: " + command);
        String processedCommand = substituteParams(command, params);
        LOGGER.fine("Command after parameter substitution: " + maskSensitiveInfo(processedCommand));
        return execute(processedCommand, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a command with parameter substitution and specific timeout
     *
     * @param command The command template with placeholders
     * @param params Map of parameter names and values to substitute
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult execute(String command, Map<String, String> params, int timeoutSeconds) {
        LOGGER.fine("Executing command with parameters and timeout " + timeoutSeconds + "s: " + command);
        String processedCommand = substituteParams(command, params);
        LOGGER.fine("Command after parameter substitution: " + maskSensitiveInfo(processedCommand));
        return execute(processedCommand, timeoutSeconds);
    }

    /**
     * Execute a PowerShell command with the default timeout
     *
     * @param script PowerShell script content
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShell(String script) {
        LOGGER.fine("Executing PowerShell script with default timeout: " + script);
        return executePowerShell(script, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a PowerShell command with parameter substitution
     *
     * @param script PowerShell script template with placeholders
     * @param params Map of parameter names and values to substitute
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShell(String script, Map<String, String> params) {
        LOGGER.fine("Executing PowerShell script with parameters: " + script);
        String processedScript = substituteParams(script, params);
        LOGGER.fine("PowerShell script after parameter substitution: " + maskSensitiveInfo(processedScript));
        return executePowerShell(processedScript, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a PowerShell command with parameter substitution and specific timeout
     *
     * @param script PowerShell script template with placeholders
     * @param params Map of parameter names and values to substitute
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShell(String script, Map<String, String> params, int timeoutSeconds) {
        LOGGER.fine("Executing PowerShell script with parameters and timeout " + timeoutSeconds + "s: " + script);
        String processedScript = substituteParams(script, params);
        LOGGER.fine("PowerShell script after parameter substitution: " + maskSensitiveInfo(processedScript));
        return executePowerShell(processedScript, timeoutSeconds);
    }

    /**
     * Execute a command with a specific timeout
     *
     * @param command The command to execute
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult execute(String command, int timeoutSeconds) {
        // If default UAC handling is enabled, delegate to the UAC-aware method
        if (ENABLE_DEFAULT_UAC_HANDLING && EnhancedUacHandler.isAutoItAvailable()) {
            LOGGER.info("Using enhanced UAC handling for command execution by default");
            return executeWithUac(command, timeoutSeconds);
        }

        LOGGER.info("Executing CMD command with " + timeoutSeconds + "s timeout: " + maskSensitiveInfo(command));

        try {
            LOGGER.fine("Creating process for command: cmd.exe /c " + command);
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);

            long startTime = System.currentTimeMillis();
            LOGGER.fine("Starting process execution");
            Process process = pb.start();

            List<String> outputLines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Start reading output asynchronously
            Thread outputThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputLines.add(line);
                        LOGGER.finest("Process output: " + line);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error reading process output: " + e.getMessage());
                }
            });
            outputThread.setName("CMD-Output-Reader-" + System.currentTimeMillis());
            outputThread.start();

            // Wait for process with timeout
            LOGGER.fine("Waiting for process to complete with timeout: " + timeoutSeconds + "s");
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                LOGGER.warning("Command execution timed out after " + timeoutSeconds + " seconds: " + maskSensitiveInfo(command));
                process.destroyForcibly();
                return CommandResult.timeout(command);
            }

            // Make sure we get all output
            try {
                LOGGER.fine("Process completed, waiting for output reader thread to finish");
                outputThread.join(2000);
            } catch (InterruptedException e) {
                LOGGER.warning("Interrupted while waiting for output reader thread: " + e.getMessage());
            }

            int exitCode = process.exitValue();
            String output = String.join("\n", outputLines);

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Command execution completed in " + duration + "ms with exit code: " + exitCode);
            if (exitCode != 0) {
                LOGGER.warning("Command failed with exit code " + exitCode + ": " + maskSensitiveInfo(command));
                if (!output.isEmpty()) {
                    LOGGER.warning("Error output: " + output);
                }
            } else {
                LOGGER.fine("Command executed successfully");
            }

            return new CommandResult(command, output, exitCode == 0, exitCode);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing command: " + maskSensitiveInfo(command), e);
            return CommandResult.error(command, e);
        }
    }

    /**
     * Execute a PowerShell command with specific timeout
     *
     * @param script PowerShell script content
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShell(String script, int timeoutSeconds) {
        // If default UAC handling is enabled, delegate to the UAC-aware method
        if (ENABLE_DEFAULT_UAC_HANDLING && EnhancedUacHandler.isAutoItAvailable()) {
            LOGGER.info("Using enhanced UAC handling for PowerShell execution by default");
            return executePowerShellWithUac(script, timeoutSeconds);
        }

        LOGGER.info("Executing PowerShell script with " + timeoutSeconds + "s timeout: " + maskSensitiveInfo(script));

        try {
            List<String> command = new ArrayList<>();
            command.add("powershell.exe");
            command.add("-NoProfile");
            command.add("-ExecutionPolicy");
            command.add("Bypass");
            command.add("-Command");
            command.add(script);

            LOGGER.fine("Creating process for PowerShell script");
            LOGGER.info("Going to execute PowerShell command: " + maskSensitiveInfo(String.join(" ", command)));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            long startTime = System.currentTimeMillis();
            LOGGER.fine("Starting PowerShell process execution");
            Process process = pb.start();

            List<String> outputLines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Start reading output asynchronously
            Thread outputThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputLines.add(line);
                        LOGGER.finest("PowerShell output: " + line);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error reading PowerShell output: " + e.getMessage());
                }
            });
            outputThread.setName("PS-Output-Reader-" + System.currentTimeMillis());
            outputThread.start();

            // Wait for process with timeout
            LOGGER.fine("Waiting for PowerShell process to complete with timeout: " + timeoutSeconds + "s");
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                LOGGER.warning("PowerShell execution timed out after " + timeoutSeconds + " seconds: " + maskSensitiveInfo(script));
                process.destroyForcibly();
                return CommandResult.timeout(script);
            }

            // Make sure we get all output
            try {
                LOGGER.fine("PowerShell process completed, waiting for output reader thread to finish");
                outputThread.join(2000);
            } catch (InterruptedException e) {
                LOGGER.warning("Interrupted while waiting for PowerShell output reader thread: " + e.getMessage());
            }

            int exitCode = process.exitValue();
            String output = outputLines.toString();

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("PowerShell execution completed in " + duration + "ms with exit code: " + exitCode);
            if (exitCode != 0) {
                LOGGER.warning("PowerShell script failed with exit code " + exitCode + ": " + maskSensitiveInfo(script));
                if (!output.isEmpty()) {
                    LOGGER.warning("Error output: " + output);
                }
            } else {
                LOGGER.fine("PowerShell script executed successfully");
            }

            return new CommandResult(script, output, exitCode == 0, exitCode);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing PowerShell script: " + maskSensitiveInfo(script), e);
            return CommandResult.error(script, e);
        }
    }

    /**
     * Execute a command with UAC elevation and default timeout using enhanced detection
     *
     * @param command The command to execute with elevation
     * @return CommandResult object containing execution results
     */
    public static CommandResult executeWithUac(String command) {
        LOGGER.fine("Executing command with enhanced UAC handling (default timeout): " + command);
        return executeWithUac(command, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a command with UAC elevation and specific timeout using enhanced detection
     *
     * @param command The command to execute with elevation
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult executeWithUac(String command, int timeoutSeconds) {
        LOGGER.info("Executing command with enhanced UAC handling (" + timeoutSeconds + "s timeout): " + maskSensitiveInfo(command));

        EnhancedUacHandler.UacAwareCommandResult result = EnhancedUacHandler.execute(command, null, timeoutSeconds);

        if (result.wasUacDetected()) {
            LOGGER.info("UAC prompt was detected and handled during command execution");
        }

        return result;
    }

    /**
     * Execute a batch file with UAC elevation and default timeout using enhanced detection
     *
     * @param batchFilePath Path to the batch file to execute with elevation
     * @return CommandResult object containing execution results
     */
    public static CommandResult executeBatchFileWithUac(String batchFilePath) {
        LOGGER.fine("Executing batch file with enhanced UAC handling (default timeout): " + batchFilePath);
        return executeBatchFileWithUac(batchFilePath, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a batch file with UAC elevation and specific timeout using enhanced detection
     *
     * @param batchFilePath Path to the batch file to execute with elevation
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult executeBatchFileWithUac(String batchFilePath, int timeoutSeconds) {
        LOGGER.info("Executing batch file with enhanced UAC handling (" + timeoutSeconds + "s timeout): " + batchFilePath);

        EnhancedUacHandler.UacAwareCommandResult result = EnhancedUacHandler.executeBatchFile(batchFilePath, timeoutSeconds);

        if (result.wasUacDetected()) {
            LOGGER.info("UAC prompt was detected and handled during batch file execution");
        }

        return result;
    }

    /**
     * Execute a command with parameter substitution and UAC elevation using enhanced detection
     *
     * @param command The command template with placeholders
     * @param params Map of parameter names and values to substitute
     * @return CommandResult object containing execution results
     */
    public static CommandResult executeWithUac(String command, Map<String, String> params) {
        LOGGER.fine("Executing command with parameters and enhanced UAC handling: " + command);
        String processedCommand = substituteParams(command, params);
        LOGGER.fine("Command after parameter substitution: " + maskSensitiveInfo(processedCommand));
        return executeWithUac(processedCommand, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a command with parameter substitution, UAC elevation and specific timeout using enhanced detection
     *
     * @param command The command template with placeholders
     * @param params Map of parameter names and values to substitute
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult executeWithUac(String command, Map<String, String> params, int timeoutSeconds) {
        LOGGER.fine("Executing command with parameters, enhanced UAC handling and timeout " + timeoutSeconds + "s: " + command);
        String processedCommand = substituteParams(command, params);
        LOGGER.fine("Command after parameter substitution: " + maskSensitiveInfo(processedCommand));
        return executeWithUac(processedCommand, timeoutSeconds);
    }

    /**
     * Execute a PowerShell command with UAC elevation and default timeout using enhanced detection
     *
     * @param script PowerShell script content
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShellWithUac(String script) {
        LOGGER.fine("Executing PowerShell script with enhanced UAC handling (default timeout): " + script);
        return executePowerShellWithUac(script, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a PowerShell command with UAC elevation and specific timeout using enhanced detection
     *
     * @param script PowerShell script content
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShellWithUac(String script, int timeoutSeconds) {
        LOGGER.info("Executing PowerShell script with enhanced UAC handling (" + timeoutSeconds + "s timeout): " + maskSensitiveInfo(script));

        // Create a wrapped PowerShell command that will be executed with UAC
        String wrappedCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -Command \"" +
                                script.replace("\"", "\\\"") + "\"";

        return executeWithUac(wrappedCommand, timeoutSeconds);
    }

    /**
     * Execute a PowerShell command with parameter substitution and UAC elevation using enhanced detection
     *
     * @param script PowerShell script template with placeholders
     * @param params Map of parameter names and values to substitute
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShellWithUac(String script, Map<String, String> params) {
        LOGGER.fine("Executing PowerShell script with parameters and enhanced UAC handling: " + script);
        String processedScript = substituteParams(script, params);
        LOGGER.fine("PowerShell script after parameter substitution: " + maskSensitiveInfo(processedScript));
        return executePowerShellWithUac(processedScript, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Execute a PowerShell command with parameter substitution, UAC elevation and specific timeout using enhanced detection
     *
     * @param script PowerShell script template with placeholders
     * @param params Map of parameter names and values to substitute
     * @param timeoutSeconds Maximum execution time in seconds
     * @return CommandResult object containing execution results
     */
    public static CommandResult executePowerShellWithUac(String script, Map<String, String> params, int timeoutSeconds) {
        LOGGER.fine("Executing PowerShell script with parameters, enhanced UAC handling and timeout " + timeoutSeconds + "s: " + script);
        String processedScript = substituteParams(script, params);
        LOGGER.fine("PowerShell script after parameter substitution: " + maskSensitiveInfo(processedScript));
        return executePowerShellWithUac(processedScript, timeoutSeconds);
    }

    /**
     * Replace parameter placeholders in a command string
     *
     * @param command Command template with ${param} placeholders
     * @param params Map of parameter names and values to substitute
     * @return Processed command with parameters substituted
     */
    private static String substituteParams(String command, Map<String, String> params) {
        if (command == null || params == null || params.isEmpty()) {
            return command;
        }

        String result = command;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue();
            if (result.contains(placeholder)) {
                LOGGER.finest("Substituting parameter: " + entry.getKey() + "=" +
                            (entry.getKey().toLowerCase().contains("password") ? "********" : value));
                result = result.replace(placeholder, value);
            }
        }
        return result;
    }

    /**
     * Mask sensitive information in logs (passwords, etc.)
     *
     * @param text Text that might contain sensitive information
     * @return Masked text safe for logging
     */
    private static String maskSensitiveInfo(String text) {
        if (text == null) {
            return null;
        }

        // Mask password parameters
        String masked = text.replaceAll("(?i)password([^\\s]*)\\s*=\\s*([^\\s]*)", "password$1=********");

        // Mask ConvertTo-SecureString patterns in PowerShell
        masked = masked.replaceAll("ConvertTo-SecureString\\s+'([^']*)'", "ConvertTo-SecureString '********'");
        masked = masked.replaceAll("ConvertTo-SecureString\\s+\"([^\"]*)\"", "ConvertTo-SecureString \"********\"");

        return masked;
    }
}
