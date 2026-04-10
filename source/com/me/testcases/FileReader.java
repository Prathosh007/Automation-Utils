package com.me.testcases;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.me.util.LogManager;

/**
 * Enhanced FileReader utility for reading and parsing various file types
 * Includes backward compatibility methods for ResultAnalyzer and FileEditHandler
 * 
 * Improved with:
 * - Support for more file formats
 * - Better error handling
 * - Enhanced value extraction capabilities
 * - Path resolution
 * - Performance optimizations for large files
 * - Improved logging
 */
public class FileReader {
    private static final Logger LOGGER = LogManager.getLogger(FileReader.class, LogManager.LOG_TYPE.FW);
    
    // Buffer size for reading large files
    private static final int BUFFER_SIZE = 8192;
    
    // Common file extensions
    private static final Set<String> TEXT_FILES = new HashSet<>(Arrays.asList(
        "txt", "log", "conf", "ini", "properties", "template"//No I18N
    ));
    
    private static final Set<String> XML_FILES = new HashSet<>(Arrays.asList(
        "xml", "config"//No I18N
    ));
    
    private static final Set<String> JSON_FILES = new HashSet<>(Arrays.asList(
        "json"//No I18N
    ));
    
    /**
     * Read the entire contents of a file as a string
     * 
     * @param filePath Path to the file, can contain server_home
     * @return The file contents as a string, or null if file cannot be read
     */
    public static String readFileAsString(String filePath) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        LOGGER.fine("Reading file: " + resolvedPath);//No I18N
        
