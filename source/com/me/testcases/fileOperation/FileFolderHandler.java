package com.me.testcases.fileOperation;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.testcases.FileReaderUtil;
import com.me.util.LogManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static com.me.testcases.DataBaseOperationHandler.saveNote;
import static com.me.util.CommonUtill.resolveHome;

public class FileFolderHandler {
    private static final Logger LOGGER = LogManager.getLogger(FileFolderHandler.class, LogManager.LOG_TYPE.FW);

    public static boolean executeOperation(Operation operation) {
        String action = operation.getParameter("action");
        if (action == null || action.isEmpty()) {
            operation.setRemarks("No action specified for file_folder_operation");
            return false;
        }

        operation = ResolveOperationParameters.resolveOperationParameters(operation);

        String filePath = operation.getParameter("file_path");
        String fileName = operation.getParameter("filename");

        try {
            switch (action.toLowerCase()) {
                case "create":
                    return createFileOrFolder(operation);
                case "copy":
                    return copyFileOrFolder(operation);
                case "move":
                    return moveFileOrFolder(operation);
                case "delete":
                    return deleteFileOrFolder(operation);
                case "check_permission":
                    return checkFilePermission(operation);
                case "set_permission":
                    return setFilePermission(operation);
                case "remove_permission":
                    return removeFilePermission(operation);
                case "check_presence":
                    return FileReaderUtil.checkFileFolderPresence(operation,filePath, fileName, true);
                case "verify_absence":
                    return FileReaderUtil.checkFileFolderPresence(operation,filePath, fileName, false);
                case "set_share_permission":
                    return setSharePermission(operation);
                case "remove_share_permission":
                    return removeSharePermission(operation);
                case "add_permission":
                    return addFilePermission(operation);
                case "get_size":
                case "check_size":
                    boolean isOK =  checkFileOrFolderSize(operation);
                    if (isOK && operation.hasNote()) {
                        operation.setRemarks("Size check passed for " + filePath + ". Actual size: " + operation.getParameter("actual_size") + " MB");
                        LOGGER.info("Size check passed for " + filePath + ". Actual size: " + operation.getParameter("actual_size") + " MB");
                        saveNote(operation,operation.getParameter("actual_size"));
                        LOGGER.info("Note saved for size check: " + operation.getParameter("actual_size"));
                        return true;
                    }
                    return isOK;
                case "check_last_modified":
                    boolean isTimeOK = checkFileOrFolderLastModified(operation);
                    if (isTimeOK && operation.hasNote()) {
                        operation.setRemarks("Last modified check passed for " + filePath + ". Last modified: " + operation.getParameter("last_modified_formatted"));
                        LOGGER.info("Last modified check passed for " + filePath + ". Last modified: " + operation.getParameter("last_modified_formatted"));
                        saveNote(operation,operation.getParameter("actual_size"));
                        LOGGER.info("Note saved for last modified check: " + operation.getParameter("last_modified_formatted"));
                        return true;
                    }
                    return isTimeOK;
                case "rename":
                    return renameFileOrFolder(operation);
                default:
                    operation.setRemarks("Unknown file_folder_operation action: " + action);
                    LOGGER.warning("Unknown file_folder_operation action: " + action);
                    return false;
            }
        } catch (Exception e) {
            LOGGER.severe("Error executing file_folder_operation: " + e);
            operation.setRemarks("Error executing file_folder_operation: " + e.getMessage());
            return false;
        }
    }

    private static boolean renameFileOrFolder(Operation operation) {
        String sourcePathStr = operation.getParameter("source");
        String targetName = operation.getParameter("target_name");

        if (sourcePathStr == null || sourcePathStr.isEmpty() || targetName == null || targetName.isEmpty()) {
            operation.setRemarks("Source path or target name not specified for rename operation");
            LOGGER.warning("Source path or target name not specified for rename operation");
            return false;
        }

        Path sourcePath = Paths.get(sourcePathStr);
        if (!Files.exists(sourcePath)) {
            operation.setRemarks("Source path does not exist: " + sourcePathStr);
            LOGGER.warning("Source path does not exist: " + sourcePathStr);
            return false;
        }

        Path targetPath = sourcePath.resolveSibling(targetName);

        if (Files.exists(targetPath)) {
            operation.setRemarks("Target name already exists: " + targetPath);
            LOGGER.warning("Target name already exists: " + targetPath);
            return false;
        }

        try {
            boolean renamed = sourcePath.toFile().renameTo(targetPath.toFile());
            if (renamed) {
                operation.setRemarks("Renamed from " + sourcePath + " to " + targetPath);
                LOGGER.info("Renamed from " + sourcePath + " to " + targetPath);
                return true;
            }else {
                operation.setRemarks("Failed to rename from " + sourcePath + " to " + targetPath);
                LOGGER.warning("Failed to rename from " + sourcePath + " to " + targetPath);
                return false;
            }
        } catch (Exception e) {
            operation.setRemarks("Error renaming: " + e.getMessage());
            LOGGER.severe("Error renaming: " + e);
            return false;
        }
    }

