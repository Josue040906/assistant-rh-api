package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UtilisateurRepository {

    private final JdbcTemplate jdbcTemplate;

    public UtilisateurRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Integer> findIdByEmail(String email) {

        String sql = """
            SELECT id
            FROM utilisateur
            WHERE email = ?
            """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setString(1, email),
                rs -> {
                    if (rs.next()) {
                        return Optional.of(rs.getInt("id"));
                    }

                    return Optional.empty();
                }
        );
    }

    public Optional<UtilisateurLoginData> findForLogin(String email) {

        String sql = """
            SELECT
                id,
                email,
                password_hash,
                role,
                statut_compte
            FROM utilisateur
            WHERE email = ?
            """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setString(1, email),
                rs -> {
                    if (rs.next()) {
                        return Optional.of(
                                new UtilisateurLoginData(
                                        rs.getInt("id"),
                                        rs.getString("email"),
                                        rs.getString("password_hash"),
                                        rs.getString("role"),
                                        rs.getString("statut_compte")
                                )
                        );
                    }

                    return Optional.empty();
                }
        );
    }

    public Optional<UtilisateurLoginData> findForLoginParId(
            int utilisateurId
    ) {

        String sql = """
            SELECT
                id,
                email,
                password_hash,
                role,
                statut_compte
            FROM utilisateur
            WHERE id = ?
            """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setInt(1, utilisateurId),
                rs -> {
                    if (rs.next()) {
                        return Optional.of(
                                new UtilisateurLoginData(
                                        rs.getInt("id"),
                                        rs.getString("email"),
                                        rs.getString("password_hash"),
                                        rs.getString("role"),
                                        rs.getString("statut_compte")
                                )
                        );
                    }

                    return Optional.empty();
                }
        );
    }

    public boolean mettreAJourMotDePasse(
            int utilisateurId,
            String nouveauPasswordHash
    ) {

        String sql = """
            UPDATE utilisateur
            SET password_hash = ?
            WHERE id = ?
            """;

        int lignesModifiees = jdbcTemplate.update(
                sql,
                nouveauPasswordHash,
                utilisateurId
        );

        return lignesModifiees > 0;
    }

    public boolean existsByEmail(String email) {

        String sql = """
            SELECT EXISTS(
                SELECT 1
                FROM utilisateur
                WHERE LOWER(email) = LOWER(?)
            )
            """;

        Boolean existe = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                email.trim()
        );

        return Boolean.TRUE.equals(existe);
    }

    public int creerUtilisateur(
            String email,
            String passwordHash
    ) {

        String sql = """
            INSERT INTO utilisateur (
                email,
                password_hash,
                role,
                statut_compte
            )
            VALUES (?, ?, 'SPERS_AGENT', 'EN_ATTENTE')
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                email.trim(),
                passwordHash
        );
    }

    /*
     * Récupère les comptes qui attendent une validation.
     *
     * On joint utilisateur et employe afin que SYGPERS
     * puisse afficher directement l'identité de l'agent.
     */
    public List<Map<String, Object>> findComptesEnAttente() {

        String sql = """
            SELECT
                u.id AS user_id,
                u.email,
                u.role,
                u.statut_compte,
                e.id AS employe_id,
                e.matricule,
                e.nom,
                e.prenom,
                e.poste_id,
                e.service_id
            FROM utilisateur u
            INNER JOIN employe e
                ON e.user_id = u.id
            WHERE u.statut_compte = 'EN_ATTENTE'
            ORDER BY u.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /*
     * Vérifie qu'un utilisateur existe et possède le rôle
     * SPERS_CHEF avec un compte actif.
     */
    public boolean estChefActif(int utilisateurId) {

        String sql = """
            SELECT EXISTS(
                SELECT 1
                FROM utilisateur
                WHERE id = ?
                  AND role = 'SPERS_CHEF'
                  AND statut_compte = 'ACTIF'
            )
            """;

        Boolean resultat = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                utilisateurId
        );

        return Boolean.TRUE.equals(resultat);
    }
    /**
     * Vérifie qu'un utilisateur est actif et autorisé
     * à effectuer des opérations dans SYGPERS.
     *
     * Les rôles autorisés sont :
     * - SPERS_AGENT
     * - SPERS_CHEF
     *
     * L'utilisateur doit également être rattaché
     * à un agent du service SPERS (service_id = 2).
     */
    public boolean estAgentSpersActif(int utilisateurId) {

        String sql = """
        SELECT EXISTS(
            SELECT 1
            FROM utilisateur u
            INNER JOIN employe e
                ON e.user_id = u.id
            WHERE u.id = ?
              AND u.statut_compte = 'ACTIF'
              AND u.role IN ('SPERS_AGENT', 'SPERS_CHEF')
              AND e.service_id = 2
        )
        """;

        Boolean resultat = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                utilisateurId
        );

        return Boolean.TRUE.equals(resultat);
    }
    /*
     * Modifie le statut du compte.
     *
     * Pour l'instant, cette méthode ne fait que modifier
     * le statut. L'activité sera enregistrée par le service.
     */
    public boolean mettreAJourStatut(
            int utilisateurId,
            String nouveauStatut
    ) {

        String sql = """
            UPDATE utilisateur
            SET statut_compte = ?
            WHERE id = ?
            """;

        int lignesModifiees = jdbcTemplate.update(
                sql,
                nouveauStatut,
                utilisateurId
        );

        return lignesModifiees > 0;
    }

    public record UtilisateurLoginData(
            int id,
            String email,
            String passwordHash,
            String role,
            String statutCompte
    ) {
    }
}