package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ReclassementRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReclassementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int enregistrer(Integer employeId, Integer activiteId, Integer categorieId) {
        return jdbcTemplate.update("""
            INSERT INTO reclassement (employe_id, categorie_id, id_activite)
            VALUES (?, ?, ?)
            """, employeId, categorieId, activiteId);
    }

    public List<Map<String, Object>> historiqueEmploye(Integer employeId) {
        return jdbcTemplate.queryForList("""
            SELECT r.id,
                   r.employe_id,
                   r.categorie_id,
                   c.code AS categorie,
                   r.id_activite,
                   a.acteur_id,
                   a.description AS activite_description,
                   a.date_heure
            FROM reclassement r
            JOIN activite a ON a.id = r.id_activite
            JOIN categorie c ON c.id = r.categorie_id
            WHERE r.employe_id = ?
            ORDER BY a.date_heure DESC, r.id DESC
            """, employeId);
    }
}
