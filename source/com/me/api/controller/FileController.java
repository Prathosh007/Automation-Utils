package com.me.api.controller;

import com.me.api.model.GoatApiResponse;
import com.me.testcases.ServerUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Controller for file upload and download operations
 */
@RestController
@RequestMapping("/files")
@Tag(name = "Files", description = "File upload and download endpoints")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private File getUploadDir() {
        String toolHome = ServerUtils.getToolServerHome();
        File dir = new File(toolHome, "UploadFiles");
        logger.info("Upload directory: {}", dir);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a file", description = "Uploads a file to the UploadFiles directory. Overwrites if exists.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
        @ApiResponse(responseCode = "500", description = "Upload failed")
    })
    public ResponseEntity<GoatApiResponse<String>> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file) {
        logger.info("Received upload request for file: {}", file.getOriginalFilename());
        try {
            File uploadDir = getUploadDir();
            logger.info("Upload directory: {}", uploadDir.getAbsolutePath());
            File dest = new File(uploadDir, file.getOriginalFilename());
            // Overwrite if exists
            file.transferTo(dest);
            String absolutePath = dest.getAbsolutePath();
            logger.info("File uploaded successfully to: {}", absolutePath);
            return ResponseEntity.ok(
                new GoatApiResponse<>(true, "File uploaded successfully", absolutePath)
            );
        } catch (Exception e) {
            logger.error("Upload failed for file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GoatApiResponse<>(false, "Upload failed: " + e.getMessage(), null));
        }
    }

    @GetMapping("/list")
    @Operation(summary = "List uploaded files", description = "Lists all files in the UploadFiles directory.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Files listed successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to list files")
    })
    public ResponseEntity<GoatApiResponse<List<String>>> listFiles() {
        logger.info("Received request to list files");
        try {
            File uploadDir = getUploadDir();
            String[] files = uploadDir.list();
            if (files == null) files = new String[0];
            logger.info("Files found: {}", Arrays.toString(files));
            return ResponseEntity.ok(
                new GoatApiResponse<>(true, "Files listed successfully", Arrays.asList(files))
            );
        } catch (Exception e) {
            logger.error("Failed to list files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GoatApiResponse<>(false, "Failed to list files: " + e.getMessage(), null));
        }
    }

    @GetMapping("/download")
    @Operation(summary = "Download a file", description = "Downloads a file from the UploadFiles directory.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "500", description = "Download failed")
    })
    public ResponseEntity<?> downloadFile(
            @Parameter(description = "Filename to download", required = true)
            @RequestParam("filename") String filename) {
        logger.info("Received download request for file: {}", filename);
        File uploadDir = getUploadDir();
        File file = new File(uploadDir, filename);
        if (!file.exists()) {
            logger.warn("File not found: {}", file.getAbsolutePath());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new GoatApiResponse<>(false, "File not found", null));
        }
        try {
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            logger.info("File download streaming: {}", file.getAbsolutePath());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .contentLength(file.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            logger.error("Download failed for file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GoatApiResponse<>(false, "Download failed: " + e.getMessage(), null));
        }
    }
}



