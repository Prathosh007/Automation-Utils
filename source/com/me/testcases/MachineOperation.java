package com.me.testcases;

import com.me.Operation;
import com.me.util.LogManager;
import com.me.util.command.CommandDefinition;
import com.me.util.command.CommandRegistry;
import com.me.util.command.CommandResult;
import com.me.util.command.CommandType;
import com.me.util.command.EnhancedUacHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

/**
 * Handler for machine-related operations using the command execution framework
 */
public class MachineOperation {
    private static final Logger LOGGER = LogManager.getLogger(MachineOperation.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute a machine operation
     *
     * @param operation The operation to execute
     * @return true if successful, false otherwise
     */
    public static boolean executeOperation(Operation operation) {
        String action = operation.getParameter("action");
        if (action == null || action.isEmpty()) {
            LOGGER.warning("No action specified for machine_operation");
            operation.setRemarks("No action specified for machine_operation");
            return false;
        }

        LOGGER.info("Executing machine operation: " + action);
        try {
            switch (action.toLowerCase()) {
                case "restart":
                    return restartMachine(operation);
                case "rename":
                    return renameMachine(operation);
                case "connect_network":
                    return connectNetwork(operation);
                case "disconnect_network":
                    return disconnectNetwork(operation);
                case "change_domain":
                    return changeDomain(operation);
                case "change_datetime":
                    return changeDateTime(operation);
                case "change_browser":
                    return changeDefaultBrowser(operation);
                case "get_machine_spec":
                    return getMachineSpec(operation);
                case "change_timezone":
                    return changeTimeZone(operation);
                default:
                    // Try to find a registered command for this action
                    if (CommandRegistry.hasCommand(action.toLowerCase())) {
                        return executeRegisteredCommand(operation, action.toLowerCase());
                    }

                    LOGGER.warning("Unknown machine_operation action: " + action);
                    operation.setRemarks("Unknown machine_operation action: " + action);
                    return false;
            }
        } catch (Exception e) {
            LOGGER.severe("Error executing machine_operation: " + e);
            operation.setRemarks("Error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute a command that's registered in the command registry
     */
    private static boolean executeRegisteredCommand(Operation operation, String commandKey) {
        // Extract all parameters from operation to pass to command
        Map<String, String> params = new HashMap<>();
        for (String paramName : operation.getParameterNames()) {
            if (!"action".equals(paramName)) { // Skip the action parameter
                params.put(paramName, operation.getParameter(paramName));
            }
        }

        // Use enhanced UAC handling by default
        CommandResult result = CommandRegistry.executeCommand(commandKey, params);
        if (result == null) {
            operation.setRemarks("Command not found: " + commandKey);
            return false;
        }

        boolean success = result.isSuccess();
        if (success) {
            String output = result.getTrimmedOutput();
            operation.setRemarks("Output: " + output);
            if (operation.hasNote()) {
                saveNote(operation, output);
            }
        } else {
            String errorMsg = result.isTimedOut() ?
                "Command timed out: " + commandKey :
                "Command failed with exit code " + result.getExitCode() + ": " + result.getOutput();
            operation.setRemarks(errorMsg);
        }

        // Check if UAC was detected and handled
        if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
            boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
            if (uacDetected) {
                LOGGER.info("UAC dialog was detected and handled during command execution: " + commandKey);
            }
        }

        return success;
    }

    private static boolean restartMachine(Operation operation) {
        Map<String, String> params = new HashMap<>();
        params.put("delay", operation.getParameter("delay") != null ? operation.getParameter("delay") : "0");

        CommandResult result = CommandRegistry.executeCommand(operation.getParameter("action"), params);
        if (result == null) {
            operation.setRemarks("Restart command not found in registry");
            return false;
        }

        operation.setRemarks(result.isSuccess() ?
            "Restart command issued successfully." :
            "Failed to issue restart command: " + result.getOutput());

        return result.isSuccess();
    }

    private static boolean getMachineSpec(Operation operation) {
        String infoType = operation.getParameter("info_type");
        if (infoType == null || infoType.isEmpty() && !operation.hasNote()) {
            operation.setRemarks("No info_type or note specified.");
            return false;
        }

        // Map user-friendly info types to command keys
        String commandKey = mapInfoTypeToCommandKey(infoType);
        LOGGER.info("Retrieving machine spec for info_type: " + infoType + " using command key: " + commandKey);

        // Use command registry with enhanced UAC handling enabled by default
        CommandResult result = CommandRegistry.executeCommand(commandKey);
        if (result == null) {
            operation.setRemarks("Unknown info_type: " + infoType);
            return false;
        }

        // Check if UAC was detected and handled
        if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
            boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
            if (uacDetected) {
                LOGGER.info("UAC dialog was detected and handled while retrieving machine spec: " + infoType);
            }
        }

        if (result.isSuccess()) {
            String output = result.getTrimmedOutput();
            operation.setRemarks("output: " + output);
            LOGGER.info("Retrieved machine spec for " + infoType + ": " + output);
            if (operation.hasNote()) {
                saveNote(operation, output);
            }
            return true;
        } else {
            operation.setRemarks("Error retrieving " + infoType + ": " + result.getOutput());
            return false;
        }
    }

    /**
     * Map user-friendly info types to command registry keys
     */
    private static String mapInfoTypeToCommandKey(String infoType) {
        if (infoType == null) return "";

        switch (infoType.trim().toLowerCase()) {
            case "computer name": return "computer_name";
            case "domain name": return "domain_name";
            case "fqdn": return "fqdn";
            case "os name": return "os_name";
            case "os version": return "os_version";
            case "architecture": return "architecture";
            case "time zone": return "time_zone";
            case "logged user": return "logged_user";
            case "mac address": return "mac_address";
            case "service tag": return "service_tag";
            default: return infoType.toLowerCase().replace(" ", "_");
        }
    }

    private static boolean changeTimeZone(Operation operation) {
        String timeZoneId = operation.getParameter("timezone_id");
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            LOGGER.warning("No timezone ID specified for change_timezone operation.");
            operation.setRemarks("No timezone ID specified.");
            return false;
        }

        Map<String, String> params = new HashMap<>();
        params.put("timezone_id", timeZoneId);

        CommandResult result = CommandRegistry.executeCommand("change_timezone", params);
        if (result == null) {
            operation.setRemarks("Change timezone command not found in registry");
            return false;
        }

        // Check if UAC was detected and handled
        if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
            boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
            if (uacDetected) {
                LOGGER.info("UAC dialog was detected and handled during timezone change operation");
            }
        }

        operation.setRemarks(result.isSuccess() ?
            "Timezone changed to " + timeZoneId :
            "Failed to change timezone: " + result.getOutput());

        return result.isSuccess();
    }

