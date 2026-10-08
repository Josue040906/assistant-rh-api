package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.model.Activite;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ActiviteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ActiviteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int enregistrer(
            Integer acteurId,
            Integer employeId,
            String typeAction,
            String description
    ) {

        String sql = """
            INSERT INTO activite (
                acteur_id,
                employe_id,
                type_action,
                description
            )
            VALUES (?, ?, ?, ?)
            """;

        return jdbcTemplate.update(
                sql,
                acteurId,
                employeId,
                typeAction,
                description
        );
    }

    public List<Activite> findByActeurId(Integer acteurId) {
        String sql = """
            SELECT
                a.id,
                a.acteur_id,
                a.employe_id,
                CASE a.type_action
                    WHEN 'CREATION_AGENT' THEN 'CREATION'
                    WHEN 'CREATION_COMPTE' THEN 'CREATION'
                    WHEN 'NOUVEAU_DOCUMENT' THEN 'CREATION'
                    WHEN 'MODIFICATION_AGENT' THEN 'MODIFICATION'
                    WHEN 'MODIFICATION_PROFIL' THEN 'MODIFICATION'
                    WHEN 'MODIFICATION_AFFECTATION' THEN 'MODIFICATION'
                    WHEN 'MODIFICATION_DOCUMENT' THEN 'MODIFICATION'
                    WHEN 'ARCHIVAGE_DOCUMENT' THEN 'MODIFICATION'
                    WHEN 'APPROBATION_COMPTE' THEN 'VALIDATION'
                    WHEN 'REFUS_COMPTE' THEN 'REFUS'
                    WHEN 'GENERATION_DOCUMENT' THEN 'GENERATION'
                    WHEN 'TELEVERSEMENT_PHOTO' THEN 'TELEVERSEMENT'
                    ELSE a.type_action
                END AS type_action,
                a.description,
                a.date_heure,
                ua.email AS acteur_email,
                ee.matricule AS employe_matricule,
                ee.nom AS employe_nom,
                ee.prenom AS employe_prenom
            FROM activite a
            LEFT JOIN utilisateur ua
                ON ua.id = a.acteur_id
            LEFT JOIN employe ee
                ON ee.id = a.employe_id
            WHERE a.acteur_id = ?
            ORDER BY a.date_heure DESC, a.id DESC
            """;

        return jdbcTemplate.query(
                sql,
                activiteRowMapper(),
                acteurId
        );
    }

    private RowMapper<Activite> activiteRowMapper() {
        return (resultSet, rowNumber) -> {
            Timestamp dateHeure = resultSet.getTimestamp("date_heure");
            return new Activite(
                    resultSet.getLong("id"),
                    lireInteger(resultSet, "acteur_id"),
                    lireInteger(resultSet, "employe_id"),
                    resultSet.getString("type_action"),
                    resultSet.getString("description"),
                    dateHeure == null ? null : dateHeure.toLocalDateTime(),
                    resultSet.getString("acteur_email"),
                    resultSet.getString("employe_matricule"),
                    resultSet.getString("employe_nom"),
                    resultSet.getString("employe_prenom")
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