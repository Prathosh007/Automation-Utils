package com.me.testcases;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.Operation;
import com.me.util.LogManager;

/**
 * Handles execution of batch files for the run_bat operation type
 */
public class BatchFileExecutor {
    private static final Logger LOGGER = LogManager.getLogger(BatchFileExecutor.class, LogManager.LOG_TYPE.FW);
    
    /**
     * Execute a batch file with optional arguments
     * 
     * @param op The operation containing batch file details
     * @return true if execution was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.severe("Operation is null");//NO I18N
            return false;
        }
        
        String batFile = op.getParameter("bat_file");//NO I18N
        String batFilePath = op.getParameter("bat_file_path");//NO I18N
        String arguments = op.getParameter("arguments");//NO I18N
        
        if (batFile == null || batFile.isEmpty()) {
            LOGGER.severe("Batch file name is missing");//NO I18N
            return false;
        }
        
        // Resolve path (handle server_home if present)
        if (batFilePath == null) {
            batFilePath = "";
        }
        
        String resolvedPath = ServerUtils.resolvePath(batFilePath);
        String fullPath;
        
        if (resolvedPath.trim().isEmpty()) {
            fullPath = batFile;
        } else {
            // Ensure path ends with separator
            if (!resolvedPath.endsWith(File.separator)) {
                resolvedPath += File.separator;
            }
            fullPath = resolvedPath + batFile;
        }
        
        // Check if the batch file exists
        File batchFile = new File(fullPath);
        if (!batchFile.exists()) {
            LOGGER.severe("Batch file does not exist: " + fullPath);//NO I18N
            return false;
        }
        
        if (!batchFile.canExecute()) {
            LOGGER.warning("Batch file may not have execute permissions: " + fullPath);//NO I18N
        }
        
        // Build the command
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");//NO I18N
        command.add("/c");//NO I18N
        command.add(fullPath);
        
        // Add arguments if present
        if (arguments != null && !arguments.isEmpty()) {
            // Split arguments by space, respecting quotes
            boolean inQuotes = false;
            StringBuilder arg = new StringBuilder();
            for (int i = 0; i < arguments.length(); i++) {
                char c = arguments.charAt(i);
                if (c == '"') {
                    inQuotes = !inQuotes;
                    arg.append(c);
                } else if (c == ' ' && !inQuotes) {
                    if (arg.length() > 0) {
                        command.add(arg.toString());
                        arg = new StringBuilder();
                    }
                } else {
                    arg.append(c);
                }
            }
            if (arg.length() > 0) {
                command.add(arg.toString());
            }
        }
        
        LOGGER.info("Executing batch file: " + fullPath);//NO I18N
        if (arguments != null && !arguments.isEmpty()) {
            LOGGER.info("With arguments: " + arguments);//NO I18N
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(resolvedPath)); // Set working directory
            pb.redirectErrorStream(true); // Merge stdout and stderr
            
            // Start the process
            Process process = pb.start();
            
            // Capture output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[Batch Output] " + line);//NO I18N
                }
            }
            
            // Wait for process to complete and get exit code
            int exitCode = process.waitFor();
            LOGGER.info("Batch file execution completed with exit code: " + exitCode);//NO I18N
            
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error executing batch file", e);//NO I18N
            return false;
        }
    }
    
    /**
     * Check if the batch file exists without executing it
     * 
     * @param op The operation containing batch file details
     * @return true if the batch file exists, false otherwise
     */
    public static boolean checkBatchFileExists(Operation op) {
        if (op == null) {
            return false;
        }
        
        String batFile = op.getParameter("bat_file");//NO I18N
        String batFilePath = op.getParameter("bat_file_path");//NO I18N
        
        if (batFile == null || batFile.isEmpty()) {
            return false;
        }
        
        // Resolve path (handle server_home if present)
        if (batFilePath == null) {
            batFilePath = "";
        }
        
        String resolvedPath = ServerUtils.resolvePath(batFilePath);
        String fullPath;
        
        if (resolvedPath.trim().isEmpty()) {
            fullPath = batFile;
        } else {
            // Ensure path ends with separator
            if (!resolvedPath.endsWith(File.separator)) {
                resolvedPath += File.separator;
            }
            fullPath = resolvedPath + batFile;
        }
        
        // Check if the batch file exists
        File batchFile = new File(fullPath);
        return batchFile.exists();
    }
}
