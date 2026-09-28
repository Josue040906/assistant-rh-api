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

    public record UtilisateurLoginData(
            int id,
            String email,
            String passwordHash
    ) {
    }
}