        package com.assistantrh.assistant_rh_api.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
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

    public List<Map<String, Object>> search(String query) {
        String sql = """
            SELECT
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            FROM statut_agent
            WHERE
                code ILIKE ?
                OR libelle ILIKE ?
                OR COALESCE(description, '') ILIKE ?
            ORDER BY
                CASE
                    WHEN code ILIKE ? THEN 0
                    WHEN libelle ILIKE ? THEN 1
                    ELSE 2
                END,
                id
            """;

        String recherche = "%" + query.trim() + "%";

        return jdbcTemplate.queryForList(
                sql,
                recherche,
                recherche,
                recherche,
                recherche,
                recherche
        );
    }

    public Map<String, Object> create(
            String code,
            String libelle,
            String description,
            Date dateDebutValidite,
            Date dateFinValidite
    ) {
        String sql = """
            INSERT INTO statut_agent (
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            )
            VALUES (?, ?, ?, ?, ?)
            RETURNING
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            """;

        try {
            return jdbcTemplate.queryForMap(
                    sql,
                    code,
                    libelle,
                    description,
                    dateDebutValidite,
                    dateFinValidite
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> update(
            Long id,
            String code,
            String libelle,
            String description,
            Date dateDebutValidite,
            Date dateFinValidite
    ) {
        String sql = """
            UPDATE statut_agent
            SET
                code = ?,
                libelle = ?,
                description = ?,
                date_debut_validite = ?,
                date_fin_validite = ?
            WHERE id = ?
            RETURNING
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            """;

        try {
            return jdbcTemplate.queryForMap(
                    sql,
                    code,
                    libelle,
                    description,
                    dateDebutValidite,
                    dateFinValidite,
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean delete(Long id) {
        String sql = """
            DELETE FROM statut_agent
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id) > 0;
    }
}
