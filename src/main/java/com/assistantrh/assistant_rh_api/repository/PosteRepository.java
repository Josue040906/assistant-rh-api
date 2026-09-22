package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PosteRepository {

    private final JdbcTemplate jdbcTemplate;

    public PosteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Récupère tous les postes avec leur service.
     */
    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                p.id,
                p.intitule,
                p.description,
                s.id AS service_id,
                s.code AS code_service,
                s.nom AS service,
                d.id AS direction_id,
                d.nom AS direction
            FROM poste p
            JOIN service s
                ON p.service_id = s.id
            LEFT JOIN direction d
                ON s.direction_id = d.id
            ORDER BY p.intitule
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Recherche un poste par son intitulé.
     */
    public List<Map<String, Object>> search(String query) {

        String recherche = query.trim();

        String sql = """
        SELECT
            p.id,
            p.intitule,
            p.description,
            s.id AS service_id,
            s.code AS code_service,
            s.nom AS service,
            d.id AS direction_id,
            d.nom AS direction
        FROM poste p
        JOIN service s
            ON p.service_id = s.id
        LEFT JOIN direction d
            ON s.direction_id = d.id
        WHERE
            p.intitule ILIKE ?
            OR p.description ILIKE ?
            OR s.code ILIKE ?
            OR s.nom ILIKE ?
            OR d.nom ILIKE ?
        ORDER BY
            CASE
                WHEN LOWER(p.intitule) = LOWER(?) THEN 1
                ELSE 2
            END,
            p.intitule,
            s.nom
        """;

        String pattern = "%" + recherche + "%";

        return jdbcTemplate.queryForList(
                sql,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                recherche
        );
    }
    /**
     * Récupère les postes appartenant à un service.
     */
    public List<Map<String, Object>> findByService(String serviceQuery) {

        String recherche = serviceQuery.trim();

        String sql = """
        SELECT
            p.id,
            p.intitule,
            p.description,
            s.id AS service_id,
            s.code AS code_service,
            s.nom AS service,
            d.id AS direction_id,
            d.nom AS direction
        FROM poste p
        JOIN service s
            ON p.service_id = s.id
        LEFT JOIN direction d
            ON s.direction_id = d.id
        WHERE
            s.code ILIKE ?
            OR s.nom ILIKE ?
        ORDER BY
            p.intitule
        """;

        String pattern = "%" + recherche + "%";

        return jdbcTemplate.queryForList(
                sql,
                pattern,
                pattern
        );
    }
}