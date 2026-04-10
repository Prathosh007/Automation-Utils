package com.me.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for serving static resources and API Explorer UI
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure resource handlers to serve static files
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve API Explorer files from classpath:/static/api-explorer/
        registry.addResourceHandler("/explorer/**")
                .addResourceLocations("classpath:/static/api-explorer/");
                
        // Serve Swagger UI resources
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
                
        // Serve other static resources
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        
        // Serve upload/download UI from /files/**
        registry.addResourceHandler("/files/**")
                .addResourceLocations("classpath:/static/file");
    }
    
    /**
     * Add view controllers for the API Explorer and Swagger UI
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect root to API Explorer
        registry.addRedirectViewController("/", "/api/explorer/");
        
        // API Explorer index
        registry.addViewController("/explorer/")
                .setViewName("forward:/explorer/explorer.html");
                
        // Swagger UI redirect
        registry.addRedirectViewController("/docs", "/swagger-ui/index.html");
        
        // Upload/Download UI index
        registry.addViewController("/files/")
                .setViewName("forward:/file/index.html");
    }
}
