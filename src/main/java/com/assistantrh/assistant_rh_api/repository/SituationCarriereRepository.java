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
                hc.id AS historique_id,
                hc.employe_id,
                hc.date_debut,
                hc.date_fin,
                hc.echelon_id,
                ec.classe_id,
                cl.libelle AS classe,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                ec.ordre AS echelon,
                ec.ordre AS echelon_ordre,
                ec.duree_min AS duree_min,
                ec.duree_min AS echelon_duree_min
            FROM historique_carriere hc
            JOIN echelon ec
                ON ec.id = hc.echelon_id
            JOIN classe cl
                ON cl.id = ec.classe_id
            WHERE hc.employe_id = ?
              AND hc.date_fin IS NULL
              AND hc.date_debut <= CURRENT_DATE
            ORDER BY hc.date_debut DESC, hc.id DESC
            LIMIT 1
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, employeId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public List<Map<String, Object>> findHistorique(
            Integer employeId
    ) {
        String sql = """
            SELECT
                hc.id,
                hc.employe_id,
                hc.date_debut,
                hc.date_fin,
                hc.echelon_id,
                ec.classe_id,
                cl.libelle AS classe,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                ec.ordre AS echelon,
                ec.ordre AS echelon_ordre,
                ec.duree_min AS duree_min,
                ec.duree_min AS echelon_duree_min
            FROM historique_carriere hc
            JOIN echelon ec
                ON ec.id = hc.echelon_id
            JOIN classe cl
                ON cl.id = ec.classe_id
            WHERE hc.employe_id = ?
            ORDER BY hc.date_debut ASC, hc.id ASC
            """;

        return jdbcTemplate.queryForList(sql, employeId);
    }

    public Optional<Map<String, Object>> findEchelonSuivant(
            Integer classeId,
            Integer ordreActuel
    ) {
        String sql = """
            SELECT
                ec.id,
                ec.classe_id,
                ec.ordre,
                ec.duree_min,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre
            FROM echelon ec
            JOIN classe cl
                ON cl.id = ec.classe_id
            WHERE ec.classe_id = ?
              AND ec.ordre > ?
            ORDER BY ec.ordre ASC
            LIMIT 1
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, classeId, ordreActuel);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public Optional<Map<String, Object>> findPremierEchelon(
            Integer classeId
    ) {
        String sql = """
            SELECT
                ec.id,
                ec.classe_id,
                ec.ordre,
                ec.duree_min,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre
            FROM echelon ec
            JOIN classe cl
                ON cl.id = ec.classe_id
            WHERE ec.classe_id = ?
            ORDER BY ec.ordre ASC
            LIMIT 1
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, classeId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public Optional<Map<String, Object>> findClasseSuivante(
            Integer ordreActuel
    ) {
        String sql = """
            SELECT
                cl.id,
                cl.libelle,
                cl.ordre
            FROM classe cl
            WHERE cl.ordre > ?
            ORDER BY cl.ordre ASC
            LIMIT 1
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, ordreActuel);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

}
