
        package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DocumentRhRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRhRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                d.id,
                d.reference,
                d.type,
                d.objet,
                d.contenu,
                d.agent_id,
                d.dossier_id,
                d.statut,
                d.auteur,
                d.date_document,
                d.date_creation,
                d.date_modification,

                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet

            FROM document_rh d
            LEFT JOIN employe e
                ON d.agent_id = e.id
            LEFT JOIN dossier_rh dr
                ON d.dossier_id = dr.id

            ORDER BY d.date_creation DESC, d.id DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Optional<Map<String, Object>> findById(Long id) {

        String sql = """
            SELECT
                d.id,
                d.reference,
                d.type,
                d.objet,
                d.contenu,
                d.agent_id,
                d.dossier_id,
                d.statut,
                d.auteur,
                d.date_document,
                d.date_creation,
                d.date_modification,

                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet

            FROM document_rh d
            LEFT JOIN employe e
                ON d.agent_id = e.id
            LEFT JOIN dossier_rh dr
                ON d.dossier_id = dr.id

            WHERE d.id = ?
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, id);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    public List<Map<String, Object>> search(String query) {

        String recherche = query.trim();
        String pattern = "%" + recherche + "%";

        String sql = """
            SELECT
                d.id,
                d.reference,
                d.type,
                d.objet,
                d.contenu,
                d.agent_id,
                d.dossier_id,
                d.statut,
                d.auteur,
                d.date_document,
                d.date_creation,
                d.date_modification,

                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet

            FROM document_rh d
            LEFT JOIN employe e
                ON d.agent_id = e.id
            LEFT JOIN dossier_rh dr
                ON d.dossier_id = dr.id

            WHERE
                d.reference ILIKE ?
                OR d.type ILIKE ?
                OR d.objet ILIKE ?
                OR d.contenu ILIKE ?
                OR d.statut ILIKE ?
                OR d.auteur ILIKE ?
                OR e.matricule ILIKE ?
                OR e.nom ILIKE ?
                OR e.prenom ILIKE ?
                OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?

            ORDER BY d.date_creation DESC, d.id DESC
            """;

        return jdbcTemplate.queryForList(
                sql,
                pattern,
                pattern,
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

    public List<Map<String, Object>> findByAgentId(Long agentId) {

        String sql = """
            SELECT
                d.id,
                d.reference,
                d.type,
                d.objet,
                d.contenu,
                d.agent_id,
                d.dossier_id,
                d.statut,
                d.auteur,
                d.date_document,
                d.date_creation,
                d.date_modification,

                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet

            FROM document_rh d
            LEFT JOIN employe e
                ON d.agent_id = e.id
            LEFT JOIN dossier_rh dr
                ON d.dossier_id = dr.id

            WHERE d.agent_id = ?

            ORDER BY d.date_creation DESC, d.id DESC
            """;

        return jdbcTemplate.queryForList(sql, agentId);
    }

    public Long create(
            String reference,
            String type,
            String objet,
            String contenu,
            Long agentId,
            Long dossierId,
            String auteur,
            java.sql.Date dateDocument
    ) {

        String sql = """
            INSERT INTO document_rh (
                reference,
                type,
                objet,
                contenu,
                agent_id,
                dossier_id,
                statut,
                auteur,
                date_document
            )
            VALUES (?, ?, ?, ?, ?, ?, 'BROUILLON', ?, ?)
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                reference,
                type,
                objet,
                contenu,
                agentId,
                dossierId,
                auteur,
                dateDocument
        );
    }

    public long countDocuments() {

        String sql = """
            SELECT COUNT(*)
            FROM document_rh
            """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class);

        return count != null ? count : 0L;
    }
}