        try {
            Path path = Paths.get(resolvedPath);
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read file: " + resolvedPath, e);//No I18N
            return null;
        }
    }
    
    /**
     * Read a file line by line, useful for large files
     * 
     * @param filePath Path to the file, can contain server_home
     * @return List of lines in the file, or empty list if file cannot be read
     */
    public static List<String> readLines(String filePath) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        List<String> lines = new ArrayList<>();
        
        LOGGER.fine("Reading file lines: " + resolvedPath);//No I18N
        
        try (BufferedReader reader = new BufferedReader(
                new java.io.FileReader(resolvedPath), BUFFER_SIZE)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read file lines: " + resolvedPath, e);//No I18N
            return Collections.emptyList();
        }
    }
    
    /**
     * Check if a file contains a specific string
     * 
     * @param filePath Path to the file, can contain server_home
     * @param searchString The string to search for
     * @return true if the string is found, false otherwise
     */
    public static boolean containsString(String filePath, String searchString) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (searchString == null || searchString.isEmpty()) {
            LOGGER.warning("Search string is empty");//No I18N
            return false;
        }
        
        LOGGER.fine("Checking if file contains string: " + resolvedPath);//No I18N
        
        try (BufferedReader reader = new BufferedReader(
                new java.io.FileReader(resolvedPath), BUFFER_SIZE)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(searchString)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to search in file: " + resolvedPath, e);//No I18N
            return false;
        }
    }
    
    /**
     * Read a property value from a properties-like file (key=value or key:value format)
     * 
     * @param filePath Path to the file, can contain server_home
     * @param key The property key to look for
     * @return The property value, or null if not found
     */
    public static String getPropertyValue(String filePath, String key) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (key == null || key.isEmpty()) {
            LOGGER.warning("Property key is empty");//No I18N
            return null;
        }
        
        LOGGER.fine("Reading property '" + key + "' from: " + resolvedPath);//No I18N
        
        // Handle .properties files specifically
        if (resolvedPath.toLowerCase().endsWith(".properties")) {//No I18N
            Properties props = new Properties();
            try (InputStream input = new FileInputStream(resolvedPath)) {
                props.load(input);
                return props.getProperty(key);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read properties file: " + resolvedPath, e);//No I18N
                return null;
            }
        }
        
        // For other file types, try to find key=value or key:value patterns
        try (BufferedReader reader = new BufferedReader(
                new java.io.FileReader(resolvedPath), BUFFER_SIZE)) {
            
            String line;
            Pattern keyValuePattern = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*[=:]\\s*(.*)$");//No I18N
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = keyValuePattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
            
            LOGGER.info("Property '" + key + "' not found in: " + resolvedPath);//No I18N
            return null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to search for property: " + resolvedPath, e);//No I18N
            return null;
        }
    }
    
    /**
     * Get a value from an XML file using XPath-like path
     * 
     * @param filePath Path to the XML file, can contain server_home
     * @param xmlPath Path to the element or attribute (e.g., "server/connector/@port")
     * @return The value, or null if not found
     */
    public static String getXmlValue(String filePath, String xmlPath) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (xmlPath == null || xmlPath.isEmpty()) {
            LOGGER.warning("XML path is empty");//No I18N
            return null;
        }
        
        LOGGER.fine("Reading XML value '" + xmlPath + "' from: " + resolvedPath);//No I18N
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(resolvedPath));
            document.getDocumentElement().normalize();
            
            // Split the path
            String[] pathElements = xmlPath.split("/");
            Node currentNode = document.getDocumentElement();
            
            // Process each path element
            for (int i = 0; i < pathElements.length; i++) {
                String element = pathElements[i];
                
                // Skip empty elements
                if (element.isEmpty()) {
                    continue;
                }
                
                // Check if it's an attribute reference
                if (element.startsWith("@")) {
                    // It's an attribute - return its value
                    if (currentNode instanceof Element) {
                        String attrName = element.substring(1);
                        return ((Element) currentNode).getAttribute(attrName);
                    } else {
                        LOGGER.warning("Cannot get attribute from non-element node");//No I18N
                        return null;
                    }
                }
                
                // Handle element with condition: element[@attr=value]
                if (element.contains("[")) {
                    int conditionStart = element.indexOf('[');
                    int conditionEnd = element.indexOf(']');
                    
                    if (conditionStart != -1 && conditionEnd != -1) {
                        String elementName = element.substring(0, conditionStart);
                        String condition = element.substring(conditionStart + 1, conditionEnd);
                        
                        // Handle attribute condition: @attr=value
                        if (condition.startsWith("@")) {
                            String[] attrCondition = condition.substring(1).split("=");
                            if (attrCondition.length == 2) {
                                String attrName = attrCondition[0];
                                String attrValue = attrCondition[1];
                                
                                // Find matching child element
                                NodeList children = ((Element) currentNode).getElementsByTagName(elementName);
                                boolean found = false;
                                
                                for (int j = 0; j < children.getLength(); j++) {
                                    Node child = children.item(j);
                                    if (child instanceof Element) {
                                        Element childElement = (Element) child;
                                        if (childElement.getAttribute(attrName).equals(attrValue)) {
                                            currentNode = child;
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                                
                                if (!found) {
                                    LOGGER.warning("XML element with condition not found: " + element);//No I18N
                                    return null;
                                }
                            }
                        }
                    }
                } else {
                    // Simple element name - find the child
                    if (currentNode instanceof Element) {
                        NodeList children = ((Element) currentNode).getElementsByTagName(element);
                        if (children.getLength() > 0) {
                            currentNode = children.item(0);
                        } else {
                            LOGGER.warning("XML element not found: " + element);//No I18N
                            return null;
                        }
                    } else {
                        LOGGER.warning("Cannot navigate from non-element node");//No I18N
                        return null;
                    }
                }
                
                // If this is the last element, return its text content
                if (i == pathElements.length - 1) {
                    return currentNode.getTextContent();
                }
            }
            
            return currentNode.getTextContent();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read XML value: " + resolvedPath, e);//No I18N
            return null;
        }
    }
    
    /**
     * Get a value from a JSON file using a path
     * 
     * @param filePath Path to the JSON file, can contain server_home
     * @param jsonPath Path to the value (e.g., "settings.display.color")
     * @return The value as a string, or null if not found
     */
    public static String getJsonValue(String filePath, String jsonPath) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (jsonPath == null || jsonPath.isEmpty()) {
            LOGGER.warning("JSON path is empty");//No I18N
            return null;
        }
        
        LOGGER.fine("Reading JSON value '" + jsonPath + "' from: " + resolvedPath);//No I18N
        
        try {
            // Read the JSON file
            String jsonContent = readFileAsString(resolvedPath);
            if (jsonContent == null) {
                return null;
            }
            
            JsonElement rootElement = JsonParser.parseString(jsonContent);
            if (!rootElement.isJsonObject()) {
                LOGGER.warning("Root JSON element is not an object");//No I18N
                return null;
            }
            
            // Split the path
            String[] pathElements = jsonPath.split("\\.");
            JsonElement currentElement = rootElement;
            
            // Process each path element
            for (String element : pathElements) {
                if (currentElement.isJsonNull()) {
                    return null;
                }
                
                // Handle array access: element[index]
                if (element.contains("[")) {
                    int bracketStart = element.indexOf('[');
                    int bracketEnd = element.indexOf(']');
                    
                    if (bracketStart != -1 && bracketEnd != -1) {
                        String elementName = element.substring(0, bracketStart);
                        String indexStr = element.substring(bracketStart + 1, bracketEnd);
                        
                        try {
                            int index = Integer.parseInt(indexStr);
                            
                            // Get object at element name
                            if (currentElement.isJsonObject() && currentElement.getAsJsonObject().has(elementName)) {
                                JsonElement arrayElement = currentElement.getAsJsonObject().get(elementName);
                                
                                // Get array element at index
                                if (arrayElement.isJsonArray()) {
                                    currentElement = arrayElement.getAsJsonArray().get(index);
                                } else {
                                    LOGGER.warning("JSON element is not an array: " + element);//No I18N
                                    return null;
                                }
                            } else {
                                LOGGER.warning("JSON element not found: " + elementName);//No I18N
                                return null;
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid array index: " + indexStr);//No I18N
                            return null;
                        }
                    }
                } else {
                    // Simple property access
                    if (currentElement.isJsonObject() && currentElement.getAsJsonObject().has(element)) {
                        currentElement = currentElement.getAsJsonObject().get(element);
                    } else {
                        LOGGER.warning("JSON element not found: " + element);//No I18N
                        return null;
                    }
                }
            }
            
            // Return the final element value
            if (currentElement.isJsonPrimitive()) {
                return currentElement.getAsString();
            } else {
                // For non-primitive types, return the JSON string
                return currentElement.toString();
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read JSON value: " + resolvedPath, e);//No I18N
            return null;
        }
    }
    
    /**
     * Get values from a file based on its extension
     * 
     * @param filePath Path to the file, can contain server_home
     * @param path Path to the value (interpretation depends on file type)
     * @return The value as a string, or null if not found
     */
    public static String getValue(String filePath, String path) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (path == null || path.isEmpty()) {
            LOGGER.warning("Path is empty");//No I18N
            return null;
        }
        
        // Get file extension
        String extension = getFileExtension(resolvedPath).toLowerCase();
        
        // Choose appropriate method based on extension
        if (XML_FILES.contains(extension)) {
            return getXmlValue(resolvedPath, path);
        } else if (JSON_FILES.contains(extension)) {
            return getJsonValue(resolvedPath, path);
        } else if (TEXT_FILES.contains(extension)) {
            return getPropertyValue(resolvedPath, path);
        } else {
            LOGGER.warning("Unsupported file type for getValue: " + extension);//No I18N
            return getPropertyValue(resolvedPath, path); // Try property as fallback
        }
    }
    
    /**
     * Read a section of a file that contains a specific pattern
     * 
     * @param filePath Path to the file, can contain server_home
     * @param pattern Regex pattern to search for
     * @param linesBeforePattern Number of lines to include before the pattern match
     * @param linesAfterPattern Number of lines to include after the pattern match
     * @return The section of the file, or empty string if pattern not found
     */
    public static String getSectionAroundPattern(String filePath, String pattern, 
                                                int linesBeforePattern, int linesAfterPattern) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (pattern == null || pattern.isEmpty()) {
            LOGGER.warning("Pattern is empty");//No I18N
            return "";
        }
        
        LOGGER.fine("Reading section around pattern '" + pattern + "' from: " + resolvedPath);//No I18N
        
        try {
            List<String> lines = readLines(resolvedPath);
            if (lines.isEmpty()) {
                return "";
            }
            
            Pattern regex = Pattern.compile(pattern);
            List<String> sectionLines = new ArrayList<>();
            
            // Find pattern and extract section
            for (int i = 0; i < lines.size(); i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    // Include lines before pattern
                    int startLine = Math.max(0, i - linesBeforePattern);
                    for (int j = startLine; j < i; j++) {
                        sectionLines.add(lines.get(j));
                    }
                    
                    // Add the matching line
                    sectionLines.add(lines.get(i));
                    
                    // Include lines after pattern
                    int endLine = Math.min(lines.size() - 1, i + linesAfterPattern);
                    for (int j = i + 1; j <= endLine; j++) {
                        sectionLines.add(lines.get(j));
                    }
                    
                    return String.join("\n", sectionLines);//NO I18N
                }
            }
            
            LOGGER.info("Pattern not found in file: " + resolvedPath);//No I18N
            return "";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read section from file: " + resolvedPath, e);//No I18N
            return "";
        }
    }
    
    /**
     * Count occurrences of a pattern in a file
     * 
     * @param filePath Path to the file, can contain server_home
     * @param pattern Regex pattern to count
     * @return The number of occurrences, or 0 if error or not found
     */
    public static int countOccurrences(String filePath, String pattern) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (pattern == null || pattern.isEmpty()) {
            LOGGER.warning("Pattern is empty");//No I18N
            return 0;
        }
        
        LOGGER.fine("Counting occurrences of pattern '" + pattern + "' in: " + resolvedPath);//No I18N
        
        try {
            String content = readFileAsString(resolvedPath);
            if (content == null) {
                return 0;
            }
            
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(content);
            
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to count occurrences in file: " + resolvedPath, e);//No I18N
            return 0;
        }
    }
    
    /**
     * Check if a file exists
     * 
     * @param filePath Path to the file, can contain server_home
     * @return true if file exists, false otherwise
     */
    public static boolean fileExists(String filePath) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        return Files.exists(Paths.get(resolvedPath));
    }
    
    /**
     * Get file size
     * 
     * @param filePath Path to the file, can contain server_home
     * @return File size in bytes, or -1 if file doesn't exist
     */
    public static long getFileSize(String filePath) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        File file = new File(resolvedPath);
        
        if (!file.exists() || !file.isFile()) {
            LOGGER.warning("File does not exist: " + resolvedPath);//No I18N
            return -1;
        }
        
        return file.length();
    }
    
    /**
     * Get last modified timestamp of a file
     * 
     * @param filePath Path to the file, can contain server_home
     * @return Last modified timestamp, or -1 if file doesn't exist
     */
    public static long getLastModifiedTime(String filePath) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        File file = new File(resolvedPath);
        
        if (!file.exists() || !file.isFile()) {
            LOGGER.warning("File does not exist: " + resolvedPath);//No I18N
            return -1;
        }
        
        return file.lastModified();
    }
    
    /**
     * Extract file extension from path
     * 
     * @param filePath File path
     * @return File extension without dot, or empty string if none
     */
    private static String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filePath.length() - 1) {
            return filePath.substring(dotIndex + 1);
        }
        return "";
    }
    
    // ==========================================
    // BACKWARD COMPATIBILITY METHODS
    // ==========================================
    
    /**
     * Legacy method for reading files - maintains compatibility with existing code
     * 
     * @param filePath Path to the file
     * @return File contents as a string, or empty string if file cannot be read
     */
    public static String getFileContents(String filePath) {
        String content = readFileAsString(filePath);
        return content != null ? content : "";
    }
    
    /**
     * Legacy method for checking if a file exists - maintains compatibility with existing code
     * 
     * @param filePath Path to the file
     * @return true if file exists, false otherwise
     */
    public static boolean checkFileExists(String filePath) {
        return fileExists(filePath);
    }
    
    /**
     * Legacy method for finding text in a file - maintains compatibility with existing code
     * 
     * @param filePath Path to the file
     * @param text Text to search for
     * @return true if text is found, false otherwise
     */
    public static boolean findTextInFile(String filePath, String text) {
        return containsString(filePath, text);
    }
    
    /**
     * Legacy method for reading property values - maintains compatibility with existing code
     * 
     * @param filePath Path to the properties file
     * @param propertyKey Property key to look for
     * @return The property value, or null if not found
     */
    public static String readPropertyValue(String filePath, String propertyKey) {
        return getPropertyValue(filePath, propertyKey);
    }
    
    /**
     * Legacy method for reading XML attribute values - maintains compatibility with existing code
     * 
     * @param filePath Path to the XML file
     * @param elementPath Path to the element
     * @param attributeName Attribute name
     * @return The attribute value, or null if not found
     */
    public static String readXmlAttributeValue(String filePath, String elementPath, String attributeName) {
        return getXmlValue(filePath, elementPath + "/@" + attributeName);
    }
    
    /**
     * Legacy method for reading XML element text - maintains compatibility with existing code
     * 
     * @param filePath Path to the XML file
     * @param elementPath Path to the element
     * @return The element text, or null if not found
     */
    public static String readXmlElementText(String filePath, String elementPath) {
        return getXmlValue(filePath, elementPath);
    }
    
    /**
     * Legacy method for reading JSON property value - maintains compatibility with existing code
     * 
     * @param filePath Path to the JSON file
     * @param jsonPath Path to the JSON property
     * @return The property value, or null if not found
     */
    public static String readJsonPropertyValue(String filePath, String jsonPath) {
        return getJsonValue(filePath, jsonPath);
    }
    
    /**
     * Legacy method for file replacement operations - maintains compatibility with FileEditHandler
     * 
     * @param filePath Path to the file
     * @param searchText Text to search for
     * @param replaceText Text to replace with
     * @return true if replacement was successful, false otherwise
     */
    public static boolean replaceInFile(String filePath, String searchText, String replaceText) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        try {
            String content = readFileAsString(resolvedPath);
            if (content == null) {
                LOGGER.warning("Could not read file for replacement: " + resolvedPath);//No I18N
                return false;
            }
            
            // Check if content contains the search text
            if (!content.contains(searchText)) {
                LOGGER.warning("Search text not found in file: " + searchText);//No I18N
                return false;
            }
            
            // Perform replacement
            String newContent = content.replace(searchText, replaceText);
            
            // Write back to file
            Files.write(Paths.get(resolvedPath), newContent.getBytes(StandardCharsets.UTF_8));
            
            LOGGER.info("Successfully replaced text in file: " + resolvedPath);//No I18N
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error replacing text in file: " + resolvedPath, e);//No I18N
            return false;
        }
    }
    
    /**
     * Legacy method for updating property values - maintains compatibility with FileEditHandler
     * 
     * @param filePath Path to the properties file
     * @param key Property key
     * @param value Property value
     * @return true if update was successful, false otherwise
     */
    public static boolean updatePropertyValue(String filePath, String key, String value) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        try {
            // For .properties files, use Properties API
            if (resolvedPath.toLowerCase().endsWith(".properties")) {//No I18N
                Properties props = new Properties();
                
                // Read properties
                try (InputStream input = new FileInputStream(resolvedPath)) {
                    props.load(input);
                }
                
                // Update property
                props.setProperty(key, value);
                
                // Write back
                try (OutputStream output = new FileOutputStream(resolvedPath)) {
                    props.store(output, "Updated by FileReader");//No I18N
                }
                
                return true;
            }
            
            // For other files, use pattern matching
            List<String> lines = readLines(resolvedPath);
            boolean updated = false;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                
                // Match "key = value" or "key: value" patterns
                if (line.trim().startsWith(key + "=") || line.trim().startsWith(key + ":") ||
                    line.trim().startsWith(key + " =") || line.trim().startsWith(key + " :")) {
                    
                    // Keep the same format (= or :) and spacing
                    String separator = line.contains("=") ? "=" : ":";
                    int separatorIndex = line.indexOf(separator);
                    String prefix = line.substring(0, separatorIndex + 1);
                    
                    // Replace the line
                    lines.set(i, prefix + " " + value);
                    updated = true;
                    break;
                }
            }
            
            // If key not found, append it
            if (!updated) {
                lines.add(key + "=" + value);
            }
            
            // Write back to file
            Files.write(Paths.get(resolvedPath), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));//NO I18N
            
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error updating property value: " + resolvedPath, e);//No I18N
            return false;
        }
    }
    
    /**
     * Legacy method for appending text to file - maintains compatibility with existing code
     * 
     * @param filePath Path to the file
     * @param textToAppend Text to append
     * @return true if append was successful, false otherwise
     */
    public static boolean appendToFile(String filePath, String textToAppend) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        try {
            Files.write(
                Paths.get(resolvedPath), 
                textToAppend.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND
            );
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error appending to file: " + resolvedPath, e);//No I18N
            return false;
        }
    }
    
    /**
     * Legacy method for getting all matched lines - maintains compatibility with ResultAnalyzer
     * 
     * @param filePath Path to the file
     * @param pattern Pattern to match
     * @return List of matched lines, or empty list if none found or error occurs
     */
    public static List<String> getMatchedLines(String filePath, String pattern) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        List<String> matchedLines = new ArrayList<>();
        
        try {
            List<String> allLines = readLines(resolvedPath);
            Pattern regex = Pattern.compile(pattern);
            
            for (String line : allLines) {
                if (regex.matcher(line).find()) {
                    matchedLines.add(line);
                }
            }
            
            return matchedLines;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting matched lines: " + resolvedPath, e);//No I18N
            return Collections.emptyList();
        }
    }
    
    /**
     * Enhanced method to check if a file contains a value with support for different formats
     * 
     * @param filePath Path to the file, can contain server_home
     * @param value The value to check for
     * @return true if the value is found, false otherwise
     */
    public static boolean checkValueInFile(String filePath, String value) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (value == null || value.isEmpty()) {
            LOGGER.warning("Value is empty");//No I18N
            return false;
        }
        
        LOGGER.fine("Checking value in file: " + resolvedPath);//No I18N
        
        try {
            // First try direct string contains
            if (containsString(resolvedPath, value)) {
                return true;
            }
            
            // Get file extension for specialized checks
            String extension = getFileExtension(resolvedPath).toLowerCase();
            
            // For properties files, also check for value as a property value
            if ("properties".equals(extension) || "conf".equals(extension) || //No I18N
                "ini".equals(extension) || "config".equals(extension)) {//No I18N
                
                // Load the properties file
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(resolvedPath)) {
                    props.load(fis);
                    
                    // Check if any property has this value
                    for (Object val : props.values()) {
                        if (val != null && val.toString().equals(value)) {
                            return true;
                        }
                    }
                }
            }
            
            // For XML files, check for the value as element content or attribute value
            if ("xml".equals(extension)) {
                String content = readFileAsString(resolvedPath);
                
                // Check element content pattern: >value<
                String elementPattern = ">" + Pattern.quote(value) + "<";
                if (Pattern.compile(elementPattern).matcher(content).find()) {
                    return true;
                }
                
                // Check attribute value pattern: ="value"
                String attrPattern = "=\"" + Pattern.quote(value) + "\"";
                if (Pattern.compile(attrPattern).matcher(content).find()) {
                    return true;
                }
            }
            
            // For JSON files, check for the value as a JSON value
            if ("json".equals(extension)) {
                String content = readFileAsString(resolvedPath);
                
                // Check JSON value pattern: :"value" or :"value",
                String jsonPattern = ":\\s*\"" + Pattern.quote(value) + "\"\\s*[,}]";//No I18N
                if (Pattern.compile(jsonPattern).matcher(content).find()) {
                    return true;
                }
                
                // Also check numeric values without quotes
                if (value.matches("\\d+(\\.\\d+)?")) {//No I18N
                    String numPattern = ":\\s*" + Pattern.quote(value) + "\\s*[,}]";//No I18N
                    if (Pattern.compile(numPattern).matcher(content).find()) {
                        return true;
                    }
                }
            }
            
            // Value not found in any format
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error checking value in file: " + resolvedPath, e);//No I18N
            return false;
        }
    }
    
    /**
     * Check if a key-value pair exists in a file
     * 
     * @param filePath Path to the file, can contain server_home
     * @param key The key to check for
     * @param value The value to check for
     * @return true if the key-value pair is found, false otherwise
     */
    public static boolean checkKeyValueInFile(String filePath, String key, String value) {
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        if (key == null || key.isEmpty()) {
            LOGGER.warning("Key is empty");//No I18N
            return false;
        }
        
        if (value == null || value.isEmpty()) {
            LOGGER.warning("Value is empty");//No I18N
            return false;
        }
        
        LOGGER.fine("Checking key-value pair in file: " + resolvedPath);//No I18N
        
        try {
            // Get file extension
            String extension = getFileExtension(resolvedPath).toLowerCase();
            
            // For properties files
            if ("properties".equals(extension) || "conf".equals(extension) || //No I18N
                "ini".equals(extension) || "config".equals(extension)) {//No I18N
                
                // Load the properties file
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(resolvedPath)) {
                    props.load(fis);
                    
                    // Check if the property matches
                    String propValue = props.getProperty(key);
                    return value.equals(propValue);
                }
            }
            
            // For XML files
            if ("xml".equals(extension)) {
                // Use XPath-like navigation (element[@attr=value])
                String attrValue = getXmlValue(resolvedPath, key + "[@value='" + value + "']");//No I18N
                return attrValue != null;
            }
            
            // For JSON files 
            if ("json".equals(extension)) {
                // Use JSON path navigation
                String jsonValue = getJsonValue(resolvedPath, key);
                return value.equals(jsonValue);
            }
            
            // For text files, check for key=value or key:value
            List<String> lines = readLines(resolvedPath);
            for (String line : lines) {
                // Match key=value or key:value patterns with variations in spacing
                if (line.matches("\\s*" + Pattern.quote(key) + "\\s*[=:]\\s*" + Pattern.quote(value) + "\\s*")) {//No I18N
                    return true;
                }
            }
            
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error checking key-value pair in file: " + resolvedPath, e);//No I18N
            return false;
        }
    }
    
    public static String readFile(String filePath) throws IOException {
        LOGGER.info("Reading file: " + filePath); //No I18N
        
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading file: " + filePath, e); //No I18N
            throw e;
        }
    }

    public static Properties readPropertiesFile(String filePath) throws IOException {
        LOGGER.info("Reading properties file: " + filePath); //No I18N
        
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(filePath)) {
            props.load(input);
            return props;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading properties file: " + filePath, e); //No I18N
            throw e;
        }
    }
}
