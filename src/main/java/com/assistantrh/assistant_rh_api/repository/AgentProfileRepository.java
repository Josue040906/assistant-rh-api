package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AgentProfileRepository {
    private final JdbcTemplate jdbcTemplate;

    public AgentProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findProfile(String query) {
        return findProfileCandidates(query).stream().findFirst();
    }

    public List<Map<String, Object>> findProfileCandidates(String query) {
        String search = query == null ? "" : query.trim();
        if (search.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT
                    e.id,
                    e.matricule,
                    e.nom,
                    e.prenom,
                    e.sexe,
                    e.date_naissance,
                    e.date_embauche,
                    e.photo,
                    a.poste_id,
                    p.intitule AS poste,
                    p.description AS description_poste,
                    a.service_id,
                    s.code AS code_service,
                    s.nom AS service,
                    s.description AS description_service,
                    d.id AS direction_id,
                    d.nom AS direction,
                    te.nom AS type_emploi,
                    c.code AS categorie,
                    co.libelle AS corps
                FROM employe e
                LEFT JOIN LATERAL (
                    SELECT af.poste_id, af.service_id, af.date_debut, af.id
                    FROM affectation af
                    WHERE af.employe_id = e.id
                      AND af.date_fin IS NULL
                    ORDER BY af.date_debut DESC, af.id DESC
                    LIMIT 1
                ) a ON TRUE
                LEFT JOIN poste p ON p.id = a.poste_id
                LEFT JOIN service s ON s.id = a.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                LEFT JOIN type_emploi te ON te.id = e.type_emploi_id
                LEFT JOIN categorie c ON c.id = e.categorie_id
                LEFT JOIN corps co ON co.id = c.corps_id
                WHERE
                    e.nom ILIKE ?
                    OR e.prenom ILIKE ?
                    OR e.matricule ILIKE ?
                    OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                    OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?
                ORDER BY
                    CASE
                        WHEN LOWER(e.matricule) = LOWER(?) THEN 1
                        WHEN LOWER(CONCAT(e.prenom, ' ', e.nom))
                             = LOWER(?) THEN 2
                        WHEN LOWER(CONCAT(e.nom, ' ', e.prenom))
                             = LOWER(?) THEN 3
                        WHEN LOWER(e.nom) = LOWER(?) THEN 4
                        WHEN LOWER(e.prenom) = LOWER(?) THEN 5
                        ELSE 6
                    END,
                    e.id
                LIMIT 2
                """;

        String pattern = "%" + search + "%";
        return jdbcTemplate.queryForList(
                sql,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                search,
                search,
                search,
                search,
                search
        );
    }
}
