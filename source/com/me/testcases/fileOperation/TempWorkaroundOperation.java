package com.me.testcases.fileOperation;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.me.testcases.DataBaseOperationHandler.saveNote;
import static com.me.util.CommonUtill.resolveHome;

/**
 * Handler for backup file operations - searching folders and 7z files by creation time
 */
public class TempWorkaroundOperation {
    private static final Logger LOGGER = LogManager.getLogger(TempWorkaroundOperation.class, LogManager.LOG_TYPE.FW);

    // Pattern to match folder names like "114254001-Nov-14-2025-13-17"
    private static final Pattern FOLDER_PATTERN = Pattern.compile("\\d+-[A-Za-z]+-\\d{2}-\\d{4}-\\d{2}-\\d{2}");

    public static boolean executeOperation(Operation operation) {
        String action = operation.getParameter("action");

        operation = ResolveOperationParameters.resolveOperationParameters(operation);

        try {
            switch (action) {
                case "get_old_backup_by_time":
                    return getOldBackupByTime(operation);
                case "get_backup_file_name":
                    return getBackupFileName(operation);
                default:
                    LOGGER.info("Unsupported action: " + action);
                    operation.setRemarks("Unsupported action: " + action);
                    return false;
            }
        } catch (Exception e) {
            LOGGER.severe("Error executing backup file operation: " + e);
            operation.setRemarks("Error executing backup file operation: " + e.getMessage());
            return false;
        }
    }



