package com.me.testcases.fileOperation;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;

import static com.me.testcases.DataBaseOperationHandler.saveNote;
import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.GOATCommonConstants.BUNDLE_VERSION;
import static com.me.util.GOATCommonConstants.JAR_SIGN_CHECKER_EXE;

/**
 * Handler for JAR file operations
 */
public class JarFileHandler {
    private static final Logger LOGGER = LogManager.getLogger(JarFileHandler.class, LogManager.LOG_TYPE.FW);
    private static final int BUFFER_SIZE = 8192;

    /**
     * Execute a JAR file operation
     *
     * @param operation The operation containing parameters
     * @return true if operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation operation) {
        if (operation == null) {
            LOGGER.warning("Operation is null");
            return false;
        }
        operation = ResolveOperationParameters.resolveOperationParameters(operation);

        String action = operation.getParameter("action");
        String jarPath = operation.getParameter("jar_path");

        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for JAR file operation");
            return false;
        }

        if (jarPath == null || jarPath.isEmpty()) {
            LOGGER.warning("JAR path is required for JAR file operation");
            return false;
        }

        // Create file object
        File jarFile = new File(jarPath);

        // For create action, we don't need the file to exist
        if (!jarFile.exists() && !action.equalsIgnoreCase("create")) {
            String errorMsg = "JAR file not found: " + jarFile.getAbsolutePath();
            LOGGER.warning(errorMsg);
            operation.setRemarks(errorMsg);
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("JAR File Operation: ").append(action).append("\n");
        remarkBuilder.append("Target JAR: ").append(jarFile.getAbsolutePath()).append("\n");

        try {
            boolean success;

            switch (action.toLowerCase()) {
                case "create":
                    success = createJarFile(jarFile, operation, remarkBuilder);
                    break;
                case "extract":
                    success = extractJar(jarFile, operation, remarkBuilder);
                    break;
                case "add":
                    success = addFilesToJar(jarFile, operation, remarkBuilder);
                    break;
//                case "list":
//                    success = listJarContents(jarFile, operation, remarkBuilder);
//                    break;
                case "update_manifest":
                    success = updateJarManifest(jarFile, operation, remarkBuilder);
                    break;
                case "find_class":
                    success = findClassInJar(jarFile, operation, remarkBuilder);
                    break;
                case "check_signed":
                case "sign":
                    success = signJarFile(jarFile, operation, remarkBuilder);
                    break;
//                case "verify":
//                    success = verifyJarSignature(jarFile, operation, remarkBuilder);
//                    break;
                case "extract_file":
                    success = extractFileFromJar(jarFile, operation, remarkBuilder);
                    break;
                case "get_manifest":
                    success = getJarManifest(jarFile, operation, remarkBuilder);
                    break;
                case "check_version":
                    success = checkJarVersion(jarFile, operation, remarkBuilder);
                    break;
                case "get_version":
                    success = getJarVersion(jarFile, operation, remarkBuilder);
                    if (success){
                        if (operation.hasNote()){
                            saveNote(operation, operation.getParameter("save_note"));
                        }
                    }
                    break;
                default:
                    String errorMsg = "Unsupported action for JAR file: " + action;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    success = false;
            }

            operation.setRemarks(remarkBuilder.toString());
            return success;

        } catch (Exception e) {
            String errorMsg = "Error executing JAR file operation: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            operation.setRemarks(remarkBuilder.toString() + "\nError: " + errorMsg);
            return false;
        }
    }

    /**
     * Check JAR version from manifest information
     */
    private static boolean checkJarVersion(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
        LOGGER.log(Level.INFO, "Checking JAR version for file: {0}", jarFile.getAbsolutePath());
        String expectedVersion = operation.getParameter("expected_version");
        String versionAttribute = operation.getParameter("version_attribute");
        String comparisonType = operation.getParameter("comparison_type"); // exact, minimum, contains

        LOGGER.log(Level.INFO, "Version check parameters - Expected: {0}, Attribute: {1}, Comparison: {2}",
                new Object[]{expectedVersion, versionAttribute, comparisonType});

        // Default to Implementation-Version if not specified
        if (versionAttribute == null || versionAttribute.isEmpty()) {
            versionAttribute = BUNDLE_VERSION.toString();
            LOGGER.log(Level.INFO, "Using default version attribute: {0}", versionAttribute);
        }

        // Default to exact comparison if not specified
        if (comparisonType == null || comparisonType.isEmpty()) {
            comparisonType = "exact";
            LOGGER.log(Level.INFO, "Using default comparison type: {0}", comparisonType);
        }

        // Extract version from filename if no expected version is provided
        if (expectedVersion == null || expectedVersion.isEmpty()) {
            String jarName = jarFile.getName();
            LOGGER.log(Level.INFO, "No expected version specified, attempting to extract from filename: {0}", jarName);

            // Common JAR naming patterns like name-x.y.z.jar, name_x.y.z.jar
            expectedVersion = extractVersionFromFilename(jarName);

            if (expectedVersion != null) {
                LOGGER.log(Level.INFO, "Extracted version from filename: {0}", expectedVersion);
                operation.setParameter("expected_version", expectedVersion);
                remarks.append("Using version extracted from filename: ").append(expectedVersion).append("\n");
            } else {
                LOGGER.log(Level.INFO, "Could not extract version from filename, will just report actual version");
            }
        }

        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();

            if (manifest == null) {
                remarks.append("No manifest found in JAR file");
                operation.setParameter("has_manifest", "false");
                operation.setParameter("has_version", "false");
                LOGGER.log(Level.WARNING, "No manifest found in JAR file: {0}", jarFile.getAbsolutePath());
                return false;
            }
            LOGGER.log(Level.FINE, "Found manifest in JAR file");

            // Get main attributes
            Attributes mainAttrs = manifest.getMainAttributes();
            String actualVersion = mainAttrs.getValue(versionAttribute);

            if (actualVersion == null) {
                // Try common version attributes if the specified one is not found
                actualVersion = findVersionInManifest(mainAttrs);

                if (actualVersion == null) {
                    remarks.append("Version information not found in manifest");
                    operation.setParameter("has_version", "false");
                    LOGGER.log(Level.WARNING, "No version information found in manifest of JAR: {0}",
                            jarFile.getAbsolutePath());
                    return false;
                }

                LOGGER.log(Level.INFO, "Found version using alternate attribute: {0}", actualVersion);
                versionAttribute = "Alternate-Found";
            }

            // Store the found version
            operation.setParameter("has_version", "true");
            operation.setParameter("actual_version", actualVersion);
            LOGGER.log(Level.INFO, "Found JAR version: {0} from attribute: {1}",
                    new Object[]{actualVersion, versionAttribute});
            remarks.append("Found JAR version: ").append(actualVersion).append("\n");

            // If expected version is specified or was extracted, compare with actual version
            if (expectedVersion != null && !expectedVersion.isEmpty()) {
                boolean versionMatches = compareVersions(actualVersion, expectedVersion, comparisonType);

                if (versionMatches) {
                    remarks.append("Version check passed: ").append(actualVersion)
                            .append(" ").append(comparisonType).append(" ").append(expectedVersion);
                } else {
                    remarks.append("Version check failed: ").append(actualVersion)
                            .append(" not ").append(comparisonType).append(" ").append(expectedVersion);
                }

                operation.setParameter("version_matches", String.valueOf(versionMatches));
                LOGGER.log(Level.INFO, "JAR version check result: {0}", versionMatches);
                return versionMatches;
            }

            // No expected version provided, just report the actual version and return success
            LOGGER.log(Level.INFO, "No expected version to compare against, returning success");
            return true;
        }
    }

    /**
     * Extract version from JAR filename
     * @param filename The JAR filename
     * @return The extracted version or null if not found
     */
    private static String extractVersionFromFilename(String filename) {
        // Remove .jar extension if present
        if (filename.toLowerCase().endsWith(".jar")) {
            filename = filename.substring(0, filename.length() - 4);
        }

        // Common patterns: name-1.2.3.jar, name_1.2.3.jar, name-v1.2.3.jar
        String[] patterns = {
                "[-_]([0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?)",  // Match -1.2.3 or -1.2.3.4
                "[-_]v([0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?)", // Match -v1.2.3
                "([0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?)"       // Match just 1.2.3
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(filename);
            if (m.find()) {
                return m.group(1);
            }
        }

        return null;
    }

    /**
     * Try to find version information in manifest using common attributes
     * @param attrs The manifest attributes
     * @return The version found or null
     */
    private static String findVersionInManifest(Attributes attrs) {
        // Common version attributes in JAR manifests
        String[] versionAttributes = {
                BUNDLE_VERSION.toString(),
                "Implementation-Version",
                "Bundle-Version",
                "Specification-Version",
                "Build-Version",
                "Project-Version",
                "Application-Version",
                "Manifest-Version"
        };

        for (String attr : versionAttributes) {
            String version = attrs.getValue(attr);
            if (version != null && !version.isEmpty()) {
                return version;
            }
        }

        return null;
    }

    /**
     * Compare versions based on comparison type
     * @param actual Actual version
     * @param expected Expected version
     * @param comparisonType Type of comparison (exact, minimum, contains)
     * @return True if comparison succeeds, false otherwise
     */
    private static boolean compareVersions(String actual, String expected, String comparisonType) {
        switch (comparisonType.toLowerCase()) {
            case "exact":
                return actual.equals(expected);
            case "minimum":
                return compareVersionNumbers(actual, expected) >= 0;
            case "contains":
                return actual.contains(expected);
            default:
                return actual.equals(expected);
        }
    }


    /**
     * Compare two version strings numerically (semantic versioning support)
     *
     * @param v1 First version string
     * @param v2 Second version string
     * @return positive if v1 > v2, 0 if equal, negative if v1 < v2
     */
    private static int compareVersionNumbers(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        // Compare version components
        int minLength = Math.min(parts1.length, parts2.length);
        for (int i = 0; i < minLength; i++) {
            try {
                int n1 = Integer.parseInt(parts1[i]);
                int n2 = Integer.parseInt(parts2[i]);
                int diff = Integer.compare(n1, n2);
                if (diff != 0) {
                    return diff;
                }
            } catch (NumberFormatException e) {
                // If parts cannot be parsed as numbers, compare them as strings
                int diff = parts1[i].compareTo(parts2[i]);
                if (diff != 0) {
                    return diff;
                }
            }
        }

        // If common parts are identical, the longer version is considered greater
        return Integer.compare(parts1.length, parts2.length);
    }

    /**
     * Compare two version strings (basic semantic versioning support)
     * @return positive if v1 > v2, 0 if equal, negative if v1 < v2
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int part1 = i < parts1.length ?
                    Integer.parseInt(parts1[i].replaceAll("\\D.*", "")) : 0;
            int part2 = i < parts2.length ?
                    Integer.parseInt(parts2[i].replaceAll("\\D.*", "")) : 0;

            if (part1 != part2) {
                return part1 - part2;
            }
        }

        return 0;
    }

/**
 * Get version information from JAR file and save it in format "jarname_jarversion=jarversion"
 */
