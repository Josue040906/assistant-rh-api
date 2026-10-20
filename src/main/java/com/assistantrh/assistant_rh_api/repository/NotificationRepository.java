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
                    (hc.date_debut + MAKE_INTERVAL(years => ec.duree_min))::date
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
                id,
                type,
                titre,
                message,
                employe_id,
                acteur_id,
                cree_le,
                lu_le
            FROM notification
            WHERE destinataire_id = ?
              AND (? = FALSE OR lu_le IS NULL)
            ORDER BY cree_le DESC, id DESC
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
                    lueLe == null ? null : lueLe.toLocalDateTime()
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
}
