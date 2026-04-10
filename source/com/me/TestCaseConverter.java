package com.me;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;

/**
 * Utility class to convert Excel (XLSX) or CSV test case files to JSON format
 */
public class TestCaseConverter {

    private static final Logger LOGGER = LogManager.getLogger(TestCaseConverter.class, LogManager.LOG_TYPE.FW);
    
    /**
     * Main method for command line execution
     * 
     * @param args Command line arguments: input_file output_file format
     *             format: "xlsx" or "csv" //NO I18N
     */
    public static void main(String[] args) {
        ConsoleOut.println("G.O.A.T - Test Case Converter"); //NO I18N
        ConsoleOut.println("============================"); //NO I18N
        
        if (args.length < 2) {
            ConsoleOut.println("Usage: java TestCaseConverter <input_file> <output_file> [format]"); //NO I18N
            ConsoleOut.println("  format: xlsx or csv (default: determined from file extension)"); //NO I18N
            System.exit(1);
        }
        
        String inputFile = args[0];
        String outputFile = args[1];
        String format = args.length > 2 ? args[2].toLowerCase() : null;
        
        // If format is not specified, try to determine from file extension
        if (format == null || format.isEmpty()) {
            if (inputFile.toLowerCase().endsWith(".xlsx")) { //NO I18N
                format = "xlsx"; //NO I18N
            } else if (inputFile.toLowerCase().endsWith(".csv")) { //NO I18N
                format = "csv"; //NO I18N
            } else {
                ConsoleOut.println("Error: Could not determine file format. Please specify format explicitly."); //NO I18N
                System.exit(1);
            }
        }
        
        try {
            File input = new File(inputFile);
            if (!input.exists()) {
                ConsoleOut.println("Error: Input file does not exist: " + inputFile); //NO I18N
                System.exit(1);
            }
            
            boolean success;
            if (format.equals("xlsx")) { //NO I18N
                ConsoleOut.println("Converting Excel file to JSON..."); //NO I18N
                success = convertExcelToJson(inputFile, outputFile);
            } else if (format.equals("csv")) { //NO I18N
                ConsoleOut.println("Converting CSV file to JSON..."); //NO I18N
                success = convertCsvToJson(inputFile, outputFile);
            } else {
                ConsoleOut.println("Error: Unsupported format: " + format); //NO I18N
                ConsoleOut.println("Supported formats: xlsx, csv"); //NO I18N
                System.exit(1);
                return;
            }
            
            if (success) {
                ConsoleOut.println("Conversion successful!"); //NO I18N
                ConsoleOut.println("JSON file saved to: " + outputFile); //NO I18N
            } else {
                ConsoleOut.println("Conversion failed. Check logs for details."); //NO I18N
                System.exit(1);
            }
            
        } catch (Exception e) {
            ConsoleOut.println("Error during conversion: " + e.getMessage()); //NO I18N
            LOGGER.log(Level.SEVERE, "Error during conversion", e); //NO I18N
            // Already logged with LOGGER.log above, no need to use printStackTrace
            System.exit(1);
        }
    }
    
