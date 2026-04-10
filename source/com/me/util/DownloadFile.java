package com.me.util;

import com.me.Operation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadFile {

    private static final Logger LOGGER = LogManager.getLogger(DownloadFile.class, LogManager.LOG_TYPE.FW);
    private static String downloadedFileName = null;

    public static boolean downloadFile(String fileURL, String destinationDirectory, String downloadToken, Operation operation) {
        LOGGER.log(Level.INFO, "********************* Download Start *********************");
        LOGGER.log(Level.INFO, "fileURL for download is : {0}", fileURL);
        LOGGER.log(Level.INFO, "destinationDirectory for download is : {0}", destinationDirectory);
            File destinationFolder = new File(destinationDirectory);
            if (!destinationFolder.exists()) {
                destinationFolder.mkdirs();
            }
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            URL url = new URL(fileURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(downloadToken.getBytes());
            connection.setRequestProperty("Authorization", basicAuth);
            connection.setRequestProperty("User-Agent","curl/7.64.1");
            connection.connect();
//            downloadedFileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);

            LOGGER.info("Extracting filename from URL");
            try {
                URL urlObj = new URL(url.toString());
                String urlPath = urlObj.getPath();
                downloadedFileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);

                // Try to extract filename from query parameters if present
                String query = urlObj.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=", 2);
                        if (pair.length == 2 && !pair[1].isEmpty()) {
                            downloadedFileName = pair[1];
                            LOGGER.info("Extracted filename from query parameter key '" + pair[0] + "': " + downloadedFileName);
                            break;
                        }
                    }
                }

            } catch (Exception e) {
//                downloadedFileName = "downloaded_file_" + System.currentTimeMillis();
                operation.setRemarks("Failed to extract filename from URL kindly check the URL");
                LOGGER.info("Failed to extract filename from URL ; " + e);
                return false;
            }

            is = connection.getInputStream();
            fos = new FileOutputStream(destinationDirectory + "/" + downloadedFileName);
            byte[] buffer = new byte[4096];
            int bytesRead = 0;

            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            LOGGER.log(Level.INFO, "********************* Download Done *********************");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception in downloadFile: ", e);
            operation.setRemarks("Download failed: " + e.getMessage());
            return false;
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Exception while closing the file outputstream in downloadFile method: ", ex);
            }
            try {
                if (is != null) {
                    is.close();
                }

            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Exception while closing the file inputstream in downloadFile method: ", ex);
            }
        }
    }
}
