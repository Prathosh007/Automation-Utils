package com.me.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Configuration for Jackson JSON serialization
 * Handles special cases like JsonObject and large response serialization
 */
@Configuration
public class JacksonConfig {

    /**
     * Configure Jackson ObjectMapper with custom settings
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        
        // Disable failing on empty beans
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        // Register a module to handle Gson JsonElements
        SimpleModule gsonModule = new SimpleModule("GsonModule");
        gsonModule.addSerializer(JsonObject.class, new ToStringSerializer());
        gsonModule.addSerializer(JsonElement.class, new ToStringSerializer());
        objectMapper.registerModule(gsonModule);
        
        return objectMapper;
    }
    
    /**
     * Create a custom message converter for HTTP responses
     */
    @Bean
    public MappingJackson2HttpMessageConverter customHttpMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }
}