package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SituationCarriereRepository {
    /**
     * Date à laquelle l'agent atteint la durée minimale de son échelon actuel
     * (hc : situation en cours, ec : son échelon). Absente quand l'échelon
     * n'impose pas de durée. Seule définition, partagée par les notifications
     * et la recherche.
     */
    public static final String DATE_ANCIENNETE_ECHELON =
            "CASE WHEN ec.duree_min > 0 THEN (hc.date_debut + MAKE_INTERVAL(years => ec.duree_min))::date END";


    private final JdbcTemplate jdbcTemplate;

    public SituationCarriereRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean verrouillerEmploye(Integer employeId) {
        String sql = """
            SELECT id
            FROM employe
            WHERE id = ?
            FOR UPDATE
            """;

        ResultSetExtractor<Boolean> extracteur =
                resultSet -> resultSet.next();
        return jdbcTemplate.query(sql, extracteur, employeId);
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
                hc.categorie_id,
                ca.code AS categorie_code,
                ec.classe_id,
                cl.libelle AS classe,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                ec.ordre AS echelon,
                ec.ordre AS echelon_ordre,
                ec.duree_min AS duree_min,
                ec.duree_min AS echelon_duree_min,
                ec.code AS grade_code,
                ec.libelle AS grade_libelle,
                pa.libelle AS parcours_libelle,
                (SELECT gi.indice FROM grille_indiciaire gi WHERE gi.categorie_id = hc.categorie_id AND gi.echelon_id = hc.echelon_id) AS indice
            FROM historique_carriere hc
            JOIN echelon ec
                ON ec.id = hc.echelon_id
            JOIN classe cl
                ON cl.id = ec.classe_id
            JOIN parcours pa
                ON pa.id = cl.parcours_id
            LEFT JOIN categorie ca
                ON ca.id = hc.categorie_id
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

    public Optional<Map<String, Object>> findDerniereSituationOuverte(
            Integer employeId
    ) {
        String sql = """
            SELECT
                hc.id AS historique_id,
                hc.date_debut,
                hc.echelon_id,
                ec.ordre AS echelon_ordre,
                cl.libelle AS classe_libelle
            FROM historique_carriere hc
            JOIN echelon ec
                ON ec.id = hc.echelon_id
            JOIN classe cl
                ON cl.id = ec.classe_id
            WHERE hc.employe_id = ?
              AND hc.date_fin IS NULL
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

    public Optional<Map<String, Object>> findEchelon(Integer echelonId) {
        String sql = """
            SELECT
                ec.id,
                ec.ordre AS echelon_ordre,
                ec.code AS grade_code,
                ec.libelle AS grade_libelle,
                cl.libelle AS classe_libelle,
                pa.type_emploi_id
            FROM echelon ec
            JOIN classe cl
                ON cl.id = ec.classe_id
            JOIN parcours pa
                ON pa.id = cl.parcours_id
            WHERE ec.id = ?
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, echelonId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public int fermerSituation(Integer historiqueId, Date dateFin) {
        String sql = """
            UPDATE historique_carriere
            SET date_fin = ?
            WHERE id = ?
              AND date_fin IS NULL
            """;

        return jdbcTemplate.update(sql, dateFin, historiqueId);
    }

    public int enregistrerCategorieSituation(
            Integer historiqueId,
            Integer categorieId
    ) {
        return jdbcTemplate.update("""
                UPDATE historique_carriere
                SET categorie_id = COALESCE(categorie_id, ?)
                WHERE id = ?
                  AND date_fin IS NULL
                """,
                categorieId, historiqueId
        );
    }

    public int modifierCategorieSituation(
            Integer historiqueId,
            Integer categorieId
    ) {
        return jdbcTemplate.update("""
                UPDATE historique_carriere
                SET categorie_id = ?
                WHERE id = ?
                  AND date_fin IS NULL
                """,
                categorieId, historiqueId
        );
    }

    public int creerSituation(
            Integer employeId,
            Integer echelonId,
            Date dateDebut
    ) {
        String sql = """
            INSERT INTO historique_carriere (
                employe_id,
                echelon_id,
                categorie_id,
                date_debut,
                date_fin
            )
            SELECT e.id, ?, e.categorie_id, ?, NULL
            FROM employe e
            WHERE e.id = ?
            """;

        return jdbcTemplate.update(sql, echelonId, dateDebut, employeId);
    }

    public int creerSituation(
            Integer employeId,
            Integer categorieId,
            Integer echelonId,
            Date dateDebut
    ) {
        String sql = """
            INSERT INTO historique_carriere (
                employe_id,
                echelon_id,
                categorie_id,
                date_debut,
                date_fin
            )
            VALUES (?, ?, ?, ?, NULL)
            """;

        return jdbcTemplate.update(
                sql, employeId, echelonId, categorieId, dateDebut
        );
    }

    public int creerAvancement(
            Integer employeId,
            Integer situationAvantId,
            Integer situationApresId,
            Date dateEffet
    ) {
        return jdbcTemplate.update("""
                INSERT INTO avancement (
                    employe_id, type_avancement, situation_avant_id,
                    situation_apres_id, date_demande, date_proposition,
                    date_decision, date_effet, statut
                )
                VALUES (
                    ?, 'Avancement d''échelon', ?, ?, CURRENT_DATE,
                    CURRENT_DATE, CURRENT_DATE, ?, 'DECIDE'
                )
                """,
                employeId, situationAvantId, situationApresId, dateEffet
        );
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
                hc.categorie_id,
                ca.code AS categorie_code,
                ec.classe_id,
                cl.libelle AS classe,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                ec.ordre AS echelon,
                ec.ordre AS echelon_ordre,
                ec.duree_min AS duree_min,
                ec.duree_min AS echelon_duree_min,
                ec.code AS grade_code,
                ec.libelle AS grade_libelle,
                pa.libelle AS parcours_libelle,
                (SELECT gi.indice FROM grille_indiciaire gi WHERE gi.categorie_id = hc.categorie_id AND gi.echelon_id = hc.echelon_id) AS indice
            FROM historique_carriere hc
            JOIN echelon ec
                ON ec.id = hc.echelon_id
            JOIN classe cl
                ON cl.id = ec.classe_id
            JOIN parcours pa
                ON pa.id = cl.parcours_id
            LEFT JOIN categorie ca
                ON ca.id = hc.categorie_id
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
                ec.code AS grade_code,
                ec.libelle AS grade_libelle,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                pa.libelle AS parcours_libelle
            FROM echelon ec
            JOIN classe cl
                ON cl.id = ec.classe_id
            JOIN parcours pa
                ON pa.id = cl.parcours_id
            WHERE ec.classe_id = ?
              AND ec.ordre = ? + 1
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
                ec.code AS grade_code,
                ec.libelle AS grade_libelle,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                pa.libelle AS parcours_libelle
            FROM echelon ec
            JOIN classe cl
                ON cl.id = ec.classe_id
            JOIN parcours pa
                ON pa.id = cl.parcours_id
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

    /** La classe qui suit celle-ci dans son propre parcours. */
    public Optional<Map<String, Object>> findClasseSuivante(
            Integer classeId
    ) {
        String sql = """
            SELECT
                cl.id,
                cl.libelle,
                cl.ordre
            FROM classe actuelle
            JOIN classe cl
                ON cl.parcours_id = actuelle.parcours_id
               AND cl.ordre > actuelle.ordre
            WHERE actuelle.id = ?
            ORDER BY cl.ordre ASC
            LIMIT 1
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, classeId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    /** Tous les grades, rangés par type d'emploi, parcours, classe et échelon. */
    public List<Map<String, Object>> findGradesParParcours() {
        return jdbcTemplate.queryForList("""
            SELECT
                te.nom AS type_emploi,
                pa.code AS parcours_code,
                pa.libelle AS parcours,
                cl.libelle AS classe,
                ec.id AS grade_id,
                ec.code,
                ec.libelle,
                ec.duree_min
            FROM echelon ec
            JOIN classe cl ON cl.id = ec.classe_id
            JOIN parcours pa ON pa.id = cl.parcours_id
            JOIN type_emploi te ON te.id = pa.type_emploi_id
            ORDER BY te.id, pa.id, cl.ordre, ec.ordre
            """);
    }

    /** L'indice d'un grade pour une catégorie ; absent quand la grille n'a pas cette case. */
    public Optional<Integer> findIndice(Integer categorieId, Integer echelonId) {
        if (categorieId == null || echelonId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.queryForList("""
            SELECT indice
            FROM grille_indiciaire
            WHERE categorie_id = ? AND echelon_id = ?
            """, Integer.class, categorieId, echelonId).stream().findFirst();
    }

    /** Toute la grille : catégorie × grade → indice, par parcours. */
    public List<Map<String, Object>> findGrilleIndiciaire() {
        return jdbcTemplate.queryForList("""
            SELECT
                pa.code AS parcours_code,
                ca.code AS categorie,
                ec.code AS grade_code,
                gi.indice
            FROM grille_indiciaire gi
            JOIN categorie ca ON ca.id = gi.categorie_id
            JOIN echelon ec ON ec.id = gi.echelon_id
            JOIN classe cl ON cl.id = ec.classe_id
            JOIN parcours pa ON pa.id = cl.parcours_id
            ORDER BY pa.id, ca.id, cl.ordre, ec.ordre
            """);
    }
}
