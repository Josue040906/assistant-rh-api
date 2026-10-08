package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
                e.nom,
                e.prenom,
                e.matricule,
                s.nom AS service,
                d.nom AS direction,
                p.intitule AS poste,
                c.code AS categorie,
                co.libelle AS corps,
                cl.libelle AS classe,
                ec.ordre AS echelon,
                e.date_embauche,
                e.lieu_travail,
                a.poste_id,
                a.service_id,
                d.id AS direction_id
            FROM employe e

            LEFT JOIN categorie c
                ON c.id = e.categorie_id

            LEFT JOIN corps co
                ON co.id = c.corps_id

            LEFT JOIN LATERAL (
                SELECT
                    hc.echelon_id
                FROM historique_carriere hc
                WHERE hc.employe_id = e.id
                  AND hc.date_fin IS NULL
                  AND hc.date_debut <= CURRENT_DATE
                ORDER BY hc.date_debut DESC, hc.id DESC
                LIMIT 1
            ) hc ON TRUE

            LEFT JOIN echelon ec
                ON ec.id = hc.echelon_id

            LEFT JOIN classe cl
                ON cl.id = ec.classe_id

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

            WHERE e.id = ?
            """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new AgentDocumentInfo(
                        resultSet.getString("nom"),
                        resultSet.getString("prenom"),
                        resultSet.getString("matricule"),
                        resultSet.getString("service"),
                        resultSet.getString("direction"),
                        resultSet.getString("poste"),
                        resultSet.getString("categorie"),
                        resultSet.getString("corps"),
                        resultSet.getString("classe"),
                        resultSet.getObject("echelon", Integer.class),
                        resultSet.getObject("date_embauche", LocalDate.class),
                        resultSet.getString("lieu_travail"),
                        resultSet.getObject("direction_id", Integer.class),
                        resultSet.getObject("service_id", Integer.class),
                        resultSet.getObject("poste_id", Integer.class)
                ),
                id
        ).stream().findFirst();
    }
}
