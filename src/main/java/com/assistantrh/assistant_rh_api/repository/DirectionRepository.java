package com.assistantrh.assistant_rh_api.repository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class DirectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public DirectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {
        String sql = """
        SELECT
            id,
            nom,
            description
        FROM direction
        ORDER BY nom
        """;

        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> findById(Long id) {
        String sql = """
        SELECT
            id,
            nom,
            description
        FROM direction
        WHERE id = ?
        """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, id);

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    public Map<String, Object> create(String nom, String description) {
        String sql = """
        INSERT INTO direction (nom, description)
        VALUES (?, ?)
        RETURNING id, nom, description
        """;

        return jdbcTemplate.queryForMap(sql, nom, description);
    }

    public Map<String, Object> update(
            Long id,
            String nom,
            String description
    ) {
        String sql = """
        UPDATE direction
        SET
            nom = ?,
            description = ?
        WHERE id = ?
        RETURNING id, nom, description
        """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        nom,
                        description,
                        id
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    public boolean delete(Long id) {
        String sql = """
        DELETE FROM direction
        WHERE id = ?
        """;

        return jdbcTemplate.update(sql, id) > 0;
    }

}
