package com.me.testcases;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;

import static com.me.testcases.DataBaseOperationHandler.saveNote;
import static com.me.testcases.ExeInstall.DEFAULT_AUTOIT_DIR;
import static com.me.testcases.ExeInstall.DEFAULT_SCRIPTS_DIR;
import static com.me.util.Uninstall.uacAutoIT;

/**
 * Handles execution of system commands for the run_command operation type
 */
public class CommandExecutor {
    private static final Logger LOGGER = LogManager.getLogger(CommandExecutor.class, LogManager.LOG_TYPE.FW);

    // Patterns for server_home replacement
    private static final Pattern SERVER_HOME_PATTERN = Pattern.compile("\\$\\{SERVER_HOME\\}|server_home",
            Pattern.CASE_INSENSITIVE);

    // Timeout settings
    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    /**
     * Execute a system command and extract values from the command output
     *
     * @param op The operation containing command parameters
     * @return true if execution was successful (exit code 0), false otherwise
     */
    public static boolean executeOperation(Operation op) {
        StringBuilder remarksBuilder = new StringBuilder();
        if (op == null) {
            LOGGER.severe("Operation is null");
            remarksBuilder.append("Operation is null");
            return false;
        }

        op = ResolveOperationParameters.resolveOperationParameters(op);

        String commandToRun = op.getParameter("command_to_run");
        String commandType = op.getParameter("command_type");
        String exactValue = op.getParameter("exact_value");
        String searchValue = op.getParameter("value_to_search");
        String regexPattern = op.getParameter("value");

//        if (!isCommandWhitelisted(commandToRun)) {
//            LOGGER.severe("Command not allowed by whitelist: " + commandToRun);
//            remarksBuilder.append("Command not allowed by whitelist").append(commandToRun);
//
//            return false;
//        }

        if (commandToRun == null || commandToRun.isEmpty()) {
            LOGGER.severe("Command is missing");
            remarksBuilder.append("Command is missing");
            
            return false;
        }

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
//            boolean isWindowsStartCommand = isWindows && commandToRun.toLowerCase().trim().startsWith("start ");

//            if (isWindowsStartCommand) {
//                boolean result = executeWindowsStartCommand(commandToRun, op);
//                remarksBuilder.append("Executed Windows start command");
//                return result;
//            }

            List<String> command = new ArrayList<>();
            if (isWindows) {
                if ("powershell".equalsIgnoreCase(commandType)) {
                    command.add("powershell.exe");
                    command.add("-Command");
                } else {
                    command.add("cmd.exe");
                    command.add("/c");
                }
                command.add(commandToRun);
            } else {
                command.add("/bin/sh");
                command.add("-c");
                command.add(commandToRun);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            LOGGER.info("Executing command : "+pb.command());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.info("Output: " + line);
                }
            }

            boolean completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                String autoItDir = op.getParameter("autoit_dir");
                String baseDir = op.getParameter("base_dir");
                String scriptsDir = op.getParameter("scripts_dir");

                if (baseDir == null || baseDir.isEmpty()) {
                    baseDir = System.getProperty("user.dir");
                }

                if (scriptsDir == null || scriptsDir.isEmpty()) {
                    scriptsDir = Paths.get(baseDir, DEFAULT_SCRIPTS_DIR).toString();
                }

                if (autoItDir == null || autoItDir.isEmpty()) {
                    autoItDir = Paths.get(baseDir, DEFAULT_AUTOIT_DIR).toString();
                }

                if (!autoItDir.isEmpty() && autoItDir != null  && !scriptsDir.isEmpty() && scriptsDir != null) {
                    LOGGER.log(Level.INFO," AutoIT script called for UAC");
                    uacAutoIT(autoItDir, scriptsDir);
                }

