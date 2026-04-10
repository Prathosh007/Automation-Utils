package com.me.testcases;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

/**
 * Handler for service management operations like start, stop, restart, and status
 */
public class ServiceManagementHandler {
    private static final Logger LOGGER = LogManager.getLogger(ServiceManagementHandler.class, LogManager.LOG_TYPE.FW);
    
    /**
     * Execute a service operation based on action parameter
     * 
     * @param op The operation with service parameters
     * @return true if the operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.severe("Operation is null"); //NO I18N
            return false;
        }

        op = ResolveOperationParameters.resolveOperationParameters(op);
        
        String serviceName = op.getParameter("service_name"); //NO I18N
        String action = op.getParameter("action"); //NO I18N
        
        if (serviceName == null || serviceName.isEmpty()) {
            LOGGER.severe("service_name parameter is required for service_actions operation"); //NO I18N
            op.setRemarks("Error: service_name parameter is required"); //NO I18N
            return false;
        }
        
        if (action == null || action.isEmpty()) {
            LOGGER.severe("action parameter is required for service_actions operation"); //NO I18N
            op.setRemarks("Error: action parameter is required"); //NO I18N
            return false;
        }
        
        LOGGER.info("Executing service operation: " + action + " on service: " + serviceName); //NO I18N
        
        // Execute the appropriate action
        switch (action.toLowerCase()) {
            case "start": //NO I18N
                return startService(serviceName, op);
                
            case "stop": //NO I18N
                return stopService(serviceName, op);
                
            case "restart": //NO I18N
                return restartService(serviceName, op);
                
            case "status": //NO I18N
                return checkServiceStatus(serviceName, op);

            case "set_startup": //NO I18N
                return setStartupType(serviceName, op);

//            case "set_logon": //NO I18N
//                return setLogonAccount(serviceName, op);

//            case "config": //NO I18N
//                return getServiceConfig(serviceName, op);

            case "set_description": //NO I18N
                return setServiceDescription(serviceName, op);

            case "get_description": //NO I18N
                return getServiceDescription(serviceName, op);

//            case "failure": //NO I18N
//                return setFailureActions(serviceName, op);

            case "get_startup": //NO I18N
                return getStartupType(serviceName, op);

            case "get_logon": //NO I18N
                return getLogonAccount(serviceName, op);

            default:
                LOGGER.severe("Unknown service action: " + action); //NO I18N
                op.setRemarks("Error: Unknown service action: " + action); //NO I18N
                return false;
        }
    }

    /**
     * Get service description
     *
     * @param serviceName The name of the service
     * @param op The operation for storing remarks
     * @return true if successful
     */
    private static boolean getServiceDescription(String serviceName, Operation op) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "qdescription", serviceName); //NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            String description = null;
            boolean descriptionFound = false;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N

                // Look for DESCRIPTION line
                if (line.trim().startsWith("DESCRIPTION")) { //NO I18N
                    descriptionFound = true;
                    String[] parts = line.split(":", 2); //NO I18N
                    if (parts.length > 1) {
                        description = parts[1].trim();
                    }
                } else if (descriptionFound && description == null) {
                    // Description might be on next line if empty on first line
                    description = line.trim();
                }
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            // Handle empty description
            if (description != null && description.isEmpty()) {
                description = "(No description)"; //NO I18N
            }

            StringBuilder remarks = new StringBuilder();
            remarks.append("Service description query: ").append(serviceName).append("\n"); //NO I18N
            remarks.append("Description: ").append(description != null ? description : "UNKNOWN").append("\n\n"); //NO I18N
            remarks.append("Full output:\n").append(outputStr); //NO I18N

            op.setRemarks(remarks.toString());

            if (description != null) {
                // Save the description value using DataBaseOperationHandler if note parameter is provided
                if (op.hasNote()) {
                    saveNote(op, description); //NO I18N
                }
                LOGGER.info("Retrieved description for service: " + serviceName + " = " + description); //NO I18N
                return true;
            } else {
                LOGGER.warning("Failed to retrieve description for service: " + serviceName); //NO I18N
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting description: " + serviceName, e); //NO I18N
            op.setRemarks("Error getting description: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Get service startup type
     *
     * @param serviceName The name of the service
     * @param op The operation for storing remarks
     * @return true if successful
     */
    private static boolean getStartupType(String serviceName, Operation op) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "qc", serviceName); //NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            String startupType = null;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N

                // Look for START_TYPE line
                if (line.trim().startsWith("START_TYPE")) { //NO I18N
                    String[] parts = line.split(":"); //NO I18N
                    if (parts.length > 1) {
                        String typeInfo = parts[1].trim();
                        // Extract the startup type (e.g., "2   AUTO_START" -> "AUTO_START")
                        if (typeInfo.contains("AUTO_START")) { //NO I18N
                            startupType = typeInfo.contains("DELAYED") ? "DELAYED-AUTO" : "AUTO"; //NO I18N
                        } else if (typeInfo.contains("DEMAND_START")) { //NO I18N
                            startupType = "DEMAND"; //NO I18N
                        } else if (typeInfo.contains("DISABLED")) { //NO I18N
                            startupType = "DISABLED"; //NO I18N
                        } else if (typeInfo.contains("BOOT_START")) { //NO I18N
                            startupType = "BOOT"; //NO I18N
                        } else if (typeInfo.contains("SYSTEM_START")) { //NO I18N
                            startupType = "SYSTEM"; //NO I18N
                        }
                    }
                }
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            StringBuilder remarks = new StringBuilder();
            remarks.append("Service startup type query: ").append(serviceName).append("\n"); //NO I18N
            remarks.append("Startup Type: ").append(startupType != null ? startupType : "UNKNOWN").append("\n\n"); //NO I18N
            remarks.append("Full output:\n").append(outputStr); //NO I18N

            op.setRemarks(remarks.toString());

            if (startupType != null) {
                // Save the startup type value
                if (op.hasNote()) {
                    saveNote(op, startupType); //NO I18N
                }
                return true;
            } else {
                LOGGER.warning("Failed to retrieve startup type for service: " + serviceName); //NO I18N
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting startup type: " + serviceName, e); //NO I18N
            op.setRemarks("Error getting startup type: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Get service logon account
     *
     * @param serviceName The name of the service
     * @param op The operation for storing remarks
     * @return true if successful
     */
    private static boolean getLogonAccount(String serviceName, Operation op) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "qc", serviceName); //NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            String logonAccount = null;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N

                // Look for SERVICE_START_NAME line
                if (line.trim().startsWith("SERVICE_START_NAME")) { //NO I18N
                    String[] parts = line.split(":", 2); //NO I18N
                    if (parts.length > 1) {
                        logonAccount = parts[1].trim();
                    }
                }
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            StringBuilder remarks = new StringBuilder();
            remarks.append("Service logon account query: ").append(serviceName).append("\n"); //NO I18N
            remarks.append("Logon Account: ").append(logonAccount != null ? logonAccount : "UNKNOWN").append("\n\n"); //NO I18N
            remarks.append("Full output:\n").append(outputStr); //NO I18N

            op.setRemarks(remarks.toString());

            if (logonAccount != null) {
                // Save the logon account value
                if (op.hasNote()) {
                    saveNote(op, logonAccount); //NO I18N
                }
                LOGGER.info("Retrieved logon account for service: " + serviceName + " = " + logonAccount); //NO I18N
                return true;
            } else {
                LOGGER.warning("Failed to retrieve logon account for service: " + serviceName); //NO I18N
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting logon account: " + serviceName, e); //NO I18N
            op.setRemarks("Error getting logon account: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Set service startup type
     *
     * @param serviceName The name of the service
     * @param op The operation with startup_type parameter (auto, demand, disabled, delayed-auto)
     * @return true if successful
     */
    private static boolean setStartupType(String serviceName, Operation op) {
        String startupType = op.getParameter("startup_type"); //NO I18N

        if (startupType == null || startupType.isEmpty()) {
            LOGGER.severe("startup_type parameter is required"); //NO I18N
            op.setRemarks("Error: startup_type parameter is required (auto, demand, disabled, delayed-auto)"); //NO I18N
            return false;
        }
        if (startupType.equalsIgnoreCase("Manual")){
            startupType = "demand"; //NO I18N
        } else if (startupType.equalsIgnoreCase("Automatic")){
            startupType = "auto"; //NO I18N
        } else if (startupType.equalsIgnoreCase("Disabled")){
            startupType = "disabled"; //NO I18N
        } else if (startupType.equalsIgnoreCase("Delayed-Auto")){
            startupType = "delayed-auto"; //NO I18N
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "config", serviceName, "start=", startupType); //NO I18N
            LOGGER.info("executing command "+pb.command());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            op.setRemarks("Service startup type change: " + serviceName + "\nStartup Type: " + startupType + "\n\n" + outputStr); //NO I18N

            boolean success = exitValue == 0 && outputStr.contains("SUCCESS"); //NO I18N

            if (success) {
                LOGGER.info("Service startup type changed successfully: " + serviceName + " to " + startupType); //NO I18N
            } else {
                LOGGER.warning("Failed to change startup type: " + serviceName + "\nOutput: " + outputStr); //NO I18N
            }

            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error changing startup type: " + serviceName, e); //NO I18N
            op.setRemarks("Error changing startup type: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Set service logon account
     *
     * @param serviceName The name of the service
     * @param op The operation with logon_account and logon_password parameters
     * @return true if successful
     */
    private static boolean setLogonAccount(String serviceName, Operation op) {
        String logonAccount = op.getParameter("logon_account"); //NO I18N
        String logonPassword = op.getParameter("logon_password"); //NO I18N

        if (logonAccount == null || logonAccount.isEmpty()) {
            LOGGER.severe("logon_account parameter is required"); //NO I18N
            op.setRemarks("Error: logon_account parameter is required"); //NO I18N
            return false;
        }

        try {
            ProcessBuilder pb;
            if (logonPassword != null && !logonPassword.isEmpty()) {
                pb = new ProcessBuilder("sc", "config", serviceName, "obj=", logonAccount, "password=", logonPassword); //NO I18N
            } else {
                pb = new ProcessBuilder("sc", "config", serviceName, "obj=", logonAccount); //NO I18N
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            op.setRemarks("Service logon account change: " + serviceName + "\nLogon Account: " + logonAccount + "\n\n" + outputStr); //NO I18N

            boolean success = exitValue == 0 && outputStr.contains("SUCCESS"); //NO I18N

            if (success) {
                LOGGER.info("Service logon account changed successfully: " + serviceName); //NO I18N
            } else {
                LOGGER.warning("Failed to change logon account: " + serviceName + "\nOutput: " + outputStr); //NO I18N
            }

            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error changing logon account: " + serviceName, e); //NO I18N
            op.setRemarks("Error changing logon account: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Get detailed service configuration
     *
     * @param serviceName The name of the service
     * @param op The operation for storing remarks
     * @return true if successful
     */
    private static boolean getServiceConfig(String serviceName, Operation op) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "qc", serviceName); //NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            op.setRemarks("Service configuration: " + serviceName + "\n\n" + outputStr); //NO I18N

            return exitValue == 0;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting service config: " + serviceName, e); //NO I18N
            op.setRemarks("Error getting service config: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Set service description
     *
     * @param serviceName The name of the service
     * @param op The operation with description parameter
     * @return true if successful
     */
    private static boolean setServiceDescription(String serviceName, Operation op) {
        String description = op.getParameter("description"); //NO I18N

        if (description == null) {
            description = ""; //NO I18N
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "description", serviceName, description); //NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            op.setRemarks("Service description update: " + serviceName + "\n\n" + outputStr); //NO I18N

            boolean success = exitValue == 0 && outputStr.contains("SUCCESS"); //NO I18N

            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting service description: " + serviceName, e); //NO I18N
            op.setRemarks("Error setting service description: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Set service failure actions
     *
     * @param serviceName The name of the service
     * @param op The operation with failure parameters (reset, actions)
     * @return true if successful
     */
    private static boolean setFailureActions(String serviceName, Operation op) {
        String reset = op.getParameter("reset"); //NO I18N
        String actions = op.getParameter("actions"); //NO I18N

        if (reset == null) reset = "86400"; // Default 24 hours //NO I18N
        if (actions == null) actions = "restart/60000/restart/60000/restart/60000"; // Restart after 1 min //NO I18N

        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "failure", serviceName, "reset=", reset, "actions=", actions); //NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n"); //NO I18N
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            op.setRemarks("Service failure actions update: " + serviceName + "\n\n" + outputStr); //NO I18N

            boolean success = exitValue == 0 && outputStr.contains("SUCCESS"); //NO I18N

            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting failure actions: " + serviceName, e); //NO I18N
            op.setRemarks("Error setting failure actions: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }


    
    /**
     * Start a service
     * 
     * @param serviceName The name of the service to start
     * @param op The operation for storing remarks
     * @return true if service was started successfully, false otherwise
     */
    private static boolean startService(String serviceName, Operation op) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "start", serviceName);//NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");//NO I18N
            }
            
            int exitValue = process.waitFor();
            String outputStr = output.toString();
            
            // Store remarks with command output
            op.setRemarks("Service start operation: " + serviceName + "\n\n" + outputStr); //NO I18N
            
            // Check output for success/failure indicators
            boolean success = exitValue == 0 || 
                             outputStr.contains("START_PENDING") || //NO I18N
                             outputStr.contains("RUNNING") || //NO I18N
                             !outputStr.contains("FAILED"); //NO I18N
                             
            if (!success) {
                LOGGER.warning("Failed to start service: " + serviceName + "\nOutput: " + outputStr); //NO I18N
            } else {
                LOGGER.info("Service started successfully: " + serviceName); //NO I18N
            }
            
            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting service: " + serviceName, e); //NO I18N
            op.setRemarks("Error starting service: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }
    
    /**
     * Stop a service
     * 
     * @param serviceName The name of the service to stop
     * @param op The operation for storing remarks
     * @return true if service was stopped successfully, false otherwise
     */
    public static boolean stopService(String serviceName, Operation op) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "stop", serviceName);//NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");//NO I18N
            }
            
            int exitValue = process.waitFor();
            String outputStr = output.toString();
            
            // Store remarks with command output
//            op.setRemarks("Service stop operation: " + serviceName + "\n\n" + outputStr); //NO I18N
            
            // Check output for success/failure indicators
            boolean success = exitValue == 0 || 
                             outputStr.contains("STOP_PENDING") || //NO I18N
                             outputStr.contains("STOPPED") || //NO I18N
                             !outputStr.contains("FAILED"); //NO I18N
                             
            if (!success) {
                LOGGER.warning("Failed to stop service: " + serviceName + "\nOutput: " + outputStr); //NO I18N
            } else {
                LOGGER.info("Service stopped successfully: " + serviceName); //NO I18N
            }
            
            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error stopping service: " + serviceName, e); //NO I18N
            op.setRemarks("Error stopping service: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }
    
    /**
     * Restart a service (stop and then start)
     * 
     * @param serviceName The name of the service to restart
     * @param op The operation for storing remarks
     * @return true if service was restarted successfully, false otherwise
     */
    private static boolean restartService(String serviceName, Operation op) {
        StringBuilder remarks = new StringBuilder();
        remarks.append("Service restart operation: ").append(serviceName).append("\n\n"); //NO I18N
        
        // First stop the service
        boolean stopSuccess = stopService(serviceName, op);
        remarks.append("Stop service result: ").append(stopSuccess ? "SUCCESS" : "FAILED").append("\n"); //NO I18N
        if (op.getRemarks() != null) {
            remarks.append(op.getRemarks()).append("\n\n");//NO I18N
        }
        
        // If stop failed, we might still try to start it
        // Wait a bit before starting
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Now start the service
        boolean startSuccess = startService(serviceName, op);
        remarks.append("Start service result: ").append(startSuccess ? "SUCCESS" : "FAILED").append("\n"); //NO I18N
        if (op.getRemarks() != null) {
            remarks.append(op.getRemarks());
        }
        
        // Set consolidated remarks
        op.setRemarks(remarks.toString());
        
        // Consider restart successful if start succeeds, even if stop had issues
        return startSuccess;
    }

    /**
     * Wait for service to reach a stable state (RUNNING or STOPPED)
     *
     * @param serviceName The name of the service
     * @param maxWaitTimeMs Maximum time to wait in milliseconds
     * @return The final stable state, or null if timeout
     */
    private static String waitForStableState(String serviceName, long maxWaitTimeMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + maxWaitTimeMs;

        while (System.currentTimeMillis() < endTime) {
            try {
                ProcessBuilder pb = new ProcessBuilder("sc", "query", serviceName); //NO I18N
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                String state = null;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("STATE")) { //NO I18N
                        state = line.trim();
                        break;
                    }
                }

                process.waitFor();

                if (state != null) {
                    // Check if in stable state
                    if (state.contains("RUNNING") || state.contains("STOPPED")) { //NO I18N
                        LOGGER.info("Service reached stable state: " + state); //NO I18N
                        return state;
                    }

                    // Still in transitional state
                    LOGGER.info("Service in transitional state: " + state + ", waiting..."); //NO I18N
                }

                // Wait before checking again
                Thread.sleep(500);

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error while waiting for stable state: " + serviceName, e); //NO I18N
                break;
            }
        }

        LOGGER.warning("Timeout waiting for service to reach stable state: " + serviceName); //NO I18N
        return null;
    }

    /**
     * Check the status of a service
     *
     * @param serviceName The name of the service to check
     * @param op The operation for storing remarks
     * @return true if service status check was successful, false otherwise
     */
    private static boolean checkServiceStatus(String serviceName, Operation op) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "query", serviceName);//NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            String state = null;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");//NO I18N

                // Look for the SERVICE_STATE line
                if (line.trim().startsWith("STATE")) { //NO I18N
                    state = line.trim();
                }
            }

            int exitValue = process.waitFor();
            String outputStr = output.toString();

            // Determine service state from output
            String currentStatus = "UNKNOWN"; //NO I18N
            boolean isRunning = false;
            boolean isTransitional = false;

            if (state != null) {
                if (state.contains("RUNNING")) { //NO I18N
                    currentStatus = "RUNNING"; //NO I18N
                    isRunning = true;
                } else if (state.contains("STOPPED")) { //NO I18N
                    currentStatus = "STOPPED"; //NO I18N
                } else if (state.contains("START_PENDING")) { //NO I18N
                    currentStatus = "START_PENDING"; //NO I18N
                    isTransitional = true;

//                    // Wait for stable state if requested
//                    String waitParam = op.getParameter("wait_for_stable"); //NO I18N
//                    if ("true".equalsIgnoreCase(waitParam)) { //NO I18N
//                        String stableState = waitForStableState(serviceName, 30000); // 30 seconds timeout
//                        if (stableState != null) {
//                            currentStatus = stableState.contains("RUNNING") ? "RUNNING" : "STOPPED"; //NO I18N
//                            isRunning = stableState.contains("RUNNING"); //NO I18N
//                            isTransitional = false;
//                        }
//                    }
                } else if (state.contains("STOP_PENDING")) { //NO I18N
                    currentStatus = "STOP_PENDING"; //NO I18N
                    isTransitional = true;

                    // Wait for stable state if requested
//                    String waitParam = op.getParameter("wait_for_stable"); //NO I18N
//                    if ("true".equalsIgnoreCase(waitParam)) { //NO I18N
//                        String stableState = waitForStableState(serviceName, 30000);
//                        if (stableState != null) {
//                            currentStatus = stableState.contains("RUNNING") ? "RUNNING" : "STOPPED"; //NO I18N
//                            isRunning = stableState.contains("RUNNING"); //NO I18N
//                            isTransitional = false;
//                        }
//                    }
                } else if (state.contains("CONTINUE_PENDING") || state.contains("PAUSE_PENDING")) { //NO I18N
                    currentStatus = "TRANSITIONAL"; //NO I18N
                    isTransitional = true;
                }
            }

            // Check if we need to verify a specific state
            String expectedState = op.getParameter("expect"); //NO I18N
            boolean stateMatchesExpectation = true;

            if (expectedState != null) {
                if ("serviceRunning".equalsIgnoreCase(expectedState)) { //NO I18N
                    stateMatchesExpectation = isRunning && !isTransitional;
                } else if ("serviceStopped".equalsIgnoreCase(expectedState)) { //NO I18N
                    stateMatchesExpectation = !isRunning && !isTransitional;
                }
            }

            // Prepare remarks
            StringBuilder remarks = new StringBuilder();
            remarks.append("Service status check: ").append(serviceName).append("\n"); //NO I18N
            remarks.append("Current status: ").append(currentStatus); //NO I18N

            if (isTransitional) {
                remarks.append(" (TRANSITIONAL STATE)"); //NO I18N
            }

            if (expectedState != null) {
                remarks.append("\nExpected state: ").append(expectedState); //NO I18N
                remarks.append("\nState matches expectation: ").append(stateMatchesExpectation); //NO I18N
            }

            remarks.append("\n\nFull output:\n").append(outputStr); //NO I18N
            op.setRemarks(remarks.toString());

            if (op.hasNote()) {
                saveNote(op, currentStatus); //NO I18N
            }

            // Operation is successful if the exit value is 0 (service exists) and
            // the state matches expectation if one was specified
            boolean success = (exitValue == 0) && stateMatchesExpectation;

            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking service status: " + serviceName, e); //NO I18N
            op.setRemarks("Error checking service status: " + serviceName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    
    /**
     * Utility method to check if a service is running
     */
    public static boolean isServiceRunning(String serviceName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "query", serviceName);//NO I18N
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            String state = null;
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");//NO I18N
                
                // Look for the SERVICE_STATE line
                if (line.trim().startsWith("STATE")) {
                    state = line.trim();
                }
            }
            
            int exitValue = process.waitFor();
            
            // Service doesn't exist
            if (exitValue != 0) {
                return false;
            }
            
            // Determine service state from output
            if (state != null) {
                return state.contains("RUNNING"); //NO I18N
            }
            
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking service status: " + serviceName, e); //NO I18N
            return false;
        }
    }
    
    /**
     * Main method to test the service management handler directly
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            ConsoleOut.println("Usage: ServiceManagementHandler <action> <service_name>"); //NO I18N
            ConsoleOut.println("  action: start, stop, restart, status"); //NO I18N
            return;
        }
        
        String action = args[0];
        String serviceName = args[1];
        
        Operation op = new Operation("service_actions"); //NO I18N
        op.setParameter("service_name", serviceName); //NO I18N
        op.setParameter("action", action); //NO I18N
        
        boolean result = executeOperation(op);
        
        ConsoleOut.println("Operation result: " + (result ? "SUCCESS" : "FAILED")); //NO I18N
        ConsoleOut.println("\nRemarks:"); //NO I18N
        ConsoleOut.println(op.getRemarks());
    }
}

