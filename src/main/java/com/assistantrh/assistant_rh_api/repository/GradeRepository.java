package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
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
                g.code,
                g.libelle,
                g.description,
                g.reference_juridique,
                g.date_debut_validite,
                g.date_fin_validite
            FROM grade g
            ORDER BY g.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> findById(Long id) {

        String sql = """
            SELECT
                g.id,
                g.code,
                g.libelle,
                g.description,
                g.reference_juridique,
                g.date_debut_validite,
                g.date_fin_validite
            FROM grade g
            WHERE g.id = ?
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
                g.id,
                g.code,
                g.libelle,
                g.description,
                g.reference_juridique,
                g.date_debut_validite,
                g.date_fin_validite
            FROM grade g
            WHERE
                g.code ILIKE ?
                OR g.libelle ILIKE ?
                OR COALESCE(g.description, '') ILIKE ?
            ORDER BY
                CASE
                    WHEN g.code ILIKE ? THEN 0
                    WHEN g.libelle ILIKE ? THEN 1
                    ELSE 2
                END,
                g.id
            """;

        String value = "%" + query.trim() + "%";
        String exactValue = query.trim();

        return jdbcTemplate.queryForList(
                sql,
                value,
                value,
                value,
                exactValue,
                exactValue
        );
    }

    public Map<String, Object> create(
            String code,
            String libelle,
            String description,
            String referenceJuridique,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite
    ) {

        String sql = """
            INSERT INTO grade (
                code,
                libelle,
                description,
                reference_juridique,
                date_debut_validite,
                date_fin_validite
            )
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING
                id,
                code,
                libelle,
                description,
                reference_juridique,
                date_debut_validite,
                date_fin_validite
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        code,
                        libelle,
                        description,
                        referenceJuridique,
                        toSqlDate(dateDebutValidite),
                        toSqlDate(dateFinValidite)
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    public Map<String, Object> update(
            Long id,
            String code,
            String libelle,
            String description,
            String referenceJuridique,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite
    ) {

        String sql = """
            UPDATE grade
            SET
                code = ?,
                libelle = ?,
                description = ?,
                reference_juridique = ?,
                date_debut_validite = ?,
                date_fin_validite = ?
            WHERE id = ?
            RETURNING
                id,
                code,
                libelle,
                description,
                reference_juridique,
                date_debut_validite,
                date_fin_validite
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        code,
                        libelle,
                        description,
                        referenceJuridique,
                        toSqlDate(dateDebutValidite),
                        toSqlDate(dateFinValidite),
                        id
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    public boolean delete(Long id) {

        String sql = """
            DELETE FROM grade
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id) > 0;
    }

    private Date toSqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }
}