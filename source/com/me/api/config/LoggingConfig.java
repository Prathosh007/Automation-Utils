package com.me.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.annotation.PostConstruct;

import com.me.util.LogManager;

/**
 * Configures detailed logging for the API
 */
@Configuration
public class LoggingConfig {

    @Value("${logging.level.goat.fw:DEBUG}")
    private String fwLogLevel;

    @Value("${logging.level.goat.api:DEBUG}")
    private String apiLogLevel;
    
    @Value("${logging.file.path:../log/api}")
    private String guiFilePath;
    
    @Value("${goat.framework.log.path:../log}")
    private String logPath;

    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    /**
     * Initialize the logging configuration
     */
    @Bean(name = "logManager")
    public Object logManager() {
        try {

            System.out.println("Logger Initialization Called");


            //Default Log Level
            HashMap<LogManager.LOG_TYPE,Level> defaultLogLevelMap = new LinkedHashMap<>();
            defaultLogLevelMap.put(LogManager.LOG_TYPE.FW,getJavaUtilLogLevel(fwLogLevel));
            defaultLogLevelMap.put(LogManager.LOG_TYPE.API,getJavaUtilLogLevel(apiLogLevel));
            defaultLogLevelMap.put(LogManager.LOG_TYPE.GUI,getJavaUtilLogLevel(guiFilePath));

            //Set Default Log Levels In Custom LogManager
            LogManager.setDefaultLogLevels(defaultLogLevelMap);

            LogManager.initialize(Arrays.asList(LogManager.LOG_TYPE.FW, LogManager.LOG_TYPE.API), LogManager.LOG_TYPE.FW);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }
        return new Object();
    }

    /**
     * Get the path where log files are stored
     */
    @Bean
    public String logPath() {
        return logPath;
    }

    private Level getJavaUtilLogLevel(String levelStr) {
        switch (levelStr.toUpperCase()) {
            case "TRACE":
                return Level.FINEST;
            case "DEBUG":
                return Level.FINE;
            case "INFO":
                return Level.INFO;
            case "WARN":
                return Level.WARNING;
            case "ERROR":
                return Level.SEVERE;
            default:
                return Level.INFO; // Default fallback
        }
    }
}
