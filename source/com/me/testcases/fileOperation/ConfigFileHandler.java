package com.me.testcases.fileOperation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.me.Operation;
import com.me.util.LogManager;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

/**
 * Handler for configuration file editing operations
 * Supports .conf, .ini, and .properties files
 */
public class ConfigFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(ConfigFileHandler.class, LogManager.LOG_TYPE.FW);

    // Maximum number of lines to log for file content
    private static final int MAX_LINES_TO_LOG = 10;

    /**
     * Execute a configuration file edit operation
     *
     * @param op The operation containing file edit parameters
     * @return true if edit was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        String action = op.getParameter("action");
        String filePath = op.getParameter("file_path");
        String fileName = op.getParameter("filename");
//        String operationType = op.getOperationType();
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
        File directory = new File(filePath);
        if (fileName == null || fileName.isEmpty()) {
            file = new File(filePath);
        } else {
            file = new File(directory, fileName);
        }

        if (!file.exists()) {
            String errorMsg = "File not found: " + file.getAbsolutePath();//NO I18N
            LOGGER.warning(errorMsg);
            op.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("Config File Edit Operation: ").append(action).append("\n");//NO I18N
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");//NO I18N

        try {
            // Read the original file content
            String originalContent = new String(Files.readAllBytes(file.toPath()));
            boolean success = false;

            switch (action.toLowerCase()) {
                case "update":
                    success = handleUpdateAction(file,op, remarkBuilder);
                    break;
                case "replace":
                    success = handleReplaceAction(file, op, remarkBuilder);
                    break;
                case "delete":
                    success = handleDeleteAction(file, op, remarkBuilder);
                    break;
                case "value_should_be_present":
                    success = checkValuePresence(file, op, remarkBuilder, true);
                    break;
                case "value_should_be_removed":
                    success = checkValuePresence(file, op, remarkBuilder, false);
                    break;
                default:
                    LOGGER.warning("Unsupported action: " + action);//NO I18N
                    remarkBuilder.append("Error: Unsupported action ").append(action);//NO I18N
                    op.setRemarks(remarkBuilder.toString());
                    return false;
            }

//            // Check if content actually changed
//            if (originalContent.equals(newContent)) {
//                remarkBuilder.append("\nWarning: File content unchanged (no changes were made)");//NO I18N
//                op.setRemarks(remarkBuilder.toString());
//                return true; // Consider this a success, but with warning
//            }
//
//            // Create backup
//            String backupFileName = fileName + ".bak." + System.currentTimeMillis();//NO I18N
//            File backupFile = new File(directory, backupFileName);
//            Files.copy(file.toPath(), backupFile.toPath());
//            remarkBuilder.append("\nBackup created: ").append(backupFile.getName());//NO I18N
//
//            // Write the modified content
//            Files.write(file.toPath(), newContent.getBytes());

            // Add stats to remarks
            remarkBuilder.append(op.getRemarks());
            int originalLines = countLines(originalContent);
            String newContent = new String(Files.readAllBytes(file.toPath()));
            int newLines = countLines(newContent);
            int linesChanged = Math.abs(newLines - originalLines);

            remarkBuilder.append("\nOperation completed successfully:");//NO I18N
            remarkBuilder.append("\n  - Lines before: ").append(originalLines);//NO I18N
            remarkBuilder.append("\n  - Lines after: ").append(newLines);//NO I18N
            remarkBuilder.append("\n  - Lines changed: ").append(linesChanged);//NO I18N
            remarkBuilder.append("\n  - File size before: ").append(formatSize(originalContent.length()));//NO I18N
            remarkBuilder.append("\n  - File size after: ").append(formatSize(newContent.length()));//NO I18N

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(remarkBuilder.toString());
            LOGGER.info(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error editing file: " + file.getAbsolutePath(), e);//NO I18N
            remarkBuilder.append("\nError editing file: ").append(e.getMessage());//NO I18N
            op.setRemarks(remarkBuilder.toString());
            return false;
        }
    }


    /**
     * Check if a value is present or absent in a configuration file
     */
    private static boolean checkValuePresence(File file, Operation op, StringBuilder remarks, boolean shouldExist) {
        try {
            String keyToCheck = op.getParameter("key");
            String expectedValue = op.getParameter("value");
            String section = op.getParameter("section"); // For INI files

            if (keyToCheck == null || keyToCheck.isEmpty()) {
                remarks.append("\nError: key parameter is required for checking value presence");
                return false;
            }

            // Read file content
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String fileName = file.getName().toLowerCase();
            String foundValue = null;
            boolean keyExists = false;

            // Check based on file type
            if (fileName.endsWith(".properties") || fileName.endsWith(".props") || fileName.endsWith(".prop")) {
                // Properties format
                Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(keyToCheck) + "\\s*=(.*)$", Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(content);

                if (matcher.find()) {
                    keyExists = true;
                    foundValue = matcher.group(1).trim();
                    LOGGER.log(Level.INFO,"Found key: " + keyToCheck + " with value: " + foundValue);
                }
            } else if (fileName.endsWith(".ini")) {
                // INI format - need to respect sections
                List<String> lines = Arrays.asList(content.split("\r\n|\r|\n", -1));
                boolean inTargetSection = (section == null || section.isEmpty());

                for (String line : lines) {
                    // Check for section headers
                    if (line.matches("\\s*\\[.*\\]\\s*")) {
                        String currentSection = line.trim().replaceAll("^\\[|\\]$", "");
                        inTargetSection = (section == null || section.isEmpty() || currentSection.equals(section));
                        continue;
                    }

                    // Check for key in target section
                    if (inTargetSection && line.matches("\\s*" + Pattern.quote(keyToCheck) + "\\s*=.*")) {
                        keyExists = true;
                        foundValue = line.replaceFirst("\\s*" + Pattern.quote(keyToCheck) + "\\s*=\\s*", "").trim();
                        LOGGER.log(Level.INFO,"Found key: " + keyToCheck + " with value: " + foundValue);
                        break;
                    }
                }
            } else if (fileName.endsWith(".conf") || fileName.endsWith(".key")) {
                // CONF format - check both key=value and key: value patterns
                Pattern equalsPattern = Pattern.compile("^\\s*" + Pattern.quote(keyToCheck) + "\\s*=\\s*(.*)$", Pattern.MULTILINE);
                Pattern colonPattern = Pattern.compile("^\\s*" + Pattern.quote(keyToCheck) + "\\s*:\\s*(.*)$", Pattern.MULTILINE);

                Matcher equalsMatcher = equalsPattern.matcher(content);
                Matcher colonMatcher = colonPattern.matcher(content);

                if (equalsMatcher.find()) {
                    keyExists = true;
                    foundValue = equalsMatcher.group(1).trim();
                    LOGGER.log(Level.INFO,"Found key: " + keyToCheck + " with value: " + foundValue);
                } else if (colonMatcher.find()) {
                    keyExists = true;
                    foundValue = colonMatcher.group(1).trim();
                    LOGGER.log(Level.INFO,"Found key: " + keyToCheck + " with value: " + foundValue);
                }
            } else {
                // Generic approach for unknown formats
                Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(keyToCheck) + "\\s*[=:]\\s*(.*)$", Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(content);

                if (matcher.find()) {
                    keyExists = true;
                    foundValue = matcher.group(1).trim();
                    LOGGER.log(Level.INFO,"Found key: " + keyToCheck + " with value: " + foundValue);
                }
            }

            // Evaluate result based on expected presence
            boolean valueMatches = expectedValue != null && keyExists && foundValue.equals(expectedValue);
            LOGGER.info("Key existence: " + keyExists + ", Value matches: " + valueMatches + ", Expected value: " + expectedValue);
            boolean result = (expectedValue == null) ? keyExists : valueMatches;
            LOGGER.info("Final result for key check: " + result + ", Should exist: " + shouldExist);

            if (shouldExist == result) {
                if (shouldExist) {
//                    remarks.append("\n✓ Found key: ").append(keyToCheck);
                    if (section != null && !section.isEmpty()) {
                        remarks.append(" in section [").append(section).append("]");
                        LOGGER.info("Key found in section: " + section);
                    }
                    remarks.append("\n Actual Value: ").append(foundValue);
                    LOGGER.info("Key found with value: " + foundValue);
                    if (expectedValue != null) {
                        remarks.append("\n  Expected value: ").append(expectedValue);
                        LOGGER.info("Expected value: " + expectedValue);
                    }
                    LOGGER.log(Level.INFO, "Key check successful: " + keyToCheck + " with value: " + foundValue);
                    if (op.hasNote()){
                        saveNote(op, foundValue);
                    }
                    return true;
                } else {
                    remarks.append("\n✓ Confirmed key is absent: ").append(keyToCheck);
                    LOGGER.info("Key confirmed absent: " + keyToCheck);
                    if (section != null && !section.isEmpty()) {
                        remarks.append(" in section [").append(section).append("]");
                    }
                    return true;
                }
            } else {
                if (shouldExist) {
                    remarks.append("\n✗ Key not found or value mismatch: ").append(keyToCheck);
                    if (section != null && !section.isEmpty()) {
                        remarks.append(" in section [").append(section).append("]");
                    }
                    if (keyExists && expectedValue != null) {
                        remarks.append("\n  Expected: ").append(expectedValue);
                        remarks.append("\n  Actual: ").append(foundValue);
                    }
                } else {
                    remarks.append("\n✗ Key exists but should not: ").append(keyToCheck);
                    if (section != null && !section.isEmpty()) {
                        remarks.append(" in section [").append(section).append("]");
                    }
                    remarks.append("\n  Current value: ").append(foundValue);
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking config value", e);
            remarks.append("\nError: ").append(e.getMessage());
            return true;
        }
    }





    /**
     * Handle update action (replace a key-value pair)
     */
    private static boolean handleUpdateAction(File file, Operation op, StringBuilder remarks) {
        String keyToUpdate = op.getParameter("key_to_update");//NO I18N
        String newValue = op.getParameter("new_value");//NO I18N
        String section = op.getParameter("section"); // For INI files
        boolean success = false;

        if (keyToUpdate == null || keyToUpdate.isEmpty()) {
            remarks.append("\nError: key_to_update parameter is required for update action");//NO I18N
            throw new IllegalArgumentException("key_to_update parameter is required for update action");//NO I18N
        }

        if (newValue == null) {
            newValue = ""; // Allow empty values
        }

        remarks.append("\nUpdating key: ").append(keyToUpdate);//NO I18N
        remarks.append("\nNew value: ").append(newValue);//NO I18N

        if (section != null && !section.isEmpty()) {
            remarks.append("\nIn section: ").append(section);//NO I18N
        }


        // Determine file type by extension
        if (file.getName().endsWith(".properties") || file.getName().endsWith(".props") || file.getName().endsWith(".prop")) {
            success = handlePropertiesUpdate(file, keyToUpdate, newValue, remarks);
        } else if (file.getName().endsWith(".ini")) {
            success = handleIniUpdate(file, keyToUpdate, newValue, section, remarks);
        } else if (file.getName().endsWith(".conf")) {
            success = handlePropertiesUpdate(file, keyToUpdate, newValue, remarks);
        } else {
            LOGGER.severe("Unsupported file type: " + file.getName());//NO I18N
        }
        op.setRemarks(remarks.toString());

        return success;
    }

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

    private static boolean handlePropertiesUpdate(File file, String keyToUpdate, String newValue, StringBuilder remarks) {
        LOGGER.info("Updating "+getFileExtension(file.getAbsolutePath())+" file: key=" + keyToUpdate + ", newValue=" + newValue); //NO I18N

        // Use Java Properties API
        Properties props = new Properties();

        try {
            // Load existing properties from file
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
            }

            // Check if key exists and record original value
            String originalValue = props.getProperty(keyToUpdate);
            if (originalValue != null) {
                LOGGER.info("Found existing key: " + keyToUpdate + " with value: " + originalValue); //NO I18N
                remarks.append("\nFound key: ").append(keyToUpdate); //NO I18N
                remarks.append("\nOld value: ").append(originalValue); //NO I18N
            } else {
                LOGGER.info("Key not found, will add new "+getFileExtension(file.getAbsolutePath())+": " + keyToUpdate); //NO I18N
                remarks.append("\nKey not found, appending to ").append(getFileExtension(file.getAbsolutePath())); //NO I18N
            }

            // Update or add the property using Properties.setProperty
            props.setProperty(keyToUpdate, newValue);
            LOGGER.info(getFileExtension(file.getAbsolutePath())+" updated: " + keyToUpdate + "=" + newValue); //NO I18N

            // Write back to file
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, null); // null means no additional comments
            }

            LOGGER.info(getFileExtension(file.getAbsolutePath())+" file update completed successfully"); //NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing "+getFileExtension(file.getAbsolutePath())+" file", e); //NO I18N
            remarks.append("\nError processing "+getFileExtension(file.getAbsolutePath())+":").append(e.getMessage()); //NO I18N
            return false;
        }
    }
    /**
     * Handle INI file update
     */
    private static boolean handleIniUpdate(File file, String keyToUpdate, String newValue, String section, StringBuilder remarks) {
        LOGGER.info("Updating INI file: key=" + keyToUpdate + ", section=" + section + ", newValue=" + newValue); //NO I18N

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> newLines = new ArrayList<>();

            boolean inTargetSection = (section == null || section.isEmpty());
            boolean keyFound = false;

            // First pass: try to update existing key
            for (String line : lines) {
                // Check for section headers
                if (line.matches("\\s*\\[.*\\]\\s*")) {
                    String currentSection = line.trim().replaceAll("^\\[|\\]$", "");
                    inTargetSection = (section == null || section.isEmpty() || currentSection.equals(section));
                    newLines.add(line);
                    continue;
                }

                // Check for key-value pair in target section
                if (inTargetSection && !keyFound && line.matches("\\s*" + Pattern.quote(keyToUpdate) + "\\s*=.*")) {
                    LOGGER.info("Found key in section: " + keyToUpdate); //NO I18N
                    remarks.append("\nFound key in section"); //NO I18N
                    newLines.add(keyToUpdate + "=" + newValue);
                    keyFound = true;
                } else {
                    newLines.add(line);
                }
            }

            // If key not found, add it to the right section
            if (!keyFound) {
                if (section != null && !section.isEmpty()) {
                    // Need to add key to specific section
                    boolean sectionFound = false;
                    List<String> updatedLines = new ArrayList<>();

                    for (String line : newLines) {
                        updatedLines.add(line);

                        // After section header, add our key
                        if (!sectionFound && line.matches("\\s*\\[" + Pattern.quote(section) + "\\]\\s*")) {
                            updatedLines.add(keyToUpdate + "=" + newValue);
                            sectionFound = true;
                            LOGGER.info("Added key to section: " + section); //NO I18N
                            remarks.append("\nAdded key to section: ").append(section); //NO I18N
                        }
                    }

                    // If section not found, add it
                    if (!sectionFound) {
                        LOGGER.info("Section not found, adding section and key: " + section); //NO I18N
                        remarks.append("\nSection not found, adding section and key"); //NO I18N
                        updatedLines.add("");
                        updatedLines.add("[" + section + "]");
                        updatedLines.add(keyToUpdate + "=" + newValue);
                    }

                    newLines = updatedLines;
                } else {
                    // Add to end of file
                    LOGGER.info("Key not found, adding to end of file: " + keyToUpdate); //NO I18N
                    remarks.append("\nKey not found, adding to end of file"); //NO I18N
                    newLines.add(keyToUpdate + "=" + newValue);
                }
            }

            // Write back to file
            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
            LOGGER.info("INI file update completed successfully"); //NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing INI file", e); //NO I18N
            remarks.append("\nError processing INI file: ").append(e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Handle conf file update (.conf typically follows key=value or key: value format)
     */
    private static boolean handleConfUpdate(File file, String keyToUpdate, String newValue, StringBuilder remarks) {
        LOGGER.info("Updating conf file: key=" + keyToUpdate + ", newValue=" + newValue); //NO I18N

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> newLines = new ArrayList<>();
            boolean keyFound = false;

            // Look for both key=value and key: value patterns
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                if (line.matches("\\s*" + Pattern.quote(keyToUpdate) + "\\s*=.*")) {
                    // Found key=value pattern
                    LOGGER.info("Found key=value at line: " + (i + 1) + ": " + line); //NO I18N
                    remarks.append("\nFound key=value at line: ").append(i + 1); //NO I18N
                    remarks.append("\nOld value: ").append(line.replaceFirst("\\s*" + Pattern.quote(keyToUpdate) + "\\s*=\\s*", "")); //NO I18N
                    newLines.add(keyToUpdate + "=" + newValue);
                    keyFound = true;
                } else if (line.matches("\\s*" + Pattern.quote(keyToUpdate) + "\\s*:.*")) {
                    // Found key: value pattern
                    LOGGER.info("Found key: value at line: " + (i + 1) + ": " + line); //NO I18N
                    remarks.append("\nFound key: value at line: ").append(i + 1); //NO I18N
                    remarks.append("\nOld value: ").append(line.replaceFirst("\\s*" + Pattern.quote(keyToUpdate) + "\\s*:\\s*", "")); //NO I18N
                    newLines.add(keyToUpdate + ": " + newValue);
                    keyFound = true;
                } else {
                    newLines.add(line);
                }
            }

            // If key not found, append (use equals sign as default)
            if (!keyFound) {
                LOGGER.info("Key not found, appending to end of file: " + keyToUpdate); //NO I18N
                remarks.append("\nKey not found, appending to end of file with '=' format"); //NO I18N
                if (!newLines.isEmpty() && !newLines.get(newLines.size() - 1).isEmpty()) {
                    newLines.add("");  // Add empty line for better readability
                }
                newLines.add(keyToUpdate + "=" + newValue);
            }

            // Write back to file
            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
            LOGGER.info("Conf file update completed successfully"); //NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing conf file", e); //NO I18N
            remarks.append("\nError processing conf file: ").append(e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Handle generic update for files with unknown format
     */
    private static String handleGenericUpdate(String content, String keyToUpdate, String newValue, StringBuilder remarks) {
        // Try various common formats
        Pattern equalsPattern = Pattern.compile("^(\\s*" + Pattern.quote(keyToUpdate) + "\\s*=).*$", Pattern.MULTILINE);//NO I18N
        Pattern colonPattern = Pattern.compile("^(\\s*" + Pattern.quote(keyToUpdate) + "\\s*:).*$", Pattern.MULTILINE);//NO I18N

        Matcher equalsMatcher = equalsPattern.matcher(content);
        Matcher colonMatcher = colonPattern.matcher(content);

        if (equalsMatcher.find()) {
            int lineNum = countLines(content.substring(0, equalsMatcher.start()));
            remarks.append("\nFound using '=' format at line: ").append(lineNum + 1);//NO I18N
            return equalsMatcher.replaceFirst("$1" + Matcher.quoteReplacement(newValue));//NO I18N
        } else if (colonMatcher.find()) {
            int lineNum = countLines(content.substring(0, colonMatcher.start()));
            remarks.append("\nFound using ':' format at line: ").append(lineNum + 1);//NO I18N
            return colonMatcher.replaceFirst("$1 " + Matcher.quoteReplacement(newValue));//NO I18N
        } else {
            remarks.append("\nKey not found, appending to end of file with '=' format");//NO I18N
            String updatedContent = content;
            if (!updatedContent.endsWith("\n")) {//No I18N
                updatedContent += "\n";//No I18N
            }
            return updatedContent + keyToUpdate + "=" + newValue + "\n";//NO I18N
        }
    }

    /**
     * Handle replace action (find and replace text)
     */
    private static boolean handleReplaceAction(File file, Operation op, StringBuilder remarks) {
        LOGGER.info("Replacing key in file: " + file.getName()); //NO I18N

        String oldKey = op.getParameter("key_to_replace"); //NO I18N
        String newKey = op.getParameter("new_key"); //NO I18N

        if (oldKey == null || oldKey.isEmpty()) {
            remarks.append("\nError: key_to_replace parameter is required for replace action"); //NO I18N
            LOGGER.warning("Missing required parameter: key_to_replace"); //NO I18N
            return false;
        }

        if (newKey == null || newKey.isEmpty()) {
            remarks.append("\nError: new_key parameter is required for replace action"); //NO I18N
            LOGGER.warning("Missing required parameter: new_key"); //NO I18N
            return false;
        }

        remarks.append("\nReplacing key: ").append(oldKey); //NO I18N
        remarks.append("\nNew key: ").append(newKey); //NO I18N

        // Use Java Properties API
        Properties props = new Properties();

        try {
            // Load existing properties from file
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
            }

            // Get the current value for the old key
            String currentValue = props.getProperty(oldKey);

            if (currentValue == null) {
                LOGGER.warning("Key or value not found: " + oldKey); //NO I18N
                remarks.append("\nKey or value not found: ").append(oldKey); //NO I18N
                op.setRemarks(remarks.toString());
                return false;
            }

            remarks.append("\nValue found for key: ").append(currentValue); //NO I18N
            LOGGER.info("Found value for key " + oldKey + ": " + currentValue); //NO I18N

            // Remove the old key
            props.remove(oldKey);
            LOGGER.info("Removed key: " + oldKey); //NO I18N

            // Add the new key with the same value
            props.setProperty(newKey, currentValue);
            LOGGER.info("Added new key: " + newKey + "=" + currentValue); //NO I18N

            // Write back to file
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, null); // null means no additional comments
            }

            LOGGER.info("Key replacement completed successfully"); //NO I18N
            remarks.append("\nKey successfully replaced"); //NO I18N
            op.setRemarks(remarks.toString());
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error replacing key", e); //NO I18N
            remarks.append("\nError replacing key: ").append(e.getMessage()); //NO I18N
            op.setRemarks(remarks.toString());
            return false;
        }
    }

    /**
     * Handle insert action (insert at specific line)
     */
    private static boolean handleInsertAction(File file, Operation op, StringBuilder remarks) {
        LOGGER.info("Inserting text at line in file: " + file.getName()); //NO I18N

        String lineParam = op.getParameter("line");//NO I18N
        String valueToInsert = op.getParameter("value_to_insert");//NO I18N

        if (lineParam == null || lineParam.isEmpty()) {
            remarks.append("\nError: line parameter is required for insert action");//NO I18N
            LOGGER.warning("Missing required parameter: line"); //NO I18N
            return false;
        }

        if (valueToInsert == null) {
            valueToInsert = ""; // Allow empty insertions
            LOGGER.info("Value to insert is null, will use empty line"); //NO I18N
        }

        // Parse line number
        int lineNum;
        try {
            lineNum = Integer.parseInt(lineParam);
        } catch (NumberFormatException e) {
            remarks.append("\nError: Invalid line number: ").append(lineParam);//NO I18N
            LOGGER.warning("Invalid line number: " + lineParam); //NO I18N
            return false;
        }

        remarks.append("\nInserting at line: ").append(lineNum);//NO I18N
        remarks.append("\nValue to insert: ").append(valueToInsert);//NO I18N

        try {
            // Read file content
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            // Validate line number
            if (lineNum < 1) {
                lineNum = 1; // Insert at the beginning
                remarks.append("\nAdjusting line number to 1 (beginning of file)");//NO I18N
                LOGGER.info("Adjusted line number to 1 (beginning of file)"); //NO I18N
            } else if (lineNum > lines.size() + 1) {
                lineNum = lines.size() + 1; // Append at the end
                remarks.append("\nAdjusting line number to ").append(lineNum).append(" (end of file)");//NO I18N
                LOGGER.info("Adjusted line number to " + lineNum + " (end of file)"); //NO I18N
            }

            // Insert the value at the specified line (line numbers are 1-based, list indices are 0-based)
            if (lineNum <= lines.size()) {
                lines.add(lineNum - 1, valueToInsert);
                LOGGER.info("Inserted text at line " + lineNum); //NO I18N
            } else {
                // Insert at the end
                lines.add(valueToInsert);
                LOGGER.info("Appended text at the end of file"); //NO I18N
            }

            // Write back to file
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);

            remarks.append("\nFile had ").append(lines.size() - 1).append(" lines before insertion");//NO I18N
            remarks.append("\nNew line added at position ").append(lineNum);//NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error inserting text at line", e); //NO I18N
            remarks.append("\nError inserting text: ").append(e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Handle insert_after action (insert after specific text)
     */
    private static boolean handleInsertAfterAction(File file, Operation op, StringBuilder remarks) {
        LOGGER.info("Inserting text after pattern in file: " + file.getName()); //NO I18N

        String afterText = op.getParameter("after_which_text");//NO I18N
        String valueToInsert = op.getParameter("value_to_insert");//NO I18N
        boolean useRegex = Boolean.parseBoolean(op.getParameter("use_regex"));//NO I18N

        if (afterText == null || afterText.isEmpty()) {
            remarks.append("\nError: after_which_text parameter is required for insert_after action");//NO I18N
            LOGGER.warning("Missing required parameter: after_which_text"); //NO I18N
            return false;
        }

        if (valueToInsert == null) {
            valueToInsert = ""; // Allow empty insertions
            LOGGER.info("Value to insert is null, will use empty line"); //NO I18N
        }

        remarks.append("\nInserting after text: ").append(afterText);//NO I18N
        remarks.append("\nValue to insert: ").append(valueToInsert);//NO I18N

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            boolean patternFound = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean matches;

                if (useRegex) {
                    LOGGER.info("Using regex pattern matching"); //NO I18N
                    remarks.append("\nUsing regex pattern matching"); //NO I18N
                    try {
                        matches = line.matches(".*" + afterText + ".*");
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Invalid regex pattern: " + afterText, e); //NO I18N
                        remarks.append("\nError: Invalid regex pattern: ").append(e.getMessage());//NO I18N
                        return false;
                    }
                } else {
                    matches = line.contains(afterText);
                }

                if (matches) {
                    lines.add(i + 1, valueToInsert);
                    patternFound = true;
                    LOGGER.info("Pattern found on line " + (i + 1) + ", inserted text after it"); //NO I18N
                    remarks.append("\nPattern found on line ").append(i + 1).append(", inserted text after it"); //NO I18N
                    break;
                }
            }

            if (!patternFound) {
                LOGGER.warning("Pattern not found in file: " + afterText); //NO I18N
                remarks.append("\nPattern not found: ").append(afterText);//NO I18N
                return false;
            }

            // Write back to file
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            LOGGER.info("Text insertion after pattern completed successfully"); //NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error inserting text after pattern", e); //NO I18N
            remarks.append("\nError inserting text: ").append(e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Handle delete action (delete lines or key-value pairs)
     */
    private static boolean handleDeleteAction(File file, Operation op, StringBuilder remarks) {
        LOGGER.info("Deleting content from file: " + file.getName()); //NO I18N
        remarks.append("\nDeleting content from file: ").append(file.getName());//NO I18N

        String keyToDelete = op.getParameter("key_to_delete");//NO I18N

        try {
            if (keyToDelete != null && !keyToDelete.isEmpty()) {
                // Delete key-value pair
                remarks.append("\nKey to delete: ").append(keyToDelete);//NO I18N
                LOGGER.severe("Deleting key: " + keyToDelete); //NO I18N
                op.setRemarks(remarks.toString());
                LOGGER.severe("Deleting key successfully from file: " + file.getName()); //NO I18N
                return handleKeyDeletion(file, keyToDelete, remarks);
            } else {
                remarks.append("\nError: either key_to_delete is required for delete action");//NO I18N
                LOGGER.warning("Missing required parameter: key_to_delete"); //NO I18N
                op.setRemarks(remarks.toString());
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting content", e); //NO I18N
            remarks.append("\nError deleting content: ").append(e.getMessage()); //NO I18N
            op.setRemarks(remarks.toString());
            return false;
        }
    }

    private static boolean handleKeyDeletion(File file, String keyToDelete,StringBuilder remarks) {
        LOGGER.info("Deleting key: " + keyToDelete); //NO I18N

        remarks.append("\nDeleting key: ").append(keyToDelete);//NO I18N

        Properties properties = new Properties();
        try {
            properties.load(new FileReader(file));
        } catch (IOException e) {
            LOGGER.severe("Error loading properties file: " + e.getMessage()); //NO I18N
            return false;
        }

        properties.remove(keyToDelete);
        LOGGER.info("Removed key: " + keyToDelete); //NO I18N
        remarks.append("\nRemoved key: ").append(keyToDelete);//NO I18N
        try {
            properties.store(new FileWriter(file), null);

            LOGGER.info("Key deleted successfully from file: " + file.getName()); //NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving properties file", e); //NO I18N
            remarks.append("\nError saving properties file: ").append(e.getMessage()); //NO I18N
            return false;
        }
    }

    private static boolean handleLineDeletion(File file, String lineToDelete, Operation op, StringBuilder remarks) {
        LOGGER.info("Deleting line(s): " + lineToDelete); //NO I18N
        remarks.append("\nDeleting line(s): ").append(lineToDelete);//NO I18N

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> newLines;

            // Check if it's a range, single line, or pattern
            if (lineToDelete.matches("\\d+")) {
                // Single line
                int lineNum = Integer.parseInt(lineToDelete);

                if (lineNum < 1 || lineNum > lines.size()) {
                    LOGGER.warning("Line number out of range: " + lineNum); //NO I18N
                    remarks.append("\nLine number out of range: ").append(lineNum); //NO I18N
                    return false;
                }

                remarks.append("\nDeleting line ").append(lineNum).append(": ");//NO I18N
                remarks.append(lines.get(lineNum - 1));//NO I18N
                LOGGER.info("Deleting line " + lineNum + ": " + lines.get(lineNum - 1)); //NO I18N

                newLines = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    if (i != lineNum - 1) { // Skip the line to delete
                        newLines.add(lines.get(i));
                    }
                }

            } else if (lineToDelete.matches("\\d+-\\d+")) {
                // Line range
                String[] parts = lineToDelete.split("-");
                int startLine = Integer.parseInt(parts[0]);
                int endLine = Integer.parseInt(parts[1]);

                if (startLine > endLine) {
                    int temp = startLine;
                    startLine = endLine;
                    endLine = temp;
                }

                if (startLine < 1) {
                    startLine = 1;
                }

                if (endLine > lines.size()) {
                    endLine = lines.size();
                }

                LOGGER.info("Deleting lines " + startLine + " to " + endLine); //NO I18N
                remarks.append("\nDeleting lines ").append(startLine).append(" to ").append(endLine);//NO I18N

                newLines = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    if (i < startLine - 1 || i > endLine - 1) { // Outside the range to delete
                        newLines.add(lines.get(i));
                    }
                }

            } else {
                // Pattern to match
                boolean useRegex = Boolean.parseBoolean(op.getParameter("use_regex"));//NO I18N
                List<Integer> deletedLines = new ArrayList<>();
                newLines = new ArrayList<>();

                for (int i = 0; i < lines.size(); i++) {
                    boolean matches;
                    if (useRegex) {
                        try {
                            matches = lines.get(i).matches(lineToDelete);
                            LOGGER.info("Using regex pattern for line deletion"); //NO I18N
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Invalid regex pattern", e); //NO I18N
                            remarks.append("\nInvalid regex pattern: ").append(e.getMessage()); //NO I18N
                            return false;
                        }
                    } else {
                        matches = lines.get(i).contains(lineToDelete);
                    }

                    if (matches) {
                        deletedLines.add(i + 1);
                    } else {
                        newLines.add(lines.get(i));
                    }
                }

                if (deletedLines.isEmpty()) {
                    LOGGER.warning("No lines matched the pattern, no changes made"); //NO I18N
                    remarks.append("\nNo lines matched the pattern, no changes made");//NO I18N
                    return false;
                } else {
                    LOGGER.info("Deleted " + deletedLines.size() + " lines: " + deletedLines); //NO I18N
                    remarks.append("\nDeleted ").append(deletedLines.size()).append(" lines: ");//NO I18N
                    remarks.append(deletedLines);//NO I18N
                }
            }

            // Write back to file
            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
            LOGGER.info("Line deletion completed successfully"); //NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error deleting lines", e); //NO I18N
            remarks.append("\nError deleting lines: ").append(e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Delete a single line by number
     */
    private static String deleteSingleLine(String content, int lineNum, StringBuilder remarks) {
        String[] lines = content.split("\r\n|\r|\n", -1);

        if (lineNum < 1 || lineNum > lines.length) {
            remarks.append("\nLine number out of range: ").append(lineNum);//NO I18N
            remarks.append("\nFile has ").append(lines.length).append(" lines");//NO I18N
            return content; // No change
        }

        remarks.append("\nDeleting line ").append(lineNum).append(": ");//NO I18N
        remarks.append(lines[lineNum - 1]);//NO I18N

        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i != lineNum - 1) { // Skip the line to delete
                newContent.append(lines[i]);
                if (i < lines.length - 1) {
                    newContent.append(System.lineSeparator());
                }
            }
        }

        return newContent.toString();
    }

    /**
     * Delete a range of lines
     */
    private static String deleteLineRange(String content, int startLine, int endLine, StringBuilder remarks) {
        String[] lines = content.split("\r\n|\r|\n", -1);

        if (startLine > endLine) {
            int temp = startLine;
            startLine = endLine;
            endLine = temp;
        }

        if (startLine < 1) {
            startLine = 1;
        }

        if (endLine > lines.length) {
            endLine = lines.length;
        }

        remarks.append("\nDeleting lines ").append(startLine).append(" to ").append(endLine);//NO I18N

        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i < startLine - 1 || i > endLine - 1) { // Outside the range to delete
                newContent.append(lines[i]);
                if (i < lines.length - 1) {
                    newContent.append(System.lineSeparator());
                }
            }
        }

        return newContent.toString();
    }

    /**
     * Delete lines that match a pattern
     */
    private static String deleteLinesByPattern(String content, String pattern, boolean useRegex, StringBuilder remarks) {
        String[] lines = content.split("\r\n|\r|\n", -1);
        List<Integer> deletedLines = new ArrayList<>();

        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            boolean matches;
            if (useRegex) {
                matches = lines[i].matches(pattern);
            } else {
                matches = lines[i].contains(pattern);
            }

            if (matches) {
                deletedLines.add(i + 1);
            } else {
                newContent.append(lines[i]);
                if (i < lines.length - 1) {
                    newContent.append(System.lineSeparator());
                }
            }
        }

        if (deletedLines.isEmpty()) {
            remarks.append("\nNo lines matched the pattern, no changes made");//NO I18N
            return content;
        } else {
            remarks.append("\nDeleted ").append(deletedLines.size()).append(" lines: ");//NO I18N
            remarks.append(deletedLines);//NO I18N
        }

        return newContent.toString();
    }

    /**
     * Handle add_section action (for INI files)
     */
    private static boolean handleAddSectionAction(File file, Operation op, StringBuilder remarks) {
        LOGGER.info("Adding section to file: " + file.getName()); //NO I18N

        String sectionName = op.getParameter("section_name");//NO I18N

        if (sectionName == null || sectionName.isEmpty()) {
            remarks.append("\nError: section_name parameter is required for add_section action");//NO I18N
            LOGGER.warning("Missing required parameter: section_name"); //NO I18N
            return false;
        }

        remarks.append("\nAdding section: [").append(sectionName).append("]");//NO I18N
        LOGGER.info("Adding section: [" + sectionName + "]"); //NO I18N

        // Check if file format supports sections
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".ini") && !fileName.endsWith(".conf")) {
            remarks.append("\nWarning: Adding section to file type that may not support sections");//NO I18N
            LOGGER.warning("Adding section to file type that may not support sections: " + fileName); //NO I18N
        }

        try {
            // Read file content
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            // Check if section already exists
            boolean sectionExists = false;
            for (String line : lines) {
                if (line.matches("\\s*\\[" + Pattern.quote(sectionName) + "\\]\\s*")) {
                    sectionExists = true;
                    break;
                }
            }

            if (sectionExists) {
                LOGGER.info("Section already exists, no changes needed"); //NO I18N
                remarks.append("\nSection already exists, no changes needed");//NO I18N
                return true;
            }

            // Add section at the end with a blank line before it if needed
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
                lines.add(""); // Add empty line for better readability
            }

            lines.add("[" + sectionName + "]");

            // Write back to file
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            LOGGER.info("Section added successfully"); //NO I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error adding section", e); //NO I18N
            remarks.append("\nError adding section: ").append(e.getMessage()); //NO I18N
            return false;
        }
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
     * Count occurrences of a substring in a string
     */
    private static int countOccurrences(String str, String subStr) {
        int count = 0;
        int lastIndex = 0;

        while (lastIndex != -1) {
            lastIndex = str.indexOf(subStr, lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += subStr.length();
            }
        }

        return count;
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
     * Resolve placeholders in path strings
     */
    private static String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // Handle environment variables
        Pattern envPattern = Pattern.compile("\\$\\{([^}]+)\\}");//NO I18N
        Matcher matcher = envPattern.matcher(path);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String envVar = matcher.group(1);
            String envValue = System.getenv(envVar);

            if (envValue == null) {
                // Try system property
                envValue = System.getProperty(envVar);
            }

            if (envValue == null) {
                // Keep the placeholder
                envValue = matcher.group(0);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}