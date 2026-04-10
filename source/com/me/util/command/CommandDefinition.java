package com.me.util.command;

import com.me.util.LogManager;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Class representing a command definition that can be stored in a repository
 * and executed later.
 */
public class CommandDefinition {
    private static final Logger LOGGER = LogManager.getLogger(CommandDefinition.class, LogManager.LOG_TYPE.FW);

    private final String key;
    private final String command;
    private final CommandType type;
    private final int timeoutSeconds;
    private final String outputParserRegex;
    private final boolean requiresUacHandling;

    /**
     * Create a command definition
     *
     * @param key Unique identifier for the command
     * @param command Command string or script to execute
     * @param type Command type (CMD or POWERSHELL)
     * @param timeoutSeconds Timeout in seconds
     * @param outputParserRegex Optional regex to parse output
     * @param requiresUacHandling Whether this command requires UAC handling
     */
    public CommandDefinition(String key, String command, CommandType type, int timeoutSeconds,
                             String outputParserRegex, boolean requiresUacHandling) {
        LOGGER.fine("Creating command definition: " + key + " (" + type +
                  ", timeout=" + timeoutSeconds + "s" +
                  (requiresUacHandling ? ", UAC handling" : "") + ")");
        this.key = key;
        this.command = command;
        this.type = type;
        this.timeoutSeconds = timeoutSeconds;
        this.outputParserRegex = outputParserRegex;
        this.requiresUacHandling = requiresUacHandling;
    }

    /**
     * Create a command definition with default timeout and no parser
     *
     * @param key Unique identifier for the command
     * @param command Command string or script to execute
     * @param type Command type (CMD or POWERSHELL)
     */
    public CommandDefinition(String key, String command, CommandType type) {
        this(key, command, type, 30, null, false);
    }

    /**
     * Create a command definition with default timeout, no parser, and specified UAC handling
     *
     * @param key Unique identifier for the command
     * @param command Command string or script to execute
     * @param type Command type (CMD or POWERSHELL)
     * @param requiresUacHandling Whether this command requires UAC handling
     */
    public CommandDefinition(String key, String command, CommandType type, boolean requiresUacHandling) {
        this(key, command, type, 30, null, requiresUacHandling);
    }

    /**
     * Create a command definition with timeout and no parser
     *
     * @param key Unique identifier for the command
     * @param command Command string or script to execute
     * @param type Command type (CMD or POWERSHELL)
     * @param timeoutSeconds Timeout in seconds
     */
    public CommandDefinition(String key, String command, CommandType type, int timeoutSeconds) {
        this(key, command, type, timeoutSeconds, null, false);
    }

    /**
     * Create a command definition with timeout, no parser, and specified UAC handling
     *
     * @param key Unique identifier for the command
     * @param command Command string or script to execute
     * @param type Command type (CMD or POWERSHELL)
     * @param timeoutSeconds Timeout in seconds
     * @param requiresUacHandling Whether this command requires UAC handling
     */
    public CommandDefinition(String key, String command, CommandType type,
                             int timeoutSeconds, boolean requiresUacHandling) {
        this(key, command, type, timeoutSeconds, null, requiresUacHandling);
    }

    /**
     * Execute this command with given parameters
     *
     * @param params Parameters to substitute in the command
     * @return Result of the command execution
     */
    public CommandResult execute(Map<String, String> params) {
        LOGGER.fine("Executing command '" + key + "'" +
                  (params != null && !params.isEmpty() ? " with parameters" : "") +
                  (requiresUacHandling ? " with UAC handling" : ""));

        CommandResult result;
        if (type == CommandType.POWERSHELL) {
            if (requiresUacHandling && EnhancedUacHandler.isAutoItAvailable()) {
                LOGGER.fine("Executing as PowerShell command with enhanced UAC handling: " + command);
                result = CommandExecutor.executePowerShellWithUac(command, params, timeoutSeconds);
            } else {
                LOGGER.fine("Executing as PowerShell command: " + command);
                result = CommandExecutor.executePowerShell(command, params, timeoutSeconds);
            }
        } else {
            if (requiresUacHandling && EnhancedUacHandler.isAutoItAvailable()) {
                LOGGER.fine("Executing as CMD command with enhanced UAC handling: " + command);
                result = CommandExecutor.executeWithUac(command, params, timeoutSeconds);
            } else {
                LOGGER.fine("Executing as CMD command: " + command);
                result = CommandExecutor.execute(command, params, timeoutSeconds);
            }
        }

        if (result != null) {
            LOGGER.fine("Command '" + key + "' execution complete: success=" + result.isSuccess() +
                      ", exitCode=" + result.getExitCode() +
                      (result.isTimedOut() ? ", TIMED OUT" : ""));

            // Check for UAC detection if this was a UAC-aware result
            if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
                boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
                if (uacDetected) {
                    LOGGER.info("UAC dialog was detected and handled during command execution: " + key);
                }
            }

            // Parse output if regex is specified
            if (result.isSuccess() && outputParserRegex != null && !outputParserRegex.isEmpty()) {
                String parsedOutput = extractPattern(result.getOutput(), outputParserRegex);
                LOGGER.fine("Parsed output using regex '" + outputParserRegex + "': " +
                          (parsedOutput.isEmpty() ? "[no match]" : parsedOutput));
            }
        } else {
            LOGGER.warning("Null result from command execution: " + key);
        }

        return result;
    }

    /**
     * Execute this command without parameters
     *
     * @return Result of the command execution
     */
    public CommandResult execute() {
        return execute(null);
    }

    /**
     * Get the command key
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the command string
     */
    public String getCommand() {
        return command;
    }

    /**
     * Get the command type
     */
    public CommandType getType() {
        return type;
    }

    /**
     * Get the timeout in seconds
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Get the output parser regex
     */
    public String getOutputParserRegex() {
        return outputParserRegex;
    }

    /**
     * Check if this command requires UAC handling
     */
    public boolean requiresUacHandling() {
        return requiresUacHandling;
    }

    /**
     * Parse the output of a command result using this definition's parser regex
     *
     * @param result The command result to parse
     * @return Parsed output or original output if no parser is defined
     */
    public String parseOutput(CommandResult result) {
        if (outputParserRegex != null && !outputParserRegex.isEmpty()) {
            LOGGER.fine("Parsing output of command '" + key + "' using regex: " + outputParserRegex);
            String parsed = extractPattern(result.getOutput(), outputParserRegex);
            LOGGER.fine("Parsed result: " + (parsed.isEmpty() ? "[no match]" : parsed));
            return parsed;
        }
        return result.getOutput() != null ? result.getOutput().trim() : "";
    }

    /**
     * Extract a pattern from the output using a regex
     *
     * @param output The output to parse
     * @param regex The regex to use
     * @return The matched pattern or empty string if no match
     */
    private String extractPattern(String output, String regex) {
        if (output == null || output.isEmpty() || regex == null || regex.isEmpty()) {
            return "";
        }

        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(output);

            if (matcher.find()) {
                return matcher.group(matcher.groupCount() > 0 ? 1 : 0).trim();
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing output with regex '" + regex + "': " + e.getMessage());
        }

        return "";
    }

    @Override
    public String toString() {
        return "CommandDefinition{key='" + key + "', type=" + type +
               ", timeout=" + timeoutSeconds + "s" +
               (requiresUacHandling ? ", UAC handling" : "") +
               (outputParserRegex != null ? ", has parser" : "") + "}";
    }
}