private static boolean getJarVersion(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
    LOGGER.log(Level.INFO, "Getting version information from JAR file: {0}", jarFile.getAbsolutePath());

    try (JarFile jar = new JarFile(jarFile)) {
        Manifest manifest = jar.getManifest();

        if (manifest == null) {
            LOGGER.warning("No manifest found in JAR file: " + jarFile.getName());
            remarks.append("No manifest found in JAR file");
            operation.setParameter("has_manifest", "false");
            operation.setParameter("has_version", "false");
            return false;
        }
        LOGGER.log(Level.FINE, "Found manifest in JAR file: {0}", jarFile.getName());

        // Get main attributes
        Attributes mainAttrs = manifest.getMainAttributes();
        String version = mainAttrs.getValue(BUNDLE_VERSION);
        LOGGER.log(Level.FINE, "Initial version check using {0}: {1}", new Object[]{BUNDLE_VERSION, version});

        // Try alternate version attributes if not found
        if (version == null) {
            LOGGER.log(Level.INFO, "Version not found with primary attribute, trying alternatives");
            String[] commonVersionAttrs = {
                    "Bundle-Version",
                    "Specification-Version",
                    "Project-Version",
                    "Build-Version"
            };

            for (String attr : commonVersionAttrs) {
                version = mainAttrs.getValue(attr);
                if (version != null) {
                    LOGGER.log(Level.INFO, "Found version using attribute: {0} = {1}", new Object[]{attr, version});
                    operation.setParameter("version_attribute_used", attr);
                    break;
                }
            }
        } else {
            LOGGER.log(Level.INFO, "Found version using primary attribute: {0}", version);
            operation.setParameter("version_attribute_used", Attributes.Name.IMPLEMENTATION_VERSION.toString());
        }

        if (version == null) {
            LOGGER.warning("No version information found in JAR manifest: " + jarFile.getName());
            remarks.append("No version information found in JAR manifest");
            operation.setParameter("has_version", "false");
            return false;
        }

        // Get jar name without extension
        String jarName = jarFile.getName();
        if (jarName.toLowerCase().endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length() - 4);
        }
        // Create the save format: "jarname_jarversion=jarversion"
//        String noteKey = jarName + "_jarversion";
        String saveNote = version ;
        LOGGER.log(Level.INFO, "Created save note: {0}", saveNote);

        // Save version information
        operation.setParameter("has_version", "true");
        operation.setParameter("version", version);
        operation.setParameter("jar_name", jarName);
        operation.setParameter("save_note", saveNote);
//        operation.setParameter(noteKey, noteValue);

        remarks.append("JAR Version for: ").append(jarFile.getName()).append("\n");
        remarks.append("Version: ").append(version).append("\n");
        if (operation.hasNote()) remarks.append("Saved note: ").append(saveNote);

        LOGGER.log(Level.INFO, "Successfully retrieved version {0} from JAR {1}", new Object[]{version, jarFile.getName()});
//        operation.setRemarks(remarks.toString());
        return true;
    } catch (ZipException e) {
        LOGGER.log(Level.SEVERE, "Error accessing JAR file: " + jarFile.getName(), e);
        remarks.append("Error accessing JAR file: ").append(e.getMessage());
        return false;
    }
}


    /**
     * Create a new JAR file
     */
    private static boolean createJarFile(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
        LOGGER.log(Level.INFO, "Starting JAR file creation: {0}", jarFile.getAbsolutePath());

        String sourceDirPath = operation.getParameter("source_dir");
        String mainClass = operation.getParameter("main_class");
        String manifestPath = operation.getParameter("manifest_path");

        LOGGER.log(Level.INFO, "JAR creation parameters - Source Dir: {0}, Main Class: {1}, Manifest Path: {2}",
                new Object[]{sourceDirPath, mainClass, manifestPath});

        // Check if source directory is provided
        if (sourceDirPath == null || sourceDirPath.isEmpty()) {
            LOGGER.warning("Source directory not specified for JAR creation");
            remarks.append("Source directory not specified");
            return false;
        }

        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            LOGGER.log(Level.WARNING, "Source directory does not exist: {0}", sourceDirPath);
            remarks.append("Source directory does not exist: ").append(sourceDirPath);
            return false;
        }
        LOGGER.log(Level.INFO, "Validated source directory: {0}", sourceDir.getAbsolutePath());

        // Create parent directories for JAR file if they don't exist
        if (jarFile.getParentFile() != null && !jarFile.getParentFile().exists()) {
            LOGGER.log(Level.INFO, "Creating parent directories for JAR file: {0}", jarFile.getParentFile().getAbsolutePath());
            jarFile.getParentFile().mkdirs();
        }

        // Create manifest
        LOGGER.info("Creating manifest for JAR file");
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();

        // Add required manifest version attribute
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        LOGGER.fine("Added Manifest-Version: 1.0");

        // Add main class if specified
        if (mainClass != null && !mainClass.isEmpty()) {
            mainAttrs.put(Attributes.Name.MAIN_CLASS, mainClass);
            LOGGER.fine("Added Main-Class: " + mainClass);
        }

        // If custom manifest file is provided, read and merge it
        if (manifestPath != null && !manifestPath.isEmpty()) {
            File manifestFile = new File(manifestPath);
            if (manifestFile.exists()) {
                try (FileInputStream fis = new FileInputStream(manifestFile)) {
                    try {
                        // Create a separate manifest to validate and read
                        Manifest customManifest = new Manifest(fis);
                        // If no exception was thrown, merge the attributes
                        mainAttrs.putAll(customManifest.getMainAttributes());
                        LOGGER.info("Successfully loaded and merged custom manifest");
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error reading manifest file - using default manifest instead: " + e.getMessage(), e);
                        remarks.append("Warning: Could not read manifest file (").append(e.getMessage()).append(") - using default manifest\n");
                        // Continue with the default manifest
                    }
                }
            } else {
                LOGGER.warning("Specified manifest file not found: " + manifestPath);
                remarks.append("Warning: Manifest file not found: ").append(manifestPath).append(" - using default manifest\n");
            }
        }

        // Create JAR output stream
        LOGGER.log(Level.INFO, "Creating JAR output stream for: {0}", jarFile.getAbsolutePath());
        try (JarOutputStream target = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            // Add files from source directory
            LOGGER.info("Adding files from source directory to JAR");
            addDirToJar(sourceDir, sourceDir, target);
            LOGGER.info("JAR file created successfully");
            remarks.append("JAR file created successfully: ").append(jarFile.getAbsolutePath());
            return true;
        }
    }

    /**
     * Adds all files from a directory to the JAR file recursively
     *
     * @param directory The directory containing files to add
     * @param baseDir The base directory for calculating entry paths
     * @param target The JAR output stream to write entries to
     * @throws IOException If an I/O error occurs
     */
    private static void addDirToJar(File directory, File baseDir, JarOutputStream target) throws IOException {
        LOGGER.log(Level.FINE, "Adding directory to JAR: {0}", directory.getAbsolutePath());

        // Get all files in directory
        File[] files = directory.listFiles();
        if (files == null) {
            LOGGER.warning("Failed to list files in directory: " + directory.getAbsolutePath());
            return;
        }

        // Process each file/subdirectory
        for (File file : files) {
            String entryName = getEntryPath(file, baseDir);

            if (file.isDirectory()) {
                // For directories, add directory entry and process recursively
                if (!entryName.isEmpty()) {
                    // Ensure directory entries end with "/"
                    if (!entryName.endsWith("/")) {
                        entryName += "/";
                    }

                    // Create directory entry
                    JarEntry entry = new JarEntry(entryName);
                    entry.setTime(file.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                    LOGGER.log(Level.FINE, "Added directory entry: {0}", entryName);
                }

                // Recursively add contents of the directory
                addDirToJar(file, baseDir, target);
            } else {
                // For regular files, add file content to JAR
                JarEntry entry = new JarEntry(entryName);
                entry.setTime(file.lastModified());
                target.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        target.write(buffer, 0, bytesRead);
                    }
                }

                target.closeEntry();
                LOGGER.log(Level.FINE, "Added file entry: {0}", entryName);
            }
        }
    }

    /**
     * Extract contents of a JAR file
     */
