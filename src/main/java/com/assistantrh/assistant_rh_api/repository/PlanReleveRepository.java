package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Le successeur pressenti de chaque poste, sa préparation et une note. */
@Repository
public class PlanReleveRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlanReleveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> plans() {
        return jdbcTemplate.queryForList("""
                SELECT pr.poste_id, pr.successeur_id, pr.statut, pr.note, pr.designe_le, pr.modifie_le,
                       e.prenom AS successeur_prenom, e.nom AS successeur_nom, e.matricule AS successeur_matricule,
                       (SELECT BTRIM(CONCAT_WS(' ', ep.prenom, ep.nom)) FROM employe ep WHERE ep.user_id = pr.designe_par)
                           AS designe_par_nom
                FROM plan_releve pr JOIN employe e ON e.id = pr.successeur_id
                """);
    }

    /** Désigne (ou remplace) le successeur pressenti du poste. */
    public void enregistrer(Integer posteId, Integer successeurId, String statut, String note, Integer acteurId) {
        jdbcTemplate.update("""
                INSERT INTO plan_releve (poste_id, successeur_id, statut, note, designe_par)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (poste_id) DO UPDATE
                SET statut = EXCLUDED.statut, note = EXCLUDED.note, modifie_le = CURRENT_TIMESTAMP,
                    -- Un autre successeur : la désignation repart de zéro (qui, quand).
                    designe_par = CASE WHEN plan_releve.successeur_id = EXCLUDED.successeur_id
                                       THEN plan_releve.designe_par ELSE EXCLUDED.designe_par END,
                    designe_le = CASE WHEN plan_releve.successeur_id = EXCLUDED.successeur_id
                                      THEN plan_releve.designe_le ELSE CURRENT_TIMESTAMP END,
                    successeur_id = EXCLUDED.successeur_id
                """, posteId, successeurId, statut, note, acteurId);
    }

    public boolean supprimer(Integer posteId) {
        return jdbcTemplate.update("DELETE FROM plan_releve WHERE poste_id = ?", posteId) == 1;
    }

    /** L'agent est-il en activité, et titulaire de ce poste ? (existe, titulaire) */
    public Map<String, Object> situationAgent(Integer employeId, Integer posteId) {
        return jdbcTemplate.queryForMap("""
                SELECT EXISTS(SELECT 1 FROM employe WHERE id = ? AND date_retraite IS NULL) AS actif,
                       EXISTS(SELECT 1 FROM affectation WHERE employe_id = ? AND poste_id = ? AND date_fin IS NULL) AS titulaire,
                       EXISTS(SELECT 1 FROM poste WHERE id = ?) AS poste_existe
                """, employeId, employeId, posteId, posteId);
    }
}
