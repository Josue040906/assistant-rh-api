package com.assistantrh.assistant_rh_api.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Adresse publique et légère : le site l'appelle dès son ouverture pour réveiller
 * l'API (et sa base) pendant que l'utilisateur se connecte. Elle ne renvoie
 * aucune donnée.
 */
@RestController
public class SanteController {

    private final JdbcTemplate jdbcTemplate;

    public SanteController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/sante")
    public Map<String, Object> sante() {
        boolean base;
        try {
            base = Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException erreur) {
            base = false;
        }
        return Map.of("statut", "ok", "base", base);
    }
}
