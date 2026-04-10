package com.me.api.controller;

import org.springframework.http.ResponseEntity;
import com.me.api.model.GoatApiResponse;

/**
 * Base controller with common functionality for all API controllers
 */
public abstract class BaseController {
    
    /**
     * Check if Swagger classes are available
     */
    protected static boolean isSwaggerAvailable() {
        try {
            Class.forName("io.swagger.v3.oas.annotations.Operation");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Create a success response with data
     */
    protected <T> ResponseEntity<GoatApiResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(new GoatApiResponse<>(true, message, data));
    }
    
    /**
     * Create an error response
     */
    protected <T> ResponseEntity<GoatApiResponse<T>> error(int status, String message) {
        return ResponseEntity.status(status)
            .body(new GoatApiResponse<>(false, message, null));
    }
}
