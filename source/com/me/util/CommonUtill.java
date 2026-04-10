package com.me.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.me.Operation;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.sql.SQLFeatureNotSupportedException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.GOATCommonConstants.LOADED_JAR_LIST;
import static com.me.util.GOATCommonConstants.PRODUCT_SETUP_CONF;
import static com.me.util.GOATCommonConstants.TEST_SETUP_DETAILES;

public class CommonUtill {
    private static final Logger LOGGER = LogManager.getLogger(CommonUtill.class.getName(), LogManager.LOG_TYPE.FW);

    /**
     * Get database type from database_params.conf
     *
     * @param serverHome server home directory
     * @return database type (mssql or postgres)
     */
    public static String getDatabaseType(String serverHome) {
        String url = getDatabaseUrl(serverHome);
        if (url == null) {
            return null;
        }

        if (url.contains("sqlserver")) {
            return "sqlserver";
        } else if (url.contains("postgresql")) {
            return "postgresql";
        }

        return null;
    }

    /**
     * Get database name from database_params.conf
     *
     * @param serverHome server home directory
     * @return database name
     */
    public static String getDatabaseName(String serverHome) {
        String url = getDatabaseUrl(serverHome);
        if (url == null) {
            return null;
        }

        if (url.contains("sqlserver")) {
            // For MSSQL, database name is after databaseName=
            int startIndex = url.indexOf("databaseName=");
            if (startIndex != -1) {
                startIndex += "databaseName=".length();
                int endIndex = url.indexOf(";", startIndex);
                if (endIndex != -1) {
                    return url.substring(startIndex, endIndex).trim();
                } else {
                    return url.substring(startIndex).trim();
                }
            }
        } else if (url.contains("postgresql")) {
            // For PostgreSQL, database name is after last /
            int startIndex = url.lastIndexOf("/");
            if (startIndex != -1) {
                int endIndex = url.indexOf("?", startIndex);
                if (endIndex != -1) {
                    return url.substring(startIndex + 1, endIndex);
                } else {
                    return url.substring(startIndex + 1);
                }
            }
        }

        return null;
    }

