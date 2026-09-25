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

    /**
     * Récupère tous les grades.
     */
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

    /**
     * Récupère un grade par son identifiant.
     */
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

    /**
     * Recherche des grades.
     */
    public List<Map<String, Object>> search(String query) {

        String sql = """
            SELECT
                g.id,
                g.code_grade,
                g.type_emploi_id,
                te.nom AS type_emploi
            FROM grade g
            LEFT JOIN type_emploi te
                ON te.id = g.type_emploi_id
            WHERE
                g.code_grade ILIKE ?
                OR te.nom ILIKE ?
            ORDER BY
                CASE
                    WHEN g.code_grade ILIKE ? THEN 0
                    ELSE 1
                END,
                g.id
            """;

        String value = "%" + query.trim() + "%";
        String exactValue = query.trim();

        return jdbcTemplate.queryForList(
                sql,
                value,
                value,
                exactValue
        );
    }

    /**
     * Crée un grade.
     */
    public Map<String, Object> create(
            String codeGrade,
            Long typeEmploiId
    ) {

        String sql = """
            INSERT INTO grade (
                code_grade,
                type_emploi_id
            )
            VALUES (?, ?)
            RETURNING
                id,
                code_grade,
                type_emploi_id
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        codeGrade,
                        typeEmploiId
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    /**
     * Modifie un grade.
     */
    public Map<String, Object> update(
            Long id,
            String codeGrade,
            Long typeEmploiId
    ) {

        String sql = """
            UPDATE grade
            SET
                code_grade = ?,
                type_emploi_id = ?
            WHERE id = ?
            RETURNING
                id,
                code_grade,
                type_emploi_id
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        codeGrade,
                        typeEmploiId,
                        id
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    /**
     * Supprime un grade.
     */
    public boolean delete(Long id) {

        String sql = """
            DELETE FROM grade
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id) > 0;
    }
}
