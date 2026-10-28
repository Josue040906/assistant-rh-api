package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.dto.AgentRechercheDto;
import com.assistantrh.assistant_rh_api.observability.PerformanceTelemetry;
import com.assistantrh.assistant_rh_api.service.RechercheStructureeAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class RechercheStructureeAgentRepository {
    private static final double SCORE_SIMILARITE_MINIMAL = 0.42;
    private static final double SCORE_SIMILARITE_MOT_MINIMAL = 0.62;
    private static final Set<String> NUMERIC_FIELDS = Set.of("age", "echelon");

    private final JdbcTemplate jdbcTemplate;
    private final PerformanceTelemetry performanceTelemetry;

    @Autowired
    public RechercheStructureeAgentRepository(
            JdbcTemplate jdbcTemplate,
            PerformanceTelemetry performanceTelemetry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.performanceTelemetry = performanceTelemetry;
    }

    public RechercheStructureeAgentRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new PerformanceTelemetry());
    }

    public ResultatPage rechercher(
            List<RechercheStructureeAgentService.Filtre> filtres,
            int limite,
            int offset,
            String tri,
            String direction
    ) {
        List<Object> scoreArguments = new ArrayList<>();
        List<Object> filterArguments = new ArrayList<>();
        List<String> clauses = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        List<String> distances = new ArrayList<>();
        List<Object> distanceArguments = new ArrayList<>();
        List<String> diplomaClauses = new ArrayList<>();
        List<Object> diplomaArguments = new ArrayList<>();

        for (RechercheStructureeAgentService.Filtre filtre : filtres) {
            if (estChampDiplome(filtre.champ())) {
                ajouterCritereDiplome(filtre, diplomaClauses, diplomaArguments);
                continue;
            }
            String expression = expression(filtre.champ());
            switch (filtre.operateur()) {
                case EQ, NE, CONTAINS -> {
                    clauses.add(expression + " "
                            + switch (filtre.operateur()) {
                                case EQ -> "=";
                                case NE -> "<>";
                                case CONTAINS -> "LIKE";
                                default -> throw new IllegalStateException();
                            }
                            + " ?");
                    if (filtre.operateur()
                            == RechercheStructureeAgentService.Operateur.CONTAINS) {
                        filterArguments.add(
                                "%" + echapperLike(texte(filtre.valeur())) + "%"
                        );
                    } else if (NUMERIC_FIELDS.contains(filtre.champ())
                            || RechercheStructureeAgentService.estChampDate(
                                    filtre.champ()
                            )) {
                        filterArguments.add(valeurNumeriqueOuDate(filtre));
                    } else {
                        filterArguments.add(texte(filtre.valeur()));
                    }
                }
                case FUZZY -> {
                    String valeur = texte(filtre.valeur());
                    clauses.add("(" + expression + " % ? AND similarity("
                            + expression + ", ?) >= ? OR word_similarity(?, "
                            + expression + ") >= ?)");
                    filterArguments.add(valeur);
                    filterArguments.add(valeur);
                    filterArguments.add(SCORE_SIMILARITE_MINIMAL);
                    filterArguments.add(valeur);
                    filterArguments.add(SCORE_SIMILARITE_MOT_MINIMAL);
                    scores.add("CASE WHEN " + expression
                            + " = ? THEN 1.0::double precision ELSE similarity("
                            + expression + ", ?) END");
                    scoreArguments.add(valeur);
                    scoreArguments.add(valeur);

                    // La distance ne s'applique qu'aux champs textuels.
                    if (!NUMERIC_FIELDS.contains(filtre.champ())
                            && !RechercheStructureeAgentService.estChampDate(filtre.champ())) {
                        distances.add("levenshtein(" + expression + ", ?)");
                        distanceArguments.add(valeur);
                    }
                }
                case GT, GTE, LT, LTE -> {
                    clauses.add(expression + " "
                            + operateurSql(filtre.operateur()) + " ?");
                    filterArguments.add(valeurNumeriqueOuDate(filtre));
                }
                case BETWEEN -> {
                    clauses.add(expression + " BETWEEN ? AND ?");
                    filterArguments.add(valeurNumeriqueOuDate(filtre));
                    filterArguments.add(valeurNumeriqueOuDate(filtre, true));
                }
                case BEFORE -> {
                    clauses.add(expression + " < ?");
                    filterArguments.add(LocalDate.parse(texte(filtre.valeur())));
                }
                case AFTER -> {
                    clauses.add(expression + " > ?");
                    filterArguments.add(LocalDate.parse(texte(filtre.valeur())));
                }
                case WITHIN_LAST_YEARS -> {
                    clauses.add(expression + " >= CURRENT_DATE - (? * INTERVAL '1 year')");
                    filterArguments.add(entier(filtre.valeur()));
                }
                case OLDER_THAN_YEARS -> {
                    clauses.add(expression + " <= CURRENT_DATE - (? * INTERVAL '1 year')");
                    filterArguments.add(entier(filtre.valeur()));
                }
            }
        }

        String scoreSelection = scores.isEmpty()
                ? "0.0::double precision"
                : "(" + String.join(" + ", scores) + ") / "
                        + scores.size() + "::double precision";
        String distanceSelection = distances.isEmpty()
                ? "0::integer"
                : String.join(" + ", distances);
        StringBuilder sql = new StringBuilder("""
                SELECT
                    e.id,
                    e.matricule,
                    e.nom,
                    e.prenom,
                    e.sexe,
                    EXTRACT(YEAR FROM age(CURRENT_DATE, e.date_naissance))::INTEGER AS age,
                    e.date_embauche,
                    p.intitule AS poste,
                    s.code AS code_service,
                    s.nom AS service,
                    d.nom AS direction,
                    c.code AS categorie,
                    co.code AS corps,
                    te.nom AS type_emploi,
                    cl.libelle AS classe,
                    ec.ordre AS echelon,
                """).append(scoreSelection).append("""
                     AS score_correspondance,
                """).append(distanceSelection).append("""
                     AS distance_correspondance
                FROM employe e
                LEFT JOIN affectation af
                    ON af.employe_id = e.id
                    AND af.date_fin IS NULL
                    AND af.date_debut <= CURRENT_DATE
                LEFT JOIN poste p ON p.id = af.poste_id
                LEFT JOIN service s ON s.id = af.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                LEFT JOIN type_emploi te ON te.id = e.type_emploi_id
                LEFT JOIN localite lr ON lr.id = e.localite_residence_id
                LEFT JOIN (
                    SELECT DISTINCT ON (hc.employe_id)
                        hc.employe_id,
                        hc.echelon_id,
                        hc.categorie_id
                    FROM historique_carriere hc
                    WHERE hc.date_fin IS NULL
                      AND hc.date_debut <= CURRENT_DATE
                    ORDER BY hc.employe_id, hc.date_debut DESC, hc.id DESC
                ) hc ON hc.employe_id = e.id
                LEFT JOIN categorie c
                    ON c.id = COALESCE(hc.categorie_id, e.categorie_id)
                LEFT JOIN corps co ON co.id = c.corps_id
                LEFT JOIN echelon ec ON ec.id = hc.echelon_id
                LEFT JOIN classe cl ON cl.id = ec.classe_id
                WHERE
                """);

        if (!diplomaClauses.isEmpty()) {
            clauses.add("EXISTS (SELECT 1 FROM employe_diplome ed "
                    + "LEFT JOIN niveau_diplome nd ON nd.id = ed.niveau_id "
                    + "LEFT JOIN domaine_etude de ON de.id = ed.domaine_id "
                    + "LEFT JOIN etablissement et ON et.id = ed.etablissement_id "
                    + "LEFT JOIN mention me ON me.id = ed.mention_id "
                    + "WHERE ed.employe_id = e.id AND "
                    + String.join(" AND ", diplomaClauses) + ")");
            filterArguments.addAll(diplomaArguments);
        }
        if (clauses.isEmpty()) {
            throw new IllegalArgumentException("Au moins un critère de recherche est obligatoire.");
        }
        sql.append(String.join(" AND ", clauses));
        String countSql = "SELECT COUNT(DISTINCT e.id) " + sql.substring(sql.indexOf("FROM employe e"));
        String sortExpression = tri == null ? "e.nom" : expression(tri);
        String sortDirection = "DESC".equals(direction) ? "DESC" : "ASC";
        sql.append(" ORDER BY ");
        if (!scores.isEmpty()) {
            sql.append("score_correspondance DESC, distance_correspondance ASC, ");
        }
        sql.append(sortExpression).append(' ').append(sortDirection)
                .append(", e.id LIMIT ? OFFSET ?");
        List<Object> arguments = new ArrayList<>(scoreArguments);
        arguments.addAll(distanceArguments);
        arguments.addAll(filterArguments);
        arguments.add(limite + 1);
        arguments.add(offset);

        long startedAtNanos = System.nanoTime();
        boolean succeeded = false;
        List<Map<String, Object>> lignes;
        try {
            lignes = jdbcTemplate.queryForList(
                    sql.toString(),
                    arguments.toArray()
            );
            succeeded = true;
        } finally {
            performanceTelemetry.databaseQuery(
                    "structured_agent_search",
                    startedAtNanos,
                    succeeded
            );
        }
        boolean hasMore = lignes.size() > limite;
        long countStartedAtNanos = System.nanoTime();
        boolean countSucceeded = false;
        Integer totalResult;
        try {
            totalResult = jdbcTemplate.queryForObject(
                    countSql, Integer.class, filterArguments.toArray()
            );
            countSucceeded = true;
        } finally {
            performanceTelemetry.databaseQuery(
                    "structured_agent_search_count", countStartedAtNanos, countSucceeded
            );
        }
        int total = totalResult == null ? 0 : totalResult;
        long mappingStartedAtNanos = System.nanoTime();
        List<AgentRechercheDto> agents = lignes.stream()
                .limit(limite)
                .map(this::versDto)
                .toList();
        performanceTelemetry.applicationProcessing(
                "structured_agent_search_mapping",
                mappingStartedAtNanos
        );
        return new ResultatPage(agents, hasMore, total);
    }

    public record ResultatPage(
            List<AgentRechercheDto> agents,
            boolean hasMore,
            int total
    ) {
    }

    private AgentRechercheDto versDto(Map<String, Object> ligne) {
        Object dateEmbauche = ligne.get("date_embauche");
        LocalDate dateEmbaucheValue = dateEmbauche instanceof java.sql.Date sqlDate
                ? sqlDate.toLocalDate()
                : dateEmbauche instanceof LocalDate localDate
                        ? localDate
                        : dateEmbauche == null
                                ? null
                                : LocalDate.parse(dateEmbauche.toString());

        return new AgentRechercheDto(
                valeurEntiere(ligne.get("id")),
                valeurTexte(ligne.get("matricule")),
                valeurTexte(ligne.get("nom")),
                valeurTexte(ligne.get("prenom")),
                valeurTexte(ligne.get("sexe")),
                valeurEntiere(ligne.get("age")),
                dateEmbaucheValue,
                valeurTexte(ligne.get("poste")),
                valeurTexte(ligne.get("code_service")),
                valeurTexte(ligne.get("service")),
                valeurTexte(ligne.get("direction")),
                valeurTexte(ligne.get("categorie")),
                valeurTexte(ligne.get("corps")),
                valeurTexte(ligne.get("type_emploi")),
                valeurTexte(ligne.get("classe")),
                valeurEntiere(ligne.get("echelon")),
                valeurDecimale(ligne.get("score_correspondance"))
        );
    }

    private String valeurTexte(Object valeur) {
        return valeur == null ? null : valeur.toString();
    }

    private Integer valeurEntiere(Object valeur) {
        return valeur instanceof Number number ? number.intValue() : null;
    }

    private Double valeurDecimale(Object valeur) {
        return valeur instanceof Number number ? number.doubleValue() : null;
    }

    private String expression(String field) {
        return switch (field) {
            case "matricule" -> "UPPER(BTRIM(e.matricule))";
            case "nom" -> texteNormalise("e.nom");
            case "prenom" -> texteNormalise("e.prenom");
            case "sexe" -> "UPPER(BTRIM(e.sexe))";
            case "adresse" -> texteNormalise("concat_ws(' ', e.adresse, lr.nom)");
            case "localite" -> texteNormalise("lr.nom");
            case "poste" -> texteNormalise("p.intitule");
            case "service" -> texteNormalise("concat_ws(' ', s.code, s.nom)");
            case "direction" -> texteNormalise("d.nom");
            case "categorie" -> "UPPER(BTRIM(c.code))";
            case "corps" -> texteNormalise("concat_ws(' ', co.code, co.libelle)");
            case "type_emploi" -> texteNormalise("te.nom");
            case "classe" -> texteNormalise("cl.libelle");
            case "echelon" -> "ec.ordre";
            case "age" -> "EXTRACT(YEAR FROM age(CURRENT_DATE, e.date_naissance))::INTEGER";
            case "date_naissance" -> "e.date_naissance";
            case "date_embauche" -> "e.date_embauche";
            default -> throw new IllegalArgumentException(
                    "Champ de recherche non autorisé : " + field
            );
        };
    }

    private boolean estChampDiplome(String champ) {
        return Set.of("niveau_diplome", "domaine_diplome", "etablissement_diplome",
                "mention_diplome", "date_obtention_diplome", "numero_diplome")
                .contains(champ);
    }

    private void ajouterCritereDiplome(
            RechercheStructureeAgentService.Filtre filtre,
            List<String> clauses,
            List<Object> arguments
    ) {
        String expression = switch (filtre.champ()) {
            case "niveau_diplome" -> texteNormalise("nd.libelle");
            case "domaine_diplome" -> texteNormalise("de.libelle");
            case "etablissement_diplome" -> texteNormalise("et.nom");
            case "mention_diplome" -> texteNormalise("me.libelle");
            case "numero_diplome" -> texteNormalise("ed.numero_diplome");
            case "date_obtention_diplome" -> "ed.date_obtention";
            default -> throw new IllegalArgumentException("Champ de diplôme non autorisé.");
        };
        switch (filtre.operateur()) {
            case EQ, NE -> {
                clauses.add(expression + (filtre.operateur()
                        == RechercheStructureeAgentService.Operateur.EQ ? " = ?" : " <> ?"));
                arguments.add(filtre.champ().equals("date_obtention_diplome")
                        ? LocalDate.parse(texte(filtre.valeur()))
                        : texte(filtre.valeur()));
            }
            case CONTAINS -> {
                clauses.add(expression + " LIKE ?");
                arguments.add("%" + echapperLike(texte(filtre.valeur())) + "%");
            }
            case BEFORE, AFTER -> {
                clauses.add(expression + (filtre.operateur()
                        == RechercheStructureeAgentService.Operateur.BEFORE ? " < ?" : " > ?"));
                arguments.add(LocalDate.parse(texte(filtre.valeur())));
            }
            case BETWEEN -> {
                clauses.add(expression + " BETWEEN ? AND ?");
                arguments.add(LocalDate.parse(texte(filtre.valeur())));
                arguments.add(LocalDate.parse(texte(filtre.valeurSuperieure())));
            }
            case WITHIN_LAST_YEARS -> {
                clauses.add(expression + " >= CURRENT_DATE - (? * INTERVAL '1 year')");
                arguments.add(entier(filtre.valeur()));
            }
            case OLDER_THAN_YEARS -> {
                clauses.add(expression + " <= CURRENT_DATE - (? * INTERVAL '1 year')");
                arguments.add(entier(filtre.valeur()));
            }
            default -> throw new IllegalArgumentException(
                    "Opérateur non pris en charge pour ce champ de diplôme.");
        }
    }

    private String texteNormalise(String expression) {
        return "translate(lower(coalesce(" + expression + ", '')), "
                + "U&'\\00e0\\00e1\\00e2\\00e3\\00e4\\00e5\\00e8\\00e9\\00ea"
                + "\\00eb\\00ec\\00ed\\00ee\\00ef\\00f2\\00f3\\00f4\\00f5"
                + "\\00f6\\00f9\\00fa\\00fb\\00fc\\00e7\\00f1\\00fd\\00ff', "
                + "'aaaaaaeeeeiiiiooooouuuucnyy')";
    }

    private String operateurSql(RechercheStructureeAgentService.Operateur operator) {
        return switch (operator) {
            case GT -> ">";
            case GTE -> ">=";
            case LT -> "<";
            case LTE -> "<=";
            default -> throw new IllegalArgumentException(
                    "Opérateur de comparaison non autorisé."
            );
        };
    }

    private Object valeurNumeriqueOuDate(
            RechercheStructureeAgentService.Filtre filtre
    ) {
        return valeurNumeriqueOuDate(filtre, false);
    }

    private Object valeurNumeriqueOuDate(
            RechercheStructureeAgentService.Filtre filtre,
            boolean borneSuperieure
    ) {
        Object value = borneSuperieure ? filtre.valeurSuperieure() : filtre.valeur();
        if (RechercheStructureeAgentService.estChampDate(filtre.champ())) {
            return LocalDate.parse(texte(value));
        }
        return entier(value);
    }

    private String texte(Object value) {
        return (String) value;
    }

    private int entier(Object value) {
        return ((Number) value).intValue();
    }

    private String echapperLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
