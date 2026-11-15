package com.assistantrh.assistant_rh_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Les adresses du front autorisées à appeler l'API : CORS_ALLOWED_ORIGINS,
 * séparées par des virgules (en ligne : l'adresse Vercel ; en local : Vite).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] originesAutorisees;

    public CorsConfig(@Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}") String[] originesAutorisees) {
        this.originesAutorisees = originesAutorisees;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/api/**")
                .allowedOrigins(originesAutorisees)
                // PATCH : marquer une notification (ou toutes) comme lue.
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");

        registry.addMapping("/uploads/employes/**")
                .allowedOrigins(originesAutorisees)
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("Authorization");
    }
}
