package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Les e-mails aux agents : brouillons proposés, puis envoyés, écartés ou en échec. */
@Repository
public class MessageAgentRepository {

    private static final String COLONNES = """
            m.id, m.employe_id, m.type, m.sujet, m.corps, m.statut, m.cree_le, m.adresse,
            m.traite_le, m.erreur, m.reference_fournisseur,
            e.matricule, e.nom, e.prenom, e.email AS email_agent,
            (SELECT BTRIM(CONCAT_WS(' ', ep.prenom, ep.nom)) FROM employe ep WHERE ep.user_id = m.traite_par) AS traite_par_nom
            """;

    private final JdbcTemplate jdbcTemplate;

    public MessageAgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Un brouillon pour cet événement ; un événement déjà proposé ne l'est pas deux fois. */
    public boolean proposer(Integer employeId, String type, String sujet, String corps, String cle) {
        return jdbcTemplate.update("""
                INSERT INTO message_agent (employe_id, type, sujet, corps, cle_deduplication)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (cle_deduplication) DO NOTHING
                """, employeId, type, sujet, corps, cle) == 1;
    }

    /**
     * Les messages, filtrés au besoin par statut, par agent et par fraîcheur (préparés
     * depuis « recentsMinutes » minutes, selon l'horloge de la base), les plus récents d'abord.
     */
    public List<Map<String, Object>> lister(String statut, Integer employeId, Integer recentsMinutes, int limite) {
        return jdbcTemplate.queryForList("SELECT " + COLONNES + """
                FROM message_agent m JOIN employe e ON e.id = m.employe_id
                WHERE (CAST(? AS VARCHAR) IS NULL OR m.statut = ?)
                  AND (CAST(? AS INTEGER) IS NULL OR m.employe_id = ?)
                  AND (CAST(? AS INTEGER) IS NULL OR m.cree_le >= CURRENT_TIMESTAMP - MAKE_INTERVAL(mins => CAST(? AS INTEGER)))
                ORDER BY COALESCE(m.traite_le, m.cree_le) DESC, m.id DESC
                LIMIT ?
                """, statut, statut, employeId, employeId, recentsMinutes, recentsMinutes, limite);
    }

    public Optional<Map<String, Object>> trouver(long id) {
        return jdbcTemplate.queryForList("SELECT " + COLONNES + """
                FROM message_agent m JOIN employe e ON e.id = m.employe_id
                WHERE m.id = ?
                """, id).stream().findFirst();
    }

    public int compterProposes() {
        Integer nombre = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_agent WHERE statut = 'PROPOSE'", Integer.class);
        return nombre == null ? 0 : nombre;
    }

    /**
     * Enregistre l'issue d'un envoi (ENVOYE ou ECHEC) avec le texte réellement envoyé,
     * seulement si le message attendait encore (proposé, ou en échec à retenter).
     */
    public boolean enregistrerEnvoi(long id, String statut, String adresse, String sujet, String corps,
                                    Integer acteurId, String reference, String erreur) {
        return jdbcTemplate.update("""
                UPDATE message_agent
                SET statut = ?, adresse = ?, sujet = ?, corps = ?, traite_par = ?, traite_le = CURRENT_TIMESTAMP,
                    reference_fournisseur = ?, erreur = ?
                WHERE id = ? AND statut IN ('PROPOSE', 'ECHEC')
                """, statut, adresse, sujet, corps, acteurId, reference, erreur, id) == 1;
    }

    public boolean ecarter(long id, Integer acteurId) {
        return jdbcTemplate.update("""
                UPDATE message_agent SET statut = 'ECARTE', traite_par = ?, traite_le = CURRENT_TIMESTAMP
                WHERE id = ? AND statut IN ('PROPOSE', 'ECHEC')
                """, acteurId, id) == 1;
    }

    /**
     * Les agents dont l'ancienneté d'échelon est atteinte, ou le sera dans le mois :
     * situation, grade et jour où la condition est remplie.
     */
    public List<Map<String, Object>> anciennetesProches() {
        return jdbcTemplate.queryForList("""
                SELECT hc.id AS situation_id, hc.employe_id, ec.code AS grade_code, ec.libelle AS grade_libelle,
                       """ + SituationCarriereRepository.DATE_ANCIENNETE_ECHELON + """
                           AS date_eligibilite
                FROM historique_carriere hc
                JOIN echelon ec ON ec.id = hc.echelon_id
                JOIN employe e ON e.id = hc.employe_id
                WHERE hc.date_fin IS NULL AND hc.date_debut <= CURRENT_DATE AND ec.duree_min > 0
                  AND e.date_retraite IS NULL
                  AND """ + SituationCarriereRepository.DATE_ANCIENNETE_ECHELON + """
                       <= (CURRENT_DATE + INTERVAL '1 month')::date
                """);
    }

    /** Les agents qui pourront partir à la retraite dans les six mois (et pas encore partis). */
    public List<Map<String, Object>> retraitesProches(int anneesRequises) {
        return jdbcTemplate.queryForList("""
                SELECT e.id AS employe_id, (e.date_embauche + MAKE_INTERVAL(years => ?))::date AS date_possible
                FROM employe e
                WHERE e.date_retraite IS NULL AND e.date_embauche IS NOT NULL
                  AND (e.date_embauche + MAKE_INTERVAL(years => ?))::date
                      BETWEEN CURRENT_DATE AND (CURRENT_DATE + INTERVAL '6 months')::date
                """, anneesRequises, anneesRequises);
    }
}
