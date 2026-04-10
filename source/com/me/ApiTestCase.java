package com.me;

import java.util.HashMap;
import java.util.Map;

/**
 * Class representing an API test case with all necessary parameters
 */
public class ApiTestCase {
    private String id;
    private String description;
    private String endpoint;
    private String method = "GET"; //No I18N
    private Map<String, String> headers = new HashMap<>();
    private String body;
    private int timeout = 30000; // 30 seconds default timeout
    private String expectedContent;
    
    /**
     * Default constructor
     */
    public ApiTestCase() {
    }
    
    /**
     * Constructor with basic parameters
     * 
     * @param id Test case ID
     * @param endpoint API endpoint URL
     * @param method HTTP method (GET, POST, etc.)
     */
    public ApiTestCase(String id, String endpoint, String method) {
        this.id = id;
        this.endpoint = endpoint;
        this.method = method;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public String getExpectedContent() {
        return expectedContent;
    }
    
    public void setExpectedContent(String expectedContent) {
        this.expectedContent = expectedContent;
    }
}
