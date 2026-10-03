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
            WHERE LOWER(email) = LOWER(?)
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
            WHERE LOWER(email) = LOWER(?)
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

    /**
     * Comptes en attente avec l'identité et l'affectation actuelle.
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

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                d.id AS direction_id,
                d.nom AS direction

            FROM utilisateur u

            INNER JOIN employe e
                ON e.user_id = u.id

            LEFT JOIN LATERAL (
                SELECT
                    af.poste_id,
                    af.service_id,
                    af.date_debut,
                    af.id
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            WHERE u.statut_compte = 'EN_ATTENTE'

            ORDER BY u.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Vérifie qu'un utilisateur possède le rôle SPERS_CHEF
     * et dispose d'un compte actif.
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
     * Vérifie qu'un utilisateur actif est autorisé à utiliser SYGPERS.
     *
     * Le rattachement au Service du Personnel est déterminé
     * par l'affectation actuelle de l'agent.
     */
    public boolean estAgentSpersActif(int utilisateurId) {

        String sql = """
            SELECT EXISTS(
                SELECT 1
                FROM utilisateur u

                INNER JOIN employe e
                    ON e.user_id = u.id

                INNER JOIN LATERAL (
                    SELECT
                        af.service_id,
                        af.date_debut,
                        af.id
                    FROM affectation af
                    WHERE af.employe_id = e.id
                      AND af.date_fin IS NULL
                    ORDER BY af.date_debut DESC, af.id DESC
                    LIMIT 1
                ) a ON TRUE

                WHERE u.id = ?
                  AND u.statut_compte = 'ACTIF'
                  AND u.role IN ('SPERS_AGENT', 'SPERS_CHEF')
                  AND a.service_id = 2
            )
            """;

        Boolean resultat = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                utilisateurId
        );

        return Boolean.TRUE.equals(resultat);
    }

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