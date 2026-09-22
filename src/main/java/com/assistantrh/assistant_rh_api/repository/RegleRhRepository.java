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

    /**
     * Récupère une règle RH active par son code.
     */
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
                r.population_concernee,
                r.reference_juridique,
                r.article,
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
            r.population_concernee,
            r.reference_juridique,
            r.article,
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

    /**
     * Récupère la population concernée par une règle.
     */
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

                rp.cadre_id,
                ca.code AS cadre,
                ca.libelle AS cadre_libelle,

                rp.echelle_id,
                ec.code AS echelle,
                ec.libelle AS echelle_libelle,

                rp.corps_id,
                co.code AS corps,
                co.libelle AS corps_libelle,

                rp.grade_carriere_id,
                gc.code AS grade,
                gc.libelle AS grade_libelle,

                rp.observation

            FROM regle_population rp

            LEFT JOIN statut_agent sa
                ON sa.id = rp.statut_agent_id

            LEFT JOIN cadre ca
                ON ca.id = rp.cadre_id

            LEFT JOIN echelle ec
                ON ec.id = rp.echelle_id

            LEFT JOIN corps co
                ON co.id = rp.corps_id

            LEFT JOIN grade_carriere gc
                ON gc.id = rp.grade_carriere_id

            WHERE rp.regle_id = ?

            ORDER BY rp.id
            """;

        return jdbcTemplate.queryForList(sql, regleId);
    }

    /**
     * Récupère les conditions d'une règle.
     */
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

        return jdbcTemplate.queryForList(sql, regleId);
    }

    /**
     * Récupère les effets d'une règle.
     */
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

        return jdbcTemplate.queryForList(sql, regleId);
    }

    /**
     * Récupère les références juridiques d'une règle.
     */
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

        return jdbcTemplate.queryForList(sql, regleId);
    }
}