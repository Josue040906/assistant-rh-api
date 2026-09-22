package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SituationCarriereRepository {

    private final JdbcTemplate jdbcTemplate;

    public SituationCarriereRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findSituationActuelle(
            Integer employeId
    ) {

        String sql = """
            SELECT
                sc.id,
                sc.employe_id,
                sc.date_debut,
                sc.date_fin,
                sc.indice,
                sc.origine,
                sc.reference_acte,
                sc.date_acte,
                sc.observation,

                sa.code AS statut,
                sa.libelle AS statut_libelle,

                ca.code AS cadre,
                ca.libelle AS cadre_libelle,

                ec.code AS echelle,
                ec.libelle AS echelle_libelle,

                co.code AS corps,
                co.libelle AS corps_libelle,

                gc.code AS grade,
                gc.libelle AS grade_libelle,

                cl.code AS classe,
                cl.libelle AS classe_libelle,

                ech.code AS echelon,
                ech.libelle AS echelon_libelle,
                ech.ordre AS echelon_ordre

            FROM situation_carriere sc

            LEFT JOIN statut_agent sa
                ON sa.id = sc.statut_agent_id

            LEFT JOIN cadre ca
                ON ca.id = sc.cadre_id

            LEFT JOIN echelle ec
                ON ec.id = sc.echelle_id

            LEFT JOIN corps co
                ON co.id = sc.corps_id

            LEFT JOIN grade_carriere gc
                ON gc.id = sc.grade_carriere_id

            LEFT JOIN classe cl
                ON cl.id = sc.classe_id

            LEFT JOIN echelon ech
                ON ech.id = sc.echelon_id

            WHERE sc.employe_id = ?
              AND sc.date_debut <= CURRENT_DATE
              AND (
                  sc.date_fin IS NULL
                  OR sc.date_fin >= CURRENT_DATE
              )

            ORDER BY sc.date_debut DESC
            LIMIT 1
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, employeId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }
    public List<Map<String, Object>> findHistorique(Integer employeId) {

        String sql = """
        SELECT
            sc.id,
            sc.employe_id,
            sc.date_debut,
            sc.date_fin,
            sc.indice,
            sc.origine,
            sc.reference_acte,
            sc.date_acte,
            sc.observation,

            sa.code AS statut,
            sa.libelle AS statut_libelle,

            ca.code AS cadre,
            ca.libelle AS cadre_libelle,

            ec.code AS echelle,
            ec.libelle AS echelle_libelle,

            co.code AS corps,
            co.libelle AS corps_libelle,

            gc.code AS grade,
            gc.libelle AS grade_libelle,

            cl.code AS classe,
            cl.libelle AS classe_libelle,

            ech.code AS echelon,
            ech.libelle AS echelon_libelle,
            ech.ordre AS echelon_ordre

        FROM situation_carriere sc

        LEFT JOIN statut_agent sa
            ON sa.id = sc.statut_agent_id

        LEFT JOIN cadre ca
            ON ca.id = sc.cadre_id

        LEFT JOIN echelle ec
            ON ec.id = sc.echelle_id

        LEFT JOIN corps co
            ON co.id = sc.corps_id

        LEFT JOIN grade_carriere gc
            ON gc.id = sc.grade_carriere_id

        LEFT JOIN classe cl
            ON cl.id = sc.classe_id

        LEFT JOIN echelon ech
            ON ech.id = sc.echelon_id

        WHERE sc.employe_id = ?

        ORDER BY sc.date_debut ASC
        """;

        return jdbcTemplate.queryForList(sql, employeId);
    }

    public Optional<Map<String, Object>> findEchelonSuivant(
            Integer classeId,
            Integer ordreActuel
    ) {

        String sql = """
        SELECT
            ech.id,
            ech.classe_id,
            ech.code,
            ech.libelle,
            ech.ordre,
            gi.indice

        FROM echelon ech

        LEFT JOIN grille_indiciaire gi
            ON gi.classe_id = ech.classe_id
           AND gi.echelon_id = ech.id

        WHERE ech.classe_id = ?
          AND ech.ordre > ?

        ORDER BY ech.ordre ASC
        LIMIT 1
        """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(
                        sql,
                        classeId,
                        ordreActuel
                );

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public Optional<Map<String, Object>> findClasseSuivante(
            Integer gradeCarriereId,
            Integer ordreActuel
    ) {
        String sql = """
        SELECT
            cl.id,
            cl.code,
            cl.libelle,
            cl.ordre,
            cl.grade_carriere_id
        FROM classe cl
        WHERE cl.grade_carriere_id = ?
          AND cl.ordre > ?
        ORDER BY cl.ordre ASC
        LIMIT 1
        """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(
                        sql,
                        gradeCarriereId,
                        ordreActuel
                );

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }
    public Optional<Map<String, Object>> findDonneesAnalyseActuelle(Integer employeId) {

        String sql = """
        SELECT
            sc.id AS situation_carriere_id,
            sc.employe_id,
            sc.date_debut,
            sc.date_fin,
            sc.statut_agent_id,
            sc.cadre_id,
            sc.echelle_id,
            sc.corps_id,
            sc.grade_carriere_id,
            sc.classe_id,
            sc.echelon_id,
            sc.indice,

            sa.code AS statut_agent_code,
            sa.libelle AS statut_agent,

            ca.code AS cadre_code,
            ca.libelle AS cadre,

            ec.code AS echelle_code,
            ec.libelle AS echelle,

            co.code AS corps_code,
            co.libelle AS corps,

            gc.code AS grade_code,
            gc.libelle AS grade,

            cl.code AS classe_code,
            cl.libelle AS classe,
            cl.ordre AS classe_ordre,

            ech.code AS echelon_code,
            ech.libelle AS echelon,
            ech.ordre AS echelon_ordre

        FROM situation_carriere sc
        LEFT JOIN statut_agent sa
            ON sa.id = sc.statut_agent_id
        LEFT JOIN cadre ca
            ON ca.id = sc.cadre_id
        LEFT JOIN echelle ec
            ON ec.id = sc.echelle_id
        LEFT JOIN corps co
            ON co.id = sc.corps_id
        LEFT JOIN grade_carriere gc
            ON gc.id = sc.grade_carriere_id
        LEFT JOIN classe cl
            ON cl.id = sc.classe_id
        LEFT JOIN echelon ech
            ON ech.id = sc.echelon_id

        WHERE sc.employe_id = ?
          AND sc.date_debut <= CURRENT_DATE
          AND (
              sc.date_fin IS NULL
              OR sc.date_fin >= CURRENT_DATE
          )

        ORDER BY sc.date_debut DESC
        LIMIT 1
        """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, employeId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }
}