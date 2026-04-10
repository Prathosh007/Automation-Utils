package com.me.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;

import com.me.api.config.JacksonConfig;

/**
 * Main Spring Boot application class for G.O.A.T REST API
 */
@SpringBootApplication(
    exclude = {
        // Disable auto-configurations to avoid dependency issues
        SecurityAutoConfiguration.class,
        ErrorMvcAutoConfiguration.class,
        ValidationAutoConfiguration.class
    },
    scanBasePackages = {
        "com.me.api.controller",
        "com.me.api.service", 
        "com.me.api.bridge",
        "com.me.api.config",
        "com.me.api.error",
        "com.me.api.model",
        "com.me.api.adapter"
    } // Only scan specific packages, excluding the problematic one
)
@Import(JacksonConfig.class) // Use our custom JSON serialization configuration
public class GoatApiApplication {
    
    /**
     * Application entry point
     */
    public static void main(String[] args) {
        // Set system properties for configuration
        System.setProperty("spring.config.location", 
                          "classpath:/com/me/api/resources/application.properties");
        
        // Enable API Explorer UI (static resources) and Swagger
        System.setProperty("spring.resources.static-locations", "classpath:/static/");
        System.setProperty("springdoc.api-docs.enabled", "true");
        System.setProperty("springdoc.swagger-ui.enabled", "true");
        System.setProperty("spring.mvc.pathmatch.matching-strategy", "ant_path_matcher");
        
        // Explicitly allow bean definition overriding to resolve conflicts
        System.setProperty("spring.main.allow-bean-definition-overriding", "true");
        
        // Create our own application context
        SpringApplication app = new SpringApplication(GoatApiApplication.class);
        
        // Start the Spring Boot application
        app.run(args);
    }
}
