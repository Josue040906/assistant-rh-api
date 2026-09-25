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

    public List<Map<String, Object>> findAll() {
        String sql = """
            SELECT
                s.id,
                s.code,
                s.nom,
                s.description,
                s.direction_id,
                d.nom AS direction
            FROM service s
            LEFT JOIN direction d
                ON d.id = s.direction_id
            ORDER BY s.nom
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> findById(Long id) {
        String sql = """
            SELECT
                s.id,
                s.code,
                s.nom,
                s.description,
                s.direction_id,
                d.nom AS direction
            FROM service s
            LEFT JOIN direction d
                ON d.id = s.direction_id
            WHERE s.id = ?
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, id);

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    public List<Map<String, Object>> search(String query) {
        String sql = """
            SELECT
                s.id,
                s.code,
                s.nom,
                s.description,
                s.direction_id,
                d.nom AS direction
            FROM service s
            LEFT JOIN direction d
                ON d.id = s.direction_id
            WHERE
                LOWER(s.code) LIKE LOWER(?)
                OR LOWER(s.nom) LIKE LOWER(?)
                OR LOWER(COALESCE(s.description, '')) LIKE LOWER(?)
            ORDER BY s.nom
            """;

        String pattern = "%" + query.trim() + "%";

        return jdbcTemplate.queryForList(
                sql,
                pattern,
                pattern,
                pattern
        );
    }

    public Map<String, Object> create(
            String code,
            String nom,
            String description,
            Long directionId
    ) {
        String sql = """
            INSERT INTO service (
                code,
                nom,
                description,
                direction_id
            )
            VALUES (?, ?, ?, ?)
            RETURNING
                id,
                code,
                nom,
                description,
                direction_id
            """;

        return jdbcTemplate.queryForMap(
                sql,
                code,
                nom,
                description,
                directionId
        );
    }

    public Map<String, Object> update(
            Long id,
            String code,
            String nom,
            String description,
            Long directionId
    ) {
        String sql = """
            UPDATE service
            SET
                code = ?,
                nom = ?,
                description = ?,
                direction_id = ?
            WHERE id = ?
            RETURNING
                id,
                code,
                nom,
                description,
                direction_id
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        code,
                        nom,
                        description,
                        directionId,
                        id
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    public boolean delete(Long id) {
        String sql = """
            DELETE FROM service
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id) > 0;
    }
}
