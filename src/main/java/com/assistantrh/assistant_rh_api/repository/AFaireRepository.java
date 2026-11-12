package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les comptages qui alimentent la liste « À faire ». */
@Repository
public class AFaireRepository {
    private final JdbcTemplate jdbcTemplate;

    public AFaireRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int comptesEnAttente() {
        return compter("SELECT COUNT(*) FROM utilisateur WHERE statut_compte = 'EN_ATTENTE'");
    }

    /** Demandes acceptées dont la date d'effet est atteinte : un humain doit les appliquer. */
    public int demandesApplicables() {
        return compter("SELECT COUNT(*) FROM document WHERE statut = 'ACCEPTE' AND date_effet <= CURRENT_DATE");
    }

    /** Demandes créées dont la réponse (signée sur papier) n'est pas encore saisie. */
    public int demandesSansReponse() {
        return compter("SELECT COUNT(*) FROM document WHERE statut = 'CREE'");
    }

    public int dossiersIncomplets() {
        return compter("SELECT COUNT(*) FROM employe e WHERE "
                + EmployeRepository.DOSSIER_INCOMPLET);
    }

    /** Agents en activité qui atteignent la durée minimale de leur échelon entre ces deux jours. */
    public int ancienneteAtteinteEntre(java.time.LocalDate du, java.time.LocalDate au) {
        return compter("SELECT COUNT(*) FROM historique_carriere hc"
                + " JOIN echelon ec ON ec.id = hc.echelon_id"
                + " JOIN employe e ON e.id = hc.employe_id"
                + " WHERE hc.date_fin IS NULL AND e.date_retraite IS NULL"
                + " AND " + SituationCarriereRepository.DATE_ANCIENNETE_ECHELON + " BETWEEN ? AND ?",
                java.sql.Date.valueOf(du), java.sql.Date.valueOf(au));
    }

    /** Agents en activité embauchés entre ces deux jours. */
    public int embauchesEntre(java.time.LocalDate du, java.time.LocalDate au) {
        return compter("SELECT COUNT(*) FROM employe WHERE date_retraite IS NULL AND date_embauche BETWEEN ? AND ?",
                java.sql.Date.valueOf(du), java.sql.Date.valueOf(au));
    }

    /** Agents en activité qui ont encore des jours de congé dans l'année. */
    public int agentsAvecSoldeConge() {
        return compter("SELECT COUNT(*) FROM employe e WHERE e.date_retraite IS NULL AND "
                + CongeRepository.SOLDE_CONGE + " > 0");
    }

    private int compter(String sql, Object... arguments) {
        Integer nombre = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return nombre == null ? 0 : nombre;
    }
}
