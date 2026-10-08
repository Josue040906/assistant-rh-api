package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.model.Document;
import com.assistantrh.assistant_rh_api.model.TypeDocument;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class DocumentRhRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DocumentRhRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<TypeDocument> findAllActiveTypes(LocalDate dateReference) {
        Objects.requireNonNull(
                dateReference,
                "La date de référence est obligatoire."
        );

        String sql = """
            SELECT
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            FROM type_document
            WHERE (date_debut_validite IS NULL OR date_debut_validite <= ?)
              AND (date_fin_validite IS NULL OR date_fin_validite >= ?)
            ORDER BY libelle, id
            """;

        return jdbcTemplate.query(
                sql,
                typeDocumentRowMapper(),
                dateReference,
                dateReference
        );
    }

    public Optional<TypeDocument> findTypeDocumentById(Integer id) {
        String sql = """
            SELECT
                id,
                code,
                libelle,
                description,
                date_debut_validite,
                date_fin_validite
            FROM type_document
            WHERE id = ?
            """;

        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            sql,
                            typeDocumentRowMapper(),
                            id
                    )
            );
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> findMutationDirection(Long id) {
        return findMutationReference(
                """
                SELECT id, nom
                FROM direction
                WHERE id = ?
                """,
                id
        );
    }

    public Optional<Map<String, Object>> findMutationService(Long id) {
        return findMutationReference(
                """
                SELECT
                    s.id,
                    s.nom,
                    s.direction_id,
                    d.nom AS direction
                FROM service s
                LEFT JOIN direction d ON d.id = s.direction_id
                WHERE s.id = ?
                """,
                id
        );
    }

    public Optional<Map<String, Object>> findMutationPoste(Long id) {
        return findMutationReference(
                """
                SELECT
                    p.id,
                    p.intitule,
                    s.id AS service_id,
                    s.nom AS service,
                    s.direction_id,
                    d.nom AS direction
                FROM poste p
                JOIN service s ON s.id = p.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                WHERE p.id = ?
                """,
                id
        );
    }

    private Optional<Map<String, Object>> findMutationReference(
            String sql,
            Long id
    ) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id);
        return results.stream().findFirst();
    }

    public List<Document> findDocuments(
            Integer employeId,
            Integer typeDocumentId
    ) {
        String sql = """
            SELECT
                id,
                reference_document,
                type_document_id,
                employe_id,
                destinataire,
                objet,
                date_document,
                date_effet,
                statut,
                fichier_path,
                observation,
                date_creation,
                donnees::text AS donnees
            FROM document
            WHERE (?::integer IS NULL OR employe_id = ?)
              AND (?::integer IS NULL OR type_document_id = ?)
            ORDER BY date_creation DESC, id DESC
            """;

        return jdbcTemplate.query(
                sql,
                documentRowMapper(),
                employeId,
                employeId,
                typeDocumentId,
                typeDocumentId
        );
    }

    public Optional<Document> findDocumentById(Integer id) {
        String sql = """
            SELECT
                id,
                reference_document,
                type_document_id,
                employe_id,
                destinataire,
                objet,
                date_document,
                date_effet,
                statut,
                fichier_path,
                observation,
                date_creation,
                donnees::text AS donnees
            FROM document
            WHERE id = ?
            """;

        return jdbcTemplate.query(
                sql,
                documentRowMapper(),
                id
        ).stream().findFirst();
    }

    public Document insertDocument(Document document) {
        String sql = """
            INSERT INTO document (
                reference_document,
                type_document_id,
                employe_id,
                destinataire,
                objet,
                date_document,
                date_effet,
                statut,
                fichier_path,
                observation,
                donnees
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            RETURNING id
            """;

        String donneesJson;
        try {
            donneesJson = objectMapper.writeValueAsString(document.donnees());
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Impossible de convertir les données du document en JSON.",
                    exception
            );
        }

        Integer id = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                document.referenceDocument(),
                document.typeDocumentId(),
                document.employeId(),
                document.destinataire(),
                document.objet(),
                document.dateDocument() == null
                        ? null
                        : Date.valueOf(document.dateDocument()),
                document.dateEffet() == null
                        ? null
                        : Date.valueOf(document.dateEffet()),
                document.statut(),
                document.fichierPath(),
                document.observation(),
                donneesJson
        );

        return findDocumentById(id).orElseThrow(
                () -> new IllegalStateException(
                        "Le document inséré n'a pas pu être relu."
                )
        );
    }

    public int updatePdfPath(Integer id, String fichierPath) {
        return jdbcTemplate.update(
                """
                UPDATE document
                SET fichier_path = ?
                WHERE id = ?
                """,
                fichierPath,
                id
        );
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

    private RowMapper<TypeDocument> typeDocumentRowMapper() {
        return (resultSet, rowNumber) -> new TypeDocument(
                resultSet.getObject("id", Integer.class),
                resultSet.getString("code"),
                resultSet.getString("libelle"),
                resultSet.getString("description"),
                resultSet.getObject("date_debut_validite", LocalDate.class),
                resultSet.getObject("date_fin_validite", LocalDate.class)
        );
    }

    private RowMapper<Document> documentRowMapper() {
        return (resultSet, rowNumber) -> new Document(
                resultSet.getObject("id", Integer.class),
                resultSet.getString("reference_document"),
                resultSet.getObject("type_document_id", Integer.class),
                resultSet.getObject("employe_id", Integer.class),
                resultSet.getString("destinataire"),
                resultSet.getString("objet"),
                resultSet.getObject("date_document", LocalDate.class),
                resultSet.getObject("date_effet", LocalDate.class),
                resultSet.getString("statut"),
                resultSet.getString("fichier_path"),
                resultSet.getString("observation"),
                resultSet.getObject("date_creation", OffsetDateTime.class),
                lireDonnees(resultSet)
        );
    }

    private Map<String, Object> lireDonnees(ResultSet resultSet)
            throws SQLException {
        try {
            return objectMapper.readValue(
                    resultSet.getString("donnees"),
                    new TypeReference<>() {
                    }
            );
        } catch (JacksonException exception) {
            throw new SQLException(
                    "Les données JSONB du document sont invalides.",
                    exception
            );
        }
    }
}