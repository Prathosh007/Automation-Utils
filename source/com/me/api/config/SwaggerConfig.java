package com.me.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger configuration for API documentation
 * This class uses reflection to avoid hard dependencies on Swagger
 * so the application can run without the Swagger libraries
 */
@Configuration
public class SwaggerConfig {

    @Value("${application.version:1.0.0}")
    private String appVersion;
    
    /**
     * Check if Swagger classes are available
     */
    private boolean isSwaggerAvailable() {
        try {
            Class.forName("org.springdoc.core.GroupedOpenApi");
            return true;
        } catch (ClassNotFoundException e) {
            System.out.println("Swagger not available, API documentation will be disabled");
            return false;
        }
    }
    
    /**
     * Main OpenAPI configuration
     * Uses reflection to avoid compile-time dependencies on Swagger
     */
    @Bean
    public Object apiDocumentation() {
        if (!isSwaggerAvailable()) {
            return new Object(); // Dummy bean when Swagger is not available
        }
        
        try {
            // Create OpenAPI object using reflection
            Class<?> openApiClass = Class.forName("io.swagger.v3.oas.models.OpenAPI");
            Class<?> infoClass = Class.forName("io.swagger.v3.oas.models.info.Info");
            Class<?> contactClass = Class.forName("io.swagger.v3.oas.models.info.Contact");
            Class<?> licenseClass = Class.forName("io.swagger.v3.oas.models.info.License");
            
            Object info = infoClass.getDeclaredConstructor().newInstance();
            Object contact = contactClass.getDeclaredConstructor().newInstance();
            Object license = licenseClass.getDeclaredConstructor().newInstance();
            Object openApi = openApiClass.getDeclaredConstructor().newInstance();
            
            // Set properties using reflection
            infoClass.getMethod("title", String.class).invoke(info, "G.O.A.T API");
            infoClass.getMethod("description", String.class).invoke(info, "API for G.O.A.T Framework - Test Automation Platform");
            infoClass.getMethod("version", String.class).invoke(info, appVersion);
            
            contactClass.getMethod("name", String.class).invoke(contact, "G.O.A.T Support");
            contactClass.getMethod("url", String.class).invoke(contact, "https://github.com/goat/framework");
            contactClass.getMethod("email", String.class).invoke(contact, "support@goatframework.org");
            
            licenseClass.getMethod("name", String.class).invoke(license, "MIT License");
            licenseClass.getMethod("url", String.class).invoke(license, "https://opensource.org/licenses/MIT");
            
            // Set contact and license on info
            infoClass.getMethod("contact", contactClass).invoke(info, contact);
            infoClass.getMethod("license", licenseClass).invoke(info, license);
            
            // Set info on OpenAPI
            openApiClass.getMethod("info", infoClass).invoke(openApi, info);
            
            return openApi;
        } catch (Exception e) {
            System.out.println("Error creating Swagger configuration: " + e.getMessage());
            return new Object(); // Return dummy bean
        }
    }
    
    /**
     * API group configuration
     */
    @Bean
    public Object apiGroup() {
        if (!isSwaggerAvailable()) {
            return new Object(); // Dummy bean when Swagger is not available
        }
        
        try {
            // Create GroupedOpenApi using reflection
            Class<?> builderClass = Class.forName("org.springdoc.core.GroupedOpenApi$Builder");
            Object builder = builderClass.getDeclaredMethod("builder").invoke(null);
            
            builderClass.getMethod("group", String.class).invoke(builder, "api");
            builderClass.getMethod("packagesToScan", String[].class).invoke(builder, new Object[]{new String[]{"com.me.api.controller"}});
            builderClass.getMethod("pathsToMatch", String[].class).invoke(builder, new Object[]{new String[]{"/**"}});
            
            return builderClass.getMethod("build").invoke(builder);
        } catch (Exception e) {
            System.out.println("Error creating API group: " + e.getMessage());
            return new Object(); // Return dummy bean
        }
    }
}
