        package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class StatutAgentRepository {

    private final JdbcTemplate jdbcTemplate;

    public StatutAgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {
        String sql = """
            SELECT
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            FROM statut_agent
            ORDER BY id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> findById(Long id) {
        String sql = """
            SELECT
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            FROM statut_agent
            WHERE id = ?
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, id);

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }
}