                process.destroyForcibly();
                LOGGER.info("Command execution timed out and process was killed");
            } else {
                LOGGER.info("Command completed with exit code: " + process.exitValue());
            }
            completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            String outputStr = output.toString();
            op.setOutputValue(outputStr);

            if (!completed) {
                LOGGER.warning("Command execution timed out");
                remarksBuilder.append("Command execution timed out");
                op.setRemarks(remarksBuilder.toString());
                process.destroyForcibly();
                return false;
            }

            boolean valueFound = false;
            String extractedValue = null;

            if (exactValue != null && !exactValue.isEmpty()) {
                valueFound = outputStr.contains(exactValue);
                extractedValue = exactValue;
                if (valueFound && op.hasNote()) {
                    saveNote(op, exactValue);
                }
            } else if (searchValue != null && !searchValue.isEmpty() && regexPattern != null && !regexPattern.isEmpty() && !valueFound) {
                String[] lines = outputStr.split("\r\n|\r|\n");
                for (String line : lines) {
                    if (line.contains(searchValue)) {
                        try {
                            Pattern pattern = Pattern.compile(regexPattern);
                            Matcher matcher = pattern.matcher(line);
                            if (matcher.find()) {
                                extractedValue = matcher.group(0);
                                valueFound = true;
                                if (op.hasNote()) {
                                    saveNote(op, extractedValue);
                                }
                                break;
                            }
                        } catch (Exception e) {
                            LOGGER.warning("Regex error: " + e.getMessage());
                            remarksBuilder.append("Regex error: ").append(e.getMessage());
                            op.setRemarks(remarksBuilder.toString());
                            return false;
                        }
                    }
                }
            }

            String expected =  exactValue != null ? extractedValue : "";
            String actual = valueFound ? extractedValue : outputStr.toString();
            if (exitCode == 0 && (expected == null || valueFound)) {
                remarksBuilder.append("Command executed successfully with exit code 0. ");
                remarksBuilder.append("Expected: ").append(expected).append(", Actual output: ").append(actual).append(". ");
                op.setRemarks(remarksBuilder.toString());
                return true;
            } else {
                String reason = "";
                if (exitCode != 0) reason += "Exit code was " + exitCode + ". ";
                if (expected != null && !valueFound) reason += "Expected: " + expected + ", Actual output : " + actual + ". ";
                if (searchValue != null && !valueFound) reason += "Search value not found or regex did not match. ";
                remarksBuilder.append("Command execution failed. ").append(reason);
                op.setRemarks(remarksBuilder.toString());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error executing command", e);
            remarksBuilder.append("Error executing command: ").append(e.getMessage());
            op.setRemarks(remarksBuilder.toString());
            
            return false;
        }
    }

    /**
     * Static whitelist of allowed command patterns
     * Commands matching these regex patterns will be permitted to execute
     */
    private static final List<Pattern> WHITELISTED_COMMANDS = new ArrayList<>();

    static {
        WHITELISTED_COMMANDS.add(Pattern.compile("^ipconfig( /all)?$", Pattern.CASE_INSENSITIVE));
        WHITELISTED_COMMANDS.add(Pattern.compile("^(dir|ls)( -[\\w]+)?( [\\w/\\\\.-]+)?$", Pattern.CASE_INSENSITIVE));
        WHITELISTED_COMMANDS.add(Pattern.compile("^(echo|type) .+$", Pattern.CASE_INSENSITIVE));
        WHITELISTED_COMMANDS.add(Pattern.compile("^netstat( -[\\w]+)*$", Pattern.CASE_INSENSITIVE));
        WHITELISTED_COMMANDS.add(Pattern.compile("^systeminfo$", Pattern.CASE_INSENSITIVE));
        WHITELISTED_COMMANDS.add(Pattern.compile("^whoami$", Pattern.CASE_INSENSITIVE));
        WHITELISTED_COMMANDS.add(Pattern.compile("^hostname$", Pattern.CASE_INSENSITIVE));
        WHITELISTED_COMMANDS.add(Pattern.compile("^echo %USERDOMAIN%$", Pattern.CASE_INSENSITIVE));
        // Add more patterns as needed
    }

    // Then, create a new list for direct string comparison
    private static final List<String> DIRECT_MATCH_COMMANDS = new ArrayList<>();

    static {
        // Add direct match commands
        DIRECT_MATCH_COMMANDS.add("$serviceName='ManageEngine UEMS - Agent';$programName='ManageEngine UEMS - Agent';$serviceExists=Get-Service -Name $serviceName -ErrorAction SilentlyContinue;$programExists=(Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* , HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -eq $programName });if ($serviceExists -or $programExists){$false}else{$true}");
    }

    /**
     * Check if a command is in the whitelist
     * @param command Command to check
     * @return true if whitelisted, false otherwise
     */
    private static boolean isCommandWhitelisted(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }

        // First check for direct matches
        String trimmedCommand = command.trim();
        for (String directCommand : DIRECT_MATCH_COMMANDS) {
            LOGGER.info("Checking command against direct match: " + directCommand);
            if (directCommand.equalsIgnoreCase(trimmedCommand)) {
                LOGGER.info("Command matched direct whitelist entry");
                return true;
            }
        }

        // Check against all patterns
        for (Pattern pattern : WHITELISTED_COMMANDS) {
            LOGGER.info("Checking command against pattern: " + pattern.pattern());
            if (pattern.matcher(command.trim()).matches()) {
                LOGGER.info("Command matched whitelist pattern: " + pattern.pattern());
                return true;
            }
        }

        LOGGER.warning("Command not in whitelist: " + command);
        return false;
    }

    /**
     * Special handling for Windows 'start' commands which launch processes in a non-blocking way
     *
     * @param command The full command string
     * @param op The operation object for storing results
     * @return true if the command was launched successfully
     */
    private static boolean executeWindowsStartCommand(String command, Operation op) {
        try {
            // Build the command with /WAIT flag to be safe
            List<String> cmdList = new ArrayList<>();
            cmdList.add("cmd.exe");//NO I18N
            cmdList.add("/c");//NO I18N
            cmdList.add(command);

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(true);

            // Start the process
            Process process = pb.start();

            // For 'start' commands, don't wait too long as they return immediately
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);

            // Whether it completes or times out is actually not important
            // as long as the child process was launched
            int exitCode = completed ? process.exitValue() : 0;

            // Brief pause to allow the process to initialize
            Thread.sleep(1000);

            LOGGER.info("Start command executed with exit code: " + exitCode);//NO I18N
            op.setOutputValue("Windows start command executed. Exit code: " + exitCode);//NO I18N

            // Windows start command generally succeeds in launching even if the app has issues
            return true;
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error executing Windows start command", e);//NO I18N
            op.setOutputValue("Error: " + e.getMessage());//NO I18N
            return false;
        }
    }

    /**
     * Execute a command and return its output
     *
     * @param command The command to execute
     * @return The command output as a string
     */
    public static String executeCommandAndGetOutput(String command) {
        LOGGER.info("Executing command to get output: " + command);//NO I18N

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");//NO I18N

            List<String> cmdList = new ArrayList<>();

            if (isWindows) {
                cmdList.add("cmd.exe");//NO I18N
                cmdList.add("/c");//NO I18N
                cmdList.add(command);
            } else {
                cmdList.add("/bin/sh");//NO I18N
                cmdList.add("-c");//NO I18N
                cmdList.add(command);
            }

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(true);

            // Start the process
            Process process = pb.start();

            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");//NO I18N
                }
            }

            // Wait for process to complete
            process.waitFor();

            return output.toString().trim();
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error executing command", e);//NO I18N
            return "";
        }
    }

    public static String executeCommandAndGetOutput(List<String> command, File executableLocation, Map<String,String> envMap) {
        LOGGER.info("Executing command to get output: " + command);//NO I18N

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");//NO I18N

            List<String> cmdList = new ArrayList<>();

            if (isWindows) {
                cmdList.add("cmd.exe");//NO I18N
                cmdList.add("/c");//NO I18N
                cmdList.addAll(command);
            } else {
                cmdList.add("/bin/sh");//NO I18N
                cmdList.add("-c");//NO I18N
                cmdList.addAll(command);
            }

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.inheritIO();
            if (executableLocation != null && executableLocation.exists()) {
                pb.directory(executableLocation);
            }
            pb.redirectErrorStream(true);

            if (envMap != null){
                pb.environment().putAll(envMap);
            }

            // Start the process
            Process process = pb.start();
            LOGGER.info("Process started with command: " + pb.command());

            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");//NO I18N
                }
            }

            // Wait for process to complete
            process.waitFor();

            return output.toString().trim();
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error executing command", e);//NO I18N
            return "";
        }
    }

    /**
     * Split a command string into an array handling quotes
     *
     * @param command The command string to split
     * @return Array of command arguments
     */
    private static String[] splitCommand(String command) {
        List<String> commandList = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                currentPart.append(c);
            } else if (c == ' ' && !inQuotes) {
                if (currentPart.length() > 0) {
                    commandList.add(currentPart.toString());
                    currentPart = new StringBuilder();
                }
            } else {
                currentPart.append(c);
            }
        }

        if (currentPart.length() > 0) {
            commandList.add(currentPart.toString());
        }

        return commandList.toArray(new String[0]);
    }
}