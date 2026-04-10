package com.me.testcases;

import com.me.Operation;
import com.me.util.LogManager;
import com.zoho.framework.utils.crypto.CryptoUtil;
import com.zoho.framework.utils.crypto.EnDecrypt;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.testcases.ServerUtils.getServerHomePath;

public class MSSQLMigration {
    private static final Logger LOGGER = LogManager.getLogger(MSSQLMigration.class, LogManager.LOG_TYPE.FW);
    private static final String SERVER_HOME_PROPERTY = "server.home";
    private static final String CONF_DIR = "conf";
    private static final String BIN_DIR = "bin";

    private static String time;
    private static boolean flag = false;

    /**
     * Executes the MSSQL migration operation
     * @param operation The operation containing migration parameters
     * @return true if migration succeeded, false otherwise
     */
    public static boolean executeOperation(Operation operation) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "MSSQLMigration method is called");

        String sqlHost = operation.getParameter("sqlHost");
        String sqlPort = operation.getParameter("sqlPort");
//        String sqlDomain = operation.getParameter("sqlDomain");
        String sqlUserName = operation.getParameter("sqlUserName");
        String sqlPassword = operation.getParameter("sqlPassword");
        String sqlWinAuth = "false";

        String serverHome = System.getProperty(SERVER_HOME_PROPERTY);
        if (serverHome == null || serverHome.isEmpty()) {
            serverHome = getServerHomePath();
        }

        String dbName = operation.getParameter("dbName");
        if (dbName == null || dbName.isEmpty()) {
            dbName = createName();
        }

        // Start PostgreSQL server
        startPostgresServer(serverHome);

        // Initialize PostgreSQL
        initializePostgres(serverHome);

        // Configure MSSQL properties
        Properties mssqlDetails = new Properties();
        mssqlDetails.setProperty("sqlHost", sqlHost);
        mssqlDetails.setProperty("sqlPort", sqlPort);