    /**
     * Search for existing 7z backup file with matching pattern and optional time filter
     */
    private static boolean getOldBackupByTime(Operation operation) {
        String folderToSearch = resolveHome(operation.getParameter("folder_to_search"));
        long timeWindowMinutes = getParameterAsLong(operation, "time_to_check_before", 0);

        if (folderToSearch == null || folderToSearch.isEmpty()) {
            operation.setRemarks("folder_to_search parameter is missing");
            LOGGER.severe("folder_to_search parameter is missing");
            return false;
        }

        Path searchPath = Paths.get(folderToSearch);
        if (!Files.exists(searchPath) || !Files.isDirectory(searchPath)) {
            operation.setRemarks("Search path does not exist or is not a directory: " + folderToSearch);
            LOGGER.severe("Search path does not exist or is not a directory: " + folderToSearch);
            return false;
        }

        LOGGER.info("Searching for 7z backup file in: " + folderToSearch);
        if (timeWindowMinutes > 0) {
            LOGGER.info("Time filter: Last modified within " + timeWindowMinutes + " minutes");
        }

        File searchDir = searchPath.toFile();
        File[] files = searchDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".7z"));

        if (files == null || files.length == 0) {
            operation.setRemarks("No 7z files found in the specified folder");
            LOGGER.severe("No 7z files found in folder: " + folderToSearch);
            return false;
        }

        for (File file : files) {
            String fileNameWithoutExtension = file.getName().replaceAll("\\.7z$", "");

            if (FOLDER_PATTERN.matcher(fileNameWithoutExtension).matches()) {
                // Check time window if specified
                if (timeWindowMinutes > 0) {
                    if (!isFileModifiedWithinTimeWindow(file, timeWindowMinutes)) {
                        LOGGER.info("File " + file.getName() + " matches pattern but not within time window");
                        continue;
                    }
                }

                LOGGER.info("Found matching 7z file: " + file.getName());

                if (operation.hasNote()) {
                    String note = operation.getParameter("note");
                    operation.setParameter("note", note + "_path");
                    saveNote(operation, file.getParent());
                    operation.setParameter("note", note + "_name");
                    saveNote(operation, file.getName());
                    LOGGER.info("Note saved with 7z file path: " + file.getAbsolutePath());
                }

                operation.setRemarks("Successfully found 7z backup file: " + file.getName() + "\n" +
                        "Full path: " + file.getAbsolutePath() + "\n" +
                        "File size: " + (file.length() / (1024.0 * 1024.0)) + " MB" + "\n" +
                        "Last modified: " + new java.util.Date(file.lastModified()));

                return true;
            } else {
                LOGGER.fine("File " + file.getName() + " does not match pattern");
            }
        }

        String timeMsg = timeWindowMinutes > 0
                ? " (within last " + timeWindowMinutes + " minutes)"
                : "";
        operation.setRemarks("No 7z file matching the pattern found" + timeMsg);
        LOGGER.severe("No matching 7z file found in folder: " + folderToSearch);
        return false;
    }

    /**
     * Check if file was modified within specified minutes from now
     */
    private static boolean isFileModifiedWithinTimeWindow(File file, long minutes) {
        try {
            long lastModified = file.lastModified();
            long now = System.currentTimeMillis();
            long diffMinutes = (now - lastModified) / (60 * 1000);

            LOGGER.info("File: " + file.getName() +
                    " last modified: " + new java.util.Date(lastModified) +
                    " (" + diffMinutes + " minutes ago)");

            return diffMinutes <= minutes;
        } catch (Exception e) {
            LOGGER.warning("Error checking file last modified time for " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }


    /**
     * Search for backup folder created within ±2 minutes and find corresponding 7z file
     */
    private static boolean getBackupFileName(Operation operation) {
        String folderToSearch = resolveHome(operation.getParameter("folder_to_search"));
        long folderTimeoutSeconds = getParameterAsLong(operation, "folder_timeout", 300);
        long zipTimeoutSeconds = getParameterAsLong(operation, "zip_timeout", 300);
        long checkIntervalSeconds = getParameterAsLong(operation, "check_interval", 10);

        if (folderToSearch == null || folderToSearch.isEmpty()) {
            operation.setRemarks("folder_to_search parameter is missing");
            LOGGER.severe("folder_to_search parameter is missing");
            return false;
        }

        Path searchPath = Paths.get(folderToSearch);
        if (!Files.exists(searchPath) || !Files.isDirectory(searchPath)) {
            operation.setRemarks("Search path does not exist or is not a directory: " + folderToSearch);
            LOGGER.severe("Search path does not exist or is not a directory: " + folderToSearch);
            return false;
        }

        LOGGER.info("Searching for backup folder in: " + folderToSearch);

        // Wait for folder matching pattern with valid creation time
        File foundFolder = waitForMatchingFolder(searchPath, folderTimeoutSeconds, checkIntervalSeconds);
        if (foundFolder == null) {
            operation.setRemarks("No backup folder found within timeout period matching pattern and creation time (±2 minutes)");
            LOGGER.severe("No backup folder found within timeout period");
            return false;
        }

        LOGGER.info("Found matching folder: " + foundFolder.getName());
        operation.setRemarks("Found backup folder: " + foundFolder.getName() + "\n");

        // Construct 7z file name from folder name
        String sevenZipFileName = foundFolder.getName() + ".7z";
        File sevenZipFile = new File(folderToSearch, sevenZipFileName);

        LOGGER.info("Looking for 7z file: " + sevenZipFileName);

        // Wait for 7z file
        boolean fileFound = waitForSevenZipFile(sevenZipFile, zipTimeoutSeconds, checkIntervalSeconds);
        if (!fileFound) {
            operation.setRemarks(operation.getRemarks() + "7z file not found within timeout: " + sevenZipFileName);
            LOGGER.severe("7z file not found within timeout: " + sevenZipFileName);
            return false;
        }

        LOGGER.info("Found 7z file: " + sevenZipFile.getAbsolutePath());

        // Save note with 7z file path
        if (operation.hasNote()) {
            String note = operation.getParameter("note");
            operation.setParameter("note",note+"_path");
            saveNote(operation, sevenZipFile.getParent());
            operation.setParameter("note",note+"_name");
            saveNote(operation, sevenZipFile.getName());
            LOGGER.info("Note saved with 7z file path: " + sevenZipFile.getAbsolutePath());
        }

        operation.setRemarks(operation.getRemarks() +
                "Successfully found 7z file: " + sevenZipFileName + "\n" +
                "Full path: " + sevenZipFile.getAbsolutePath() + "\n" +
                "File size: " + (sevenZipFile.length() / (1024.0 * 1024.0)) + " MB");

        return true;
    }

    /**
     * Poll for folder matching pattern and creation time within ±2 minutes
     */
    private static File waitForMatchingFolder(Path searchPath, long timeoutSeconds, long intervalSeconds) {
        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000);

        while (System.currentTimeMillis() < endTime) {
            File searchDir = searchPath.toFile();
            File[] folders = searchDir.listFiles(File::isDirectory);

            if (folders != null) {
                for (File folder : folders) {
                    // Check if folder name matches pattern
                    if (FOLDER_PATTERN.matcher(folder.getName()).matches()) {
                        // Check if folder was created within ±2 minutes
                        if (isFolderCreatedWithinTimeWindow(folder)) {
                            LOGGER.info("Found matching folder: " + folder.getName() +
                                    " created within ±2 minutes");
                            return folder;
                        } else {
                            LOGGER.info("Folder " + folder.getName() +
                                    " matches pattern but not within time window");
                        }
                    }else {
                        LOGGER.fine("Folder " + folder.getName() + " does not match pattern");
                    }
                }
            }

            // Sleep before next check
            try {
                Thread.sleep(intervalSeconds * 1000);
                LOGGER.info("Waiting for matching folder...");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Interrupted while waiting for folder");
                return null;
            }
        }

        return null;
    }

    /**
     * Check if folder was created within ±2 minutes of current time
     */
    private static boolean isFolderCreatedWithinTimeWindow(File folder) {
        try {
            FileTime creationTime = (FileTime) Files.getAttribute(folder.toPath(), "creationTime");
            Instant now = Instant.now();
            Instant created = creationTime.toInstant();
            long diffMinutes = Math.abs(Duration.between(now, created).toMinutes());

            LOGGER.info("Folder: " + folder.getName() +
                    " created at: " + created +
                    " (difference: " + diffMinutes + " minutes)");

            return diffMinutes <= 4;
        } catch (Exception e) {
            LOGGER.warning("Error checking folder creation time for " + folder.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Poll for 7z file existence
     */
    private static boolean waitForSevenZipFile(File sevenZipFile, long timeoutSeconds, long intervalSeconds) {
        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000);

        while (System.currentTimeMillis() < endTime) {
            if (sevenZipFile.exists() && sevenZipFile.isFile()) {
                LOGGER.info("Found 7z file: " + sevenZipFile.getName());
                return true;
            }

            try {
                Thread.sleep(intervalSeconds * 1000);
                LOGGER.info("Waiting for 7z file: " + sevenZipFile.getName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Interrupted while waiting for 7z file");
                return false;
            }
        }

        return false;
    }

    /**
     * Get parameter as long with default value
     */
    private static long getParameterAsLong(Operation operation, String paramName, long defaultValue) {
        String value = operation.getParameter(paramName);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid " + paramName + " value: " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
}
