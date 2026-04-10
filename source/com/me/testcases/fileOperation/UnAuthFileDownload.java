package com.me.testcases.fileOperation;

import java.io.*;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.Operation;
import com.me.util.LogManager;

import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.CommonUtill.resolveHome;

/**
 * Handler for file download operations
 */
public class UnAuthFileDownload {
    private static final Logger LOGGER = LogManager.getLogger(UnAuthFileDownload.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute a file download operation
     *
     * @param op The operation containing download parameters
     * @return true if download was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        LOGGER.info("Starting file download operation");

        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        String url = op.getParameter("url");
        String targetPath = op.getParameter("target_path");
        String fileName = op.getParameter("filename");
        boolean isOverwrite = op.getParameter("overwrite") == null ? true : Boolean.parseBoolean(op.getParameter("overwrite"));
        LOGGER.info("Download parameters - URL: " + url + ", Target path: " +
                (targetPath != null ? targetPath : "not specified") +
                ", Filename: " + (fileName != null ? fileName : "not specified"));

        if (url == null || url.isEmpty()) {
            LOGGER.warning("URL is required for file download operation");
            return false;
        }

        if (targetPath == null || targetPath.isEmpty()) {
            targetPath = getToolServerHome() +File.separator+"downloads"; // Default to temp directory
            LOGGER.info("No target path specified, using temp directory: " + targetPath);
        } else {
            LOGGER.fine("Resolving target path: " + targetPath);
            targetPath = resolveHome(targetPath);
            LOGGER.info("Resolved target path: " + targetPath);
        }

        StringBuilder remarkBuilder = new StringBuilder();

        try {
            // Create target directory if it doesn't exist
            File directory = new File(targetPath);
            if (!directory.exists()) {
                LOGGER.info("Target directory does not exist, creating: " + targetPath);
                if (!directory.mkdirs()) {
                    String errorMsg = "Failed to create target directory: " + targetPath;
                    LOGGER.warning(errorMsg);
                    remarkBuilder.append("Error: ").append(errorMsg);
                    op.setRemarks(remarkBuilder.toString());
                    return false;
                }
                LOGGER.info("Target directory created successfully");
            }

            // Extract filename from URL if not provided
            if (fileName == null || fileName.isEmpty()) {
                LOGGER.info("Extracting filename from URL");
                try {
                    URL urlObj = new URL(url);
                    String urlPath = urlObj.getPath();
                    fileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);

                    // Try to extract filename from query parameters if present
                    String query = urlObj.getQuery();
                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=", 2);
                            if (pair.length == 2 && !pair[1].isEmpty()) {
                                fileName = pair[1];
                                LOGGER.info("Extracted filename from query parameter key '" + pair[0] + "': " + fileName);
                                break;
                            }
                        }
                    }

                    if (fileName.isEmpty()) {
                        fileName = "downloaded_file_" + System.currentTimeMillis();
                        LOGGER.info("URL does not contain filename, generating one: " + fileName);
                    } else {
                        LOGGER.info("Extracted filename: " + fileName);
                    }
                } catch (Exception e) {
                    fileName = "downloaded_file_" + System.currentTimeMillis();
                    LOGGER.info("Failed to extract filename from URL, generating one: " + fileName + e);
                }
            } else {
                // If user provided filename without extension, try to get extension from URL
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex == -1) {
                    try {
                        String urlPath = new URL(url).toString();
                        String urlFileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);
                        int urlDotIndex = urlFileName.lastIndexOf('.');
                        if (urlDotIndex != -1) {
                            String extension = urlFileName.substring(urlDotIndex);
                            fileName = fileName + extension;
                            LOGGER.info("Appended extension from URL filename to provided filename: " + fileName);
                        }
                    } catch (Exception e) {
                        LOGGER.info("Could not extract extension from URL filename: " + e);
                    }
                }
            }

