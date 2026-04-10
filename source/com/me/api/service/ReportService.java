package com.me.api.service;

import com.me.util.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.me.api.error.ResourceNotFoundException;

import javax.annotation.PostConstruct;

/**
 * Service for report operations
 */
@Service
@DependsOn("logManager")
public class ReportService {
    
    private static Logger LOGGER;

    @Value("${goat.reports.path:../logs/reports}")
    private String reportsPath;
    
    /**
     * Get all available reports
     * 
     * @return List of report information
     */
    public List<Map<String, Object>> getAllReports() {
        LOGGER.info("Getting all reports from: " + reportsPath);
        
        List<Map<String, Object>> reports = new ArrayList<>();
        Path dir = Paths.get(reportsPath);
        
        try {
            if (Files.exists(dir)) {
                List<Path> files = Files.list(dir)
                    .filter(path -> path.toString().endsWith(".html") || path.toString().endsWith(".json"))
                    .collect(Collectors.toList());
                
                for (Path file : files) {
                    // Extract metadata from filename or file content
                    Map<String, Object> reportInfo = getReportMetadata(file);
                    reports.add(reportInfo);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error listing reports", e);
        }
        
        return reports;
    }
    
    /**
     * Get report by ID
     * 
     * @param id Report ID
     * @return Report information
     * @throws ResourceNotFoundException if report not found
     */
    public Map<String, Object> getReportById(String id) {
        LOGGER.info("Getting report with ID: " + id);
        
        Path reportPath = findReportPath(id);
        
        if (reportPath == null) {
            throw new ResourceNotFoundException("Report", id);
        }
        
        return getReportMetadata(reportPath);
    }
    
    /**
     * Get HTML report by ID
     * 
     * @param id Report ID
     * @return Report resource
     * @throws ResourceNotFoundException if report not found
     */
    public Resource getHtmlReport(String id) throws IOException {
        LOGGER.info("Getting HTML report with ID: " + id);
        
        Path reportPath = findReportPath(id);
        
        if (reportPath == null || !reportPath.toString().endsWith(".html")) {
            // Try for a report with html extension
            reportPath = findReportPath(id + ".html");
            
            if (reportPath == null) {
                throw new ResourceNotFoundException("HTML Report", id);
            }
        }
        
        return new UrlResource(reportPath.toUri());
    }
    
    /**
     * Find a report file by ID
     * 
     * @param id Report ID
     * @return Path to the report file or null if not found
     */
    private Path findReportPath(String id) {
        Path dir = Paths.get(reportsPath);
        
        try {
            if (Files.exists(dir)) {
                // Look for exact match first
                Path exactPath = dir.resolve(id);
                if (Files.exists(exactPath)) {
                    return exactPath;
                }
                
                // Then look for files containing the ID
                return Files.list(dir)
                    .filter(path -> path.getFileName().toString().contains(id))
                    .findFirst()
                    .orElse(null);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error finding report: " + id, e);
        }
        
        return null;
    }
    
    /**
     * Extract metadata from a report file
     * 
     * @param reportPath Path to the report file
     * @return Metadata as map
     */
    private Map<String, Object> getReportMetadata(Path reportPath) {
        Map<String, Object> metadata = new HashMap<>();
        
        String filename = reportPath.getFileName().toString();
        metadata.put("id", filename);
        
        // Extract date from filename if it matches pattern like report_20250331_123456
        Pattern pattern = Pattern.compile("report_(\\d{8})_(\\d{6})");
        Matcher matcher = pattern.matcher(filename);
        if (matcher.find()) {
            String dateStr = matcher.group(1);
            String timeStr = matcher.group(2);
            metadata.put("date", dateStr.substring(0, 4) + "-" + 
                         dateStr.substring(4, 6) + "-" + 
                         dateStr.substring(6, 8));
            metadata.put("time", timeStr.substring(0, 2) + ":" + 
                         timeStr.substring(2, 4) + ":" + 
                         timeStr.substring(4, 6));
        } else {
            // Use file modification date if pattern doesn't match
            try {
                metadata.put("lastModified", Files.getLastModifiedTime(reportPath).toString());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error getting file modification time", e);
            }
        }
        
        metadata.put("size", reportPath.toFile().length());
        metadata.put("type", reportPath.toString().endsWith(".html") ? "HTML" : "JSON");
        
        return metadata;
    }

    @PostConstruct
    public void init() {
        System.out.println("POST INIT OF Report Service Called");
        LOGGER = LogManager.getLogger(ReportService.class.getName(), LogManager.LOG_TYPE.FW);
    }
}