private static boolean extractJar(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
    LOGGER.log(Level.INFO, "Extracting contents from JAR file: {0}", jarFile.getAbsolutePath());

    String outputDirPath = operation.getParameter("output_dir");

    // Set default output directory if not provided
    if (outputDirPath == null || outputDirPath.isEmpty()) {
        outputDirPath = jarFile.getParent() + File.separator + jarFile.getName().replaceFirst("\\.jar$", "");
        LOGGER.log(Level.INFO, "Using default output directory: {0}", outputDirPath);
    }

    File outputDir = new File(outputDirPath);
    if (!outputDir.exists()) {
        LOGGER.log(Level.FINE, "Creating output directory: {0}", outputDirPath);
        outputDir.mkdirs();
    }

    try (JarFile jar = new JarFile(jarFile)) {
        Enumeration<JarEntry> entries = jar.entries();
        int extractedCount = 0;

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            File outFile = new File(outputDir, entry.getName());

            LOGGER.log(Level.FINE, "Processing entry: {0}", entry.getName());

            // Create parent directories if needed
            if (entry.isDirectory()) {
                LOGGER.log(Level.FINE, "Creating directory: {0}", outFile.getAbsolutePath());
                outFile.mkdirs();
                continue;
            } else if (!outFile.getParentFile().exists()) {
                LOGGER.log(Level.FINE, "Creating parent directory: {0}", outFile.getParentFile().getAbsolutePath());
                outFile.getParentFile().mkdirs();
            }

            // Extract file
            try (InputStream input = jar.getInputStream(entry);
                 FileOutputStream output = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = input.read(buffer)) > 0) {
                    output.write(buffer, 0, len);
                }
                extractedCount++;
                LOGGER.log(Level.FINE, "Extracted file: {0}", outFile.getAbsolutePath());
            }
        }

        LOGGER.log(Level.INFO, "Successfully extracted {0} files from JAR to: {1}",
                new Object[]{extractedCount, outputDir.getAbsolutePath()});

        remarks.append("Extracted ").append(extractedCount).append(" files from JAR to: ")
                .append(outputDir.getAbsolutePath());

        return true;
    }
}

    /**
     * Add files to an existing JAR file
     */
/**
 * Add files to an existing JAR file
 */
