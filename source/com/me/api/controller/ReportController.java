package com.me.api.controller;

import com.me.api.model.GoatApiResponse;
import com.me.api.service.ReportService;

// Import Swagger annotations properly
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST Controller for test report operations
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "Reports", description = "Test report operations")
public class ReportController {
    
    private static final Logger LOGGER = Logger.getLogger(ReportController.class.getName());
    
    @Autowired
    private ReportService reportService;
    
    /**
     * Get a list of available reports
     */
    @GetMapping
    @Operation(summary = "List all reports", description = "Get a list of all available test reports")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reports retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> getAllReports() {
        try {
            List<Map<String, Object>> reports = reportService.getAllReports();
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Reports retrieved successfully", reports));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving reports", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoatApiResponse<>(false, "Error retrieving reports: " + e.getMessage(), null));
        }
    }
    
    /**
     * Get a specific test report
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get report by ID", description = "Get a specific test report by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Report retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Report not found"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> getReportById(
            @Parameter(description = "Report ID", required = true)
            @PathVariable String id) {
        try {
            Map<String, Object> report = reportService.getReportById(id);
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Report retrieved successfully", report));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving report: " + id, e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoatApiResponse<>(false, "Error retrieving report: " + e.getMessage(), null));
        }
    }
    
    /**
     * Download a HTML report
     */
    @GetMapping("/{id}/html")
    @Operation(summary = "Download HTML report", description = "Download an HTML report for a specific test run")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "HTML report downloaded successfully"),
        @ApiResponse(responseCode = "404", description = "Report not found"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<?> downloadHtmlReport(
            @Parameter(description = "Report ID", required = true)
            @PathVariable String id) {
        try {
            Resource reportResource = reportService.getHtmlReport(id);
            
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + "\"")
                .body(reportResource);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error downloading HTML report: " + id, e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoatApiResponse<>(false, "Error downloading HTML report: " + e.getMessage(), null));
        }
    }
}
