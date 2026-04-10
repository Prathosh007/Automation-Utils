package com.me.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.me.api.model.GoatApiResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global exception handler for consistent error responses across the API
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionHandler.class.getName());
    
    /**
     * Master override for the handleExceptionInternal method to ensure consistent response format 
     * for all exceptions handled by the parent ResponseEntityExceptionHandler
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, 
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        
        String message = "An error occurred processing your request: " + ex.getMessage();
        if (ex instanceof HttpMessageNotReadableException) {
            message = "Invalid request format: " + ex.getMessage();
        }
        
        LOGGER.log(Level.WARNING, message, ex);
        
        GoatApiResponse<String> response = new GoatApiResponse<>(false, message, null);
        
        // Always return HTTP 200 OK for a consistent client-side experience
        return ResponseEntity.ok(response);
    }
    
    /**
     * Properly override the parent method for handling HttpMessageNotReadableException
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, 
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        
        LOGGER.log(Level.WARNING, "Invalid request format", ex);
        
        GoatApiResponse<String> response = new GoatApiResponse<>(false, 
            "Invalid request format: " + ex.getMessage(), null);
            
        return ResponseEntity.ok(response);
    }
    
    /**
     * Handle JSON processing exceptions
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<Object> handleJsonProcessingException(JsonProcessingException ex, WebRequest request) {
        LOGGER.log(Level.SEVERE, "JSON processing error", ex);
        
        GoatApiResponse<String> response = new GoatApiResponse<>(false, 
            "Error processing JSON: " + ex.getMessage(), null);
            
        // Always return HTTP 200 with error details in the body
        return ResponseEntity.ok(response);
    }
    
    /**
     * Handle general exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
        LOGGER.log(Level.SEVERE, "Unhandled exception", ex);
        
        GoatApiResponse<String> response = new GoatApiResponse<>(false, 
            "An unexpected error occurred: " + ex.getMessage(), null);
            
        // Always return HTTP 200 OK with error details in the response body
        // This ensures the client gets a proper response rather than HTTP 500
        return ResponseEntity.ok(response);
    }
    
    /**
     * Handle not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        LOGGER.log(Level.WARNING, "Resource not found", ex);
        
        GoatApiResponse<String> response = new GoatApiResponse<>(false, ex.getMessage(), null);
        
        // Always return HTTP 200 OK instead of 404 for consistent client handling
        return ResponseEntity.ok(response);
    }
    
    /**
     * Handle validation exceptions
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidationException(ValidationException ex, WebRequest request) {
        LOGGER.log(Level.WARNING, "Validation error", ex);
        
        GoatApiResponse<Object> response = new GoatApiResponse<>(false, 
            "Validation error", ex.getErrors());
            
        // Always return HTTP 200 OK instead of 400 for consistent client handling
        return ResponseEntity.ok(response);
    }
}
