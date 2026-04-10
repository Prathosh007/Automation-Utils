package com.me.testcases;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.testcases.fileOperation.BatFileEditor;
import com.me.util.CommonUtill;
import com.me.util.LogManager;
//import com.me.util.ConsoleOut;
import com.adventnet.mfw.ConsoleOut;
import com.zoho.framework.utils.crypto.CryptoUtil;
import com.zoho.framework.utils.crypto.EnDecrypt;
import com.zoho.framework.utils.crypto.EnDecryptAES256Impl;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.testcases.ServerUtils.getProductServerHome;
import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.CommonUtill.loadJarsFromFile;
import static com.me.util.GOATCommonConstants.CLASS_PATH_FILE;

public class DataBaseOperationHandler {

    private static final Logger LOGGER = LogManager.getLogger(DataBaseOperationHandler.class, LogManager.LOG_TYPE.FW);
    private static Connection connection ;

    /**
     * Execute a database operation based on the operation type
     *
     * @param operation The database operation to execute
     * @return true if the operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation operation) throws Exception {
        if (operation == null) {
            LOGGER.severe("Operation is null"); //NO I18N
            return false;
        }

        // Create a remarks builder to collect all remarks during operation
        StringBuilder remarksBuilder = new StringBuilder();

        String action = operation.getParameter("action"); //NO I18N
        if (action == null || action.isEmpty()) {
            remarksBuilder.append("No action specified in operation"); //NO I18N
            operation.setRemarks(remarksBuilder.toString());
            LOGGER.severe(remarksBuilder.toString());
            return false;
        }

        remarksBuilder.append("Executing database operation: ").append(action).append("\n"); //NO I18N

        String serverHome = getProductServerHome();
        String toolServerHome = getToolServerHome();
        try {
            loadJarsFromFile(toolServerHome+CLASS_PATH_FILE,serverHome);
        } catch (Exception e) {
            remarksBuilder.append("Error loading jars from classpath.txt: ").append(e.getMessage()).append("\n"); //NO I18N
            LOGGER.severe(remarksBuilder.toString());
        }

        String dbType = CommonUtill.getDatabaseType(serverHome); //NO I18N
        if (dbType.equals("postgresql") || dbType.equals("pgsql") || dbType.equals("mssql") || dbType.equals("sqlserver")) {
            // For POSTGRES and MSSQL, retrieve credentials from BAT file
            Map<String,String> credentials = getUserNamePasswordFromConfig(); //NO I18N
            operation.setParameter("username", credentials.get("username")); //NO I18N
            operation.setParameter("password", credentials.get("password")); //NO I18N
        }

        String dbName = CommonUtill.getDatabaseName(serverHome); //NO I18N
        LOGGER.log(Level.INFO, "Database type: " + dbType); //NO I18N
        LOGGER.log(Level.INFO, "Database name: " + dbName); //NO I18N
        operation.setParameter("db_type", dbType); //NO I18N
        operation.setParameter("db_name", dbName); //NO I18N

        remarksBuilder.append("Database type: ").append(dbType).append("\n"); //NO I18N
        remarksBuilder.append("Database name: ").append(dbName).append("\n\n"); //NO I18N

        LOGGER.info("Executing database operation: " + action + " on " + dbType + " database " + dbName); //NO I18N
        boolean success;

        switch (action.toLowerCase()) {
            case "start":
                success = startDatabase(dbType, dbName, operation);
                break;
            case "stop":
                success = stopDatabase(dbType, dbName, operation);
                break;
            case "restart":
                success = restartDatabase(dbType, dbName, operation);
                break;
            case "status":
                success = checkDatabaseStatus(dbType, dbName, operation);
                break;
            case "query":
                success = executeQuery(operation);
//                if (operation.hasNote() && success) {
//                    LOGGER.info("Going to store note for operation: " + operation.getOperationType()); //NO I18N
//                    remarksBuilder.append("Note found, checking and saving if needed.\n"); //NO I18N
//                    checkNote(true, operation, remarksBuilder); //NO I18N
//                    LOGGER.info("Note check completed for operation: " + operation.getOperationType()); //NO I18N
//                    remarksBuilder.append("Note check completed for operation: ").append(operation.getOperationType()).append("\n"); //NO I18N
//                }else {
//                    LOGGER.info("No note found or query execution failed, skipping note check."); //NO I18N
//                    remarksBuilder.append("No note found or query execution failed, skipping note check.\n"); //NO I18N
//                }
                break;
            case "update":
                success = executeUpdate(operation);
                break;
            case "insert":
                success = executeInsert(operation);
                break;
            case "delete":
                success = executeDelete(operation);
                break;
            case "get_values":
                String dbValue = getValueFromDatabase(operation);
                if (dbValue != null) {
                    remarksBuilder.append("Value '").append(dbValue).append("' was found as expected.\n"); //NO I18N
                    if (operation.hasNote()){
                        saveNote(operation, dbValue);
                    }
                    success = true;
                } else {
                    remarksBuilder.append("Value was not found when it should be present.\n"); //NO I18N
                    success = false;
                }
                break;
            case "verify_presence":
                String value = checkValueInDatabase(operation);
                if (Objects.equals(value, operation.getParameter("value"))) {
                    remarksBuilder.append("Value '").append(value).append("' was found as expected.\n"); //NO I18N
                    if (operation.hasNote()){
                        saveNote(operation, value);
                    }
                    success = value.equals(operation.getParameter("value"));
                } else {
                    remarksBuilder.append("Value was not found when it should be present.\n"); //NO I18N
                    success = false;
                }
                break;
            case "verify_absence":
                String absenceValue = checkValueInDatabase(operation);
                if (!Objects.equals(absenceValue, operation.getParameter("value"))) {
                    remarksBuilder.append("Value is absent as expected.\n"); //NO I18N
                    success = true;
                } else {
                    remarksBuilder.append("Value '").append(absenceValue).append("' was found when it should be absent.\n"); //NO I18N
                    success = false;
                }
                break;
            default:
                remarksBuilder.append("Unknown action: ").append(action); //NO I18N
                LOGGER.warning(remarksBuilder.toString());
                success = false;
        }

        // Append any remarks that may have been set by the operation methods
        if (operation.getRemarks() != null && !operation.getRemarks().isEmpty()) {
            remarksBuilder.append("\n").append(operation.getRemarks());
        }

        // Set final consolidated remarks on the operation
        operation.setRemarks(remarksBuilder.toString());

        return success;
    }

    /**
     * Reads the database password from database_params.conf file
     * @return The password as a string, or null if not found
     */
    private static Map getUserNamePasswordFromConfig() {

        Map <String, String> credentials = new HashMap<>();
        String serverHome = System.getProperty("server.home"); //NO I18N
        if (serverHome == null || serverHome.isEmpty()) {
            serverHome = getProductServerHome();
        }

        String configFile = serverHome + File.separator + "conf" + File.separator + "database_params.conf";
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);

