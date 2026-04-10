package com.me.testcases.fileOperation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import com.me.Operation;
import com.me.util.LogManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import static com.me.testcases.DataBaseOperationHandler.saveNote;


/**
 * Handler for XML file operations
 */
public class XMLFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(XMLFileHandler.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute an XML file operation
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
            LOGGER.warning("Action is required for XML file operation");
            return false;
        }

        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warning("File path is required for XML file operation");
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
            LOGGER.warning(errorMsg);
            operation.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("XML File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target file: ").append(file.getAbsolutePath()).append("\n");

        try {
            boolean success = false;

            switch (action.toLowerCase()) {
//                case "create":
//                    success = createXmlFile(file, operation, remarkBuilder);
//                    break;
//                case "read":
//                    success = readXmlFile(file, operation, remarkBuilder);
//                    break;
//                case "validate":
//                    success = validateXmlFile(file, operation, remarkBuilder);
//                    break;
//                case "query":
//                    success = queryXmlFile(file, operation, remarkBuilder);
//                    break;
//                case "update_element":
//                    success = updateXmlElement(file, operation, remarkBuilder);
//                    break;
//                case "update_attribute":
//                    success = updateXmlAttribute(file, operation, remarkBuilder);
//                    break;
//                case "add_element":
//                    success = addXmlElement(file, operation, remarkBuilder);
//                    break;
//                case "add_attribute":
//                    success = addXmlAttribute(file, operation, remarkBuilder);
//                    break;
//                case "remove_element":
//                    success = removeXmlElement(file, operation, remarkBuilder);
//                    break;
//                case "remove_attribute":
//                    success = removeXmlAttribute(file, operation, remarkBuilder);
//                    break;
//                case "transform":
//                    success = transformXmlFile(file, operation, remarkBuilder);
//                    break;
                case "value_should_be_present":
                    success = checkValuePresence(file, operation, remarkBuilder, true);
                    break;
                case "value_should_be_removed":
                    success = checkValuePresence(file, operation, remarkBuilder, false);
                    break;
                default:
                    String errorMsg = "Unsupported action for XML file: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing XML file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }

    /**
     * Check if a value is present or absent in an XML file
     */
    private static boolean checkValuePresence(File file, Operation operation, StringBuilder remarks, boolean shouldExist) {
        try {
            String xpath = operation.getParameter("path");
            String expectedValue = operation.getParameter("value");

            if (xpath == null || xpath.isEmpty()) {
                remarks.append("Error: xpath parameter is required\n");
                return false;
            }

            // Parse XML document
            Document doc = parseXmlFile(file);

            // Find nodes at xpath
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpathObj = xpathFactory.newXPath();
            XPathExpression expr = xpathObj.compile(xpath);

            NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

            boolean nodeExists = nodes.getLength() > 0;
            boolean valueMatches = false;
            String foundValue = null;

            if (nodeExists) {
                // Extract the found value from the first matching node
                Node node = nodes.item(0);

                if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    foundValue = node.getNodeValue();
                } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                    foundValue = node.getTextContent();
                } else {
                    foundValue = node.getNodeValue();
                }

                if (expectedValue != null && !expectedValue.isEmpty()) {
                    valueMatches = foundValue != null && foundValue.equals(expectedValue);
                }
            }

            boolean result = nodeExists;
            if (expectedValue != null && nodeExists) {
                result = valueMatches;
            }

            if (shouldExist == result) {
                if (shouldExist) {
                    remarks.append("✓ Found XML path: ").append(xpath).append("\n");
                    if (expectedValue != null) {
                        remarks.append("  Expected value: ").append(expectedValue).append("\n");
                        remarks.append("  Actual value: ").append(foundValue).append("\n");
                    } else {
                        remarks.append("  Node exists with value: ").append(foundValue).append("\n");
                    }
                    if (operation.hasNote()){
                        saveNote(operation, foundValue);
                    }
                    return true;
                } else {
                    remarks.append("✓ Confirmed XML path or value is absent: ").append(xpath).append("\n");
                    return true;
                }
            } else {
                if (shouldExist) {
                    remarks.append("✗ XML path not found or value mismatch: ").append(xpath).append("\n");
                    if (expectedValue != null && nodeExists) {
                        remarks.append("  Expected value: ").append(expectedValue).append("\n");
                        remarks.append("  Actual value: ").append(foundValue).append("\n");
                    }
                } else {
                    remarks.append("✗ XML path or value exists but should not: ").append(xpath).append("\n");
                    if (nodeExists) {
                        remarks.append("  Current value: ").append(foundValue).append("\n");
                    }
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking XML value", e);
            remarks.append("Error: ").append(e.getMessage());
            return false;
        }
    }


    /**
     * Create a new XML file
     */
    private static boolean createXmlFile(File file, Operation operation, StringBuilder remarks) throws Exception {
        String rootElement = operation.getParameter("root_element");
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

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // Create XML document
        Document doc;

        if (content != null && !content.trim().isEmpty()) {
            // Parse the provided XML content
            doc = docBuilder.parse(new InputSource(new StringReader(content)));
            remarks.append("XML file created with provided content.");
        } else {
            // Create a new XML document with root element
            doc = docBuilder.newDocument();
            if (rootElement == null || rootElement.trim().isEmpty()) {
                rootElement = "root";
            }
            Element rootElem = doc.createElement(rootElement);
            doc.appendChild(rootElem);
            remarks.append("XML file created with root element <").append(rootElement).append(">.");
        }

        // Write XML to file
        writeXmlDocument(doc, file);

        return true;
    }

    /**
     * Read XML file content
     */
    private static boolean readXmlFile(File file, Operation operation, StringBuilder remarks) throws Exception {
        String format = operation.getParameter("format");
        boolean prettyPrint = "pretty".equalsIgnoreCase(format);

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Convert DOM to String
        String xmlContent = domToString(doc, prettyPrint);

        operation.setParameter("xml_content", xmlContent);
        remarks.append("XML file read successfully. ")
                .append(file.length()).append(" bytes.");

        return true;
    }

    /**
     * Validate XML file against schema
     */
    private static boolean validateXmlFile(File file, Operation operation, StringBuilder remarks) throws Exception {
        String schemaPath = operation.getParameter("schema_path");

        if (schemaPath == null || schemaPath.isEmpty()) {
            remarks.append("Schema path not provided for validation.");
            return false;
        }

        File schemaFile = new File(schemaPath);
        if (!schemaFile.exists()) {
            remarks.append("Schema file not found: ").append(schemaPath);
            return false;
        }

        // Create schema
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(schemaFile);

        // Create validator
        Validator validator = schema.newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            private List<String> errors = new ArrayList<>();

            @Override
            public void warning(SAXParseException exception) {
                errors.add("Warning: " + exception.getMessage());
            }

            @Override
            public void error(SAXParseException exception) {
                errors.add("Error: " + exception.getMessage());
            }

            @Override
            public void fatalError(SAXParseException exception) {
                errors.add("Fatal Error: " + exception.getMessage());
            }

            public List<String> getErrors() {
                return errors;
            }
        });

        // Validate
        validator.validate(new StreamSource(file));

        ErrorHandler handler = validator.getErrorHandler();
        List<String> errors = (List<String>) ((ErrorHandler) handler).getClass().getMethod("getErrors").invoke(handler);

        if (errors.isEmpty()) {
            remarks.append("XML validation successful. The file conforms to the schema.");
            operation.setParameter("validation_result", "valid");
            return true;
        } else {
            remarks.append("XML validation failed with the following errors:\n");
            for (String error : errors) {
                remarks.append("- ").append(error).append("\n");
            }
            operation.setParameter("validation_result", "invalid");
            operation.setParameter("validation_errors", String.join("\n", errors));
            return false;
        }
    }

    /**
     * Query XML file using XPath
     */
    private static boolean queryXmlFile(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xpathQuery = operation.getParameter("xpath");

        if (xpathQuery == null || xpathQuery.isEmpty()) {
            remarks.append("XPath query not provided.");
            return false;
        }

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Create XPath
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        XPathExpression expr = xpath.compile(xpathQuery);

        // Execute query
        NodeList result = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        // Build result
        StringBuilder resultBuilder = new StringBuilder();
        List<String> nodeValues = new ArrayList<>();

        for (int i = 0; i < result.getLength(); i++) {
            Node node = result.item(i);
            String nodeValue;

            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    Element element = (Element) node;
                    nodeValue = element.getTextContent();
                    resultBuilder.append("<").append(element.getNodeName()).append(">");
                    resultBuilder.append(nodeValue);
                    resultBuilder.append("</").append(element.getNodeName()).append(">");
                    break;
                case Node.ATTRIBUTE_NODE:
                    nodeValue = node.getNodeValue();
                    resultBuilder.append(node.getNodeName()).append("=\"").append(nodeValue).append("\"");
                    break;
                default:
                    nodeValue = node.getNodeValue();
                    resultBuilder.append(nodeValue);
            }

            nodeValues.add(nodeValue);
            resultBuilder.append("\n");
        }

        operation.setParameter("query_result", resultBuilder.toString());
        operation.setParameter("result_count", String.valueOf(result.getLength()));
        operation.setParameter("result_values", String.join(",", nodeValues));

        remarks.append("XPath query executed successfully. Found ")
                .append(result.getLength()).append(" node(s).");

        return true;
    }

    /**
     * Update XML element content
     */
    private static boolean updateXmlElement(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xpath = operation.getParameter("xpath");
        String newValue = operation.getParameter("new_value");

        if (xpath == null || xpath.isEmpty()) {
            remarks.append("XPath expression not provided for element update.");
            return false;
        }

        if (newValue == null) {
            newValue = ""; // Empty string if not specified
        }

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Find elements to update
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpathObj = xpathFactory.newXPath();
        XPathExpression expr = xpathObj.compile(xpath);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        if (nodes.getLength() == 0) {
            remarks.append("No elements found matching XPath: ").append(xpath);
            return false;
        }

        // Update elements
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            node.setTextContent(newValue);
        }

        // Write back to file
        writeXmlDocument(doc, file);

        remarks.append("Updated ").append(nodes.getLength())
                .append(" element(s) matching XPath: ").append(xpath);

        return true;
    }

    /**
     * Update XML attribute
     */
    private static boolean updateXmlAttribute(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xpath = operation.getParameter("xpath");
        String attribute = operation.getParameter("attribute");
        String newValue = operation.getParameter("new_value");

        if (xpath == null || xpath.isEmpty()) {
            remarks.append("XPath expression not provided for attribute update.");
            return false;
        }

        if (attribute == null || attribute.isEmpty()) {
            remarks.append("Attribute name not provided for update.");
            return false;
        }

        if (newValue == null) {
            newValue = ""; // Empty string if not specified
        }

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Find elements to update attributes on
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpathObj = xpathFactory.newXPath();
        XPathExpression expr = xpathObj.compile(xpath);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        if (nodes.getLength() == 0) {
            remarks.append("No elements found matching XPath: ").append(xpath);
            return false;
        }

        // Update attributes
        int updatedCount = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) nodes.item(i);
                element.setAttribute(attribute, newValue);
                updatedCount++;
            }
        }

        // Write back to file
        writeXmlDocument(doc, file);

        remarks.append("Updated attribute '").append(attribute)
                .append("' on ").append(updatedCount)
                .append(" element(s) matching XPath: ").append(xpath);

        return true;
    }

    /**
     * Add a new XML element
     */
    private static boolean addXmlElement(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xpath = operation.getParameter("xpath");
        String elementName = operation.getParameter("element_name");
        String elementValue = operation.getParameter("element_value");
        String position = operation.getParameter("position"); // child, before, after

        if (xpath == null || xpath.isEmpty()) {
            remarks.append("XPath expression not provided for adding element.");
            return false;
        }

        if (elementName == null || elementName.isEmpty()) {
            remarks.append("Element name not provided.");
            return false;
        }

        if (position == null || position.isEmpty()) {
            position = "child"; // Default to adding as child
        }

        if (elementValue == null) {
            elementValue = ""; // Empty string if not specified
        }

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Find target elements
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpathObj = xpathFactory.newXPath();
        XPathExpression expr = xpathObj.compile(xpath);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        if (nodes.getLength() == 0) {
            remarks.append("No elements found matching XPath: ").append(xpath);
            return false;
        }

        // Create new element
        Element newElement = doc.createElement(elementName);
        newElement.setTextContent(elementValue);

        // Add attributes if specified
        String attributes = operation.getParameter("attributes");
        if (attributes != null && !attributes.isEmpty()) {
            String[] attrPairs = attributes.split(";");
            for (String pair : attrPairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    newElement.setAttribute(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }

        // Add element to each target node
        int addedCount = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node targetNode = nodes.item(i);

            if (targetNode.getNodeType() == Node.ELEMENT_NODE) {
                Element targetElement = (Element) targetNode;

                switch (position.toLowerCase()) {
                    case "child":
                        targetElement.appendChild(newElement.cloneNode(true));
                        break;
                    case "before":
                        targetElement.getParentNode().insertBefore(newElement.cloneNode(true), targetElement);
                        break;
                    case "after":
                        if (targetElement.getNextSibling() != null) {
                            targetElement.getParentNode().insertBefore(newElement.cloneNode(true),
                                    targetElement.getNextSibling());
                        } else {
                            targetElement.getParentNode().appendChild(newElement.cloneNode(true));
                        }
                        break;
                    default:
                        remarks.append("Invalid position specified: ").append(position);
                        return false;
                }

                addedCount++;
            }
        }

        // Write back to file
        writeXmlDocument(doc, file);

        remarks.append("Added element <").append(elementName).append("> ")
                .append(position).append(" ")
                .append(addedCount).append(" element(s) matching XPath: ")
                .append(xpath);

        return true;
    }

    /**
     * Add an attribute to XML elements
     */
    private static boolean addXmlAttribute(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xpath = operation.getParameter("xpath");
        String attribute = operation.getParameter("attribute");
        String value = operation.getParameter("value");

        if (xpath == null || xpath.isEmpty()) {
            remarks.append("XPath expression not provided for adding attribute.");
            return false;
        }

        if (attribute == null || attribute.isEmpty()) {
            remarks.append("Attribute name not provided.");
            return false;
        }

        if (value == null) {
            value = ""; // Empty string if not specified
        }

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Find elements to add attribute to
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpathObj = xpathFactory.newXPath();
        XPathExpression expr = xpathObj.compile(xpath);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        if (nodes.getLength() == 0) {
            remarks.append("No elements found matching XPath: ").append(xpath);
            return false;
        }

        // Add attribute to elements
        int addedCount = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) nodes.item(i);
                element.setAttribute(attribute, value);
                addedCount++;
            }
        }

        // Write back to file
        writeXmlDocument(doc, file);

        remarks.append("Added attribute '").append(attribute).append("=\"").append(value).append("\"' ")
                .append("to ").append(addedCount)
                .append(" element(s) matching XPath: ").append(xpath);

        return true;
    }

    /**
     * Remove XML elements
     */
    private static boolean removeXmlElement(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xpath = operation.getParameter("xpath");

        if (xpath == null || xpath.isEmpty()) {
            remarks.append("XPath expression not provided for removing elements.");
            return false;
        }

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Find elements to remove
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpathObj = xpathFactory.newXPath();
        XPathExpression expr = xpathObj.compile(xpath);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        if (nodes.getLength() == 0) {
            remarks.append("No elements found matching XPath: ").append(xpath);
            return false;
        }

        // Remove elements (note: we need to collect them first since removing
        // affects the NodeList)
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            toRemove.add(nodes.item(i));
        }

        for (Node node : toRemove) {
            Node parentNode = node.getParentNode();
            if (parentNode != null) {
                parentNode.removeChild(node);
            }
        }

        // Write back to file
        writeXmlDocument(doc, file);

        remarks.append("Removed ").append(toRemove.size())
                .append(" element(s) matching XPath: ").append(xpath);

        return true;
    }

    /**
     * Remove attributes from XML elements
     */
    private static boolean removeXmlAttribute(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xpath = operation.getParameter("xpath");
        String attribute = operation.getParameter("attribute");

        if (xpath == null || xpath.isEmpty()) {
            remarks.append("XPath expression not provided for removing attribute.");
            return false;
        }

        if (attribute == null || attribute.isEmpty()) {
            remarks.append("Attribute name not provided for removal.");
            return false;
        }

        // Parse XML document
        Document doc = parseXmlFile(file);

        // Create backup if requested
        boolean createBackup = Boolean.parseBoolean(operation.getParameter("backup"));
        if (createBackup) {
            String backupPath = file.getPath() + ".bak";
            Files.copy(file.toPath(), Paths.get(backupPath));
            remarks.append("Backup created at: ").append(backupPath).append("\n");
        }

        // Find elements with attribute to remove
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpathObj = xpathFactory.newXPath();
        XPathExpression expr = xpathObj.compile(xpath);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        if (nodes.getLength() == 0) {
            remarks.append("No elements found matching XPath: ").append(xpath);
            return false;
        }

        // Remove attribute from elements
        int removedCount = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) nodes.item(i);
                if (element.hasAttribute(attribute)) {
                    element.removeAttribute(attribute);
                    removedCount++;
                }
            }
        }

        // Write back to file
        writeXmlDocument(doc, file);

        remarks.append("Removed attribute '").append(attribute)
                .append("' from ").append(removedCount)
                .append(" element(s) matching XPath: ").append(xpath);

        return true;
    }

    /**
     * Transform XML file using XSLT
     */
    private static boolean transformXmlFile(File file, Operation operation, StringBuilder remarks) throws Exception {
        String xsltPath = operation.getParameter("xslt_path");
        String outputPath = operation.getParameter("output_path");

        if (xsltPath == null || xsltPath.isEmpty()) {
            remarks.append("XSLT stylesheet path not provided.");
            return false;
        }

        if (outputPath == null || outputPath.isEmpty()) {
            outputPath = file.getPath() + ".transformed.xml";
        }

        File xsltFile = new File(xsltPath);
        if (!xsltFile.exists()) {
            remarks.append("XSLT stylesheet not found: ").append(xsltPath);
            return false;
        }

        // Create parent directories for output if they don't exist
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        // Setup XSLT transformation
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        StreamSource xsltSource = new StreamSource(xsltFile);
        Transformer transformer = transformerFactory.newTransformer(xsltSource);

        // Set output properties
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        // Perform transformation
        StreamSource xmlSource = new StreamSource(file);
        StreamResult result = new StreamResult(outputFile);
        transformer.transform(xmlSource, result);

        remarks.append("XML file transformed successfully using XSLT stylesheet: ")
                .append(xsltPath).append("\nOutput written to: ").append(outputPath);

        return true;
    }

    /**
     * Helper method to parse XML file
     */
    private static Document parseXmlFile(File file) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // Support for namespaces
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }

    /**
     * Helper method to write XML document to file
     */
    private static void writeXmlDocument(Document doc, File file) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            StreamResult result = new StreamResult(fos);
            transformer.transform(source, result);
        }
    }

    /**
     * Helper method to convert DOM document to string
     */
    private static String domToString(Document doc, boolean prettyPrint) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        if (prettyPrint) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        }

        DOMSource source = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);

        return writer.toString();
    }
}