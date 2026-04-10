package com.me.api.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Standard API response format for all endpoints
 */
@JsonSerialize(using = GoatApiResponseSerializer.class)
public class GoatApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private long timestamp;
    
    public GoatApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public GoatApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();

    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