            // Look for password property
            String encryptedPass = props.getProperty("password");
            String username = props.getProperty("username");
            if (encryptedPass != null) {
                // Check if password is encrypted and decrypt if needed
                if (encryptedPass.length() > 32) {
                    try {
                        com.zoho.toolkit.commons.crypto.EnDecrypt cryptInstance = new EnDecryptAES256Impl();
                        CryptoUtil.setEnDecryptInstance(cryptInstance);
                        String decryptedPass = CryptoUtil.decrypt(encryptedPass, EnDecrypt.AES256);
                        int retryCount = 0;
                        while (decryptedPass != null && decryptedPass.equals(encryptedPass) && retryCount < 5) {
                            LOGGER.info("Decryption attempt " + (retryCount + 1) + " failed, retrying after delay of 10 sec..."); //NO I18N
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException ie) {
                                LOGGER.severe("Decryption retry sleep interrupted"); //NO I18N
                                Thread.currentThread().interrupt();
                                break;
                            }
                            decryptedPass = CryptoUtil.decrypt(encryptedPass, EnDecrypt.AES256);
                            LOGGER.info("Decryption attempt " + (retryCount + 1) + " result: " + decryptedPass); //NO I18N
                            retryCount++;
                        }
                        LOGGER.info("Continue with this user: " + username); //NO I18N
                        credentials.put("username", username);
                        credentials.put("password", decryptedPass);
                        LOGGER.info("Password in database_params: " + encryptedPass); //NO I18N
                        String encryptionKey = "simplekey1234567";
                        String encrypted = Encrypt(decryptedPass, encryptionKey);
                        LOGGER.info("Encrypted password ': " + encrypted); //NO I18N
                        return credentials;
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to decrypt password, using as-is", e); //NO I18N
                    }
                }
                return credentials;
            }else {
                LOGGER.warning("Password not found in config file: " + configFile); //NO I18N
                return null;
            }


        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading password from config file: " + configFile, e); //NO I18N
            return null;
        }
    }

    private static String Encrypt(String input, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            LOGGER.info("Error while encrypting: " + e.getMessage());
            return null;
        }
    }

    private static String reversibleDecrypt(String encrypted, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.info("Error while encrypting: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, String> getPostgresDBPass(String dbType) {
        Map<String, String> credentials = new HashMap<>();
        Properties props = new Properties();
        String configFile;


        // Special handling for MSSQL - retrieve password from BAT file
        if ("postgresql".equalsIgnoreCase(dbType) || "pgsql".equalsIgnoreCase(dbType)) {
            try {
                String serverHome = System.getProperty("server.home");
                if (serverHome == null || serverHome.isEmpty()) {
                    serverHome = getProductServerHome();
                }
                configFile = serverHome + File.separator + "conf" + File.separator + "database_params.conf"; // Default config file
                String batFilePath = serverHome + File.separator + "bin" + File.separator + "getDBPassword.bat"; //NO I18N

                File batFile = new File(batFilePath);
                if (batFile.exists()) {
                    Operation batOp = new Operation("get_db_password");
                    batOp.setParameter("file_path", batFilePath);
                    batOp.setParameter("timeout", "120"); // 2 minutes timeout
                    batOp.setParameter("output_search_text", "DB password is:"); //DB password is:
                    batOp.setParameter("output_value_pattern", "[a-zA-Z0-9]{16}");

                    StringBuilder batRemarks = new StringBuilder();
                    boolean success = BatFileEditor.executeBatAndGetValue(batFile, batOp, batRemarks);

                    if (success && batOp.getParameter("Found_output_value") != null) {
                        String password = batOp.getParameter("Found_output_value");

                        // Read username from config file
                        try (FileInputStream fis = new FileInputStream(configFile)) {
                            props.load(fis);
                            String username = props.getProperty("username");

                            credentials.put("username", username);
                            credentials.put("password", password);
                            LOGGER.info("Retrieved POSTGRES credentials from BAT file: " + username + " / " + password +" ;"); //NO I18N
                            batRemarks.append("Retrieved POSTGRES credentials: ").append(username).append(" / ").append(password).append(";"); //NO I18N

                            LOGGER.log(Level.INFO, "Retrieved POSTGRES credentials from BAT file");
                            return credentials;
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Error reading username from config file", e);
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to extract password from BAT file: " + batRemarks);
                    }
                } else {
                    LOGGER.log(Level.WARNING, "Password BAT file not found: " + batFilePath);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error retrieving MSSQL credentials from BAT file", e);
            }
        }

//        // Fall back to regular config file method if MSSQL special handling failed or for other DB types
//        try (FileInputStream fis = new FileInputStream(configFile)) {
//            props.load(fis);
//
//            // Construct key prefix for specified database
//            String prefix = dbType.toLowerCase() + "." + dbName + ".";
//
//            // Get username
//            String username = props.getProperty(prefix + "username");
//            if (username == null) {
//                // Try default username for this db type
//                username = props.getProperty(dbType.toLowerCase() + ".default.username");
//            }
//
//            // Get encrypted password
//            String encryptedPassword = props.getProperty(prefix + "password");
//            if (encryptedPassword == null) {
//                // Try default password for this db type
//                encryptedPassword = props.getProperty(dbType.toLowerCase() + ".default.password");
//            }
//
//            // Decrypt password
//            String password = null;
//            if (encryptedPassword != null) {
//                password = decryptPassword(encryptedPassword, op);
//            }
//
//            credentials.put("username", username);
//            credentials.put("password", password);
//
//            return credentials;
//        } catch (IOException e) {
//            LOGGER.log(Level.SEVERE, "Error reading database configuration file: " + configFile, e);
//            op.setRemarks("Failed to read database configuration from " + configFile + ": " + e.getMessage());
//            return credentials;
//        }
        return null;
    }


    /**
     * Moves JAR and BAT files from tool home bin/lib to product home bin/lib directories
     *
     * @param op Operation containing parameters
     * @return true if files were moved successfully, false otherwise
     */
    private static boolean moveFilesToProductHome(Operation op) {
        try {
            String toolHome = getToolServerHome();
            String serverHome = System.getProperty("server.home"); //NO I18N
            if (serverHome == null || serverHome.isEmpty()) {
                serverHome = getProductServerHome();
            }

            // Source directories in tool home
            File batSourceDir = new File(toolHome +File.separator+"product_package"+ File.separator + "bin"+File.separator+"getDBPassword.bat");
            File jarSourceDir = new File(toolHome +File.separator+"product_package"+ File.separator + "lib"+File.separator+"getDBPassword.jar");

            // Target directories in product home
            File batTargetDir = new File(serverHome + File.separator + "bin"+File.separator+"getDBPassword.bat");
            File jarTargetDir = new File(serverHome + File.separator + "lib"+File.separator+"getDBPassword.jar");

            if (batSourceDir.exists() && jarSourceDir.exists()) {
                // Move BAT file
                if (!batTargetDir.exists()) {
                    Files.copy(batSourceDir.toPath(), batTargetDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Copied BAT file to product home: " + batTargetDir.getAbsolutePath()); //NO I18N
                }

                // Move JAR file
                if (!jarTargetDir.exists()) {
                    Files.copy(jarSourceDir.toPath(), jarTargetDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Copied JAR file to product home: " + jarTargetDir.getAbsolutePath()); //NO I18N
                }

                return true;
            } else {
                op.setRemarks("Required files not found in tool home: " + toolHome); //NO I18N
                LOGGER.severe("Required files not found in tool home: " + toolHome); //NO I18N
                return false;
            }


        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error moving files", e); //NO I18N
            op.setRemarks("Error moving files: " + e.getMessage()); //NO I18N
            return false;
        }
    }


    public static void saveNote(Operation operation, String value) {
        String variableName = operation.getParameter("note");
        ResolveOperationParameters.VariableManager.setVariable(variableName, value);
        LOGGER.info("Stored extracted value in variable: " + variableName + " = " + value); //NO I18N
    }



    static String checkValueInDatabase(Operation op) {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String dbType = op.getParameter("db_type");
            String dbName = op.getParameter("db_name");
            String query = op.getParameter("query");

            // If query is not already provided, construct it
            if (query == null || query.isEmpty()) {
                // Ensure query type is SELECT
                op.setParameter("query_type", "SELECT");

                // Construct query from operation parameters
                query = constructQuery(op);
                if (query == null) {
                    LOGGER.severe("Failed to construct query for database value check"); //NO I18N
                    return null;
                }
            }

            LOGGER.log(Level.INFO, "Executing query for value check: " + query); //NO I18N

            // Get connection URL
            String url = getConnectionUrl(dbType, dbName, op);
            if (url == null) {
                return null;
            }

            // Get database credentials
            String username = op.getParameter("username");
            String password = op.getParameter("password");

            if (username == null || password == null) {
                Map<String, String> credentials = getDatabaseCredentials(dbType, dbName, op);
                username = credentials.get("username");
                password = credentials.get("password");

                if (username == null || password == null) {
                    op.setRemarks("Database credentials not found for " + dbType + " " + dbName); //NO I18N
                    return null;
                }
            }

            // Establish connection and execute query
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, username, password);
            }

            stmt = connection.prepareStatement(query);
            rs = stmt.executeQuery();

            // Return the first value from result set
            if (rs.next()) {
                return rs.getString(1);
            } else {
                op.setRemarks("No value found matching the criteria"); //NO I18N
                return null;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking value in database", e); //NO I18N
            op.setRemarks("Error checking value in database: " + e.getMessage()); //NO I18N
            return null;
        } finally {
            closeResources(null, stmt, rs);
        }
    }





    /**
     * Get a specific value from database based on operation parameters
     *
     * @param op Operation containing parameters
     * @return The retrieved value as a string, or null if not found
     */
    private static String getValueFromDatabase(Operation op) {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String dbType = op.getParameter("db_type");
            String dbName = op.getParameter("db_name");
            String query = op.getParameter("query");

            // If query is not already provided, construct it
            if (query == null || query.isEmpty()) {
                // Ensure query type is SELECT
                op.setParameter("query_type", "SELECT");

                // Construct query from operation parameters
                query = constructQuery(op);
                if (query == null) {
                    LOGGER.severe("Failed to construct query for database value check"); //NO I18N
                    return null;
                }
            }
//
//            // Ensure query type is SELECT
//            op.setParameter("query_type", "SELECT");
//
//            // Construct query from operation parameters
//            query = constructQuery(op);
//            if (query == null) {
//                LOGGER.severe("Failed to construct query for database value retrieval"); //NO I18N
//                return null;
//            }

            LOGGER.log(Level.INFO, "Executing query for value retrieval: " + query); //NO I18N

            // Get connection URL
            String url = getConnectionUrl(dbType, dbName, op);
            if (url == null) {
                return null;
            }

            // Get database credentials
            String username = op.getParameter("username");
            String password = op.getParameter("password");

            if (username == null || password == null) {
                Map<String, String> credentials = getDatabaseCredentials(dbType, dbName, op);
                username = credentials.get("username");
                password = credentials.get("password");

                if (username == null || password == null) {
                    op.setRemarks("Database credentials not found for " + dbType + " " + dbName); //NO I18N
                    return null;
                }
            }

            // Establish connection and execute query
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, username, password);
            }

            stmt = connection.prepareStatement(query);
            rs = stmt.executeQuery();

            // Return the first value from result set
            if (rs.next()) {
                return rs.getString(1);
            } else {
                op.setRemarks("No value found matching the criteria"); //NO I18N
                return null;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving value from database", e); //NO I18N
            op.setRemarks("Error retrieving value from database: " + e.getMessage()); //NO I18N
            return null;
        } finally {
            closeResources(null, stmt, rs);
        }
    }





    /**
     * Check if a specific value is present or absent in the database
     * @param op Operation containing parameters
     * @param shouldExist true to check for presence, false to check for absence
     * @return "true" if condition is met (value exists when shouldExist=true, or value doesn't exist when shouldExist=false),
     *         "false" otherwise
     */
    private static String checkDatabaseValuePresence(Operation op, boolean shouldExist) {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String dbType = op.getParameter("db_type");
            String dbName = op.getParameter("db_name");
            String tableName = op.getParameter("table_name");
            String columnName = op.getParameter("column");
            String rowIdentifier = op.getParameter("row");
            String rowIdColumn = op.getParameter("row_id_column");
            String valueToCheck = op.getParameter("value");

            if (tableName == null || tableName.isEmpty()) {
                op.setRemarks("Table name is required for value verification"); //NO I18N
                return null;
            }

            if (valueToCheck == null) {
                op.setRemarks("Value to verify is required"); //NO I18N
                return null;
            }

            // Get connection
            String url = getConnectionUrl(dbType, dbName, op);
            if (url == null) {
                return null;
            }

            String username = op.getParameter("username");
            String password = op.getParameter("password");

            if (username == null || password == null) {
                Map<String, String> credentials = getDatabaseCredentials(dbType, dbName, op);
                username = credentials.get("username");
                password = credentials.get("password");

                if (username == null || password == null) {
                    op.setRemarks("Database credentials not found for " + dbType + " " + dbName); //NO I18N
                    return null;
                }
            }

            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, username, password);
            }

            StringBuilder queryBuilder = new StringBuilder();
            boolean valueExists = false;

            // Case 1: Both column and row specified - check specific cell
            if (columnName != null && !columnName.isEmpty() && rowIdentifier != null && !rowIdentifier.isEmpty()) {
                if (rowIdColumn == null || rowIdColumn.isEmpty()) {
                    rowIdColumn = "id"; // Default ID column
                }

                queryBuilder.append("SELECT COUNT(*) FROM ").append(tableName)
                        .append(" WHERE ").append(rowIdColumn).append(" = ? AND ")
                        .append(columnName).append(" = ?");

                stmt = connection.prepareStatement(queryBuilder.toString());
                stmt.setString(1, rowIdentifier);
                stmt.setString(2, valueToCheck);
            }
            // Case 2: Only column specified - check if value exists in column
            else if (columnName != null && !columnName.isEmpty()) {
                queryBuilder.append("SELECT COUNT(*) FROM ").append(tableName)
                        .append(" WHERE ").append(columnName).append(" = ?");

                stmt = connection.prepareStatement(queryBuilder.toString());
                stmt.setString(1, valueToCheck);
            }
            // Case 3: Only row specified - check if value exists in any column in this row
            else if (rowIdentifier != null && !rowIdentifier.isEmpty()) {
                if (rowIdColumn == null || rowIdColumn.isEmpty()) {
                    rowIdColumn = "id"; // Default ID column
                }

                // This is more complex as we need to check all columns
                queryBuilder.append("SELECT * FROM ").append(tableName)
                        .append(" WHERE ").append(rowIdColumn).append(" = ?");

                stmt = connection.prepareStatement(queryBuilder.toString());
                stmt.setString(1, rowIdentifier);

                rs = stmt.executeQuery();

                if (rs.next()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    for (int i = 1; i <= columnCount; i++) {
                        String value = rs.getString(i);
                        if (valueToCheck.equals(value)) {
                            valueExists = true;
                            break;
                        }
                    }
                }

                // Result already determined for this case
                String result = (valueExists == shouldExist) ? "true" : "false";
                String statusMsg = shouldExist ?
                        (valueExists ? "Value found as expected" : "Value NOT found when it should exist") :
                        (valueExists ? "Value found when it should NOT exist" : "Value absent as expected");

                op.setRemarks("Database value verification: " + statusMsg); //NO I18N
                return result;
            }
            // Case 4: Just table and value - check entire table
            else {
                queryBuilder.append("SELECT COUNT(*) FROM ").append(tableName)
                        .append(" WHERE ");

                // We need a dynamic WHERE clause that checks all columns
                // This is a simplification - in production code you might want
                // to get the column names first and build a more specific query
                queryBuilder.append("CAST(COALESCE(").append(tableName).append(".*) AS VARCHAR) = ?");

                stmt = connection.prepareStatement(queryBuilder.toString());
                stmt.setString(1, valueToCheck);
            }

            // Execute query and check result for cases 1, 2, and 4
            rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                valueExists = (count > 0);
            }

            String result = (valueExists == shouldExist) ? "true" : "false";
            String statusMsg = shouldExist ?
                    (valueExists ? "Value found as expected" : "Value NOT found when it should exist") :
                    (valueExists ? "Value found when it should NOT exist" : "Value absent as expected");

            op.setRemarks("Database value verification: " + statusMsg); //NO I18N
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error verifying database value presence", e); //NO I18N
            op.setRemarks("Error verifying database value presence: " + e.getMessage()); //NO I18N
            return null;
        } finally {
            closeResources(null, stmt, rs);
        }
    }



    /**
     * Verify if specific values exist in the database
     * @param op Operation containing verification parameters
     * @return true if all verifications pass, false otherwise
     */
    private static boolean verifyDatabaseValue(Operation op) {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String dbType = op.getParameter("db_type");
            String dbName = op.getParameter("db_name");
            String tables = op.getParameter("table_name");
            String columns = op.getParameter("column");
            String rows = op.getParameter("row");
            String rowIdColumn = op.getParameter("row_id_column");
            String values = op.getParameter("value");

            if (tables == null || tables.isEmpty()) {
                op.setRemarks("Table name is required for verification"); //NO I18N
                return false;
            }

            if (values == null) {
                op.setRemarks("Value to verify is required"); //NO I18N
                return false;
            }

            // Get connection
            String url = getConnectionUrl(dbType, dbName, op);
            if (url == null) {
                return false;
            }

            String username = op.getParameter("username");
            String password = op.getParameter("password");

            if (username == null || password == null) {
                Map<String, String> credentials = getDatabaseCredentials(dbType, dbName, op);
                username = credentials.get("username");
                password = credentials.get("password");

                if (username == null || password == null) {
                    op.setRemarks("Database credentials not found for " + dbType + " " + dbName); //NO I18N
                    return false;
                }
            }

            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, username, password);
            }

            // Split into multiple tables, columns, rows and values
            String[] tableArray = tables.split(",");
            String[] columnArray = columns != null ? columns.split(",") : null;
            String[] rowArray = rows != null ? rows.split(",") : null;
            String[] valueArray = values.split(",");

            StringBuilder verificationResults = new StringBuilder();
            verificationResults.append("Database verification results:\n\n"); //NO I18N

            boolean allVerificationsPass = true;

            // Handle multiple tables
            for (String tableName : tableArray) {
                tableName = tableName.trim();
                verificationResults.append("Table: ").append(tableName).append("\n"); //NO I18N

                // Handle multiple values
                for (String valueToVerify : valueArray) {
                    valueToVerify = valueToVerify.trim();
                    boolean valueFound = false;

                    // Case 1: Both column and row specified - check specific cells
                    if (columnArray != null && rowArray != null) {
                        for (String column : columnArray) {
                            column = column.trim();
                            for (String row : rowArray) {
                                row = row.trim();
                                if (verifySpecificCell(tableName, column, row, rowIdColumn, valueToVerify, stmt, rs)) {
                                    valueFound = true;
                                    verificationResults.append(" - Value '").append(valueToVerify)
                                            .append("' found in column '").append(column)
                                            .append("', row '").append(row).append("'\n"); //NO I18N
                                    break;
                                }
                            }
                            if (valueFound) break;
                        }
                    }
                    // Case 2: Only column specified - check any row with this value in the column
                    else if (columnArray != null) {
                        for (String column : columnArray) {
                            column = column.trim();
                            if (verifyInColumn(tableName, column, valueToVerify, stmt, rs)) {
                                valueFound = true;
                                verificationResults.append(" - Value '").append(valueToVerify)
                                        .append("' found in column '").append(column).append("'\n"); //NO I18N
                                break;
                            }
                        }
                    }
                    // Case 3: Only row specified - check any column in this row
                    else if (rowArray != null) {
                        for (String row : rowArray) {
                            row = row.trim();
                            if (verifyInRow(tableName, row, rowIdColumn, valueToVerify, stmt, rs)) {
                                valueFound = true;
                                verificationResults.append(" - Value '").append(valueToVerify)
                                        .append("' found in row '").append(row).append("'\n"); //NO I18N
                                break;
                            }
                        }
                    }
                    // Case 4: Neither specified - check entire table
                    else {
                        if (verifyInTable(tableName, valueToVerify, stmt, rs)) {
                            valueFound = true;
                            verificationResults.append(" - Value '").append(valueToVerify)
                                    .append("' found somewhere in table\n"); //NO I18N
                        }
                    }

                    if (!valueFound) {
                        allVerificationsPass = false;
                        verificationResults.append(" - Value '").append(valueToVerify).append("' NOT FOUND\n"); //NO I18N
                    }
                }
                verificationResults.append("\n");
            }

            op.setRemarks(verificationResults.toString());
            return allVerificationsPass;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error verifying database value", e); //NO I18N
            op.setRemarks("Error verifying database value: " + e.getMessage()); //NO I18N
            return false;
        } finally {
            closeResources(null, stmt, rs);
        }
    }

    /**
     * Verify a specific cell in the database
     */
    private static boolean verifySpecificCell(String tableName, String columnName, String rowId,
                                              String rowIdColumn, String value,
                                              PreparedStatement stmt, ResultSet rs) throws SQLException {
        if (rowIdColumn == null || rowIdColumn.isEmpty()) {
            rowIdColumn = "id"; // Default ID column
        }

        String query = "SELECT COUNT(*) FROM " + tableName +
                " WHERE " + columnName + " = ? AND " + rowIdColumn + " = ?";

        stmt = connection.prepareStatement(query);
        stmt.setString(1, value);
        stmt.setString(2, rowId);

        rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getInt(1) > 0;
        }
        return false;
    }

    /**
     * Verify if a value exists in a column
     */
    private static boolean verifyInColumn(String tableName, String columnName, String value,
                                          PreparedStatement stmt, ResultSet rs) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?";

        stmt = connection.prepareStatement(query);
        stmt.setString(1, value);

        rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getInt(1) > 0;
        }
        return false;
    }

    /**
     * Verify if a value exists in a row
     */
    private static boolean verifyInRow(String tableName, String rowId, String rowIdColumn,
                                       String value, PreparedStatement stmt, ResultSet rs) throws SQLException {
        if (rowIdColumn == null || rowIdColumn.isEmpty()) {
            rowIdColumn = "id"; // Default ID column
        }

        String query = "SELECT * FROM " + tableName + " WHERE " + rowIdColumn + " = ?";

        stmt = connection.prepareStatement(query);
        stmt.setString(1, rowId);

        rs = stmt.executeQuery();
        if (rs.next()) {
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String colValue = rs.getString(i);
                if (colValue != null && colValue.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verify if a value exists anywhere in a table
     */
    private static boolean verifyInTable(String tableName, String value,
                                         PreparedStatement stmt, ResultSet rs) throws SQLException {
        String query = "SELECT * FROM " + tableName;

        stmt = connection.prepareStatement(query);
        rs = stmt.executeQuery();

        while (rs.next()) {
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String colValue = rs.getString(i);
                if (colValue != null && colValue.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }





    /**
     * Start a database server
     */
    private static boolean startDatabase(String dbType, String dbName, Operation op) {
        try {
            ProcessBuilder pb;

            switch (dbType.toLowerCase()) {
                case "mysql":
                    pb = new ProcessBuilder("net", "start", "MySQL" + (dbName != null ? "_" + dbName : "")); //NO I18N
                    break;
                case "oracle":
                    pb = new ProcessBuilder("net", "start", "OracleServiceXE"); //NO I18N
                    break;
                case "sqlserver":
                    pb = new ProcessBuilder("net", "start", "MSSQLSERVER"); //NO I18N
                    break;
                case "postgresql":
                case "postgres":
                case "pgsql":
                    // Get PostgreSQL port from configuration file
                    pb = startStopPG("start");
                    break;
                default:
                    op.setRemarks("Unsupported database type: " + dbType); //NO I18N
                    return false;
            }

            return executeProcessAndCheck(pb, "start", dbType, dbName, op);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting database: " + dbType + " " + dbName, e); //NO I18N
            op.setRemarks("Error starting database: " + dbType + " " + dbName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    public static ProcessBuilder startStopPG(String action) {
        String port = getPostgreSQLPortFromConfig();
        if (port == null) {
            port = "8028"; // Default port if not found in config
            LOGGER.log(Level.INFO, "Using default PostgreSQL port: " + port); //NO I18N
        }

        String serverHome = System.getProperty("server.home"); //NO I18N
        if (serverHome == null || serverHome.isEmpty()) {
            serverHome = getProductServerHome();
        }

        String pgBinPath = new File(serverHome + File.separator + "pgsql" + File.separator + "bin").getAbsolutePath();
        String pgDataPath = new File(serverHome + File.separator + "pgsql" + File.separator + "data").getAbsolutePath();

        // Construct pg_ctl command with port
        ProcessBuilder pb = new ProcessBuilder(pgBinPath + File.separator + "pg_ctl.exe", "-D", pgDataPath, "-o", "\"-p " + port + "\"", action);

        LOGGER.log(Level.INFO, "Starting PostgreSQL with command: " +
                pgBinPath + File.separator + "pg_ctl.exe -D " + pgDataPath + " -o\"-p " + port + "\" start"); //NO I18N
        return pb;
    }


    /**
     * Reads the PostgreSQL port from database_params.conf file
     * @return The port number as a string, or null if not found
     */
    private static String getPostgreSQLPortFromConfig() {
        String serverHome = System.getProperty("server.home"); //NO I18N
        if (serverHome == null || serverHome.isEmpty()) {
            serverHome = getProductServerHome();
        }

        String configFile = serverHome + File.separator + "conf" + File.separator + "database_params.conf";
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);

            // Look for URL property that contains port information
            String url = props.getProperty("url");

            if (url != null && url.contains("postgresql")) {
                // Extract port from URL like jdbc:postgresql://localhost:8028/postgres
                int portIndex = url.indexOf(':', url.indexOf("://") + 3);
                if (portIndex != -1) {
                    int endIndex = url.indexOf('/', portIndex);
                    if (endIndex != -1) {
                        return url.substring(portIndex + 1, endIndex);
                    }
                }
            }

            // If URL parsing fails, look for explicit port property
            return props.getProperty("port");

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading PostgreSQL port from config file: " + configFile, e); //NO I18N
            return null;
        }
    }
    /**
     * Stop a database server
     */
    private static boolean stopDatabase(String dbType, String dbName, Operation op) {
        try {
            ProcessBuilder pb;

            switch (dbType.toLowerCase()) {
                case "mysql":
                    pb = new ProcessBuilder("net", "stop", "MySQL" + (dbName != null ? "_" + dbName : "")); //NO I18N
                    break;
                case "oracle":
                    pb = new ProcessBuilder("net", "stop", "OracleServiceXE"); //NO I18N
                    break;
                case "sqlserver":
                    pb = new ProcessBuilder("net", "stop", "MSSQLSERVER"); //NO I18N
                    break;
                case "postgresql":
//                    pb = new ProcessBuilder("net", "stop", "postgresql-x64-14"); //NO I18N
                    pb = startStopPG("stop");
                    break;
                default:
                    op.setRemarks("Unsupported database type: " + dbType); //NO I18N
                    return false;
            }

            return executeProcessAndCheck(pb, "stop", dbType, dbName, op);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error stopping database: " + dbType + " " + dbName, e); //NO I18N
            op.setRemarks("Error stopping database: " + dbType + " " + dbName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
    }

    /**
     * Restart a database (stop and then start)
     */
    private static boolean restartDatabase(String dbType, String dbName, Operation op) {
        StringBuilder remarks = new StringBuilder();
        remarks.append("Database restart operation: ").append(dbType).append(" ").append(dbName).append("\n\n"); //NO I18N

        // First stop the database
        boolean stopSuccess = stopDatabase(dbType, dbName, op);
        remarks.append("Stop database result: ").append(stopSuccess ? "SUCCESS" : "FAILED").append("\n"); //NO I18N
        if (op.getRemarks() != null) {
            remarks.append(op.getRemarks()).append("\n\n");//NO I18N
        }

        // Wait a bit before starting
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Now start the database
        boolean startSuccess = startDatabase(dbType, dbName, op);
        remarks.append("Start database result: ").append(startSuccess ? "SUCCESS" : "FAILED").append("\n"); //NO I18N
        if (op.getRemarks() != null) {
            remarks.append(op.getRemarks());
        }

        // Set consolidated remarks
        op.setRemarks(remarks.toString());

        return startSuccess;
    }

    /**
     * Check database status
     */
    private static boolean checkDatabaseStatus(String dbType, String dbName, Operation op) {
        try {
//            Connection connection = null;
            String url = getConnectionUrl(dbType, dbName, op);
            LOGGER.log(Level.INFO, "Going to connect the Database URL : " + url); //NO I18N
            String username = op.getParameter("username"); //NO I18N
            String password = op.getParameter("password"); //NO I18N

            if (url == null) {
                return false;
            }

            try {
                if (connection == null || connection.isClosed()) {
                    connection = DriverManager.getConnection(url, username, password);
                    op.setRemarks("Database connection successful: " + dbType + " " + dbName); //NO I18N
                    return true;
                }
            } catch (SQLException e) {
                op.setRemarks("Failed to connect to database: " + dbType + " " + dbName + "\nError: " + e.getMessage()); //NO I18N
                return false;
            } finally {
                assert connection != null;
                if (!connection.isClosed()) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        LOGGER.log(Level.WARNING, "Error closing connection", e); //NO I18N
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking database status: " + dbType + " " + dbName, e); //NO I18N
            op.setRemarks("Error checking database status: " + dbType + " " + dbName + "\n" + e.getMessage()); //NO I18N
            return false;
        }
        return false;
    }


    /**
     * Construct a SQL query based on parameters
     *
     * @param op Operation containing query parameters
     * @return Constructed SQL query string
     */
    private static String constructQuery(Operation op) {

        String columns ;
        String whereClause ;
        String orderBy;
        String limit ;

        String queryType = op.getParameter("query_type"); // SELECT, INSERT, UPDATE, DELETE
        String tableName = op.getParameter("table_name");

        if (tableName == null || tableName.isEmpty()) {
            op.setRemarks("Table name is required for query construction"); //NO I18N
            return null;
        }

        StringBuilder query = new StringBuilder();

        switch (queryType != null ? queryType.toUpperCase() : "SELECT") {
            case "SELECT":
                columns = op.getParameter("columns");
                whereClause = op.getParameter("where");
                orderBy = op.getParameter("order_by");
                limit = op.getParameter("limit");

                query.append("SELECT ").append(columns != null && !columns.isEmpty() ? columns : "*")
                        .append(" FROM ").append(tableName);

                if (whereClause != null && !whereClause.isEmpty()) {
                    query.append(" WHERE ").append(whereClause);
                }

                if (orderBy != null && !orderBy.isEmpty()) {
                    query.append(" ORDER BY ").append(orderBy);
                }

                if (limit != null && !limit.isEmpty()) {
                    query.append(" LIMIT ").append(limit);
                }
                break;

            case "INSERT":
                columns = op.getParameter("columns");
                String values = op.getParameter("values");

                if (columns == null || values == null) {
                    op.setRemarks("Columns and values are required for INSERT query"); //NO I18N
                    return null;
                }

                query.append("INSERT INTO ").append(tableName)
                        .append(" (").append(columns).append(")")
                        .append(" VALUES (").append(values).append(")");
                break;

            case "UPDATE":
                String setClause = op.getParameter("set");
                whereClause = op.getParameter("where");

                if (setClause == null) {
                    op.setRemarks("SET clause is required for UPDATE query"); //NO I18N
                    return null;
                }

                query.append("UPDATE ").append(tableName)
                        .append(" SET ").append(setClause);

                if (whereClause != null && !whereClause.isEmpty()) {
                    query.append(" WHERE ").append(whereClause);
                }
                break;

            case "DELETE":
                whereClause = op.getParameter("where");

                query.append("DELETE FROM ").append(tableName);

                if (whereClause != null && !whereClause.isEmpty()) {
                    query.append(" WHERE ").append(whereClause);
                }
                break;

            default:
                op.setRemarks("Unknown query type: " + queryType); //NO I18N
                return null;
        }

        return query.toString();
    }


    /**
     * Read database credentials from configuration file
     * @param dbType Database type
     * @param dbName Database name
     * @param op Operation object to store remarks
     * @return Map containing username and password
     */
    private static Map<String, String> getDatabaseCredentials(String dbType, String dbName, Operation op) {
        Map<String, String> credentials = new HashMap<>();
        Properties props = new Properties();
        String configFile = op.getParameter("config_file");

        if (configFile == null || configFile.isEmpty()) {
            configFile = getProductServerHome() + File.separator + "conf" + File.separator +"database_params.conf"; // Default config file
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);

            // Construct key prefix for specified database
            String prefix = dbType.toLowerCase() + "." + dbName + ".";

            // Get username
            String username = props.getProperty(prefix + "username");
            if (username == null) {
                // Try default username for this db type
                username = props.getProperty(dbType.toLowerCase() + ".default.username");
            }

            // Get encrypted password
            String encryptedPassword = props.getProperty(prefix + "password");
            if (encryptedPassword == null) {
                // Try default password for this db type
                encryptedPassword = props.getProperty(dbType.toLowerCase() + ".default.password");
            }

            // Decrypt password
            String password = null;
            if (encryptedPassword != null) {
                password = decryptPassword(encryptedPassword, op);
            }

            credentials.put("username", username);
            credentials.put("password", password);

            return credentials;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading database configuration file: " + configFile, e);
            op.setRemarks("Failed to read database configuration from " + configFile + ": " + e.getMessage());
            return credentials;
        }
    }

    /**
     * Decrypt password
     * @param encryptedPassword The encrypted password string
     * @param op Operation object to store remarks
     * @return Decrypted password
     */
    private static String decryptPassword(String encryptedPassword, Operation op) {
        try {
            // Get the encryption handler from system property
            String encryptionHandlerClass = System.getProperty("db.encryption.handler",
                    "com.me.security.EncryptionHandler");

            Class<?> handlerClass = Class.forName(encryptionHandlerClass);
            Object handler = handlerClass.getDeclaredConstructor().newInstance();

            // Use reflection to call the decrypt method
            Method decryptMethod = handlerClass.getMethod("decrypt", String.class);
            return (String) decryptMethod.invoke(handler, encryptedPassword);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error decrypting password", e);
            op.setRemarks("Failed to decrypt database password: " + e.getMessage());
            return null;
        }
    }

    /**
     * Execute a SQL query (SELECT)
     */
    private static boolean executeQuery(Operation op) {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String dbType = op.getParameter("db_type");
            String dbName = op.getParameter("db_name");
            String query = op.getParameter("query");
            String expectedValue = op.getParameter("expected_value");

            // Use constructed query if no direct query provided
            if ((query == null || query.isEmpty()) && op.getParameter("table_name") != null) {
                query = constructQuery(op);
                if (query == null) {
                    return false; // Error already set in remarks
                }
            } else if (query == null || query.isEmpty()) {
                op.setRemarks("No query specified"); //NO I18N
                return false;
            }
            LOGGER.log(Level.INFO, "Going to execute query: " + query); //NO I18N

            String url = getConnectionUrl(dbType, dbName, op);
            LOGGER.info("Going to connect the Database URL : " + url); //NO I18N
            if (url == null) {
                return false;
            }

            // Get database credentials - first try parameters, then config file
            String username = op.getParameter("username");
            String password = op.getParameter("password");

            if (username == null || password == null) {
                Map<String, String> credentials = getDatabaseCredentials(dbType, dbName, op);
                username = credentials.get("username");
                password = credentials.get("password");

                if (username == null || password == null) {
                    op.setRemarks("Database credentials not found for " + dbType + " " + dbName); //NO I18N
                    return false;
                }
            }

            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, username, password);
            }

            stmt = connection.prepareStatement(query);

            // Use execute() which returns true if the result is a ResultSet
            boolean isResultSet = stmt.execute();
            StringBuilder result = new StringBuilder();
            boolean valueMatched = true; // Default to true if no expected value

            if (isResultSet) {
                // Handle SELECT queries (returns ResultSet)
                rs = stmt.getResultSet();
                result.append("Query executed successfully:\n").append(query).append("\n\n"); //NO I18N

                int rowCount = 0;
                int columnCount = rs.getMetaData().getColumnCount();
                String actualValue = null;

                // Add column headers
                for (int i = 1; i <= columnCount; i++) {
                    result.append(rs.getMetaData().getColumnName(i)).append("\t"); //NO I18N
                }
                result.append("\n"); //NO I18N

                // Add data rows
                while (rs.next()) {
                    rowCount++;

                    // Capture first cell of first row as the actual value for comparison
                    if (rowCount == 1) {
                        actualValue = rs.getString(1);
                        LOGGER.info("Actual value from first row: " + actualValue); //NO I18N
                    }

                    for (int i = 1; i <= columnCount; i++) {
                        result.append(rs.getString(i)).append("\t"); //NO I18N
                    }
                    result.append("\n"); //NO I18N

                    // Limit results to avoid overwhelming output
                    if (rowCount >= 100) {
                        result.append("... (results truncated, showing first 100 rows)"); //NO I18N
                        break;
                    }
                }

                result.append("\nTotal rows: ").append(rowCount); //NO I18N

                // Compare expected value with actual value if provided
                if (expectedValue != null && actualValue != null) {
                    valueMatched = expectedValue.equals(actualValue);
                    result.append("\nValue comparison: ").append(valueMatched ? "MATCHED" : "NOT MATCHED")
                            .append("\nExpected: '").append(expectedValue)
                            .append("', Actual: '").append(actualValue).append("'"); //NO I18N

                    if (valueMatched && op.hasNote()) {
                        saveNote(op, actualValue);
                    }
                } else if (expectedValue == null && actualValue != null) {
                    LOGGER.info("No expected value provided for comparison, actual value is: " + actualValue); //NO I18N
                    result.append("\nNo expected value provided for comparison, actual value is: ").append(actualValue); //NO I18N
                    if (op.hasNote()){
                        LOGGER.info("Saving note with actual value: " + actualValue); //NO I18N
                        saveNote(op, actualValue);
                        LOGGER.info("Note saved successfully."); //NO I18N
                    }
                } else {
                    result.append("\nNo expected value provided for comparison.");
                    LOGGER.info("No expected value provided for comparison, proceeding without validation."); //NO I18N
                }

            } else {
                // Handle UPDATE, INSERT, DELETE (returns update count)
                int rowsAffected = stmt.getUpdateCount();
                result.append("Update operation executed successfully:\n").append(query)
                        .append("\n\nRows affected: ").append(rowsAffected); //NO I18N
            }

            op.setRemarks(result.toString());
            return expectedValue != null ? valueMatched : true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing query", e); //NO I18N
            op.setRemarks("Error executing query: " + e.getMessage()); //NO I18N
            return false;
        } finally {
            closeResources(connection, stmt, rs);
        }
    }

    /**
     * Execute a SQL update statement (UPDATE)
     */
    private static boolean executeUpdate(Operation op) {
        return executeUpdateStatement(op, "update"); //NO I18N
    }

    /**
     * Execute a SQL insert statement (INSERT)
     */
    private static boolean executeInsert(Operation op) {
        return executeUpdateStatement(op, "insert"); //NO I18N
    }

    /**
     * Execute a SQL delete statement (DELETE)
     */
    private static boolean executeDelete(Operation op) {
        return executeUpdateStatement(op, "delete"); //NO I18N
    }

    /**
     * Helper method to execute any statement that updates the database (INSERT, UPDATE, DELETE)
     */
    private static boolean executeUpdateStatement(Operation op, String operationType) {
//        Connection connection = null;
        PreparedStatement stmt = null;

        try {
            String dbType = op.getParameter("db_type"); //NO I18N
            String dbName = op.getParameter("db_name"); //NO I18N
            String query = op.getParameter("query"); //NO I18N
            String username = op.getParameter("username"); //NO I18N
            String password = op.getParameter("password"); //NO I18N

            if (query == null || query.isEmpty()) {
                op.setRemarks("No SQL statement specified"); //NO I18N
                return false;
            }

            String url = getConnectionUrl(dbType, dbName, op);
            if (url == null) {
                return false;
            }

            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, username, password);
            }
            stmt = connection.prepareStatement(query);
            int rowsAffected = stmt.executeUpdate();

            op.setRemarks(operationType.toUpperCase() + " operation executed successfully.\nRows affected: " + rowsAffected); //NO I18N
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing " + operationType, e); //NO I18N
            op.setRemarks("Error executing " + operationType + ": " + e.getMessage()); //NO I18N
            return false;
        } finally {
            closeResources(connection, stmt, null);
        }
    }

    /**
     * Helper method to get database connection URL and register appropriate driver
     */
    private static String getConnectionUrl(String dbType, String dbName, Operation op) {
        if (dbType == null || dbName == null) {
            op.setRemarks("Database type or name is missing"); //NO I18N
            return null;
        }
        String configFile = getProductServerHome() + File.separator + "conf" + File.separator + "database_params.conf";
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            return props.getProperty("url");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading database configuration file", e);
            return null;
        }
    }

    /**
     * Execute a process and check its output
     */
    private static boolean executeProcessAndCheck(ProcessBuilder pb, String action, String dbType, String dbName, Operation op) throws Exception {
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n"); //NO I18N
            if (action.equals("start") && line.equals("server started")){
                LOGGER.info("Database server started successfully: " + dbType + " " + dbName); //NO I18N
                break;
            } else if (action.equals("stop") && line.equals("server stopped")) {
                LOGGER.info("Database server stopped successfully: " + dbType + " " + dbName); //NO I18N
                break;
            }
            LOGGER.info(line); //NO I18N
        }

        int exitValue = process.waitFor();
        String outputStr = output.toString();

        // Check if operation was successful
        boolean success = exitValue == 0;

        if (!success) {
            LOGGER.warning("Failed to " + action + " database: " + dbType + " " + dbName + "\nOutput: " + outputStr); //NO I18N
        } else {
            LOGGER.info("Database " + action + " successful: " + dbType + " " + dbName); //NO I18N
        }

        op.setRemarks("Database " + action + " operation for " + dbType + " " + dbName + ":\n" + outputStr); //NO I18N
        return success;
    }

    /**
     * Close database resources
     */
    private static void closeResources(Connection conn, PreparedStatement stmt, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing ResultSet", e); //NO I18N
            }
        }

        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing PreparedStatement", e); //NO I18N
            }
        }

        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Connection", e); //NO I18N
            }
        }
    }

    /**
     * Main method to test the database operation handler directly
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            ConsoleOut.println("Usage: DataBaseOperationHandler <action> <db_type> <db_name> [additional parameters]"); //NO I18N
            ConsoleOut.println("  action: start, stop, restart, status, query, update, insert, delete"); //NO I18N
            ConsoleOut.println("  db_type: mysql, oracle, sqlserver, postgresql"); //NO I18N
            return;
        }

        String action = args[0];
        String dbType = args[1];
        String dbName = args[2];

        Operation op = new Operation("database_actions"); //NO I18N
        op.setParameter("action", action); //NO I18N
        op.setParameter("db_type", dbType); //NO I18N
        op.setParameter("db_name", dbName); //NO I18N

        // Process additional parameters
        for (int i = 3; i < args.length; i++) {
            String[] parts = args[i].split("=", 2);
            if (parts.length == 2) {
                op.setParameter(parts[0], parts[1]);
            }
        }

        boolean result = executeOperation(op);

        ConsoleOut.println("Operation result: " + (result ? "SUCCESS" : "FAILED")); //NO I18N
        ConsoleOut.println("\nRemarks:"); //NO I18N
        ConsoleOut.println(op.getRemarks());
    }
}