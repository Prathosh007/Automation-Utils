package com.me.api.adapter;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.Collections;
import com.me.Operation;

/**
 * Helper class for TestCaseAdapter to handle operations that might not be
 * directly available in the core Operation class
 */
public class TestCaseAdapterHelper {

    /**
     * Extract all parameters from an Operation object
     * using reflection to avoid changing core classes
     * 
     * @param operation The core Operation object
     * @return Map of parameter names to values
     */
    public static Map<String, Object> getOperationParameters(Operation operation) {
        Map<String, Object> parameters = new HashMap<>();
        
        try {
            // Try to get parameters via getAllParameters method if it exists
            try {
                Method getAllParametersMethod = operation.getClass().getMethod("getAllParameters");
                if (getAllParametersMethod != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> params = (Map<String, String>) getAllParametersMethod.invoke(operation);
                    if (params != null) {
                        parameters.putAll(params);
                    }
                    return parameters;
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, fall through to alternative approach
            }
            
            // Try alternative approach - get parameters via reflection on fields
            // This is used if there's no getAllParameters() method
            return getParametersByReflection(operation);
            
        } catch (Exception e) {
            // Log error but return empty map rather than failing
            System.err.println("Error getting operation parameters: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    /**
     * Get parameters using reflection on the Operation object's fields
     */
    private static Map<String, Object> getParametersByReflection(Operation operation) {
        Map<String, Object> parameters = new HashMap<>();
        
        try {
            // If there's a parameters field, try to access it
            try {
                java.lang.reflect.Field paramsField = operation.getClass().getDeclaredField("parameters");
                paramsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, String> params = (Map<String, String>) paramsField.get(operation);
                if (params != null) {
                    parameters.putAll(params);
                }
            } catch (NoSuchFieldException e) {
                // Field doesn't exist, use the individual parameter getter
            }
        } catch (Exception e) {
            System.err.println("Error accessing parameters via reflection: " + e.getMessage());
        }
        
        return parameters;
    }
    
    /**
     * Set all parameters on an Operation object
     * 
     * @param operation The core Operation object
     * @param parameters Map of parameters to set
     */
    public static void setOperationParameters(Operation operation, Map<String, Object> parameters) {
        if (parameters == null) return;
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() != null) {
                operation.setParameter(entry.getKey(), entry.getValue().toString());
            } else {
                operation.setParameter(entry.getKey(), null);
            }
        }
    }
}
