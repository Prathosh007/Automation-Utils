package com.me;

import com.me.testcases.*;
import com.me.testcases.AgentCommunicationOperation;
import com.me.testcases.fileOperation.*;
import com.me.util.LogManager;
import com.me.testcases.MachineOperation;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.util.CommonUtill.changeServiceNameInTestSetupConf;

/**
 * Factory for creating and dispatching operations to appropriate handlers
 */
public class OperationHandlerFactory {
    private static final Logger LOGGER = LogManager.getLogger(OperationHandlerFactory.class, LogManager.LOG_TYPE.FW);


    /**
     * Execute an operation using the appropriate handler based on operation type
     * with retry functionality
     *
     * @param operation The operation to execute
     * @return true if the operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation operation) {
        if (operation == null) {
            LOGGER.severe("Cannot execute null operation");
            return false;
        }

        String operationType = operation.getOperationType();
        if (operationType == null || operationType.isEmpty()) {
            LOGGER.severe("Operation type is null or empty");
            return false;
        }

        // Extract max wait time and check interval parameters
        long maxWaitTime = getParameterAsLong(operation, "max_wait_time");
        long checkInterval = getParameterAsLong(operation, "check_interval");

        LOGGER.info("Executing operation: " + operationType + " with max wait time: " + maxWaitTime +
                "s and check interval: " + checkInterval + "s");

        changeServiceNameInTestSetupConf(operation);

        // Setup for retry loop
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (maxWaitTime * 1000);
        boolean success = false;
        Exception lastException = null;
        boolean isWaitForCondition = false;
        StringBuilder remarkBuilder = new StringBuilder();

        if (!(maxWaitTime == 0) && !(checkInterval == 0)) {
            isWaitForCondition = true;
            remarkBuilder.append("Operation: ").append(operationType).append("\n");
            remarkBuilder.append("Max wait time: ").append(maxWaitTime).append(" seconds\n");
            remarkBuilder.append("Check interval: ").append(checkInterval).append(" seconds\n\n");
            LOGGER.info("Starting operation: " + operationType + " with max wait time: " + maxWaitTime +
                    " seconds and check interval: " + checkInterval + " seconds");
        }

        // Use do-while loop to ensure switch case is executed at least once
        do {
            try {
                // Dispatch to appropriate handler based on operation type
                switch (operationType.toLowerCase()) {
                    // File operations
                    case "exe_install":
                        success = ExeInstall.executeOperation(operation);
                        break;
                    case "wait_for_condition":
                        success = WaitForConditionHandler.executeOperation(operation);
                        break;
                    case "uninstall":
                        success = UnInstallProduct.executeOperation(operation);
                        break;
                    case "file_folder_operation":
                        success = FileFolderHandler.executeOperation(operation);
                        break;
                    case "download_file":
                        success = UnAuthFileDownload.executeOperation(operation);
                        break;
                    case "task_manager":
                        success = TaskManagerHandler.executeOperation(operation);
                        break;
                    case "api_case":
                        success = ApiCaseHandler.executeOperation(operation);
                        break;
                    case "run_bat":
                        success = BatFileExecutor.executeOperation(operation);
                        break;
                    case "run_command":
                        success = CommandExecutor.executeOperation(operation);
                        break;
                    case "file_folder_modification":
                        success = FileEditHandler.executeOperation(operation);
                        break;
                    case "service_actions":
                        success = ServiceManagementHandler.executeOperation(operation);
                        break;
                    case "ppm_upgrade":
                        success = InstallPPM.executeOperation(operation);
                        break;
                    case "revert_ppm":
                        success = RevertPPM.executeOperation(operation);
                        break;
                    case "mssql_migration":
                        success = MSSQLMigration.executeOperation(operation);
                        break;
                    case "jar_operation":
                        success = JarFileHandler.executeOperation(operation);
                        break;
                    case "bat_operation":
                        success = BatFileExecutor.executeOperation(operation);
                        break;
                    case "db_operation":
                        success = DataBaseOperationHandler.executeOperation(operation);
                        break;
                    case "zip_operation":
                        success = ArchiveHandler.executeOperation(operation);
                        break;
                    case "machine_operation":
                        success = MachineOperation.executeOperation(operation);
                        break;
                    case "certificate_operation":
                        success = GetValueFromCertificatefile.executeOperation(operation);
                        break;
//                    case "base64_encode_decode_operation":
//                        success = Base64EncodeDecoder.executeOperation(operation);
//                        break;
                    case "native_gui_operation":
                        success = NativeGUIOperationHandler.executeOperation(operation);
                        break;
                    case "registry_operation":
                        success = RegistryOperation.executeOperation(operation);
                        break;
                    case "communication_operation":
                        success = AgentCommunicationOperation.executeOperation(operation);
                        break;
                    case "temp_workaround_operation":
                        success = TempWorkaroundOperation.executeOperation(operation);
                        break;
                    default:
                        LOGGER.severe("Unknown operation type: " + operationType);
                        return false; // Unrecoverable error, exit immediately
                }

                if (success) {
                    // Operation succeeded, exit the loop
                    long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                    remarkBuilder.append("Operation succeeded after ").append(elapsedTime).append(" seconds\n");
                } else {
                    // Operation failed but we can retry
                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    long remainingSeconds = maxWaitTime - elapsedSeconds;

                    if (remainingSeconds <= 0) {
                        break; // Time's up
                    }

                    LOGGER.info("Operation failed, retrying in " + checkInterval +
                            " seconds... (" + elapsedSeconds + "s elapsed, " +
                            remainingSeconds + "s remaining)");

                    remarkBuilder.append("Attempt failed. Retrying in ").append(checkInterval)
                            .append(" seconds... (").append(elapsedSeconds)
                            .append("s elapsed, ").append(remainingSeconds)
                            .append("s remaining)\n");

                    // Sleep before retry
                    Thread.sleep(checkInterval * 1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Operation interrupted", e);
                remarkBuilder.append("Error: Operation interrupted\n");
                success = false;
                break; // Exit the loop on interrupt
            } catch (Exception e) {
                lastException = e;
                LOGGER.log(Level.SEVERE, "Error executing operation: " + operationType, e);

                // Check if this is a fatal exception that should not be retried
                if (isFatalException(e)) {
                    remarkBuilder.append("Fatal error: ").append(e.getMessage()).append("\n");
                    success = false;
                    break; // Exit the loop on fatal exceptions
                }

                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                long remainingSeconds = maxWaitTime - elapsedSeconds;

                if (remainingSeconds <= 0) {
                    break; // Time's up
                }

                LOGGER.info("Operation error, retrying in " + checkInterval +
                        " seconds... (" + elapsedSeconds + "s elapsed, " +
                        remainingSeconds + "s remaining)");

                remarkBuilder.append("Error: ").append(e.getMessage())
                        .append(". Retrying in ").append(checkInterval)
                        .append(" seconds... (").append(elapsedSeconds)
                        .append("s elapsed, ").append(remainingSeconds)
                        .append("s remaining)\n");

                // Sleep before retry
                try {
                    Thread.sleep(checkInterval * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (System.currentTimeMillis() < endTime && !success);

        // Check final result
        if (!success) {
            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsedTime >= maxWaitTime) {
                if (isWaitForCondition) {
                    remarkBuilder.append("\nTimeout: Operation did not succeed within ")
                            .append(maxWaitTime).append(" seconds\n");
                    LOGGER.warning("Timeout: Operation " + operationType + " did not succeed after " + maxWaitTime + " seconds");
                }
            } else if (lastException != null) {
                remarkBuilder.append("\nOperation failed due to error: ")
                        .append(lastException.getMessage()).append("\n");
            } else {
                remarkBuilder.append("\nOperation failed after multiple attempts\n");
            }
        }

        // Set remarks on the operation
        if (operation.getRemarks() == null || operation.getRemarks().isEmpty()) {
            operation.setRemarks(remarkBuilder.toString());
        } else {
            operation.setRemarks(remarkBuilder.toString() + "\n" + operation.getRemarks());
        }

        return success;
    }

    /**
     * Get a parameter value as a long with default fallback
     */
    private static long getParameterAsLong(Operation op, String paramName) {
        String value = op.getParameter(paramName);
        if (value == null || value.isEmpty()) {
            return 0;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid " + paramName + " value: " + value +
                    ". Using default: " + 0);//NO I18N
            return 0;
        }
    }

    /**
     * Determine if an exception should prevent further retries
     */
    private static boolean isFatalException(Exception e) {
        // Add logic to identify exceptions that should not be retried
        // For example, configuration errors or invalid input errors
        if (e instanceof IllegalArgumentException ||
                e instanceof NullPointerException) {
            return true;
        }
        return false;
    }

    public static String getFileExtension(String fullFileName) {
        if (fullFileName == null){
            throw new NullPointerException("File Name Is Null");
        }
        String fileName = (new File(fullFileName)).getName();
        int dotIndex = fileName.lastIndexOf(46);
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
    }


}
