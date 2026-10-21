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

        for (RechercheStructureeAgentService.Filtre filtre : filtres) {
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
                    scoreArguments.add(valeur);
                    filterArguments.add(valeur);
                    filterArguments.add(SCORE_SIMILARITE_MOT_MINIMAL);
                    scores.add("GREATEST(similarity(" + expression
                            + ", ?), word_similarity(?, " + expression + "))");
                    scoreArguments.add(valeur);
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
                : "GREATEST(" + String.join(", ", scores) + ")";
        StringBuilder sql = new StringBuilder("""
                SELECT
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
                     AS score_correspondance
                FROM employe e
                LEFT JOIN affectation af
                    ON af.employe_id = e.id
                    AND af.date_fin IS NULL
                    AND af.date_debut <= CURRENT_DATE
                LEFT JOIN poste p ON p.id = af.poste_id
                LEFT JOIN service s ON s.id = af.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                LEFT JOIN type_emploi te ON te.id = e.type_emploi_id
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

        if (clauses.isEmpty()) {
            throw new IllegalArgumentException(
                    "Au moins un critère de recherche est obligatoire."
            );
        }
        sql.append(String.join(" AND ", clauses));
        String sortExpression = tri == null ? "e.nom" : expression(tri);
        String sortDirection = "DESC".equals(direction) ? "DESC" : "ASC";
        sql.append(" ORDER BY ");
        if (!scores.isEmpty()) {
            sql.append("score_correspondance DESC, ");
        }
        sql.append(sortExpression).append(' ').append(sortDirection)
                .append(", e.id LIMIT ? OFFSET ?");
        List<Object> arguments = new ArrayList<>(scoreArguments);
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
        long mappingStartedAtNanos = System.nanoTime();
        List<AgentRechercheDto> agents = lignes.stream()
                .limit(limite)
                .map(this::versDto)
                .toList();
        performanceTelemetry.applicationProcessing(
                "structured_agent_search_mapping",
                mappingStartedAtNanos
        );
        return new ResultatPage(agents, hasMore);
    }

    public record ResultatPage(
            List<AgentRechercheDto> agents,
            boolean hasMore
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
            case "adresse" -> texteNormalise("e.adresse");
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
