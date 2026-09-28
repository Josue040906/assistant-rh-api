package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
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

                s.id AS service_id,
                s.nom AS agent_service,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet,
                dr.statut AS dossier_statut

            FROM document_rh d

            LEFT JOIN employe e
                ON d.agent_id = e.id

            LEFT JOIN service s
                ON e.service_id = s.id

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

                s.id AS service_id,
                s.nom AS agent_service,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet,
                dr.statut AS dossier_statut

            FROM document_rh d

            LEFT JOIN employe e
                ON d.agent_id = e.id

            LEFT JOIN service s
                ON e.service_id = s.id

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

                s.id AS service_id,
                s.nom AS agent_service,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet,
                dr.statut AS dossier_statut

            FROM document_rh d

            LEFT JOIN employe e
                ON d.agent_id = e.id

            LEFT JOIN service s
                ON e.service_id = s.id

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

                OR s.nom ILIKE ?

                OR dr.reference ILIKE ?
                OR dr.objet ILIKE ?
                OR dr.statut ILIKE ?

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

                s.id AS service_id,
                s.nom AS agent_service,

                dr.reference AS dossier_reference,
                dr.objet AS dossier_objet,
                dr.statut AS dossier_statut

            FROM document_rh d

            LEFT JOIN employe e
                ON d.agent_id = e.id

            LEFT JOIN service s
                ON e.service_id = s.id

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
            Date dateDocument
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

    public int update(
            Long id,
            String type,
            String objet,
            String contenu,
            Long agentId,
            Long dossierId,
            String statut,
            String auteur,
            Date dateDocument
    ) {

        String sql = """
            UPDATE document_rh
            SET
                type = ?,
                objet = ?,
                contenu = ?,
                agent_id = ?,
                dossier_id = ?,
                statut = ?,
                auteur = ?,
                date_document = ?,
                date_modification = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        return jdbcTemplate.update(
                sql,
                type,
                objet,
                contenu,
                agentId,
                dossierId,
                statut,
                auteur,
                dateDocument,
                id
        );
    }

    public int archive(Long id) {

        String sql = """
            UPDATE document_rh
            SET
                statut = 'ARCHIVE',
                date_modification = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id);
    }

    public long countDocuments() {

        String sql = """
            SELECT COUNT(*)
            FROM document_rh
            """;

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class
        );

        return count != null ? count : 0L;
    }
}