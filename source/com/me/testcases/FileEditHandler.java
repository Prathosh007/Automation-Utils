package com.me.testcases;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.testcases.fileOperation.*;
import com.me.util.LogManager;

/**
 * Handler for file editing operations
 */
public class FileEditHandler {
    private static final Logger LOGGER = LogManager.getLogger(FileEditHandler.class, LogManager.LOG_TYPE.FW);
    
    // Maximum number of lines to log for file content
    private static final int MAX_LINES_TO_LOG = 10;
    // Maximum number of characters to preview per line
    private static final int MAX_CHARS_PER_LINE = 100;

    /**
     * Execute a file edit operation
     * 
     * @param op The operation containing file edit parameters
     * @return true if edit was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        op = ResolveOperationParameters.resolveOperationParameters(op);

        String action = op.getParameter("action");
        String filePath = op.getParameter("file_path");
        String fileName = op.getParameter("filename");
        String operationType = op.getOperationType();
        String value = op.getParameter("value");


        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for file_edit operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for file_edit operation");
            return false;
        }

        File file;
        if (fileName == null || fileName.isEmpty()) {
            file = new File(filePath);
        } else {
            File directory = new File(filePath);
            file = new File(directory, fileName);
        }

        if (!file.exists() && !op.getParameter("action").equals("create")) {
            String errorMsg = "File not found: " + file.getAbsolutePath();
            LOGGER.warning("File not found: " + file.getAbsolutePath());
            LOGGER.warning(errorMsg);
            op.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("File Edit Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            String originalContent = "";
            // Read the original file content
            if (!op.getParameter("action").equals("create")) {
                originalContent = new String(Files.readAllBytes(file.toPath()));
            }
            boolean operationResult = false;

            String extension = getFileExtension(String.valueOf(file)).toLowerCase();
            LOGGER.info("File extension: " + extension);
            switch (extension) {
                case "xml":
                    operationResult = XMLFileHandler.executeOperation(op);
                    break;
                case "json":
                    operationResult = JSONFileHandler.executeOperation(op);
                    break;
                case "txt":
//                case "log":
                    operationResult = TextFileHandler.executeOperation(op);
                    break;
                case "properties":
                case "props":
                case "ini":
                case "conf":
                case "prop":
                case "key":
                    case "config":
                    operationResult = ConfigFileHandler.executeOperation(op);
                    break;
//                case "bat":
////                    operationResult = BatFileHandler.executeOperation(op);
//                    break;
                case "pdf":
                    operationResult = PDFFileHandler.executeOperation(op);
                    break;
                case "xlsx":
                    operationResult = XLSXFileHandler.executeOperation(op);
                    break;
                case "csv":
                    operationResult = CSVFileHandler.executeOperation(op);
                    break;
                default:
                    LOGGER.info("No specific handler for this '"+extension+"' extension so consider as text file.");
                    operationResult = TextFileHandler.executeOperation(op);
                    remarkBuilder.append("\nNote: No specific handler for '").append(extension).append("' extension, treated as text file.\n");
            }

            // The file-specific handlers now manage all file operations
            // Add operation stats to remarks
            String updatedContent = new String(Files.readAllBytes(file.toPath()));
            int originalLines = countLines(originalContent);
            int newLines = countLines(updatedContent);
            int linesChanged = Math.abs(newLines - originalLines);

            remarkBuilder.append(op.getRemarks());

            remarkBuilder.append("\nOperation completed successfully:");
            remarkBuilder.append("\n  - Lines before: ").append(originalLines);
            remarkBuilder.append("\n  - Lines after: ").append(newLines);
            remarkBuilder.append("\n  - Lines changed: ").append(linesChanged);
            remarkBuilder.append("\n  - File size before: ").append(formatSize(originalContent.length()));
            remarkBuilder.append("\n  - File size after: ").append(formatSize(updatedContent.length()));

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(remarkBuilder.toString());
            LOGGER.info(remarkBuilder.toString());
            return operationResult;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error editing file: " + file.getAbsolutePath(), e);
            remarkBuilder.append("\nError editing file: ").append(e.getMessage());
            op.setRemarks(remarkBuilder.toString());
            return false;
        }
    }

    /**
     * Handle update action (replace a key-value pair)
     */
    private static String handleUpdateAction(String content, Operation op, StringBuilder remarks) {
        String keyToUpdate = op.getParameter("key_to_update");//NO I18N
        String newValue = op.getParameter("new_value");//NO I18N
        
        if (keyToUpdate == null || keyToUpdate.isEmpty()) {
            remarks.append("\nError: key_to_update parameter is required for update action");//NO I18N
            throw new IllegalArgumentException("key_to_update parameter is required for update action");//NO I18N
        }
        
        if (newValue == null) {
            newValue = ""; // Allow empty values
        }
        
        remarks.append("\nUpdating key: ").append(keyToUpdate);//NO I18N
        remarks.append("\nNew value: ").append(newValue);//NO I18N
        
        String updatedContent;
        
        // Try to determine file format and handle accordingly
        if (op.getParameter("filename").toLowerCase().endsWith(".properties")) {//NO I18N
            // Java properties file
            Pattern pattern = Pattern.compile("^(\\s*" + Pattern.quote(keyToUpdate) + "\\s*=).*$", Pattern.MULTILINE);//NO I18N
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                // Key exists, record where it was found
                int lineNum = countLines(content.substring(0, matcher.start()));
                String oldLine = matcher.group(0);
                remarks.append("\nFound at line: ").append(lineNum + 1);//NO I18N
                remarks.append("\nOld value: ").append(oldLine.substring(matcher.group(1).length()));//NO I18N
                
                updatedContent = matcher.replaceFirst("$1" + Matcher.quoteReplacement(newValue));//NO I18N
            } else {
                remarks.append("\nKey not found, appending to end of file");//NO I18N
                updatedContent = content;
                if (!updatedContent.endsWith("\n")) {//No I18N
                    updatedContent += "\n";//No I18N
                }
                updatedContent += keyToUpdate + "=" + newValue + "\n";//NO I18N
            }
        } else if (op.getParameter("filename").toLowerCase().endsWith(".xml")) {//NO I18N
            // XML file (basic approach)
            remarks.append("\nAttempting XML update using simple pattern matching");//NO I18N
            Pattern pattern = Pattern.compile("<" + Pattern.quote(keyToUpdate) + ">([^<]*)</" + Pattern.quote(keyToUpdate) + ">");//NO I18N
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                int lineNum = countLines(content.substring(0, matcher.start()));
                remarks.append("\nFound at line: ").append(lineNum + 1);//NO I18N
                remarks.append("\nOld value: ").append(matcher.group(1));//NO I18N
                updatedContent = matcher.replaceFirst("<" + keyToUpdate + ">" + Matcher.quoteReplacement(newValue) + "</" + keyToUpdate + ">");
            } else {
                // Try attribute pattern
                pattern = Pattern.compile(Pattern.quote(keyToUpdate) + "\\s*=\\s*\"([^\"]*)\"");//NO I18N
                matcher = pattern.matcher(content);
                
                if (matcher.find()) {
                    int lineNum = countLines(content.substring(0, matcher.start()));
                    remarks.append("\nFound attribute at line: ").append(lineNum + 1);//NO I18N
                    remarks.append("\nOld value: ").append(matcher.group(1));//NO I18N
                    updatedContent = matcher.replaceFirst(keyToUpdate + "=\"" + Matcher.quoteReplacement(newValue) + "\"");
                } else {
                    remarks.append("\nKey not found in XML content");//NO I18N
                    updatedContent = content; // No change
                }
            }
        } else {
            // Generic key=value or key:value approach
            Pattern pattern = Pattern.compile("^(\\s*" + Pattern.quote(keyToUpdate) + "\\s*[=:])(.*)$", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                int lineNum = countLines(content.substring(0, matcher.start()));
                remarks.append("\nFound at line: ").append(lineNum + 1);//NO I18N
                remarks.append("\nOld value: ").append(matcher.group(2).trim());//NO I18N
                updatedContent = matcher.replaceFirst("$1 " + Matcher.quoteReplacement(newValue));
            } else {
                remarks.append("\nKey not found in file content");//NO I18N
                // Maybe it should be appended? For now don't change anything
                updatedContent = content; 
            }
        }
        
        return updatedContent;
    }

    /**
     * Handle replace action (find and replace text)
     */
    private static String handleReplaceAction(String content, Operation op, StringBuilder remarks) {
        String replacedValue = op.getParameter("replaced_value");//NO I18N
        String newValue = op.getParameter("new_value");//NO I18N
        
        if (replacedValue == null || replacedValue.isEmpty()) {
            remarks.append("\nError: replaced_value parameter is required for replace action");//NO I18N
            throw new IllegalArgumentException("replaced_value parameter is required for replace action");//NO I18N
        }
        
        if (newValue == null) {
            newValue = ""; // Allow empty replacements (deletion)
        }
        
        remarks.append("\nReplacing: ").append(replacedValue);//NO I18N
        remarks.append("\nNew value: ").append(newValue);//NO I18N
        
        int replaceCount = 0;
        StringBuilder contentBuilder = new StringBuilder(content);
        
        // Find all occurrences and display line numbers
        remarks.append("\nOccurrences: ");//NO I18N
        List<Integer> lineNums = new ArrayList<>();
        
        int lastIndex = -1;
        int index;
        while ((index = contentBuilder.indexOf(replacedValue, lastIndex + 1)) != -1) {
            replaceCount++;
            
            // Calculate line number of this occurrence
            int lineNum = countLines(content.substring(0, index)) + 1;
            lineNums.add(lineNum);
            
            lastIndex = index;
        }
        
        if (lineNums.isEmpty()) {
            remarks.append("Text not found in file");//NO I18N
        } else {
            remarks.append(lineNums.size()).append(" (lines: ");//NO I18N
            remarks.append(String.join(", ", lineNums.stream().map(String::valueOf).collect(Collectors.toList())));
            remarks.append(")");
        }
        
        // Do the replacement
        String updatedContent = content.replace(replacedValue, newValue);
        
        return updatedContent;
    }

    /**
     * Handle insert action (insert at specific line)
     */
    private static String handleInsertAction(String content, Operation op, StringBuilder remarks) {
        String lineParam = op.getParameter("line");//NO I18N
        String valueToInsert = op.getParameter("value_to_insert");//NO I18N
        
        if (lineParam == null || lineParam.isEmpty()) {
            remarks.append("\nError: line parameter is required for insert action");//NO I18N
            throw new IllegalArgumentException("line parameter is required for insert action");//NO I18N
        }
        
        if (valueToInsert == null) {
            valueToInsert = ""; // Allow empty insertions
        }
        
        // Parse line number
        int lineNum;
        try {
            lineNum = Integer.parseInt(lineParam);
        } catch (NumberFormatException e) {
            remarks.append("\nError: Invalid line number: ").append(lineParam);//NO I18N
            throw new IllegalArgumentException("Invalid line number: " + lineParam, e);//NO I18N
        }
        
        remarks.append("\nInserting at line: ").append(lineNum);//NO I18N
        remarks.append("\nValue to insert: ").append(valueToInsert);//NO I18N
        
        // Split content into lines
        String[] lines = content.split("\r\n|\r|\n", -1);
        
        // Validate line number
        if (lineNum < 1) {
            lineNum = 1; // Insert at the beginning
            remarks.append("\nAdjusting line number to 1 (beginning of file)");//NO I18N
        } else if (lineNum > lines.length + 1) {
            lineNum = lines.length + 1; // Append at the end
            remarks.append("\nAdjusting line number to ").append(lineNum).append(" (end of file)");//NO I18N
        }
        
        // Insert the value at the specified line
        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == lineNum - 1) {
                newContent.append(valueToInsert).append(System.lineSeparator());
            }
            newContent.append(lines[i]);
            if (i < lines.length - 1) {
                newContent.append(System.lineSeparator());
            }
        }
        
        // Handle case where insertion is at the end
        if (lineNum > lines.length) {
            if (lines.length > 0) {
                newContent.append(System.lineSeparator());
            }
            newContent.append(valueToInsert);
        }
        
        remarks.append("\nFile had ").append(lines.length).append(" lines before insertion");//NO I18N
        remarks.append("\nNew line added at position ").append(lineNum);//NO I18N
        
        return newContent.toString();
    }

    /**
     * Handle insert_after action (insert after specific text)
     */
    private static String handleInsertAfterAction(String content, Operation op, StringBuilder remarks) {
        String afterText = op.getParameter("after_which_text");//NO I18N
        String valueToInsert = op.getParameter("value_to_insert");//NO I18N
        
        if (afterText == null || afterText.isEmpty()) {
            remarks.append("\nError: after_which_text parameter is required for insert_after action");//NO I18N
            throw new IllegalArgumentException("after_which_text parameter is required for insert_after action");//NO I18N
        }
        
        if (valueToInsert == null) {
            valueToInsert = ""; // Allow empty insertions
        }
        
        remarks.append("\nInserting after text: ").append(afterText);//NO I18N
        remarks.append("\nValue to insert: ").append(valueToInsert);//NO I18N
        
        int index = content.indexOf(afterText);
        
        if (index == -1) {
            remarks.append("\nWarning: Text not found, nothing inserted");//NO I18N
            return content; // No change
        }
        
        // Calculate line number
        int lineNum = countLines(content.substring(0, index)) + 1;
        remarks.append("\nFound target text at line: ").append(lineNum);//NO I18N
        
        // Insert after the text
        int insertPos = index + afterText.length();
        String updatedContent = content.substring(0, insertPos) + valueToInsert + content.substring(insertPos);
        
        return updatedContent;
    }

    /**
     * Count the number of lines in a string
     */
    private static int countLines(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        return str.split("\r\n|\r|\n", -1).length;
    }

    /**
     * Format file size in human-readable format
     */
    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " bytes";//No I18N
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);//NO I18N
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024));//NO I18N
        }
    }

    /**
     * Log a preview of file content (with limits)
     */
    private static void logFileContentPreview(String context, String filePath) {
        LOGGER.info("---------- FILE CONTENT " + context + " ----------");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null && lineCount < MAX_LINES_TO_LOG) {
                // Truncate long lines
                if (line.length() > MAX_CHARS_PER_LINE) {
                    LOGGER.info(String.format("Line %d: %s... [truncated, %d chars total]", //NO I18N
                        lineCount + 1, line.substring(0, MAX_CHARS_PER_LINE), line.length()));
                } else {
                    LOGGER.info(String.format("Line %d: %s", lineCount + 1, line));//NO I18N
                }
                lineCount++;
            }
            
            if (lineCount == MAX_LINES_TO_LOG) {
                LOGGER.info("... [file content truncated, showing first " + MAX_LINES_TO_LOG + //NO I18N
                           " lines only. Total lines: " + countLines(filePath) + "]");//NO I18N
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read file for preview: " + filePath, e);//NO I18N
        }
        
        LOGGER.info("-----------------------------------------------------");//NO I18N
    }
    
    /**
     * Update a key-value pair in a properties or config file
     */
    private static boolean updateKeyValueInFile(Operation op, String fullPath) {
        String keyToUpdate = op.getParameter("key_to_update");//NO I18N
        String newValue = op.getParameter("new_value");//NO I18N
        
        LOGGER.info("Updating key-value in file:");//NO I18N
        LOGGER.info("- Key to update: " + keyToUpdate);//NO I18N
        LOGGER.info("- New value: " + newValue);//NO I18N
        
        if (keyToUpdate == null || keyToUpdate.isEmpty()) {
            LOGGER.severe("Missing required parameter: key_to_update");//NO I18N
            return false;
        }
        
        if (newValue == null) {
            LOGGER.warning("New value is null, will remove the key");//NO I18N
            newValue = "";
        }
        
        try {
            String extension = getFileExtension(fullPath).toLowerCase();
            
            // Handle based on file type
            if (extension.equals("properties")) {//NO I18N
                return updatePropertyFile(fullPath, keyToUpdate, newValue);
            } else if (extension.equals("ini")) {//NO I18N
                return updateIniFile(fullPath, keyToUpdate, newValue);
            } else {
                // For other file types, do a basic replacement
                String fileContent = new String(Files.readAllBytes(Paths.get(fullPath)), StandardCharsets.UTF_8);
                String oldPattern = keyToUpdate + "\\s*=\\s*[^\\r\\n]*";//NO I18N
                String newPattern = keyToUpdate + "=" + newValue;
                
                LOGGER.info("Using regex pattern replacement: " + oldPattern + " -> " + newPattern);//NO I18N
                
                if (fileContent.matches("(?s).*" + oldPattern + ".*")) {//NO I18N
                    String updatedContent = fileContent.replaceAll(oldPattern, newPattern);
                    Files.write(Paths.get(fullPath), updatedContent.getBytes(StandardCharsets.UTF_8));
                    LOGGER.info("Key-value pair updated successfully");
                    return true;
                } else {
                    LOGGER.warning("Key not found in file: " + keyToUpdate);//NO I18N
                    return false;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error updating key-value in file: " + fullPath, e);//NO I18N
            return false;
        }
    }
    
    /**
     * Update a key-value pair in a .properties file
     */
    private static boolean updatePropertyFile(String fullPath, String keyToUpdate, String newValue) {
        LOGGER.info("Handling .properties file update");//NO I18N
        
        try {
            Properties props = new Properties();
            
            // Read the properties file
            try (FileReader reader = new FileReader(fullPath)) {
                props.load(reader);
            }
            
            // Check if key exists
            boolean keyExists = props.containsKey(keyToUpdate);
            LOGGER.info("Key exists: " + keyExists);//NO I18N
            
            if (keyExists) {
                LOGGER.info("Current value: " + props.getProperty(keyToUpdate));//NO I18N
            }
            
            // Update the key
            props.setProperty(keyToUpdate, newValue);
            
            // Write back to the file
            try (FileWriter writer = new FileWriter(fullPath)) {
                props.store(writer, "Updated by FileReader");//NO I18N
                LOGGER.info("Property file updated successfully");//NO I18N
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error updating properties file", e);//NO I18N
            return false;
        }
    }
    
    /**
     * Update a key-value pair in an .ini file
     */
    private static boolean updateIniFile(String fullPath, String keyToUpdate, String newValue) {
        LOGGER.info("Handling .ini file update");//NO I18N
        
        try {
            List<String> lines = new ArrayList<>();
            boolean keyFound = false;
            
            // Read the file line by line
            try (BufferedReader reader = new BufferedReader(new FileReader(fullPath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Check if this line contains the key
                    if (line.matches("\\s*" + Pattern.quote(keyToUpdate) + "\\s*=.*")) {
                        LOGGER.info("Found key on line: " + line);//NO I18N
                        lines.add(keyToUpdate + "=" + newValue);
                        keyFound = true;
                    } else {
                        lines.add(line);
                    }
                }
            }
            
            if (!keyFound) {
                LOGGER.warning("Key not found in INI file: " + keyToUpdate);//NO I18N
                return false;
            }
            
            // Write the modified content back to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fullPath))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
                LOGGER.info("INI file updated successfully");//NO I18N
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error updating INI file", e);//NO I18N
            return false;
        }
    }
    
    /**
     * Replace text in a file using find and replace
     */
    private static boolean replaceTextInFile(Operation op, String fullPath) {
        String textToFind = op.getParameter("text_to_find");//NO I18N
        String replacementText = op.getParameter("replacement_text");//NO I18N
        boolean regexMode = Boolean.parseBoolean(op.getParameter("regex_mode"));//NO I18N
        
        LOGGER.info("Replacing text in file:");//NO I18N
        LOGGER.info("- Text to find: " + textToFind);//NO I18N
        LOGGER.info("- Replacement: " + replacementText);//NO I18N
        LOGGER.info("- Using regex: " + regexMode);//NO I18N
        
        if (textToFind == null || textToFind.isEmpty()) {
            LOGGER.severe("Missing required parameter: text_to_find");//NO I18N
            return false;
        }
        
        if (replacementText == null) {
            LOGGER.warning("Replacement text is null, will replace with empty string");//NO I18N
            replacementText = "";
        }
        
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(Paths.get(fullPath)), StandardCharsets.UTF_8);
            
            // Store the original length for verification
            int originalLength = content.length();
            
            // Perform replacement
            String updatedContent;
            if (regexMode) {
                updatedContent = content.replaceAll(textToFind, replacementText);
            } else {
                updatedContent = content.replace(textToFind, replacementText);
            }
            
            // Check if any replacements were made
            if (updatedContent.length() == originalLength && updatedContent.equals(content)) {
                LOGGER.warning("No matches found for the text to replace");//NO I18N
                return false;
            }
            
            // Write the updated content back to the file
            Files.write(Paths.get(fullPath), updatedContent.getBytes(StandardCharsets.UTF_8));
            
            LOGGER.info("Text replacement completed successfully");//NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error replacing text in file: " + fullPath, e);//NO I18N
            return false;
        }
    }
    
    /**
     * Insert text at a specific line in a file
     */
    private static boolean insertTextAtLine(Operation op, String fullPath) {
        String textToInsert = op.getParameter("text_to_insert");//NO I18N
        String lineNumberStr = op.getParameter("line_number");//NO I18N
        //NO I18N
        LOGGER.info("Inserting text at line:");//NO I18N
        LOGGER.info("- Text to insert: " + textToInsert);//NO I18N
        LOGGER.info("- Line number: " + lineNumberStr);//NO I18N
        
        if (textToInsert == null) {
            LOGGER.warning("Text to insert is null, will insert empty line");//NO I18N
            textToInsert = "";
        }
        
        if (lineNumberStr == null || lineNumberStr.isEmpty()) {
            LOGGER.severe("Missing required parameter: line_number");//NO I18N
            return false;
        }
        
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(lineNumberStr);
            if (lineNumber < 1) {
                LOGGER.severe("Invalid line number: " + lineNumber + ". Must be at least 1.");//NO I18N
                return false;
            }
        } catch (NumberFormatException e) {
            LOGGER.severe("Invalid line number format: " + lineNumberStr);//NO I18N
            return false;
        }
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(fullPath), StandardCharsets.UTF_8);
            
            // Check if line number is within range
            if (lineNumber > lines.size() + 1) {
                LOGGER.warning("Line number " + lineNumber + " exceeds file length (" + //NO I18N
                              lines.size() + " lines). Will append to end.");//NO I18N
                lines.add(textToInsert);
            } else {
                // Insert at specified position (line numbers are 1-based, list indices are 0-based)
                lines.add(lineNumber - 1, textToInsert);
                LOGGER.info("Text inserted at line " + lineNumber);//NO I18N
            }
            
            // Write the modified content back to the file
            Files.write(Paths.get(fullPath), lines, StandardCharsets.UTF_8);
            //NO I18N
            LOGGER.info("Text insertion completed successfully");//NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error inserting text at line in file: " + fullPath, e);//NO I18N
            return false;
        }
    }
    
    /**
     * Insert text after a specific pattern in a file
     */
    private static boolean insertTextAfterPattern(Operation op, String fullPath) {
        String pattern = op.getParameter("pattern");//NO I18N
        String textToInsert = op.getParameter("text_to_insert");//NO I18N
        boolean regexMode = Boolean.parseBoolean(op.getParameter("regex_mode"));//NO I18N
        
        LOGGER.info("Inserting text after pattern:");//NO I18N
        LOGGER.info("- Pattern: " + pattern);//NO I18N
        LOGGER.info("- Text to insert: " + textToInsert);//NO I18N
        LOGGER.info("- Using regex: " + regexMode);//NO I18N
        
        if (pattern == null || pattern.isEmpty()) {
            LOGGER.severe("Missing required parameter: pattern");//NO I18N
            return false;
        }
        
        if (textToInsert == null) {
            LOGGER.warning("Text to insert is null, will insert empty line");//NO I18N
            textToInsert = "";
        }
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(fullPath), StandardCharsets.UTF_8);
            boolean patternFound = false;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean matches;
                
                if (regexMode) {
                    matches = line.matches(".*" + pattern + ".*");
                } else {
                    matches = line.contains(pattern);
                }
                
                if (matches) {
                    lines.add(i + 1, textToInsert);
                    patternFound = true;
                    LOGGER.info("Pattern found on line " + (i + 1) + ", inserted text after it");//NO I18N
                    break;
                }
            }
            
            if (!patternFound) {
                LOGGER.warning("Pattern not found in file: " + pattern);//NO I18N
                return false;
            }
            
            // Write the modified content back to the file
            Files.write(Paths.get(fullPath), lines, StandardCharsets.UTF_8);
            
            LOGGER.info("Text insertion after pattern completed successfully");//NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error inserting text after pattern in file: " + fullPath, e);//NO I18N
            return false;
        }
    }
    
    /**
     * Extract the file extension from a file path
     * 
     * @param filePath the path to the file
     * @return the file extension (without the dot) or an empty string if no extension
     */
        private static String getFileExtension(String filePath) {
            if (filePath == null) {
                return "";
            }
            
            int lastDotIndex = filePath.lastIndexOf('.');
            if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
                return ""; // No extension or ends with a dot
            }
            
            return filePath.substring(lastDotIndex + 1);
        }
        
    /**
     * Resolve placeholders in path strings like server_home
     * 
     * @param path Path that may contain placeholders
     * @return Resolved path with placeholders replaced with actual paths
     */
    private static String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        
        // Handle the server_home placeholder
        if (path.contains("server_home")) {
            // First, ensure we're using the test context for server.home
            ServerUtils.setupServerHomeProperty("test");//NO I18N
            
            // Get the server home from system property (set up by ServerUtils)
            String serverHome = System.getProperty("server.home");//NO I18N
            
            // If still null, fall back to product server home directly
            if (serverHome == null) {
                serverHome = ServerUtils.getProductServerHome();
            }
            
            LOGGER.info("Resolved server_home to: " + serverHome);//NO I18N
            
            // Replace server_home with actual path
            path = path.replace("server_home", serverHome);//NO I18N
        }
        
        // Could handle other path placeholders here as needed
        
        return path;
    }
}

