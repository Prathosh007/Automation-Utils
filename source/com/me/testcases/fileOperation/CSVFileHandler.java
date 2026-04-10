package com.me.testcases.fileOperation;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.Operation;
import com.me.util.LogManager;

/**
 * Handler for CSV file operations
 */
public class CSVFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(CSVFileHandler.class.getName(), LogManager.LOG_TYPE.FW);

    /**
     * Execute a CSV file operation
     *
     * @param operation The operation containing parameters
     * @return true if operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation operation) {
        if (operation == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        String action = operation.getParameter("action");
        String filePath = operation.getParameter("file_path");

        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for CSV file operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for CSV file operation");
            return false;
        }

        // Create file object
        File file = new File(filePath);

        if (!file.exists()) {
            String errorMsg = "File not found: " + file.getAbsolutePath();
            LOGGER.warning(errorMsg);
            operation.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("CSV File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            boolean success;

            switch (action.toLowerCase()) {
                case "value_should_be_present":
                    success = checkValueExistence(operation, remarkBuilder, true);
                    break;
                case "value_should_be_removed":
                    success = checkValueExistence(operation, remarkBuilder, false);
                    break;
                default:
                    String errorMsg = "Unsupported action for CSV file: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing CSV file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }


    /**
     * Check if a value exists in a CSV file
     *
     * @param op Operation containing parameters
     * @param remarks StringBuilder to append operation remarks
     * @param shouldExist true if value should exist, false if it should not exist
     * @return true if existence check matches expectation, false otherwise
     */
    private static boolean checkValueExistence(Operation op, StringBuilder remarks, boolean shouldExist) {
        LOGGER.info("Executing CSV " + (shouldExist ? "value_should_be_present" : "value_should_be_removed") + " operation");

        String filePath = op.getParameter("file_path");
        String valueToCheck = op.getParameter("value");
        String delimiter = op.getParameter("delimiter");

        if (delimiter == null || delimiter.isEmpty()) {
            delimiter = ","; // Default delimiter
            LOGGER.info("Using default delimiter: comma");
        }

        LOGGER.info("Parameters - file_path: " + filePath + ", value to check: " + valueToCheck + ", delimiter: " + delimiter);

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("Missing required parameter: file_path");
            remarks.append("Error: file_path parameter is required");
            return false;
        }

        if (valueToCheck == null || valueToCheck.isEmpty()) {
            LOGGER.warning("Missing required parameter: value");
            remarks.append("Error: value parameter is required");
            return false;
        }

        File csvFile = new File(filePath);

        if (!csvFile.exists()) {
            LOGGER.warning("CSV file does not exist: " + filePath);
            remarks.append("Error: CSV file not found: ").append(filePath);
            return false;
        }

        if (!csvFile.isFile()) {
            LOGGER.warning("Path is not a file: " + filePath);
            remarks.append("Error: Path is not a file: ").append(filePath);
            return false;
        }

        if (!csvFile.canRead()) {
            LOGGER.warning("Cannot read CSV file: " + filePath);
            remarks.append("Error: Cannot read CSV file: ").append(filePath);
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            LOGGER.info("Loading CSV document: " + filePath);

            String line;
            int lineNumber = 0;
            boolean valueFound = false;
            StringBuilder searchDetails = new StringBuilder();

            while ((line = reader.readLine()) != null && !valueFound) {
                lineNumber++;

                // Check if the line contains the value directly
                if (line.contains(valueToCheck)) {
                    valueFound = true;
                    searchDetails.append("Found on line ").append(lineNumber);
                    break;
                }

                // Also check individual fields
                String[] fields = line.split(delimiter, -1);
                for (int i = 0; i < fields.length; i++) {
                    String field = fields[i].trim();
                    // Remove quotes if present
                    if (field.startsWith("\"") && field.endsWith("\"") && field.length() >= 2) {
                        field = field.substring(1, field.length() - 1);
                    }

                    if (field.contains(valueToCheck)) {
                        valueFound = true;
                        searchDetails.append("Found on line ").append(lineNumber)
                                .append(" in field ").append(i + 1);
                        break;
                    }
                }
            }

            boolean success = (valueFound == shouldExist);

            if (shouldExist) {
                // For value_should_be_present
                if (valueFound) {
                    remarks.append("Success: Value '").append(valueToCheck).append("' was found in the CSV file. ");
                    remarks.append(searchDetails);
                    op.setParameter("found", "true");
                } else {
                    remarks.append("Failed: Value '").append(valueToCheck).append("' was NOT found in the CSV file");
                    op.setParameter("found", "false");
                }
            } else {
                // For value_should_be_removed
                if (!valueFound) {
                    remarks.append("Success: Value '").append(valueToCheck).append("' was not found in the CSV file");
                    op.setParameter("removed", "true");
                } else {
                    remarks.append("Failed: Value '").append(valueToCheck).append("' was found. ");
                    remarks.append(searchDetails);
                    op.setParameter("removed", "false");
                }
            }

            return success;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO error processing CSV: " + e.getMessage(), e);
            remarks.append("Error: IO error: ").append(e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error processing CSV: " + e.getMessage(), e);
            remarks.append("Error: ").append(e.getMessage());
            return false;
        }
    }
}