package com.me.testcases.fileOperation;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.GOATCommonConstants;
import com.me.util.LogManager;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.GOATCommonConstants.SEVENZEXE;

/**
 * Handler for 7z archive file operations
 */
public class ArchiveHandler {
    private static final Logger LOGGER = LogManager.getLogger(ArchiveHandler.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute an archive operation
     *
     * @param op The operation containing archive parameters
     * @return true if operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        op = ResolveOperationParameters.resolveOperationParameters(op);

        String action = op.getParameter("action");
        String archivePath = op.getParameter("archive_path");

        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for archive operation");
            op.setRemarks("Action is required for archive operation");
            return false;
        }

        if (archivePath == null || archivePath.isEmpty()) {
            LOGGER.warning("Archive path is required for archive operation");
            op.setRemarks("Archive path is required for archive operation");
            return false;
        }
        File archiveFile = new File(archivePath);

        if (!archiveFile.exists()){
            LOGGER.info("Archive file does not exist at path: " + archivePath);
            op.setRemarks("Archive file does not exist at path: " + archivePath);
        }

        // Resolve the file path (including server_home and other placeholders)

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("Archive Operation: ").append(action).append("\n");
        remarkBuilder.append("Target archive: ").append(archivePath).append("\n");

        try {
            boolean result;

            switch (action.toLowerCase()) {
                case "extract":
                    result = handleExtractOperation(op, archivePath, remarkBuilder);
                    break;
                case "create":
                    result = handleCreateOperation(op, archivePath, remarkBuilder);
                    break;
                case "list":
                    result = handleListOperation(op, archivePath, remarkBuilder);
                    break;
                case "add":
                    result = handleAddOperation(op, archivePath, remarkBuilder);
                    break;
                case "delete":
                    result = handleDeleteOperation(op, archivePath, remarkBuilder);
                    break;
                case "update":
                    result = handleUpdateOperation(op, archivePath, remarkBuilder);
                    break;
                case "zip_contains":
                    result = handleZipContains(op, archivePath, remarkBuilder, true);
                    break;
                case "zip_not_contains":
                    result = handleZipContains(op, archivePath, remarkBuilder, false);
                    break;
                default:
                    LOGGER.warning("Unsupported action: " + action);
                    remarkBuilder.append("Error: Unsupported action ").append(action);
                    op.setRemarks(remarkBuilder.toString());
                    return false;
            }

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(remarkBuilder.toString());
            LOGGER.info(remarkBuilder.toString());
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error performing archive operation: " + archivePath, e);
            remarkBuilder.append("\nError: ").append(e.getMessage());
            op.setRemarks(remarkBuilder.toString());
            return false;
        }
    }


    private static boolean handleZipContains(Operation op, String archivePath, StringBuilder remarks, boolean shouldContain) throws IOException {
        String filePath = op.getParameter("file_path");
        String fileName = op.getParameter("filename");

        File archiveFile = new File(archivePath);
        if (!archiveFile.exists() || !archiveFile.canRead()) {
            remarks.append("\nError: Archive does not exist or cannot be read: ").append(archivePath);
            return false;
        }

        boolean found = false;
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName().replace("\\", "/");

                // Case 1: Both file_path and filename provided
                if (filePath != null && !filePath.isEmpty() && fileName != null && !fileName.isEmpty()) {
                    String fullPath = filePath.replace("\\", "/");
                    if (!fullPath.endsWith("/")) fullPath += "/";
                    fullPath += fileName;
                    if (entryName.equals(fullPath)) {
                        found = true;
                        break;
                    }
                }
                // Case 2: Only filename provided
                else if (fileName != null && !fileName.isEmpty()) {
                    if (new File(entryName).getName().equals(fileName)) {
                        found = true;
                        break;
                    }
                }
                // Case 3: Only file_path provided (check for folder)
                else if (filePath != null && !filePath.isEmpty()) {
                    String folderPath = filePath.replace("\\", "/");
                    if (!folderPath.endsWith("/")) folderPath += "/";
                    if (entryName.startsWith(folderPath)) {
                        found = true;
                        break;
                    }
                }
            }
        }
        if (shouldContain) {
            remarks.append(found ? "\nFile/Folder found in zip archive." : "\nFile/Folder NOT found in zip archive.");
            return found;
        } else {
            remarks.append(!found ? "\nFile/Folder NOT found in zip archive." : "\nFile/Folder found in zip archive.");
            return !found;
        }
    }

    /**
     * Handle extract operation (extract files from archive)
     */
    private static boolean handleExtractOperation(Operation op, String archivePath, StringBuilder remarks) {
        File archiveFile = new File(archivePath);
        if (!archiveFile.canRead()) {
            LOGGER.warning("Cannot read archive file (permission denied): " + archivePath);
            remarks.append("\nError: Cannot read archive file (permission denied): ").append(archivePath);
            return false;
        }

        String password = op.getParameter("password");
        String targetDir = op.getParameter("target_dir");
        String filesToExtractParam = op.getParameter("files_to_extract");
        String specificFile = op.getParameter("file_to_extract");

        Set<String> filesToExtract = new HashSet<>();
        if (filesToExtractParam != null && !filesToExtractParam.isEmpty()) {
            for (String f : filesToExtractParam.split(",")) {
                f = f.replace("\\", "/").trim();
                filesToExtract.add(f.endsWith("/") ? f.substring(0, f.length() - 1) : f);
            }
        } else if (specificFile != null && !specificFile.isEmpty()) {
            String f = specificFile.replace("\\", "/").trim();
            filesToExtract.add(f.endsWith("/") ? f.substring(0, f.length() - 1) : f);
        }

        if (targetDir == null || targetDir.isEmpty()) {
            targetDir = new File(archivePath).getParent();
        }
        LOGGER.info("Target directory for extraction: " + targetDir);
        LOGGER.info("source archive path: " + archivePath);

        File targetDirFile = new File(targetDir);
        if (!targetDirFile.exists() && !targetDirFile.mkdirs()) {
            remarks.append("\nError: Failed to create target directory: ").append(targetDir);
            return false;
        }
        if (!targetDirFile.canWrite()) {
            LOGGER.warning("No write permission to target directory: " + targetDir);
            return false;
        }

        remarks.append("\nExtracting to: ").append(targetDir);
//        if (!filesToExtract.isEmpty()) {
//            remarks.append("\nExtracting specific folders/files: ").append(filesToExtract);
//        }

        // Use 7za.exe for all extractions
        String sevenZExe = getToolServerHome() + GOATCommonConstants.SEVENZEXE;
        LOGGER.info("Using 7z.exe for extraction: " + sevenZExe);

        List<String> cmd = new ArrayList<>();
        cmd.add(sevenZExe);
        cmd.add("x");
        cmd.add(archivePath);
        cmd.add("-o" + targetDir);
        cmd.add("-y");

        // Add password if provided
        if (password != null && !password.isEmpty()) {
            cmd.add("-p" + password);
        }

        // Add specific files/folders to extract
        for (String file : filesToExtract) {
            file = file.trim();
            if (!file.isEmpty()) {
                cmd.add(file);
            }
        }

        LOGGER.info("Executing command: " + cmd);
        LOGGER.info("It took some time based on the size of the archive and number of files to extract. So please wait...");

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(targetDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean noFilesExtracted = false;
            boolean foundFilesLine = false;
            int filesExtractedCount = -1;
            int filesExtracted = 0;

            while ((line = reader.readLine()) != null) {
                LOGGER.info("Extracting: " + line);
//                remarks.append("\n").append(line);
                if (line.contains("No files to process")) {
                    noFilesExtracted = true;
                }
                if (line.startsWith("Files:")) {
                    foundFilesLine = true;
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        try {
                            filesExtractedCount = Integer.parseInt(parts[1].trim());
                            filesExtracted = filesExtractedCount;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && !noFilesExtracted && (filesExtracted > 0 || !foundFilesLine)) {
                remarks.append("\nExtraction completed successfully using 7z.exe.");

                // Count extracted files if not already counted
                if (filesExtracted == 0) {
                    filesExtracted = countFilesInDirectory(targetDirFile);
                }

                remarks.append("\n\nExtraction summary:");
                remarks.append("\n  - Files extracted: ").append(filesExtracted);

                return true;
            } else {
                remarks.append("\nError: 7z.exe extraction failed or no files extracted. Exit code: ").append(exitCode);
                return false;
            }
        } catch (Exception e) {
            remarks.append("\nError executing 7z.exe: ").append(e.getMessage());
            return false;
        }
    }

    // Helper method to count files in directory recursively
    private static int countFilesInDirectory(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    count++;
                } else if (file.isDirectory()) {
                    count += countFilesInDirectory(file);
                }
            }
        }
        return count;
    }

    // Helper method to decompress all .gz files in a directory recursively
