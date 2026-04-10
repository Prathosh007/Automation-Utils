package com.me.util.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.util.LogManager;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.util.GOATCommonConstants.DEFAULT_COMMANDS_FILE;

/**
 * A registry for storing and retrieving command definitions.
 * Commands are loaded from a JSON configuration file.
 */
public class CommandRegistry {
    private static final Logger LOGGER = LogManager.getLogger(CommandRegistry.class, LogManager.LOG_TYPE.FW);
    private static final Map<String, CommandDefinition> commands = new HashMap<>();
    private static final int DEFAULT_TIMEOUT = 30;

    // Default command file path
    private static boolean defaultFileLoaded = false;

    // Global setting for UAC handling
    private static boolean globalUacHandlingEnabled = true;

    // Static initializer to load commands when the class is first used
    static {
        LOGGER.info("Initializing CommandRegistry and loading default commands");
        loadCommandsFromDefaultFile();
    }

    /**
     * Enable or disable global UAC handling for all commands
     *
     * @param enabled true to enable UAC handling, false to disable
     */
    public static void setGlobalUacHandling(boolean enabled) {
        globalUacHandlingEnabled = enabled;
        LOGGER.info("Global UAC handling " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Check if global UAC handling is enabled
     *
     * @return true if global UAC handling is enabled
     */
    public static boolean isGlobalUacHandlingEnabled() {
        return globalUacHandlingEnabled;
    }

    /**
     * Register a new command with custom timeout and output parser
     *
     * @param key Unique identifier for the command
     * @param command Command string to execute
     * @param type Command type (CMD or POWERSHELL)
     * @param timeoutSeconds Timeout in seconds
     * @param outputParserRegex Optional regex to parse output
     * @param requiresUac Whether the command requires UAC elevation
     */
    private static void registerCommand(String key, String command, CommandType type, int timeoutSeconds, String outputParserRegex, boolean requiresUac) {
        CommandDefinition cmdDef = new CommandDefinition(key, command, type, timeoutSeconds, outputParserRegex, requiresUac);
        commands.put(key, cmdDef);
        LOGGER.info("Registered command: " + key + " (" + type + ", timeout=" + timeoutSeconds + "s, UAC=" + requiresUac + ")");
    }

    /**
     * Register a new command with default timeout and no parser
     *
     * @param key Unique identifier for the command
     * @param command Command string to execute
     * @param type Command type (CMD or POWERSHELL)
     */
    public static void registerCommand(String key, String command, CommandType type) {
        registerCommand(key, command, type, DEFAULT_TIMEOUT, null, false);
    }

    /**
     * Register a new command with custom timeout and output parser
     *
     * @param key Unique identifier for the command
     * @param command Command string to execute
     * @param type Command type (CMD or POWERSHELL)
     * @param timeoutSeconds Timeout in seconds
     * @param outputParserRegex Optional regex to parse output
     */
    public static void registerCommand(String key, String command, CommandType type, int timeoutSeconds, String outputParserRegex) {
        registerCommand(key, command, type, timeoutSeconds, outputParserRegex, false);
    }

    /**
     * Get a command definition by key
     *
     * @param key Command identifier
     * @return CommandDefinition or null if not found
     */
    public static CommandDefinition getCommand(String key) {
        // If commands haven't been loaded yet, try to load them
        if (commands.isEmpty() && !defaultFileLoaded) {
            LOGGER.info("Command registry is empty, attempting to load from default file");
            loadCommandsFromDefaultFile();
        }

        CommandDefinition cmd = commands.get(key);

        // If command not found in registry, try to load it from default file
        if (cmd == null && !defaultFileLoaded) {
            LOGGER.info("Command '" + key + "' not found, attempting to reload from default file");
            loadCommandsFromDefaultFile();
            cmd = commands.get(key);
        }

        if (cmd == null) {
            LOGGER.warning("Command not found: " + key);
        } else {
            LOGGER.fine("Retrieved command: " + key);
        }

        return cmd;
    }

    /**
     * Check if a command exists in the registry
     *
     * @param key Command identifier
     * @return true if the command exists
     */
    public static boolean hasCommand(String key) {
        boolean exists = getCommand(key) != null;
        LOGGER.fine("Command '" + key + "' exists: " + exists);
        return exists;
    }

    /**
     * Load commands from a JSON file
     *
     * @param filePath Path to the JSON file
     * @return Number of commands loaded
     */
    public static int loadCommandsFromJsonFile(String filePath) {
        LOGGER.info("Loading commands from JSON file: " + filePath);

        File file = new File(filePath);
        if (!file.exists()) {
            LOGGER.warning("Command file not found: " + filePath);
            return 0;
        }

        int commandsLoaded = 0;

        try (Reader reader = new FileReader(file)) {
            LOGGER.fine("Reading JSON command file: " + filePath);
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            LOGGER.fine("Successfully parsed JSON file");

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String key = entry.getKey();
                JsonObject commandObj = entry.getValue().getAsJsonObject();

                LOGGER.fine("Processing command definition: " + key);

                String command = commandObj.get("command").getAsString();
                CommandType type = commandObj.get("type").getAsString().equalsIgnoreCase("powershell") ?
                        CommandType.POWERSHELL : CommandType.CMD;

                int timeout = DEFAULT_TIMEOUT;
                if (commandObj.has("timeout") && !commandObj.get("timeout").isJsonNull()) {
                    timeout = commandObj.get("timeout").getAsInt();
                }

                String parser = null;
                if (commandObj.has("outputParser") && !commandObj.get("outputParser").isJsonNull()) {
                    parser = commandObj.get("outputParser").getAsString();
                }

                boolean requiresUac = false;
                if (commandObj.has("requiresUac") && !commandObj.get("requiresUac").isJsonNull()) {
                    requiresUac = commandObj.get("requiresUac").getAsBoolean();
                }

                registerCommand(key, command, type, timeout, parser, requiresUac);
                commandsLoaded++;
            }

            LOGGER.info("Loaded " + commandsLoaded + " commands from " + filePath);

            // If we loaded commands from the default file, mark it as loaded
            if (DEFAULT_COMMANDS_FILE.equals(filePath)) {
                LOGGER.fine("Marking default file as loaded");
                defaultFileLoaded = true;
            }

            return commandsLoaded;
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Command file not found: " + filePath, e);
            if (DEFAULT_COMMANDS_FILE.equals(filePath)) {
                defaultFileLoaded = true;
            }
            return 0;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading commands from " + filePath, e);

            // Mark default file as checked even if it failed
            if (DEFAULT_COMMANDS_FILE.equals(filePath)) {
                defaultFileLoaded = true;
            }

            return 0;
        }
    }

    /**
     * Load all commands from the default commands file
     *
     * @return Number of commands loaded
     */
    public static int loadCommandsFromDefaultFile() {
        LOGGER.info("Loading commands from default file: " + DEFAULT_COMMANDS_FILE);
        int count = loadCommandsFromJsonFile(DEFAULT_COMMANDS_FILE);
        defaultFileLoaded = true;
        return count;
    }

    /**
     * Set a custom path to the commands file and load from it
     *
     * @param filePath New path to the commands file
     * @return Number of commands loaded
     */
    public static int setCommandsFile(String filePath) {
        LOGGER.info("Setting custom commands file: " + filePath);
        defaultFileLoaded = false;
        commands.clear();
        return loadCommandsFromJsonFile(filePath);
    }

    /**
     * Load commands from multiple JSON files
     *
     * @param filePaths Array of paths to JSON files
     * @return Total number of commands loaded
     */
    public static int loadCommandsFromFiles(String... filePaths) {
        LOGGER.info("Loading commands from multiple files: " + String.join(", ", filePaths));
        int total = 0;
        for (String filePath : filePaths) {
            total += loadCommandsFromJsonFile(filePath);
        }
        LOGGER.info("Total commands loaded from multiple files: " + total);
        return total;
    }

    /**
     * Execute a command by its key with parameters
     *
     * @param key Command identifier
     * @param params Parameters to substitute in the command
     * @return Result of the command execution or null if command not found
     */
    public static CommandResult executeCommand(String key, Map<String, String> params) {
        LOGGER.info("Executing command: " + key + (params != null && !params.isEmpty() ? " with parameters" : ""));

        if (params != null && !params.isEmpty() && LOGGER.isLoggable(Level.FINE)) {
            for (Map.Entry<String, String> param : params.entrySet()) {
                String paramValue = param.getKey().toLowerCase().contains("password") ? "********" : param.getValue();
                LOGGER.fine("  Parameter: " + param.getKey() + "=" + paramValue);
            }
        }

        CommandDefinition cmdDef = getCommand(key);
        if (cmdDef == null) {
            LOGGER.warning("Command not found: " + key);
            return null;
        }

        // Apply global UAC handling if enabled
        CommandResult result;
        if (globalUacHandlingEnabled || cmdDef.requiresUacHandling()) {
            LOGGER.fine("Using UAC handling for command: " + key);
            if (cmdDef.getType() == CommandType.POWERSHELL) {
                result = CommandExecutor.executePowerShellWithUac(cmdDef.getCommand(), params, cmdDef.getTimeoutSeconds());
            } else {
                result = CommandExecutor.executeWithUac(cmdDef.getCommand(), params, cmdDef.getTimeoutSeconds());
            }
        } else {
            result = cmdDef.execute(params);
        }

        if (result != null) {
            boolean uacDetected = false;
            if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
                uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
            }

            LOGGER.fine("Command execution complete: " + key + ", success=" + result.isSuccess() +
                      ", exitCode=" + result.getExitCode() +
                      (result.isTimedOut() ? ", TIMED OUT" : "") +
                      (uacDetected ? ", UAC handled" : ""));

            if (!result.isSuccess() && result.getOutput() != null && !result.getOutput().isEmpty()) {
                LOGGER.fine("Command output (failed): " + result.getOutput());
            }
        } else {
            LOGGER.warning("Null result from command execution: " + key);
        }

        return result;
    }

    /**
     * Execute a command by its key without parameters
     *
     * @param key Command identifier
     * @return Result of the command execution or null if command not found
     */
    public static CommandResult executeCommand(String key) {
        return executeCommand(key, null);
    }

    /**
     * Get all registered command keys
     *
     * @return Array of command keys
     */
    public static String[] getAllCommandKeys() {
        if (commands.isEmpty() && !defaultFileLoaded) {
            LOGGER.info("Command registry is empty, loading from default file before returning keys");
            loadCommandsFromDefaultFile();
        }
        String[] keys = commands.keySet().toArray(new String[0]);
        LOGGER.fine("Retrieved " + keys.length + " command keys");
        return keys;
    }

    /**
     * Clear all registered commands
     */
    public static void clearCommands() {
        LOGGER.info("Clearing all registered commands");
        commands.clear();
        defaultFileLoaded = false;
    }

    /**
     * Reload commands from the default file
     *
     * @return Number of commands loaded
     */
    public static int reloadCommands() {
        LOGGER.info("Reloading commands from default file");
        clearCommands();
        return loadCommandsFromDefaultFile();
    }
}
