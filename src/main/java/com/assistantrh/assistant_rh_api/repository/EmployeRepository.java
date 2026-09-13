package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class EmployeRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmployeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {

        String sql = """
                SELECT
                    e.id,
                    e.matricule,
                    e.nom,
                    e.prenom,
                    e.date_naissance,
                    e.date_embauche,
                    p.intitule AS poste,
                    s.nom AS service
                FROM employe e
                JOIN poste p ON e.poste_id = p.id
                JOIN service s ON e.service_id = s.id
                ORDER BY e.id
                """;

        return jdbcTemplate.queryForList(sql);
    }
}