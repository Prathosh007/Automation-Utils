package com.me.testcases.fileOperation;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.Operation;
import com.me.util.LogManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;

/**
 * Handler for XLSX file operations
 */
public class XLSXFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(XLSXFileHandler.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute an XLSX file operation
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
            LOGGER.warning("Action is required for XLSX file operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for XLSX file operation");
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
        remarkBuilder.append("XLSX File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            boolean success;

            switch (action.toLowerCase()) {
                case "value_should_be_present":
                    success = handleValueShouldBePresent(operation, remarkBuilder);
                    break;
                case "value_should_be_removed":
                    success = handleValueShouldBeRemoved(operation, remarkBuilder);
                    break;
                default:
                    String errorMsg = "Unsupported action for XLSX file: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing XLSX file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }
    /**
     * Handle 'value_should_be_present' operation
     */
    private static boolean handleValueShouldBePresent(Operation op, StringBuilder remarks) {
        LOGGER.info("Executing XLSX value_should_be_present operation");

        String filePath = op.getParameter("file_path");
        String valueToFind = op.getParameter("value");

        LOGGER.info("Parameters - file_path: " + filePath + ", value to find: " + valueToFind);

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("Missing required parameter: file_path");
            remarks.append("Error: file_path parameter is required");
            return false;
        }

        if (valueToFind == null || valueToFind.isEmpty()) {
            LOGGER.warning("Missing required parameter: value");
            remarks.append("Error: value parameter is required");
            return false;
        }

        File xlsxFile = new File(filePath);

        if (!xlsxFile.exists()) {
            LOGGER.warning("XLSX file does not exist: " + filePath);
            remarks.append("Error: XLSX file not found: ").append(filePath);
            return false;
        }

        if (!xlsxFile.isFile()) {
            LOGGER.warning("Path is not a file: " + filePath);
            remarks.append("Error: Path is not a file: ").append(filePath);
            return false;
        }

        if (!xlsxFile.canRead()) {
            LOGGER.warning("Cannot read XLSX file: " + filePath);
            remarks.append("Error: Cannot read XLSX file: ").append(filePath);
            return false;
        }

        Workbook workbook = null;
        try {
            LOGGER.info("Loading XLSX document: " + filePath);
            workbook = WorkbookFactory.create(xlsxFile);
            LOGGER.info("XLSX document loaded successfully, sheet count: " + workbook.getNumberOfSheets());

            boolean valueFound = false;
            StringBuilder searchDetails = new StringBuilder();

            // Search through all sheets
            for (int i = 0; i < workbook.getNumberOfSheets() && !valueFound; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = workbook.getSheetName(i);
                LOGGER.info("Searching sheet: " + sheetName);

                // Iterate through all rows and cells in the sheet
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String cellValue = "";

                        // Extract cell value based on type
                        switch (cell.getCellType()) {
                            case STRING:
                                cellValue = cell.getStringCellValue();
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    cellValue = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cell.getDateCellValue());
                                } else {
                                    cellValue = String.valueOf(cell.getNumericCellValue());
                                }
                                break;
                            case BOOLEAN:
                                cellValue = String.valueOf(cell.getBooleanCellValue());
                                break;
                            case FORMULA:
                                try {
                                    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                                    CellValue cellValue2 = evaluator.evaluate(cell);
                                    switch (cellValue2.getCellType()) {
                                        case STRING:
                                            cellValue = cellValue2.getStringValue();
                                            break;
                                        case NUMERIC:
                                            if (DateUtil.isCellDateFormatted(cell)) {
                                                cellValue = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cell.getDateCellValue());
                                            } else {
                                                cellValue = String.valueOf(cellValue2.getNumberValue());
                                            }
                                            break;
                                        case BOOLEAN:
                                            cellValue = String.valueOf(cellValue2.getBooleanValue());
                                            break;
                                        default:
                                            cellValue = "";
                                    }
                                } catch (Exception e) {
                                    cellValue = cell.getCellFormula();
                                }
                                break;
                            default:
                                cellValue = "";
                        }

                        // Check if value is found
                        if (cellValue.contains(valueToFind)) {
                            valueFound = true;
                            CellReference cellRef = new CellReference(row.getRowNum(), cell.getColumnIndex());
                            searchDetails.append("Found in sheet '").append(sheetName)
                                    .append("' at cell ").append(cellRef.formatAsString());
                            break;
                        }
                    }
                    if (valueFound) break;
                }
            }

            if (valueFound) {
                remarks.append("Success: Value '").append(valueToFind).append("' was found in the XLSX file. ");
                remarks.append(searchDetails);
                op.setParameter("found", "true");
                return true;
            } else {
                remarks.append("Failed: Value '").append(valueToFind).append("' was NOT found in the XLSX file");
                op.setParameter("found", "false");
                return false;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO error processing XLSX: " + e.getMessage(), e);
            remarks.append("Error: IO error: ").append(e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error processing XLSX: " + e.getMessage(), e);
            remarks.append("Error: ").append(e.getMessage());
            return false;
        } finally {
            if (workbook != null) {
                try {
                    LOGGER.info("Closing XLSX document");
                    workbook.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing XLSX document: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Handle 'value_should_be_removed' operation
     */
    private static boolean handleValueShouldBeRemoved(Operation op, StringBuilder remarks) {
        LOGGER.info("Executing XLSX value_should_be_removed operation");

        String filePath = op.getParameter("file_path");
        String valueToCheck = op.getParameter("value");

        LOGGER.info("Parameters - file_path: " + filePath + ", value to check: " + valueToCheck);

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

        File xlsxFile = new File(filePath);

        if (!xlsxFile.exists()) {
            LOGGER.warning("XLSX file does not exist: " + filePath);
            remarks.append("Error: XLSX file not found: ").append(filePath);
            return false;
        }

        if (!xlsxFile.isFile()) {
            LOGGER.warning("Path is not a file: " + filePath);
            remarks.append("Error: Path is not a file: ").append(filePath);
            return false;
        }

        if (!xlsxFile.canRead()) {
            LOGGER.warning("Cannot read XLSX file: " + filePath);
            remarks.append("Error: Cannot read XLSX file: ").append(filePath);
            return false;
        }

        Workbook workbook = null;
        try {
            LOGGER.info("Loading XLSX document: " + filePath);
            workbook = WorkbookFactory.create(xlsxFile);
            LOGGER.info("XLSX document loaded successfully, sheet count: " + workbook.getNumberOfSheets());

            boolean valueFound = false;
            StringBuilder searchDetails = new StringBuilder();

            // Search through all sheets
            for (int i = 0; i < workbook.getNumberOfSheets() && !valueFound; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = workbook.getSheetName(i);
                LOGGER.info("Searching sheet: " + sheetName);

                // Iterate through all rows and cells in the sheet
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String cellValue = "";

                        // Extract cell value based on type
                        switch (cell.getCellType()) {
                            case STRING:
                                cellValue = cell.getStringCellValue();
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    cellValue = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cell.getDateCellValue());
                                } else {
                                    cellValue = String.valueOf(cell.getNumericCellValue());
                                }
                                break;
                            case BOOLEAN:
                                cellValue = String.valueOf(cell.getBooleanCellValue());
                                break;
                            case FORMULA:
                                try {
                                    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                                    CellValue cellValue2 = evaluator.evaluate(cell);
                                    switch (cellValue2.getCellType()) {
                                        case STRING:
                                            cellValue = cellValue2.getStringValue();
                                            break;
                                        case NUMERIC:
                                            if (DateUtil.isCellDateFormatted(cell)) {
                                                cellValue = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cell.getDateCellValue());
                                            } else {
                                                cellValue = String.valueOf(cellValue2.getNumberValue());
                                            }
                                            break;
                                        case BOOLEAN:
                                            cellValue = String.valueOf(cellValue2.getBooleanValue());
                                            break;
                                        default:
                                            cellValue = "";
                                    }
                                } catch (Exception e) {
                                    cellValue = cell.getCellFormula();
                                }
                                break;
                            default:
                                cellValue = "";
                        }

                        // Check if value is found
                        if (cellValue.contains(valueToCheck)) {
                            valueFound = true;
                            CellReference cellRef = new CellReference(row.getRowNum(), cell.getColumnIndex());
                            searchDetails.append("Found in sheet '").append(sheetName)
                                    .append("' at cell ").append(cellRef.formatAsString());
                            break;
                        }
                    }
                    if (valueFound) break;
                }
            }

            if (!valueFound) {
                LOGGER.info("Value '" + valueToCheck + "' was not found in the XLSX file (as expected)");
                remarks.append("Success: Value '").append(valueToCheck).append("' was not found in the XLSX file");
                op.setParameter("removed", "true");
                return true;
            } else {
                LOGGER.warning("Value '" + valueToCheck + "' was not found");
                remarks.append("Failed: Value '").append(valueToCheck).append("' was not found. ");
                remarks.append(searchDetails);
                op.setParameter("removed", "false");
                return false;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO error processing XLSX: " + e.getMessage(), e);
            remarks.append("Error: IO error: ").append(e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error processing XLSX: " + e.getMessage(), e);
            remarks.append("Error: ").append(e.getMessage());
            return false;
        } finally {
            if (workbook != null) {
                try {
                    LOGGER.info("Closing XLSX document");
                    workbook.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing XLSX document: " + e.getMessage(), e);
                }
            }
        }
    }
}
