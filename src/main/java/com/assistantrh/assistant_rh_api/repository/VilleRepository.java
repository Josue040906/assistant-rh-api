package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.model.Ville;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class VilleRepository {

    private final JdbcTemplate jdbcTemplate;

    public VilleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Ville> findAll() {
        return jdbcTemplate.query("""
                SELECT DISTINCT ON (lower(nom)) nom
                FROM ville
                ORDER BY lower(nom), nom, source_type, source_id
                """,
                (resultSet, rowNumber) -> new Ville(
                        resultSet.getString("nom")
                )
        );
    }

    public Optional<String> findName(String nom) {
        List<String> matches = jdbcTemplate.query("""
                SELECT nom
                FROM ville
                WHERE lower(nom) = lower(?)
                ORDER BY source_type, source_id
                LIMIT 1
                """,
                (resultSet, rowNumber) -> resultSet.getString("nom"),
                nom
        );
        return matches.stream().findFirst();
    }
}
