package com.assistantrh.assistant_rh_api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Moteur unifié de recherche RH. Le modèle ne produit qu'un AST JSON :
 * les champs, opérateurs, valeurs et paramètres SQL sont validés ici.
 */
@Service
public class RechercheAgentAvanceeService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_OFFSET = 10000;
    private static final int MAX_NODES = 40;
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};
    private static final String FROM = """
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
                    hc.employe_id, hc.echelon_id, hc.categorie_id
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
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RechercheAgentAvanceeService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Resultat rechercher(String queryJson, String resultMode) {
        if (queryJson == null || queryJson.isBlank() || queryJson.length() > 12000) {
            throw new IllegalArgumentException("queryJson est obligatoire (12 000 caractères maximum).");
        }
        String mode = resultMode == null ? "LIST" : resultMode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("LIST", "COUNT").contains(mode)) {
            throw new IllegalArgumentException("resultMode doit être LIST ou COUNT.");
        }

        Map<String, Object> root;
        try {
            root = mapper.readValue(queryJson, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("queryJson doit être un objet JSON valide.");
        }
        if (root == null) throw new IllegalArgumentException("La requête doit être un objet JSON.");
        rejectUnknown(root, Set.of("must", "should", "must_not", "minimum_should_match",
                "limit", "offset", "sortBy", "sortDirection"), "requête");

        Compiled query = compileBoolean(root, 0, new int[]{0});
        if (!query.hasPositiveCriterion()) {
            throw new IllegalArgumentException("Au moins un critère positif dans must ou should est obligatoire.");
        }

        int limit = integer(root.getOrDefault("limit", DEFAULT_LIMIT), "limit");
        int offset = integer(root.getOrDefault("offset", 0), "offset");
        if (limit < 1 || limit > MAX_LIMIT || offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("limit doit être entre 1 et 50 et offset entre 0 et 10000.");
        }
        String sortBy = string(root.get("sortBy"), "score");
        String sortDirection = string(root.get("sortDirection"), "DESC").toUpperCase(Locale.ROOT);
        if (!Set.of("ASC", "DESC").contains(sortDirection)) {
            throw new IllegalArgumentException("sortDirection doit être ASC ou DESC.");
        }
        String order = switch (sortBy) {
            case "score" -> "score_correspondance " + sortDirection + ", nom ASC, prenom ASC, id ASC";
            case "nom" -> "nom " + sortDirection + ", prenom ASC, id ASC";
            case "prenom" -> "prenom " + sortDirection + ", nom ASC, id ASC";
            case "age" -> "_sort_age " + sortDirection + " NULLS LAST, nom ASC, id ASC";
            case "date_embauche" -> "_sort_date_embauche " + sortDirection + " NULLS LAST, id ASC";
            case "poste" -> "poste " + sortDirection + " NULLS LAST, nom ASC, id ASC";
            case "service" -> "service " + sortDirection + " NULLS LAST, nom ASC, id ASC";
            case "direction" -> "direction " + sortDirection + " NULLS LAST, nom ASC, id ASC";
            default -> throw new IllegalArgumentException("Champ de tri non autorisé : " + sortBy);
        };

        String scoreSql = query.scoreExpressions().isEmpty()
                ? "0.0::double precision"
                : "GREATEST(" + String.join(", ", query.scoreExpressions()) + ")";
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT e.id) " + FROM + " WHERE " + query.sql(),
                Integer.class,
                query.arguments().toArray()
        );
        int total = count == null ? 0 : count;
        if (mode.equals("COUNT")) return new Resultat(total, 0, List.of(), 0, false);

        // Les paramètres du SELECT (scores) précèdent ceux du WHERE dans le SQL.
        List<Object> args = new ArrayList<>(query.scoreArguments());
        args.addAll(query.arguments());
        args.add(limit);
        args.add(offset);
        String sql = """
                SELECT id, nom, prenom, poste, service, direction,
                       score_correspondance
                FROM (
                    SELECT DISTINCT ON (e.id)
                        e.id, e.nom, e.prenom,
                        EXTRACT(YEAR FROM age(CURRENT_DATE, e.date_naissance))::INTEGER AS _sort_age,
                        e.date_embauche AS _sort_date_embauche,
                        p.intitule AS poste,
                        s.nom AS service,
                        d.nom AS direction,
                        """ + scoreSql + """
                         AS score_correspondance
                    """ + FROM + """
                    WHERE """ + query.sql() + """
                    ORDER BY e.id, score_correspondance DESC
                ) candidats
                ORDER BY """ + order + " LIMIT ? OFFSET ?";
        List<Map<String, Object>> agents = jdbc.queryForList(sql, args.toArray());
        return new Resultat(total, limit, agents, offset, offset + agents.size() < total);
    }

    private Compiled compileBoolean(Map<String, Object> node, int depth, int[] count) {
        if (depth > 6) throw new IllegalArgumentException("La profondeur booléenne maximale est 6.");
        rejectUnknown(node, Set.of("must", "should", "must_not", "minimum_should_match"), "groupe booléen");

        List<Compiled> must = compileGroup(node.get("must"), depth, count);
        List<Compiled> should = compileGroup(node.get("should"), depth, count);
        List<Compiled> mustNot = compileGroup(node.get("must_not"), depth, count);
        int minimum = integer(node.getOrDefault("minimum_should_match",
                must.isEmpty() && mustNot.isEmpty() && !should.isEmpty() ? 1 : 0),
                "minimum_should_match");
        if (minimum < 0 || minimum > should.size() || minimum > 1) {
            throw new IllegalArgumentException("minimum_should_match doit valoir 0 ou 1 et correspondre au groupe should.");
        }

        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        List<Object> scoreArgs = new ArrayList<>();
        appendCompiledGroup(must, " AND ", clauses, args);
        if (!should.isEmpty() && minimum == 1) appendCompiledGroup(should, " OR ", clauses, args);
        for (Compiled excluded : mustNot) {
            clauses.add("NOT (" + excluded.sql() + ")");
            args.addAll(excluded.arguments());
        }
        // Seuls les critères positifs participent au score.
        appendScores(must, scores, scoreArgs);
        appendScores(should, scores, scoreArgs);
        boolean positive = must.stream().anyMatch(Compiled::hasPositiveCriterion) || (!should.isEmpty() && minimum == 1 && should.stream().anyMatch(Compiled::hasPositiveCriterion));
        return new Compiled(clauses.isEmpty() ? "TRUE" : String.join(" AND ", clauses),
                args, scores, scoreArgs, positive);
    }

    private List<Compiled> compileGroup(Object raw, int depth, int[] count) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list) || list.size() > MAX_NODES) {
            throw new IllegalArgumentException("Chaque groupe must/should/must_not doit être une liste de 40 clauses maximum.");
        }
        List<Compiled> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("Chaque clause doit être un objet JSON.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            if (map.containsKey("bool")) {
                if (!(map.get("bool") instanceof Map<?, ?> nestedRaw)) {
                    throw new IllegalArgumentException("bool doit contenir un objet.");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) nestedRaw;
                result.add(compileBoolean(nested, depth + 1, count));
            } else {
                result.add(compileLeaf(map, count));
            }
        }
        return result;
    }

    private void appendCompiledGroup(List<Compiled> group, String separator,
                                     List<String> clauses, List<Object> args) {
        if (group.isEmpty()) return;
        clauses.add("(" + String.join(separator, group.stream().map(Compiled::sql).toList()) + ")");
        group.forEach(item -> args.addAll(item.arguments()));
    }

    private void appendScores(List<Compiled> group, List<String> scores, List<Object> args) {
        for (Compiled item : group) {
            scores.addAll(item.scoreExpressions());
            args.addAll(item.scoreArguments());
        }
    }

    private Compiled compileLeaf(Map<String, Object> leaf, int[] count) {
        if (++count[0] > MAX_NODES) throw new IllegalArgumentException("La requête dépasse 40 critères.");
        rejectUnknown(leaf, Set.of("field", "operator", "value", "upperValue"), "critère");
        String field = required(leaf.get("field"), "field").toLowerCase(Locale.ROOT);
        String op = required(leaf.get("operator"), "operator").toUpperCase(Locale.ROOT);
        Object value = leaf.get("value");
        Object upper = leaf.get("upperValue");
        if (value == null) throw new IllegalArgumentException("value est obligatoire.");
        String expression = expression(field);
        List<Object> args = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        List<Object> scoreArgs = new ArrayList<>();
        String sql;

        if (op.equals("FUZZY_PERSON")) {
            if (!Set.of("personne", "nom_complet").contains(field)) {
                throw new IllegalArgumentException("FUZZY_PERSON s'utilise avec personne ou nom_complet.");
            }
            String query = normalize(required(value, "value"));
            if (query.length() < 3 || query.length() > 150) {
                throw new IllegalArgumentException("Une identité approximative doit contenir 3 à 150 caractères.");
            }
            String nom = normalized("e.nom");
            String prenom = normalized("e.prenom");
            String full = normalized("concat_ws(' ', e.nom, e.prenom)");
            String reverse = normalized("concat_ws(' ', e.prenom, e.nom)");
            String fuzzyFull = fuzzyCondition(full);
            String fuzzyReverse = fuzzyCondition(reverse);
            sql = "(" + fuzzyFull + " OR " + fuzzyReverse
                    + " OR word_similarity(?, " + nom + ") >= 0.55"
                    + " OR word_similarity(?, " + prenom + ") >= 0.55)";
            addFuzzyArguments(args, query);
            addFuzzyArguments(args, query);
            args.add(query);
            args.add(query);
            scores.add("GREATEST(similarity(" + full + ", ?), similarity(" + reverse
                    + ", ?), word_similarity(?, " + nom + "), word_similarity(?, " + prenom + "))");
            scoreArgs.add(query); scoreArgs.add(query); scoreArgs.add(query); scoreArgs.add(query);
        } else if (op.equals("FUZZY")) {
            if (!isText(field) || Set.of("matricule", "sexe").contains(field)) {
                throw new IllegalArgumentException("FUZZY n'est pas autorisé pour ce champ.");
            }
            String query = normalize(required(value, "value"));
            if (query.length() < 3 || query.length() > 150) {
                throw new IllegalArgumentException("FUZZY exige 3 à 150 caractères.");
            }
            sql = fuzzyCondition(expression);
            addFuzzyArguments(args, query);
            scores.add("GREATEST(similarity(" + expression + ", ?), word_similarity(?, " + expression + "))");
            scoreArgs.add(query); scoreArgs.add(query);
        } else if (isText(field)) {
            String normalizedValue = normalize(required(value, "value"));
            if (Set.of("sexe", "matricule", "categorie").contains(field)) {
                normalizedValue = field.equals("sexe") ? normalizeSex(normalizedValue)
                        : normalizedValue.toUpperCase(Locale.ROOT);
            }
            switch (op) {
                case "EQ" -> { sql = expression + " = ?"; args.add(normalizedValue); }
                case "NE" -> { sql = expression + " <> ?"; args.add(normalizedValue); }
                case "CONTAINS" -> { sql = expression + " LIKE ? ESCAPE '\\'"; args.add("%" + escapeLike(normalizedValue) + "%"); }
                default -> throw new IllegalArgumentException("Opérateur textuel non autorisé : " + op);
            }
        } else if (field.equals("age") || field.equals("echelon")) {
            switch (op) {
                case "EQ", "NE", "GT", "GTE", "LT", "LTE" -> {
                    int n = integer(value, field);
                    if (n < 0 || (field.equals("age") && n > 120)) throw new IllegalArgumentException("Valeur numérique hors limites.");
                    sql = expression + " " + comparison(op) + " ?"; args.add(n);
                }
                case "BETWEEN" -> {
                    int low = integer(value, field), high = integer(upper, "upperValue");
                    if (low < 0 || high < low || (field.equals("age") && high > 120)) throw new IllegalArgumentException("Bornes BETWEEN incohérentes.");
                    sql = expression + " BETWEEN ? AND ?"; args.add(low); args.add(high);
                }
                default -> throw new IllegalArgumentException("Opérateur numérique non autorisé : " + op);
            }
        } else {
            switch (op) {
                case "EQ", "BEFORE", "AFTER" -> {
                    LocalDate date = parseDate(value);
                    sql = expression + " " + (op.equals("EQ") ? "=" : op.equals("BEFORE") ? "<" : ">") + " ?";
                    args.add(date);
                }
                case "BETWEEN" -> {
                    LocalDate low = parseDate(value), high = parseDate(upper);
                    if (high.isBefore(low)) throw new IllegalArgumentException("Bornes de dates incohérentes.");
                    sql = expression + " BETWEEN ? AND ?"; args.add(low); args.add(high);
                }
                case "WITHIN_LAST_MONTHS" -> {
                    int months = integer(value, "value");
                    if (months < 1 || months > 1200) throw new IllegalArgumentException("Période en mois invalide.");
                    sql = expression + " >= CURRENT_DATE - (? * INTERVAL '1 month')"; args.add(months);
                }
                case "WITHIN_LAST_YEARS" -> {
                    int years = integer(value, "value");
                    if (years < 1 || years > 100) throw new IllegalArgumentException("Période en années invalide.");
                    sql = expression + " >= CURRENT_DATE - (? * INTERVAL '1 year')"; args.add(years);
                }
                case "OLDER_THAN_YEARS" -> {
                    int years = integer(value, "value");
                    if (years < 1 || years > 100) throw new IllegalArgumentException("Période en années invalide.");
                    sql = expression + " <= CURRENT_DATE - (? * INTERVAL '1 year')"; args.add(years);
                }
                default -> throw new IllegalArgumentException("Opérateur de date non autorisé : " + op);
            }
        }
        if (!op.equals("BETWEEN") && upper != null) throw new IllegalArgumentException("upperValue est réservé à BETWEEN.");
        return new Compiled(sql, args, scores, scoreArgs, true);
    }

    private String fuzzyCondition(String expression) {
        return "((" + expression + " % ? AND similarity(" + expression + ", ?) >= 0.30)"
                + " OR word_similarity(?, " + expression + ") >= 0.55"
                + " OR (length(" + expression + ") <= 255 AND length(?) <= 255"
                + " AND levenshtein(" + expression + ", ?) <= GREATEST(1, length(?) / 5))"
                + " OR dmetaphone(" + expression + ") = dmetaphone(?))";
    }

    private void addFuzzyArguments(List<Object> args, String query) {
        for (int i = 0; i < 7; i++) args.add(query);
    }

    private String expression(String field) {
        return switch (field) {
            case "matricule" -> "UPPER(BTRIM(e.matricule))";
            case "nom" -> normalized("e.nom");
            case "prenom" -> normalized("e.prenom");
            case "personne", "nom_complet" -> normalized("concat_ws(' ', e.nom, e.prenom)");
            case "sexe" -> "UPPER(BTRIM(e.sexe))";
            case "adresse" -> normalized("e.adresse");
            case "poste" -> normalized("p.intitule");
            case "service" -> normalized("concat_ws(' ', s.code, s.nom)");
            case "direction" -> normalized("d.nom");
            case "categorie" -> "UPPER(BTRIM(c.code))";
            case "corps" -> normalized("concat_ws(' ', co.code, co.libelle)");
            case "type_emploi" -> normalized("te.nom");
            case "classe" -> normalized("cl.libelle");
            case "age" -> "EXTRACT(YEAR FROM age(CURRENT_DATE, e.date_naissance))::INTEGER";
            case "echelon" -> "ec.ordre";
            case "date_naissance" -> "e.date_naissance";
            case "date_embauche" -> "e.date_embauche";
            default -> throw new IllegalArgumentException("Champ de recherche non autorisé : " + field);
        };
    }

    private String normalized(String sql) {
        return "translate(lower(coalesce(" + sql + ", '')), "
                + "U&'\\00e0\\00e1\\00e2\\00e3\\00e4\\00e5\\00e8\\00e9\\00ea"
                + "\\00eb\\00ec\\00ed\\00ee\\00ef\\00f2\\00f3\\00f4\\00f5"
                + "\\00f6\\00f9\\00fa\\00fb\\00fc\\00e7\\00f1\\00fd\\00ff', "
                + "'aaaaaaeeeeiiiiooooouuuucnyy')";
    }

    private boolean isText(String field) {
        return Set.of("matricule", "nom", "prenom", "personne", "nom_complet", "sexe", "adresse",
                "poste", "service", "direction", "categorie", "corps", "type_emploi", "classe")
                .contains(field);
    }

    private String normalizeSex(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "FEMME", "FEMMES", "FEMININ", "F" -> "F";
            case "HOMME", "HOMMES", "MASCULIN", "M" -> "M";
            default -> value.toUpperCase(Locale.ROOT);
        };
    }

    private String comparison(String op) {
        return switch (op) {
            case "EQ" -> "="; case "NE" -> "<>"; case "GT" -> ">";
            case "GTE" -> ">="; case "LT" -> "<"; case "LTE" -> "<=";
            default -> throw new IllegalArgumentException("Comparaison invalide.");
        };
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private LocalDate parseDate(Object value) {
        try { return LocalDate.parse(required(value, "date")); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Date invalide : utilisez AAAA-MM-JJ."); }
    }

    private int integer(Object value, String name) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(name + " doit être numérique.");
        try { return new java.math.BigDecimal(number.toString()).intValueExact(); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(name + " doit être un entier."); }
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : required(value, "valeur").trim();
    }

    private String required(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " est obligatoire.");
        return text.trim();
    }

    private void rejectUnknown(Map<?, ?> values, Set<String> allowed, String label) {
        for (Object key : values.keySet()) {
            if (!(key instanceof String name) || !allowed.contains(name)) {
                throw new IllegalArgumentException("Clé non autorisée dans " + label + " : " + key);
            }
        }
    }

    private record Compiled(String sql, List<Object> arguments,
                            List<String> scoreExpressions, List<Object> scoreArguments,
                            boolean hasPositiveCriterion) {}
    public record Resultat(int total, int limite, List<Map<String, Object>> agents,
                           int offset, boolean hasMore) {}
}
