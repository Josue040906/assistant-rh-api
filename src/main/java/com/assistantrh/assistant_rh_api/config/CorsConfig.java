package com.assistantrh.assistant_rh_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private static final String[] FRONTEND_ORIGINS = {
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/api/**")
                .allowedOrigins(FRONTEND_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");

        registry.addMapping("/uploads/employes/**")
                .allowedOrigins(FRONTEND_ORIGINS)
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("Authorization");
    }
}