package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ActiviteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ActiviteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int enregistrer(
            Integer acteurId,
            Integer employeId,
            String typeAction,
            String description
    ) {

        String sql = """
            INSERT INTO activite (
                acteur_id,
                employe_id,
                type_action,
                description
            )
            VALUES (?, ?, ?, ?)
            """;

        return jdbcTemplate.update(
                sql,
                acteurId,
                employeId,
                typeAction,
                description
        );
    }

    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                a.id,
                a.acteur_id,
                a.employe_id,
                a.type_action,
                a.description,
                a.date_heure,

                ua.email AS acteur_email,
                ea.nom AS acteur_nom,
                ea.prenom AS acteur_prenom,

                ee.matricule AS employe_matricule,
                ee.nom AS employe_nom,
                ee.prenom AS employe_prenom

            FROM activite a

            LEFT JOIN utilisateur ua
                ON ua.id = a.acteur_id

            LEFT JOIN employe ea
                ON ea.user_id = a.acteur_id

            LEFT JOIN employe ee
                ON ee.id = a.employe_id

            ORDER BY a.date_heure DESC, a.id DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }
}