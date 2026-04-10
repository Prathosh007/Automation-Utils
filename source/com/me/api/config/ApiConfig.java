package com.me.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API configuration for G.O.A.T REST API
 */
@Configuration
public class ApiConfig {
    
    @Value("${goat.api.cors.allowed-origins:http://localhost:9295}")
    private String allowedOrigins;
    
    @Value("${goat.api.cors.allowed-methods:GET,POST,PUT,DELETE}")
    private String allowedMethods;
    
    @Value("${goat.api.cors.max-age:3600}")
    private long maxAge;
    
    /**
     * Configure CORS for the API
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (allowedOrigins.contains("*")) {
                    // If wildcard is used, don't allow credentials
                    registry.addMapping("/**")
                        .allowedOriginPatterns("*") // Use patterns instead of origins with wildcard
                        .allowedMethods(allowedMethods.split(","))
                        .allowCredentials(false) // Set to false with wildcard
                        .maxAge(maxAge);
                } else {
                    // For specific origins, we can allow credentials
                    registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins.split(","))
                        .allowedMethods(allowedMethods.split(","))
                        .allowCredentials(true)
                        .maxAge(maxAge);
                }
            }
        };
    }
    
    /**
     * Application version information
     */
    @Bean
    public String appVersion(@Value("${application.version:1.0.0}") String version) {
        return version;
    }
}
