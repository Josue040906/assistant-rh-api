package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AgentDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AgentDocumentInfo> findDocumentInfoById(Integer id) {
        String sql = """
            SELECT
                CONCAT(e.prenom, ' ', e.nom) AS nom_complet,
                e.matricule,
                s.nom AS service,
                p.intitule AS poste
            FROM employe e

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

            WHERE e.id = ?
            """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new AgentDocumentInfo(
                        resultSet.getString("nom_complet"),
                        resultSet.getString("matricule"),
                        resultSet.getString("service"),
                        resultSet.getString("poste")
                ),
                id
        ).stream().findFirst();
    }
}