private static boolean addFilesToJar(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
    String filesStr = operation.getParameter("files");
    String baseDirPath = operation.getParameter("base_dir");
    String overwriteStr = operation.getParameter("overwrite");
    boolean overwrite = Boolean.parseBoolean(overwriteStr);

    LOGGER.log(Level.INFO, "Adding files to JAR: {0}", jarFile.getAbsolutePath());
    LOGGER.log(Level.INFO, "Base directory: {0}", baseDirPath);

    if (filesStr == null || filesStr.isEmpty()) {
        LOGGER.warning("No files specified to add to JAR");
        remarks.append("No files specified to add");
        return false;
    }

    String[] filePaths = filesStr.split(",");

    // Normalize the base directory path for JAR entries
    if (baseDirPath != null && !baseDirPath.isEmpty()) {
        baseDirPath = baseDirPath.replace('\\', '/');
        if (!baseDirPath.endsWith("/")) {
            baseDirPath += "/";
        }
        LOGGER.log(Level.INFO, "Normalized base directory: {0}", baseDirPath);
    } else {
        baseDirPath = "";
    }

    // Create a temporary JAR file
    File tempFile = File.createTempFile("temp", ".jar");
    tempFile.deleteOnExit();
    LOGGER.log(Level.FINE, "Created temporary file: {0}", tempFile.getAbsolutePath());

    int addedCount = 0;
    int skippedCount = 0;
    int errorCount = 0;

    // Copy existing JAR content and prepare to track entries we've processed
    Set<String> processedEntries = new HashSet<>();

    try (JarFile jar = new JarFile(jarFile);
         JarOutputStream target = new JarOutputStream(new FileOutputStream(tempFile))) {

        // First pass: Copy existing entries except those we'll overwrite
        Enumeration<JarEntry> entries = jar.entries();
        LOGGER.log(Level.FINE, "First pass: Copying existing entries");

        // Create set of paths we'll be adding
        Set<String> newEntryPaths = new HashSet<>();
        for (String filePath : filePaths) {
            File fileToAdd = new File(filePath.trim());
            if (fileToAdd.exists()) {
                String entryPath = baseDirPath + fileToAdd.getName();
                newEntryPaths.add(entryPath);
                LOGGER.log(Level.FINE, "Will add new entry: {0}", entryPath);
            }
        }

        // Copy all existing entries except those we'll overwrite
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            // Skip if this entry will be overwritten
            if (overwrite && newEntryPaths.contains(entryName)) {
                LOGGER.log(Level.FINE, "Skipping existing entry that will be overwritten: {0}", entryName);
                continue;
            }

            processedEntries.add(entryName);
            JarEntry newEntry = new JarEntry(entryName);
            target.putNextEntry(newEntry);

            try (InputStream is = jar.getInputStream(entry)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    target.write(buffer, 0, bytesRead);
                }
            }
            target.closeEntry();
        }

        // Second pass: Add new files
        LOGGER.log(Level.FINE, "Second pass: Adding new files");
        for (String filePath : filePaths) {
            filePath = filePath.trim();
            File fileToAdd = new File(filePath);

            if (!fileToAdd.exists()) {
                LOGGER.warning("File does not exist: " + filePath);
                errorCount++;
                continue;
            }

            // Get just the filename
            String fileName = fileToAdd.getName();

            // Create the entry path with base directory
            String entryPath = baseDirPath + fileName;
            LOGGER.log(Level.INFO, "Adding file {0} as {1}", new Object[]{filePath, entryPath});

            // Skip if this entry exists and we're not overwriting
            if (!overwrite && processedEntries.contains(entryPath)) {
                LOGGER.log(Level.INFO, "Skipping file, entry already exists: {0}", entryPath);
                remarks.append("Skipping file, entry already exists: ").append(entryPath).append("\n");
                skippedCount++;
                continue;
            }

            try {
                // Add the file to the JAR
                JarEntry newEntry = new JarEntry(entryPath);
                newEntry.setTime(fileToAdd.lastModified());
                target.putNextEntry(newEntry);

                try (FileInputStream fis = new FileInputStream(fileToAdd)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        target.write(buffer, 0, bytesRead);
                    }
                }

                target.closeEntry();
                processedEntries.add(entryPath);
                addedCount++;
                LOGGER.log(Level.INFO, "Successfully added file to JAR: {0}", entryPath);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error adding file to JAR: " + filePath, e);
                errorCount++;
            }
        }
    }

    // Replace original JAR with the updated one
    LOGGER.log(Level.FINE, "Replacing original JAR with updated version");
    Files.move(tempFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    // Set results in operation parameters
    operation.setParameter("added_count", String.valueOf(addedCount));
    operation.setParameter("skipped_count", String.valueOf(skippedCount));
    operation.setParameter("error_count", String.valueOf(errorCount));

    LOGGER.log(Level.INFO, "Added {0} files to JAR {1} (skipped: {2}, errors: {3})",
            new Object[]{addedCount, jarFile.getName(), skippedCount, errorCount});

    remarks.append("JAR Update Results:\n");
    remarks.append("- Added files: ").append(addedCount).append("\n");
    remarks.append("- Skipped files: ").append(skippedCount).append("\n");
    remarks.append("- Failed files: ").append(errorCount).append("\n");

    return addedCount > 0;
}

/**
 * Add a single file to JAR
 */
private static boolean addFileToJar(File fileToAdd, File baseDir, JarOutputStream target,
                                  Set<String> entryNames, boolean overwrite, StringBuilder remarks) throws IOException {
    // Determine entry path
    String entryPath = getEntryPath(fileToAdd, baseDir);
    LOGGER.log(Level.FINE, "Processing file: {0} -> {1}", new Object[]{fileToAdd.getAbsolutePath(), entryPath});

    // Check if entry with same name already exists
    if (entryNames.contains(entryPath)) {
        if (!overwrite) {
            LOGGER.log(Level.FINE, "Skipping existing entry: {0}", entryPath);
            remarks.append("Skipped existing entry: ").append(entryPath).append("\n");
            return false;
        } else {
            LOGGER.log(Level.FINE, "Overwriting existing entry: {0}", entryPath);
            remarks.append("Overwriting entry: ").append(entryPath).append("\n");
            // We keep the entry in entryNames set to detect duplicates in input files
        }
    } else {
        entryNames.add(entryPath);
    }

    try {
        // Add the file
        JarEntry entry = new JarEntry(entryPath);
        entry.setTime(fileToAdd.lastModified());
        target.putNextEntry(entry);

        try (FileInputStream input = new FileInputStream(fileToAdd)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = input.read(buffer)) > 0) {
                target.write(buffer, 0, len);
            }
        }

        target.closeEntry();
        LOGGER.log(Level.FINE, "Added file: {0}", entryPath);
        return true;
    } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Error adding file: " + fileToAdd.getName(), e);
        remarks.append("Error adding file: ").append(fileToAdd.getName())
               .append(" - ").append(e.getMessage()).append("\n");
        return false;
    }
}

/**
 * Add directory contents recursively to JAR
 * @return int[3] array with {added count, skipped count, error count}
 */
private static int[] addDirectoryToJar(File directory, File baseDir, JarOutputStream target,
                                     Set<String> entryNames, boolean overwrite, StringBuilder remarks) throws IOException {
    int[] counts = new int[3]; // added, skipped, error

    try {
        Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    File fileToAdd = file.toFile();

                    if (addFileToJar(fileToAdd, baseDir != null ? baseDir : directory,
                            target, entryNames, overwrite, remarks)) {
                        counts[0]++;
                    } else if (entryNames.contains(getEntryPath(fileToAdd, baseDir))) {
                        counts[1]++;
                    } else {
                        counts[2]++;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error processing file: " + file, e);
                    remarks.append("Error processing file: ").append(file)
                           .append(" - ").append(e.getMessage()).append("\n");
                    counts[2]++;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOGGER.log(Level.WARNING, "Failed to visit file: " + file, exc);
                remarks.append("Failed to access file: ").append(file)
                       .append(" - ").append(exc.getMessage()).append("\n");
                counts[2]++;
                return FileVisitResult.CONTINUE;
            }
        });
    } catch (IOException e) {
        LOGGER.log(Level.SEVERE, "Error traversing directory: " + directory, e);
        remarks.append("Error traversing directory: ").append(directory)
               .append(" - ").append(e.getMessage()).append("\n");
        counts[2]++;
    }

    return counts;
}

/**
 * Get JAR entry path for a file
 */
private static String getEntryPath(File file, File baseDir) {
    if (baseDir != null && file.getAbsolutePath().startsWith(baseDir.getAbsolutePath())) {
        return baseDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
    } else {
        return file.getName();
    }
}

    /**
     * List contents of a JAR file
     */
    private static boolean listJarContents(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            List<String> fileList = new ArrayList<>();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                String entryInfo = entry.getName();
                if (entry.isDirectory()) {
                    entryInfo += " (directory)";
                } else {
                    entryInfo += String.format(" (%,d bytes)", entry.getSize());
                }

                fileList.add(entryInfo);
            }

            // Sort the list alphabetically for better readability
            Collections.sort(fileList);

            // Add information to operation parameters
            operation.setParameter("entry_count", String.valueOf(fileList.size()));
            operation.setParameter("entries", String.join("\n", fileList));

            // Build remarks
            remarks.append("JAR Contents: ").append(jarFile.getName()).append("\n");
            remarks.append("Total entries: ").append(fileList.size()).append("\n\n");

            for (String entryInfo : fileList) {
                remarks.append(entryInfo).append("\n");
            }

            return true;
        }
    }

    /**
     * Update manifest in a JAR file
     */
