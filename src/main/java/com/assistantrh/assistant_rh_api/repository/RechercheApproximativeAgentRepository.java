package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.observability.PerformanceTelemetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class RechercheApproximativeAgentRepository {

    private static final double SCORE_MINIMAL = 0.55;
    private static final int NOMBRE_MAX_RESULTATS = 10;

    private final JdbcTemplate jdbcTemplate;
    private final PerformanceTelemetry performanceTelemetry;

    @Autowired
    public RechercheApproximativeAgentRepository(
            JdbcTemplate jdbcTemplate,
            PerformanceTelemetry performanceTelemetry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.performanceTelemetry = performanceTelemetry;
    }

    public RechercheApproximativeAgentRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new PerformanceTelemetry());
    }

    public List<Map<String, Object>> rechercher(String query) {
        return rechercher(query, null, null, null);
    }

    public List<Map<String, Object>> rechercher(
            String query,
            String service,
            String poste,
            String direction
    ) {
        String recherche = query == null ? "" : query.trim();
        if (recherche.isEmpty()) {
            return List.of();
        }

        String rechercheNormalisee = normaliser(recherche);

        String sql = """
                WITH input AS (
                    SELECT
                        BTRIM(?) AS raw_query,
                        LOWER(?) AS normalized_query,
                        NULLIF(BTRIM(?), '') AS service_filter,
                        NULLIF(BTRIM(?), '') AS poste_filter,
                        NULLIF(BTRIM(?), '') AS direction_filter
                ),
                agents AS (
                    SELECT DISTINCT ON (e.id)
                        e.id,
                        e.matricule,
                        e.nom,
                        e.prenom,
                        translate(
                            lower(coalesce(e.nom, '')),
                            U&'\\00e0\\00e1\\00e2\\00e3\\00e4\\00e5\\00e8\\00e9\\00ea\\00eb\\00ec\\00ed\\00ee\\00ef\\00f2\\00f3\\00f4\\00f5\\00f6\\00f9\\00fa\\00fb\\00fc\\00e7\\00f1\\00fd\\00ff',
                            'aaaaaaeeeeiiiiooooouuuucnyy'
                        ) AS nom_normalise,
                        translate(
                            lower(coalesce(e.prenom, '')),
                            U&'\\00e0\\00e1\\00e2\\00e3\\00e4\\00e5\\00e8\\00e9\\00ea\\00eb\\00ec\\00ed\\00ee\\00ef\\00f2\\00f3\\00f4\\00f5\\00f6\\00f9\\00fa\\00fb\\00fc\\00e7\\00f1\\00fd\\00ff',
                            'aaaaaaeeeeiiiiooooouuuucnyy'
                        ) AS prenom_normalise,
                        p.intitule AS poste,
                        s.code AS code_service,
                        s.nom AS service,
                        d.nom AS direction
                    FROM employe e
                    LEFT JOIN affectation af
                        ON af.employe_id = e.id
                        AND af.date_fin IS NULL
                    LEFT JOIN poste p ON p.id = af.poste_id
                    LEFT JOIN service s ON s.id = af.service_id
                    LEFT JOIN direction d ON d.id = s.direction_id
                    ORDER BY
                        e.id,
                        af.date_debut DESC NULLS LAST,
                        af.id DESC NULLS LAST
                ),
                scored AS (
                    SELECT
                        a.id,
                        a.matricule,
                        a.nom,
                        a.prenom,
                        a.poste,
                        a.code_service,
                        a.service,
                        a.direction,
                        i.normalized_query,
                        i.service_filter,
                        i.poste_filter,
                        i.direction_filter,
                        UPPER(BTRIM(coalesce(a.matricule, '')))
                            = UPPER(i.raw_query) AS matricule_exact,
                        CASE
                            WHEN UPPER(BTRIM(coalesce(a.matricule, '')))
                                 = UPPER(i.raw_query)
                                THEN 1.0::double precision
                            ELSE fuzzy_score.score
                        END AS score_correspondance
                    FROM agents a
                    CROSS JOIN input i
                    CROSS JOIN LATERAL (
                        SELECT MAX(
                            GREATEST(
                                similarity(i.normalized_query, candidate.value),
                                word_similarity(
                                    i.normalized_query,
                                    candidate.value
                                ),
                                CASE
                                    WHEN char_length(i.normalized_query) <= 255
                                        THEN 1.0 - (
                                            levenshtein(
                                                i.normalized_query,
                                                candidate.value
                                            )::double precision
                                            / GREATEST(
                                                char_length(i.normalized_query),
                                                char_length(candidate.value),
                                                1
                                            )
                                        )
                                    ELSE 0.0
                                END
                            )
                        ) AS score
                        FROM (
                            VALUES
                                (a.nom_normalise),
                                (a.prenom_normalise),
                                (concat_ws(
                                    ' ',
                                    a.nom_normalise,
                                    a.prenom_normalise
                                )),
                                (concat_ws(
                                    ' ',
                                    a.prenom_normalise,
                                    a.nom_normalise
                                ))
                        ) AS candidate(value)
                    ) fuzzy_score
                ),
                ranked AS (
                    SELECT
                        scored.*,
                        ROW_NUMBER() OVER (
                            ORDER BY
                                scored.matricule_exact DESC,
                                scored.score_correspondance DESC,
                                scored.id
                        ) AS candidate_rank
                    FROM scored
                    WHERE (
                        scored.matricule_exact
                        OR char_length(
                            regexp_replace(
                                scored.normalized_query,
                                '[[:space:]]',
                                '',
                                'g'
                            )
                        ) >= 3
                    )
                    AND (
                        scored.service_filter IS NULL
                        OR POSITION(
                            LOWER(scored.service_filter)
                            IN LOWER(coalesce(scored.service, ''))
                        ) > 0
                    )
                    AND (
                        scored.poste_filter IS NULL
                        OR POSITION(
                            LOWER(scored.poste_filter)
                            IN LOWER(coalesce(scored.poste, ''))
                        ) > 0
                    )
                    AND (
                        scored.direction_filter IS NULL
                        OR POSITION(
                            LOWER(scored.direction_filter)
                            IN LOWER(coalesce(scored.direction, ''))
                        ) > 0
                    )
                )
                SELECT
                    id,
                    matricule,
                    nom,
                    prenom,
                    poste,
                    code_service,
                    service,
                    direction,
                    score_correspondance
                FROM ranked
                WHERE candidate_rank <= 2
                   OR score_correspondance >= ?
                ORDER BY matricule_exact DESC, score_correspondance DESC, id
                LIMIT ?
                """;

        long startedAtNanos = System.nanoTime();
        boolean succeeded = false;
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    sql,
                    recherche,
                    rechercheNormalisee,
                    filtreTexte(service),
                    filtreTexte(poste),
                    filtreTexte(direction),
                    SCORE_MINIMAL,
                    NOMBRE_MAX_RESULTATS
            );
            succeeded = true;
            return results;
        } finally {
            performanceTelemetry.databaseQuery(
                    "approximate_agent_search",
                    startedAtNanos,
                    succeeded
            );
        }
    }

    private String filtreTexte(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private String normaliser(String valeur) {
        return Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
