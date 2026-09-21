package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class EmployeRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmployeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                p.intitule AS poste,
                s.code AS code_service,
                s.nom AS service,
                d.nom AS direction
            FROM employe e
            JOIN poste p ON e.poste_id = p.id
            JOIN service s ON e.service_id = s.id
            LEFT JOIN direction d ON s.direction_id = d.id
            ORDER BY e.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Optional<Map<String, Object>> findById(Integer id) {

        String sql = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                p.intitule AS poste,
                p.description AS description_poste,
                s.code AS code_service,
                s.nom AS service,
                s.description AS description_service,
                d.nom AS direction
            FROM employe e
            JOIN poste p ON e.poste_id = p.id
            JOIN service s ON e.service_id = s.id
            LEFT JOIN direction d ON s.direction_id = d.id
            WHERE e.id = ?
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, id);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public List<Map<String, Object>> search(String query) {

        String sql = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                p.intitule AS poste,
                s.code AS code_service,
                s.nom AS service,
                d.nom AS direction,

                GREATEST(
                    similarity(lower(e.nom), lower(?)),
                    similarity(lower(e.prenom), lower(?)),
                    similarity(
                        lower(e.prenom || ' ' || e.nom),
                        lower(?)
                    ),
                    similarity(
                        lower(e.nom || ' ' || e.prenom),
                        lower(?)
                    )
                ) AS pertinence

            FROM employe e
            JOIN poste p ON e.poste_id = p.id
            JOIN service s ON e.service_id = s.id
            LEFT JOIN direction d ON s.direction_id = d.id

            WHERE
                e.nom ILIKE ?
                OR e.prenom ILIKE ?
                OR e.matricule ILIKE ?
                OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?
                OR p.intitule ILIKE ?
                OR s.code ILIKE ?
                OR s.nom ILIKE ?
                OR d.nom ILIKE ?

                OR similarity(lower(e.nom), lower(?)) >= 0.30
                OR similarity(lower(e.prenom), lower(?)) >= 0.30
                OR similarity(
                    lower(e.prenom || ' ' || e.nom),
                    lower(?)
                ) >= 0.30
                OR similarity(
                    lower(e.nom || ' ' || e.prenom),
                    lower(?)
                ) >= 0.30

            ORDER BY
                CASE
                    WHEN
                        e.nom ILIKE ?
                        OR e.prenom ILIKE ?
                        OR e.matricule ILIKE ?
                        OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                        OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?
                        OR p.intitule ILIKE ?
                        OR s.code ILIKE ?
                        OR s.nom ILIKE ?
                        OR d.nom ILIKE ?
                    THEN 0
                    ELSE 1
                END,
                pertinence DESC,
                e.id
            """;

        String pattern = "%" + query + "%";

        return jdbcTemplate.queryForList(
                sql,

                // Scores de pertinence
                query,
                query,
                query,
                query,

                // Recherche classique
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,

                // Recherche fuzzy
                query,
                query,
                query,
                query,

                // Priorité aux correspondances classiques
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern
        );
    }

    public Optional<Map<String, Object>> findProfile(String query) {

        String sql = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                p.intitule AS poste,
                p.description AS description_poste,
                s.code AS code_service,
                s.nom AS service,
                s.description AS description_service,
                d.nom AS direction
            FROM employe e
            JOIN poste p ON e.poste_id = p.id
            JOIN service s ON e.service_id = s.id
            LEFT JOIN direction d ON s.direction_id = d.id
            WHERE
                e.nom ILIKE ?
                OR e.prenom ILIKE ?
                OR e.matricule ILIKE ?
                OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?
            LIMIT 1
            """;

        String pattern = "%" + query + "%";

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        pattern,
                        pattern,
                        pattern,
                        pattern,
                        pattern
                );

        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.get(0));
    }



}