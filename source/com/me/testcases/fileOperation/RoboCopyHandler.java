package com.me.testcases.fileOperation;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.Operation;
import com.me.util.LogManager;

/**
 * Handler for robocopy operations between machines or directories
 */
public class RoboCopyHandler {
    private static final Logger LOGGER = LogManager.getLogger(RoboCopyHandler.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute a robocopy operation
     *
     * @param op The operation containing robocopy parameters
     * @return true if robocopy was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");//NO I18N
            return false;
        }

        String sourcePath = op.getParameter("source_path");//NO I18N
        String destinationPath = op.getParameter("destination_path");//NO I18N
        String options = op.getParameter("options");//NO I18N

        if (sourcePath == null || sourcePath.isEmpty()) {
            LOGGER.warning("Source path is required for robocopy operation");//NO I18N
            return false;
        }

        if (destinationPath == null || destinationPath.isEmpty()) {
            LOGGER.warning("Destination path is required for robocopy operation");//NO I18N
            return false;
        }

        // Resolve paths (handle placeholders)
        sourcePath = resolvePath(sourcePath);
        destinationPath = resolvePath(destinationPath);

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("RoboCopy Operation\n");//NO I18N
        remarkBuilder.append("Source path: ").append(sourcePath).append("\n");//NO I18N
        remarkBuilder.append("Destination path: ").append(destinationPath).append("\n");//NO I18N

        try {
            // Check if source exists
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                String errorMsg = "Source path does not exist: " + sourcePath;//NO I18N
                LOGGER.warning(errorMsg);
                remarkBuilder.append("Error: ").append(errorMsg);//NO I18N
                op.setRemarks(remarkBuilder.toString());
                return false;
            }

            // Ensure destination parent directory exists
            File destFile = new File(destinationPath);
            File parentDir = destFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                String errorMsg = "Failed to create destination directory: " + parentDir.getPath();//NO I18N
                LOGGER.warning(errorMsg);
                remarkBuilder.append("Error: ").append(errorMsg);//NO I18N
                op.setRemarks(remarkBuilder.toString());
                return false;
            }

            // Build robocopy command
            List<String> command = buildRobocopyCommand(sourcePath, destinationPath, options);
            remarkBuilder.append("Command: ").append(String.join(" ", command)).append("\n");//NO I18N

            // Execute robocopy command
            long startTime = System.currentTimeMillis();
            int exitCode = executeRobocopyCommand(command, remarkBuilder);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Add robocopy statistics
            remarkBuilder.append("\nRoboCopy completed with exit code: ").append(exitCode).append("\n");//NO I18N
            remarkBuilder.append("  - Duration: ").append(formatDuration(duration)).append("\n");//NO I18N

            // In robocopy, exit codes 0-7 are considered successful with different meanings
            boolean success = exitCode < 8;

            if (success) {
                remarkBuilder.append("  - Status: Successful (").append(getRobocopyStatusMessage(exitCode)).append(")\n");//NO I18N
            } else {
                remarkBuilder.append("  - Status: Failed (").append(getRobocopyStatusMessage(exitCode)).append(")\n");//NO I18N
            }

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(remarkBuilder.toString());
            LOGGER.info(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing robocopy command", e);//NO I18N
            remarkBuilder.append("\nError executing robocopy: ").append(e.getMessage());//NO I18N
            op.setRemarks(remarkBuilder.toString());
            return false;
        }
    }

    /**
     * Build robocopy command with options
     */
    private static List<String> buildRobocopyCommand(String sourcePath, String destinationPath, String options) {
        List<String> command = new ArrayList<>();
        command.add("robocopy");
        command.add(sourcePath);
        command.add(destinationPath);

        // Add options if provided
        if (options != null && !options.isEmpty()) {
            // Split options by space, respecting quotes
            boolean inQuotes = false;
            StringBuilder sb = new StringBuilder();

            for (char c : options.toCharArray()) {
                if (c == '"') {
                    inQuotes = !inQuotes;
                    sb.append(c);
                } else if (c == ' ' && !inQuotes) {
                    if (sb.length() > 0) {
                        command.add(sb.toString());
                        sb.setLength(0);
                    }
                } else {
                    sb.append(c);
                }
            }

            if (sb.length() > 0) {
                command.add(sb.toString());
            }
        } else {
            // Default options if none provided
            command.add("/E"); // Copy subdirectories, including empty ones
            command.add("/Z"); // Copy files in restartable mode
            command.add("/R:3"); // Retry 3 times
            command.add("/W:10"); // Wait 10 seconds between retries
        }

        return command;
    }

    /**
     * Execute robocopy command and capture output
     */
    private static int executeRobocopyCommand(List<String> command, StringBuilder remarks) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // Merge stdout and stderr

        Process process = processBuilder.start();

        // Capture and log output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            remarks.append("\nOutput:\n");//NO I18N
            while ((line = reader.readLine()) != null) {
                LOGGER.info("RoboCopy output: " + line);//NO I18N
                remarks.append(line).append("\n");
            }
        }

        // Wait for process to complete and get exit code
        return process.waitFor();
    }

    /**
     * Get robocopy status message based on exit code
     */
    private static String getRobocopyStatusMessage(int exitCode) {
        switch (exitCode) {
            case 0:
                return "No files were copied. No failure was encountered.";//NO I18N
            case 1:
                return "One or more files were copied successfully.";//NO I18N
            case 2:
                return "Extra files or directories were detected. No files were copied.";//NO I18N
            case 3:
                return "Some files were copied. Additional files were present.";//NO I18N
            case 4:
                return "Some Mismatched files or directories were detected.";//NO I18N
            case 5:
                return "Some files were copied. Some files were mismatched.";//NO I18N
            case 6:
                return "Additional files and mismatched files exist. No files were copied.";//NO I18N
            case 7:
                return "Files were copied, a file mismatch was present, and additional files were present.";//NO I18N
            case 8:
                return "Several files did not copy.";//NO I18N
            default:
                return "Fatal error (exit code: " + exitCode + ")";//NO I18N
        }
    }

    /**
     * Format duration in human-readable format
     */
    private static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + " ms";//NO I18N
        } else if (ms < 60000) {
            return String.format("%.2f seconds", ms / 1000.0);//NO I18N
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%d minutes, %d seconds", minutes, seconds);//NO I18N
        }
    }

    /**
     * Resolve placeholders in path strings
     */
    private static String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // Handle the server_home placeholder
        if (path.contains("server_home")) {
            // Get the server home from system property
            String serverHome = System.getProperty("server.home");

            // If null, use a fallback approach
            if (serverHome == null) {
                serverHome = System.getProperty("user.dir");
            }

            LOGGER.info("Resolved server_home to: " + serverHome);//NO I18N
            path = path.replace("server_home", serverHome);//NO I18N
        }

        return path;
    }
}