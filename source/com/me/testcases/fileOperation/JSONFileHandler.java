package com.me.testcases.fileOperation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.me.Operation;
import com.me.util.LogManager;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

/**
 * Handler for JSON file operations using only core Java and Jackson
 */
public class JSONFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(JSONFileHandler.class, LogManager.LOG_TYPE.FW);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // Configure ObjectMapper
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Execute a JSON file operation
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
            LOGGER.warning("Action is required for JSON file operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for JSON file operation");
            return false;
        }

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
            LOGGER.warning("File not found: " + file.getAbsolutePath());
            LOGGER.warning(errorMsg);
            operation.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("JSON File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            boolean success = false;

            switch (action.toLowerCase()) {
                case "create":
                    success = createJsonFile(file, operation, remarkBuilder);
                    break;
                case "write":
                    success = writeJsonFile(file, operation, remarkBuilder);
                    break;
                case "update":
                    success = updateJsonNode(file, operation, remarkBuilder);
                    break;
                case "remove":
                    success = removeJsonProperty(file, operation, remarkBuilder);
                    break;
                case "check_value_presence":
                case "value_should_be_present":
                    success = checkValuePresence(file, operation, remarkBuilder, true);
                    break;
                case "check_value_absence":
                case "value_should_be_removed":
                    success = checkValuePresence(file, operation, remarkBuilder, false);
                    break;
                default:
                    String errorMsg = "Unsupported action for JSON file: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing JSON file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }


    /**
     * Check if a JSON value is present or absent
     * Returns the found value as a string if present, or "true" if not found/for other cases
     */
    private static boolean checkValuePresence(File file, Operation operation, StringBuilder remarks, boolean shouldExist) {
        try {
            String path = operation.getParameter("path");
            String expectedValue = operation.getParameter("value");

            if (file.canWrite()) {
                remarks.append("File is writable: ").append(file.getAbsolutePath()).append("\n");
                LOGGER.info("File is writable: " + file.getAbsolutePath());
            } else {
                remarks.append("File is not writable: ").append(file.getAbsolutePath()).append("\n");
                LOGGER.info("File is not writable: " + file.getAbsolutePath());
            }
            if (file.canRead()) {
                remarks.append("File is readable: ").append(file.getAbsolutePath()).append("\n");
                LOGGER.info("File is readable: " + file.getAbsolutePath());
            } else {
                remarks.append("File is not readable: ").append(file.getAbsolutePath()).append("\n");
                LOGGER.info("File is not readable: " + file.getAbsolutePath());
            }

            if (path == null || path.isEmpty()) {
                remarks.append("Error: path parameter is required\n");
                LOGGER.warning("Path parameter is required for JSON value check");
                return false;
            }

            // Read JSON file
            JsonNode rootNode = MAPPER.readTree(file);
            LOGGER.info("The root node is: " + rootNode.toString());

            // Find node at path
            JsonNode targetNode = findNodeByPath(rootNode, path);

            boolean nodeExists = targetNode != null;
            boolean valueMatches = false;
            String foundValue = null;

            if (nodeExists) {
                // Extract the found value
                foundValue = targetNode.isTextual() ? targetNode.asText() : targetNode.toString();

                if (expectedValue != null) {
                    // Check if node value matches expected value
                    try {
                        JsonNode expectedNode = MAPPER.readTree(expectedValue);
                        valueMatches = targetNode.equals(expectedNode);
                    } catch (Exception e) {
                        // If we can't parse as JSON, try as simple string
                        valueMatches = foundValue.equals(expectedValue);
                    }
                }
            } else {
                LOGGER.info("Node not found at path: " + path);
            }

            boolean result = nodeExists;
            if (expectedValue != null && nodeExists) {
                result = valueMatches;
            } else {
                LOGGER.info("Node exists: " + nodeExists + ", Value matches: " + valueMatches);
            }

            if (shouldExist == result) {
                if (shouldExist) {
                    remarks.append("✓ Found JSON path: ").append(path).append("\n");
                    remarks.append("  Value: ").append(foundValue).append("\n");

                    if (expectedValue != null) {
                        remarks.append("  Matches expected value: ").append(expectedValue).append("\n");
                    }

                    // Save note if requested by the operation (preserve previous behavior)
                    if (operation.hasNote() && foundValue != null) {
                        LOGGER.info("Saving note for operation: " + operation.getNote());
                        saveNote(operation, foundValue);
                    }

                    return true;
                } else {
                    remarks.append("✓ Confirmed JSON path or value is absent: ").append(path).append("\n");
                    return true;
                }
            } else {
                if (shouldExist) {
                    remarks.append("✗ JSON path not found or value mismatch: ").append(path).append("\n");
                    if (expectedValue != null && nodeExists) {
                        remarks.append("  Expected value: ").append(expectedValue).append("\n");
                        remarks.append("  Actual value: ").append(foundValue).append("\n");
                    }
                    suggestSimilarPaths(rootNode, path, remarks);
                } else {
                    remarks.append("✗ JSON path or value exists but should not: ").append(path).append("\n");
                    if (nodeExists) {
                        remarks.append("  Current value: ").append(foundValue).append("\n");
                    }
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking JSON value", e);
            remarks.append("Error: ").append(e.getMessage());
            return false;
        }
    }

    /**
     * Find parent node of a path
     */
    private static JsonNode findParentNodeByPath(JsonNode rootNode, String path) {
        String normalizedPath = path;
        if (path.startsWith("$")) {
            normalizedPath = path.substring(1);
            if (normalizedPath.startsWith(".")) {
                normalizedPath = normalizedPath.substring(1);
            }
        }

        if (normalizedPath.isEmpty()) {
            return rootNode;
        }

        // Find the last meaningful separator to determine parent path
        int lastSeparator = -1;
        int bracketDepth = 0;
        boolean inQuotes = false;
        char quoteChar = '\0';

        // Scan from right to left to find the last separator outside brackets/quotes
        for (int i = normalizedPath.length() - 1; i >= 0; i--) {
            char c = normalizedPath.charAt(i);

            // Handle quotes
            if ((c == '\'' || c == '"') && (i == 0 || normalizedPath.charAt(i - 1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                    quoteChar = '\0';
                }
            }

            // Handle brackets
            if (!inQuotes) {
                if (c == ']') {
                    bracketDepth++;
                } else if (c == '[') {
                    bracketDepth--;
                    // If we just closed all brackets and this is a bracket notation
                    if (bracketDepth == 0 && i > 0) {
                        // Check if there's a dot before the bracket
                        if (i > 0 && normalizedPath.charAt(i - 1) == '.') {
                            lastSeparator = i - 1;
                            break;
                        } else if (i == 0) {
                            // Bracket at start means parent is root
                            return rootNode;
                        } else {
                            // No dot before bracket, so the part before bracket is parent
                            lastSeparator = i;
                            break;
                        }
                    }
                } else if (c == '.' && bracketDepth == 0) {
                    // Found a dot outside brackets/quotes
                    lastSeparator = i;
                    break;
                }
            }
        }

        // If no separator found, root is the parent
        if (lastSeparator == -1 || lastSeparator == 0) {
            return rootNode;
        }

        // Get parent path and use findNodeByPath to navigate to it
        String parentPath = normalizedPath.substring(0, lastSeparator);
        if (parentPath.isEmpty()) {
            return rootNode;
        }

        return findNodeByPath(rootNode, parentPath);
    }


    /**
     * Suggest similar paths when path not found
     */
    private static void suggestSimilarPaths(JsonNode rootNode, String path, StringBuilder remarks) {
        String normalizedPath = path;
        if (path.startsWith("$")) {
            normalizedPath = path.substring(1);
            if (normalizedPath.startsWith(".")) {
                normalizedPath = normalizedPath.substring(1);
            }
        }

        // For simple one-level path
        if (!normalizedPath.contains(".") && !normalizedPath.contains("[")) {
            if (rootNode.isObject()) {
                List<String> similarFields = new ArrayList<>();
                String finalNormalizedPath = normalizedPath;
                rootNode.fieldNames().forEachRemaining(field -> {
                    if (field.contains(finalNormalizedPath) || finalNormalizedPath.contains(field)) {
                        similarFields.add(field);
                    }
                });

                if (!similarFields.isEmpty()) {
                    remarks.append("  Similar paths at root level: ").append(String.join(", ", similarFields)).append("\n");
                }
            }
            return;
        }

        // Get parent part of the path
        String[] parts = normalizedPath.split("\\.");
        if (parts.length > 1) {
            StringBuilder parentPath = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) parentPath.append(".");
                parentPath.append(parts[i]);
            }

            JsonNode parentNode = findNodeByPath(rootNode, parentPath.toString());
            if (parentNode != null && parentNode.isObject()) {
                String lastPart = parts[parts.length - 1];
                List<String> similarFields = new ArrayList<>();

                parentNode.fieldNames().forEachRemaining(field -> {
                    if (field.contains(lastPart) || lastPart.contains(field)) {
                        similarFields.add(field);
                    }
                });

                if (!similarFields.isEmpty()) {
                    remarks.append("  Similar fields in parent path: ").append(String.join(", ", similarFields)).append("\n");
                } else if (parentNode.size() > 0) {
                    remarks.append("  Available fields in parent path: ");
                    Iterator<String> fieldNames = parentNode.fieldNames();
                    int count = 0;
                    while (fieldNames.hasNext() && count < 5) {
                        remarks.append(fieldNames.next());
                        if (fieldNames.hasNext() && count < 4) remarks.append(", ");
                        count++;
                    }
                    if (count == 5 && fieldNames.hasNext()) remarks.append("...");
                    remarks.append("\n");
                }
            }
        }
    }

    /**
     * Create a new JSON file
     */
private static boolean createJsonFile(File file, Operation operation, StringBuilder remarks) throws IOException {
    String content = operation.getParameter("content");
    boolean overwrite = Boolean.parseBoolean(operation.getParameter("overwrite"));

    if (file.exists() && !overwrite) {
        LOGGER.warning("File already exists and overwrite is false: " + file.getAbsolutePath());
        remarks.append("File already exists and overwrite=false. No action taken.");
        return false;
    } else {
        LOGGER.info("Creating JSON file: " + file.getAbsolutePath());
        remarks.append("Creating JSON file: ").append(file.getAbsolutePath()).append("\n");
    }

    // Create parent directories if they don't exist
    if (file.getParentFile() != null && !file.getParentFile().exists()) {
        LOGGER.info("Creating directory: " + file.getParentFile().getAbsolutePath());
        if (!file.getParentFile().mkdirs()) {
            String err = "Failed to create parent directories: " + file.getParentFile().getAbsolutePath();
            LOGGER.severe(err);
            remarks.append(err);
            return false;
        }
    }

    JsonNode rootNode;
    if (content != null && !content.trim().isEmpty()) {
        // Parse provided content and validate JSON
        try {
            rootNode = MAPPER.readTree(content);
            LOGGER.info("Valid JSON content provided for new file");
            remarks.append("JSON file created with provided content.");
        } catch (Exception e) {
            String errMsg = "Invalid JSON content provided: " + e.getMessage();
            LOGGER.severe(errMsg);
            remarks.append(errMsg);
            return false;
        }
    } else {
        // Create empty object if no content provided
        rootNode = MAPPER.createObjectNode();
        remarks.append("Empty JSON object file created.");
        LOGGER.info("Creating empty JSON object file: " + file.getAbsolutePath());
    }

    // Write to file with pretty print
    MAPPER.writeValue(file, rootNode);

    return true;
}

    /**
     * Write content to JSON file (merging with existing content)
     */
    private static boolean writeJsonFile(File file, Operation operation, StringBuilder remarks) throws IOException {
        String content = operation.getParameter("content");
        String mergeMode = operation.getParameter("merge_mode");
        boolean appendOnly = "append".equalsIgnoreCase(mergeMode);
        String path = operation.getParameter("path");

        LOGGER.info("Writing to JSON file: " + file.getAbsolutePath());//NO I18N
        LOGGER.info("- Path: " + (path != null ? path : "root"));//NO I18N
        LOGGER.info("- Merge mode: " + mergeMode);//NO I18N

        if (content == null || content.trim().isEmpty()) {
            LOGGER.warning("No content provided for write operation.");//NO I18N
            remarks.append("No content provided for write operation.");
            return false;
        }

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup && file.exists()) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            LOGGER.info("Created backup at: " + backupPath);//NO I18N
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        JsonNode rootNode;
        JsonNode newContentNode;

        try {
            newContentNode = MAPPER.readTree(content);
            LOGGER.info("Successfully parsed content JSON");//NO I18N
        } catch (Exception e) {
            LOGGER.severe("Invalid JSON content: " + e.getMessage());//NO I18N
            remarks.append("Invalid JSON content: ").append(e.getMessage());
            return false;
        }

        // If file exists, read existing content
        if (file.exists() && file.length() > 0) {
            try {
                rootNode = MAPPER.readTree(file);
                LOGGER.info("Read existing JSON file (" + file.length() + " bytes)");//NO I18N
            } catch (Exception e) {
                LOGGER.severe("Error reading existing JSON file: " + e.getMessage());//NO I18N
                remarks.append("Error reading existing JSON file: ").append(e.getMessage());
                return false;
            }

            // If path is specified, find the target node at that path
            if (path != null && !path.isEmpty() && !path.equals("$")) {
                LOGGER.info("Processing write at specific path: " + path);//NO I18N
                // Find the target node and its parent
                JsonNode parentNode = findParentNodeByPath(rootNode, path);
                String lastPathPart = getLastPathPart(path);

                if (parentNode != null && parentNode.isObject()) {
                    // For objects at the specified path
                    if (newContentNode.isObject()) {
                        ObjectNode targetObj;

                        // Check if the node at path already exists
                        if (parentNode.has(lastPathPart) &&
                                parentNode.get(lastPathPart).isObject()) {
                            targetObj = (ObjectNode) parentNode.get(lastPathPart);
                            LOGGER.info("Found existing object at path: " + path);//NO I18N

                            if (appendOnly) {
                                LOGGER.info("Appending properties to existing object (non-replacing mode)");//NO I18N
                                // Only add new properties, never replace existing ones
                                newContentNode.fields().forEachRemaining(entry -> {
                                    if (!targetObj.has(entry.getKey())) {
                                        targetObj.set(entry.getKey(), entry.getValue());
                                    }
                                });
                            } else {
                                LOGGER.info("Deep merging with existing object");//NO I18N
                                // Deep merge
                                JsonNode mergedNode = mergeJsonObjectsDeep(targetObj,
                                        (ObjectNode) newContentNode);
                                ((ObjectNode) parentNode).set(lastPathPart, mergedNode);
                            }
                        } else {
                            // Node doesn't exist at path, create it
                            LOGGER.info("Creating new object at path: " + path);//NO I18N
                            ((ObjectNode) parentNode).set(lastPathPart, newContentNode);
                        }

                        remarks.append("Content ");
                        remarks.append(appendOnly ? "appended" : "merged");
                        remarks.append(" at path: ").append(path).append("\n");
                    } else {
                        // For non-object nodes, just set the value
                        LOGGER.info("Setting non-object value at path: " + path);//NO I18N
                        ((ObjectNode) parentNode).set(lastPathPart, newContentNode);
                        remarks.append("Value set at path: ").append(path).append("\n");
                    }
                } else {
                    LOGGER.warning("Cannot write to path: " + path +
                            ". Parent node not found or is not an object.");//NO I18N
                    remarks.append("Cannot write to path: ").append(path)
                            .append(". Parent node not found or is not an object.\n");
                    return false;
                }
            } else {
                // Root level operation (existing behavior)
                LOGGER.info("Processing write at root level");//NO I18N
                if (rootNode.isObject() && newContentNode.isObject()) {
                    if (appendOnly) {
                        // Append mode - only add new properties, never replace existing ones
                        LOGGER.info("Appending properties to root object (non-replacing mode)");//NO I18N
                        ObjectNode targetObj = (ObjectNode) rootNode;
                        newContentNode.fields().forEachRemaining(entry -> {
                            if (!targetObj.has(entry.getKey())) {
                                targetObj.set(entry.getKey(), entry.getValue());
                            }
                        });
                    } else {
                        // Standard deep merge (might replace existing properties)
                        LOGGER.info("Deep merging with root object");//NO I18N
                        rootNode = mergeJsonObjectsDeep((ObjectNode) rootNode, (ObjectNode) newContentNode);
                    }
                } else {
                    rootNode = newContentNode;
                    LOGGER.warning("Existing content not compatible for merging, replacing with new content");//NO I18N
                    remarks.append("Warning: Existing content was not compatible for merging, replacing with new content.\n");
                }
            }
        } else {
            // If file doesn't exist or is empty, use only the new content
            LOGGER.info("File doesn't exist or is empty, creating new file with provided content");//NO I18N
            rootNode = newContentNode;
        }

        try {
            // Write to file
            MAPPER.writeValue(file, rootNode);
            LOGGER.info("Successfully wrote JSON to file: " + file.getAbsolutePath());//NO I18N

            if (path != null && !path.isEmpty() && !path.equals("$")) {
                remarks.append("Operation completed at path: ").append(path).append(". ");
            } else {
                if (appendOnly) {
                    remarks.append("Content appended to root of existing JSON file. ");
                } else {
                    remarks.append("Content merged with existing JSON and written to file. ");
                }
            }
            int contentLength = MAPPER.writeValueAsString(rootNode).length();
            LOGGER.info("Final JSON content length: " + contentLength + " chars");//NO I18N
            remarks.append(contentLength).append(" chars.");

            return true;
        } catch (Exception e) {
            LOGGER.severe("Error writing to JSON file: " + e.getMessage());//NO I18N
            remarks.append("Error writing to file: ").append(e.getMessage());
            return false;
        }
    }

    /**
     * Gets the last part of a path
     */
    private static String getLastPathPart(String path) {
        String normalizedPath = path;
        if (path.startsWith("$")) {
            normalizedPath = path.substring(1);
            if (normalizedPath.startsWith(".")) {
                normalizedPath = normalizedPath.substring(1);
            }
        }

        if (normalizedPath.isEmpty()) {
            return "";
        }

        // Find the last meaningful separator
        int lastSeparator = -1;
        int bracketDepth = 0;
        boolean inQuotes = false;
        char quoteChar = '\0';

        // Scan from right to left
        for (int i = normalizedPath.length() - 1; i >= 0; i--) {
            char c = normalizedPath.charAt(i);

            // Handle quotes
            if ((c == '\'' || c == '"') && (i == 0 || normalizedPath.charAt(i - 1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                    quoteChar = '\0';
                }
            }

            // Handle brackets and dots outside quotes
            if (!inQuotes) {
                if (c == ']') {
                    bracketDepth++;
                } else if (c == '[') {
                    bracketDepth--;
                    // When we close all brackets and find the opening bracket
                    if (bracketDepth == 0) {
                        // Check if there's a dot before the bracket
                        if (i > 0 && normalizedPath.charAt(i - 1) == '.') {
                            lastSeparator = i - 1;
                            break;
                        } else {
                            // The bracket follows directly after a field name
                            // Find the field name before the bracket
                            int fieldStart = i - 1;
                            while (fieldStart >= 0 && normalizedPath.charAt(fieldStart) != '.') {
                                fieldStart--;
                            }
                            lastSeparator = fieldStart;
                            break;
                        }
                    }
                } else if (c == '.' && bracketDepth == 0) {
                    lastSeparator = i;
                    break;
                }
            }
        }

        // Get the last part after the separator
        String lastPart;
        if (lastSeparator == -1) {
            lastPart = normalizedPath;
        } else {
            lastPart = normalizedPath.substring(lastSeparator + 1);
        }

        // If it's bracket notation, extract the key
        if (lastPart.contains("[") && lastPart.endsWith("]")) {
            int bracketStart = lastPart.indexOf('[');
            String bracketContent = lastPart.substring(bracketStart + 1, lastPart.length() - 1);

            // Remove quotes if present
            if ((bracketContent.startsWith("'") && bracketContent.endsWith("'")) ||
                    (bracketContent.startsWith("\"") && bracketContent.endsWith("\""))) {
                bracketContent = bracketContent.substring(1, bracketContent.length() - 1);
            }

            return bracketContent;
        }

        return lastPart;
    }

    /**
     * Update a JSON node using a simple path
     */
    private static boolean updateJsonNode(File file, Operation operation, StringBuilder remarks) throws IOException {
        String path = operation.getParameter("path");
        String newValue = operation.getParameter("new_value");

        LOGGER.info("Updating JSON node at path: " + path);
        LOGGER.info("New value: " + newValue);

        if (path == null || path.isEmpty()) {
            LOGGER.warning("Path not provided for update operation.");
            remarks.append("Path not provided for update operation.");
            return false;
        }

        if (newValue == null) {
            LOGGER.warning("New value not provided for update operation.");
            remarks.append("New value not provided for update operation.");
            return false;
        }

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup && file.exists()) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            LOGGER.info("Created backup at: " + backupPath);
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Read JSON
        JsonNode rootNode;
        try {
            rootNode = MAPPER.readTree(file);
            LOGGER.info("Successfully read JSON file: " + file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.severe("Error reading JSON file: " + e.getMessage());
            remarks.append("Error reading JSON file: ").append(e.getMessage());
            return false;
        }

        // Find the parent node using the updated helper method (supports bracket notation)
        JsonNode parentNode = findParentNodeByPath(rootNode, path);
        String lastPathPart = getLastPathPart(path);

        LOGGER.info("Parent node found: " + (parentNode != null));
        LOGGER.info("Last path part: " + lastPathPart);

        if (parentNode == null) {
            LOGGER.warning("Path not found: " + path);
            remarks.append("Path not found: ").append(path).append("\n");
            remarks.append("Please verify the path syntax. For keys with dots, use bracket notation: ['key.name']");
            return false;
        }

        // Parse the new value as JSON or appropriate type
        JsonNode newValueNode;
        try {
            // Try to parse as JSON first
            newValueNode = MAPPER.readTree(newValue);
            LOGGER.info("Parsed new value as JSON: " + newValueNode.getNodeType());
        } catch (Exception e) {
            // If parsing as JSON fails, determine the correct data type
            try {
                // Try to parse as number
                int intValue = Integer.parseInt(newValue);
                newValueNode = MAPPER.getNodeFactory().numberNode(intValue);
                LOGGER.info("Parsed new value as integer: " + intValue);
            } catch (NumberFormatException e1) {
                try {
                    double doubleValue = Double.parseDouble(newValue);
                    newValueNode = MAPPER.getNodeFactory().numberNode(doubleValue);
                    LOGGER.info("Parsed new value as double: " + doubleValue);
                } catch (NumberFormatException e2) {
                    // Handle boolean
                    if ("true".equalsIgnoreCase(newValue) || "false".equalsIgnoreCase(newValue)) {
                        boolean boolValue = Boolean.parseBoolean(newValue);
                        newValueNode = MAPPER.getNodeFactory().booleanNode(boolValue);
                        LOGGER.info("Parsed new value as boolean: " + boolValue);
                    } else if ("null".equalsIgnoreCase(newValue)) {
                        newValueNode = MAPPER.getNodeFactory().nullNode();
                        LOGGER.info("Parsed new value as null");
                    } else {
                        // Default to string for any other value
                        newValueNode = MAPPER.getNodeFactory().textNode(newValue);
                        LOGGER.info("Treating new value as string");
                    }
                }
            }
        }

        // Update the value
        if (parentNode.isObject()) {
            LOGGER.info("Updating field '" + lastPathPart + "' in object node");

            // Check if the field exists before updating
            if (!parentNode.has(lastPathPart)) {
                LOGGER.warning("Field '" + lastPathPart + "' does not exist in parent node");
                remarks.append("Warning: Field '").append(lastPathPart)
                        .append("' does not exist. Creating new field.\n");
            }

            ((ObjectNode) parentNode).set(lastPathPart, newValueNode);
            LOGGER.info("Successfully updated field: " + lastPathPart);
        } else if (parentNode.isArray()) {
            try {
                int index = Integer.parseInt(lastPathPart);
                LOGGER.info("Updating element in array at index " + index);

                if (index < 0 || index >= parentNode.size()) {
                    LOGGER.severe("Array index out of bounds: " + index + " (array size: " + parentNode.size() + ")");
                    remarks.append("Error: Array index out of bounds: ").append(index)
                            .append(" (array size: ").append(parentNode.size()).append(")");
                    return false;
                }

                ((ArrayNode) parentNode).set(index, newValueNode);
                LOGGER.info("Successfully updated array element at index: " + index);
            } catch (NumberFormatException e) {
                LOGGER.severe("Invalid array index (not a number): " + lastPathPart);
                remarks.append("Error: Invalid array index: ").append(lastPathPart);
                return false;
            }
        } else {
            LOGGER.warning("Cannot update value in node type: " + parentNode.getNodeType());
            remarks.append("Cannot update value at path: ").append(path)
                    .append(". Parent is neither an object nor an array.");
            return false;
        }

        // Write back to file
        try {
            MAPPER.writeValue(file, rootNode);
            LOGGER.info("Successfully wrote updated JSON to file");
            remarks.append("✓ Updated JSON node at path: ").append(path).append("\n");
            remarks.append("  New value: ").append(newValue);
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error writing JSON to file: " + e.getMessage());
            remarks.append("Error writing to file: ").append(e.getMessage());
            return false;
        }
    }


    /**
     * Remove a property from JSON object or array element
     */
    private static boolean removeJsonProperty(File file, Operation operation, StringBuilder remarks) throws IOException {
        String path = operation.getParameter("path");
        String propertyName = operation.getParameter("key_name");

        LOGGER.info("Remove operation - Path: " + path + ", Key: " + propertyName);

        // Determine what to remove based on parameters
        boolean removeByPath = (path != null && !path.isEmpty() && (propertyName == null || propertyName.isEmpty()));
        boolean removeByKey = (propertyName != null && !propertyName.isEmpty());

        if (!removeByPath && !removeByKey) {
            remarks.append("Either 'path' or 'key_name' parameter must be provided for remove operation.");
            LOGGER.warning("Neither path nor key_name provided for remove operation");
            return false;
        }

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup && file.exists()) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            LOGGER.info("Created backup at: " + backupPath);
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Read JSON
        JsonNode rootNode;
        try {
            rootNode = MAPPER.readTree(file);
            LOGGER.info("Successfully read JSON file: " + file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.severe("Error reading JSON file: " + e.getMessage());
            remarks.append("Error reading JSON file: ").append(e.getMessage());
            return false;
        }

        // Case 1: Remove node at specific path (entire node)
        if (removeByPath) {
            LOGGER.info("Removing node at path: " + path);

            // Find parent of the node to remove
            JsonNode parentNode = findParentNodeByPath(rootNode, path);
            String lastPathPart = getLastPathPart(path);

            if (parentNode == null) {
                LOGGER.warning("Parent path not found: " + path);
                remarks.append("Path not found: ").append(path).append("\n");
                remarks.append("Please verify the path syntax. For keys with dots, use bracket notation: ['key.name']");
                return false;
            }

            // Remove from parent
            if (parentNode.isObject()) {
                ObjectNode objectNode = (ObjectNode) parentNode;

                if (!objectNode.has(lastPathPart)) {
                    LOGGER.warning("Field '" + lastPathPart + "' not found in parent object");
                    remarks.append("Field '").append(lastPathPart).append("' not found at path: ").append(path);
                    return false;
                }

                objectNode.remove(lastPathPart);
                LOGGER.info("Successfully removed field: " + lastPathPart);
                remarks.append("✓ Removed field '").append(lastPathPart).append("' from path: ").append(path);

            } else if (parentNode.isArray()) {
                try {
                    int index = Integer.parseInt(lastPathPart);
                    ArrayNode arrayNode = (ArrayNode) parentNode;

                    if (index < 0 || index >= arrayNode.size()) {
                        LOGGER.severe("Array index out of bounds: " + index);
                        remarks.append("Error: Array index out of bounds: ").append(index);
                        return false;
                    }

                    arrayNode.remove(index);
                    LOGGER.info("Successfully removed array element at index: " + index);
                    remarks.append("✓ Removed array element at index: ").append(index);

                } catch (NumberFormatException e) {
                    LOGGER.severe("Invalid array index: " + lastPathPart);
                    remarks.append("Error: Invalid array index: ").append(lastPathPart);
                    return false;
                }
            } else {
                LOGGER.warning("Parent node is neither object nor array");
                remarks.append("Cannot remove from parent node type: ").append(parentNode.getNodeType());
                return false;
            }
        }
        // Case 2: Remove property by key name from path (or root)
        else {
            JsonNode targetNode;
            String targetPath = "root";

            if (path == null || path.isEmpty() || path.equals("$")) {
                targetNode = rootNode;
                LOGGER.info("Removing property '" + propertyName + "' from root");
            } else {
                targetNode = findNodeByPath(rootNode, path);
                targetPath = path;
                LOGGER.info("Removing property '" + propertyName + "' from path: " + path);

                if (targetNode == null) {
                    LOGGER.warning("Path not found: " + path);
                    remarks.append("Path not found: ").append(path).append("\n");
                    remarks.append("Please verify the path syntax. For keys with dots, use bracket notation: ['key.name']");
                    return false;
                }
            }

            // Remove property from target node
            if (targetNode.isObject()) {
                ObjectNode objectNode = (ObjectNode) targetNode;

                if (!objectNode.has(propertyName)) {
                    LOGGER.warning("Property '" + propertyName + "' not found in target object");
                    remarks.append("Property '").append(propertyName).append("' not found");
                    if (!targetPath.equals("root")) {
                        remarks.append(" at path: ").append(targetPath);
                    }
                    return false;
                }

                objectNode.remove(propertyName);
                LOGGER.info("Successfully removed property: " + propertyName);

                remarks.append("✓ Removed property '").append(propertyName).append("'");
                if (!targetPath.equals("root")) {
                    remarks.append(" from path: ").append(targetPath);
                }
            } else {
                LOGGER.warning("Target node is not an object, cannot remove property");
                remarks.append("Cannot remove property from non-object node");
                if (!targetPath.equals("root")) {
                    remarks.append(" at path: ").append(targetPath);
                }
                return false;
            }
        }

        // Write back to file
        try {
            MAPPER.writeValue(file, rootNode);
            LOGGER.info("Successfully wrote updated JSON to file after removal");
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error writing JSON to file: " + e.getMessage());
            remarks.append("\nError writing to file: ").append(e.getMessage());
            return false;
        }
    }


    /**
     * Find a node in the JSON tree using a simple path expression
     * Supports:
     * - Dot notation: "users.name" or "$.users.name"
     * - Array access: "users[0].name"
     * - Bracket notation for special characters: "backup_configuration['backup.content.type']"
     * - Mixed notation: "$.data.config['special.key'].value"
     * $ represents the root node
     */
    private static JsonNode findNodeByPath(JsonNode rootNode, String path) {
        if (path == null || path.isEmpty() || path.equals("$")) {
            return rootNode;
        }

        String normalizedPath = path;
        if (path.startsWith("$")) {
            normalizedPath = path.substring(1);
            if (normalizedPath.startsWith(".")) {
                normalizedPath = normalizedPath.substring(1);
            }
        }

        if (normalizedPath.isEmpty()) {
            return rootNode;
        }

        JsonNode currentNode = rootNode;
        int i = 0;

        while (i < normalizedPath.length()) {
            if (currentNode == null) {
                return null;
            }

            // Handle bracket notation ['key'] or ["key"] or [index]
            if (normalizedPath.charAt(i) == '[') {
                int closeBracket = normalizedPath.indexOf(']', i);
                if (closeBracket == -1) {
                    LOGGER.warning("Invalid path: unclosed bracket at position " + i);
                    return null;
                }

                String bracketContent = normalizedPath.substring(i + 1, closeBracket);

                // Remove quotes if present (both single and double quotes)
                if ((bracketContent.startsWith("'") && bracketContent.endsWith("'")) ||
                        (bracketContent.startsWith("\"") && bracketContent.endsWith("\""))) {
                    bracketContent = bracketContent.substring(1, bracketContent.length() - 1);
                }

                // Check if it's an array index or object key
                try {
                    int index = Integer.parseInt(bracketContent);
                    // It's an array index
                    if (currentNode.isArray() && index >= 0 && index < currentNode.size()) {
                        currentNode = currentNode.get(index);
                        LOGGER.fine("Accessed array index: " + index);
                    } else {
                        LOGGER.warning("Array index out of bounds or node is not an array: " + index);
                        return null;
                    }
                } catch (NumberFormatException e) {
                    // It's an object key
                    if (currentNode.isObject() && currentNode.has(bracketContent)) {
                        currentNode = currentNode.get(bracketContent);
                        LOGGER.fine("Accessed field via bracket notation: " + bracketContent);
                    } else {
                        LOGGER.warning("Field not found in bracket notation: " + bracketContent);
                        return null;
                    }
                }

                i = closeBracket + 1;
                // Skip the dot after bracket if present (e.g., ['key'].nextKey)
                if (i < normalizedPath.length() && normalizedPath.charAt(i) == '.') {
                    i++;
                }
                continue;
            }

            // Handle dot notation
            int nextDot = normalizedPath.indexOf('.', i);
            int nextBracket = normalizedPath.indexOf('[', i);

            int endPos;
            if (nextBracket != -1 && (nextDot == -1 || nextBracket < nextDot)) {
                // Next token ends at bracket
                endPos = nextBracket;
            } else if (nextDot != -1) {
                // Next token ends at dot
                endPos = nextDot;
            } else {
                // Last token
                endPos = normalizedPath.length();
            }

            String part = normalizedPath.substring(i, endPos);

            if (!part.isEmpty()) {
                // Handle array access in dot notation (e.g., users[0])
                if (part.contains("[") && part.endsWith("]")) {
                    String arrayName = part.substring(0, part.indexOf("["));
                    String indexStr = part.substring(part.indexOf("[") + 1, part.length() - 1);

                    if (arrayName.isEmpty()) {
                        // Direct array access like [0]
                        if (currentNode.isArray()) {
                            try {
                                int index = Integer.parseInt(indexStr);
                                if (index >= 0 && index < currentNode.size()) {
                                    currentNode = currentNode.get(index);
                                    LOGGER.fine("Accessed root array index: " + index);
                                } else {
                                    LOGGER.warning("Array index out of bounds: " + index);
                                    return null;
                                }
                            } catch (NumberFormatException e) {
                                LOGGER.warning("Invalid array index: " + indexStr);
                                return null;
                            }
                        } else {
                            LOGGER.warning("Current node is not an array");
                            return null;
                        }
                    } else {
                        // Field with array access like users[0]
                        if (!currentNode.has(arrayName)) {
                            LOGGER.warning("Field not found: " + arrayName);
                            return null;
                        }

                        JsonNode arrayNode = currentNode.get(arrayName);
                        if (!arrayNode.isArray()) {
                            LOGGER.warning("Field is not an array: " + arrayName);
                            return null;
                        }

                        try {
                            int index = Integer.parseInt(indexStr);
                            if (index >= 0 && index < arrayNode.size()) {
                                currentNode = arrayNode.get(index);
                                LOGGER.fine("Accessed array field " + arrayName + "[" + index + "]");
                            } else {
                                LOGGER.warning("Array index out of bounds for " + arrayName + ": " + index);
                                return null;
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid array index for " + arrayName + ": " + indexStr);
                            return null;
                        }
                    }
                } else {
                    // Simple field access
                    if (!currentNode.has(part)) {
                        LOGGER.warning("Field not found: " + part);
                        return null;
                    }
                    currentNode = currentNode.get(part);
                    LOGGER.fine("Accessed field: " + part);
                }
            }

            i = endPos;
            // Skip the dot separator
            if (i < normalizedPath.length() && normalizedPath.charAt(i) == '.') {
                i++;
            }
        }

        return currentNode;
    }



    /**
     * Deep merge JSON objects
     */
    private static JsonNode mergeJsonObjectsDeep(ObjectNode target, ObjectNode source) {
        source.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode sourceValue = entry.getValue();
            JsonNode targetValue = target.get(key);

            if (targetValue != null && targetValue.isObject() && sourceValue.isObject()) {
                // Recursively merge objects
                target.set(key, mergeJsonObjectsDeep((ObjectNode) targetValue, (ObjectNode) sourceValue));
            } else {
                // For arrays, primitives, or null values, overwrite
                target.set(key, sourceValue);
            }
        });

        return target;
    }

}