//    private static void decompressGzFiles(File dir, StringBuilder remarks) {
//        File[] files = dir.listFiles();
//        if (files == null) return;
//        for (File file : files) {
//            if (file.isDirectory()) {
//                decompressGzFiles(file, remarks);
//            } else if (file.getName().endsWith(".gz")) {
//                File outFile = new File(file.getParent(), file.getName().substring(0, file.getName().length() - 3));
//                try (
//                        FileInputStream fis = new FileInputStream(file);
//                        java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(fis);
//                        FileOutputStream fos = new FileOutputStream(outFile)
//                ) {
//                    byte[] buffer = new byte[8192];
//                    int len;
//                    while ((len = gis.read(buffer)) > 0) {
//                        fos.write(buffer, 0, len);
//                    }
//                    remarks.append("\nDecompressed: ").append(file.getName()).append(" -> ").append(outFile.getName());
//                    file.delete();
//                } catch (IOException e) {
//                    remarks.append("\nError decompressing: ").append(file.getName()).append(" - ").append(e.getMessage());
//                }
//            }
//        }
//    }

    /**
     * Handle create operation (create new archive)
     */
    private static boolean handleCreateOperation(Operation op, String archivePath, StringBuilder remarks) {
        String sourcePath = op.getParameter("source_path");
        String sourceFiles = op.getParameter("source_files"); // Optional, comma-separated file list
        String excludeFiles = op.getParameter("exclude_files"); // Optional, comma-separated

        if (sourcePath == null || sourcePath.isEmpty()) {
            remarks.append("\nError: source_path parameter is required for create action");
            return false;
        }

        File sourceDir = new File(sourcePath);

        if (!sourceDir.exists()) {
            remarks.append("\nError: Source path does not exist: ").append(sourcePath);
            return false;
        }

        File archiveFile = new File(archivePath);
        if (archiveFile.exists()) {
            remarks.append("\nWarning: Overwriting existing archive");
            if (!archiveFile.delete()) {
                remarks.append("\nError: Failed to delete existing archive");
                return false;
            }
        }

        // Ensure parent directory exists
        File parentDir = archiveFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            remarks.append("\nError: Failed to create directory for archive: ").append(parentDir.getPath());
            return false;
        }

        List<String> cmd = new ArrayList<>();
        String sevenZExe = getToolServerHome() + GOATCommonConstants.SEVENZEXE;
        cmd.add(sevenZExe);
        cmd.add("a");
        cmd.add(archivePath);

        if (sourceFiles != null && !sourceFiles.isEmpty()) {
            for (String fileName : sourceFiles.split(",")) {
                fileName = fileName.trim();
                File file = new File(fileName);
                if (file.exists()) {
                    cmd.add(file.getAbsolutePath());
                } else {
                    remarks.append("\nWarning: Source file not found: ").append(file.getPath());
                }
            }
        } else if (sourceDir.isDirectory()) {
            cmd.add(sourceDir.getAbsolutePath() + "\\*");
        } else {
            cmd.add(sourceDir.getAbsolutePath());
        }

        // Handle exclude patterns
        if (excludeFiles != null && !excludeFiles.isEmpty()) {
            for (String exclude : excludeFiles.split(",")) {
                exclude = exclude.trim().replace("/", "\\");
                if (!exclude.isEmpty()) {
                    cmd.add("-x!" + exclude);
                }
            }
        }

        remarks.append("\nCommand: ").append(String.join(" ", cmd));
        LOGGER.info("Executing command: " + cmd);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("Compressing: " + line);
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                long archiveSize = new File(archivePath).length();

                remarks.append("\nStatus: success");
                remarks.append("\nArchive size: ").append(archiveSize);
                remarks.append("\nArchive size (formatted): ").append(formatSize(archiveSize));
                return true;
            } else {
                remarks.append("\nError: 7z.exe failed with exit code ").append(exitCode);
                return false;
            }
        } catch (Exception e) {
            remarks.append("\nError executing 7z.exe: ").append(e.getMessage());
            return false;
        }
    }

    /**
     * Handle list operation (list contents of archive)
     */
    private static boolean handleListOperation(Operation op, String archivePath, StringBuilder remarks) throws IOException {

        File archiveFile = new File(archivePath);
        if (!archiveFile.canRead()) {
            LOGGER.warning("Cannot read archive file (permission denied): " + archivePath);
            return false;
        }else {
            LOGGER.info("Archive file is readable: " + archivePath);
        }

//        File archiveFile = new File(archivePath);
        if (!archiveFile.exists()) {
            remarks.append("\nError: Archive does not exist: ").append(archivePath);
            return false;
        }

        try (SevenZFile sevenZFile = new SevenZFile(archiveFile)) {
            remarks.append("\nArchive contents:");

            int fileCount = 0;
            int dirCount = 0;
            long totalSize = 0;
            SevenZArchiveEntry entry;

            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    dirCount++;
                    if (fileCount + dirCount <= 20) {
                        remarks.append("\n  [DIR] ").append(entry.getName());
                    }
                } else {
                    fileCount++;
                    totalSize += entry.getSize();
                    if (fileCount + dirCount <= 20) {
                        remarks.append("\n  [FILE] ").append(entry.getName())
                                .append(" (").append(formatSize(entry.getSize())).append(")");
                    }
                }
            }

            if (fileCount + dirCount > 20) {
                remarks.append("\n... and ").append(fileCount + dirCount - 20).append(" more entries");
            }

            remarks.append("\n\nSummary:");
            remarks.append("\n  - Total files: ").append(fileCount);
            remarks.append("\n  - Total directories: ").append(dirCount);
            remarks.append("\n  - Total uncompressed size: ").append(formatSize(totalSize));
            remarks.append("\n  - Archive size: ").append(formatSize(archiveFile.length()));

            // Store file list in output
            op.setOutputValue("file_count"+ Integer.toString(fileCount));
            op.setOutputValue("dir_count"+ Integer.toString(dirCount));
            op.setOutputValue("total_size"+ Long.toString(totalSize));

            return true;
        }
    }

    /**
     * Handle add operation (add files to existing archive)
     */
    private static boolean handleAddOperation(Operation op, String archivePath, StringBuilder remarks) throws IOException {
        boolean isFullyAdded = true;
        File archiveFile = new File(archivePath);
        if (!archiveFile.canRead()) {
            LOGGER.warning("Cannot read archive file (permission denied): " + archivePath);
            return false;
        }

        String sourcePath = op.getParameter("source_path");
        String sourceFiles = op.getParameter("source_files"); // Optional, comma-separated file list

        if (sourcePath == null || sourcePath.isEmpty()) {
            remarks.append("\nError: source_path parameter is required for add action");
            throw new IllegalArgumentException("source_path parameter is required for add action");
        }

        File sourceDir = new File(sourcePath);

        if (!sourceDir.exists()) {
            remarks.append("\nError: Source path does not exist: ").append(sourcePath);
            return false;
        }

        if (!archiveFile.exists()) {
            remarks.append("\nError: Archive does not exist: ").append(archivePath);
            return false;
        }

        // First, extract existing archive contents to a temporary directory
        File tempDir = Files.createTempDirectory("7z_update_").toFile();
        try {
            // Extract current archive
            try (SevenZFile sevenZFile = new SevenZFile(archiveFile)) {
                SevenZArchiveEntry entry;
                while ((entry = sevenZFile.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        File dir = new File(tempDir, entry.getName());
                        if (!dir.exists() && !dir.mkdirs()) {
                            remarks.append("\nWarning: Failed to create temp directory: ").append(dir.getPath());
                        }
                        continue;
                    }

                    File outFile = new File(tempDir, entry.getName());
                    File parent = outFile.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        remarks.append("\nWarning: Failed to create temp directory: ").append(parent.getPath());
                        continue;
                    }

                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        byte[] content = new byte[(int) entry.getSize()];
                        sevenZFile.read(content, 0, content.length);
                        out.write(content);
                    }
                }
            }

            // Add new files to temp directory
            List<File> filesToAdd = new ArrayList<>();
            if (sourceFiles != null && !sourceFiles.isEmpty()) {
                // Add specific files
                for (String fileName : sourceFiles.split(",")) {
                    fileName = fileName.trim();
                    File file = new File(sourceDir, fileName);
                    if (file.exists()) {
                        filesToAdd.add(file);
                    } else {
                        remarks.append("\nWarning: Source file not found: ").append(file.getPath());
                        LOGGER.warning("Source file not found: " + file.getPath());
                        isFullyAdded = false; // At least one file was not found
                    }
                }
            } else if (sourceDir.isDirectory()) {
                // Add all files in directory
                collectFilesRecursively(sourceDir, filesToAdd);
            } else {
                // Single file
                filesToAdd.add(sourceDir);
            }

            int filesAdded = 0;
            Path sourceDirPath = sourceDir.toPath();

            for (File file : filesToAdd) {
                if (file.isDirectory()) {
                    continue;
                }

                // Calculate the relative path correctly
                Path filePath = file.toPath();
                String relativePath;

                // If the file is inside the source directory, calculate proper relative path
                if (filePath.startsWith(sourceDirPath)) {
                    relativePath = sourceDirPath.relativize(filePath).toString().replace('\\', '/');
                } else {
                    // For files outside source directory, just use the filename
                    relativePath = file.getName();
                }

                File targetFile = new File(tempDir, relativePath);
                File targetParent = targetFile.getParentFile();
                if (!targetParent.exists() && !targetParent.mkdirs()) {
                    remarks.append("\nWarning: Failed to create temp directory: ").append(targetParent.getPath());
                    continue;
                }

                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                filesAdded++;

                if (filesAdded <= 10) {
                    remarks.append("\nAdding: ").append(relativePath)
                            .append(" (").append(formatSize(file.length())).append(")");
                }
            }

            if (filesAdded > 10) {
                remarks.append("\n... and ").append(filesAdded - 10).append(" more files");
            }

            // Delete original archive
            if (!archiveFile.delete()) {
                remarks.append("\nError: Failed to delete original archive for update");
                return false;
            }

            // Create new archive with all files
            List<File> allFiles = new ArrayList<>();
            collectFilesRecursively(tempDir, allFiles);
            Path tempDirPath = tempDir.toPath();

            try (SevenZOutputFile outArchive = new SevenZOutputFile(archiveFile)) {
                // Add directories first to maintain structure
                Set<String> addedDirs = new HashSet<>();
                for (File file : allFiles) {
                    if (file.isDirectory()) {
                        String entryName = tempDirPath.relativize(file.toPath()).toString().replace('\\', '/');
                        if (!entryName.isEmpty() && !addedDirs.contains(entryName)) {
                            if (!entryName.endsWith("/")) entryName += "/";
                            SevenZArchiveEntry entry = outArchive.createArchiveEntry(file, entryName);
                            outArchive.putArchiveEntry(entry);
                            outArchive.closeArchiveEntry();
                            addedDirs.add(entryName);
                        }
                    }
                }

                // Then add files
                for (File file : allFiles) {
                    if (file.isDirectory()) continue;

                    String entryName = tempDirPath.relativize(file.toPath()).toString().replace('\\', '/');
                    SevenZArchiveEntry entry = outArchive.createArchiveEntry(file, entryName);
                    outArchive.putArchiveEntry(entry);

                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            outArchive.write(buffer, 0, bytesRead);
                        }
                    }

                    outArchive.closeArchiveEntry();
                }
            }

            remarks.append("\n\nAdd operation summary:");
            remarks.append("\n  - Files added: ").append(filesAdded);
            remarks.append("\n  - New archive size: ").append(formatSize(archiveFile.length()));

            return isFullyAdded;
        } finally {
            // Clean up temp directory
            deleteDirectory(tempDir);
        }
    }

    private static boolean handleDeleteOperation(Operation op, String archivePath, StringBuilder remarks) {
        File archiveFile = new File(archivePath);
        if (!archiveFile.canRead()) {
            LOGGER.warning("Cannot read archive file (permission denied): " + archivePath);
            return false;
        }

        String filesToDeleteParam = op.getParameter("file_to_delete");
        String password = op.getParameter("password");

        if (filesToDeleteParam == null || filesToDeleteParam.isEmpty()) {
            remarks.append("\nError: file_to_delete parameter is required for delete action");
            throw new IllegalArgumentException("file_to_delete parameter is required for delete action");
        }

        if (!archiveFile.exists()) {
            remarks.append("\nError: Archive does not exist: ").append(archivePath);
            return false;
        }

        // Parse files to delete
        List<String> filesToDelete = new ArrayList<>();
        for (String fileToDelete : filesToDeleteParam.split(",")) {
            fileToDelete = fileToDelete.trim();
            if (!fileToDelete.isEmpty()) {
                fileToDelete = fileToDelete.replace('/', '\\'); // 7z.exe uses backslashes on Windows
                filesToDelete.add(fileToDelete);
                LOGGER.info("Will delete: " + fileToDelete);
            }
        }

        if (filesToDelete.isEmpty()) {
            remarks.append("\nError: No valid files specified for deletion");
            return false;
        }

        // Use 7z.exe for direct deletion
        String sevenZExe = getToolServerHome() + SEVENZEXE;

        List<String> cmd = new ArrayList<>();
        cmd.add(sevenZExe);
        cmd.add("d"); // Delete command
        cmd.add(archiveFile.getAbsolutePath());

        // Add password if provided
        if (password != null && !password.isEmpty()) {
            cmd.add("-p" + password);
        }

        // Add files/folders to delete
        cmd.addAll(filesToDelete);

        cmd.add("-y"); // Assume yes for all prompts

        LOGGER.info("Executing command: " + cmd);
        remarks.append("\nDeleting files from archive using 7z.exe...");

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int deletedCount = 0;
            boolean foundDeleteLine = false;

            while ((line = reader.readLine()) != null) {
                LOGGER.info("7z: " + line);

                // Parse output to count deleted files
                if (line.startsWith("Deleting ")) {
                    deletedCount++;
                    if (deletedCount <= 10) {
                        remarks.append("\n").append(line);
                    }
                }

                if (line.contains("Everything is Ok")) {
                    foundDeleteLine = true;
                }
            }

            if (deletedCount > 10) {
                remarks.append("\n... and ").append(deletedCount - 10).append(" more files deleted");
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && (foundDeleteLine || deletedCount > 0)) {
                remarks.append("\n\nDelete operation summary:");
                remarks.append("\n  - Files/folders deleted: ").append(deletedCount);
                remarks.append("\n  - Archive size: ").append(formatSize(archiveFile.length()));
                remarks.append("\nDelete operation completed successfully");
                return true;
            } else if (exitCode == 0 && deletedCount == 0) {
                remarks.append("\nWarning: No matching files found to delete");
                return false;
            } else {
                remarks.append("\nError: 7z.exe delete failed. Exit code: ").append(exitCode);
                return false;
            }
        } catch (Exception e) {
            remarks.append("\nError executing 7z.exe: ").append(e.getMessage());
            LOGGER.log(Level.SEVERE, "Error deleting from archive", e);
            return false;
        }
    }



    /**
     * Handle update operation (update existing file or folder in archive)
     */
    private static boolean handleUpdateOperation(Operation op, String archivePath, StringBuilder remarks) throws IOException {
        File archiveFile = new File(archivePath);
        if (!archiveFile.canRead()) {
            LOGGER.warning("Cannot read archive file (permission denied): " + archivePath);
            return false;
        } else {
            LOGGER.info("Archive file is readable: " + archivePath);
        }

        String fileToUpdate = op.getParameter("file_to_update");
        String sourcePath = op.getParameter("source_path");

        if (fileToUpdate == null || fileToUpdate.isEmpty()) {
            remarks.append("\nError: file_to_update parameter is required for update action");
            throw new IllegalArgumentException("file_to_update parameter is required for update action");
        }

        if (sourcePath == null || sourcePath.isEmpty()) {
            remarks.append("\nError: source_path parameter is required for update action");
            throw new IllegalArgumentException("source_path parameter is required for update action");
        }

        File sourceFile = new File(sourcePath);

        if (!sourceFile.exists()) {
            remarks.append("\nError: Source path does not exist: ").append(sourcePath);
            return false;
        }

        if (!archiveFile.exists()) {
            remarks.append("\nError: Archive does not exist: ").append(archivePath);
            return false;
        }

        // Normalize paths to use forward slashes
        fileToUpdate = fileToUpdate.replace('\\', '/');
        // Ensure directory paths end with a slash
        boolean isFolder = sourceFile.isDirectory();
        if (isFolder && !fileToUpdate.endsWith("/")) {
            fileToUpdate += "/";
        }

        LOGGER.info("Updating " + (isFolder ? "folder: " : "file: ") + fileToUpdate);

        // Extract to temporary directory
        File tempDir = Files.createTempDirectory("7z_update_").toFile();
        try {
            boolean itemFound = false;
            int filesSkipped = 0;

            // Extract current archive, excluding the file/folder to update
            try (SevenZFile sevenZFile = new SevenZFile(archiveFile)) {
                SevenZArchiveEntry entry;
                while ((entry = sevenZFile.getNextEntry()) != null) {
                    String entryName = entry.getName().replace('\\', '/');

                    // Check if this entry should be skipped (it's the file or in the folder we're updating)
                    boolean shouldSkip = false;
                    if (isFolder) {
                        // Skip if entry is in the folder we're updating
                        if (entryName.equals(fileToUpdate) || entryName.startsWith(fileToUpdate)) {
                            itemFound = true;
                            shouldSkip = true;
                            filesSkipped++;

                            if (filesSkipped <= 5) {
                                remarks.append("\nSkipping existing entry: ").append(entryName);
                            }
                        }
                    } else {
                        // Skip if entry is the file we're updating
                        if (entryName.equals(fileToUpdate)) {
                            itemFound = true;
                            shouldSkip = true;
                            filesSkipped++;
                            remarks.append("\nSkipping existing file: ").append(entryName);
                        }
                    }

                    if (shouldSkip) {
                        continue;
                    }

                    // Extract this entry (not being updated)
                    if (entry.isDirectory()) {
                        File dir = new File(tempDir, entryName);
                        if (!dir.exists() && !dir.mkdirs()) {
                            remarks.append("\nWarning: Failed to create temp directory: ").append(dir.getPath());
                        }
                        continue;
                    }

                    File outFile = new File(tempDir, entryName);
                    File parent = outFile.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        remarks.append("\nWarning: Failed to create temp directory: ").append(parent.getPath());
                        continue;
                    }

                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        byte[] content = new byte[(int) entry.getSize()];
                        sevenZFile.read(content, 0, content.length);
                        out.write(content);
                    }
                }
            }

            if (filesSkipped > 5) {
                remarks.append("\n... and ").append(filesSkipped - 5).append(" more skipped entries");
            }

            if (!itemFound) {
                remarks.append("\nWarning: ").append(isFolder ? "Folder" : "File")
                        .append(" not found in archive: ").append(fileToUpdate);
                remarks.append("\nWill add as new ").append(isFolder ? "folder" : "file").append(" instead.");
            }

            // Copy the new file/folder to temp directory
            if (isFolder) {
                // For folders, copy entire directory structure
                int filesAdded = 0;
                List<File> sourceFiles = new ArrayList<>();
                collectFilesRecursively(sourceFile, sourceFiles);

                for (File file : sourceFiles) {
                    if (file.isDirectory()) continue;

                    // Calculate relative path from source directory
                    String relativePath = file.getAbsolutePath()
                            .substring(sourceFile.getAbsolutePath().length())
                            .replace('\\', '/');

                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }

                    // Full path in the archive
                    String archivePath_1 = fileToUpdate + relativePath;

                    // Copy file to temporary directory
                    File destFile = new File(tempDir, archivePath_1);
                    File destParent = destFile.getParentFile();
                    if (!destParent.exists() && !destParent.mkdirs()) {
                        remarks.append("\nWarning: Failed to create temp directory: ").append(destParent.getPath());
                        continue;
                    }

                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    filesAdded++;

                    if (filesAdded <= 5) {
                        remarks.append("\nAdding: ").append(archivePath_1).append(" (").append(formatSize(file.length())).append(")");
                    }
                }

                if (filesAdded > 5) {
                    remarks.append("\n... and ").append(filesAdded - 5).append(" more files added");
                }

                remarks.append("\nUpdated folder: ").append(fileToUpdate)
                        .append(" with contents from: ").append(sourceFile.getAbsolutePath());

            } else {
                // For single file, just copy it directly
                File updatedFile = new File(tempDir, fileToUpdate);
                File updatedParent = updatedFile.getParentFile();
                if (!updatedParent.exists() && !updatedParent.mkdirs()) {
                    remarks.append("\nError: Failed to create temp directory: ").append(updatedParent.getPath());
                    return false;
                }

                Files.copy(sourceFile.toPath(), updatedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                remarks.append("\nUpdated with new file: ").append(sourceFile.getAbsolutePath());
                remarks.append("\n  - New size: ").append(formatSize(sourceFile.length()));
            }

            // Delete original archive
            if (!archiveFile.delete()) {
                remarks.append("\nError: Failed to delete original archive for update");
                return false;
            }

            // Create new archive with updated files
            List<File> allFiles = new ArrayList<>();
            collectFilesRecursively(tempDir, allFiles);
            Path tempDirPath = tempDir.toPath().normalize();

            try (SevenZOutputFile outArchive = new SevenZOutputFile(archiveFile)) {
                // First add directories to maintain structure
                Set<String> addedDirs = new HashSet<>();
                for (File file : allFiles) {
                    if (file.isDirectory()) {
                        String entryName = tempDirPath.relativize(file.toPath()).toString().replace('\\', '/');
                        if (!entryName.isEmpty() && !addedDirs.contains(entryName)) {
                            if (!entryName.endsWith("/")) entryName += "/";
                            SevenZArchiveEntry entry = outArchive.createArchiveEntry(file, entryName);
                            outArchive.putArchiveEntry(entry);
                            outArchive.closeArchiveEntry();
                            addedDirs.add(entryName);
                        }
                    }
                }

                // Then add files
                int filesAdded = 0;
                long totalSize = 0;
                for (File file : allFiles) {
                    if (file.isDirectory()) continue;

                    String entryName = tempDirPath.relativize(file.toPath()).toString().replace('\\', '/');
                    SevenZArchiveEntry entry = outArchive.createArchiveEntry(file, entryName);
                    outArchive.putArchiveEntry(entry);

                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            outArchive.write(buffer, 0, bytesRead);
                            totalSize += bytesRead;
                        }
                    }

                    outArchive.closeArchiveEntry();
                    filesAdded++;
                }

                remarks.append("\n\nUpdate operation summary:");
                remarks.append("\n  - ").append(isFolder ? "Folder" : "File").append(" updated: ").append(fileToUpdate);
                if (isFolder) {
                    remarks.append("\n  - Files added/replaced: ").append(filesAdded);
                }
                remarks.append("\n  - New archive size: ").append(formatSize(archiveFile.length()));
                remarks.append("\n  - Total uncompressed size: ").append(formatSize(totalSize));
            }

            return true;
        } finally {
            // Clean up temp directory
            deleteDirectory(tempDir);
        }
    }

    /**
     * Recursively collect files from a directory
     */
    private static void collectFilesRecursively(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                fileList.add(file);
                if (file.isDirectory()) {
                    collectFilesRecursively(file, fileList);
                }
            }
        }
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private static boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            LOGGER.warning("Failed to delete file: " + file);
                        }
                    }
                }
            }
        }
        return directory.delete();
    }

    /**
     * Format file size in human-readable format
     */
    private static String formatSize(long size) {
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
     * Count the number of lines in a string
     */
    private static int countLines(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        return str.split("\r\n|\r|\n", -1).length;
    }

    /**
     * Resolve placeholders in path strings like server_home
     */
//    private static String resolvePath(String path) {
//        if (path == null || path.isEmpty()) {
//            return path;
//        }
//
//        // Handle the server_home placeholder
//        if (path.contains("server_home")) {
//            // Get the server home from system property
//            String serverHome = System.getProperty("server.home");
//
//            // If null, use a fallback approach
//            if (serverHome == null) {
//                serverHome = System.getProperty("user.dir");
//            }
//
//            LOGGER.info("Resolved server_home to: " + serverHome);
//            path = path.replace("server_home", serverHome);
//        }
//
//        return path;
//    }
}