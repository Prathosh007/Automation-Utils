package com.me.util;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Log manager for G.O.A.T framework
 * Centralizes logging configuration and provides common logging methods
 */
public class LogManager {
    private static final String DEFAULT_LOG_PATH = "../log/framework";
    private static final int MAX_LOG_FILES = 10;
    private static final int MAX_LOG_SIZE = 10 * 1024 * 1024; // 10 MB


    //Custom
    private static final Logger FRAMEWORK_LOGGER = Logger.getLogger("goat.framework");
    private static final Logger ADAPTER_LOGGER = Logger.getLogger("goat.api.adapter");
    private static final Logger API_LOGGER = Logger.getLogger("goat.api");
    private static final Logger GUI_LOGGER = Logger.getLogger("goat.gui");
    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    private static LOG_TYPE defaultLogType;


    // Change Map key type from Class to String since we're storing loggers by name
    private static final Map<String, Logger> componentLoggers = new HashMap<>();
    private static List<LOG_TYPE> initializedLogTypes = new LinkedList<>();

    private static HashMap<LOG_TYPE,FileHandler> fileHandlerMap = new LinkedHashMap<>();
    private static String logPath = System.getProperty("goat.app.home","..")+File.separator+"logs"+File.separator;
    private static HashMap<LOG_TYPE,Level> logLevelMap = new LinkedHashMap<>();
    private static boolean initialized = false;

    public LogManager() {
        System.out.println("Log Path :: "+logPath);
    }

    public enum LOG_TYPE{
        FW,
        API,
        GUI
    }

    public static void setDefaultLogLevels(HashMap<LOG_TYPE,Level> defaultLogLevelMap){
        logLevelMap = defaultLogLevelMap;
    }

