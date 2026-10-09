package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class EmployeReferentielRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmployeReferentielRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAllTypesEmploi() {
        return jdbcTemplate.queryForList("""
                SELECT id, nom
                FROM type_emploi
                ORDER BY id
                """);
    }

    public List<Map<String, Object>> findAllCategories() {
        return jdbcTemplate.queryForList("""
                SELECT id, code
                FROM categorie
                ORDER BY id
                """);
    }
}