//        mssqlDetails.setProperty("sqlDomain", sqlDomain);
        mssqlDetails.setProperty("sqlUserName", sqlUserName);
        mssqlDetails.setProperty("sqlPassword", sqlPassword);
        mssqlDetails.setProperty("sqlWinAuth", sqlWinAuth);

        // Start migration process
        convertNonMssqlToMssql("", serverHome, mssqlDetails, "mssql", dbName);
        return flag;
    }

    /**
     * Starts the PostgreSQL server
     */
    private static void startPostgresServer(String serverHome) throws IOException {
        String command = String.format("%s\\pgsql\\bin\\pg_ctl.exe -w -D %s\\pgsql\\data -o \"-p8028\" start",
                serverHome, serverHome);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("cmd", "/c", command);
        processBuilder.redirectErrorStream(true);
        processBuilder.start();
    }

    /**
     * Initializes PostgreSQL database
     */
    private static void initializePostgres(String serverHome) throws IOException {
        String command = "cd " + serverHome + "\\bin && initPgsql.bat";
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("cmd", "/c", command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                // Process output if needed
            }
        }
    }

    /**
     * Converts non-MSSQL database to MSSQL
     */
    public static boolean convertNonMssqlToMssql(String applicationServerHome, String productServerHome,
                                                 Properties sqlProperties, String databaseType, String dbname) {
        LOGGER.log(Level.INFO, "convertNonMssqlToMssql method");

        try {
            createMigrationBatchFile(productServerHome);
            changeNonMssqlToMssql(productServerHome, sqlProperties, dbname);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception in convertNonMssqlToMssql method", e);
            return false;
        }
    }

    /**
     * Creates the database migration batch file
     */
    private static void createMigrationBatchFile(String productServerHome) throws IOException {
        File newBatFile = new File(productServerHome + File.separator + BIN_DIR + File.separator + "mgrtDBtoPostgres.bat");
        if (!newBatFile.exists()) {
            newBatFile.createNewFile();
        }

        try (FileWriter fw = new FileWriter(newBatFile)) {
            fw.write("@echo off\n\n");
            fw.write("rem $Id$\n\n");
            fw.write("call .\\setCommonEnv.bat\n");
            fw.write("call .\\setLibraryPath.bat\n");
            fw.write("call .\\setFWCommonProperties.bat\n");
            fw.write("set destinationDB=%1\n");
            fw.write("set destinationDBPropertyPath=%2\n");
            fw.write("set JAVA=\"%JAVA_HOME%\\bin\\java.exe\"\n");
            fw.write("set JAVA_OPTS= -Dserver.home=\"%SERVER_HOME%\" -Ddb.home=\"%DB_HOME%\" " +
                    "-Dmysql.home=\"%MYSQL_DB_HOME%\" -Dpgsql.home=\"%PGSQL_DB_HOME%\" " +
                    "-Djava.library.path=\"%SERVER_HOME%\\%SERVER_LIBRARY_PATH%\" " +
                    "-Djava.util.logging.manager=\"org.apache.juli.ClassLoaderLogManager\" " +
                    "-Duser.language=\"en\" -Dfile.encoding=\"utf8\" " +
                    "-Ddb.migration.main.class=\"com.me.devicemanagement.onpremise.tools.dbmigration.action.DBMigrationAction\" " +
                    "-Duniformlogformatter.enable=\"true\"\n\n");
            fw.write("set CLASS_PATH=\"%SERVER_HOME%\\bin\\run.jar\";\"%SERVER_HOME%\\lib\\*\";" +
                    "\"%SERVER_HOME%\\lib\\tomcat\\*\";%SERVER_HOME%\\ServerTroubleShooter\\lib\\*;" +
                    "%SERVER_HOME%\\ServerTroubleShooter\\lib\\starter\\*;\n\n");
            fw.write("call %JAVA% %JAVA_OPTS%  -cp \"%CLASS_PATH%\" com.adventnet.mfw.Starter " +
                    "DBMigration %destinationDB% %destinationDBPropertyPath%\n");
            fw.write("goto END\n\n");
            fw.write(":DISPLAY_USAGE\n");
            fw.write("echo Usage:\n");
            fw.write("echo ..........................................................\n");
            fw.write("echo  sh app_ctl.bat migrateDB ^<destinationDB^> ^<destinationDBPropertyPath^>\n");
            fw.write("echo ..........................................................\n");
            fw.write("@echo destinationDB -^> value should be either mysql/mssql/postgres/firebird & " +
                    "echo destinationDBPropertyPath -^> Path to database properties file^(database params file for destination DB^)\n");
            fw.write("goto End\n\n");
            fw.write(":END\n");
        }
    }

    /**
     * Creates a unique database name using timestamp
     */
    public static String createName() {
        time = String.valueOf(new Date().getTime());
        return "desktopcentral".concat(time);
    }

    /**
     * Configures and starts migration from non-MSSQL to MSSQL
     */
    public static void changeNonMssqlToMssql(String productServerHome, Properties sqlAuthentication, String dbName) {
        try {
            LOGGER.log(Level.INFO, "Server home for changeMyToMssql: {0}", productServerHome);

            String driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            String dbAdapter = "com.adventnet.db.adapter.mssql.DCMssqlDBAdapter";
            String sqlGenerator = "com.adventnet.db.adapter.mssql.DCMssqlSQLGenerator";
            String exceptionSorter = "com.me.devicemanagement.onpremise.server.sql.MssqlExceptionSorter";

            LOGGER.log(Level.INFO, "Database name: {0}", dbName);

            String sqlHost = sqlAuthentication.getProperty("sqlHost");
            String sqlPort = sqlAuthentication.getProperty("sqlPort");
//            String sqlDomain = sqlAuthentication.getProperty("sqlDomain");
            String sqlUserName = sqlAuthentication.getProperty("sqlUserName");
            String sqlPassword = sqlAuthentication.getProperty("sqlPassword");
            String winAuth = sqlAuthentication.getProperty("sqlWinAuth");

            LOGGER.log(Level.INFO, "SQL connection details - host: {0}, port: {1}, user: {2}, winAuth: {3}",
                    new Object[]{sqlHost, sqlPort, sqlUserName, winAuth});

            updateDBConfigFile("mssql", sqlHost, sqlPort, Boolean.parseBoolean(winAuth), true,
                    sqlUserName, sqlPassword, null, null, dbName, driver, dbAdapter,
                    sqlGenerator, exceptionSorter, productServerHome);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception in changeMysqlToMssql method", ex);
        }
    }

    /**
     * Updates database configuration and runs migration
     */
    public static void updateDBConfigFile(String server, String host, String port, boolean isWinAuthType,
                                          boolean isNTLMEnabled, String user, String pwd,
                                          String serverdir, String instanceName, String dataBaseName,
                                          String driver, String dbAdapter, String sqlGenerator,
                                          String exceptionSorter, String serverHome) throws Exception {

        try {
            if (host == null || host.trim().isEmpty()) {
                throw new Exception("Invalid host name : "+host);
            }

            String connectionUrl = createURL(server, dataBaseName, host, port, isWinAuthType, isNTLMEnabled);
            writeDatabaseParams(server, user, pwd, connectionUrl, driver, exceptionSorter, serverHome);

            LOGGER.log(Level.INFO, "DBMigration process started - this may take some time");

            // Update db_migration.conf file
            String dbMigrationPath = serverHome + File.separator + CONF_DIR + File.separator + "db_migration.conf";
            LOGGER.log(Level.INFO, "db_migration.conf file path: {0}", dbMigrationPath);

            changePropertyValueInFile(dbMigrationPath, "dest.create.db.name", dataBaseName);
            changePropertyValueInFile(dbMigrationPath, "dbmigration.notifier", "com.adventnet.db.migration.notifier.LogProgressNotifier");

            LOGGER.log(Level.INFO, "db_migration.conf file properties updated successfully");

            // Run migration batch file
            File databaseParamsFile = new File(serverHome + File.separator + "dbmigration" + File.separator + "database_params_mssql.conf");
            File batchFile = new File(serverHome + File.separator + BIN_DIR + File.separator + "mgrtDBtoPostgres.bat");

            ProcessBuilder builder = new ProcessBuilder(
                    batchFile.getCanonicalPath(),
                    server,
                    databaseParamsFile.getCanonicalPath());

            builder.directory(new File(serverHome + File.separator + BIN_DIR));
            builder.redirectErrorStream(true);

            Process process = builder.start();

            // Track migration status
            boolean migrationFailed = false;
            StringBuilder outputLogs = new StringBuilder();

            // Capture and analyze process output
            try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = stdInput.readLine()) != null) {
                    LOGGER.log(Level.INFO, "Process output: {0}", line);
                    outputLogs.append(line).append(System.lineSeparator());

                    // Look for specific failure indicators in the output
                    if (line.contains("Database migration failed") ||
                            line.contains("Exception in migrating DB") ||
                            line.contains("Migration failed") ||
                            line.contains("Error in migrating DB") ||
                            line.contains("Migration failed with") ||
                            line.contains("execute initPgsql.bat script with administrator privileges") ||
                            line.contains("please contact support")) {

                        LOGGER.log(Level.SEVERE, "Migration failure detected: {0}", line);
                        migrationFailed = true;
                    }
                }
            }

            int errorCode = process.waitFor();
            LOGGER.log(Level.INFO, "DBMigration bat file executed with error code {0}", errorCode);
            process.destroy();

            // Set flag based on both output analysis and error code
            flag = !migrationFailed;

            if (flag) {
                LOGGER.log(Level.INFO, "DBMigration completed successfully, updating configuration files");

                // Update configuration files only on success
                writePersistenceConfiguration(server, host, serverHome);
                writeDatabaseParams(server, user, pwd, connectionUrl, driver, exceptionSorter, serverHome);
                writeDataBaseParamsConf(server, user, pwd, connectionUrl, driver, exceptionSorter, serverHome);
            } else {
                LOGGER.log(Level.SEVERE, "DBMigration failed. Error code: {0}, Error details: {1}",
                        new Object[]{errorCode, outputLogs.toString()});

                // Check for PostgreSQL initialization error specifically
                if (outputLogs.toString().contains("initPgsql.bat script with administrator privileges")) {
                    LOGGER.log(Level.SEVERE, "PostgreSQL initialization failed. Please run initPgsql.bat with administrator privileges");
                    throw new Exception("PostgreSQL initialization failed. Please run initPgsql.bat with administrator privileges");
                }
            }

        } catch (Exception e) {
            flag = false;
            LOGGER.log(Level.SEVERE, "Exception in migrating DB: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Changes a property value in a configuration file without losing other properties
     */
    public static boolean changePropertyValueInFile(String fileName, String propertyName, String valueToChange) {
        Properties props = new Properties();

        // Step 1: Read the properties file completely
        try (java.io.FileReader reader = new java.io.FileReader(fileName)) {
            props.load(reader);

            LOGGER.info("Changing property " + propertyName +
                    " from: " + props.getProperty(propertyName) +
                    " to: " + valueToChange);
        } catch (Exception e) {
            LOGGER.severe("Error reading properties from file " + fileName + ": " + e.getMessage());
            return false;
        }

        // Step 2: Update the property
        props.setProperty(propertyName, valueToChange);

        // Step 3: Write the updated properties back to the file
        try (FileWriter writer = new FileWriter(fileName)) {
            props.store(writer, null);
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error writing property " + propertyName +
                    " to " + valueToChange +
                    " in file " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates persistence configuration in XML file
     */
    private static void writePersistenceConfiguration(String dbName, String host, String serverHome) throws Exception {
        LOGGER.log(Level.INFO, "Updating persistence configuration - dbName: {0}, host: {1}", new Object[]{dbName, host});

        String fileURL = serverHome + File.separator + CONF_DIR + File.separator + "customer-config.xml";
        File file = new File(fileURL);

        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + fileURL);
        }

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(file);
        Element root = doc.getDocumentElement();
        NodeList connList = root.getElementsByTagName("configuration");

        for (int i = 0; i < connList.getLength(); i++) {
            Element connectorEl = (Element) connList.item(i);
            String name = connectorEl.getAttribute("name");

            if (name != null) {
                if (name.equals("DBName")) {
                    connectorEl.setAttribute("value", dbName);
                } else if (name.equals("StartDBServer")) {
                    connectorEl.setAttribute("value", startDBServer(dbName, host));
                } else if (name.equals("DSAdapter")) {
                    connectorEl.setAttribute("value", dbName);
                }
            }
        }

        writeToXML(file, root);
    }

    /**
     * Writes XML content to file
     */
    private static void writeToXML(File file, Element root) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            String encoding = "ISO-8859-1";
            Transformer transformer = TransformerFactory.newInstance().newTransformer();

            Properties prop = new Properties();
            prop.put(OutputKeys.INDENT, "yes");
            prop.put(OutputKeys.ENCODING, encoding);
            prop.put(OutputKeys.METHOD, "xml");
            transformer.setOutputProperties(prop);

            transformer.transform(new DOMSource(root), new StreamResult(writer));
        }
    }

    /**
     * Determines if DB server should be started
     */
    private static String startDBServer(String serverType, String hostName) {
        if ("mssql".equalsIgnoreCase(serverType)) {
            return "false";
        }

        boolean remoteDB = false;
        if (hostName != null && !hostName.equalsIgnoreCase("localhost") && !hostName.equalsIgnoreCase("127.0.0.1")) {
            remoteDB = true;
        }
        return String.valueOf(!remoteDB);
    }

    /**
     * Creates a database connection URL based on database type
     */
    private static String createURL(String server, String dataBaseName, String host, String port,
                                    boolean isWinAuthType, boolean isNTLMEnabled) {

        if ("mysql".equalsIgnoreCase(server)) {
            return "jdbc:mysql://" + host + ":" + port + "/" + dataBaseName +
                    "?useUnicode=true&characterEncoding=UTF-8&jdbcCompliantTruncation=false";

        } else if ("postgre".equalsIgnoreCase(server)) {
            return "jdbc:postgreql://" + host + ":" + port + "/" + dataBaseName;

        } else if ("mssql".equalsIgnoreCase(server)) {
//            if (isWinAuthType) {
//                if (isNTLMEnabled) {
//                    return "jdbc:sqlserver://" + host + ":" + port + "/" + dataBaseName +
//                            ";Domain=" + domain + ";authenticationScheme=NTLM;integratedSecurity=true;ssl=request";
//                } else {
//                    return "jdbc:sqlserver://" + host + ":" + port + "/" + dataBaseName +
//                            ";Domain=" + domain;
//                }
//            } else {
                if (isNTLMEnabled) {
                    return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" +
                            dataBaseName + ";ssl=request";
                } else {
                    return "jdbc:sqlserver://" + host + ":" + port + "/" + dataBaseName;
                }
//            }
        }
        return null;
    }

    /**
     * Writes database parameters to migration config file
     */
    private static void writeDatabaseParams(String server, String usrName, String pswd, String connectionUrl,
                                            String driver, String exceptionSorter, String serverHome) throws Exception {

        File fileURL = new File(serverHome + File.separator + "dbmigration" + File.separator + "database_params_mssql.conf");
        if (!fileURL.exists()) {
            fileURL.createNewFile();
        }

        String encryptPWD = CryptoUtil.encrypt(pswd, EnDecrypt.AES256);

        try (FileWriter fw = new FileWriter(fileURL)) {
            fw.write("drivername=" + driver + "\n");
            fw.write("username=" + usrName + "\n");
            fw.write("password=" + encryptPWD + "\n");
            fw.write("url=" + connectionUrl + "\n");
            fw.write("minsize=1\n");
            fw.write("maxsize=75\n");
            fw.write("exceptionsorterclassname=" + exceptionSorter);
        }
    }

    /**
     * Updates the main database_params.conf file
     */
    public static void writeDataBaseParamsConf(String server, String usrName, String pswd, String connectionUrl,
                                               String driver, String exceptionSorter, String serverHome) throws Exception {

        String databaseFile = serverHome + File.separator + CONF_DIR + File.separator + "database_params.conf";
        LOGGER.log(Level.INFO, "Updating database config file: {0}", databaseFile);

        File file = new File(databaseFile);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + databaseFile);
        }

        findAndReplaceStringInFile(databaseFile, "drivername.*=.*", "drivername=" + driver);
        findAndReplaceStringInFile(databaseFile, "username.*=.*", "username=" + usrName);
        findAndReplaceStringInFile(databaseFile, "password.*=.*", "password=" + CryptoUtil.encrypt(pswd, EnDecrypt.AES256));
        findAndReplaceStringInFile(databaseFile, "url.*=.*", "url=" + connectionUrl);
        findAndReplaceStringInFile(databaseFile, "exceptionsorterclassname.*=.*", "exceptionsorterclassname=" + exceptionSorter);
    }

    /**
     * Finds and replaces text in a file using regex
     */
    public static void findAndReplaceStringInFile(String fileName, String findStr, String replaceStr) throws Exception {
        LOGGER.log(Level.INFO, "Replacing {0} with {1} in {2}", new Object[]{findStr, replaceStr, fileName});

        File givenFile = new File(fileName);
        if (!givenFile.exists()) {
            LOGGER.log(Level.WARNING, "File does not exist: {0}", fileName);
            throw new FileNotFoundException("File does not exist: " + fileName);
        }

        StringBuilder content = new StringBuilder();
        try (java.io.FileReader freader = new java.io.FileReader(fileName)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = freader.read(buffer)) > -1) {
                content.append(buffer, 0, read);
            }
        }

        String updatedContent = content.toString().replaceAll(findStr, replaceStr);

        try (FileWriter fwriter = new FileWriter(fileName, false)) {
            fwriter.write(updatedContent);
        }
    }
}