private static boolean updateJarManifest(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
    LOGGER.log(Level.INFO, "Starting manifest update for JAR file: {0}", jarFile.getAbsolutePath());

    String attributesStr = operation.getParameter("attributes");
    String manifestPath = operation.getParameter("manifest_path");

    LOGGER.log(Level.INFO, "Update parameters - Attributes: {0}, Manifest Path: {1}",
            new Object[]{attributesStr, manifestPath});

    if (attributesStr == null && manifestPath == null) {
        LOGGER.warning("Neither attributes nor manifest file specified");
        remarks.append("Neither attributes nor manifest file specified");
        return false;
    }

    // Create a temporary JAR file
    File tempFile = File.createTempFile("temp", ".jar");
    tempFile.deleteOnExit();
    LOGGER.log(Level.FINE, "Created temporary file: {0}", tempFile.getAbsolutePath());

    // Read existing JAR and update manifest
    try (JarFile jar = new JarFile(jarFile);
         JarOutputStream target = new JarOutputStream(new FileOutputStream(tempFile))) {

        // Get or create manifest
        Manifest manifest;
        JarEntry manifestEntry = jar.getJarEntry(JarFile.MANIFEST_NAME);
        if (manifestEntry != null) {
            try (InputStream input = jar.getInputStream(manifestEntry)) {
                manifest = new Manifest(input);
                LOGGER.log(Level.FINE, "Read existing manifest from JAR");
            }
        } else {
            manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            LOGGER.log(Level.FINE, "Created new manifest with default version");
        }

        // Update manifest from provided attributes
        if (attributesStr != null && !attributesStr.isEmpty()) {
            LOGGER.log(Level.INFO, "Updating manifest with provided attributes");
            String[] attrPairs = attributesStr.split(",");
            for (String pair : attrPairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    // Handle special attribute names
                    if (key.equalsIgnoreCase("Main-Class")) {
                        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, value);
                    } else if (key.equalsIgnoreCase("Implementation-Title")) {
                        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, value);
                    } else if (key.equalsIgnoreCase("Implementation-Version")) {
                        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, value);
                    } else if (key.equalsIgnoreCase("Implementation-Vendor")) {
                        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, value);
                    } else {
                        // Custom attribute
                        manifest.getMainAttributes().putValue(key, value);
                    }

                    LOGGER.log(Level.FINE, "Set manifest attribute: {0}={1}", new Object[]{key, value});
                    remarks.append("Set manifest attribute: ").append(key)
                            .append("=").append(value).append("\n");
                }
            }
        }

        // Update manifest from provided file
        if (manifestPath != null && !manifestPath.isEmpty()) {
            File manifestFile = new File(manifestPath);
            if (manifestFile.exists()) {
                LOGGER.log(Level.INFO, "Reading manifest from file: {0}", manifestPath);
                try (FileInputStream fis = new FileInputStream(manifestFile)) {
                    Manifest customManifest = new Manifest(fis);
                    manifest.getMainAttributes().putAll(customManifest.getMainAttributes());
                    LOGGER.log(Level.FINE, "Successfully merged manifest from file");
                    remarks.append("Updated manifest with contents from: ")
                            .append(manifestPath).append("\n");
                }
            } else {
                LOGGER.warning("Manifest file not found: " + manifestPath);
                remarks.append("Manifest file not found: ").append(manifestPath).append("\n");
            }
        }

        // Write updated manifest as the first entry
        LOGGER.log(Level.FINE, "Writing manifest as first entry in new JAR");
        target.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
        manifest.write(target);
        target.closeEntry();

        // Copy all other entries
        LOGGER.log(Level.INFO, "Copying JAR entries to new JAR file");
        Enumeration<JarEntry> entries = jar.entries();
        int copiedEntries = 0;
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!JarFile.MANIFEST_NAME.equals(entry.getName())) {
                target.putNextEntry(new JarEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream input = jar.getInputStream(entry)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = input.read(buffer)) > 0) {
                            target.write(buffer, 0, len);
                        }
                    }
                }
                target.closeEntry();
                copiedEntries++;
            }
        }
        LOGGER.log(Level.FINE, "Copied {0} entries to new JAR", copiedEntries);

        // Finish writing
        target.finish();
        LOGGER.log(Level.FINE, "Finished writing to temporary JAR file");
    }

    // Replace original JAR with the updated one
    LOGGER.log(Level.INFO, "Replacing original JAR with updated version");
    Files.move(tempFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    LOGGER.log(Level.INFO, "Successfully updated manifest in JAR file: {0}", jarFile.getAbsolutePath());
    remarks.append("Successfully updated manifest in JAR file: ")
            .append(jarFile.getAbsolutePath());

    return true;
}

    /**
     * Find a class in a JAR file
     */
    private static boolean findClassInJar(File jarFile, Operation operation, StringBuilder remarks) {
        String className = operation.getParameter("class_name");
        boolean exactMatch = Boolean.parseBoolean(operation.getParameter("exact_match"));

        LOGGER.log(Level.INFO, "Searching for class in JAR file: {0}, class name: {1}, exact match: {2}",
                new Object[]{jarFile.getAbsolutePath(), className, exactMatch});

        if (className == null || className.isEmpty()) {
            LOGGER.warning("Class name not specified for JAR search operation");
            remarks.append("Class name not specified");
            return false;
        }

        // First determine if the input is a simple class name or a path
        // Check if this is a simple class name (possibly with .class extension) versus a path or fully qualified name
        boolean endsWithDotClass = className.toLowerCase().endsWith(".class");
        boolean containsPath = className.contains("/") || className.contains("\\");
        boolean containsPackageSeparator = className.contains(".") && !(endsWithDotClass && className.indexOf('.') == className.lastIndexOf('.'));
        boolean isSimpleClassName = !containsPath && !containsPackageSeparator;

        LOGGER.log(Level.FINE, "Input appears to be a {0}", isSimpleClassName ? "simple class name" : "class path");        LOGGER.log(Level.FINE, "Input appears to be a {0}", isSimpleClassName ? "simple class name" : "class path");

        // Normalize class name by replacing backslashes with forward slashes
        String normalizedClassName = className.replace('\\', '/');
        String searchName;

        // Format class name for matching based on input type
        if (isSimpleClassName) {
            // For simple class names, we'll just search for the filename part
            searchName = normalizedClassName;
            if (!searchName.endsWith(".class")) {
                searchName += ".class";
            }
            LOGGER.log(Level.FINE, "Simple class name search format: {0}", searchName);
        } else {
            // For path-based searches
            searchName = normalizedClassName;

            // Convert dot notation to path format if needed
            if (searchName.contains(".") && !searchName.contains("/")) {
                searchName = searchName.replace('.', '/');
            }

            // Add .class suffix if missing
            if (!searchName.endsWith(".class")) {
                searchName += ".class";
            }
            LOGGER.log(Level.FINE, "Path-based search format: {0}", searchName);
        }

        List<String> foundClasses = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            LOGGER.log(Level.FINE, "Opened JAR file, searching for entries matching: {0}", searchName);
            Enumeration<JarEntry> entries = jar.entries();
            int totalEntries = 0;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                totalEntries++;

                boolean matches;
                if (isSimpleClassName) {
                    // For simple class name, match only the filename part
                    String entryFileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    if (exactMatch) {
                        matches = entryFileName.equals(searchName);
                    } else {
                        matches = entryFileName.contains(searchName);
                    }
                } else {
                    // For path-based searches, match the whole path
                    if (exactMatch) {
                        matches = entryName.equals(searchName);
                    } else {
                        matches = entryName.contains(searchName);
                    }
                }

                if (matches && entryName.endsWith(".class")) {
                    foundClasses.add(entryName);
                    LOGGER.log(Level.FINE, "Found matching class: {0}", entryName);
                }
            }

            LOGGER.log(Level.FINE, "Searched through {0} entries in JAR file", totalEntries);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading JAR file: " + jarFile.getName(), e);
            remarks.append("Error reading JAR file: ").append(e.getMessage());
            return false;
        }

        // Set results in operation parameters
        operation.setParameter("found_count", String.valueOf(foundClasses.size()));
        operation.setParameter("found_classes", String.join("\n", foundClasses));

        LOGGER.log(Level.INFO, "Found {0} classes matching '{1}' in JAR file: {2}",
                new Object[]{foundClasses.size(), className, jarFile.getName()});

        // Build remarks
        if (foundClasses.isEmpty()) {
            String msg = "No classes found matching '" + className + "'";
            LOGGER.info(msg);
            remarks.append(msg);
        } else {
            remarks.append("Found ").append(foundClasses.size()).append(" classes matching '")
                    .append(className).append("':\n");
            for (String foundClass : foundClasses) {
                remarks.append("- ").append(foundClass).append("\n");
            }
        }

        return !foundClasses.isEmpty();
    }

    /**
     * Check if a JAR file is signed
     */
    /**
     * Check if a JAR file is signed
     */
    private static boolean signJarFile(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
        LOGGER.log(Level.INFO, "Checking if JAR file is signed: {0}", jarFile.getAbsolutePath());

        if (!jarFile.exists()) {
            String errorMsg = "JAR file not found: " + jarFile.getAbsolutePath();
            LOGGER.warning(errorMsg);
            remarks.append(errorMsg);
            return false;
        }

        // Use external jarsigner tool to verify JAR signature
        try {
            // Build path to jarsigner.exe
            String jarsignerPath = getToolServerHome()+JAR_SIGN_CHECKER_EXE;

            LOGGER.log(Level.INFO, "Using jarsigner tool: {0}", jarsignerPath);

            // Build the command: jarsigner -verify jarFile
            ProcessBuilder pb = new ProcessBuilder(
                    jarsignerPath,
                    "-verify",
                    jarFile.getAbsolutePath()
            );

            LOGGER.log(Level.FINE, "Executing command: {0}", pb.command());

            // Execute the process and capture output
            Process process = pb.start();

            // Read the output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.log(Level.FINE, "jarsigner output: {0}", line);
                }

                // Read error output as well
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    while ((line = errorReader.readLine()) != null) {
                        output.append(line).append("\n");
                        LOGGER.log(Level.FINE, "jarsigner error: {0}", line);
                    }
                }

                // Wait for process to complete
                int exitCode = process.waitFor();
                LOGGER.log(Level.FINE, "jarsigner exit code: {0}", exitCode);

                // Determine if JAR is signed based on output and exit code
                String outputStr = output.toString().toLowerCase();
                boolean isSigned = exitCode == 0 &&
                        !outputStr.contains("unsigned") &&
                        !outputStr.contains("no manifest");

                // Store results in operation parameters
                operation.setParameter("is_signed", String.valueOf(isSigned));
                operation.setParameter("verification_output", output.toString().trim());

                // Build detailed remarks
                remarks.append("JAR Signature Status: ").append(jarFile.getName()).append("\n");
                remarks.append("Signed: ").append(isSigned ? "Yes" : "No").append("\n");
                remarks.append("Verification Output:\n").append(output);

                LOGGER.log(Level.INFO, "JAR file {0} is {1}signed",
                        new Object[]{jarFile.getName(), isSigned ? "" : "not "});

                return isSigned;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "Verification process interrupted: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            remarks.append("Error: ").append(errorMsg);
            return false;
        } catch (Exception e) {
            String errorMsg = "Error verifying JAR signature: " + e.getMessage();
            LOGGER.log(Level.SEVERE, errorMsg, e);
            remarks.append("Error: ").append(errorMsg);
            return false;
        }
    }

    /**
     * Create a JarSigner instance for signing JAR files
     * Note: This is a simplified implementation as JarSigner is not publicly available in all JDKs
     */
    private static JarSigner createJarSigner(PrivateKey privateKey, java.security.cert.Certificate[] certChain,
                                             String signatureAlgorithm) {
        return new JarSigner(privateKey, certChain, signatureAlgorithm);
    }

    /**
     * Verify JAR file signature
     */
    private static boolean verifyJarSignature(File jarFile, Operation operation, StringBuilder remarks) throws IOException, GeneralSecurityException {
        boolean verify = true;
        boolean verbose = Boolean.parseBoolean(operation.getParameter("verbose"));

        try (JarFile jar = new JarFile(jarFile, verify)) {
            // Get all entries
            Enumeration<JarEntry> entries = jar.entries();

            // Keep track of signature information
            Map<String, List<String>> signerInfo = new HashMap<>();
            boolean hasSignatures = false;
            int entryCount = 0;
            int signedCount = 0;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                entryCount++;

                if (entry.getName().startsWith("META-INF/") && entry.getName().endsWith(".SF")) {
                    hasSignatures = true;
                    String signerName = entry.getName().substring(9, entry.getName().length() - 3);
                    List<String> info = signerInfo.computeIfAbsent(signerName, k -> new ArrayList<>());
                }

                // Skip directories and manifest
                if (entry.isDirectory() || entry.getName().equals(JarFile.MANIFEST_NAME)) {
                    continue;
                }

                // This will verify the entry
                try (InputStream is = jar.getInputStream(entry)) {
                    // Read the certificate for this entry
                    java.security.cert.Certificate[] certs = entry.getCertificates();

                    // Count signed entries
                    if (certs != null && certs.length > 0) {
                        signedCount++;

                        if (verbose) {
                            StringBuilder certInfo = new StringBuilder();
                            for (java.security.cert.Certificate cert : certs) {
                                if (cert instanceof java.security.cert.X509Certificate) {
                                    java.security.cert.X509Certificate x509 = (java.security.cert.X509Certificate) cert;
                                    certInfo.append("\n      Subject: ").append(x509.getSubjectX500Principal().getName());
                                    certInfo.append("\n      Issuer: ").append(x509.getIssuerX500Principal().getName());
                                }
                            }

                            // Find signer name
                            for (Map.Entry<String, List<String>> signer : signerInfo.entrySet()) {
                                signer.getValue().add(entry.getName() + certInfo.toString());
                            }
                        }
                    } else if (verbose) {
                        remarks.append("Unsigned: ").append(entry.getName()).append("\n");
                    }

                    // Read the entire stream to force verification
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (is.read(buffer) != -1) { /* consume stream */ }
                }
            }

            // Set results in operation parameters
            operation.setParameter("is_signed", String.valueOf(hasSignatures));
            operation.setParameter("entry_count", String.valueOf(entryCount));
            operation.setParameter("signed_count", String.valueOf(signedCount));
            operation.setParameter("signers", String.join(", ", signerInfo.keySet()));

            // Build remarks
            if (hasSignatures) {
                remarks.append("JAR file is signed\n");
                remarks.append("Total entries: ").append(entryCount).append("\n");
                remarks.append("Signed entries: ").append(signedCount).append("\n");
                remarks.append("Signers: ").append(String.join(", ", signerInfo.keySet())).append("\n");

                if (verbose) {
                    remarks.append("\nDetailed signature information:\n");
                    for (Map.Entry<String, List<String>> signer : signerInfo.entrySet()) {
                        remarks.append("Signer: ").append(signer.getKey()).append("\n");
                        remarks.append("  Signed entries:\n");
                        for (String entry : signer.getValue()) {
                            remarks.append("    ").append(entry).append("\n");
                        }
                    }
                }

                return true;
            } else {
                remarks.append("JAR file is not signed");
                return false;
            }
        } catch (SecurityException e) {
            remarks.append("JAR signature verification failed: ").append(e.getMessage());
            return false;
        }
    }

