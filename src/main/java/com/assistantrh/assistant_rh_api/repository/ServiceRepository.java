package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ServiceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Récupère tous les services avec leur direction.
     */
    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                s.id,
                s.code,
                s.nom,
                s.description,
                d.id AS direction_id,
                d.nom AS direction
            FROM service s
            LEFT JOIN direction d
                ON s.direction_id = d.id
            ORDER BY s.nom
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Recherche des services par code, nom ou description.
     */
    public List<Map<String, Object>> search(String query) {

        String recherche = query.trim();

        String sql = """
            SELECT
                s.id,
                s.code,
                s.nom,
                s.description,
                d.id AS direction_id,
                d.nom AS direction
            FROM service s
            LEFT JOIN direction d
                ON s.direction_id = d.id
            WHERE
                s.code ILIKE ?
                OR s.nom ILIKE ?
                OR s.description ILIKE ?
                OR d.nom ILIKE ?
            ORDER BY
                CASE
                    WHEN LOWER(s.code) = LOWER(?) THEN 1
                    WHEN LOWER(s.nom) = LOWER(?) THEN 2
                    ELSE 3
                END,
                s.nom
            """;

        String pattern = "%" + recherche + "%";

        return jdbcTemplate.queryForList(
                sql,
                pattern,
                pattern,
                pattern,
                pattern,
                recherche,
                recherche
        );
    }
}