//            remarkBuilder.append("File name: ").append(fileName).append("\n");
            LOGGER.info("File name: " + fileName);
            File targetFile = new File(directory, fileName);
            LOGGER.info("Target file path: " + targetFile.getAbsolutePath());

            // Download the file
            LOGGER.info("Beginning download from: " + url);
            LOGGER.info("It takes some time based on the file size and network speed, please wait...");
            long startTime = System.currentTimeMillis();
            downloadUnAuthFile(url, targetFile, remarkBuilder,isOverwrite);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("Download completed in " + formatDuration(duration));

            // Add download statistics
            remarkBuilder.append("\nDownload completed successfully:\n");
            remarkBuilder.append("  - File saved to: ").append(targetFile.getAbsolutePath()).append("\n");
            remarkBuilder.append("  - File size: ").append(formatSize(targetFile.length())).append("\n");
            remarkBuilder.append("  - Download time: ").append(formatDuration(duration)).append("\n");

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(remarkBuilder.toString());
            LOGGER.info("File download operation successful: " + targetFile.getAbsolutePath());
//            LOGGER.fine(remarkBuilder.toString());
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error downloading file from: " + url, e);
            remarkBuilder.append("\nError downloading file: ").append(e.getMessage());
            op.setRemarks(remarkBuilder.toString());
            return false;
        }
    }

    /**
     * Download a file from URL to target file
     */
    private static void downloadUnAuthFile(String urlStr, File targetFile, StringBuilder remarks, boolean isOverwrite) throws IOException {
        URL url = new URL(urlStr);
        remarks.append("Connecting to ").append(url.getHost()).append("...\n");

        // Configure SSL for HTTPS connections
        configureSSLForHttps(urlStr);

        int maxRetries = 5;
        int retryDelayMs = 5000; // Start with 5 seconds
        int retryCount = 0;
        long downloadedBytes = 0;
        boolean downloadComplete = false;
        long expectedContentLength = -1;

        // If file exists, handle based on overwrite flag and resumption capability
        if (targetFile.exists()) {
            if (!isOverwrite) {
                remarks.append("File already exists at: ").append(targetFile.getAbsolutePath()).append(". Skipping download as overwrite is false.\n");
                LOGGER.info("File already exists and overwrite is set to false. Skipping download.");
                return;
            } else {
                // Keep the file for possible resume
                downloadedBytes = targetFile.length();
                remarks.append("Existing file found (").append(formatSize(downloadedBytes)).append("). ");
            }
        }

        while (!downloadComplete && retryCount <= maxRetries) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.190 Safari/537.36");

                // Set range header for resuming download if we have partial data
                if (downloadedBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");
                    remarks.append("Attempting to resume download from byte position ").append(downloadedBytes).append("\n");
                    LOGGER.info("Attempting to resume download from byte position " + downloadedBytes);
                }

                connection.connect();

                int responseCode = connection.getResponseCode();
                LOGGER.info("HTTP response code: " + responseCode);

                // Get content length for progress tracking and validation
                String contentLengthHeader = connection.getHeaderField("Content-Length");
                long contentLength = contentLengthHeader != null ? Long.parseLong(contentLengthHeader) : -1;

                if (contentLength > 0) {
                    expectedContentLength = (responseCode == 206) ?
                            downloadedBytes + contentLength : contentLength;
                    remarks.append("Expected total file size: ").append(formatSize(expectedContentLength)).append("\n");
                    LOGGER.info("Expected total file size: " + formatSize(expectedContentLength));
                }

                // Check if server supports resume
                boolean canResume = (responseCode == 206); // Partial content
                if (downloadedBytes > 0 && responseCode == 200) {
                    // Server doesn't support resume, start over
                    remarks.append("Server doesn't support resume, starting from beginning\n");
                    LOGGER.info("Server doesn't support resume, starting from beginning");
                    downloadedBytes = 0;
                    if (!targetFile.delete()) {
                        LOGGER.warning("Could not delete partial file before restarting download");
                    }
                }

                // Create parent directories if needed
                if (!targetFile.getParentFile().exists()) {
                    targetFile.getParentFile().mkdirs();
                }

                FileOutputStream fos = null;
                boolean dataReceived = false; // Flag to track if we received any data in this attempt
                long bytesBeforeThisAttempt = downloadedBytes;

                try (InputStream in = connection.getInputStream()) {
                    fos = new FileOutputStream(targetFile, canResume && downloadedBytes > 0);

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesRead = 0;
                    long lastLogTime = System.currentTimeMillis();

                    remarks.append("Downloading...\n");
                    LOGGER.info("Downloading...");

                    while ((bytesRead = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        dataReceived = true; // We received some data

                        // Reset retry count and delay if we're making progress
                        if (totalBytesRead > buffer.length * 2) { // Reset after receiving more than 2 buffers
                            if (retryCount > 0) {
                                LOGGER.info("Download progressing, resetting retry counter");
                                retryCount = 0;
                                retryDelayMs = 5000; // Reset to initial delay
                            }
                        }

                        // Log progress periodically (every 5 seconds)
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastLogTime > 5000) {
                            long totalDownloaded = downloadedBytes + totalBytesRead;
                            if (expectedContentLength > 0) {
                                double progress = (double) totalDownloaded / expectedContentLength * 100;
                                LOGGER.info(String.format("Download progress: %.1f%% (%s / %s)",
                                        progress, formatSize(totalDownloaded), formatSize(expectedContentLength)));
                            } else {
                                LOGGER.info("Downloaded: " + formatSize(totalDownloaded));
                            }
                            lastLogTime = currentTime;
                        }
                    }

                    // Update total bytes downloaded
                    downloadedBytes += totalBytesRead;

                    // Validate download completeness
                    if (expectedContentLength > 0 && downloadedBytes != expectedContentLength) {
                        throw new IOException("Download incomplete. Expected: " + expectedContentLength +
                                " bytes, Downloaded: " + downloadedBytes + " bytes");
                    }

                    downloadComplete = true;
                    remarks.append("Download completed successfully.\n");
                    LOGGER.info("Download completed successfully. Total bytes: " + downloadedBytes);

                } finally {
                    if (fos != null) {
                        fos.close();
                    }
                }

            } catch (IOException e) {
                // Check if we got some data before the error
                long newLength = targetFile.exists() ? targetFile.length() : 0;
                boolean madeProgress = newLength > downloadedBytes;

                if (madeProgress) {
                    downloadedBytes = newLength;
                    remarks.append("Downloaded ").append(formatSize(downloadedBytes)).append(" before interruption.\n");
                    LOGGER.info("Downloaded " + formatSize(downloadedBytes) + " before interruption");

                    // Reset retry count if we made progress - this is the key change
                    retryCount = 0;
                    retryDelayMs = 5000;
                    LOGGER.info("Made download progress, resetting retry counter");
                } else {
                    retryCount++;
                }

                if (retryCount <= maxRetries) {
                    LOGGER.warning("Download interrupted: " + e.getMessage() + ". Retrying (" +
                            retryCount + "/" + maxRetries + ") in " + (retryDelayMs / 1000) + " seconds...");

                    try {
                        Thread.sleep(retryDelayMs);
                        // Exponential backoff with jitter
                        retryDelayMs = Math.min(30000, retryDelayMs * 2) + (int) (Math.random() * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted during retry wait", ie);
                    }
                } else {
                    LOGGER.severe("Max retries exceeded (" + maxRetries + "): " + e.getMessage());
                    remarks.append("Error: Max retries exceeded (").append(maxRetries).append(")\n");
                    throw new IOException("Max retries exceeded (" + maxRetries + ")", e);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        // Final validation of downloaded file
        if (!downloadComplete) {
            throw new IOException("Download failed after " + maxRetries + " retries");
        }
    }

    // Helper method to configure SSL for HTTPS
    private static void configureSSLForHttps(String urlStr) throws IOException {
        if (urlStr.toLowerCase().startsWith("https")) {
            try {
                javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                        new javax.net.ssl.X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                        }
                };
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error configuring SSL context", e);
                throw new IOException("Failed to configure HTTPS connection", e);
            }
        }
    }

    /**
     * Format file size in human-readable format
     */
    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " bytes";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        }
    }

    /**
     * Format duration in human-readable format
     */
    private static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        } else if (ms < 60000) {
            return String.format("%.2f seconds", ms / 1000.0);
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%d minutes, %d seconds", minutes, seconds);
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
        if (path.contains("${")) {
            int startIndex = path.indexOf("${");
            int endIndex = path.indexOf("}", startIndex);
            if (endIndex > startIndex) {
                String envVar = path.substring(startIndex + 2, endIndex);
                String envValue = System.getenv(envVar);
                if (envValue != null) {
                    path = path.replace("${" + envVar + "}", envValue);
                }
            }
        }

        return path;
    }
}