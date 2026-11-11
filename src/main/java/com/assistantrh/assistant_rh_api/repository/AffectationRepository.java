package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@Repository
public class AffectationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AffectationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Recupere l'affectation actuellement active d'un agent.
     */
    public Map<String, Object> findActiveByEmployeId(Integer employeId) {

        String sql = """
            SELECT
                a.id,
                a.employe_id,
                a.poste_id,
                p.intitule AS poste,
                a.service_id,
                s.code AS service_code,
                s.nom AS service,
                a.date_debut,
                a.date_fin,
                a.reference_acte,
                a.observation
            FROM affectation a
            JOIN poste p
                ON p.id = a.poste_id
            JOIN service s
                ON s.id = a.service_id
            WHERE
                a.employe_id = ?
                AND a.date_fin IS NULL
            ORDER BY a.date_debut DESC, a.id DESC
            LIMIT 1
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, employeId);

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    /**
     * Termine l'affectation active a la date indiquee.
     */
    public int fermerAffectation(
            Integer affectationId,
            Date dateFin
    ) {
        String sql = """
            UPDATE affectation
            SET date_fin = ?
            WHERE id = ?
              AND date_fin IS NULL
            """;

        return jdbcTemplate.update(
                sql,
                dateFin,
                affectationId
        );
    }

    /**
     * Cree une nouvelle affectation.
     */
    public int creer(
            Integer employeId,
            Integer posteId,
            Integer serviceId,
            Date dateDebut,
            String referenceActe,
            String observation
    ) {
        String sql = """
            INSERT INTO affectation (
                employe_id,
                poste_id,
                service_id,
                date_debut,
                date_fin,
                reference_acte,
                observation
            )
            VALUES (?, ?, ?, ?, NULL, ?, ?)
            """;

        return jdbcTemplate.update(
                sql,
                employeId,
                posteId,
                serviceId,
                dateDebut,
                referenceActe,
                observation
        );
    }

    /**
     * Recupere l'historique des affectations d'un agent.
     */
    public List<Map<String, Object>> findByEmployeId(Integer employeId) {

        String sql = """
            SELECT
                a.id,
                a.employe_id,
                a.poste_id,
                p.intitule AS poste,
                a.service_id,
                s.code AS service_code,
                s.nom AS service,
                a.date_debut,
                a.date_fin,
                a.reference_acte,
                a.observation
            FROM affectation a
            JOIN poste p
                ON p.id = a.poste_id
            JOIN service s
                ON s.id = a.service_id
            WHERE a.employe_id = ?
            ORDER BY a.date_debut DESC, a.id DESC
            """;

        return jdbcTemplate.queryForList(
                sql,
                employeId
        );
    }

    /**
     * Places et occupants d'un poste, hors de l'agent indiqué (qui le quitterait
     * ou y resterait). Le poste est verrouillé jusqu'à la fin de la transaction :
     * deux affectations simultanées ne peuvent pas prendre la même dernière place.
     */
    public Map<String, Object> occupationPoste(Integer posteId, Integer employeId) {
        return jdbcTemplate.queryForMap("""
            SELECT p.intitule, p.nombre_places AS places,
                   (SELECT COUNT(*) FROM affectation a
                    JOIN employe e ON e.id = a.employe_id
                    WHERE a.poste_id = p.id AND a.date_fin IS NULL
                      AND e.date_retraite IS NULL AND a.employe_id <> ?)::int AS occupes
            FROM poste p
            WHERE p.id = ?
            FOR UPDATE OF p
            """, employeId == null ? 0 : employeId, posteId);
    }

    /** Refuse une affectation sur un poste dont toutes les places sont prises. */
    public void verifierPlaceLibre(Integer posteId, Integer employeId) {
        Map<String, Object> occupation = occupationPoste(posteId, employeId);
        int places = ((Number) occupation.get("places")).intValue();
        int occupes = ((Number) occupation.get("occupes")).intValue();
        if (occupes >= places) {
            throw new IllegalArgumentException("Le poste « " + occupation.get("intitule") + " » est complet ("
                    + occupes + "/" + places + ") : libérez une place ou augmentez son nombre de places.");
        }
    }
}