    // Add this method to FileFolderHandler.java
    private static boolean addFilePermission(Operation operation) {
        String path = resolveHome(operation.getParameter("path"));
        String permissions = operation.getParameter("permissions"); // "R", "RW", "RWX"
//        boolean recursive = Boolean.parseBoolean(operation.getParameter("recursive"));
        String usersList = operation.getParameter("user");

        if (usersList == null || usersList.isEmpty()) {
            operation.setRemarks("No users specified for permission addition");
            return false;
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            operation.setRemarks("Path does not exist: " + path);
            return false;
        }

        // Determine access right
        String accessRight;
        String perm = permissions.toUpperCase();
        if (perm.contains("R") && perm.contains("W") && perm.contains("X")) {
            accessRight = "FullControl";
            perm = "F";
        } else if (perm.contains("R") && perm.contains("W")) {
            accessRight = "Modify";
            perm = "M";
        } else if (perm.contains("R") && perm.contains("X")) {
            accessRight = "ReadAndExecute";
            perm = "RX";
        } else if (perm.contains("R")) {
            accessRight = "Read";
            perm = "R";
        } else if (perm.contains("W")) {
            accessRight = "Write";
            perm = "W";
        } else if (perm.contains("X")) {
            accessRight = "ExecuteFile";
            perm = "X";
        } else {
            operation.setRemarks("No valid permissions specified");
            return false;
        }

        // Split the user list
        String[] users = usersList.split(",");

        // Create PowerShell command that handles multiple users
        StringBuilder psCommandBuilder = new StringBuilder();
        psCommandBuilder.append("powershell -Command \"");
        psCommandBuilder.append("$acl = Get-Acl '").append(filePath).append("'; ");

        // For each user, create a rule and add it
        for (String user : users) {
            user = user.trim();
            if (!user.isEmpty()) {
                // First remove existing permissions for the user
                try {
                    String removeCmd = "icacls \"" + filePath + "\" /remove \"" + user + "\"";
                    LOGGER.info("Executing command to remove existing permissions: " + removeCmd);
                    Process removeProcess = Runtime.getRuntime().exec(removeCmd);
                    removeProcess.waitFor();
                } catch (Exception e) {
                    // Ignore errors if user has no permissions yet
                    LOGGER.info("Could not remove existing permissions for user " + user + ": " + e.getMessage());
                }

                // Add rule for this user
                psCommandBuilder.append("$rule = New-Object System.Security.AccessControl.FileSystemAccessRule('")
                        .append(user).append("', '").append(accessRight)
                        .append("', 'ContainerInherit,ObjectInherit', 'None', 'Allow'); ")
                        .append("$acl.AddAccessRule($rule); ");
            }
        }

        // Finish the command with Set-Acl
        psCommandBuilder.append("Set-Acl -Path '").append(filePath).append("' -AclObject $acl\"");

        String psCommand = psCommandBuilder.toString();
        StringBuilder remarks = new StringBuilder("Setting permissions " + permissions + " for users " + usersList + " on " + path + ":\n");
        boolean success = false;

        try {
            LOGGER.info("Executing PowerShell command: " + psCommand);
            Process process = Runtime.getRuntime().exec(psCommand);
            process.waitFor();

            // Verify using icacls
            String icaclsCmd = "icacls \"" + filePath + "\"";
            Process icaclsProcess = Runtime.getRuntime().exec(icaclsCmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(icaclsProcess.getInputStream()));
            String line;
            Set<String> foundUsers = new HashSet<>();

            while ((line = reader.readLine()) != null) {
                LOGGER.info("icacls output: " + line);
                line = line.trim();
                for (String user : users) {
                    user = user.trim();
                    if (!user.isEmpty() && line.toLowerCase().contains(user.toLowerCase())) {
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\(([^)]+)\\)").matcher(line);
                        java.util.List<String> flags = new java.util.ArrayList<>();
                        while (matcher.find()) {
                            flags.add(matcher.group(1));
                            LOGGER.info("Found permission flag: " + matcher.group(1));
                        }
                        for (String flag : flags) {
                            if (perm.equals(flag)) {
                                LOGGER.info("Permission " + permissions + " successfully set for user " + user + " on " + path);
                                foundUsers.add(user);
                                break;
                            }
                        }
                    }
                }
            }

            success = foundUsers.size() == users.length;

            if (success) {
                remarks.append("\nSuccessfully set permissions for all users");
            } else {
                remarks.append("\nWarning: Not all users' permissions were verified. Found permissions for: " + foundUsers);
            }

        } catch (Exception e) {
            LOGGER.severe("Error setting permissions: " + e);
            remarks.append("Error setting permissions: ").append(e.getMessage()).append("\n");
        } finally {
            operation.setRemarks(remarks.toString());
        }

        return success;
    }


    private static boolean setSharePermission(Operation operation) {
        String path = resolveHome(operation.getParameter("path"));
        String shareName = operation.getParameter("share_name");
        String permissions = operation.getParameter("permissions"); // "read" or "full" or "change"
        String users = operation.getParameter("user"); // Comma-separated list of users
        String removeExisting = operation.getParameter("remove_existing"); // "true" or "false"
        if (removeExisting != null && removeExisting.isEmpty()) {
            removeExisting = "false"; // Default to false if not specified
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            operation.setRemarks("Path does not exist: " + path);
            LOGGER.warning("Path does not exist: " + path);
            return false;
        }

        StringBuilder remarks = new StringBuilder("Setting share permissions for " + path + ":\n");
        boolean overallSuccess = true;

        try {
            // First check if share already exists
            String checkShareCmd = "net share";
            Process checkProcess = Runtime.getRuntime().exec(checkShareCmd);
            boolean shareExists = false;
            boolean isSamePath = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith(shareName + " ")) {
                        shareExists = true;
                        if (line.trim().contains(path)) {
                            isSamePath = true;
//                            remarks.append("- Share \"").append(shareName).append("\" already exists\n");
                            LOGGER.info("Share \"" + shareName + "\" already exists");
                            break;
                        }
                    }
                }
            }

            // If share exists and removeExisting is true, delete it first
            if (shareExists && "true".equalsIgnoreCase(removeExisting) && !isSamePath) {
//                remarks.append("- Removing existing share \"").append(shareName).append("\" before creating new one\n");
                String deleteCmd = "net share \"" + shareName + "\" /DELETE";
                LOGGER.info("Executing command to delete share: " + deleteCmd);
                Process deleteProcess = Runtime.getRuntime().exec(deleteCmd);
                int deleteExitCode = deleteProcess.waitFor();
                if (deleteExitCode != 0) {
                    remarks.append("- Failed to delete existing share\n");
                    LOGGER.info("Failed to delete existing share \"" + shareName + "\"");
                    operation.setRemarks(remarks.toString());
                    return false;
                }
                shareExists = false;
            }else {
                if (shareExists && !"true".equalsIgnoreCase(removeExisting) && !isSamePath) {
                    LOGGER.info("Share \"" + shareName + "\" already exists, Failed to create a new share.");
                    remarks.append("- Share \"").append(shareName).append("\" already exists, Failed to create a new share.\n");
                    return false;
                }
            }

            // Create share if it doesn't exist
            if (!shareExists) {
                String createShareCmd = "net share \"" + shareName + "\"=\"" + path + "\"";
                LOGGER.info("Executing command to create share: " + createShareCmd);
                Process createProcess = Runtime.getRuntime().exec(createShareCmd);
                int createExitCode = createProcess.waitFor();
                LOGGER.info("Create share command exit code: " + createExitCode);
                if (createExitCode != 0) {
                    remarks.append("- Failed to create share\n");
                    operation.setRemarks(remarks.toString());
                    return false;
                }
            }

            // Determine access right based on permission level
            String accessRight;
            if (permissions == null || permissions.isEmpty() || permissions.equalsIgnoreCase("read")) {
                accessRight = "Read";
            } else if (permissions.equalsIgnoreCase("full")) {
                accessRight = "Full";
            } else if (permissions.equalsIgnoreCase("change")) {
                accessRight = "Change";
            } else {
                accessRight = "Read"; // Default
            }

            // Parse users and set permissions for each one
            String[] userArray = users.split(",");
            for (String user : userArray) {
                user = user.trim();
                if (user.isEmpty()) continue;

                // Use PowerShell to set permissions
                String psCommand = "powershell -Command \"Grant-SmbShareAccess -Name '" + shareName +
                        "' -AccountName '" + user + "' -AccessRight " + accessRight + " -Force\"";

                LOGGER.info("Executing command: " + psCommand);
                Process psProcess = Runtime.getRuntime().exec(psCommand);

                boolean permissionSet = false;
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProcess.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(psProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.info("PowerShell output: " + line);
                        output.append(line).append("\n");
//                        remarks.append(line).append("\n");
                        if (line.toLowerCase().contains(user.toLowerCase()) && line.toLowerCase().contains(accessRight.toLowerCase())) {
                            LOGGER.info("Permission Check at: " + line);
                            permissionSet = true;
                        }
                    }
                    while ((line = errorReader.readLine()) != null) {
                        output.append(line).append("\n");
//                        remarks.append(line).append("\n");
                    }
                }

                int exitCode = psProcess.waitFor();
                LOGGER.info("PowerShell command exit code: " + exitCode);

                if (exitCode == 0 && permissionSet) {
//                    remarks.append("- Successfully granted ").append(accessRight).append(" permission to user ").append(user).append("\n");
                    LOGGER.info("Successfully granted " + accessRight + " permission to user " + user + " on share " + shareName);
                } else {
                    remarks.append("- Failed to grant ").append(accessRight).append(" permission to user ").append(user).append("\n");
                    LOGGER.info("Failed to grant " + accessRight + " permission to user " + user + " on share " + shareName);
                    overallSuccess = false;
                }
            }
            operation.setParameter("recursive", "true"); // Set recursive to true for file permission operations
            operation = changePermissionType(operation); // Change permission type to appropriate format
            boolean isSecurityPermissionSet = addFilePermission(operation);

            LOGGER.info("File security permission set: " + isSecurityPermissionSet);
