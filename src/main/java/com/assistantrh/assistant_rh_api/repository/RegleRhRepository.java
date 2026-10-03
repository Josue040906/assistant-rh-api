package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RegleRhRepository {

    private final JdbcTemplate jdbcTemplate;

    public RegleRhRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                r.id,
                r.type_regle_id,
                tr.code AS type_regle,
                tr.libelle AS type_regle_libelle,
                r.code,
                r.libelle,
                r.description,
                r.date_debut_validite,
                r.date_fin_validite,
                r.priorite,
                r.active

            FROM regle_rh r

            JOIN type_regle_rh tr
                ON tr.id = r.type_regle_id

            ORDER BY r.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> findById(Integer id) {

        String sql = """
            SELECT
                r.id,
                r.type_regle_id,
                tr.code AS type_regle,
                tr.libelle AS type_regle_libelle,
                r.code,
                r.libelle,
                r.description,
                r.date_debut_validite,
                r.date_fin_validite,
                r.priorite,
                r.active

            FROM regle_rh r

            JOIN type_regle_rh tr
                ON tr.id = r.type_regle_id

            WHERE r.id = ?
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, id);

        return result.isEmpty()
                ? null
                : result.get(0);
    }

    public List<Map<String, Object>> search(String query) {

        String sql = """
            SELECT
                r.id,
                r.type_regle_id,
                tr.code AS type_regle,
                tr.libelle AS type_regle_libelle,
                r.code,
                r.libelle,
                r.description,
                r.date_debut_validite,
                r.date_fin_validite,
                r.priorite,
                r.active

            FROM regle_rh r

            JOIN type_regle_rh tr
                ON tr.id = r.type_regle_id

            WHERE
                r.code ILIKE ?
                OR r.libelle ILIKE ?
                OR r.description ILIKE ?
                OR tr.code ILIKE ?
                OR tr.libelle ILIKE ?

            ORDER BY r.id
            """;

        String value = "%" + query.trim() + "%";

        return jdbcTemplate.queryForList(
                sql,
                value,
                value,
                value,
                value,
                value
        );
    }

    public Optional<Map<String, Object>> findRegleActiveByCode(
            String code
    ) {

        String sql = """
            SELECT
                r.id,
                r.type_regle_id,
                tr.code AS type_regle,
                tr.libelle AS type_regle_libelle,
                r.code,
                r.libelle,
                r.description,
                r.date_debut_validite,
                r.date_fin_validite,
                r.priorite,
                r.active

            FROM regle_rh r

            JOIN type_regle_rh tr
                ON tr.id = r.type_regle_id

            WHERE r.code = ?
              AND r.active = true
              AND (
                    r.date_debut_validite IS NULL
                    OR r.date_debut_validite <= CURRENT_DATE
                  )
              AND (
                    r.date_fin_validite IS NULL
                    OR r.date_fin_validite >= CURRENT_DATE
                  )

            ORDER BY r.priorite ASC NULLS LAST
            LIMIT 1
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, code);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public List<Map<String, Object>> findReglesActives() {

        String sql = """
            SELECT
                r.id,
                r.type_regle_id,
                tr.code AS type_regle,
                tr.libelle AS type_regle_libelle,
                r.code,
                r.libelle,
                r.description,
                r.date_debut_validite,
                r.date_fin_validite,
                r.priorite,
                r.active

            FROM regle_rh r

            JOIN type_regle_rh tr
                ON tr.id = r.type_regle_id

            WHERE r.active = true
              AND (
                    r.date_debut_validite IS NULL
                    OR r.date_debut_validite <= CURRENT_DATE
                  )
              AND (
                    r.date_fin_validite IS NULL
                    OR r.date_fin_validite >= CURRENT_DATE
                  )

            ORDER BY
                r.priorite ASC NULLS LAST,
                r.id ASC
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> create(
            Integer typeRegleId,
            String code,
            String libelle,
            String description,
            java.sql.Date dateDebutValidite,
            java.sql.Date dateFinValidite,
            Integer priorite,
            Boolean active
    ) {

        String sql = """
            INSERT INTO regle_rh (
                type_regle_id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite,
                priorite,
                active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)

            RETURNING
                id,
                type_regle_id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite,
                priorite,
                active
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(
                        sql,
                        typeRegleId,
                        code,
                        libelle,
                        description,
                        dateDebutValidite,
                        dateFinValidite,
                        priorite,
                        active
                );

        return result.isEmpty()
                ? null
                : result.get(0);
    }

    public Map<String, Object> update(
            Integer id,
            Integer typeRegleId,
            String code,
            String libelle,
            String description,
            java.sql.Date dateDebutValidite,
            java.sql.Date dateFinValidite,
            Integer priorite,
            Boolean active
    ) {

        String sql = """
            UPDATE regle_rh
            SET
                type_regle_id = ?,
                code = ?,
                libelle = ?,
                description = ?,
                date_debut_validite = ?,
                date_fin_validite = ?,
                priorite = ?,
                active = ?
            WHERE id = ?

            RETURNING
                id,
                type_regle_id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite,
                priorite,
                active
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(
                        sql,
                        typeRegleId,
                        code,
                        libelle,
                        description,
                        dateDebutValidite,
                        dateFinValidite,
                        priorite,
                        active,
                        id
                );

        return result.isEmpty()
                ? null
                : result.get(0);
    }

    public boolean delete(Integer id) {

        String sql = """
            DELETE FROM regle_rh
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id) > 0;
    }

    public List<Map<String, Object>> findTypesRegles() {

        String sql = """
            SELECT
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite

            FROM type_regle_rh

            ORDER BY id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> findPopulationByRegleId(
            Integer regleId
    ) {

        String sql = """
            SELECT
                rp.id,
                rp.regle_id,
                rp.type_population,

                rp.statut_agent_id,
                sa.code AS statut_agent,
                sa.libelle AS statut_agent_libelle,

                rp.corps_id,
                co.code AS corps,
                co.libelle AS corps_libelle,

                rp.observation

            FROM regle_population rp

            LEFT JOIN statut_agent sa
                ON sa.id = rp.statut_agent_id

            LEFT JOIN corps co
                ON co.id = rp.corps_id

            WHERE rp.regle_id = ?

            ORDER BY rp.id
            """;

        return jdbcTemplate.queryForList(
                sql,
                regleId
        );
    }

    public List<Map<String, Object>> findConditionsByRegleId(
            Integer regleId
    ) {

        String sql = """
            SELECT
                id,
                regle_id,
                code,
                libelle,
                type_condition,
                operateur,
                valeur,
                ordre,
                obligatoire,
                description

            FROM condition_regle_rh

            WHERE regle_id = ?

            ORDER BY ordre
            """;

        return jdbcTemplate.queryForList(
                sql,
                regleId
        );
    }

    public List<Map<String, Object>> findEffetsByRegleId(
            Integer regleId
    ) {

        String sql = """
            SELECT
                id,
                regle_id,
                code,
                libelle,
                type_effet,
                valeur,
                ordre,
                description

            FROM effet_regle_rh

            WHERE regle_id = ?

            ORDER BY ordre
            """;

        return jdbcTemplate.queryForList(
                sql,
                regleId
        );
    }

    public List<Map<String, Object>> findReferencesJuridiquesByRegleId(
            Integer regleId
    ) {

        String sql = """
            SELECT
                rr.id,
                rr.regle_id,
                rr.texte_juridique_id,
                rr.article_juridique_id,
                rr.role_reference,
                rr.observation,

                tj.type_texte,
                tj.numero AS numero_texte,
                tj.titre AS titre_texte,
                tj.date_texte,
                tj.autorite,
                tj.objet,
                tj.reference_publication,
                tj.url_source,

                aj.numero_article,
                aj.titre AS titre_article,
                aj.contenu AS contenu_article,
                aj.observation AS observation_article

            FROM regle_reference_juridique rr

            JOIN texte_juridique tj
                ON tj.id = rr.texte_juridique_id

            LEFT JOIN article_juridique aj
                ON aj.id = rr.article_juridique_id

            WHERE rr.regle_id = ?

            ORDER BY rr.id
            """;

        return jdbcTemplate.queryForList(
                sql,
                regleId
        );
    }
}