/**
 * Extract a specific file from a JAR
 */
private static boolean extractFileFromJar(File jarFile, Operation operation, StringBuilder remarks) {
    String filePath = operation.getParameter("package_path");
    String outputPath = operation.getParameter("output_path");

    LOGGER.log(Level.INFO, "Extracting file from JAR: {0}, file path: {1}", new Object[]{jarFile.getAbsolutePath(), filePath});

    if (filePath == null || filePath.isEmpty()) {
        String errorMsg = "File path to extract not specified";
        LOGGER.warning(errorMsg);
        remarks.append(errorMsg);
        return false;
    }

// Set default output path if not provided
    if (outputPath == null || outputPath.isEmpty()) {
        outputPath = new File(jarFile.getParent(), new File(filePath).getName()).getPath();
        LOGGER.log(Level.INFO, "Using default output path: {0}", outputPath);
    } else {
        // Check if outputPath is a directory (ends with separator or exists as directory)
        File outPathFile = new File(outputPath);
        if (outputPath.endsWith(File.separator) || (outPathFile.exists() && outPathFile.isDirectory())) {
            // If it's a directory, append the original filename
            outputPath = new File(outputPath, new File(filePath).getName()).getPath();
            LOGGER.log(Level.INFO, "Output path is a directory, using: {0}", outputPath);
        }
    }

    // Open the JAR file
    try (JarFile jar = new JarFile(jarFile)) {
        // Find the entry
        LOGGER.log(Level.FINE, "Looking for entry: {0}", filePath);
        JarEntry entry = jar.getJarEntry(filePath);
        if (entry == null) {
            String errorMsg = "File not found in JAR: " + filePath;
            LOGGER.warning(errorMsg);
            remarks.append(errorMsg);
            return false;
        }
        LOGGER.log(Level.FINE, "Found entry: {0}, size: {1} bytes", new Object[]{filePath, entry.getSize()});

        // Create parent directories if needed
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            LOGGER.log(Level.FINE, "Creating parent directories for: {0}", outputPath);
            outputFile.getParentFile().mkdirs();
        }

        // Extract the file
        LOGGER.log(Level.INFO, "Extracting file to: {0}", outputFile.getAbsolutePath());
        try (InputStream input = jar.getInputStream(entry);
             FileOutputStream output = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            long bytesExtracted = 0;
            while ((len = input.read(buffer)) > 0) {
                output.write(buffer, 0, len);
                bytesExtracted += len;
            }
            LOGGER.log(Level.FINE, "Extracted {0} bytes", bytesExtracted);
        }

        String successMsg = "Successfully extracted file: " + filePath + "\nSaved to: " + outputFile.getAbsolutePath();
        LOGGER.info(successMsg);
        remarks.append(successMsg);

        operation.setRemarks(remarks.toString());
        return true;
    } catch (ZipException e) {
        String errorMsg = "Error accessing JAR file: " + e.getMessage();
        LOGGER.log(Level.SEVERE, errorMsg, e);
        remarks.append(errorMsg);
        return false;
    } catch (IOException e) {
        String errorMsg = "IO error while extracting file: " + e.getMessage();
        LOGGER.log(Level.SEVERE, errorMsg, e);
        remarks.append(errorMsg);
        return false;
    }
}

    /**
     * Get manifest from JAR file
     */
