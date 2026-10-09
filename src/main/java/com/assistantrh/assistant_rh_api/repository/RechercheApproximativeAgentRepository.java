package com.assistantrh.assistant_rh_api.repository;

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

    public RechercheApproximativeAgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> rechercher(String query) {
        String recherche = query == null ? "" : query.trim();
        if (recherche.isEmpty()) {
            return List.of();
        }

        String rechercheNormalisee = normaliser(recherche);

        String sql = """
                WITH input AS (
                    SELECT BTRIM(?) AS raw_query, LOWER(?) AS normalized_query
                ),
                agents AS (
                    SELECT
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
                    LEFT JOIN LATERAL (
                        SELECT af.poste_id, af.service_id
                        FROM affectation af
                        WHERE af.employe_id = e.id
                          AND af.date_fin IS NULL
                        ORDER BY af.date_debut DESC, af.id DESC
                        LIMIT 1
                    ) a ON TRUE
                    LEFT JOIN poste p ON p.id = a.poste_id
                    LEFT JOIN service s ON s.id = a.service_id
                    LEFT JOIN direction d ON d.id = s.direction_id
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
                        UPPER(BTRIM(coalesce(a.matricule, '')))
                            = UPPER(i.raw_query) AS matricule_exact,
                        CASE
                            WHEN UPPER(BTRIM(coalesce(a.matricule, '')))
                                 = UPPER(i.raw_query)
                                THEN 1.0::double precision
                            ELSE GREATEST(
                                similarity(i.normalized_query, a.nom_normalise),
                                word_similarity(i.normalized_query, a.nom_normalise),
                                similarity(i.normalized_query, a.prenom_normalise),
                                word_similarity(i.normalized_query, a.prenom_normalise),
                                similarity(
                                    i.normalized_query,
                                    concat_ws(' ', a.nom_normalise, a.prenom_normalise)
                                ),
                                similarity(
                                    i.normalized_query,
                                    concat_ws(' ', a.prenom_normalise, a.nom_normalise)
                                ),
                                word_similarity(
                                    i.normalized_query,
                                    concat_ws(' ', a.nom_normalise, a.prenom_normalise)
                                ),
                                word_similarity(
                                    i.normalized_query,
                                    concat_ws(' ', a.prenom_normalise, a.nom_normalise)
                                )
                            )::double precision
                        END AS score_correspondance
                    FROM agents a
                    CROSS JOIN input i
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
                FROM scored
                WHERE matricule_exact
                   OR (
                       char_length(
                           regexp_replace(normalized_query, '[[:space:]]', '', 'g')
                       ) >= 3
                       AND score_correspondance >= ?
                   )
                ORDER BY matricule_exact DESC, score_correspondance DESC, id
                LIMIT ?
                """;

        return jdbcTemplate.queryForList(
                sql,
                recherche,
                rechercheNormalisee,
                SCORE_MINIMAL,
                NOMBRE_MAX_RESULTATS
        );
    }

    private String normaliser(String valeur) {
        return Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
