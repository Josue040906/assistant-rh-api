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
import java.util.ArrayList;
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
                donnees::text AS donnees,
                auteur_id,
                decision_acceptee,
                decision_signataire,
                decision_reference,
                decision_date,
                decision_motif,
                decision_saisie_par,
                decision_saisie_le,
                (SELECT COALESCE(NULLIF(BTRIM(CONCAT_WS(' ', es.prenom, es.nom)), ''), us.email)
                 FROM utilisateur us LEFT JOIN employe es ON es.user_id = us.id
                 WHERE us.id = document.decision_saisie_par) AS decision_saisie_par_nom
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
                donnees::text AS donnees,
                auteur_id,
                decision_acceptee,
                decision_signataire,
                decision_reference,
                decision_date,
                decision_motif,
                decision_saisie_par,
                decision_saisie_le,
                (SELECT COALESCE(NULLIF(BTRIM(CONCAT_WS(' ', es.prenom, es.nom)), ''), us.email)
                 FROM utilisateur us LEFT JOIN employe es ON es.user_id = us.id
                 WHERE us.id = document.decision_saisie_par) AS decision_saisie_par_nom
            FROM document
            WHERE id = ?
            """;

        return jdbcTemplate.query(
                sql,
                documentRowMapper(),
                id
        ).stream().findFirst();
    }

    /** Le compte qui a créé le document : lui seul pourra le valider. */
    public void enregistrerAuteur(Integer documentId, Integer auteurId) {
        jdbcTemplate.update("UPDATE document SET auteur_id = ? WHERE id = ?", auteurId, documentId);
    }

    /** Ce qu'il faut savoir d'un document pour faire évoluer son statut. */
    public Optional<Map<String, Object>> trouverPourCycle(Long id) {
        return jdbcTemplate.queryForList("""
                SELECT d.id, d.statut, d.auteur_id, d.employe_id, d.reference_document, d.objet,
                       d.date_effet, d.donnees::text AS donnees, td.code AS type_code,
                       d.decision_reference, d.decision_signataire
                FROM document d
                JOIN type_document td ON td.id = d.type_document_id
                WHERE d.id = ?
                """, id).stream().findFirst().map(ligne -> {
                    Map<String, Object> document = new java.util.HashMap<>(ligne);
                    Object texte = ligne.get("donnees");
                    try {
                        document.put("donnees", texte == null ? Map.of()
                                : objectMapper.readValue(texte.toString(),
                                        new TypeReference<Map<String, Object>>() { }));
                    } catch (JacksonException erreur) {
                        throw new IllegalStateException("Les données de la demande sont illisibles.", erreur);
                    }
                    return document;
                });
    }

    /**
     * Enregistre la réponse reçue sur papier et passe la demande, encore créée,
     * au statut ACCEPTE ou REFUSE. Une date d'effet accordée remplace la date demandée.
     */
    public boolean enregistrerDecision(
            Long id, String statut, String signataire, String reference, LocalDate date,
            String motif, LocalDate dateEffet, Integer saisiePar
    ) {
        return jdbcTemplate.update("""
                UPDATE document
                SET statut = ?, decision_acceptee = (? = 'ACCEPTE'), decision_signataire = ?, decision_reference = ?, decision_date = ?,
                    decision_motif = ?, decision_saisie_par = ?, decision_saisie_le = now(),
                    date_effet = COALESCE(?, date_effet)
                WHERE id = ? AND statut = 'CREE'
                """, statut, statut, signataire, reference, Date.valueOf(date), motif, saisiePar,
                dateEffet == null ? null : Date.valueOf(dateEffet), id) == 1;
    }

    /**
     * Passe le document au statut vers, seulement s'il est dans l'un des
     * statuts depuis : deux actions simultanées ne peuvent pas réussir toutes deux.
     */
    public boolean changerStatut(Long id, String vers, List<String> depuis) {
        String places = String.join(", ", java.util.Collections.nCopies(depuis.size(), "?"));
        List<Object> arguments = new java.util.ArrayList<>();
        arguments.add(vers);
        arguments.add(id);
        arguments.addAll(depuis);
        return jdbcTemplate.update(
                "UPDATE document SET statut = ? WHERE id = ? AND statut IN (" + places + ")",
                arguments.toArray()
        ) == 1;
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
                (d.statut = 'ACCEPTE' AND d.date_effet <= CURRENT_DATE) AS applicable,
                d.decision_acceptee,
                d.decision_signataire,
                d.decision_reference,
                d.decision_date,
                d.decision_motif,
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
                (d.statut = 'ACCEPTE' AND d.date_effet <= CURRENT_DATE) AS applicable,
                d.decision_acceptee,
                d.decision_signataire,
                d.decision_reference,
                d.decision_date,
                d.decision_motif,
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
                (d.statut = 'ACCEPTE' AND d.date_effet <= CURRENT_DATE) AS applicable,
                d.decision_acceptee,
                d.decision_signataire,
                d.decision_reference,
                d.decision_date,
                d.decision_motif,
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
                (d.statut = 'ACCEPTE' AND d.date_effet <= CURRENT_DATE) AS applicable,
                d.decision_acceptee,
                d.decision_signataire,
                d.decision_reference,
                d.decision_date,
                d.decision_motif,
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
                lireDonnees(resultSet),
                resultSet.getObject("auteur_id", Integer.class),
                lireDecision(resultSet)
        );
    }

    /** La décision saisie, ou rien tant que la demande attend sa réponse. */
    private static com.assistantrh.assistant_rh_api.model.DocumentDecision lireDecision(ResultSet resultSet)
            throws SQLException {
        LocalDate date = resultSet.getObject("decision_date", LocalDate.class);
        if (date == null) {
            return null;
        }
        return new com.assistantrh.assistant_rh_api.model.DocumentDecision(
                Boolean.TRUE.equals(resultSet.getObject("decision_acceptee", Boolean.class)),
                resultSet.getString("decision_signataire"),
                resultSet.getString("decision_reference"),
                date,
                resultSet.getString("decision_motif"),
                resultSet.getObject("decision_saisie_par", Integer.class),
                resultSet.getString("decision_saisie_par_nom"),
                resultSet.getObject("decision_saisie_le", OffsetDateTime.class)
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

    /**
     * Le jour où l'agent atteint la durée de service qu'exige la retraite
     * (donnée du type de document RETRAITE) ; vide sans date d'embauche.
     */
    public Optional<java.time.LocalDate> dateRetraitePossible(Integer employeId) {
        return jdbcTemplate.queryForList("""
                SELECT (e.date_embauche
                        + MAKE_INTERVAL(years => td.anciennete_service_min_annees))::date
                FROM employe e
                CROSS JOIN type_document td
                WHERE e.id = ?
                  AND td.code = 'RETRAITE'
                  AND e.date_embauche IS NOT NULL
                  AND td.anciennete_service_min_annees IS NOT NULL
                """, java.time.LocalDate.class, employeId).stream().findFirst();
    }

    /** Les années de service qu'exige la retraite (donnée), si elles sont renseignées. */
    public Optional<Integer> ancienneteRetraiteAnnees() {
        return jdbcTemplate.queryForList("""
                SELECT anciennete_service_min_annees FROM type_document
                WHERE code = 'RETRAITE' AND anciennete_service_min_annees IS NOT NULL
                """, Integer.class).stream().findFirst();
    }

    /**
     * Les demandes créées ou acceptées de ce type et de cet agent que l'effet qui
     * vient d'être produit satisfait (condition SQL fixe, écrite par le code
     * appelant, jamais par l'utilisateur) passent à APPLIQUE.
     */
    public List<Map<String, Object>> appliquerDemandesSatisfaites(
            Integer employeId, String typeCode, String condition, Object... arguments
    ) {
        List<Object> parametres = new ArrayList<>();
        parametres.add(typeCode);
        parametres.add(employeId);
        parametres.addAll(java.util.Arrays.asList(arguments));
        String sql = "UPDATE document d SET statut = 'APPLIQUE' FROM type_document td"
                + " WHERE td.id = d.type_document_id AND td.code = ? AND d.employe_id = ?"
                + " AND d.statut IN ('CREE', 'ACCEPTE') AND (" + condition + ")"
                + " RETURNING d.id, d.reference_document, d.employe_id";
        return jdbcTemplate.queryForList(sql, parametres.toArray());
    }
}