/**
 * Get manifest from JAR file
 */
private static boolean getJarManifest(File jarFile, Operation operation, StringBuilder remarks) throws IOException {
    LOGGER.log(Level.INFO, "Getting manifest from JAR file: {0}", jarFile.getAbsolutePath());

    try (JarFile jar = new JarFile(jarFile)) {
        Manifest manifest = jar.getManifest();

        if (manifest == null) {
            LOGGER.warning("No manifest found in JAR file: " + jarFile.getName());
            remarks.append("No manifest found in JAR file");
            operation.setParameter("has_manifest", "false");
            return false;
        }
        LOGGER.log(Level.FINE, "Successfully retrieved manifest from JAR");

        // Get main attributes
        Attributes mainAttrs = manifest.getMainAttributes();
        StringBuilder mainAttrStr = new StringBuilder();
        StringBuilder allAttrStr = new StringBuilder();
        LOGGER.log(Level.FINE, "Processing {0} main attributes", mainAttrs.size());

        // Main attributes
        mainAttrStr.append("Main Attributes:\n");
        for (Map.Entry<Object, Object> entry : mainAttrs.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            mainAttrStr.append("  ").append(key).append(": ").append(value).append("\n");
            allAttrStr.append(key).append(": ").append(value).append("\n");
            LOGGER.log(Level.FINEST, "Main attribute: {0}={1}", new Object[]{key, value});
        }

        // Per-entry attributes
        int entryCount = manifest.getEntries().size();
        LOGGER.log(Level.FINE, "Processing {0} per-entry attributes", entryCount);

        StringBuilder perEntryAttrStr = new StringBuilder();
        perEntryAttrStr.append("\nPer-Entry Attributes:\n");
        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            perEntryAttrStr.append("Entry: ").append(entry.getKey()).append("\n");
            LOGGER.log(Level.FINE, "Processing entry: {0}", entry.getKey());

            for (Map.Entry<Object, Object> attr : entry.getValue().entrySet()) {
                String key = attr.getKey().toString();
                String value = attr.getValue().toString();
                perEntryAttrStr.append("  ").append(key).append(": ").append(value).append("\n");
                allAttrStr.append(entry.getKey()).append("/").append(key)
                        .append(": ").append(value).append("\n");
                LOGGER.log(Level.FINEST, "Entry attribute: {0}/{1}={2}",
                        new Object[]{entry.getKey(), key, value});
            }
        }

        // Extract specific attributes of interest
        String mainClass = mainAttrs.getValue(Attributes.Name.MAIN_CLASS);
        String implTitle = mainAttrs.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
        String implVersion = mainAttrs.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        String implVendor = mainAttrs.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
        String createdBy = mainAttrs.getValue("Created-By");

        LOGGER.log(Level.FINE, "Key attributes - Main Class: {0}, Title: {1}, Version: {2}",
                new Object[]{mainClass, implTitle, implVersion});

        // Set results in operation parameters
        operation.setParameter("has_manifest", "true");
        operation.setParameter("manifest_entries", Integer.toString(manifest.getEntries().size()));
        operation.setParameter("manifest_content", allAttrStr.toString());

        if (mainClass != null) operation.setParameter("main_class", mainClass);
        if (implTitle != null) operation.setParameter("implementation_title", implTitle);
        if (implVersion != null) operation.setParameter("implementation_version", implVersion);
        if (implVendor != null) operation.setParameter("implementation_vendor", implVendor);
        if (createdBy != null) operation.setParameter("created_by", createdBy);

        // Build remarks
        remarks.append("JAR Manifest for: ").append(jarFile.getName()).append("\n\n");
        remarks.append(mainAttrStr);

        if (manifest.getEntries().size() > 0) {
            remarks.append(perEntryAttrStr);
        }

        LOGGER.log(Level.INFO, "Successfully processed manifest for JAR: {0}, {1} main attributes, {2} entries",
                new Object[]{jarFile.getName(), mainAttrs.size(), entryCount});
        return true;
    } catch (ZipException e) {
        LOGGER.log(Level.SEVERE, "Error accessing JAR file: " + jarFile.getName(), e);
        remarks.append("Error accessing JAR file: ").append(e.getMessage());
        return false;
    }
}

    /**
     * Simple JarSigner implementation
     */
    private static class JarSigner {
        private final PrivateKey privateKey;
        private final java.security.cert.Certificate[] certChain;
        private final String signatureAlgorithm;

        public JarSigner(PrivateKey privateKey, java.security.cert.Certificate[] certChain, String signatureAlgorithm) {
            this.privateKey = privateKey;
            this.certChain = certChain;
            this.signatureAlgorithm = signatureAlgorithm;
        }

        public void sign(File inputJar, OutputStream outputStream) throws IOException, GeneralSecurityException {
            // Create a temporary manifest that includes digests for each entry
            Manifest manifest = new Manifest();
            Attributes mainAttributes = manifest.getMainAttributes();
            mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");

            // Add implementation details
            mainAttributes.put(new Attributes.Name("Created-By"), "JarFileHandler");
            mainAttributes.put(new Attributes.Name("Signature-Version"), "1.0");

            // Read the input JAR and generate manifest entries
            Map<String, byte[]> digestMap = new HashMap<>();
            try (JarFile jarFile = new JarFile(inputJar)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    // Skip manifest and signature files
                    if (name.equals(JarFile.MANIFEST_NAME) ||
                            name.startsWith("META-INF/") &&
                                    (name.endsWith(".SF") || name.endsWith(".DSA") ||
                                            name.endsWith(".RSA") || name.endsWith(".EC"))) {
                        continue;
                    }

                    // Calculate digest for this entry
                    messageDigest.reset();
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            messageDigest.update(buffer, 0, len);
                        }
                    }

                    byte[] digest = messageDigest.digest();
                    digestMap.put(name, digest);

                    // Add to manifest
                    Attributes attributes = new Attributes();
                    attributes.putValue("SHA-256-Digest", Base64.getEncoder().encodeToString(digest));
                    manifest.getEntries().put(name, attributes);
                }
            }

            // Create a temporary JAR file to build our signed JAR
            File tempFile = File.createTempFile("signing", ".jar");
            tempFile.deleteOnExit();

            // Create the signed JAR with manifest and signature files
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempFile))) {
                // Add the manifest as the first entry
                JarEntry manifestEntry = new JarEntry(JarFile.MANIFEST_NAME);
                jos.putNextEntry(manifestEntry);
                manifest.write(jos);
                jos.closeEntry();

                // Generate the signature file
                Manifest signatureManifest = new Manifest();
                Attributes signatureMainAttributes = signatureManifest.getMainAttributes();
                signatureMainAttributes.put(Attributes.Name.SIGNATURE_VERSION, new Attributes.Name("1.0"));

                // Calculate digest of the entire manifest
                MessageDigest manifestDigest = MessageDigest.getInstance("SHA-256");
                ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
                manifest.write(manifestBytes);
                byte[] manifestDigestBytes = manifestDigest.digest(manifestBytes.toByteArray());
                signatureMainAttributes.putValue("SHA-256-Digest-Manifest",
                        Base64.getEncoder().encodeToString(manifestDigestBytes));

                // Generate signature file name
                String signerName = "SIGNER";
                if (certChain.length > 0 && certChain[0] instanceof java.security.cert.X509Certificate) {
                    signerName = ((java.security.cert.X509Certificate)certChain[0])
                            .getSubjectX500Principal().getName()
                            .replaceAll("[^A-Za-z0-9]", "")
                            .substring(0, Math.min(8,
                                    ((java.security.cert.X509Certificate)certChain[0])
                                            .getSubjectX500Principal().getName()
                                            .replaceAll("[^A-Za-z0-9]", "").length()));
                }

                // Add signature file
                JarEntry signatureFileEntry = new JarEntry("META-INF/" + signerName + ".SF");
                jos.putNextEntry(signatureFileEntry);
                signatureManifest.write(jos);
                jos.closeEntry();

                // Create block file with signature
                ByteArrayOutputStream signatureFileBytes = new ByteArrayOutputStream();
                signatureManifest.write(signatureFileBytes);
                byte[] signatureBytes = signatureFileBytes.toByteArray();

                // Sign the signature file
                Signature signature = Signature.getInstance(signatureAlgorithm);
                signature.initSign(privateKey);
                signature.update(signatureBytes);
                byte[] signedBytes = signature.sign();

                // Determine block file extension based on algorithm
                String blockExt = signatureAlgorithm.toLowerCase().contains("rsa") ? "RSA" :
                        (signatureAlgorithm.toLowerCase().contains("dsa") ? "DSA" : "EC");

                // Add block file
                JarEntry blockFileEntry = new JarEntry("META-INF/" + signerName + "." + blockExt);
                jos.putNextEntry(blockFileEntry);

                // Write signature block
                writeSignatureBlock(jos, signedBytes, certChain);
                jos.closeEntry();

                // Copy all entries from the original JAR
                try (JarFile jarFile = new JarFile(inputJar)) {
                    Enumeration<JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();

                        // Skip manifest and signature files
                        if (name.equals(JarFile.MANIFEST_NAME) ||
                                name.startsWith("META-INF/") &&
                                        (name.endsWith(".SF") || name.endsWith(".DSA") ||
                                                name.endsWith(".RSA") || name.endsWith(".EC"))) {
                            continue;
                        }

                        // Add the entry to our output JAR
                        JarEntry newEntry = new JarEntry(name);
                        jos.putNextEntry(newEntry);

                        // Copy the file content
                        if (!entry.isDirectory()) {
                            try (InputStream is = jarFile.getInputStream(entry)) {
                                byte[] buffer = new byte[BUFFER_SIZE];
                                int len;
                                while ((len = is.read(buffer)) != -1) {
                                    jos.write(buffer, 0, len);
                                }
                            }
                        }
                        jos.closeEntry();
                    }
                }
            }

            // Copy the signed JAR to the output stream
            Files.copy(tempFile.toPath(), outputStream);

            // Delete the temporary file
            tempFile.delete();
        }

        private void writeSignatureBlock(OutputStream os, byte[] signature,
                                         java.security.cert.Certificate[] certChain) throws IOException {
            // This is a simplified implementation that writes signature and certificates
            // In a real implementation, you would use proper ASN.1 encoding for PKCS #7 blocks

            // Write signature length and signature bytes
            os.write(signature.length >>> 24);
            os.write(signature.length >>> 16);
            os.write(signature.length >>> 8);
            os.write(signature.length);
            os.write(signature);

            // Write certificate count
            os.write(certChain.length >>> 24);
            os.write(certChain.length >>> 16);
            os.write(certChain.length >>> 8);
            os.write(certChain.length);

            // Write each certificate
            for (java.security.cert.Certificate cert : certChain) {
                try {
                    byte[] certBytes = cert.getEncoded();
                    os.write(certBytes.length >>> 24);
                    os.write(certBytes.length >>> 16);
                    os.write(certBytes.length >>> 8);
                    os.write(certBytes.length);
                    os.write(certBytes);
                } catch (java.security.cert.CertificateEncodingException e) {
                    throw new IOException("Error encoding certificate", e);
                }
            }
        }
    }
}