    /**
     * Convert Excel (XLSX) file to JSON format
     * 
     * @param inputFile Path to input Excel file //NO I18N
     * @param outputFile Path to output JSON file //NO I18N
     * @return true if conversion was successful, false otherwise
     */
    public static boolean convertExcelToJson(String inputFile, String outputFile) {
        try (Workbook workbook = new XSSFWorkbook(new File(inputFile))) {
            Sheet sheet = workbook.getSheetAt(0);
            
            List<Map<String, String>> testCases = new ArrayList<>();
            
            // Extract header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                LOGGER.severe("Excel file has no header row"); //NO I18N
                return false;
            }
            
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    headers.add(getCellValueAsString(cell));
                } else {
                    headers.add("");
                }
            }
            
            // Map Excel column names to expected JSON field names
            Map<String, String> fieldMap = new HashMap<>();
            
            // Try to automatically map column headers to expected field names
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i).toLowerCase();
                
                if (header.contains("id") || header.equals("testcase_id") || header.equals("test case id")) { //NO I18N
                    fieldMap.put(headers.get(i), "id"); //NO I18N
                }
                else if (header.contains("testcase") || header.contains("test case") || header.contains("description")) { //NO I18N
                    fieldMap.put(headers.get(i), "testcase"); //NO I18N
                }
                else if (header.contains("steps") || header.contains("operation") || header.contains("procedure")) { //NO I18N
                    fieldMap.put(headers.get(i), "steps"); //NO I18N
                }
                else if (header.contains("expect") || header.contains("result")) { //NO I18N
                    fieldMap.put(headers.get(i), "expectedResult"); //NO I18N
                }
            }
            
            // Process data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                // Skip rows with all empty cells
                boolean hasData = false;
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                        hasData = true;
                        break;
                    }
                }
                if (!hasData) {
                    continue;
                }
                
                Map<String, String> testCase = new HashMap<>();
                
                // Add test case data using mapping
                for (int j = 0; j < headers.size(); j++) {
                    String header = headers.get(j);
                    if (header.isEmpty()) {
                        continue;
                    }
                    
                    Cell dataCell = row.getCell(j);
                    String value = (dataCell != null) ? getCellValueAsString(dataCell) : "";
                    
                    // Map to expected field name
                    String fieldName = fieldMap.getOrDefault(header, null);
                    if (fieldName != null) {
                        testCase.put(fieldName, value);
                    }
                }
                
                testCases.add(testCase);
            }
            
            return saveAsJsonArray(testCases, outputFile);
            
        } catch (IOException | InvalidFormatException e) {
            LOGGER.log(Level.SEVERE, "Error processing Excel file", e); //NO I18N
            return false;
        }
    }
    
    
    /**
     * Convert CSV file to JSON format
     * 
     * @param inputFile Path to input CSV file //NO I18N
     * @param outputFile Path to output JSON file //NO I18N
     * @return true if conversion was successful, false otherwise
     */
    public static boolean convertCsvToJson(String inputFile, String outputFile) {
        try (Reader reader = Files.newBufferedReader(Paths.get(inputFile));
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {
            
            List<String[]> rows = csvReader.readAll();
            if (rows.isEmpty()) {
                LOGGER.severe("CSV file is empty"); //NO I18N
                return false;
            }
            
            // Extract header row
            String[] headerRow = rows.get(0);
            List<String> headers = Arrays.asList(headerRow);
            
            // Map CSV column names to expected JSON field names
            Map<String, String> fieldMap = new HashMap<>();
            
            // Try to automatically map column headers to expected field names
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i).toLowerCase();
                
                if (header.contains("id") || header.equals("testcase_id") || header.equals("test case id")) { //NO I18N
                    fieldMap.put(headers.get(i), "id"); //NO I18N
                }
                else if (header.contains("testcase") || header.contains("test case") || header.contains("description")) { //NO I18N
                    fieldMap.put(headers.get(i), "testcase"); //NO I18N
                }
                else if (header.contains("steps") || header.contains("operation") || header.contains("procedure")) { //NO I18N
                    fieldMap.put(headers.get(i), "steps"); //NO I18N
                }
                else if (header.contains("expect") || header.contains("result")) { //NO I18N
                    fieldMap.put(headers.get(i), "expectedResult"); //NO I18N
                }
            }
            
            // Process data rows
            List<Map<String, String>> testCases = new ArrayList<>();
            
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                
                // Skip rows with all empty cells
                boolean hasData = false;
                for (String cell : row) {
                    if (cell != null && !cell.trim().isEmpty()) {
                        hasData = true;
                        break;
                    }
                }
                if (!hasData) {
                    continue;
                }
                
                Map<String, String> testCase = new HashMap<>();
                
                // Add test case data using mapping
                for (int j = 0; j < Math.min(row.length, headerRow.length); j++) {
                    String header = headerRow[j];
                    if (header.isEmpty()) {
                        continue;
                    }
                    
                    String value = j < row.length ? row[j] : "";
                    
                    // Map to expected field name
                    String fieldName = fieldMap.getOrDefault(header, null);
                    if (fieldName != null) {
                        testCase.put(fieldName, value);
                    }
                }
                
                testCases.add(testCase);
            }
            
            // Convert to JSON
            return saveAsJsonArray(testCases, outputFile);
            
        } catch (IOException | CsvException e) {
            LOGGER.log(Level.SEVERE, "Error processing CSV file", e); //NO I18N
            return false;
        }
    }
    
    /**
     * Save test cases as a JSON array
     * 
     * @param testCases List of test case maps //NO I18N
     * @param outputFile Path to output JSON file //NO I18N
     * @return true if successful, false otherwise
     */
    private static boolean saveAsJsonArray(List<Map<String, String>> testCases, String outputFile) {
        try {
            JsonArray jsonArray = new JsonArray();
            
            for (Map<String, String> testCase : testCases) {
                JsonObject testCaseObject = new JsonObject();
                
                // Add the required fields in the expected format
                if (testCase.containsKey("id")) { //NO I18N
                    testCaseObject.addProperty("id", testCase.get("id")); //NO I18N
                }
                
                if (testCase.containsKey("testcase")) { //NO I18N
                    testCaseObject.addProperty("testcase", testCase.get("testcase")); //NO I18N
                }
                
                if (testCase.containsKey("steps")) { //NO I18N
                    testCaseObject.addProperty("steps", testCase.get("steps")); //NO I18N
                }
                
                if (testCase.containsKey("expectedResult")) { //NO I18N
                    testCaseObject.addProperty("expectedResult", testCase.get("expectedResult")); //NO I18N
                }
                
                jsonArray.add(testCaseObject);
            }
            
            // Write to file with pretty printing
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create();
            
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(gson.toJson(jsonArray));
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error converting to JSON", e); //NO I18N
            return false;
        }
    }
    
    /**
     * Get cell value as string, handling different cell types
     * 
     * @param cell Cell to get value from //NO I18N
     * @return String representation of cell value
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double value = cell.getNumericCellValue();
                    // Check if it's an integer
                    if (value == Math.floor(value)) {
                        return String.format("%.0f", value);//NO I18N
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception e2) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }
}
