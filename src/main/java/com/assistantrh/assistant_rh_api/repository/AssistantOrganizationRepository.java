package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AssistantOrganizationRepository {
    private final JdbcTemplate jdbcTemplate;

    public AssistantOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> search(
            String query,
            String scope,
            int limit
    ) {
        String pattern = "%" + query
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
                + "%";
        return jdbcTemplate.queryForList("""
                SELECT d.id AS direction_id,
                       d.nom AS direction,
                       d.description AS direction_description,
                       s.id AS service_id,
                       s.code AS service_code,
                       s.nom AS service,
                       s.description AS service_description,
                       p.id AS poste_id,
                       p.intitule AS poste,
                       p.description AS poste_description
                FROM direction d
                LEFT JOIN service s ON s.direction_id = d.id
                LEFT JOIN poste p ON p.service_id = s.id
                WHERE (? = 'DIRECTION' AND (
                           d.nom ILIKE ? ESCAPE '!'
                           OR COALESCE(d.description, '') ILIKE ? ESCAPE '!'
                       ))
                   OR (? = 'SERVICE' AND (
                           s.code ILIKE ? ESCAPE '!'
                           OR s.nom ILIKE ? ESCAPE '!'
                           OR COALESCE(s.description, '') ILIKE ? ESCAPE '!'
                       ))
                   OR (? = 'POSTE' AND (
                           p.intitule ILIKE ? ESCAPE '!'
                           OR COALESCE(p.description, '') ILIKE ? ESCAPE '!'
                       ))
                   OR (? = 'ALL' AND (
                           d.nom ILIKE ? ESCAPE '!'
                           OR COALESCE(d.description, '') ILIKE ? ESCAPE '!'
                           OR s.code ILIKE ? ESCAPE '!'
                           OR s.nom ILIKE ? ESCAPE '!'
                           OR COALESCE(s.description, '') ILIKE ? ESCAPE '!'
                           OR p.intitule ILIKE ? ESCAPE '!'
                           OR COALESCE(p.description, '') ILIKE ? ESCAPE '!'
                       ))
                ORDER BY d.nom, s.nom, p.intitule
                LIMIT ?
                """,
                scope, pattern, pattern,
                scope, pattern, pattern, pattern,
                scope, pattern, pattern,
                scope, pattern, pattern, pattern, pattern, pattern, pattern, pattern,
                limit
        );
    }
}
