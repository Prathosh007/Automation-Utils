package com.me.api.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.me.Operation;
import com.me.TestCase;
import com.me.api.model.OperationDTO;
import com.me.api.model.TestCaseDTO;

/**
 * Adapter for TestCase to avoid direct modification of core classes.
 * This class acts as a bridge between the API layer and the core framework.
 */
public class TestCaseAdapter {
    
    private TestCase coreTestCase;
    
    /**
     * Create a new adapter wrapping the core test case
     * 
     * @param coreTestCase The core TestCase object to adapt
     */
    public TestCaseAdapter(TestCase coreTestCase) {
        this.coreTestCase = coreTestCase;
    }
    
    /**
     * Create a new empty test case adapter
     */
    public TestCaseAdapter() {
        try {
            // Try creating with String constructor first since that's common
            try {
                Constructor<?> constructor = TestCase.class.getConstructor(String.class);
                this.coreTestCase = (TestCase) constructor.newInstance("api_test_" + System.currentTimeMillis());
                return;
            } catch (NoSuchMethodException e) {
                // String constructor not found, try alternatives
            }
            
            // Get all available constructors and use the one with the fewest parameters
            Constructor<?>[] constructors = TestCase.class.getConstructors();
            if (constructors.length > 0) {
                // Sort constructors by parameter count to find simplest one
                Constructor<?> simplestConstructor = constructors[0];
                for (Constructor<?> c : constructors) {
                    if (c.getParameterCount() < simplestConstructor.getParameterCount()) {
                        simplestConstructor = c;
                    }
                }
                
                // Create default arguments
                Object[] defaultArgs = new Object[simplestConstructor.getParameterCount()];
                for (int i = 0; i < defaultArgs.length; i++) {
                    Class<?> type = simplestConstructor.getParameterTypes()[i];
                    if (type == String.class) {
                        defaultArgs[i] = "api_test_" + System.currentTimeMillis();
                    } else if (type.isPrimitive()) {
                        if (type == boolean.class) defaultArgs[i] = false;
                        else if (type == int.class) defaultArgs[i] = 0;
                        else if (type == long.class) defaultArgs[i] = 0L;
                        else if (type == double.class) defaultArgs[i] = 0.0;
                        else if (type == float.class) defaultArgs[i] = 0.0f;
                    } else {
                        defaultArgs[i] = null;
                    }
                }
                
                this.coreTestCase = (TestCase) simplestConstructor.newInstance(defaultArgs);
            } else {
                throw new IllegalStateException("No constructors found for TestCase");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TestCase instance: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert a TestCaseDTO from the API to a core TestCase
     * 
     * @param dto The data transfer object from API
     * @return A TestCaseAdapter wrapping the core TestCase
     */
    public static TestCaseAdapter fromDTO(TestCaseDTO dto) {
        if (dto == null) {
            return null;
        }
        
        // Create a TestCase with appropriate constructor
        TestCase coreTestCase;
        try {
            // Try to find constructor with id parameter
            try {
                Constructor<?> constructor = TestCase.class.getConstructor(String.class);
                coreTestCase = (TestCase) constructor.newInstance(dto.getId());
            } catch (NoSuchMethodException e) {
                // Fall back to any available constructor
                Constructor<?>[] constructors = TestCase.class.getConstructors();
                if (constructors.length > 0) {
                    Object[] defaultArgs = new Object[constructors[0].getParameterCount()];
                    coreTestCase = (TestCase) constructors[0].newInstance(defaultArgs);
                } else {
                    throw new IllegalStateException("No constructors found for TestCase");
                }
            }
            
            // Set properties using reflection if direct setters are unavailable
            setPropertySafely(coreTestCase, "id", dto.getId());
            setPropertySafely(coreTestCase, "description", dto.getDescription());
            setPropertySafely(coreTestCase, "productName", dto.getProductName());
            setPropertySafely(coreTestCase, "reuseInstallation", dto.isReuseInstallation());
            setPropertySafely(coreTestCase, "expectedResult", dto.getExpectedResult());
            
            // Add operations using the available method signature
            if (dto.getOperations() != null) {
                for (OperationDTO opDto : dto.getOperations()) {
                    Operation operation = new Operation(opDto.getOperationType());
                    
                    // Set parameters
                    for (Map.Entry<String, Object> param : opDto.getParameters().entrySet()) {
                        if (param.getValue() != null) {
                            operation.setParameter(param.getKey(), param.getValue().toString());
                        }
                    }
                    
                    // Add operation using available method
                    addOperationSafely(coreTestCase, operation);
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TestCase from DTO", e);
        }
        
        return new TestCaseAdapter(coreTestCase);
    }
    
    /**
     * Set a property on an object using reflection, trying various methods
     */
    private static void setPropertySafely(Object obj, String propertyName, Object value) {
        // Skip if value is null
        if (value == null) return;
        
        try {
            // First try direct setter method
            String setterName = "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            try {
                Method setter = obj.getClass().getMethod(setterName, value.getClass());
                setter.invoke(obj, value);
                return;
            } catch (NoSuchMethodException e) {
                // Setter not found, try field access
            }
            
            // Try to access field directly
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(propertyName);
                field.setAccessible(true);
                field.set(obj, value);
            } catch (NoSuchFieldException e) {
                // Field not found, ignore
            }
        } catch (Exception e) {
            System.err.println("Failed to set property " + propertyName + ": " + e.getMessage());
        }
    }
    
    /**
     * Add an operation to a TestCase using available methods
     */
    private static void addOperationSafely(TestCase testCase, Operation operation) {
        try {
            // Try to find the appropriate addOperation method
            try {
                Method addOp = TestCase.class.getMethod("addOperation", Operation.class);
                addOp.invoke(testCase, operation);
                return;
            } catch (NoSuchMethodException e) {
                // Try other signatures
            }
            
            // Try alternative method signatures
            // For example: addOperation(String type, String param1, String param2...)
            try {
                Method addOp = TestCase.class.getMethod("addOperation", String.class);
                addOp.invoke(testCase, operation.getOperationType());
                
                // Now set parameters separately
                Map<String, Object> params = TestCaseAdapterHelper.getOperationParameters(operation);
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    Method setParam = TestCase.class.getMethod("setOperationParameter", 
                                                              String.class, String.class, String.class);
                    setParam.invoke(testCase, operation.getOperationType(), 
                                  entry.getKey(), entry.getValue().toString());
                }
                return;
            } catch (NoSuchMethodException e) {
                // Continue trying other approaches
            }
            
            // Try to access operations list directly
            try {
                java.lang.reflect.Field opsField = TestCase.class.getDeclaredField("operations");
                opsField.setAccessible(true);
                List<Operation> operations = (List<Operation>) opsField.get(testCase);
                if (operations == null) {
                    operations = new ArrayList<>();
                    opsField.set(testCase, operations);
                }
                operations.add(operation);
            } catch (Exception e) {
                throw new RuntimeException("Could not add operation to test case", e);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
        }
    }
    
    /**
     * Get the wrapped core TestCase
     * 
     * @return The core TestCase object
     */
    public TestCase getCoreTestCase() {
        return coreTestCase;
    }
    
    // Delegate methods to the core test case using reflection for safety
    public String getId() {
        return getPropertySafely(coreTestCase, "id", String.class);
    }
    
    public void setId(String id) {
        setPropertySafely(coreTestCase, "id", id);
    }
    
    public String getDescription() {
        return getPropertySafely(coreTestCase, "description", String.class);
    }
    
    public void setDescription(String description) {
        setPropertySafely(coreTestCase, "description", description);
    }
    
    /**
     * Get a property from an object using reflection, trying various methods
     */
    private static <T> T getPropertySafely(Object obj, String propertyName, Class<T> type) {
        try {
            // First try direct getter method
            String getterPrefix = (type == Boolean.class || type == boolean.class) ? "is" : "get";
            String getterName = getterPrefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            
            try {
                Method getter = obj.getClass().getMethod(getterName);
                Object result = getter.invoke(obj);
                return type.cast(result);
            } catch (NoSuchMethodException e) {
                // Getter not found, try field access
            }
            
            // Try to access field directly
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(propertyName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return type.cast(result);
            } catch (NoSuchFieldException e) {
                // Field not found, return null
                return null;
            }
        } catch (Exception e) {
            System.err.println("Failed to get property " + propertyName + ": " + e.getMessage());
            return null;
        }
    }
}
