        package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class GradeRepository {

    private final JdbcTemplate jdbcTemplate;

    public GradeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {
        String sql = """
            SELECT
                g.id,
                g.code_grade,
                g.type_emploi_id,
                te.nom AS type_emploi
            FROM grade g
            LEFT JOIN type_emploi te
                ON te.id = g.type_emploi_id
            ORDER BY g.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> findById(Long id) {
        String sql = """
            SELECT
                g.id,
                g.code_grade,
                g.type_emploi_id,
                te.nom AS type_emploi
            FROM grade g
            LEFT JOIN type_emploi te
                ON te.id = g.type_emploi_id
            WHERE g.id = ?
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, id);

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }
}
