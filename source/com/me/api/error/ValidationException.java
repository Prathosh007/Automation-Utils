package com.me.api.error;

import java.util.List;
import java.util.Map;

/**
 * Exception thrown when validation fails
 */
public class ValidationException extends RuntimeException {

    private Object errors;
    
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }
    
    public ValidationException(String message, Map<String, Object> errors) {
        super(message);
        this.errors = errors;
    }
    
    public Object getErrors() {
        return errors;
    }
}