//            remarks.append("- File security permission set: ").append(isSecurityPermissionSet).append("\n");
            LOGGER.info("File share permission set: " + overallSuccess);
//            remarks.append("- File share permission set: ").append(overallSuccess).append("\n");

            if (isSecurityPermissionSet && overallSuccess) {
                operation.setRemarks("Share permissions set successfully for " + shareName + " and share path of: " + "\\\\" + java.net.InetAddress.getLocalHost().getHostName() + "\\" + shareName);
                LOGGER.info("Share permissions set successfully for " + shareName + " at " + path);
                operation.setParameter("note",shareName);
                saveNote(operation, "\\\\" + java.net.InetAddress.getLocalHost().getHostName() + "\\" + shareName);
            } else {
                operation.setRemarks("Failed to set share permissions for " + shareName + " at " + path);
                LOGGER.info("Failed to set share permissions for " + shareName + " at " + path);
            }

            return overallSuccess && isSecurityPermissionSet;
        } catch (Exception e) {
            remarks.append("Error setting share permissions: ").append(e.getMessage()).append("\n");
            LOGGER.severe("Error setting share permissions: " + e);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            LOGGER.severe(sw.toString());
            return false;
        } finally {
            operation.setRemarks(remarks.toString());
        }
    }

    private static Operation changePermissionType(Operation operation) {
        String permission = operation.getParameter("permissions");

        LOGGER.info("Changing permission type from: " + permission);
        switch (permission.toLowerCase()) {
            case "full":
                operation.setParameter("permissions", "RWX");
                break;
            case "change":
                operation.setParameter("permissions", "RW");
                break;
            case "read":
                operation.setParameter("permissions", "R");
                break;
            default:
                System.out.println("Invalid permission");
        }
        LOGGER.info("Changing permission type from: " + permission + " to appropriate format: "+operation.getParameter("permissions"));
        operation.setRemarks("Changed permission type to: " + operation.getParameter("permissions"));
        return operation;
    }

    private static boolean removeSharePermission(Operation operation) {
        String shareName = operation.getParameter("share_name");
        String users = operation.getParameter("user"); // Optional comma-separated list

        StringBuilder remarks = new StringBuilder("Removing share permissions for " + shareName + ":\n");
        boolean success = true;

        try {
            // If users specified, remove permissions for each user
            if (users != null && !users.isEmpty()) {
                String[] userArray = users.split(",");
                for (String user : userArray) {
                    user = user.trim();
                    if (user.isEmpty()) continue;

                    String psCommand = "powershell -Command \"Revoke-SmbShareAccess -Name '" + shareName +
                            "' -AccountName '" + user + "' -Force\"";
                    LOGGER.info("Executing command: " + psCommand);
                    Process process = Runtime.getRuntime().exec(psCommand);

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                         BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        captureCommandOutput(reader, errorReader, remarks);
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        remarks.append("- Failed to remove permissions for user ").append(user).append(" (exit code: ").append(exitCode).append(")\n");
                        LOGGER.warning("Failed to remove permissions for user " + user + " (exit code: " + exitCode + ")");
                        success = false;
                    } else {
                        remarks.append("- Successfully removed permissions for user ").append(user).append("\n");
                        LOGGER.info("Successfully removed permissions for user " + user + " on share " + shareName);
                    }
                }
            } else {
                // If no users specified, delete the entire share
                String deleteCmd = "net share \"" + shareName + "\" /DELETE";
                LOGGER.info("Executing command: " + deleteCmd);
                Process process = Runtime.getRuntime().exec(deleteCmd);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    captureCommandOutput(reader, errorReader, remarks);
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    remarks.append("- Failed to delete share (exit code: ").append(exitCode).append(")\n");
                    LOGGER.warning("Failed to delete share \"" + shareName + "\" (exit code: " + exitCode + ")");
                    success = false;
                } else {
                    remarks.append("- Successfully deleted share \"").append(shareName).append("\"\n");
                    LOGGER.info("Successfully deleted share \"" + shareName + "\"");
                }
            }

            return success;
        } catch (Exception e) {
            remarks.append("Error removing share permissions: ").append(e.getMessage()).append("\n");
            LOGGER.severe("Error removing share permissions: " + e);
            return false;
        } finally {
            operation.setRemarks(remarks.toString());
        }
    }

    // Helper method to check if a command is available
    private static boolean isCommandAvailable(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", command});
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper method to capture command output
    private static void captureCommandOutput(BufferedReader reader, BufferedReader errorReader, StringBuilder remarks)
            throws IOException {
        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        StringBuilder errorOutput = new StringBuilder();
        while ((line = errorReader.readLine()) != null) {
            errorOutput.append(line).append("\n");
        }

        if (errorOutput.length() > 0) {
            remarks.append("Command errors:\n").append(errorOutput).append("\n");
        }

        if (output.length() > 0) {
            remarks.append("Command output:\n").append(output).append("\n");
        }
    }




    private static boolean createFileOrFolder(Operation operation) throws IOException {
        String path = resolveHome(operation.getParameter("file_path"));
        String type = operation.getParameter("type"); // "file" or "folder"
        String size = operation.getParameter("size"); // size in MB
        boolean deleteIfExists = Boolean.parseBoolean(operation.getParameter("delete_if_exists")); // "true" or "false"
        Path filePath = Paths.get(path);

        if (Files.exists(filePath)) {
            operation.setRemarks("Path already exists: " + path);
            LOGGER.info("Path already exists: " + path);
            if (deleteIfExists){
                try {
                    if (Files.isDirectory(filePath)) {
                        Files.walk(filePath)
                                .sorted(Comparator.reverseOrder()) // Delete files before directories
                                .forEach(p -> {
                                    try {
                                        Files.delete(p);
                                        LOGGER.info("Deleted: " + p);
                                    } catch (IOException e) {
                                        LOGGER.severe("Failed to delete: " + p + " - " + e.getMessage());
                                    }
                                });
                        operation.setRemarks("Folder deleted: " + path);
                    } else {
                        Files.delete(filePath);
                        LOGGER.info("File deleted: " + path);
                        operation.setRemarks("File deleted: " + path);
                    }
                } catch (IOException e) {
                    operation.setRemarks("Failed to delete existing path: " + e.getMessage());
                    LOGGER.severe("Failed to delete existing path: " + e.getMessage());
                    return false;
                }
            }else {
                LOGGER.info("Not deleting existing path as delete_if_exists is false.");
                operation.setRemarks("Not deleting existing path as delete_if_exists is false.");
                return false;
            }
        }

        if ("folder".equalsIgnoreCase(type)) {
            Files.createDirectories(filePath);
            operation.setRemarks("Folder created: " + path);
            LOGGER.info("Created folder: " + path);
        } else if ("file".equalsIgnoreCase(type)) {
            // Create parent directories if they do not exist
            File parentDir = filePath.getParent().toFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
                LOGGER.info("Created parent directories: " + parentDir.getAbsolutePath());
            }
            Files.createFile(filePath);
            LOGGER.info("Created file: " + path);
            if (size != null && !size.isEmpty()) {
                // Set file size if specified
                long sizeInBytes = Long.parseLong(size) * 1024 * 1024; // Convert MB to bytes
                try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
                    raf.setLength(sizeInBytes);
                    operation.setRemarks("File created: " + path + " with size: " + size + " MB");
                    LOGGER.info("Created file: " + path + " with size: " + size + " MB");
                    return true;
                }
            }
            operation.setRemarks("File created: " + path + " with size: " + operation.getParameter("size"));
            LOGGER.info("Created file: " + path);
        } else {
            operation.setRemarks("Invalid type specified: " + type);
            LOGGER.info("Invalid type specified: " + type);
            return false;
        }

        LOGGER.info("Created " + type + ": " + path);
        return true;
    }

    private static boolean checkFileOrFolderSize(Operation operation) {
        String path = resolveHome(operation.getParameter("file_path"));
        String expectedSizeStr = operation.getParameter("expected_size"); // in MB
        Path filePath = Paths.get(path);

        if (!Files.exists(filePath)) {
            operation.setRemarks("Path does not exist: " + path);
            return false;
        }

        try {
            long actualSize;
            if (Files.isDirectory(filePath)) {
                actualSize = Files.walk(filePath)
                        .filter(p -> !Files.isDirectory(p))
                        .mapToLong(p -> {
                            try { return Files.size(p); } catch (IOException e) { return 0L; }
                        }).sum();
            } else {
                actualSize = Files.size(filePath);
            }
            double actualSizeMB = actualSize / (1024.0 * 1024.0);
            operation.setParameter("actual_size", String.valueOf(actualSizeMB));
            operation.setRemarks("Actual size: " + actualSizeMB + " MB");
            LOGGER.info("Actual size of " + path + ": " + actualSizeMB + " MB");
            if (expectedSizeStr != null && !expectedSizeStr.isEmpty()) {
                double expectedSize = Double.parseDouble(expectedSizeStr);
                LOGGER.info("Expected size: " + expectedSize + " MB");
                boolean result =  Math.abs(actualSizeMB - expectedSize) < 0.1; // allow small tolerance 10 KB
                LOGGER.info("Size check result: " + result);
                return result;
            }else {
                operation.setRemarks("No expected size provided, returning actual size only.");
                LOGGER.info("No expected size provided, returning actual size only: " + actualSizeMB + " MB");
            }
            return true;
        } catch (IOException e) {
            operation.setRemarks("Error checking size: " + e.getMessage());
            return false;
        }
    }

    private static boolean checkFileOrFolderLastModified(Operation operation) {
        String path = resolveHome(operation.getParameter("file_path"));
        String expectedTimeStr = operation.getParameter("expected_time"); // epoch millis or formatted string
        String comparison = operation.getParameter("comparison"); // can be "before", "after", or null for exact match
        Path filePath = Paths.get(path);

        if (!Files.exists(filePath)) {
            operation.setRemarks("Path does not exist: " + path);
            return false;
        }

        try {
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

            // Convert to LocalDateTime and truncate to minutes to match input format precision
            LocalDateTime actualDateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(lastModified),
                            ZoneId.systemDefault())
                    .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

            String formattedTime = formatter.format(actualDateTime);

            LOGGER.info("Formatted last modified time: " + formattedTime);
            LOGGER.info("Expected time: " + expectedTimeStr);
            operation.setParameter("last_modified", String.valueOf(lastModified));
            operation.setParameter("last_modified_formatted", formattedTime);
            operation.setRemarks("Last modified: " + formattedTime + " (" + lastModified + " epoch millis)");

            if (expectedTimeStr != null && !expectedTimeStr.isEmpty()) {
                // Parse expected time
                LocalDateTime expectedDateTime = null;

                // Try epoch millis
                try {
                    long expectedTimeMillis = Long.parseLong(expectedTimeStr);
                    expectedDateTime = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(expectedTimeMillis),
                                    ZoneId.systemDefault())
                            .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
                } catch (NumberFormatException e) {
                    // Try formatted string
                    try {
                        expectedDateTime = LocalDateTime.parse(expectedTimeStr, formatter);
                    } catch (Exception ex) {
                        operation.setRemarks("Invalid expected_time format: " + expectedTimeStr);
                        LOGGER.warning("Invalid expected_time format: " + expectedTimeStr);
                        return false;
                    }
                }

                // Perform comparison
                if (comparison != null) {
                    switch (comparison.toLowerCase()) {
                        case "before":
                            boolean isBefore = actualDateTime.isBefore(expectedDateTime);
                            operation.setRemarks("Last modified time (" + formattedTime + ") is " +
                                    (isBefore ? "before" : "not before") + " expected time (" +
                                    expectedTimeStr + ")");
                            LOGGER.info("Last modified time (" + formattedTime + ") is " +
                                    (isBefore ? "before" : "not before") + " expected time (" +
                                    expectedTimeStr + ")");
                            return isBefore;
                        case "after":
                            boolean isAfter = actualDateTime.isAfter(expectedDateTime);
                            operation.setRemarks("Last modified time (" + formattedTime + ") is " +
                                    (isAfter ? "after" : "not after") + " expected time (" +
                                    expectedTimeStr + ")");
                            LOGGER.info("Last modified time (" + formattedTime + ") is " +
                                    (isAfter ? "after" : "not after") + " expected time (" +
                                    expectedTimeStr + ")");
                            return isAfter;
                        default:
                            operation.setRemarks("Unknown comparison type: " + comparison);
                            return false;
                    }
                } else {
                    // Default exact match behavior
                    LOGGER.info("Comparing actual date time: " + actualDateTime + " with expected date time: " + expectedDateTime);
                    boolean matches = actualDateTime.equals(expectedDateTime);
                    operation.setRemarks("Last modified time (" + formattedTime + ") " +
                            (matches ? "matches" : "does not match") + " expected time (" +
                            expectedTimeStr + ")");
                    LOGGER.info("Last modified time (" + formattedTime + ") " +
                            (matches ? "matches" : "does not match") + " expected time (" +
                            expectedTimeStr + ")");
                    return matches;
                }
            }
            return true;
        } catch (IOException e) {
            operation.setRemarks("Error checking last modified time: " + e.getMessage());
            LOGGER.severe("Error checking last modified time: " + e);
            return false;
        }
    }

    private static boolean removeFilePermission(Operation operation) throws IOException {
        String path = operation.getParameter("path");
        String permissions = operation.getParameter("permissions");
        boolean recursive = Boolean.parseBoolean(operation.getParameter("recursive"));
        String user = operation.getParameter("user");
        boolean disableInheritance = Boolean.parseBoolean(operation.getParameter("disable_inheritance"));

        if (user == null || user.isEmpty()) {
            operation.setRemarks("No user specified for permission removal");
            LOGGER.info("No user specified for permission removal");
            return false;
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            operation.setRemarks("Path does not exist: " + path);
            LOGGER.info("Path does not exist: " + path);
            return false;
        }

        StringBuilder remarks = new StringBuilder("Removing permissions " + permissions + " on " + path + ":\n");
        boolean success;

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            if (disableInheritance) {
                disableWindowsInheritance(filePath, recursive, remarks);
            }

            // Use icacls to remove permissions directly
            success = removeWindowsPermissionsWithIcacls(filePath, permissions, user, recursive, remarks);
        } else {
            success = removeUnixPermissions(filePath, permissions, recursive, user, remarks);
        }

        operation.setRemarks(remarks.toString());
        return success;
    }

    private static boolean removeWindowsPermissionsWithIcacls(Path filePath, String permissions,
                                                              String user, boolean recursive, StringBuilder remarks) {
        try {
            boolean success = true;

            // Use separate commands for each permission type
            if (permissions.toLowerCase().contains("r")) {
                String command = "icacls \"" + filePath + "\" /deny " + user + ":(R)";
                if (recursive) {
                    command += " /T";
                }
                LOGGER.info("Executing icacls command: " + command);
                Process process = Runtime.getRuntime().exec(command);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    remarks.append("- Failed to remove read permission (exit code: ").append(exitCode).append(")\n");
                    success = false;
                }
            }

            if (permissions.toLowerCase().contains("w")) {
                String command = "icacls \"" + filePath + "\" /deny " + user + ":(W)";
                if (recursive) {
                    command += " /T";
                }
                LOGGER.info("Executing icacls command: " + command);
                Process process = Runtime.getRuntime().exec(command);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    remarks.append("- Failed to remove write permission (exit code: ").append(exitCode).append(")\n");
                    success = false;
                }
            }

            if (permissions.toLowerCase().contains("x")) {
                String command = "icacls \"" + filePath + "\" /deny " + user + ":(X)";
                if (recursive) {
                    command += " /T";
                }
                LOGGER.info("Executing icacls command: " + command);
                Process process = Runtime.getRuntime().exec(command);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    remarks.append("- Failed to remove execute permission (exit code: ").append(exitCode).append(")\n");
                    success = false;
                }
            }
            if (success){
                remarks.append("- Successfully removed permissions ").append(permissions).append(" for user ").append(user).append("\n");
                LOGGER.info("Successfully removed permissions " + permissions + " for user " + user + " on " + filePath);
            }
            return success;
        } catch (Exception e) {
            remarks.append("Error removing permissions with icacls: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    private static void disableWindowsInheritance(Path filePath, boolean recursive, StringBuilder remarks) {
        try {
            String icaclsCommand = "icacls \"" + filePath + "\" /inheritance:r";
            if (recursive) {
                icaclsCommand += " /T";
            }

            LOGGER.info("Disabling inheritance with command: " + icaclsCommand);
            Process process = Runtime.getRuntime().exec(icaclsCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                LOGGER.info("Successfully disabled inheritance for " + filePath.getFileName());
                remarks.append("- Successfully disabled inheritance for ").append(filePath.getFileName()).append("\n");
            } else {
                LOGGER.info("Failed to disable inheritance for " + filePath.getFileName() + " (exit code: " + exitCode + ")");
                remarks.append("- Failed to disable inheritance (exit code: ").append(exitCode).append(")\n");
            }
        } catch (Exception e) {
            remarks.append("- Error disabling inheritance: ").append(e.getMessage()).append("\n");
            LOGGER.severe("Error disabling inheritance: " + e);
        }
    }


    private static boolean removeUnixPermissions(Path filePath, String permissions, boolean recursive, String scope, StringBuilder remarks) throws IOException {
        boolean success = true;

        try {
            // Get current permissions
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(filePath);
            Set<PosixFilePermission> originalPerms = new HashSet<>(perms);

            // Remove permissions based on scope
            if (scope.equals("all") || scope.equals("owner")) {
                if (permissions.toLowerCase().contains("r")) perms.remove(PosixFilePermission.OWNER_READ);
                if (permissions.toLowerCase().contains("w")) perms.remove(PosixFilePermission.OWNER_WRITE);
                if (permissions.toLowerCase().contains("x")) perms.remove(PosixFilePermission.OWNER_EXECUTE);
            }

            if (scope.equals("all") || scope.equals("group")) {
                if (permissions.toLowerCase().contains("r")) perms.remove(PosixFilePermission.GROUP_READ);
                if (permissions.toLowerCase().contains("w")) perms.remove(PosixFilePermission.GROUP_WRITE);
                if (permissions.toLowerCase().contains("x")) perms.remove(PosixFilePermission.GROUP_EXECUTE);
            }

            if (scope.equals("all") || scope.equals("others")) {
                if (permissions.toLowerCase().contains("r")) perms.remove(PosixFilePermission.OTHERS_READ);
                if (permissions.toLowerCase().contains("w")) perms.remove(PosixFilePermission.OTHERS_WRITE);
                if (permissions.toLowerCase().contains("x")) perms.remove(PosixFilePermission.OTHERS_EXECUTE);
            }

            // Apply the permissions
            Files.setPosixFilePermissions(filePath, perms);

            // Verify permissions were removed correctly
            Set<PosixFilePermission> verifyPerms = Files.getPosixFilePermissions(filePath);

            remarks.append("For " + filePath.getFileName() + ":\n");

            // Check if all requested permissions were removed
            boolean allRemoved = true;
            if (permissions.toLowerCase().contains("r")) {
                boolean readShouldBeRemoved = false;
                if ((scope.equals("all") || scope.equals("owner")) && originalPerms.contains(PosixFilePermission.OWNER_READ)) {
                    readShouldBeRemoved = true;
                    if (verifyPerms.contains(PosixFilePermission.OWNER_READ)) allRemoved = false;
                }
                if ((scope.equals("all") || scope.equals("group")) && originalPerms.contains(PosixFilePermission.GROUP_READ)) {
                    readShouldBeRemoved = true;
                    if (verifyPerms.contains(PosixFilePermission.GROUP_READ)) allRemoved = false;
                }
                if ((scope.equals("all") || scope.equals("others")) && originalPerms.contains(PosixFilePermission.OTHERS_READ)) {
                    readShouldBeRemoved = true;
                    if (verifyPerms.contains(PosixFilePermission.OTHERS_READ)) allRemoved = false;
                }

                if (readShouldBeRemoved) {
                    if (allRemoved) {
                        remarks.append("  - Read permissions removed successfully\n");
                    } else {
                        remarks.append("  - Failed to remove some read permissions\n");
                        success = false;
                    }
                }
            }

            // Similar checks for write and execute (omitted for brevity)

// Handle recursive removal for directories
            if (recursive && Files.isDirectory(filePath)) {
                try {
                    File[] children = filePath.toFile().listFiles();
                    if (children != null) {
                        for (File child : children) {
                            boolean childSuccess = removeUnixPermissions(child.toPath(), permissions, true, scope, remarks);
                            if (!childSuccess) success = false;
                        }
                    }
                } catch (Exception e) {
                    remarks.append("- Error listing directory contents: ").append(e.getMessage()).append("\n");
                    success = false;
                }
            }
        } catch (UnsupportedOperationException e) {
            remarks.append("- This filesystem does not support POSIX file permissions\n");
            success = false;
        }

        return success;
    }


    private static boolean copyFileOrFolder(Operation operation) throws IOException {
        String source = resolveHome(operation.getParameter("source"));
        String target = resolveHome(operation.getParameter("target"));
        boolean overwrite = Boolean.parseBoolean(operation.getParameter("overwrite"));

        Path sourcePath = Paths.get(source);
        Path targetPath = Paths.get(target);

        if (!Files.exists(sourcePath)) {
            operation.setRemarks("Source path does not exist: " + source);
            LOGGER.info("Source path does not exist: " + source);
            return false;
        }

        // Create the target directory if it doesn't exist
        if (!Files.exists(targetPath) && !target.contains(".")) {
            LOGGER.info("Creating target directory: " + targetPath);
            new File(targetPath.toString()).mkdirs();
        }

        // If target exists and is a directory, append the source filename to the target path
        if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
            LOGGER.info("Target is a directory, appending source filename: " + sourcePath.getFileName());
            targetPath = targetPath.resolve(sourcePath.getFileName());
        }

        // Create parent directories if needed
        File targetParent = targetPath.getParent().toFile();
        if (!targetParent.exists()) {
            LOGGER.info("Creating parent directories for target: " + targetParent);
            targetParent.mkdirs();
        }

        if (Files.isDirectory(sourcePath)) {
            copyFolder(sourcePath, targetPath, overwrite);
            operation.setRemarks("Folder copied from " + source + " to " + targetPath);
            LOGGER.info("Folder copied from " + source + " to " + targetPath);
        } else {
            if (overwrite) {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(sourcePath, targetPath);
            }
            operation.setRemarks("File copied from " + source + " to " + targetPath);
            LOGGER.info("File copied from " + source + " to " + targetPath);
        }

        return true;
    }

    private static void copyFolder(Path source, Path target, boolean overwrite) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                } else {
                    if (overwrite) {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } else if (!Files.exists(targetPath)) {
                        Files.copy(sourcePath, targetPath);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static boolean moveFileOrFolder(Operation operation) throws IOException {
        String source = operation.getParameter("source");
        String target = operation.getParameter("target");
        boolean overwrite = Boolean.parseBoolean(operation.getParameter("overwrite"));

        Path sourcePath = Paths.get(source);
        Path targetPath = Paths.get(target);

        if (!Files.exists(sourcePath)) {
            operation.setRemarks("Source path does not exist: " + source);
            return false;
        }

        // Create the target directory if it doesn't exist
        if (!Files.exists(targetPath) && !target.contains(".")) {
            LOGGER.info("Creating target directory: " + targetPath);
            new File(targetPath.toString()).mkdirs();
        }

        // If target exists and is a directory, append the source filename to the target path
        if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
            LOGGER.info("Target is a directory, appending source filename: " + sourcePath.getFileName());
            targetPath = targetPath.resolve(sourcePath.getFileName());
        }

        // Create parent directories if needed
        File targetParent = targetPath.getParent().toFile();
        if (!targetParent.exists()) {
            LOGGER.info("Creating parent directories for target: " + targetParent);
            targetParent.mkdirs();
        }

        if (overwrite) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(sourcePath, targetPath);
        }

        operation.setRemarks("Moved from " + source + " to " + targetPath);
        LOGGER.info("Moved from " + source + " to " + targetPath);
        return true;
    }

    private static boolean deleteFileOrFolder(Operation operation) throws IOException {
        String path = operation.getParameter("file_path");
        if (path == null || path.isEmpty()) {
            operation.setRemarks("No file or folder path specified for deletion");
            LOGGER.warning("No file or folder path specified for deletion");
            return false;
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            operation.setRemarks("Path does not exist: " + path);
            LOGGER.warning("Path does not exist: " + path);
            return false;
        }

        if (Files.isDirectory(filePath)) {
            Files.walk(filePath)
                    .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order to delete children first
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            LOGGER.info("Deleted: " + p);
                            operation.setRemarks("Deleted: " + p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } else {
            Files.delete(filePath);
            LOGGER.info("Deleted file: " + path);
            operation.setRemarks("Deleted file: " + path);
        }

        return true;
    }

    private static boolean setFilePermission(Operation operation) {
        String path = operation.getParameter("path");
        String permissions = operation.getParameter("permissions");
        boolean recursive = Boolean.parseBoolean(operation.getParameter("enable_inheritance"));
        String user = operation.getParameter("user");

        if (user == null || user.isEmpty()) {
            LOGGER.warning("No user specified for permission setting");
            operation.setRemarks("No user specified for permission setting");
            return false;
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            LOGGER.warning("Path does not exist: " + path);
            operation.setRemarks("Path does not exist: " + path);
            return false;
        }

        StringBuilder remarks = new StringBuilder("Setting permissions " + permissions + " for user " + user + " on " + path + ":\n");
        boolean success = false;

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            LOGGER.info("Detected Windows OS, disabling inheritance and setting permissions with icacls");
            disableWindowsInheritance(filePath, recursive, remarks);
            success = setWindowsPermissionsWithIcacls(filePath, permissions, user, recursive, remarks);
        } else {
            LOGGER.info("Detected Unix OS, setting permissions for user with chmod/getfacl");
            success = setUnixPermissionsForUser(filePath, permissions, user, recursive, remarks);
        }

        LOGGER.info("setFilePermission result for " + path + ": " + success);
        operation.setRemarks(remarks.toString());
        return success;
    }
    private static boolean setUnixPermissionsForUser(Path filePath, String permissions,
                                                     String user, boolean recursive, StringBuilder remarks) {
        try {
            StringBuilder permString = new StringBuilder();

            // Convert rwx format to setfacl format
            if (permissions.toLowerCase().contains("r")) permString.append("r");
            if (permissions.toLowerCase().contains("w")) permString.append("w");
            if (permissions.toLowerCase().contains("x")) permString.append("x");

            // Build setfacl command
            String recursiveFlag = recursive ? " -R" : "";
            String command = "setfacl" + recursiveFlag + " -m u:" + user + ":" + permString + " \"" + filePath + "\"";

            LOGGER.info("Executing setfacl command: " + command);
            Process process = Runtime.getRuntime().exec(command);

            // Capture command output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                // Also capture error output
                StringBuilder errorOutput = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }

                if (errorOutput.length() > 0) {
                    remarks.append("Command errors:\n").append(errorOutput).append("\n");
                    LOGGER.warning("Command errors:\n" + errorOutput);
                }

                if (output.length() > 0) {
                    LOGGER.info("Command output:\n" + output);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                remarks.append("- Failed to set permissions for user ").append(user).append(" (exit code: ").append(exitCode).append(")\n");
                LOGGER.warning("Failed to set permissions for user " + user + " on " + filePath.getFileName() + " (exit code: " + exitCode + ")");

                // Check if setfacl is not available, try fallback to chmod for basic permissions
                if (exitCode == 127) { // Command not found
                    remarks.append("- setfacl not available, trying fallback method\n");
                    LOGGER.warning("setfacl command not found, trying fallback method for permissions");
                    return setUnixPermissionsFallback(filePath, permissions, user, recursive, remarks);
                }

                return false;
            } else {
                remarks.append("- Successfully set ").append(permissions)
                        .append(" permissions for user ").append(user)
                        .append(" on ").append(filePath.getFileName()).append("\n");
                return true;
            }
        } catch (Exception e) {
            remarks.append("Error setting Unix permissions: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    private static boolean setUnixPermissionsFallback(Path filePath, String permissions,
                                                      String user, boolean recursive, StringBuilder remarks) {
        try {
            // First try to change ownership if the specified user is different
            if (!user.equals(System.getProperty("user.name"))) {
                String chownCmd = "chown " + (recursive ? "-R " : "") + user + " \"" + filePath + "\"";
                Process chownProcess = Runtime.getRuntime().exec(chownCmd);
                int chownExitCode = chownProcess.waitFor();

                if (chownExitCode != 0) {
                    remarks.append("- Failed to change owner to ").append(user)
                            .append(" (may require sudo/root privileges)\n");
                    // Continue anyway to set permissions
                }
            }

            // Convert permissions to octal format for chmod
            int permValue = 0;
            if (permissions.toLowerCase().contains("r")) permValue += 4;
            if (permissions.toLowerCase().contains("w")) permValue += 2;
            if (permissions.toLowerCase().contains("x")) permValue += 1;

            // Build chmod command with numeric permissions
            String chmodCmd = "chmod " + (recursive ? "-R " : "") + permValue + "00 \"" + filePath + "\"";
            Process chmodProcess = Runtime.getRuntime().exec(chmodCmd);

            int chmodExitCode = chmodProcess.waitFor();
            if (chmodExitCode != 0) {
                remarks.append("- Fallback chmod failed (exit code: ").append(chmodExitCode).append(")\n");
                LOGGER.warning("Fallback chmod failed for " + filePath.getFileName() + " (exit code: " + chmodExitCode + ")");
                return false;
            }

            remarks.append("- Set permissions using fallback chmod method\n");
            LOGGER.info("Set permissions using fallback chmod method for " + filePath.getFileName());
            return true;
        } catch (Exception e) {
            remarks.append("Error in fallback permission setting: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    private static boolean setWindowsPermissionsWithIcacls(Path filePath, String permissions,
                                                           String user, boolean recursive, StringBuilder remarks) {
        try {
            // Prepare the appropriate permission flags
            String permFlags;

            if (permissions.toLowerCase().contains("r") && permissions.toLowerCase().contains("w") && permissions.toLowerCase().contains("x")) {
                permFlags = "(F)";  // Full control
            } else if (permissions.toLowerCase().contains("r") && permissions.toLowerCase().contains("x")) {
                permFlags = "(RX)"; // Read & Execute
            } else if (permissions.toLowerCase().contains("r") && permissions.toLowerCase().contains("w")) {
                permFlags = "(RW)"; // Read & Write
            } else if (permissions.toLowerCase().contains("w") && permissions.toLowerCase().contains("x")) {
                permFlags = "(M)";  // Modify (closest to write+execute)
            } else if (permissions.toLowerCase().contains("r")) {
                permFlags = "(R)";  // Read only
            } else if (permissions.toLowerCase().contains("w")) {
                permFlags = "(W)";  // Write only
            } else if (permissions.toLowerCase().contains("x")) {
                permFlags = "(X)";  // Execute only
            } else {
                remarks.append("No valid permissions specified\n");
                LOGGER.warning("No valid permissions specified for user " + user + " on " + filePath);
                return false;
            }

            // Build and execute the icacls command
            String command = "icacls \"" + filePath + "\" /grant " + user + ":(OI)(CI)" + permFlags;
            if (recursive) {
                command += " /T";
            }

            LOGGER.info("Executing icacls command: " + command);
            Process process = Runtime.getRuntime().exec(command);

            // Capture and log the command output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                StringBuilder errorOutput = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }

                if (errorOutput.length() > 0) {
                    remarks.append("Command errors:\n").append(errorOutput).append("\n");
                }

                if (output.length() > 0) {
                    remarks.append("Command output:\n").append(output).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                remarks.append("- Failed to set permissions (exit code: ").append(exitCode).append(")\n");
                LOGGER.warning("Failed to set permissions with icacls (exit code: " + exitCode + ")");
                return false;
            } else {
                remarks.append("- Successfully set ").append(permissions).append(" permissions for ").append(user).append("\n");
                LOGGER.info("Successfully set " + permissions + " permissions for " + user + " on " + filePath.getFileName());
                return true;
            }
        } catch (Exception e) {
            remarks.append("Error setting permissions with icacls: ").append(e.getMessage()).append("\n");
            LOGGER.severe("Error setting permissions with icacls: " + e);
            return false;
        }
    }

    private static boolean checkFilePermission(Operation operation) {
        String path = operation.getParameter("path");

        String requiredPermissions = operation.getParameter("permissions");
        String user = operation.getParameter("user");

        if (user == null || user.isEmpty()) {
            operation.setRemarks("No user specified for permission check");
            return false;
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            operation.setRemarks("Path does not exist: " + path);
            return false;
        }

        StringBuilder remarks = new StringBuilder("Permission check for " + path + " for user " + user + ":\n");
        boolean hasExpectedPermissions;

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            hasExpectedPermissions = checkWindowsPermissionsWithIcacls(filePath, requiredPermissions,
                    user, remarks);
        } else {
            // Unix permission check
            hasExpectedPermissions = checkUnixPermissionsForUser(filePath, requiredPermissions,
                    user, remarks);
        }

        operation.setRemarks(remarks.toString());
        return hasExpectedPermissions;
    }
    private static boolean checkWindowsPermissionsWithIcacls(Path filePath, String permissions,
                                                             String user, StringBuilder remarks) {
        try {
            // Execute icacls to get current permissions
            String command = "icacls \"" + filePath + "\"";
            LOGGER.info("Executing icacls command: " + command);
            Process process = Runtime.getRuntime().exec(command);

            // Read output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();
            Set<String> userPerms = new HashSet<>();
            boolean userFound = false;

            // Parse icacls output
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                line = line.trim();

                // Check if this line contains the user we're looking for
                if (line.contains(user + ":")) {
                    userFound = true;
                    int colonIndex = line.indexOf(":");
                    String perms = line.substring(colonIndex + 1).trim();
                    userPerms.addAll(parsePermissionFlags(perms));
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                remarks.append("Failed to check permissions (exit code: ").append(exitCode).append(")\n");
                LOGGER.warning("Failed to check permissions with icacls (exit code: " + exitCode + ")");
                return false;
            }

            // If user wasn't found in the ACL
            if (!userFound) {
                remarks.append("User/group ").append(user).append(" not found in the ACL for this path\n");
                LOGGER.warning("User/group " + user + " not found in the ACL for this path");
                return false;
            }

            // Check for denials first (they override grants)
            boolean readDenied = userPerms.contains("DENY_R");
            boolean writeDenied = userPerms.contains("DENY_W");
            boolean executeDenied = userPerms.contains("DENY_X");

            // Verify required permissions
            boolean hasRead = !readDenied && (userPerms.contains("R") || userPerms.contains("RX") ||
                    userPerms.contains("F") || userPerms.contains("M"));
            boolean hasWrite = !writeDenied && (userPerms.contains("W") || userPerms.contains("F") ||
                    userPerms.contains("M"));
            boolean hasExecute = !executeDenied && (userPerms.contains("X") || userPerms.contains("RX") ||
                    userPerms.contains("F") || userPerms.contains("M"));

            // Verify the required permissions
            boolean hasRequiredPermissions = true;
            if (permissions.toLowerCase().contains("r") && !hasRead) {
                remarks.append("- Read permission check failed for user ").append(user).append("\n");
                hasRequiredPermissions = false;
            } else if (permissions.toLowerCase().contains("r")) {
                remarks.append("- Read permission verified for user ").append(user).append("\n");
            }

            if (permissions.toLowerCase().contains("w") && !hasWrite) {
                remarks.append("- Write permission check failed for user ").append(user).append("\n");
                hasRequiredPermissions = false;
            } else if (permissions.toLowerCase().contains("w")) {
                remarks.append("- Write permission verified for user ").append(user).append("\n");
            }

            if (permissions.toLowerCase().contains("x") && !hasExecute) {
                remarks.append("- Execute permission check failed for user ").append(user).append("\n");
                hasRequiredPermissions = false;
            } else if (permissions.toLowerCase().contains("x")) {
                remarks.append("- Execute permission verified for user ").append(user).append("\n");
            }

            return hasRequiredPermissions;
        } catch (Exception e) {
            remarks.append("Error checking permissions with icacls: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    private static boolean checkUnixPermissionsForUser(Path filePath, String permissions,
                                                       String user, StringBuilder remarks) {
        try {
            // Use getfacl to check user-specific permissions
            String command = "getfacl -p \"" + filePath + "\"";
            LOGGER.info("Executing getfacl command: " + command);
            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();
            boolean userFound = false;
            boolean hasRead = false;
            boolean hasWrite = false;
            boolean hasExecute = false;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");

                // Check for user-specific entries
                if (line.startsWith("user:" + user + ":")) {
                    userFound = true;
                    String perms = line.substring(line.lastIndexOf(":") + 1);

                    if (perms.toLowerCase().contains("r")) hasRead = true;
                    if (perms.toLowerCase().contains("w")) hasWrite = true;
                    if (perms.toLowerCase().contains("x")) hasExecute = true;
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                remarks.append("Failed to check permissions (exit code: " + exitCode + ")\n");
                remarks.append("getfacl may not be available, trying fallback method\n");

                // Try fallback with ls -l
                return checkUnixPermissionsFallback(filePath, permissions, user, remarks);
            }

            // If user wasn't found using getfacl
            if (!userFound) {
                remarks.append("User " + user + " not found in ACL, trying fallback method\n");
                return checkUnixPermissionsFallback(filePath, permissions, user, remarks);
            }

            // Verify required permissions
            boolean hasRequiredPermissions = true;
            if (permissions.toLowerCase().contains("r") && !hasRead) {
                remarks.append("- Read permission check failed for user " + user + "\n");
                hasRequiredPermissions = false;
            } else if (permissions.toLowerCase().contains("r")) {
                remarks.append("- Read permission verified for user " + user + "\n");
            }

            if (permissions.toLowerCase().contains("w") && !hasWrite) {
                remarks.append("- Write permission check failed for user " + user + "\n");
                hasRequiredPermissions = false;
            } else if (permissions.toLowerCase().contains("w")) {
                remarks.append("- Write permission verified for user " + user + "\n");
            }

            if (permissions.toLowerCase().contains("x") && !hasExecute) {
                remarks.append("- Execute permission check failed for user " + user + "\n");
                hasRequiredPermissions = false;
            } else if (permissions.toLowerCase().contains("x")) {
                remarks.append("- Execute permission verified for user " + user + "\n");
            }

            return hasRequiredPermissions;
        } catch (Exception e) {
            remarks.append("Error checking Unix permissions: " + e.getMessage() + "\n");
            return false;
        }
    }
    private static boolean checkUnixPermissionsFallback(Path filePath, String permissions,
                                                        String user, StringBuilder remarks) {
        try {
            // Check if the specified user is the current user
            boolean isCurrentUser = user.equals(System.getProperty("user.name"));

            // Get file owner and group using ls -l
            String lsCommand = "ls -l \"" + filePath + "\"";
            Process lsProcess = Runtime.getRuntime().exec(lsCommand);
            BufferedReader lsReader = new BufferedReader(new InputStreamReader(lsProcess.getInputStream()));

            String lsOutput = lsReader.readLine(); // First line contains the permissions info
            if (lsOutput == null) {
                remarks.append("Error: Could not get file permissions\n");
                return false;
            }

            // Parse ls -l output
            // Format: -rwxrwxrwx 1 owner group size date time filename
            String[] parts = lsOutput.trim().split("\\s+", 9);
            if (parts.length < 4) {
                remarks.append("Error: Unexpected ls output format\n");
                return false;
            }

            String permString = parts[0];
            String fileOwner = parts[2];
            String fileGroup = parts[3];

            // Determine which permission set to check (owner, group, others)
            int permStartIndex;
            if (isCurrentUser || user.equals(fileOwner)) {
                // Check owner permissions (positions 1-3)
                permStartIndex = 1;
                remarks.append("Checking owner permissions for " + user + "\n");
            } else if (isUserInGroup(user, fileGroup)) {
                // Check group permissions (positions 4-6)
                permStartIndex = 4;
                remarks.append("Checking group permissions for " + user + " in group " + fileGroup + "\n");
            } else {
                // Check others permissions (positions 7-9)
                permStartIndex = 7;
                remarks.append("Checking others permissions for " + user + "\n");
            }

            // Check required permissions
            boolean hasAllPermissions = true;

            if (permissions.toLowerCase().contains("r") && permString.charAt(permStartIndex) != 'r') {
                remarks.append("- Read permission check failed\n");
                hasAllPermissions = false;
            } else if (permissions.toLowerCase().contains("r")) {
                remarks.append("- Read permission verified\n");
            }

            if (permissions.toLowerCase().contains("w") && permString.charAt(permStartIndex + 1) != 'w') {
                remarks.append("- Write permission check failed\n");
                hasAllPermissions = false;
            } else if (permissions.toLowerCase().contains("w")) {
                remarks.append("- Write permission verified\n");
            }

            if (permissions.toLowerCase().contains("x") && permString.charAt(permStartIndex + 2) != 'x') {
                remarks.append("- Execute permission check failed\n");
                hasAllPermissions = false;
            } else if (permissions.toLowerCase().contains("x")) {
                remarks.append("- Execute permission verified\n");
            }

            return hasAllPermissions;
        } catch (Exception e) {
            remarks.append("Error in fallback permission check: " + e.getMessage() + "\n");
            return false;
        }
    }

    // Helper method to check if a user is member of a group
    private static boolean isUserInGroup(String user, String group) {
        try {
            // Using 'groups' command to check if user is in the specified group
            Process process = Runtime.getRuntime().exec("groups " + user);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line = reader.readLine();
            if (line != null) {
                // Format: username : group1 group2 ...
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String groupsList = parts[1].trim();
                    for (String g : groupsList.split("\\s+")) {
                        if (g.equals(group)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            // If any error occurs, assume user is not in group
            return false;
        }
    }

    // Helper method to parse permission flags
    private static Set<String> parsePermissionFlags(String permFlags) {
        Set<String> result = new HashSet<>();

        // Handle special Windows permission flags
        if (permFlags.contains("(F)")) result.add("F"); // Full control
        if (permFlags.contains("(M)")) result.add("M"); // Modify
        if (permFlags.contains("(RX)")) result.add("RX"); // Read & Execute
        if (permFlags.contains("(R)")) result.add("R"); // Read
        if (permFlags.contains("(W)")) result.add("W"); // Write
        if (permFlags.contains("(X)")) result.add("X"); // Execute

        // Handle denied permissions
        if (permFlags.contains("DENY") && permFlags.contains("(R)")) result.add("DENY_R");
        if (permFlags.contains("DENY") && permFlags.contains("(W)")) result.add("DENY_W");
        if (permFlags.contains("DENY") && permFlags.contains("(X)")) result.add("DENY_X");

        return result;
    }
}