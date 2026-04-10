package com.me;
import com.me.util.LogManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.opencsv.CSVReader;
import java.io.FileReader;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.annotations.Expose;
import java.io.FileWriter;

public class TestCaseReader {
    private static final Logger LOGGER = LogManager.getLogger(TestCaseReader.class.getName(), LogManager.LOG_TYPE.FW);

    /**
     * Simple class for JSON serialization without operations and productName
     */
    private static class SimpleTestCase {
        private String id;
        private String testcase;
        private String steps;
        private String expectedResult;
        
        public SimpleTestCase(TestCase testCase) {
            this.id = testCase.getId();
            this.testcase = testCase.getTestcase();
            this.steps = testCase.getSteps();
            this.expectedResult = testCase.getExpectedResult();
        }
    }

    public static List<TestCase> readTestCasesFromExcel(String filePath) throws IOException {
        List<TestCase> testCases = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            LOGGER.info("Reading test cases from Excel file: " + filePath);
            
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header row
                
                try {
                    String id = getCellValueAsString(row, 0);
                    String testcase = getCellValueAsString(row, 1);
                    String steps = getCellValueAsString(row, 2);
                    String expectedResult = getCellValueAsString(row, 3);
                  
                    LOGGER.info("Read test case: ID=" + id);
                    
                    // Create test case without product name (will be added by LLAMA later)
                    testCases.add(new TestCase(id, testcase, steps, expectedResult));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error reading row " + row.getRowNum(), e);
                }
            }
        }
        
        LOGGER.info("Successfully read " + testCases.size() + " test cases from Excel");
        return testCases;
    }

    public static List<TestCase> readTestCasesFromCsv(String filePath) throws IOException, com.opencsv.exceptions.CsvValidationException {
        List<TestCase> testCases = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] line;
            boolean isFirstLine = true;
            
            LOGGER.info("Reading test cases from CSV file: " + filePath);
            
            while ((line = reader.readNext()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header row
                }
                
                try {
                    if (line.length < 4) {
                        LOGGER.warning("Invalid CSV line format. Expected at least 4 columns, got " + line.length);
                        continue;
                    }
                    
                    String id = line[0];
                    String testcase = line[1];
                    String steps = line[2];
                    String expectedResult = line[3];
                    
                    LOGGER.info("Read test case: ID=" + id);
                    
                    // Create test case without product name (will be added by LLAMA later)
                    testCases.add(new TestCase(id, testcase, steps, expectedResult));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error reading CSV line", e);
                }
            }
        }
        
        LOGGER.info("Successfully read " + testCases.size() + " test cases from CSV");
        return testCases;
    }
    
    /**
     * Convert test cases to JSON without operations and productName
     * @param testCases The list of test cases to convert
     * @param outputFile The file to write the JSON to
     * @throws IOException If an I/O error occurs
     */
    public static void convertToJson(List<TestCase> testCases, String outputFile) throws IOException {
        LOGGER.info("Converting " + testCases.size() + " test cases to JSON");
        
        // Convert to simple format without operations and productName
        List<SimpleTestCase> simpleTestCases = new ArrayList<>();
        for (TestCase testCase : testCases) {
            simpleTestCases.add(new SimpleTestCase(testCase));
        }
        
        // Create JSON with pretty printing
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(simpleTestCases);
        
        // Write to output file
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(json);
        }
        
        LOGGER.info("JSON written to " + outputFile);
    }
    
    private static String getCellValueAsString(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((int)cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
