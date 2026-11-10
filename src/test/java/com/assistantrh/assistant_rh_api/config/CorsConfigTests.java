package com.assistantrh.assistant_rh_api.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTests {

    /** Donne accès aux règles enregistrées, protégées dans CorsRegistry. */
    private static final class Registre extends CorsRegistry {
        Map<String, CorsConfiguration> regles() {
            return getCorsConfigurations();
        }
    }

    @Test
    void leNavigateurPeutMarquerLesNotificationsCommeLuesEtLesSupprimer() {
        Registre registre = new Registre();
        new CorsConfig().addCorsMappings(registre);

        CorsConfiguration api = registre.regles().get("/api/**");
        assertTrue(api.getAllowedMethods().contains("PATCH"));
        assertTrue(api.getAllowedMethods().contains("DELETE"));
    }
}
