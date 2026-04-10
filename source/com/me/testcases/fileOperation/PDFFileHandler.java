package com.me.testcases.fileOperation;


import com.me.Operation;
import com.me.util.LogManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

/**
 * Handler for PDF file operations
 */
public class PDFFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(PDFFileHandler.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute a PDF file operation
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
            LOGGER.warning("Action is required for PDF file operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for PDF file operation");
            return false;
        }

        // Create file object
        File file = new File(filePath);

        // For create action, we don't need the file to exist
        if (!file.exists() && !action.equalsIgnoreCase("create")) {
            String errorMsg = "File not found: " + file.getAbsolutePath();
            LOGGER.warning(errorMsg);
            operation.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("PDF File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            boolean success;

            switch (action.toLowerCase()) {
                case "value_should_be_present":
                    success = handleValueShouldBePresenceAndAbsence(operation, remarkBuilder,true);
                    break;
                case "value_should_be_removed":
                    success = handleValueShouldBePresenceAndAbsence(operation, remarkBuilder, false);
                    break;
                default:
                    String errorMsg = "Unsupported action for PDF file: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing PDF file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }
    /**
     * Handle 'value_should_be_present' operation
     */
    private static boolean handleValueShouldBePresenceAndAbsence(Operation op, StringBuilder remarks, boolean shouldExist) {
        LOGGER.info("Executing PDF value_should_be_present operation");//NO I18N

        String pdfPath = op.getParameter("file_path");
        String valueToFind = op.getParameter("exact_value");
        String key = op.getParameter("key");
        String regexPattern = op.getParameter("value");

        LOGGER.info("Parameters - file_path: " + pdfPath + ", value to find: " + valueToFind +
                ", key: " + key + ", regex: " + regexPattern);//NO I18N

        if (pdfPath == null || pdfPath.isEmpty()) {
            LOGGER.warning("Missing required parameter: file_path");//NO I18N
            remarks.append("Error: file_path parameter is required");
            return false;
        }

        if ((valueToFind == null || valueToFind.isEmpty()) && regexPattern == null) {
            LOGGER.warning("Missing required parameter: value or regex");//NO I18N
            remarks.append("Error: value or regex parameter is required");
            return false;
        }

        File pdfFile = new File(pdfPath);
        LOGGER.info("Checking PDF file: " + pdfPath);//NO I18N

        if (!pdfFile.exists() || !pdfFile.isFile() || !pdfFile.canRead()) {
            LOGGER.warning("PDF file issue: " + pdfPath);//NO I18N
            remarks.append("Error: PDF file not found or unreadable: ").append(pdfPath);
            return false;
        }

        boolean success = false;
        String foundValue = null;
        PDDocument document = null;
        try {
            LOGGER.info("Loading PDF document: " + pdfPath);//NO I18N
            document = PDDocument.load(pdfFile);
            LOGGER.info("PDF document loaded successfully, page count: " + document.getNumberOfPages());//NO I18N

            PDFTextStripper stripper = new PDFTextStripper();
            LOGGER.info("Extracting text from PDF");//NO I18N
            String pdfText = stripper.getText(document);
            LOGGER.info("PDF content extracted, text length: " + pdfText.length());//NO I18N

            boolean valueFound = false;

            if (key != null && !key.isEmpty()) {
                // Key-value pair mode: search by key first
                LOGGER.info("Searching for key: " + key);//NO I18N
                String[] lines = pdfText.split("\n");

                for (String line : lines) {
                    if (line.contains(key)) {
                        LOGGER.info("Found line with key: " + line);//NO I18N

                        // Check value in the line that contains the key
                        if (regexPattern != null) {
                            // Use regex pattern to find a match in the line
                            Pattern pattern = Pattern.compile(regexPattern);
                            Matcher matcher = pattern.matcher(line);
                            LOGGER.info("Line to match regex: " + line);//NO I18N

                            if (matcher.find()) {
                                foundValue = matcher.group();
                                valueFound = true;
                                LOGGER.info("Regex pattern match found: " + foundValue);//NO I18N
                            }
                        } else if (valueToFind != null) {
                            // Use simple text matching
                            valueFound = line.contains(valueToFind);
                        }

                        if (valueFound) {
                            break;
                        }
                    }
                }
            } else {
                // Search in the entire document
                if (regexPattern != null) {
                    Pattern pattern = Pattern.compile(regexPattern);
                    Matcher matcher = pattern.matcher(pdfText);
                    valueFound = matcher.find();
                } else {
                    valueFound = pdfText.contains(valueToFind);
                }
            }

            if (foundValue == null && valueFound && valueToFind != null) {
                // If we found a match but didn't capture it, use the valueToFind or regex match
                foundValue = valueToFind;
                op.setParameter("Found_output_value", foundValue);
                saveNote(op, foundValue);
            } else if (foundValue != null) {
                op.setParameter("Found_output_value", foundValue);
                saveNote(op, foundValue);
            }

            // Check results against expectations
            if (shouldExist == valueFound) {
                String resultMsg = shouldExist ?
                        "Value " + (regexPattern != null ? "matching regex '" + regexPattern + "'" : "'" + valueToFind + "'") + " was found" :
                        "Value " + (regexPattern != null ? "matching regex '" + regexPattern + "'" : "'" + valueToFind + "'") + " was NOT found (as expected)";

                if (key != null) {
                    resultMsg += " in line with key '" + key + "'";
                }

                remarks.append("Success: ").append(resultMsg);
                LOGGER.info(resultMsg);//NO I18N
                success = true;
            } else {
                String resultMsg = shouldExist ?
                        "Value " + (regexPattern != null ? "matching regex '" + regexPattern + "'" : "'" + valueToFind + "'") + " was found" :
                        "Value " + (regexPattern != null ? "matching regex '" + regexPattern + "'" : "'" + valueToFind + "'") + " was not found";

                if (key != null) {
                    resultMsg += " in line with key '" + key + "'";
                }

                remarks.append("Failed: ").append(resultMsg);
                LOGGER.info(resultMsg);//NO I18N
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO error processing PDF: " + e.getMessage(), e);//NO I18N
            remarks.append("Error: IO error: ").append(e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing PDF: " + e.getMessage(), e);//NO I18N
            remarks.append("Error: ").append(e.getMessage());
            return false;
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing PDF document", e);//NO I18N
                }
            }
        }
        return success;
    }
    /**
     * Handle 'value_should_be_removed' operation
     */
//    private static boolean handleValueShouldBeRemoved(Operation op, StringBuilder remarks) throws IOException {
//        LOGGER.info("Executing PDF value_should_be_removed operation");//NO I18N
//
//        String pdfPath = op.getParameter("file_path");  // Changed from "pdf_path" to "file_path"
//        String valueToCheck = op.getParameter("value");
//
//        LOGGER.info("Parameters - file_path: " + pdfPath + ", value to check: " + valueToCheck);//NO I18N
//
//        if (pdfPath == null || pdfPath.isEmpty()) {
//            LOGGER.warning("Missing required parameter: file_path");//NO I18N
//            remarks.append("Error: file_path parameter is required");
//            return false;
//        }
//
//        if (valueToCheck == null || valueToCheck.isEmpty()) {
//            LOGGER.warning("Missing required parameter: value");//NO I18N
//            remarks.append("Error: value parameter is required");
//            return false;
//        }
//
//        File pdfFile = new File(pdfPath);
//        LOGGER.info("Resolved PDF path: " + pdfPath);//NO I18N
//
//        if (!pdfFile.exists() || !pdfFile.isFile()) {
//            LOGGER.warning("PDF file not found: " + pdfPath);//NO I18N
//            remarks.append("Error: PDF file not found: ").append(pdfPath);
//            return false;
//        }
//
//        PDDocument document = null;
//        try {
//            LOGGER.info("Loading PDF document: " + pdfPath);//NO I18N
//            document = PDDocument.load(pdfFile);
//            LOGGER.info("PDF document loaded successfully");//NO I18N
//
//            PDFTextStripper stripper = new PDFTextStripper();
//            LOGGER.info("Extracting text from PDF");//NO I18N
//            String pdfText = stripper.getText(document);
//            LOGGER.info("PDF content extracted successfully, text length: " + pdfText.length());//NO I18N
//
//            boolean valueFound = pdfText.contains(valueToCheck);
//
//            if (!valueFound) {
//                LOGGER.info("Value '" + valueToCheck + "' was not found in the PDF (as expected)");//NO I18N
//                remarks.append("Success: Value '").append(valueToCheck).append("' was not found in the PDF");
//                op.setParameter("removed", "true");
//                return true;
//            } else {
//                LOGGER.warning("Value '" + valueToCheck + "' was found in the PDF but should be removed");//NO I18N
//                remarks.append("Failed: Value '").append(valueToCheck).append("' was found in the PDF but should be removed");
//                op.setParameter("removed", "false");
//                return false;
//            }
//        } catch (Exception exception){
//            LOGGER.log(Level.SEVERE, "Error processing PDF file: " + exception.getMessage(), exception);
//            remarks.append("Error: ").append(exception.getMessage());
//            return false;
//        } finally {
//            if (document != null) {
//                try {
//                    LOGGER.info("Closing PDF document");//NO I18N
//                    document.close();
//                } catch (IOException e) {
//                    LOGGER.log(Level.WARNING, "Error closing PDF document: " + e.getMessage(), e);//NO I18N
//                }
//            }
//        }
//    }

}