    /**
     * Initialize the logging system with the specified log directory
     * 
     * @param logDirectory Directory where log files will be stored
     */
    public static synchronized void initialize(List<LOG_TYPE> logTypes,LOG_TYPE _defaultLogType) {
        if (initialized) {
            return;
        }

        defaultLogType = _defaultLogType;

        try {
            for (LOG_TYPE logType : logTypes) {
                initialize(logType,false);
            }
        }catch (Exception e){
            System.err.println("Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private static synchronized void initialize(List<LOG_TYPE> logTypes,LOG_TYPE _defaultLogType,boolean forceReinitialize) {
        if (initialized) {
            return;
        }

        defaultLogType = _defaultLogType;

        try {
            for (LOG_TYPE logType : logTypes) {
                initialize(logType,forceReinitialize);
            }
        }catch (Exception e){
            System.err.println("Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }

    }

    public static synchronized void initialize(LOG_TYPE logType,boolean forceReinitialize) {

        if (initializedLogTypes.contains(logType) && !forceReinitialize){
            System.err.println("Logger For This Type Already Initialized");
        }

        try {
            switch (logType) {
                case FW:
                    initializeFWLogging();
                    if (!forceReinitialize) {
                        initializedLogTypes.add(LOG_TYPE.FW);
                    }
                    break;
                case API:
                    initializeApiLogging();
                    if (!forceReinitialize){
                        initializedLogTypes.add(LOG_TYPE.API);
                    }
                    break;
                case GUI:
                    initializeGUILogging();
                    if (!forceReinitialize) {
                        initializedLogTypes.add(LOG_TYPE.GUI);
                    }
                    break;
            }
            System.out.println("Log Manager Initialization Successful");
        }catch (Exception e){
            System.err.println("Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private static void  initializeFWLogging() throws IOException {
            Level fwLogLevel = logLevelMap.get(LOG_TYPE.FW);

            // Create log directory if it doesn't exist
            File logDir = new File(logPath);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // Set up file handler for framework logs with rotation
            FileHandler fileHandler = new FileHandler(logPath + "/framework_%g.log", MAX_LOG_SIZE, MAX_LOG_FILES, true);
            fileHandler.setFormatter(new LogFormatter());
            fileHandler.setLevel(fwLogLevel);

            fileHandlerMap.put(LOG_TYPE.FW,fileHandler);

            // Configure framework logger
            FRAMEWORK_LOGGER.setUseParentHandlers(false);

            // Remove any existing handlers
            for (Handler handler : FRAMEWORK_LOGGER.getHandlers()) {
                FRAMEWORK_LOGGER.removeHandler(handler);
            }

            // Add our handlers
            FRAMEWORK_LOGGER.addHandler(fileHandler);

            // Add console handler for local development if needed
            if (isDevMode()) {
                ConsoleHandler consoleHandler = new ConsoleHandler();
                consoleHandler.setFormatter(new LogFormatter());
                consoleHandler.setLevel(fwLogLevel);
                FRAMEWORK_LOGGER.addHandler(consoleHandler);
            }

            FRAMEWORK_LOGGER.setLevel(fwLogLevel);

            // Log initialization message
            FRAMEWORK_LOGGER.info("G.O.A.T Framework logging initialized. Log files: " + logPath);
            initialized = true;
    }

    /**
     * Initialize API-specific logging
     */
    private static void initializeApiLogging() throws Exception {
        Level apiLogLevel = logLevelMap.get(LOG_TYPE.API);

        // Create logs directory if it doesn't exist
        File logDir = new File(logPath);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // Set up file handler for detailed API logs
        FileHandler fileHandler = new FileHandler(logPath + "/goat_api_%g.log", 10485760, 5, true);
        fileHandler.setFormatter(new SimpleFormatter());

        fileHandlerMap.put(LOG_TYPE.API,fileHandler);

        // Set log level based on config
        API_LOGGER.setLevel(apiLogLevel);
        ADAPTER_LOGGER.setLevel(apiLogLevel);

        // Remove existing handlers and add our custom ones
        for (Handler handler : API_LOGGER.getHandlers()) {
            API_LOGGER.removeHandler(handler);
        }

        API_LOGGER.addHandler(fileHandler);
        ADAPTER_LOGGER.addHandler(fileHandler);

        // Set API logger to use parent handlers (which includes console)
        API_LOGGER.setUseParentHandlers(true);

        API_LOGGER.info("API Logging initialized. Log files will be written to: " + logDir + "/goat_api.log");
    }

    private static void  initializeGUILogging() throws IOException {
        Level fwLogLevel = logLevelMap.get(LOG_TYPE.GUI);

        // Create log directory if it doesn't exist
        File logDir = new File(logPath);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // Set up file handler for framework logs with rotation
        FileHandler fileHandler = new FileHandler(logPath + "/gui_%g.log", MAX_LOG_SIZE, MAX_LOG_FILES, true);
        fileHandler.setFormatter(new LogFormatter());
        fileHandler.setLevel(fwLogLevel);

        fileHandlerMap.put(LOG_TYPE.GUI,fileHandler);

        // Configure framework logger
        GUI_LOGGER.setUseParentHandlers(false);

        // Remove any existing handlers
        for (Handler handler : GUI_LOGGER.getHandlers()) {
            GUI_LOGGER.removeHandler(handler);
        }

        // Add our handlers
        GUI_LOGGER.addHandler(fileHandler);

        // Add console handler for local development if needed
        if (isDevMode()) {
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new LogFormatter());
            consoleHandler.setLevel(fwLogLevel);
            GUI_LOGGER.addHandler(consoleHandler);
        }

        GUI_LOGGER.setLevel(fwLogLevel);

        // Log initialization message
        GUI_LOGGER.info("G.O.A.T GUI logging initialized. Log files: " + logPath);
        initialized = true;
    }

    public static void setDefaultLogType(LOG_TYPE defaultLogType) {
        LogManager.defaultLogType = defaultLogType;
    }

    /**
     * Set the log level for all loggers
     * 
     * @param level The log level to set
     */
    public static synchronized void setLogLevel(LOG_TYPE logType,Level level) {
        logLevelMap.put(logType,level);
        
        // Initialize if not already done
        if (!initialized) {
           throw new RuntimeException("Log Manager Not Initialized");
        }
        
        // Update framework logger
        FRAMEWORK_LOGGER.setLevel(level);
        
        // Update file handler
        if (!fileHandlerMap.isEmpty()) {
            for (LOG_TYPE _logType : fileHandlerMap.keySet()){
                fileHandlerMap.get(_logType).setLevel(level);
            }
        }
        
        // Update component loggers
        for (Logger logger : componentLoggers.values()) {
            logger.setLevel(level);
        }
        
        FRAMEWORK_LOGGER.info("Log level changed to: " + level.getName());
    }

    public static synchronized Logger getLogger(String loggerName,LOG_TYPE logType) {
        return getLogger(loggerName,logType,false);
    }

    /**
     * Get or create a logger for a specific component
     * 
     * @param loggerName The component name
     * @return Logger for the component
     */
    public static synchronized Logger getLogger(String loggerName,LOG_TYPE logType,boolean getDefaultLoggerIfNotDefined) {
        if (!initialized) {
            throw new RuntimeException("Log Manager Not Initialized");
        }
        
        if (!componentLoggers.containsKey(loggerName)) {
            System.err.println("Creating New Logger :; "+getLoggerPrefix(logType)+loggerName);
            Logger logger = Logger.getLogger(getLoggerPrefix(logType)+loggerName);

            //If The Provided Log Type Is Not loaded In  This  JVM , Use Default log Type
            if (!precheckLogType(logType)){
                logType = defaultLogType;
            }

            logger.setUseParentHandlers(false);
            logger.setLevel(logLevelMap.get(logType));

            // Remove existing handlers
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }

            if (fileHandlerMap.get(logType) == null){
                logger.addHandler(fileHandlerMap.get(defaultLogType));
            }else {
                logger.addHandler(fileHandlerMap.get(logType));
            }
            componentLoggers.put(loggerName, logger);
        }else {
            System.err.println("Existing Logger Name :: "+getLoggerPrefix(logType)+loggerName);
            System.err.println("Existing Logger Handler Count :: "+componentLoggers.get(loggerName).getHandlers().length);
            //If Handler Are Removed Due To Logger Read Config
            if (componentLoggers.get(loggerName).getHandlers().length == 0){
                initialized = false;
                initialize(initializedLogTypes,defaultLogType,true);
                componentLoggers.get(loggerName).addHandler(fileHandlerMap.get(logType));
            }
        }
        return componentLoggers.get(loggerName);
    }
    
    /**
     * Get or create a logger for a specific class
     * 
     * @param clazz The class to create a logger for
     * @return Logger for the class
     */
    public static synchronized Logger getLogger(Class<?> clazz,LOG_TYPE logType) {
        // Use the class name as the string key instead of using the Class directly
        return getLogger(clazz.getName(),logType);
    }

    /**
     * Get the current log level
     * 
     * @return Current log level
     */
    public static Level getLogLevel(LOG_TYPE logType) {
        return logLevelMap.get(logType);
    }
    
    /**
     * Get the path where log files are stored
     * 
     * @return Log directory path
     */
    public static String getLogPath() {
        return logPath;
    }
    
    /**
     * Check if we're in development mode
     * 
     * @return true if in development mode
     */
    private static boolean isDevMode() {
        return System.getProperty("goat.devmode", "false").equalsIgnoreCase("true");
    }
    
    /**
     * Custom formatter for log messages
     */
    private static class LogFormatter extends Formatter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(dateFormat.format(new Date(record.getMillis())))
              .append(" [").append(record.getThreadID()).append("] ")
              .append(record.getLevel().getName()).append(" ")
              .append(record.getLoggerName()).append(" - ")
              .append(formatMessage(record)).append("\n");
              
            if (record.getThrown() != null) {
                try {
                    sb.append(formatException(record.getThrown()));
                } catch (Exception ex) {
                    sb.append("Error formatting exception: ").append(ex.getMessage()).append("\n");
                }
            }
            
            return sb.toString();
        }
        
        private String formatException(Throwable thrown) {
            StringBuilder sb = new StringBuilder();
            sb.append(thrown.getClass().getName()).append(": ").append(thrown.getMessage()).append("\n");
            
            for (StackTraceElement element : thrown.getStackTrace()) {
                sb.append("    at ").append(element.toString()).append("\n");
            }
            
            Throwable cause = thrown.getCause();
            if (cause != null) {
                sb.append("Caused by: ").append(formatException(cause));
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Perform cleanup of logging resources
     */
    public static synchronized void shutdown() {
        if (!fileHandlerMap.isEmpty()) {
            for (LOG_TYPE logType : fileHandlerMap.keySet()) {
                fileHandlerMap.get(logType).close();
            }
        }
        
        for (Logger logger : componentLoggers.values()) {
            for (Handler handler : logger.getHandlers()) {
                handler.close();
            }
        }
        
        initialized = false;
    }

    private static String getLoggerPrefix(LOG_TYPE logType){
        switch (logType){
            case FW:
                return "goat.framework.";
            case API:
                return "goat.api.";
            case GUI:
                return "goat.gui.";
            default:
                throw new IllegalStateException("Unexpected value: " + logType);
        }
    }

    private static boolean precheckLogType(LOG_TYPE logType){
        return initializedLogTypes.contains(logType);
    }
}
