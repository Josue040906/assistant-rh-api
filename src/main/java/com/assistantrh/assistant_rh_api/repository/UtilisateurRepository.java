package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                SELECT id, email, password_hash
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
                                            rs.getString("password_hash")
                                    )
                            );
                        }

                        return Optional.empty();
                    }
            );
        }

    public Optional<UtilisateurLoginData> findForLoginParId(int utilisateurId) {

        String sql = """
        SELECT id, email, password_hash
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
                                        rs.getString("password_hash")
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
                password_hash
            )
            VALUES (?, ?)
            RETURNING id
            """;

            return jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    email.trim(),
                    passwordHash
            );
        }
        public record UtilisateurLoginData(
                int id,
                String email,
                String passwordHash
        ) {
        }
}