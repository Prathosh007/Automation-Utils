package com.me.testcases;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.adventnet.mfw.ConsoleOut;
import com.me.Operation;
import com.me.util.LogManager;

/**
 * Utility for file read operations
 */
public class FileReaderUtil {
    private static final Logger LOGGER = LogManager.getLogger(FileReaderUtil.class, LogManager.LOG_TYPE.FW);
    private static String lastOperationRemarks;
    public static String currentTime; //NO I18N

    /**
     * Check if a file or folder is present
     *
     * @param filePath    Path to look in
     * @param fileName    Name of file or folder to check
     * @param shouldExist Whether the file should exist or not
     * @return true if the file check matches expectation, false otherwise
     */
    public static boolean checkFileFolderPresence(Operation operation, String filePath, String fileName, boolean shouldExist) {
        StringBuilder remarks = new StringBuilder();
        try {
            File fileToCheck;
            File directory = new File(filePath);
            if (fileName == null){
                fileToCheck = new File(directory, ""); //NO I18N
            }else {
                fileToCheck = new File(directory, fileName);
            }
            boolean exists = fileToCheck.exists();

            if (shouldExist) {
                if (exists) {
                    remarks.append("✓ Found: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N

                    // Add more detailed information about the file
                    remarks.append("  Type: ").append(fileToCheck.isDirectory() ? "Directory" : "File").append("\n"); //NO I18N

                    if (fileToCheck.isFile()) {
                        remarks.append("  Size: ").append(formatFileSize(fileToCheck.length())).append("\n"); //NO I18N
                        remarks.append("  Last modified: ").append(formatDate(fileToCheck.lastModified())).append("\n"); //NO I18N

                        // Add basic content type detection
                        remarks.append("  Content type: ").append(detectContentType(fileName)).append("\n"); //NO I18N
                    }

                    if (fileToCheck.isDirectory()) {
                        // Count files in directory
                        File[] files = fileToCheck.listFiles();
                        if (files != null) {
                            remarks.append("  Contains: ").append(files.length).append(" files/directories\n"); //NO I18N
                        }
                    }

                    LOGGER.info(remarks.toString());
                    operation.setRemarks(remarks.toString()); //NO I18N
                    return true;
                } else {
                    remarks.append("✗ Not found: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N

                    if (directory.exists()) {
                        remarks.append("  Directory contents:\n"); //NO I18N
                        File[] files = directory.listFiles();
                        if (files != null && files.length > 0) {
                            for (int i = 0; i < Math.min(files.length, 5); i++) {
                                remarks.append("    - ").append(files[i].getName()).append("\n"); //NO I18N
                            }
                            if (files.length > 5) {
                                remarks.append("    - ... (").append(files.length - 5).append(" more files)\n"); //NO I18N
                            }
                        } else {
                            remarks.append("    (empty directory)\n"); //NO I18N
                        }
                    }

                    LOGGER.warning(remarks.toString());
                    operation.setRemarks(remarks.toString()); //NO I18N
                    return false;
                }
            } else {
                // Check for absence
                if (!exists) {
                    remarks.append("✓ Confirmed absence: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N

                    LOGGER.info(remarks.toString());
                    operation.setRemarks(remarks.toString()); //NO I18N
                    return true;
                } else {
                    remarks.append("✗ File exists but should not: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N

                    // Add details about the file that shouldn't exist
                    remarks.append("  Type: ").append(fileToCheck.isDirectory() ? "Directory" : "File").append("\n"); //NO I18N

                    if (fileToCheck.isFile()) {
                        remarks.append("  Size: ").append(formatFileSize(fileToCheck.length())).append("\n"); //NO I18N
                        remarks.append("  Last modified: ").append(formatDate(fileToCheck.lastModified())).append("\n"); //NO I18N
                    }

                    LOGGER.warning(remarks.toString());
                    operation.setRemarks(remarks.toString()); //NO I18N
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking file presence", e); //NO I18N
            remarks.append("Error checking file presence: ").append(e.getMessage()).append("\n"); //NO I18N
            operation.setRemarks(remarks.toString()); //NO I18N
            return false;
        }
    }

//    /**
//     * Check if a value is present in a file
//     *
//     * @param filePath    Path to the file's directory
//     * @param fileName    Name of the file to check
//     * @param value       Value to check for
//     * @param shouldExist Whether the value should exist or not
//     * @return true if the value check matches expectation, false otherwise
//     */
//    public static boolean checkValueInFile(String filePath, String fileName, String value, boolean shouldExist) {
//        try {
//            File directory = new File(filePath);
//            File fileToCheck = new File(directory, fileName);
//
//            if (!fileToCheck.exists()) {
//                lastOperationRemarks = "File not found: " + fileToCheck.getAbsolutePath(); //NO I18N
//                LOGGER.warning(lastOperationRemarks);
//                return false;
//            }
//
//            // Read the file content
//            String fileContent = "";
//            boolean containsValue = false;
//
//            StringBuilder remarks = new StringBuilder();
//
//            if (shouldExist) {
//
//                currentTime = buildDateTime(); //NO I18N
//                LOGGER.warning("Current time: " + currentTime); //NO I18N
////                currentTime = "[10:29:11:036]|[04-21-2025]"; //NO I18N
//                String theSearchValue = extractValueFromLog(String.valueOf(fileToCheck), currentTime, value, ""); //NO I18N
//
//                if (theSearchValue.equals("") || theSearchValue == null) { //NO I18N
//                    LOGGER.info("Checking for value in file: " + fileToCheck.getAbsolutePath()); //NO I18N
//                    remarks.append("Checking for value in file: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N
//                    // Read the file content
//                    fileContent = new String(Files.readAllBytes(fileToCheck.toPath()));
//                    containsValue = fileContent.contains(value); //NO I18N
//                } else {
//                    containsValue = true;
//                    remarks.append("Value found in log file: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N
//                    remarks.append("  Found value: ").append(theSearchValue).append("\n"); //NO I18N
//                }
//
//                if (containsValue) {
//                    remarks.append("✓ Value found in: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N
//
//                    // Find line number and context
//                    int valuePos = fileContent.indexOf(value);
//                    int lineNumber = 1;
//                    int columnNumber = 1;
//                    for (int i = 0; i < valuePos; i++) {
//                        if (fileContent.charAt(i) == '\n') {
//                            lineNumber++;
//                            columnNumber = 1;
//                        } else {
//                            columnNumber++;
//                        }
//                    }
//
//                    remarks.append("  Found at: line ").append(lineNumber).append(", column ").append(columnNumber).append("\n"); //NO I18N
//
//                    // Count occurrences
//                    int occurrences = 0;
//                    int lastIndex = -1;
//                    while ((lastIndex = fileContent.indexOf(value, lastIndex + 1)) != -1) {
//                        occurrences++;
//                    }
//
//                    if (occurrences > 1) {
//                        remarks.append("  Occurrences: ").append(occurrences).append("\n"); //NO I18N
//                    }
//
//                    // Show context around the value
//                    String[] lines = fileContent.split("\n");
//                    if (lineNumber <= lines.length) {
//                        remarks.append("  Context:\n"); //NO I18N
//
//                        // Show up to 2 lines before
//                        for (int i = Math.max(0, lineNumber - 3); i < lineNumber - 1; i++) {
//                            remarks.append("    ").append(i + 1).append(": ").append(lines[i]).append("\n"); //NO I18N
//                        }
//
//                        // Show the line with the value
//                        remarks.append("  → ").append(lineNumber).append(": ").append(lines[lineNumber - 1]).append("\n"); //NO I18N
//
//                        // Show up to 2 lines after
//                        for (int i = lineNumber; i < Math.min(lines.length, lineNumber + 2); i++) {
//                            remarks.append("    ").append(i + 1).append(": ").append(lines[i]).append("\n"); //NO I18N
//                        }
//                    }
//
//                    LOGGER.info(remarks.toString());
//                    lastOperationRemarks = remarks.toString();
//                    return true;
//                } else {
//                    remarks.append("✗ Value not found in: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N
//
//                    // File info
//                    remarks.append("  File size: ").append(formatFileSize(fileToCheck.length())).append("\n"); //NO I18N
//
//                    // Find similar values that might be a typo or mistake
//                    List<String> similarValues = findSimilarValues(fileContent, value);
//                    if (!similarValues.isEmpty()) {
//                        remarks.append("  Similar values found:\n"); //NO I18N
//                        for (String similar : similarValues) {
//                            remarks.append("    - ").append(similar).append("\n"); //NO I18N
//                        }
//                    }
//
//                    LOGGER.warning(remarks.toString());
//                    lastOperationRemarks = remarks.toString();
//                    return false;
//                }
//            } else {
//                // Check for absence of value
//                if (!containsValue) {
//                    remarks.append("✓ Confirmed value is not present in: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N
//                    remarks.append("  File size: ").append(formatFileSize(fileToCheck.length())).append("\n"); //NO I18N
//
//                    LOGGER.info(remarks.toString());
//                    lastOperationRemarks = remarks.toString();
//                    return true;
//                } else {
//                    remarks.append("✗ Value exists but should not in: ").append(fileToCheck.getAbsolutePath()).append("\n"); //NO I18N
//
//                    // Find line number and context
//                    int valuePos = fileContent.indexOf(value);
//                    int lineNumber = 1;
//                    int columnNumber = 1;
//                    for (int i = 0; i < valuePos; i++) {
//                        if (fileContent.charAt(i) == '\n') {
//                            lineNumber++;
//                            columnNumber = 1;
//                        } else {
//                            columnNumber++;
//                        }
//                    }
//
//                    remarks.append("  Found at: line ").append(lineNumber).append(", column ").append(columnNumber).append("\n"); //NO I18N
//
//                    // Show context around the value
//                    String[] lines = fileContent.split("\n");
//                    if (lineNumber <= lines.length) {
//                        remarks.append("  Context:\n"); //NO I18N
//                        remarks.append("    ").append(lineNumber).append(": ").append(lines[lineNumber - 1]).append("\n"); //NO I18N
//                    }
//
//                    LOGGER.warning(remarks.toString());
//                    lastOperationRemarks = remarks.toString();
//                    return false;
//                }
//            }
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error checking value in file", e); //NO I18N
//            lastOperationRemarks = "Error checking value in file: " + e.getMessage(); //NO I18N
//            return false;
//        }
//    }

//    public static String getCurrectTIme(String pattern){
//        LocalTime now = LocalTime.now();
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern); //NO I18N
//        return now.format(formatter);
//    }

    public static String getCurrentTime(String pattern) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern); // NO I18N
        return now.format(formatter);
    }

    public static String buildDateTime() {
        // Format both
        String timePart = getCurrentTime("HH:mm:ss:SSS");
        String datePart = getCurrentTime("MM-dd-yyyy");

        // Combine in the required format
        return "[" + timePart + "]|[" + datePart + "]";
    }


    /**
     * Extract a specific value from a log line using regex
     *
     * @param logFilePath  Path to the log file
     * @param timestamp    Timestamp to locate relevant logs (e.g., "06:38:43" or "01-15-2025")
     * @param linePattern  Text pattern to identify the line (e.g., "DESTINATION FOLDER NAME")
     * @param extractRegex Regular expression with capture group to extract the value
     * @return The extracted value or empty string if not found
     */
//    public static String extractValueFromLog(String logFilePath, String timestamp, String linePattern, String extractRegex) {
//        StringBuilder remarks = new StringBuilder();
//        remarks.append("Extracting value from log file\n");
//
//        String foundedValue = "";
//        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
//            boolean timestampFound = false;
//            String line;
//
//            while ((line = reader.readLine()) != null) {
//                // Find the timestamp first
//                if (!timestampFound && line.contains(timestamp)) {
//                    timestampFound = true;
//                    remarks.append("Found timestamp in line: ").append(line).append("\n");
//                }
//
//                // After finding timestamp, look for the pattern
//                if (timestampFound && line.contains(linePattern)) {
//                    remarks.append("Found pattern in line: ").append(line).append("\n");
//
//                    // Extract value using regex
//                    Pattern pattern = Pattern.compile(extractRegex);
//                    Matcher matcher = pattern.matcher(line);
//
//                    if (matcher.find()) {
//                        foundedValue = matcher.group();
//                        remarks.append("Extracted value: ").append(foundedValue);
//
//                        LOGGER.info(remarks.toString());
//                        lastOperationRemarks = remarks.toString();
//                        return foundedValue.trim();
//                    }
//                }
//            }
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error extracting value from log", e);
//            lastOperationRemarks = "Error: " + e.getMessage();
//            return foundedValue;
//        }
//        if (foundedValue.equals("") || foundedValue == null) { //NO I18N
//            foundedValue = extractValueFromFile(logFilePath, linePattern, extractRegex);
//        }
//        LOGGER.warning("Value found after timestamp " + timestamp);
//        lastOperationRemarks = remarks.toString();
//        return foundedValue;
//    }




    public static String extractValueFromLog(Operation operation, String logFilePath, String timestamp, String linePattern, String extractRegex) {
        StringBuilder remarks = new StringBuilder();
        remarks.append("Extracting value from log file\n");

        // Extract date and time parts from the reference timestamp
        String refHour = "";
        String refMinute = "";
        String datePart = "";
        try {
            // Parse timestamp format [HH:mm:ss:SSS]|[MM-dd-yyyy]
            Pattern timestampPattern = Pattern.compile("\\[(\\d{2}):(\\d{2}):[^\\]]*\\]\\|\\[([^\\]]*)\\]");
            Matcher timestampMatcher = timestampPattern.matcher(timestamp);

            if (timestampMatcher.find()) {
                refHour = timestampMatcher.group(1);
                refMinute = timestampMatcher.group(2);
                datePart = timestampMatcher.group(3); // MM-dd-yyyy
                remarks.append("Searching for entries on date " + datePart + " from time " + refHour + ":" + refMinute + " onwards\n");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to parse timestamp: " + timestamp);
        }

        String foundedValue = "";
        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            boolean timestampMatched = false;
            String line;

            while ((line = reader.readLine()) != null) {
                // Check if this line has a timestamp that matches our criteria
                if (!timestampMatched && !refHour.isEmpty()) {
                    Pattern lineTimestampPattern = Pattern.compile("\\[(\\d{2}):(\\d{2}):[^\\]]*\\]\\|\\[([^\\]]*)\\]");
                    Matcher lineTimestampMatcher = lineTimestampPattern.matcher(line);

                    if (lineTimestampMatcher.find()) {
                        String lineHour = lineTimestampMatcher.group(1);
                        String lineMinute = lineTimestampMatcher.group(2);
                        String lineDate = lineTimestampMatcher.group(3);

                        // Accept if date matches AND time is equal to or later than reference time
                        if (lineDate.equals(datePart) &&
                                (Integer.parseInt(lineHour) > Integer.parseInt(refHour) ||
                                        (lineHour.equals(refHour) && Integer.parseInt(lineMinute) >= Integer.parseInt(refMinute)))) {
                            timestampMatched = true;
                            remarks.append("Found matching timestamp in line: ").append(line).append("\n");
                        }
                    }
                } else if (!timestampMatched && refHour.isEmpty()) {
                    // Fallback to original behavior if timestamp parsing failed
                    if (line.contains(timestamp)) {
                        timestampMatched = true;
                        remarks.append("Found timestamp in line: ").append(line).append("\n");
                    }
                }

                // After finding timestamp, look for the pattern
                if (timestampMatched && line.contains(linePattern)) {
                    remarks.append("Found pattern in line: ").append(line).append("\n");
                    LOGGER.log(Level.INFO, "Found pattern in line: " + line);

                    // Extract value using regex
                    Pattern pattern = Pattern.compile(extractRegex);
                    Matcher matcher = pattern.matcher(line);

                    if (matcher.find()) {
                        foundedValue = matcher.group();
                        remarks.append("Extracted value: ").append(foundedValue);

                        LOGGER.info(remarks.toString());
                        operation.setRemarks(remarks.toString()); //NO I18N
                        return foundedValue.trim();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error extracting value from log", e);
            remarks.append("Error: ").append(e.getMessage()).append("\n");
            operation.setRemarks(remarks.toString()); //NO I18N
            return foundedValue;
        }

        if (!logFilePath.endsWith(".log") && !logFilePath.endsWith(".txt")) {
            foundedValue = extractValueFromFile(operation,logFilePath, linePattern, extractRegex);
        }

        LOGGER.warning("Value not found after timestamp " + timestamp);
        operation.setRemarks(remarks.toString()); //NO I18N
        return foundedValue;
    }






    /**
     * Extract a value from a file by finding a line containing a specific value
     * and then extracting another value from that line using a regex pattern
     *
     * @param filePath        Path to the file
     * @param searchValue     Value to search for in the file
     * @param extractionRegex Regular expression pattern to extract the desired value
     * @return The extracted value or empty string if not found
     */
    public static String extractValueFromFile(Operation operation, String filePath, String searchValue, String extractionRegex) {
        StringBuilder remarks = new StringBuilder();
        remarks.append("Extracting value from file\n");
        remarks.append("- File: ").append(filePath).append("\n");
        remarks.append("- Searching for: ").append(searchValue).append("\n");
        remarks.append("- Using regex: ").append(extractionRegex).append("\n");

        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                remarks.append("- Error: File not found or is not a regular file");
                LOGGER.warning(remarks.toString());
                operation.setRemarks(remarks.toString()); //NO I18N
                return "";
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                int lineNum = 0;

                // Compile the regex pattern
                Pattern pattern = Pattern.compile(extractionRegex);

                while ((line = reader.readLine()) != null) {
                    lineNum++;

                    // Check if line contains the search value
                    if (line.contains(searchValue)) {
                        remarks.append("- Found search value at line ").append(lineNum).append(": ").append(line).append("\n");

                        // Apply regex to extract the value
                        Matcher matcher = pattern.matcher(line);

                        if (matcher.find()) {
                            String extractedValue = matcher.group();
                            remarks.append("- Extracted value: ").append(extractedValue);

                            LOGGER.info(remarks.toString());
                            operation.setRemarks(remarks.toString()); //NO I18N
                            return extractedValue;
                        } else {
                            remarks.append("- No match found for regex pattern in the line containing search value");
                            LOGGER.warning(remarks.toString());
                            operation.setRemarks(remarks.toString()); //NO I18N
                            return "";
                        }
                    }
                }

                remarks.append("- Search value not found in file");
                LOGGER.warning(remarks.toString());
                operation.setRemarks(remarks.toString()); //NO I18N
                return "";
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error extracting value from file", e);
            operation.setRemarks(remarks.toString()); //NO I18N
            return "";
        }
    }


    /**
     * Create a folder if it doesn't exist
     *
     * @param folderPath Path to create
     * @return true if folder exists or was created successfully
     */
    public static boolean createFolderIfNotExists(String folderPath) {
        try {
            // Convert to absolute path and resolve any server_home references
            String resolvedPath = ServerUtils.resolvePath(folderPath);

            LOGGER.info("Creating folder if not exists: " + resolvedPath); //NO I18N
            LOGGER.info("Original path provided: " + folderPath); //NO I18N

            Path path = Paths.get(resolvedPath);
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    LOGGER.info("Folder already exists: " + path.toAbsolutePath()); //NO I18N
                    return true;
                } else {
                    LOGGER.warning("Path exists but is not a directory: " + path.toAbsolutePath()); //NO I18N
                    return false;
                }
            }

            Files.createDirectories(path);
            LOGGER.info("Folder created: " + path.toAbsolutePath()); //NO I18N
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating folder: " + e.getMessage(), e); //NO I18N
            return false;
        }
    }

    /**
     * Format file size in human-readable format
     *
     * @param size File size in bytes
     * @return Formatted file size string
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " bytes"; //NO I18N
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0); //NO I18N
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024)); //NO I18N
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024)); //NO I18N
        }
    }

    /**
     * Format date in human-readable format
     *
     * @param timestamp Timestamp in milliseconds
     * @return Formatted date string
     */
    private static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //NO I18N
        return sdf.format(new Date(timestamp));
    }

    /**
     * Detect basic content type based on file extension
     *
     * @param fileName Name of the file
     * @return Content type description
     */
    private static String detectContentType(String fileName) {
        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".txt") || lowerName.endsWith(".log")) { //NO I18N
            return "Text"; //NO I18N
        } else if (lowerName.endsWith(".json")) { //NO I18N
            return "JSON"; //NO I18N
        } else if (lowerName.endsWith(".xml")) { //NO I18N
            return "XML"; //NO I18N
        } else if (lowerName.endsWith(".properties")) { //NO I18N
            return "Properties"; //NO I18N
        } else if (lowerName.endsWith(".ini")) { //NO I18N
            return "INI Configuration"; //NO I18N
        } else if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) { //NO I18N
            return "HTML"; //NO I18N
        } else if (lowerName.endsWith(".java")) { //NO I18N
            return "Java Source"; //NO I18N
        } else if (lowerName.endsWith(".class")) { //NO I18N
            return "Java Class"; //NO I18N
        } else if (lowerName.endsWith(".jar")) { //NO I18N
            return "Java Archive"; //NO I18N
        } else if (lowerName.endsWith(".zip")) { //NO I18N
            return "ZIP Archive"; //NO I18N
        } else if (lowerName.endsWith(".bat") || lowerName.endsWith(".cmd")) { //NO I18N
            return "Batch Script"; //NO I18N
        } else if (lowerName.endsWith(".sh")) { //NO I18N
            return "Shell Script"; //NO I18N
        } else if (lowerName.endsWith(".exe") || lowerName.endsWith(".dll")) { //NO I18N
            return "Windows Executable"; //NO I18N
        } else if (lowerName.endsWith(".config")) { //NO I18N
            return "Configuration"; //NO I18N
        } else {
            return "Unknown"; //NO I18N
        }
    }

    /**
     * Find similar values in text that might be typos of the search value
     *
     * @param content     Text content to search in
     * @param searchValue Value to find similar matches for
     * @return List of similar values found
     */
    private static List<String> findSimilarValues(String content, String searchValue) {
        List<String> results = new ArrayList<>();

        // For very short search values, don't try to find similar values
        if (searchValue.length() < 5) {
            return results;
        }

        // Split by common separators
        String[] words = content.split("[\r\n\\s,;:=\"'\\{\\}\\[\\]]+"); //NO I18N

        for (String word : words) {
            if (word.length() < 3 || word.equals(searchValue)) {
                continue;
            }

            // Simple similarity check - share at least half of the characters
            double similarity = calculateSimilarity(word, searchValue);

            if (similarity >= 0.7) {  // Using double comparison with 0.7 instead of int
                results.add(word);

                // Limit results
                if (results.size() >= 3) {
                    break;
                }
            }
        }

        return results;
    }

    /**
     * Calculate simple similarity between two strings
     *
     * @param str1 First string
     * @param str2 Second string
     * @return Similarity score from 0.0 to 1.0
     */
    private static double calculateSimilarity(String str1, String str2) {
        // Quick check for trivial cases
        if (str1.equals(str2)) {
            return 1.0;
        }

        if (str1.length() == 0 || str2.length() == 0) {
            return 0.0;
        }

        // Count common characters
        int commonChars = 0;
        String s1 = str1.toLowerCase();
        String s2 = str2.toLowerCase();

        // Check if either string contains the other
        if (s1.contains(s2)) {
            return 0.9;
        }

        if (s2.contains(s1)) {
            return 0.9;
        }

        // Check for common prefix or suffix
        int prefixLength = 0;
        int minLength = Math.min(s1.length(), s2.length());

        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLength++;
            } else {
                break;
            }
        }

        int suffixLength = 0;
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(s1.length() - 1 - i) == s2.charAt(s2.length() - 1 - i)) {
                suffixLength++;
            } else {
                break;
            }
        }

        double prefixSimilarity = (double) prefixLength / minLength;
        double suffixSimilarity = (double) suffixLength / minLength;

        return Math.max(prefixSimilarity, suffixSimilarity);
    }

    /**
     * Main method for testing the utility
     */
//    public static void main(String[] args) {
//        // Test file presence
//        testFilePresence(".", "pom.xml", true); //NO I18N
//        testFilePresence(".", "nonexistent.txt", false); //NO I18N
//
//        // Test value in file
//        testValueInFile(".", "pom.xml", "<project", true); //NO I18N
//        testValueInFile(".", "pom.xml", "nonexistentvalue", false); //NO I18N
//    }

//    private static void testFilePresence(String dirPath, String fileName, boolean shouldExist) {
//        boolean result = checkFileFolderPresence(dirPath, fileName, shouldExist);
//        ConsoleOut.println("Check " + fileName + " " + (shouldExist ? "exists" : "does not exist") + //NO I18N
//                ": " + (result ? "PASSED" : "FAILED")); //NO I18N
//    }

//    private static void testValueInFile(String dirPath, String fileName, String value, boolean shouldExist) {
//        boolean result = checkValueInFile(dirPath, fileName, value, shouldExist);
//        ConsoleOut.println("Check value \"" + value + "\" " + //NO I18N
//                (shouldExist ? "exists" : "does not exist") + //NO I18N
//                " in " + fileName + ": " + (result ? "PASSED" : "FAILED")); //NO I18N
//    }
}
