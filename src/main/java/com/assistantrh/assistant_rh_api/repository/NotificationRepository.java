package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.model.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int notifierNouvelAgent(Integer employeId, Integer acteurId) {
        String sql = """
            WITH agent AS (
                SELECT id, matricule, nom, prenom
                FROM employe
                WHERE id = ?
            ),
            createur AS (
                SELECT
                    u.id,
                    COALESCE(
                        NULLIF(BTRIM(CONCAT_WS(' ', e.prenom, e.nom)), ''),
                        u.email
                    ) AS nom
                FROM utilisateur u
                LEFT JOIN employe e
                    ON e.user_id = u.id
                WHERE u.id = ?
            ),
            destinataires AS (
                SELECT DISTINCT u.id
                FROM utilisateur u
                JOIN employe e
                    ON e.user_id = u.id
                JOIN LATERAL (
                    SELECT af.service_id
                    FROM affectation af
                    WHERE af.employe_id = e.id
                      AND af.date_fin IS NULL
                    ORDER BY af.date_debut DESC, af.id DESC
                    LIMIT 1
                ) affectation_actuelle ON TRUE
                WHERE u.statut_compte = 'ACTIF'
                  AND u.role IN ('SPERS_AGENT', 'SPERS_CHEF')
                  AND affectation_actuelle.service_id = 2
            )
            INSERT INTO notification (
                destinataire_id,
                type,
                titre,
                message,
                employe_id,
                acteur_id,
                cle_deduplication
            )
            SELECT
                destinataires.id,
                'NOUVEL_AGENT',
                'Nouvel agent ajouté',
                CONCAT(
                    'Agent ',
                    BTRIM(CONCAT_WS(' ', agent.prenom, agent.nom)),
                    ' (',
                    agent.matricule,
                    ') créé par ',
                    createur.nom,
                    '.'
                ),
                agent.id,
                createur.id,
                CONCAT('agent-cree:', agent.id)
            FROM agent
            CROSS JOIN createur
            CROSS JOIN destinataires
            ON CONFLICT (destinataire_id, cle_deduplication) DO NOTHING
            """;

        return jdbcTemplate.update(sql, employeId, acteurId);
    }

    /**
     * Surveillance : chaque chef actif du Service du Personnel (sauf celui qui
     * valide) est notifié qu'un document a été validé et son effet appliqué.
     */
    /** Le chef du Service du Personnel et l'auteur de la demande (sauf celui qui l'applique). */
    public int notifierDocumentApplique(long documentId, Integer acteurId) {
        return notifierEtape(documentId, acteurId, "DOCUMENT_APPLIQUE", "Demande appliquée", "appliquée par");
    }

    /** La réponse saisie : acceptée ou refusée (même destinataires que l'application). */
    public int notifierDecision(long documentId, Integer acteurId, boolean acceptee) {
        return acceptee
                ? notifierEtape(documentId, acteurId, "DOCUMENT_ACCEPTE", "Demande acceptée", "acceptée, réponse saisie par")
                : notifierEtape(documentId, acteurId, "DOCUMENT_REFUSE", "Demande refusée", "refusée, réponse saisie par");
    }

    private int notifierEtape(long documentId, Integer acteurId, String type, String titre, String verbe) {
        String sql = """
            WITH doc AS (
                SELECT d.id, d.reference_document, d.objet, d.employe_id, d.auteur_id,
                       BTRIM(CONCAT_WS(' ', e.prenom, e.nom)) AS agent
                FROM document d
                LEFT JOIN employe e ON e.id = d.employe_id
                WHERE d.id = ?
            ),
            valideur AS (
                SELECT u.id,
                       COALESCE(NULLIF(BTRIM(CONCAT_WS(' ', e.prenom, e.nom)), ''), u.email) AS nom
                FROM utilisateur u
                LEFT JOIN employe e ON e.user_id = u.id
                WHERE u.id = ?
            )
            INSERT INTO notification (
                destinataire_id, type, titre, message, employe_id, acteur_id, cle_deduplication
            )
            SELECT dest.id,
                   ?,
                   ?,
                   CONCAT(doc.objet, ' ', doc.reference_document,
                          CASE WHEN doc.agent <> '' THEN CONCAT(' (', doc.agent, ')') ELSE '' END,
                          ' ', ?, ' ', valideur.nom, '.'),
                   doc.employe_id,
                   valideur.id,
                   CONCAT(LOWER(REPLACE(?, '_', '-')), ':', doc.id)
            FROM doc
            CROSS JOIN valideur
            JOIN utilisateur dest
              ON dest.statut_compte = 'ACTIF'
             AND (dest.role = 'SPERS_CHEF' OR dest.id = doc.auteur_id)
             AND dest.id <> valideur.id
            ON CONFLICT (destinataire_id, cle_deduplication) DO NOTHING
            """;
        return jdbcTemplate.update(sql, documentId, acteurId, type, titre, verbe, type);
    }

    public int creerNotificationsAnciennete() {
        String sql = """
            WITH situations AS (
                SELECT
                    hc.id AS situation_id,
                    e.id AS employe_id,
                    e.matricule,
                    e.nom,
                    e.prenom,
                    ec.ordre AS echelon,
                    cl.libelle AS classe,
                    """ + SituationCarriereRepository.DATE_ANCIENNETE_ECHELON + """
                        AS date_eligibilite
                FROM historique_carriere hc
                JOIN employe e
                    ON e.id = hc.employe_id
                JOIN echelon ec
                    ON ec.id = hc.echelon_id
                JOIN classe cl
                    ON cl.id = ec.classe_id
                WHERE hc.date_fin IS NULL
                  AND hc.date_debut <= CURRENT_DATE
                  AND ec.duree_min > 0
            ),
            evenements AS (
                SELECT
                    situations.*,
                    CASE
                        WHEN date_eligibilite <= CURRENT_DATE
                            THEN 'ANCIENNETE_ATTEINTE'
                        ELSE 'ANCIENNETE_UN_MOIS'
                    END AS type_evenement
                FROM situations
                WHERE date_eligibilite <= CURRENT_DATE
                   OR date_eligibilite <= (CURRENT_DATE + INTERVAL '1 month')::date
            ),
            destinataires AS (
                SELECT DISTINCT u.id
                FROM utilisateur u
                JOIN employe e
                    ON e.user_id = u.id
                JOIN LATERAL (
                    SELECT af.service_id
                    FROM affectation af
                    WHERE af.employe_id = e.id
                      AND af.date_fin IS NULL
                    ORDER BY af.date_debut DESC, af.id DESC
                    LIMIT 1
                ) affectation_actuelle ON TRUE
                WHERE u.statut_compte = 'ACTIF'
                  AND u.role IN ('SPERS_AGENT', 'SPERS_CHEF')
                  AND affectation_actuelle.service_id = 2
            )
            INSERT INTO notification (
                destinataire_id,
                type,
                titre,
                message,
                employe_id,
                cle_deduplication
            )
            SELECT
                destinataires.id,
                evenements.type_evenement,
                CASE evenements.type_evenement
                    WHEN 'ANCIENNETE_ATTEINTE'
                        THEN 'Condition d''ancienneté remplie'
                    ELSE 'Condition d''ancienneté à atteindre dans le mois'
                END,
                CONCAT(
                    'Agent ',
                    BTRIM(CONCAT_WS(' ', evenements.prenom, evenements.nom)),
                    ' (',
                    evenements.matricule,
                    ') : ',
                    evenements.classe,
                    ', échelon ',
                    evenements.echelon,
                    ', éligibilité le ',
                    TO_CHAR(evenements.date_eligibilite, 'DD/MM/YYYY'),
                    '.'
                ),
                evenements.employe_id,
                CONCAT(
                    'anciennete:',
                    evenements.situation_id,
                    ':',
                    LOWER(evenements.type_evenement)
                )
            FROM evenements
            CROSS JOIN destinataires
            ON CONFLICT (destinataire_id, cle_deduplication) DO NOTHING
            """;

        return jdbcTemplate.update(sql);
    }

    public List<Notification> findForRecipient(
            Integer destinataireId,
            boolean nonLuesSeulement,
            int limite
    ) {
        String sql = """
            SELECT
                n.id,
                n.type,
                n.titre,
                n.message,
                n.employe_id,
                n.acteur_id,
                n.cree_le,
                n.lu_le,
                lie.id AS message_id,
                lie.statut AS message_statut,
                (SELECT BTRIM(CONCAT_WS(' ', e.prenom, e.nom)) FROM employe e
                 WHERE e.id = n.employe_id) AS employe_nom
            FROM notification n
            LEFT JOIN LATERAL (
                SELECT m.id, m.statut
                FROM message_agent m
                WHERE m.employe_id = n.employe_id
                  AND (m.cle_deduplication = 'notification:' || n.id
                       OR m.cle_deduplication = CASE n.type
                           WHEN 'ANCIENNETE_ATTEINTE' THEN REPLACE(n.cle_deduplication, ':anciennete_atteinte', ':atteinte')
                           WHEN 'ANCIENNETE_UN_MOIS' THEN REPLACE(n.cle_deduplication, ':anciennete_un_mois', ':un-mois')
                           WHEN 'RETRAITE_PROCHE' THEN n.cle_deduplication
                           WHEN 'DOCUMENT_ACCEPTE' THEN REPLACE(n.cle_deduplication, 'document-accepte:', 'demande-acceptee:')
                           WHEN 'DOCUMENT_REFUSE' THEN REPLACE(n.cle_deduplication, 'document-refuse:', 'demande-refusee:')
                       END
                       OR (n.type = 'DOCUMENT_APPLIQUE'
                           AND m.type IN ('MUTATION', 'AVANCEMENT', 'RECLASSEMENT', 'TITULARISATION', 'RETRAITE')
                           AND m.cree_le BETWEEN n.cree_le - INTERVAL '1 day' AND n.cree_le + INTERVAL '1 day'))
                ORDER BY (m.statut = 'PROPOSE') DESC, m.cree_le DESC, m.id DESC
                LIMIT 1
            ) lie ON TRUE
            WHERE n.destinataire_id = ?
              AND (? = FALSE OR n.lu_le IS NULL)
            ORDER BY n.cree_le DESC, n.id DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                notificationRowMapper(),
                destinataireId,
                nonLuesSeulement,
                limite
        );
    }

    /**
     * Une notification de ce destinataire, avec l'agent concerné et l'e-mail déjà lié
     * (même règle que la liste) : id, type, titre, message, employe_id, message_id.
     */
    public java.util.Optional<java.util.Map<String, Object>> pourDestinataire(long notificationId, Integer destinataireId) {
        return jdbcTemplate.queryForList("""
            SELECT n.id, n.type, n.titre, n.message, n.employe_id, n.cle_deduplication, lie.id AS message_id
            FROM notification n
            LEFT JOIN LATERAL (
                SELECT m.id, m.statut
                FROM message_agent m
                WHERE m.employe_id = n.employe_id
                  AND (m.cle_deduplication = 'notification:' || n.id
                       OR m.cle_deduplication = CASE n.type
                           WHEN 'ANCIENNETE_ATTEINTE' THEN REPLACE(n.cle_deduplication, ':anciennete_atteinte', ':atteinte')
                           WHEN 'ANCIENNETE_UN_MOIS' THEN REPLACE(n.cle_deduplication, ':anciennete_un_mois', ':un-mois')
                           WHEN 'RETRAITE_PROCHE' THEN n.cle_deduplication
                           WHEN 'DOCUMENT_ACCEPTE' THEN REPLACE(n.cle_deduplication, 'document-accepte:', 'demande-acceptee:')
                           WHEN 'DOCUMENT_REFUSE' THEN REPLACE(n.cle_deduplication, 'document-refuse:', 'demande-refusee:')
                       END
                       OR (n.type = 'DOCUMENT_APPLIQUE'
                           AND m.type IN ('MUTATION', 'AVANCEMENT', 'RECLASSEMENT', 'TITULARISATION', 'RETRAITE')
                           AND m.cree_le BETWEEN n.cree_le - INTERVAL '1 day' AND n.cree_le + INTERVAL '1 day'))
                ORDER BY (m.statut = 'PROPOSE') DESC, m.cree_le DESC, m.id DESC
                LIMIT 1
            ) lie ON TRUE
            WHERE n.id = ? AND n.destinataire_id = ?
            """, notificationId, destinataireId).stream().findFirst();
    }

    /**
     * Chaque jour : le départ à la retraite devient possible dans les six mois
     * (durée de service exigée, donnée du type de document RETRAITE).
     */
    public int creerNotificationsRetraiteProche() {
        return jdbcTemplate.update("""
            WITH duree AS (
                SELECT anciennete_service_min_annees AS annees FROM type_document
                WHERE code = 'RETRAITE' AND anciennete_service_min_annees IS NOT NULL
                LIMIT 1
            ),
            proches AS (
                SELECT e.id AS employe_id, e.matricule, e.nom, e.prenom, duree.annees,
                       (e.date_embauche + MAKE_INTERVAL(years => duree.annees))::date AS date_possible
                FROM employe e CROSS JOIN duree
                WHERE e.date_retraite IS NULL AND e.date_embauche IS NOT NULL
                  AND (e.date_embauche + MAKE_INTERVAL(years => duree.annees))::date
                      BETWEEN CURRENT_DATE AND (CURRENT_DATE + INTERVAL '6 months')::date
            ),
            destinataires AS (
                SELECT DISTINCT u.id
                FROM utilisateur u
                WHERE u.statut_compte = 'ACTIF'
                  AND u.role IN ('SPERS_AGENT', 'SPERS_CHEF')
            )
            INSERT INTO notification (destinataire_id, type, titre, message, employe_id, cle_deduplication)
            SELECT destinataires.id,
                   'RETRAITE_PROCHE',
                   'Départ à la retraite possible dans moins de six mois',
                   CONCAT('Agent ', BTRIM(CONCAT_WS(' ', proches.prenom, proches.nom)), ' (', proches.matricule,
                          ') : départ à la retraite possible le ', TO_CHAR(proches.date_possible, 'DD/MM/YYYY'),
                          ', après ', proches.annees, ' ans de service.'),
                   proches.employe_id,
                   CONCAT('retraite-proche:', proches.employe_id, ':', proches.date_possible)
            FROM proches CROSS JOIN destinataires
            ON CONFLICT (destinataire_id, cle_deduplication) DO NOTHING
            """);
    }

    public long countUnread(Integer destinataireId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notification
                WHERE destinataire_id = ?
                  AND lu_le IS NULL
                """,
                Long.class,
                destinataireId
        );
        return count == null ? 0 : count;
    }

    public boolean marquerLue(long notificationId, Integer destinataireId) {
        return jdbcTemplate.update(
                """
                UPDATE notification
                SET lu_le = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND destinataire_id = ?
                  AND lu_le IS NULL
                """,
                notificationId,
                destinataireId
        ) > 0;
    }

    /** Supprime une notification de son seul destinataire. */
    public boolean supprimer(long notificationId, Integer destinataireId) {
        return jdbcTemplate.update(
                "DELETE FROM notification WHERE id = ? AND destinataire_id = ?",
                notificationId,
                destinataireId
        ) > 0;
    }

    public int marquerToutesLues(Integer destinataireId) {
        return jdbcTemplate.update(
                """
                UPDATE notification
                SET lu_le = CURRENT_TIMESTAMP
                WHERE destinataire_id = ?
                  AND lu_le IS NULL
                """,
                destinataireId
        );
    }

    private RowMapper<Notification> notificationRowMapper() {
        return (resultSet, rowNumber) -> {
            Timestamp creeLe = resultSet.getTimestamp("cree_le");
            Timestamp lueLe = resultSet.getTimestamp("lu_le");
            return new Notification(
                    resultSet.getLong("id"),
                    resultSet.getString("type"),
                    resultSet.getString("titre"),
                    resultSet.getString("message"),
                    lireInteger(resultSet, "employe_id"),
                    lireInteger(resultSet, "acteur_id"),
                    creeLe == null ? null : creeLe.toLocalDateTime(),
                    lueLe == null ? null : lueLe.toLocalDateTime(),
                    resultSet.getObject("message_id") == null ? null : resultSet.getLong("message_id"),
                    resultSet.getString("employe_nom"),
                    resultSet.getString("message_statut")
            );
        };
    }

    private Integer lireInteger(
            java.sql.ResultSet resultSet,
            String colonne
    ) throws java.sql.SQLException {
        Object valeur = resultSet.getObject(colonne);
        return valeur instanceof Number nombre
                ? Math.toIntExact(nombre.longValue())
                : null;
    }

    /**
     * Prévient l'auteur (un chef si l'auteur est inconnu) de chaque demande
     * créée dont la date d'effet est atteinte : elle peut être appliquée.
     */
    public int creerNotificationsDemandesApplicables() {
        return jdbcTemplate.update("""
            INSERT INTO notification (
                destinataire_id, type, titre, message, employe_id, acteur_id, cle_deduplication
            )
            SELECT dest.id,
                   'DOCUMENT_APPLICABLE',
                   'Demande à appliquer',
                   CONCAT(d.objet, ' ', d.reference_document,
                          CASE WHEN BTRIM(CONCAT_WS(' ', e.prenom, e.nom)) <> ''
                               THEN CONCAT(' (', BTRIM(CONCAT_WS(' ', e.prenom, e.nom)), ')') ELSE '' END,
                          ' : sa date d''effet du ', TO_CHAR(d.date_effet, 'DD/MM/YYYY'),
                          ' est atteinte, elle peut être appliquée.'),
                   d.employe_id,
                   NULL,
                   CONCAT('document-applicable:', d.id)
            FROM document d
            LEFT JOIN employe e ON e.id = d.employe_id
            JOIN utilisateur dest
              ON dest.statut_compte = 'ACTIF'
             AND (dest.id = d.auteur_id OR (d.auteur_id IS NULL AND dest.role = 'SPERS_CHEF'))
            WHERE d.statut = 'ACCEPTE'
              AND d.date_effet <= CURRENT_DATE
            ON CONFLICT (destinataire_id, cle_deduplication) DO NOTHING
            """);
    }
}