    private static boolean renameMachine(Operation operation) {
        String newName = operation.getParameter("new_name");
        if (newName == null || newName.isEmpty()) {
            LOGGER.warning("No new name specified for rename operation.");
            operation.setRemarks("No new name specified for rename operation.");
            return false;
        }

        Map<String, String> params = new HashMap<>();
        params.put("new_name", newName);

        // Use command with UAC handling as renaming typically requires elevation
        CommandResult result = CommandRegistry.executeCommand(operation.getParameter("action"), params);

        // Check if UAC was detected and handled
        if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
            boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
            if (uacDetected) {
                LOGGER.info("UAC dialog was detected and handled during machine rename operation");
            }
        }

        operation.setRemarks(result.isSuccess() ?
            "Machine renamed to " + newName :
            "Failed to rename machine: " + result.getOutput());

        return result.isSuccess();
    }

    private static boolean connectNetwork(Operation operation) {
        String adapter = operation.getParameter("adapter");
        if (adapter == null || adapter.isEmpty()) {
            LOGGER.warning("No adapter specified for connect_network operation.");
            operation.setRemarks("No adapter specified for connect_network operation.");
            return false;
        }

        Map<String, String> params = new HashMap<>();
        params.put("adapter", adapter);

        CommandResult result = CommandRegistry.executeCommand(operation.getParameter("action"), params);

        operation.setRemarks(result.isSuccess() ?
            "Network connected for adapter " + adapter :
            "Failed to connect network: " + result.getOutput());

        return result.isSuccess();
    }

    private static boolean disconnectNetwork(Operation operation) {
        String adapter = operation.getParameter("adapter");
        if (adapter == null || adapter.isEmpty()) {
            LOGGER.warning("No adapter specified for disconnect_network operation.");
            operation.setRemarks("No adapter specified for disconnect_network operation.");
            return false;
        }

        Map<String, String> params = new HashMap<>();
        params.put("adapter", adapter);

        CommandResult result = CommandRegistry.executeCommand(operation.getParameter("action"), params);

        operation.setRemarks(result.isSuccess() ?
            "Network disconnected for adapter " + adapter :
            "Failed to disconnect network: " + result.getOutput());

        return result.isSuccess();
    }

    private static boolean changeDomain(Operation operation) {
        String domain = operation.getParameter("domain");
        String user = operation.getParameter("user");
        String password = operation.getParameter("password");
        boolean doRestart = Boolean.parseBoolean(operation.getParameter("do_restart"));
        String workgroup = operation.getParameter("workgroup");

        if ((domain == null || domain.isEmpty()) && (workgroup == null || workgroup.isEmpty())) {
            LOGGER.warning("Neither domain nor workgroup specified for change_domain operation.");
            operation.setRemarks("Neither domain nor workgroup specified for change_domain operation.");
            return false;
        }

        if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
            LOGGER.warning("No user credentials specified for change_domain operation.");
            operation.setRemarks("No user credentials specified for change_domain operation.");
            return false;
        }

        // Construct the appropriate PowerShell command based on whether we're joining a domain or workgroup
        String command;
        Map<String, String> params = new HashMap<>();

        if (domain != null && !domain.isEmpty()) {
            command = "Add-Computer -DomainName '${domain}' -Credential (New-Object System.Management.Automation.PSCredential('${user}',(ConvertTo-SecureString '${password}' -AsPlainText -Force))) -Force${restart}";
            params.put("domain", domain);
            params.put("user", user);
            params.put("password", password);
            params.put("restart", doRestart ? " -Restart" : "");
        } else {
            if (workgroup == null || workgroup.isEmpty()) {
                workgroup = "WORKGROUP";
            }

            command = "$cred = New-Object System.Management.Automation.PSCredential('${user}',(ConvertTo-SecureString '${password}' -AsPlainText -Force)); " +
                    "Remove-Computer -UnjoinDomainCredential $cred -PassThru -Force${restart}; " +
                    "Add-Computer -WorkGroupName '${workgroup}'${restart}";

            params.put("user", user);
            params.put("password", password);
            params.put("workgroup", workgroup);
            params.put("restart", doRestart ? " -Restart" : "");
        }

        CommandDefinition cmdDef = new CommandDefinition(
            "temp_change_domain",
            command,
            CommandType.POWERSHELL,
            60,
            null,
            true  // Domain changes require elevation, so enable UAC handling
        );

        CommandResult result = cmdDef.execute(params);

        // Log command with password masked
        String safeCommand = command.replace("${password}", "********");
        LOGGER.info("Executed domain change command: " + safeCommand);

        // Check if UAC was detected and handled
        if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
            boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
            if (uacDetected) {
                LOGGER.info("UAC dialog was detected and handled during domain change operation");
            }
        }

        if (result.isSuccess()) {
            operation.setRemarks("Domain changed to " + (domain != null ? domain : workgroup));
        } else {
            operation.setRemarks("Failed to change domain: " + result.getOutput());
        }

        return result.isSuccess();
    }

    private static boolean changeDateTime(Operation operation) {
        String date = operation.getParameter("date"); // Format: dd-MM-yyyy
        String time = operation.getParameter("time"); // Format: HH:mm:ss

        if (date == null || time == null) {
            LOGGER.warning("Missing date/time for change_datetime operation.");
            operation.setRemarks("Missing date/time for change_datetime operation.");
            return false;
        }

        try {
            // Parse the date components
            String[] dateParts = date.split("-");
            if (dateParts.length != 3) {
                LOGGER.warning("Invalid date format. Expected dd-MM-yyyy, got: " + date);
                operation.setRemarks("Invalid date format. Expected dd-MM-yyyy");
                return false;
            }

            String day = dateParts[0];
            String month = dateParts[1];
            String year = dateParts[2];

            // Format for PowerShell: MM/dd/yyyy HH:mm:ss
            String dateTimeString = month + "/" + day + "/" + year + " " + time;

            Map<String, String> params = new HashMap<>();
            params.put("datetime", dateTimeString);

            String command = "Set-Date '${datetime}'";
            CommandDefinition cmdDef = new CommandDefinition(
                "change_datetime",
                command,
                CommandType.POWERSHELL,
                20,
                null,
                true  // Date/time changes require elevation, so enable UAC handling
            );

            CommandResult result = cmdDef.execute(params);

            // Check if UAC was detected and handled
            if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
                boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
                if (uacDetected) {
                    LOGGER.info("UAC dialog was detected and handled during date/time change operation");
                }
            }

            if (result.isSuccess()) {
                operation.setRemarks("Date and time changed to " + dateTimeString);
            } else {
                operation.setRemarks("Failed to change date/time: " + result.getOutput());
            }

            return result.isSuccess();
        } catch (Exception e) {
            LOGGER.severe("Error changing date/time: " + e.getMessage());
            operation.setRemarks("Error changing date/time: " + e.getMessage());
            return false;
        }
    }

    private static boolean changeDefaultBrowser(Operation operation) {
        String browserPath = operation.getParameter("browser_path");
        if (browserPath == null || browserPath.isEmpty()) {
            LOGGER.warning("No browser path specified for change_browser operation.");
            operation.setRemarks("No browser path specified.");
            return false;
        }

        Map<String, String> params = new HashMap<>();
        params.put("browser_path", browserPath);

        String command = "reg add \"HKEY_CLASSES_ROOT\\http\\shell\\open\\command\" /ve /d \"${browser_path} %1\" /f";
        CommandDefinition cmdDef = new CommandDefinition(
            "change_browser",
            command,
            CommandType.CMD,
            20,
            null,
            true  // Registry changes require elevation, so enable UAC handling
        );

        CommandResult result = cmdDef.execute(params);

        // Check if UAC was detected and handled
        if (result instanceof EnhancedUacHandler.UacAwareCommandResult) {
            boolean uacDetected = ((EnhancedUacHandler.UacAwareCommandResult)result).wasUacDetected();
            if (uacDetected) {
                LOGGER.info("UAC dialog was detected and handled during browser change operation");
            }
        }

        if (result.isSuccess()) {
            operation.setRemarks("Default browser changed to " + browserPath);
        } else {
            operation.setRemarks("Failed to change default browser: " + result.getOutput());
        }

        return result.isSuccess();
    }
}
