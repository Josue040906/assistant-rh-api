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
                d.reference_document,
                d.objet,
                d.date_document,
                d.date_effet,
                d.statut,
                d.fichier_path,
                d.observation,

                d.type_document_id,
                td.code AS type_document_code,
                td.libelle AS type_document,

                d.employe_id,
                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom,

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                dir.id AS direction_id,
                dir.nom AS direction

            FROM document d

            LEFT JOIN type_document td
                ON td.id = d.type_document_id

            LEFT JOIN employe e
                ON e.id = d.employe_id

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

            LEFT JOIN direction dir
                ON dir.id = s.direction_id

            ORDER BY d.date_document DESC NULLS LAST, d.id DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }

    public Optional<Map<String, Object>> findById(Long id) {

        String sql = """
            SELECT
                d.id,
                d.reference_document,
                d.objet,
                d.date_document,
                d.date_effet,
                d.statut,
                d.fichier_path,
                d.observation,

                d.type_document_id,
                td.code AS type_document_code,
                td.libelle AS type_document,
                td.description AS type_document_description,

                d.employe_id,
                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom,

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                dir.id AS direction_id,
                dir.nom AS direction

            FROM document d

            LEFT JOIN type_document td
                ON td.id = d.type_document_id

            LEFT JOIN employe e
                ON e.id = d.employe_id

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

            LEFT JOIN direction dir
                ON dir.id = s.direction_id

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

        String recherche =
                query == null ? "" : query.trim();

        if (recherche.isEmpty()) {
            return findAll();
        }

        String pattern = "%" + recherche + "%";

        String sql = """
            SELECT
                d.id,
                d.reference_document,
                d.objet,
                d.date_document,
                d.date_effet,
                d.statut,
                d.fichier_path,
                d.observation,

                d.type_document_id,
                td.code AS type_document_code,
                td.libelle AS type_document,

                d.employe_id,
                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom,

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                dir.id AS direction_id,
                dir.nom AS direction

            FROM document d

            LEFT JOIN type_document td
                ON td.id = d.type_document_id

            LEFT JOIN employe e
                ON e.id = d.employe_id

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

            LEFT JOIN direction dir
                ON dir.id = s.direction_id

            WHERE
                d.reference_document ILIKE ?
                OR d.objet ILIKE ?
                OR d.statut ILIKE ?
                OR td.code ILIKE ?
                OR td.libelle ILIKE ?

                OR e.matricule ILIKE ?
                OR e.nom ILIKE ?
                OR e.prenom ILIKE ?
                OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?

                OR p.intitule ILIKE ?
                OR s.code ILIKE ?
                OR s.nom ILIKE ?
                OR dir.nom ILIKE ?

            ORDER BY
                d.date_document DESC NULLS LAST,
                d.id DESC
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
                pattern
        );
    }

    public List<Map<String, Object>> findByAgentId(Long employeId) {

        String sql = """
            SELECT
                d.id,
                d.reference_document,
                d.objet,
                d.date_document,
                d.date_effet,
                d.statut,
                d.fichier_path,
                d.observation,

                d.type_document_id,
                td.code AS type_document_code,
                td.libelle AS type_document,

                d.employe_id,
                e.matricule AS agent_matricule,
                e.nom AS agent_nom,
                e.prenom AS agent_prenom

            FROM document d

            LEFT JOIN type_document td
                ON td.id = d.type_document_id

            LEFT JOIN employe e
                ON e.id = d.employe_id

            WHERE d.employe_id = ?

            ORDER BY
                d.date_document DESC NULLS LAST,
                d.id DESC
            """;

        return jdbcTemplate.queryForList(
                sql,
                employeId
        );
    }

    public boolean existsEmploye(Long employeId) {

        String sql = """
            SELECT EXISTS(
                SELECT 1
                FROM employe
                WHERE id = ?
            )
            """;

        Boolean result =
                jdbcTemplate.queryForObject(
                        sql,
                        Boolean.class,
                        employeId
                );

        return Boolean.TRUE.equals(result);
    }

    public boolean existsTypeDocument(
            Integer typeDocumentId
    ) {

        String sql = """
            SELECT EXISTS(
                SELECT 1
                FROM type_document
                WHERE id = ?
            )
            """;

        Boolean result =
                jdbcTemplate.queryForObject(
                        sql,
                        Boolean.class,
                        typeDocumentId
                );

        return Boolean.TRUE.equals(result);
    }

    public Long create(
            String referenceDocument,
            Integer typeDocumentId,
            Long employeId,
            String objet,
            Date dateDocument,
            Date dateEffet,
            String statut,
            String fichierPath,
            String observation
    ) {

        String sql = """
            INSERT INTO document (
                reference_document,
                type_document_id,
                employe_id,
                objet,
                date_document,
                date_effet,
                statut,
                fichier_path,
                observation
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                referenceDocument,
                typeDocumentId,
                employeId,
                objet,
                dateDocument,
                dateEffet,
                statut,
                fichierPath,
                observation
        );
    }

    public int update(
            Long id,
            Integer typeDocumentId,
            Long employeId,
            String objet,
            Date dateDocument,
            Date dateEffet,
            String statut,
            String fichierPath,
            String observation
    ) {

        String sql = """
            UPDATE document
            SET
                type_document_id = ?,
                employe_id = ?,
                objet = ?,
                date_document = ?,
                date_effet = ?,
                statut = ?,
                fichier_path = ?,
                observation = ?
            WHERE id = ?
            """;

        return jdbcTemplate.update(
                sql,
                typeDocumentId,
                employeId,
                objet,
                dateDocument,
                dateEffet,
                statut,
                fichierPath,
                observation,
                id
        );
    }

    public int archive(Long id) {

        String sql = """
            UPDATE document
            SET statut = 'ARCHIVE'
            WHERE id = ?
            """;

        return jdbcTemplate.update(
                sql,
                id
        );
    }

    public long countDocuments() {

        String sql = """
            SELECT COUNT(*)
            FROM document
            """;

        Long count =
                jdbcTemplate.queryForObject(
                        sql,
                        Long.class
                );

        return count != null ? count : 0L;
    }
}