    /**
     * Get the database URL from database_params.conf
     *
     * @param serverHome server home directory
     * @return JDBC URL string
     */
    private static String getDatabaseUrl(String serverHome) {
        String configPath = serverHome + File.separator + "conf" + File.separator + "database_params.conf";
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(configPath)) {
            props.load(fis);
            return props.getProperty("url");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading database_params.conf", e);
            return null;
        }
    }

    private static boolean isJARsLoaded = false;

    public static void loadJarsFromFile(String jarListFile, String serverHome) {
        if (isJARsLoaded) {
            LOGGER.log(Level.INFO, "LoadJARs already called. JARs have been loaded in previous initialization");
            return;
        }

        List<URL> jarsNeedToAdd = new ArrayList<>();
        int skippedJars = 0;
        int loadedJars = 0;

        if (new File(jarListFile).exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(jarListFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    String jarPath = line.replace("server_home", serverHome);
                    File jarFile = new File(jarPath);

                    if (jarFile.exists()) {
                        if (jarFile.isDirectory()) {
                            File[] jars = jarFile.listFiles((dir, name) ->
                                    name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".dll"));
                            if (jars != null && jars.length > 0) {
                                for (File jar : jars) {
                                    URL jarUrl = jar.toURI().toURL();
                                    if (!LOADED_JAR_LIST.contains(jarUrl)) {
                                        jarsNeedToAdd.add(jarUrl);
                                        LOADED_JAR_LIST.add(jarUrl);
                                        loadedJars++;
                                        LOGGER.log(Level.INFO, "Added JAR from directory: " + jar.getAbsolutePath());
                                    }
                                }
                            } else {
                                LOGGER.log(Level.WARNING, "No JAR files found in directory: " + jarPath);
                                skippedJars++;
                            }
                        } else if (jarPath.toLowerCase().endsWith(".jar") || jarPath.toLowerCase().endsWith(".dll")) {
                            URL jarUrl = jarFile.toURI().toURL();
                            if (!LOADED_JAR_LIST.contains(jarUrl)) {
                                jarsNeedToAdd.add(jarUrl);
                                LOADED_JAR_LIST.add(jarUrl);
                                loadedJars++;
                                LOGGER.log(Level.INFO, "Added JAR: " + jarPath);
                            }
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "File or directory not found: " + jarPath);
                        skippedJars++;
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error reading JAR list file: " + jarListFile, e);
            }

            if (!jarsNeedToAdd.isEmpty()) {
                LOGGER.log(Level.INFO, "Loading " + loadedJars + " JARs into JVM, skipped " + skippedJars);
                LOGGER.log(Level.FINE, "The list of JARs to be added to URLClassLoader are {0}", jarsNeedToAdd);

                try {
                    URLClassLoader ucl = new URLClassLoader(
                            jarsNeedToAdd.toArray(new URL[0]),
                            Thread.currentThread().getContextClassLoader()
                    );
                    Thread.currentThread().setContextClassLoader(ucl);
                    registerDatabaseDrivers(ucl, serverHome);
                    isJARsLoaded = true;
                    LOGGER.log(Level.INFO, "Successfully loaded " + loadedJars + " JARs into thread context class loader");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to create URLClassLoader", e);
                }
            } else {
                LOGGER.log(Level.WARNING, "No new JARs to load from " + jarListFile);
            }
        } else {
            LOGGER.log(Level.WARNING, "JAR list file not found: " + jarListFile);
        }
    }

    private static void registerDatabaseDrivers(URLClassLoader classLoader, String serverHome) {
        String driverClassName = getDatabaseDriver(serverHome);

        if (driverClassName == null || driverClassName.isEmpty()) {
            LOGGER.log(Level.WARNING, "No database driver found in database_params.conf");
            return;
        }

        try {
            Class<?> driver = classLoader.loadClass(driverClassName);
            java.sql.Driver driverInstance = (java.sql.Driver) driver.getDeclaredConstructor().newInstance();
            java.sql.DriverManager.registerDriver(new DriverShim(driverInstance));
            LOGGER.log(Level.INFO, "Successfully registered JDBC driver: " + driverClassName);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Driver class not found in loaded JARs: " + driverClassName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to register driver: " + driverClassName, e);
        }
    }

    private static String getDatabaseDriver(String serverHome) {
        String configPath = serverHome + File.separator + "conf" + File.separator + "database_params.conf";
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(configPath)) {
            props.load(fis);
            return props.getProperty("drivername");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading database_params.conf", e);
            return null;
        }
    }

    static class DriverShim implements java.sql.Driver {
        private java.sql.Driver driver;

        DriverShim(java.sql.Driver d) {
            this.driver = d;
        }

        @Override
        public java.sql.Connection connect(String u, java.util.Properties p) throws java.sql.SQLException {
            return this.driver.connect(u, p);
        }

        @Override
        public boolean acceptsURL(String u) throws java.sql.SQLException {
            return this.driver.acceptsURL(u);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, java.util.Properties p) throws java.sql.SQLException {
            return this.driver.getPropertyInfo(u, p);
        }

        @Override
        public int getMajorVersion() {
            return this.driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return this.driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return this.driver.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return this.driver.getParentLogger();
        }
    }


    /**
     * Create an Operation object from a JSON operation definition
     *
     * @param operationJson The JSON object defining the operation
     * @return The Operation object, or null if creation fails
     */
    public static Operation createOperationFromJson(JsonObject operationJson) {
        if (operationJson == null) {
            LOGGER.severe("Operation JSON is null");//No I18N
            return null;
        }

        try {
            // Extract operation type
            if (!operationJson.has("operation_type")) {//No I18N
                LOGGER.severe("Missing operation_type in operation JSON");//No I18N
                return null;
            }
            String operationType = operationJson.get("operation_type").getAsString();//No I18N

            // Extract parameters
            Map<String, String> parameters = new HashMap<>();
            if (operationJson.has("parameters")) {//No I18N
                JsonObject paramsJson = operationJson.getAsJsonObject("parameters");//No I18N

                for (Map.Entry<String, JsonElement> entry : paramsJson.entrySet()) {
                    String key = entry.getKey();
                    JsonElement value = entry.getValue();

                    // Handle different value types
                    if (value.isJsonPrimitive()) {
                        parameters.put(key, value.getAsString());
                    } else {
                        // For non-primitive types (objects, arrays), convert to JSON string
                        parameters.put(key, value.toString());
                    }
                }
            }

            // Create the operation object
            Operation operation = new Operation(operationType, parameters);
            return operation;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating operation from JSON", e);//No I18N
            return null;
        }
    }

    public static boolean deleteOldestUnusedServerFolder(String parentDir) {
        File dir = new File(parentDir);
        File[] folders = dir.listFiles(file -> file.isDirectory() && file.getName().matches("^[0-9]{8}_[0-9]{6}$"));
        if (folders == null || folders.length <= 5) {
            LOGGER.info("Folder count is 2 or less, no folder will be deleted.");
            return false;
        }

        // Sort by last modified (oldest first)
        Arrays.sort(folders, Comparator.comparingLong(File::lastModified));

        for (File folder : folders) {
            // Check if any service is running from this folder
            if (!isServiceInstalledInFolder(folder.getAbsolutePath())) {
                try {
                    deleteDirectoryRecursively(folder);
                    LOGGER.info("Deleted unused server folder: " + folder.getAbsolutePath());
                    return true;
                } catch (Exception e) {
                    LOGGER.warning("Failed to delete folder: " + folder.getAbsolutePath() + " - " + e.getMessage());
                }
            }
        }
        LOGGER.info("No unused server folder found to delete.");
        return false;
    }

    // Java code to run PowerShell and check if any service's PathName contains the folder path
    private static boolean isServiceInstalledInFolder(String path) {
        try {
            String command = "powershell.exe -Command \"Get-WmiObject Win32_Service | " +
                    "Where-Object { $_.PathName -match '" + path.replace("\\", "\\\\") + "' } | " +
                    "Select-Object -First 1\"";
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().anyMatch(line -> !line.trim().isEmpty());
            }
        } catch (Exception e) {
            return false;
        }
    }

    // Helper: Recursively delete a directory
    private static void deleteDirectoryRecursively(File dir) throws Exception {
        Files.walk(dir.toPath())
                .sorted(Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(File::delete);
    }

    public static String resolveHome(String filePath) {
        if (filePath == null) {
            return null;
        }

        // Define all home keywords and their corresponding resolver methods
        String[][] homeMappings = {
                {"server_home", "serverHome",},
                {"agent_home", "getAgentHome"},
                {"ds_home", "getDSHome"},
                {"sgs_home", "getSGSHome"},
                {"vmp_home", "getVMPHome"},
                {"pmp_home", "getPMPHome"},
                {"msp_home", "getMSPHome"},
                {"mdm_home", "getMDMHome"},
                {"ss_home", "getSSHome"},
                {"tool_home", "getToolHome"}
        };

        for (String[] mapping : homeMappings) {
            String keyword = mapping[0];
            String method = mapping[1];

            if (filePath.toLowerCase().contains(keyword)) {
                String homeValue = null;
                switch (method) {
                    case "serverHome":
                        homeValue = com.me.testcases.ServerUtils.resolvePath("server_home");
                        break;
                    case "getAgentHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("agent");
                        break;
                    case "getDSHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("ds");
                        break;
                    case "getSGSHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("sgs");
                        break;
                    case "getVMPHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("vmp");
                        break;
                    case "getPMPHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("pmp");
                        break;
                    case "getMSPHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("msp");
                        break;
                    case "getMDMHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("mdm");
                        break;
                    case "getSSHome":
                        homeValue = com.me.testcases.ServerUtils.getHomeFromService("summary_server");
                        break;
                    case "getToolHome":
                        homeValue = getToolServerHome();
                }
                if (homeValue != null) {
                    filePath = filePath.replace(keyword, homeValue);
                    // Continue replacing if multiple keywords exist
                }
            }
        }
        return filePath;
    }

    public static boolean changeServiceNameInTestSetupConf(Operation operation) {
        // Check if product_name parameter exists and save it
        String testProductName = operation.getParameter("product_name");
        if (testProductName != null && !testProductName.isEmpty()) {
            LOGGER.info("Received product_name parameter: " + testProductName);
            return updateServiceNameByProductName(testProductName);
        }
        return true;
    }

    public static boolean updateServiceNameByProductName(String testProductName) {
        try {
            // Read service name from product-setup.conf
            String configFilePath = getToolServerHome() + PRODUCT_SETUP_CONF;
            String serviceName = null;
            try {
                String jsonContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(configFilePath)), java.nio.charset.StandardCharsets.UTF_8);
                org.json.JSONObject jsonObject = new org.json.JSONObject(jsonContent);

                // Find product key (case-insensitive)
                String matchedProduct = null;
                for (String key : jsonObject.keySet()) {
                    if (key.equalsIgnoreCase(testProductName)) {
                        matchedProduct = key;
                        LOGGER.info("Matched product key in config: " + matchedProduct);
                        break;
                    }
                }
                if (matchedProduct != null) {
                    org.json.JSONObject productConfig = jsonObject.getJSONObject(matchedProduct);
                    if (productConfig.has("serviceName")) {
                        LOGGER.info("Found serviceName key in product config");
                        serviceName = productConfig.getString("serviceName");
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error reading service_name from product-setup.conf", e);
            }

            if (serviceName != null && !serviceName.isEmpty()) {
                File setupFile = new File(getToolServerHome() + TEST_SETUP_DETAILES);
                try (FileWriter writer = new FileWriter(setupFile)) {
                    writer.write("service_name = " + serviceName);
                    LOGGER.info("Saved service name to TEST_SETUP_DETAILES file");
                    return true;
                }
            } else {
                LOGGER.warning("Service name not found for product: " + testProductName);
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing service name for product", e);
            return false;
        }
    }


}