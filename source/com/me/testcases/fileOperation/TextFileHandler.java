package com.me.testcases.fileOperation;

import com.me.Operation;
import com.me.util.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

/**
 * Handler for text file operations
 */
public class TextFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(TextFileHandler.class, LogManager.LOG_TYPE.FW);

    // Maximum number of lines to log for file content
    private static final int MAX_LINES_TO_LOG = 10;
    // Maximum number of characters to preview per line
    private static final int MAX_CHARS_PER_LINE = 100;

    /**
     * Execute a text file operation
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
//        String operationType = operation.getOperationType();
        String fileName = operation.getParameter("filename");
        String value = operation.getParameter("value");

        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for text file operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for text file operation");
            return false;
        }

        // Create file object
        File file;
        if (fileName == null || fileName.isEmpty()) {
            file = new File(filePath);
        } else {
            File directory = new File(filePath);
            file = new File(directory, fileName);
        }

        // For create action, we don't need the file to exist
        if (!file.exists() && !action.equalsIgnoreCase("create")) {
            String errorMsg = "File not found: " + file.getAbsolutePath();
            LOGGER.warning(errorMsg);
            operation.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("Text File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            boolean success;

            switch (action.toLowerCase()) {
                case "create":
                    success = createFile(file, operation, remarkBuilder);
                    break;
                case "read":
                    success = readFile(file, operation, remarkBuilder);
                    break;
                case "write":
                    success = writeFile(file, operation, remarkBuilder);
                    break;
                case "insert_first":
                    success = insertLineInFile(file, operation, remarkBuilder, 1);
                    break;
                case "insert_last":
                    success = insertLineInFile(file, operation, remarkBuilder, -1);
                    break;
                case "remove_first_line":
                    success = removeLineInFile(file, operation, remarkBuilder, 1);
                    break;
                case "remove_last_line":
                    success = removeLineInFile(file, operation, remarkBuilder, -1);
                    break;
                case "append":
                    success = appendToFile(file, operation, remarkBuilder);
                    break;
                case "delete":
                    success = deleteFile(file, operation, remarkBuilder);
                    break;
                case "replace":
                    success = replaceTextInFile(file, operation, remarkBuilder);
                    break;
                case "update_line":
                    success = updateLineInFile(file, operation, remarkBuilder);
                    break;
                case "insert_line":
                    success = insertLineInFile(file, operation, remarkBuilder);
                    break;
                case "delete_line":
                    success = deleteLineInFile(file, operation, remarkBuilder);
                    break;
                case "find_text":
                    success = findTextInFile(file, operation, remarkBuilder);
                    break;
                case "count":
                    success = countInFile(file, operation, remarkBuilder);
                    break;
                case "copy":
                    success = copyFile(file, operation, remarkBuilder);
                    break;
                case "move":
                    success = moveFile(file, operation, remarkBuilder);
                    break;
                case "rename":
                    success = renameFile(file, operation, remarkBuilder);
                    break;
//                case "check_presence":
//                    success = checkFilePresence(file, remarkBuilder, true);
//                    break;
//                case "check_absence":
//                    success = checkFilePresence(file, remarkBuilder, false);
//                    break;
                case "value_should_be_present":
                case "check_value_presence":
                    success = checkValuePresence(file, operation, remarkBuilder, true);
                    break;
                case "value_should_be_removed":
                case "check_value_absence":
                    success = checkValuePresence(file, operation, remarkBuilder, false);
                    break;
//                case "extract_log_value":
//                    success = extractLogValue(file, operation, remarkBuilder);
//                    break;
                default:
                    String errorMsg = "Unsupported action: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing text file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }

    private static boolean insertLineInFile(File file, Operation operation, StringBuilder remarks, int position) throws IOException {
        String newContent = operation.getParameter("new_content");
        if (newContent == null) newContent = "";
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        if (position == 1) {
            lines.add(0, newContent);
            remarks.append("Line inserted at the beginning.");
        } else if (position == -1) {
            lines.add(newContent);
            remarks.append("Line inserted at the end.");
        } else {
            remarks.append("Invalid position for insert.");
            return false;
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        return true;
    }

    private static boolean removeLineInFile(File file, Operation operation, StringBuilder remarks, int position) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            remarks.append("File is empty. Nothing to remove.");
            return false;
        }
        if (position == 1) {
            lines.remove(0);
            remarks.append("First line removed.");
        } else if (position == -1) {
            lines.remove(lines.size() - 1);
            remarks.append("Last line removed.");
        } else {
            remarks.append("Invalid position for remove.");
            return false;
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        return true;
    }

    /**
     * Check if a value is present or absent in a file
     */
    private static boolean checkValuePresence(File file, Operation operation, StringBuilder remarks, boolean shouldExist) {
        try {
            String value = operation.getParameter("value");
            String regex = operation.getParameter("regex");
            if (regex == null || regex.isEmpty()) {
                regex = "";
            }

            if (value == null || value.isEmpty()) {
                remarks.append("Error: value parameter is required\n");
                return false;
            }

            // Read the file content
            String fileContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            boolean containsValue = fileContent.contains(value);

            // Check if we need to use log extraction
            String currentTime = getCurrentTimestamp();
            LOGGER.info("Current timestamp for log extraction: " + currentTime);
            String extractedValue = "";
            if (shouldExist) {
                extractedValue = extractValueFromLog(file.getAbsolutePath(), currentTime, value, regex );
                if (extractedValue != null && !extractedValue.isEmpty()) {
                    saveNote(operation, extractedValue);
                    containsValue = true;
                    remarks.append("Value found in log file: ").append(file.getAbsolutePath()).append("\n");
                    remarks.append("  Found value: ").append(extractedValue).append("\n");
                    return containsValue;
                }
            }

            if (shouldExist == containsValue) {
                if (shouldExist) {
                    remarks.append("✓ Value found in: ").append(file.getAbsolutePath()).append("\n");

                    // Find line number and context
                    int valuePos = fileContent.indexOf(value);
                    int lineNumber = 1;
                    int columnNumber = 1;
                    for (int i = 0; i < valuePos; i++) {
                        if (fileContent.charAt(i) == '\n') {
                            lineNumber++;
                            columnNumber = 1;
                        } else {
                            columnNumber++;
                        }
                    }

                    remarks.append("  Found at: line ").append(lineNumber).append(", column ").append(columnNumber).append("\n");

                    // Count occurrences
                    int occurrences = 0;
                    int lastIndex = -1;
                    while ((lastIndex = fileContent.indexOf(value, lastIndex + 1)) != -1) {
                        occurrences++;
                    }

                    if (occurrences > 1) {
                        remarks.append("  Occurrences: ").append(occurrences).append("\n");
                    }

                    // Show context around the value
                    String[] lines = fileContent.split("\n");
                    if (lineNumber <= lines.length) {
                        remarks.append("  Context:\n");

                        // Show up to 2 lines before
                        for (int i = Math.max(0, lineNumber - 3); i < lineNumber - 1; i++) {
                            remarks.append("    ").append(i + 1).append(": ").append(lines[i]).append("\n");
                        }

                        // Show the line with the value
                        remarks.append("  → ").append(lineNumber).append(": ").append(lines[lineNumber - 1]).append("\n");

                        // Show up to 2 lines after
                        for (int i = lineNumber; i < Math.min(lines.length, lineNumber + 2); i++) {
                            remarks.append("    ").append(i + 1).append(": ").append(lines[i]).append("\n");
                        }
                    }
                } else {
                    remarks.append("✓ Confirmed value is not present in: ").append(file.getAbsolutePath()).append("\n");
                    remarks.append("  File size: ").append(formatFileSize(file.length())).append("\n");
                }
                return true;
            } else {
                if (shouldExist) {
                    remarks.append("✗ Value not found in: ").append(file.getAbsolutePath()).append("\n");
                    remarks.append("  File size: ").append(formatFileSize(file.length())).append("\n");

                    // Find similar values that might be a typo or mistake
                    List<String> similarValues = findSimilarValues(fileContent, value);
                    if (!similarValues.isEmpty()) {
                        remarks.append("  Similar values found:\n");
                        for (String similar : similarValues) {
                            remarks.append("    - ").append(similar).append("\n");
                        }
                    }
                } else {
                    remarks.append("✗ Value exists but should not in: ").append(file.getAbsolutePath()).append("\n");

                    // Find line number and context
                    int valuePos = fileContent.indexOf(value);
                    int lineNumber = 1;
                    int columnNumber = 1;
                    for (int i = 0; i < valuePos; i++) {
                        if (fileContent.charAt(i) == '\n') {
                            lineNumber++;
                            columnNumber = 1;
                        } else {
                            columnNumber++;
                        }
                    }

                    remarks.append("  Found at: line ").append(lineNumber).append(", column ").append(columnNumber).append("\n");

                    // Show context around the value
                    String[] lines = fileContent.split("\n");
                    if (lineNumber <= lines.length) {
                        remarks.append("  Context:\n");
                        remarks.append("    ").append(lineNumber).append(": ").append(lines[lineNumber - 1]).append("\n");
                    }
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking value in file", e);
            remarks.append("Error: ").append(e.getMessage());
            return false;
        }
    }

    /**
     * Extract a value from a log file
     */
    private static boolean extractLogValue(File file, Operation operation, StringBuilder remarks) {
        String timestamp = operation.getParameter("timestamp");
        String linePattern = operation.getParameter("line_pattern");
        String extractRegex = operation.getParameter("extract_regex");

        if (linePattern == null || linePattern.isEmpty()) {
            remarks.append("Error: line_pattern parameter is required\n");
            return false;
        }

        if (timestamp == null || timestamp.isEmpty()) {
            timestamp = getCurrentTimestamp();
        }

        if (extractRegex == null || extractRegex.isEmpty()) {
            extractRegex = ""; // Will extract the whole line
        }

        String extractedValue = extractValueFromLog(file.getAbsolutePath(), timestamp, linePattern, extractRegex);

        if (extractedValue != null && !extractedValue.isEmpty()) {
            remarks.append("Successfully extracted value: ").append(extractedValue).append("\n");
            operation.setParameter("extracted_value", extractedValue);
            return true;
        } else {
            remarks.append("Failed to extract value from log file\n");
            return false;
        }
    }

    /**
     * Extract a specific value from a log line using regex within a time window
     * of +/- 5 minutes from the reference timestamp
     */
    public static String extractValueFromLog(String logFilePath, String timestamp, String linePattern, String extractRegex) {
        StringBuilder remarks = new StringBuilder();
        remarks.append("Extracting value from log file\n");

        // Extract date and time parts from the reference timestamp
        String refHour = "";
        String refMinute = "";
        String datePart = "";
        int refHourInt = -1;
        int refMinuteInt = -1;

        try {
            // Parse timestamp format [HH:mm:ss:SSS]|[MM-dd-yyyy]
            Pattern timestampPattern = Pattern.compile("\\[(\\d{2}):(\\d{2}):[^\\]]*\\]\\|\\[([^\\]]*)\\]");
            Matcher timestampMatcher = timestampPattern.matcher(timestamp);

            if (timestampMatcher.find()) {
                refHour = timestampMatcher.group(1);
                refMinute = timestampMatcher.group(2);
                datePart = timestampMatcher.group(3); // MM-dd-yyyy

                refHourInt = Integer.parseInt(refHour);
                refMinuteInt = Integer.parseInt(refMinute);

                remarks.append("Searching for entries on date " + datePart + " within ±5 minutes of " +
                        refHour + ":" + refMinute + "\n");
                LOGGER.info("Parsed timestamp - Date: " + datePart + ", Hour: " + refHour + ", Minute: " + refMinute);
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
                if (!timestampMatched && refHourInt >= 0 && refMinuteInt >= 0) {
                    Pattern lineTimestampPattern = Pattern.compile("\\[(\\d{2}):(\\d{2}):[^\\]]*\\]\\|\\[([^\\]]*)\\]");
                    Matcher lineTimestampMatcher = lineTimestampPattern.matcher(line);

                    if (lineTimestampMatcher.find()) {
                        LOGGER.info("Found timestamp in line: " + line);
                        String lineHour = lineTimestampMatcher.group(1);
                        String lineMinute = lineTimestampMatcher.group(2);
                        String lineDate = lineTimestampMatcher.group(3);

                        LOGGER.info("Line timestamp - Date: " + lineDate + ", Hour: " + lineHour + ", Minute: " + lineMinute);

                        int lineHourInt = Integer.parseInt(lineHour);
                        int lineMinuteInt = Integer.parseInt(lineMinute);

                        // Check if the line timestamp falls within our time window
                        if (lineDate.equals(datePart)) {
                            LOGGER.info("Line date matches reference date: " + lineDate);
                            // Convert time to minutes for easier comparison
                            int refTimeInMinutes = refHourInt * 60 + refMinuteInt;
                            int lineTimeInMinutes = lineHourInt * 60 + lineMinuteInt;
                            int timeDifference = Math.abs(refTimeInMinutes - lineTimeInMinutes);

                            LOGGER.info("Reference time in minutes: " + refTimeInMinutes + ", Line time in minutes: " + lineTimeInMinutes +
                                    ", Difference: " + timeDifference + " minutes");

                            // Check if the difference is within 5 minutes
                            if (timeDifference <= 5) {
                                LOGGER.info("Line time " + lineHour + ":" + lineMinute +
                                        " is within ±5 minutes of reference time " + refHour + ":" + refMinute);
                                timestampMatched = true;
                                remarks.append("Found timestamp within ±5 minutes in line: ").append(line).append("\n");
                            }
                        }
                    }
                } else if (!timestampMatched) {
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
                    if (extractRegex != null && !extractRegex.isEmpty()) {
                        Pattern pattern = Pattern.compile(extractRegex);
                        Matcher matcher = pattern.matcher(line);

                        if (matcher.find()) {
                            foundedValue = matcher.group();
                            remarks.append("Extracted value: ").append(foundedValue);
                            return foundedValue.trim();
                        }
                    } else {
                        // If no regex provided, return the whole line
                        foundedValue = line;
                        remarks.append("No regex provided, returning whole line");
                        return foundedValue.trim();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error extracting value from log", e);
            return foundedValue;
        }

//        if (!logFilePath.endsWith(".log") && logFilePath.endsWith(".txt")) {
//            LOGGER.info("Log file is a text file, trying to extract value without timestamp");
//            foundedValue = extractValueFromFile(logFilePath, linePattern, extractRegex);
//        }

        LOGGER.warning("Value not found within ±5 minutes of timestamp " + timestamp);
        return foundedValue;
    }

    /**
     * Extract a value from a file by finding a line containing a specific value
     * and then extracting another value from that line using a regex pattern
     */
//    public static String extractValueFromFile(String filePath, String searchValue, String extractionRegex) {
//        StringBuilder remarks = new StringBuilder();
//        remarks.append("Extracting value from file\n");
//        remarks.append("- File: ").append(filePath).append("\n");
//        remarks.append("- Searching for: ").append(searchValue).append("\n");
//        remarks.append("- Using regex: ").append(extractionRegex).append("\n");
//
//        try {
//            File file = new File(filePath);
//            if (!file.exists() || !file.isFile()) {
//                remarks.append("- Error: File not found or is not a regular file");
//                LOGGER.warning(remarks.toString());
//                return "";
//            }
//
//            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//                String line;
//                int lineNum = 0;
//
//                // Compile the regex pattern if provided
//                Pattern pattern = null;
//                if (extractionRegex != null && !extractionRegex.isEmpty()) {
//                    pattern = Pattern.compile(extractionRegex);
//                }
//
//                while ((line = reader.readLine()) != null) {
//                    lineNum++;
//
//                    // Check if line contains the search value
//                    if (line.contains(searchValue)) {
//                        remarks.append("- Found search value at line ").append(lineNum).append(": ").append(line).append("\n");
//
//                        // Apply regex to extract the value if provided
//                        if (pattern != null) {
//                            Matcher matcher = pattern.matcher(line);
//
//                            if (matcher.find()) {
//                                String extractedValue = matcher.group();
//                                remarks.append("- Extracted value: ").append(extractedValue);
//                                LOGGER.info(remarks.toString());
//                                return extractedValue;
//                            } else {
//                                remarks.append("- No match found for regex pattern in the line containing search value");
//                                LOGGER.warning(remarks.toString());
//                                return "";
//                            }
//                        } else {
//                            // If no regex, return the entire line
//                            LOGGER.info("No regex provided, returning whole line containing search value");
//                            return line;
//                        }
//                    }
//                }
//
//                remarks.append("- Search value not found in file");
//                LOGGER.warning(remarks.toString());
//                return "";
//            }
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error extracting value from file", e);
//            return "";
//        }
//    }

    /**
     * Get current timestamp in the format [HH:mm:ss:SSS]|[MM-dd-yyyy]
     */
    private static String getCurrentTimestamp() {
        return "[" + getCurrentTime("HH:mm:ss:SSS") + "]|[" + getCurrentTime("MM-dd-yyyy") + "]";
    }

    /**
     * Get current time formatted according to pattern
     */
    private static String getCurrentTime(String pattern) {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Format file size in human-readable format
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " bytes";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Format date in human-readable format
     */
    private static String formatDate(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(timestamp));
    }

    /**
     * Detect basic content type based on file extension
     */
    private static String detectContentType(String fileName) {
        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".txt") || lowerName.endsWith(".log")) {
            return "Text";
        } else if (lowerName.endsWith(".json")) {
            return "JSON";
        } else if (lowerName.endsWith(".xml")) {
            return "XML";
        } else if (lowerName.endsWith(".properties")) {
            return "Properties";
        } else if (lowerName.endsWith(".ini")) {
            return "INI Configuration";
        } else if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return "HTML";
        } else if (lowerName.endsWith(".java")) {
            return "Java Source";
        } else if (lowerName.endsWith(".class")) {
            return "Java Class";
        } else if (lowerName.endsWith(".jar")) {
            return "Java Archive";
        } else if (lowerName.endsWith(".zip")) {
            return "ZIP Archive";
        } else if (lowerName.endsWith(".bat") || lowerName.endsWith(".cmd")) {
            return "Batch Script";
        } else if (lowerName.endsWith(".sh")) {
            return "Shell Script";
        } else if (lowerName.endsWith(".exe") || lowerName.endsWith(".dll")) {
            return "Windows Executable";
        } else if (lowerName.endsWith(".config")) {
            return "Configuration";
        } else {
            return "Unknown";
        }
    }

    /**
     * Find similar values in text that might be typos of the search value
     */
    private static List<String> findSimilarValues(String content, String searchValue) {
        List<String> results = new ArrayList<>();

        // For very short search values, don't try to find similar values
        if (searchValue.length() < 5) {
            return results;
        }

        // Split by common separators
        String[] words = content.split("[\r\n\\s,;:=\"'\\{\\}\\[\\]]+");

        for (String word : words) {
            if (word.length() < 3 || word.equals(searchValue)) {
                continue;
            }

            // Simple similarity check
            double similarity = calculateSimilarity(word, searchValue);

            if (similarity >= 0.7) {
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
     * Create a new text file
     */
    private static boolean createFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String content = operation.getParameter("content");
        boolean overwrite = Boolean.parseBoolean(operation.getParameter("overwrite"));

        if (file.exists() && !overwrite) {
            remarks.append("File already exists and overwrite=false. No action taken.");
            return false;
        }

        // Create parent directories if they don't exist
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (content != null) {
            // Create and write content
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            remarks.append("File created with content.");
        } else {
            // Just create empty file
            file.createNewFile();
            remarks.append("Empty file created.");
        }

        return true;
    }

    /**
     * Read file content
     */
    private static boolean readFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String contentLimit = operation.getParameter("limit");
        int limit = contentLimit != null ? Integer.parseInt(contentLimit) : -1;

        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (limit > 0 && lineCount > limit) {
                    contentBuilder.append("... (additional lines truncated)");
                    break;
                }
                contentBuilder.append(line).append("\n");
            }
        }

        String content = contentBuilder.toString();
        operation.setParameter("file_content", content);
        remarks.append("File content read successfully. ").append(file.length()).append(" bytes.");
        return true;
    }

    /**
     * Write content to file (overwriting existing content)
     */
    private static boolean writeFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String content = operation.getParameter("content");

        if (content == null) {
            remarks.append("No content provided for write operation.");
            return false;
        }

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup && file.exists()) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Write content to file
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        remarks.append("Content written to file. ").append(content.length()).append(" chars written.");
        return true;
    }

    /**
     * Append content to file
     */
    private static boolean appendToFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String content = operation.getParameter("content");

        if (content == null) {
            remarks.append("No content provided for append operation.");
            return false;
        }

        // Create file if it doesn't exist
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        // Append content to file
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        remarks.append("Content appended to file. ").append(content.length()).append(" chars appended.");
        return true;
    }

    /**
     * Delete file
     */
    private static boolean deleteFile(File file, Operation operation, StringBuilder remarks) {
        boolean backup = Boolean.parseBoolean(operation.getParameter("backup"));

        // Create backup if requested
        if (backup) {
            try {
                String backupPath = file.getPath() + ".bak";
                Files.copy(file.toPath(), Paths.get(backupPath));
                remarks.append("Backup created at: ").append(backupPath).append("\n");
            } catch (IOException e) {
                remarks.append("Failed to create backup before deletion: ").append(e.getMessage()).append("\n");
                return false;
            }
        }

        if (file.delete()) {
            remarks.append("File deleted successfully.");
            return true;
        } else {
            remarks.append("Failed to delete file.");
            return false;
        }
    }

    /**
     * Replace text in file
     */
    private static boolean replaceTextInFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String searchText = operation.getParameter("search_text");
        String replaceText = operation.getParameter("replace_text");
        boolean regex = Boolean.parseBoolean(operation.getParameter("regex"));
        boolean ignoreCase = Boolean.parseBoolean(operation.getParameter("ignore_case"));

        if (searchText == null || searchText.isEmpty()) {
            remarks.append("Search text not specified.");
            return false;
        }

        if (replaceText == null) {
            replaceText = ""; // Empty string if not specified
        }

        // Read file content
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String originalContent = content;

        // Perform replacement
        if (regex) {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            Pattern pattern = Pattern.compile(searchText, flags);
            Matcher matcher = pattern.matcher(content);
            content = matcher.replaceAll(replaceText);
        } else {
            if (ignoreCase) {
                searchText = searchText.toLowerCase();
                StringBuilder sb = new StringBuilder(content);
                int index = content.toLowerCase().indexOf(searchText);
                while (index != -1) {
                    sb.replace(index, index + searchText.length(), replaceText);
                    index = content.toLowerCase().indexOf(searchText, index + replaceText.length());
                }
                content = sb.toString();
            } else {
                content = content.replace(searchText, replaceText);
            }
        }

        // Write back if changed
        if (!content.equals(originalContent)) {
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            remarks.append("Replaced text successfully. File updated.");
        } else {
            remarks.append("No occurrences of search text found. File unchanged.");
        }

        return true;
    }

    /**
     * Update a specific line in file
     */
    private static boolean updateLineInFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String lineNumberStr = operation.getParameter("line_number");
        String newContent = operation.getParameter("new_content");

        if (lineNumberStr == null || lineNumberStr.isEmpty()) {
            remarks.append("Line number not specified.");
            return false;
        }

        int lineNumber = Integer.parseInt(lineNumberStr);

        if (newContent == null) {
            newContent = ""; // Empty string if not specified
        }

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        if (lineNumber < 1 || lineNumber > lines.size()) {
            remarks.append("Invalid line number. File has ").append(lines.size()).append(" lines.");
            return false;
        }

        // Update the specified line
        lines.set(lineNumber - 1, newContent);

        // Write back to file
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        remarks.append("Line ").append(lineNumber).append(" updated successfully.");

        return true;
    }

    /**
     * Insert a line at specified position
     */
    private static boolean insertLineInFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String lineNumberStr = operation.getParameter("line_number");
        String newContent = operation.getParameter("new_content");

        if (lineNumberStr == null || lineNumberStr.isEmpty()) {
            remarks.append("Line number not specified.");
            return false;
        }

        int lineNumber = Integer.parseInt(lineNumberStr);

        if (newContent == null) {
            newContent = ""; // Empty string if not specified
        }

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        // Line number validation
        if (lineNumber < 1 || lineNumber > lines.size() + 1) {
            remarks.append("Invalid line number. File has ").append(lines.size()).append(" lines.");
            return false;
        }

        // Insert the new line
        lines.add(lineNumber - 1, newContent);

        // Write back to file
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        remarks.append("Line inserted at position ").append(lineNumber).append(" successfully.");

        return true;
    }

    /**
     * Delete a specific line from file
     */
    private static boolean deleteLineInFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String lineNumberStr = operation.getParameter("line_number");

        if (lineNumberStr == null || lineNumberStr.isEmpty()) {
            remarks.append("Line number not specified.");
            return false;
        }

        int lineNumber = Integer.parseInt(lineNumberStr);

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        if (lineNumber < 1 || lineNumber > lines.size()) {
            remarks.append("Invalid line number. File has ").append(lines.size()).append(" lines.");
            return false;
        }

        // Remove the specified line
        lines.remove(lineNumber - 1);

        // Write back to file
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        remarks.append("Line ").append(lineNumber).append(" deleted successfully.");

        return true;
    }

    /**
     * Find text in file
     */
    private static boolean findTextInFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String searchText = operation.getParameter("search_text");
        boolean regex = Boolean.parseBoolean(operation.getParameter("regex"));
        boolean ignoreCase = Boolean.parseBoolean(operation.getParameter("ignore_case"));

        if (searchText == null || searchText.isEmpty()) {
            remarks.append("Search text not specified.");
            return false;
        }

        // Initialize result list
        List<String> matches = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();

        // Read file line by line
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;

            Pattern pattern = null;
            if (regex) {
                int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
                pattern = Pattern.compile(searchText, flags);
            }

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                boolean found = false;

                if (regex) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        found = true;
                    }
                } else {
                    String lineToSearch = ignoreCase ? line.toLowerCase() : line;
                    String textToFind = ignoreCase ? searchText.toLowerCase() : searchText;
                    if (lineToSearch.contains(textToFind)) {
                        found = true;
                    }
                }

                if (found) {
                    matches.add(line);
                    lineNumbers.add(lineNumber);
                }
            }
        }

        // Set results in operation parameters
        operation.setParameter("match_count", String.valueOf(matches.size()));
        operation.setParameter("matches", String.join("\n", matches));
        operation.setParameter("line_numbers", lineNumbers.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));

        remarks.append("Found ").append(matches.size()).append(" matches for '")
                .append(searchText).append("'");

        return true;
    }

    /**
     * Count lines/words/characters in file
     */
    private static boolean countInFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String countType = operation.getParameter("count_type");

        if (countType == null || countType.isEmpty()) {
            countType = "all"; // Default to counting everything
        }

        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        int lineCount = 0;
        int wordCount = 0;
        int charCount = content.length();

        if (countType.equals("all") || countType.equals("lines")) {
            String[] lines = content.split("\r\n|\r|\n");
            lineCount = lines.length;
            operation.setParameter("line_count", String.valueOf(lineCount));
        }

        if (countType.equals("all") || countType.equals("words")) {
            String[] words = content.split("\\s+");
            wordCount = words.length;
            operation.setParameter("word_count", String.valueOf(wordCount));
        }

        if (countType.equals("all") || countType.equals("chars")) {
            operation.setParameter("char_count", String.valueOf(charCount));
        }

        remarks.append("File statistics: ");
        if (countType.equals("all") || countType.equals("lines")) {
            remarks.append(lineCount).append(" lines, ");
        }
        if (countType.equals("all") || countType.equals("words")) {
            remarks.append(wordCount).append(" words, ");
        }
        if (countType.equals("all") || countType.equals("chars")) {
            remarks.append(charCount).append(" characters");
        }

        return true;
    }

    /**
     * Copy file to a new location
     */
    private static boolean copyFile(File sourceFile, Operation operation, StringBuilder remarks) throws IOException {
        String targetPath = operation.getParameter("target_path");
        boolean overwrite = Boolean.parseBoolean(operation.getParameter("overwrite"));

        if (targetPath == null || targetPath.isEmpty()) {
            remarks.append("Target path not specified.");
            return false;
        }

        Path target = Paths.get(targetPath);

        // Create parent directories if they don't exist
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }

        // Check if target already exists
        if (Files.exists(target) && !overwrite) {
            remarks.append("Target file already exists and overwrite=false.");
            return false;
        }

        // Copy the file
        Files.copy(sourceFile.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        remarks.append("File copied successfully to: ").append(targetPath);

        return true;
    }

    /**
     * Move file to a new location
     */
    private static boolean moveFile(File sourceFile, Operation operation, StringBuilder remarks) throws IOException {
        String targetPath = operation.getParameter("target_path");
        boolean overwrite = Boolean.parseBoolean(operation.getParameter("overwrite"));

        if (targetPath == null || targetPath.isEmpty()) {
            remarks.append("Target path not specified.");
            return false;
        }

        Path target = Paths.get(targetPath);

        // Create parent directories if they don't exist
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }

        // Check if target already exists
        if (Files.exists(target) && !overwrite) {
            remarks.append("Target file already exists and overwrite=false.");
            return false;
        }

        // Move the file
        Files.move(sourceFile.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        remarks.append("File moved successfully to: ").append(targetPath);

        return true;
    }

    /**
     * Rename a file
     */
    private static boolean renameFile(File file, Operation operation, StringBuilder remarks) {
        String newName = operation.getParameter("new_name");

        if (newName == null || newName.isEmpty()) {
            remarks.append("New name not specified.");
            return false;
        }

        File newFile = new File(file.getParent(), newName);

        // Check if target already exists
        if (newFile.exists()) {
            remarks.append("File with name ").append(newName).append(" already exists.");
            return false;
        }

        // Rename the file
        if (file.renameTo(newFile)) {
            remarks.append("File renamed successfully to: ").append(newName);
            return true;
        } else {
            remarks.append("Failed to rename file.");
            return false;
        }
    }

    /**
     * Format preview content for logging
     */
    private static String formatContentForLog(String content) {
        if (content == null || content.isEmpty()) {
            return "[empty]";
        }

        String[] lines = content.split("\n");
        StringBuilder preview = new StringBuilder();

        for (int i = 0; i < Math.min(lines.length, MAX_LINES_TO_LOG); i++) {
            String line = lines[i];
            if (line.length() > MAX_CHARS_PER_LINE) {
                line = line.substring(0, MAX_CHARS_PER_LINE) + "...";
            }
            preview.append(line).append("\n");
        }

        if (lines.length > MAX_LINES_TO_LOG) {
            preview.append("... (").append(lines.length - MAX_LINES_TO_LOG).append(" more lines)");
        }

        return preview.toString();
    }

//    /**
//     * Helper class for logging
//     */
//    private static class LogManager {
//        public static Logger getLogger(Class<?> clazz) {
//            return Logger.getLogger(clazz.getName());
//        }
//    }
}