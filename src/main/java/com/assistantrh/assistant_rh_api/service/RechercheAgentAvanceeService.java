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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Moteur unifié de recherche RH. Le modèle ne produit qu'un AST JSON :
 * les champs, opérateurs, valeurs et paramètres SQL sont validés ici,
 * d'après RechercheAgentSchema. Un refus nomme toujours ce qui est permis,
 * pour que le modèle se corrige seul.
 */
@Service
public class RechercheAgentAvanceeService {
    private static final int DEFAULT_LIMIT = 20;
    /** Ressemblance minimale d'un candidat pour une identité approximative (FUZZY_PERSON). */
    private static final double PLANCHER_IDENTITE = 0.25;
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
                    hc.employe_id, hc.echelon_id, hc.categorie_id, hc.date_debut
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
            LEFT JOIN localite lr ON lr.id = e.localite_residence_id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RechercheAgentAvanceeService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Resultat rechercher(String queryJson, String resultMode) {
        if (queryJson == null || queryJson.isBlank() || queryJson.length() > 12000) {
            throw new IllegalArgumentException("criteriaJson est obligatoire (12 000 caractères maximum).");
        }
        String mode = resultMode == null || resultMode.isBlank() ? "LIST" : resultMode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("LIST", "COUNT", "GROUP").contains(mode)) {
            throw new IllegalArgumentException("resultMode doit être LIST, COUNT ou GROUP.");
        }

        Map<String, Object> root;
        try {
            root = mapper.readValue(queryJson, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("criteriaJson doit être un objet JSON valide.");
        }
        if (root == null) throw new IllegalArgumentException("La requête doit être un objet JSON.");
        rejectUnknown(root, Set.of("must", "should", "must_not", "minimum_should_match",
                "limit", "offset", "sortBy", "sortDirection", "groupBy", "measures"), "requête");
        if (mode.equals("GROUP") != root.containsKey("groupBy") || (root.containsKey("measures") && !mode.equals("GROUP"))) {
            throw new IllegalArgumentException("groupBy (et measures) vont avec resultMode=GROUP, et GROUP exige groupBy.");
        }

        // Keep pagination/sort options outside the boolean AST.
        Map<String, Object> booleanRoot = new LinkedHashMap<>();
        for (String key : Set.of("must", "should", "must_not", "minimum_should_match")) {
            if (root.containsKey(key)) booleanRoot.put(key, root.get(key));
        }
        Compiled query = compileBoolean(booleanRoot, 0, new int[]{0}, RechercheAgentSchema.AGENT, null);
        // Une liste d'agents exige un critère ; un total ou une répartition peut porter sur tous.
        if (mode.equals("LIST") && !query.hasPositiveCriterion()) {
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

        // Une répartition est entièrement validée avant le premier accès à la base.
        Repartition repartition = mode.equals("GROUP") ? preparerRepartition(root, query) : null;

        // Sans critère approché, aucune similarité n'est mesurée : score absent, jamais 0.
        String scoreSql = query.scoreExpressions().isEmpty()
                ? "NULL::double precision"
                : "GREATEST(" + String.join(", ", query.scoreExpressions()) + ")";
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT e.id) " + FROM + " WHERE " + query.sql(),
                Integer.class,
                query.arguments().toArray()
        );
        int total = count == null ? 0 : count;
        if (mode.equals("COUNT")) return new Resultat(total, 0, List.of(), 0, false);
        if (repartition != null) return new Resultat(total, 0, List.of(), 0, false, executer(repartition, query));

        // Une colonne par explication (c0, c1…), au plus une par nom.
        Map<String, Explication> explications = new LinkedHashMap<>();
        for (Explication explication : query.explications()) {
            String nom = explication.nom();
            for (int n = 2; explications.containsKey(nom) && !explication.arguments().isEmpty(); n++) {
                nom = explication.nom() + "_" + n;
            }
            explications.putIfAbsent(nom, explication);
        }
        List<String> noms = new ArrayList<>(explications.keySet());
        StringBuilder colonnes = new StringBuilder();
        for (int i = 0; i < noms.size(); i++) {
            colonnes.append(",\n                        ").append(explications.get(noms.get(i)).sql()).append(" AS c").append(i);
        }
        String colonnesExternes = noms.isEmpty() ? "" : ", " + String.join(", ",
                java.util.stream.IntStream.range(0, noms.size()).mapToObj(i -> "c" + i).toList());

        // Les paramètres du SELECT (scores, puis explications) précèdent ceux du WHERE dans le SQL.
        List<Object> args = new ArrayList<>(query.scoreArguments());
        explications.values().forEach(explication -> args.addAll(explication.arguments()));
        args.addAll(query.arguments());
        args.add(limit);
        args.add(offset);
        String sql = """
                SELECT id, nom, prenom, type_emploi, poste, service, direction,
                       score_correspondance""" + colonnesExternes + """

                FROM (
                    SELECT DISTINCT ON (e.id)
                        e.id, e.nom, e.prenom, te.nom AS type_emploi,
                        EXTRACT(YEAR FROM age(CURRENT_DATE, e.date_naissance))::INTEGER AS _sort_age,
                        e.date_embauche AS _sort_date_embauche,
                        p.intitule AS poste,
                        s.nom AS service,
                        d.nom AS direction,
                        """ + scoreSql + " AS score_correspondance" + colonnes + "\n" + FROM + """
                    WHERE """ + query.sql() + """
                    ORDER BY e.id, score_correspondance DESC
                ) candidats
                ORDER BY
                """ + order + " LIMIT ? OFFSET ?";
        List<Map<String, Object>> agents = new ArrayList<>();
        for (Map<String, Object> ligne : jdbc.queryForList(sql, args.toArray())) {
            Map<String, Object> agent = new LinkedHashMap<>(ligne);
            if (!noms.isEmpty()) {
                Map<String, Object> correspondance = new LinkedHashMap<>();
                for (int i = 0; i < noms.size(); i++) {
                    Object valeur = agent.remove("c" + i);
                    if (valeur != null) correspondance.put(noms.get(i), valeur);
                }
                agent.put("correspondance", correspondance);
            }
            agents.add(agent);
        }
        // Identité : s'il existe des correspondances exactes, elles seules sont montrées.
        if (contientOperateur(root, "FUZZY_PERSON")) {
            List<Map<String, Object>> exactes = agents.stream()
                    .filter(agent -> agent.get("score_correspondance") instanceof Number score
                            && score.doubleValue() >= 0.999)
                    .toList();
            if (!exactes.isEmpty()) {
                return new Resultat(exactes.size(), limit, exactes, 0, false);
            }
        }
        return new Resultat(total, limit, agents, offset, offset + agents.size() < total);
    }

    /**
     * Groupe booléen sur l'agent (champs de RechercheAgentSchema.AGENT) ou sur
     * une de ses listes. Seule la racine peut ouvrir une liste.
     */
    private Compiled compileBoolean(Map<String, Object> node, int depth, int[] count,
                                    Map<String, RechercheAgentSchema.Champ> champs, String liste) {
        if (depth > 6) throw new IllegalArgumentException("La profondeur booléenne maximale est 6.");
        rejectUnknown(node, liste == null
                ? Set.of("must", "should", "must_not", "minimum_should_match")
                : Set.of("collection", "must", "should", "must_not", "minimum_should_match"),
                liste == null ? "groupe booléen" : "liste " + liste);

        List<Compiled> must = compileGroup(node.get("must"), depth, count, champs, liste);
        List<Compiled> should = compileGroup(node.get("should"), depth, count, champs, liste);
        List<Compiled> mustNot = compileGroup(node.get("must_not"), depth, count, champs, liste);
        int minimum = integer(node.getOrDefault("minimum_should_match",
                must.isEmpty() && !should.isEmpty() ? 1 : 0),
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
        // Ce qui a retenu l'agent : les critères positifs, jamais les exclusions.
        List<Explication> explications = new ArrayList<>();
        must.forEach(item -> explications.addAll(item.explications()));
        should.forEach(item -> explications.addAll(item.explications()));
        return new Compiled(clauses.isEmpty() ? "TRUE" : String.join(" AND ", clauses),
                args, scores, scoreArgs, positive, explications);
    }

    private List<Compiled> compileGroup(Object raw, int depth, int[] count,
                                        Map<String, RechercheAgentSchema.Champ> champs, String liste) {
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
                rejectUnknown(map, Set.of("bool"), "enveloppe bool imbriquée");
                if (!(map.get("bool") instanceof Map<?, ?> nestedRaw)) {
                    throw new IllegalArgumentException("bool doit contenir un objet.");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) nestedRaw;
                result.add(compileBoolean(nested, depth + 1, count, champs, liste));
            } else if (map.containsKey("collection")) {
                result.add(compileListe(map, depth, count, liste));
            } else {
                result.add(compileLeaf(map, count, champs, liste));
            }
        }
        return result;
    }

    /**
     * {"collection":"diplomes","must":[…]} : l'agent a au moins un élément de
     * la liste qui répond au groupe ; sans critère, au moins un élément.
     * Une similarité mesurée dans une liste filtre sans compter dans le score.
     */
    private Compiled compileListe(Map<String, Object> node, int depth, int[] count, String parent) {
        if (parent != null) {
            throw new IllegalArgumentException("Une liste ne s'ouvre qu'au niveau de l'agent, pas dans la liste " + parent + ".");
        }
        String nom = required(node.get("collection"), "collection").toLowerCase(Locale.ROOT);
        RechercheAgentSchema.Liste liste = RechercheAgentSchema.LISTES.get(nom);
        if (liste == null) {
            throw new IllegalArgumentException("Liste inconnue : " + nom + ". Listes : "
                    + String.join(", ", RechercheAgentSchema.LISTES.keySet()) + ".");
        }
        Compiled inner = compileBoolean(node, depth + 1, count, liste.champs(), nom);
        String elements = " FROM " + liste.from() + " WHERE " + liste.lien() + " AND (" + inner.sql() + ")";
        Explication explication = new Explication(nom,
                "(SELECT string_agg(DISTINCT " + liste.resume() + ", ' ; ')" + elements + ")", inner.arguments());
        return new Compiled("EXISTS (SELECT 1" + elements + ")", inner.arguments(),
                List.of(), List.of(), true, List.of(explication));
    }

    private static final int MAX_GROUPES = 100;
    private static final Set<RechercheAgentSchema.Type> REGROUPABLES = Set.of(
            RechercheAgentSchema.Type.TEXTE, RechercheAgentSchema.Type.CODE, RechercheAgentSchema.Type.SEXE,
            RechercheAgentSchema.Type.RANG, RechercheAgentSchema.Type.NOMBRE, RechercheAgentSchema.Type.DATE);
    private static final Map<String, String> MESURES = Map.of(
            "AVG", "moyenne", "MIN", "minimum", "MAX", "maximum", "SUM", "somme");

    /**
     * Répartition des agents retenus par un ou deux champs : un champ de
     * l'agent (« direction ») ou d'une de ses listes (« diplomes.niveau » :
     * chaque agent compte une fois par niveau qu'il détient). Une date est
     * regroupée par année, un rang par libellé dans son ordre. Chaque groupe
     * donne le nombre d'agents et, au besoin, des mesures sur l'agent.
     */
    private Repartition preparerRepartition(Map<String, Object> root, Compiled query) {
        List<String> cles = textes(root.get("groupBy"), "groupBy");
        if (cles.isEmpty() || cles.size() > 2) {
            throw new IllegalArgumentException("groupBy doit nommer un ou deux champs, par exemple [\"direction\", \"sexe\"].");
        }
        List<String> colonnes = new ArrayList<>();
        List<String> regroupement = new ArrayList<>();
        List<String> tri = new ArrayList<>();
        String jointure = "";
        String listeJointe = null;
        RechercheAgentSchema.Type premierType = null;
        for (int i = 0; i < cles.size(); i++) {
            String cle = cles.get(i).toLowerCase(Locale.ROOT);
            RechercheAgentSchema.Champ champ;
            int point = cle.indexOf('.');
            if (point > 0) {
                String nomListe = cle.substring(0, point);
                RechercheAgentSchema.Liste liste = RechercheAgentSchema.LISTES.get(nomListe);
                if (liste == null) {
                    throw new IllegalArgumentException("Liste inconnue dans groupBy : " + nomListe + ". Listes : "
                            + String.join(", ", RechercheAgentSchema.LISTES.keySet()) + ".");
                }
                if (listeJointe != null && !listeJointe.equals(nomListe)) {
                    throw new IllegalArgumentException("groupBy ne peut répartir que sur une seule liste à la fois.");
                }
                champ = liste.champs().get(cle.substring(point + 1));
                if (champ == null) {
                    throw new IllegalArgumentException("Champ inconnu dans la liste " + nomListe + " : "
                            + cle.substring(point + 1) + ". Champs : " + String.join(", ", liste.champs().keySet()) + ".");
                }
                listeJointe = nomListe;
                jointure = " LEFT JOIN (" + liste.from() + ") ON " + liste.lien();
            } else {
                champ = RechercheAgentSchema.AGENT.get(cle);
                if (champ == null) {
                    throw new IllegalArgumentException("Champ inconnu dans groupBy : " + cle + ". Champs de l'agent : "
                            + String.join(", ", RechercheAgentSchema.AGENT.keySet())
                            + " ; ou liste.champ (diplomes.niveau…).");
                }
            }
            if (!REGROUPABLES.contains(champ.type())) {
                throw new IllegalArgumentException("Impossible de répartir par " + cle + " : choisissez un champ de texte, code, sexe, rang, nombre ou date.");
            }
            if (i == 0) premierType = champ.type();
            String valeur = switch (champ.type()) {
                case DATE -> "EXTRACT(YEAR FROM " + champ.sql() + ")::INTEGER";
                case RANG -> champ.libelleSql();
                case CODE, SEXE -> "UPPER(BTRIM(" + champ.sql() + "))";
                default -> "NULLIF(BTRIM((" + champ.sql() + ")::text), '')";
            };
            colonnes.add(valeur + " AS g" + i);
            regroupement.add("g" + i);
            if (champ.type() == RechercheAgentSchema.Type.RANG) {
                colonnes.add(champ.sql() + " AS r" + i);
                regroupement.add("r" + i);
                tri.add("r" + i + " ASC NULLS LAST");
            } else {
                tri.add("g" + i + " ASC NULLS LAST");
            }
        }
        // Un rang, un nombre ou une année se lisent dans leur ordre ; un texte, du plus fréquent au moins fréquent.
        boolean ordonne = Set.of(RechercheAgentSchema.Type.RANG, RechercheAgentSchema.Type.NOMBRE,
                RechercheAgentSchema.Type.DATE).contains(premierType);

        List<String> mesures = new ArrayList<>();
        Object brutes = root.get("measures");
        if (brutes != null) {
            if (!(brutes instanceof List<?> liste) || liste.size() > 3) {
                throw new IllegalArgumentException("measures doit être une liste de 3 mesures au plus.");
            }
            for (int i = 0; i < liste.size(); i++) {
                if (!(liste.get(i) instanceof Map<?, ?> mesure)) {
                    throw new IllegalArgumentException("Chaque mesure est un objet {\"operator\":\"AVG\",\"field\":\"age\"}.");
                }
                rejectUnknown(mesure, Set.of("operator", "field"), "mesure");
                String op = required(mesure.get("operator"), "operator").toUpperCase(Locale.ROOT);
                String field = required(mesure.get("field"), "field").toLowerCase(Locale.ROOT);
                RechercheAgentSchema.Champ champ = RechercheAgentSchema.AGENT.get(field);
                boolean permis = champ != null && MESURES.containsKey(op)
                        && (champ.type() == RechercheAgentSchema.Type.NOMBRE
                        || (champ.type() == RechercheAgentSchema.Type.DATE && Set.of("MIN", "MAX").contains(op)));
                if (!permis) {
                    throw new IllegalArgumentException("Mesure non autorisée : " + op + " sur " + field
                            + ". AVG, MIN, MAX ou SUM sur un nombre de l'agent (age, echelon, anciennete_echelon) ; MIN ou MAX sur une de ses dates.");
                }
                colonnes.add(champ.sql() + " AS m" + i);
                String agregat = op.equals("AVG") ? "ROUND(AVG(m" + i + ")::numeric, 1)" : op + "(m" + i + ")";
                mesures.add(agregat + " AS \"" + MESURES.get(op) + "_" + field + "\"");
            }
        }

        String sql = "SELECT " + String.join(", ", regroupement) + ", COUNT(*) AS nombre"
                + (mesures.isEmpty() ? "" : ", " + String.join(", ", mesures))
                + " FROM (SELECT DISTINCT e.id, " + String.join(", ", colonnes) + " " + FROM + jointure
                + " WHERE " + query.sql() + ") repartition"
                + " GROUP BY " + String.join(", ", regroupement)
                + " ORDER BY " + (ordonne ? "" : "nombre DESC, ") + String.join(", ", tri)
                + " LIMIT " + MAX_GROUPES;
        return new Repartition(sql, cles);
    }

    /** Une répartition prête : son SQL et le nom de ses clés, dans l'ordre de groupBy. */
    private record Repartition(String sql, List<String> cles) {
    }

    private List<Map<String, Object>> executer(Repartition repartition, Compiled query) {
        List<Map<String, Object>> groupes = new ArrayList<>();
        for (Map<String, Object> ligne : jdbc.queryForList(repartition.sql(), query.arguments().toArray())) {
            Map<String, Object> groupe = new LinkedHashMap<>();
            for (int i = 0; i < repartition.cles().size(); i++) {
                groupe.put(repartition.cles().get(i), ligne.get("g" + i));
            }
            ligne.forEach((nom, valeur) -> {
                if (!nom.matches("[gr]\\d")) groupe.put(nom, valeur);
            });
            groupes.add(groupe);
        }
        return groupes;
    }

    private List<String> textes(Object value, String name) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException(name + " doit être une liste de noms de champs.");
        }
        return list.stream().map(item -> ((String) item).trim()).toList();
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

    private static final Set<String> COMPARAISONS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN");
    private static final Map<RechercheAgentSchema.Type, Set<String>> OPERATEURS = Map.of(
            RechercheAgentSchema.Type.TEXTE, Set.of("EQ", "NE", "CONTAINS", "FUZZY"),
            RechercheAgentSchema.Type.PERSONNE, Set.of("EQ", "NE", "CONTAINS", "FUZZY", "FUZZY_PERSON"),
            RechercheAgentSchema.Type.CODE, Set.of("EQ", "NE", "CONTAINS", "FUZZY"),
            RechercheAgentSchema.Type.IDENTIFIANT, Set.of("EQ", "NE", "CONTAINS"),
            RechercheAgentSchema.Type.SEXE, Set.of("EQ", "NE"),
            RechercheAgentSchema.Type.NOMBRE, COMPARAISONS,
            RechercheAgentSchema.Type.RANG, COMPARAISONS,
            RechercheAgentSchema.Type.DATE, Set.of("EQ", "BEFORE", "AFTER", "BETWEEN",
                    "WITHIN_LAST_MONTHS", "WITHIN_LAST_YEARS", "OLDER_THAN_YEARS",
                    "WITHIN_NEXT_MONTHS", "TODAY_OR_BEFORE")
    );
    /** Valables pour tout champ : la valeur est vide, ou renseignée. */
    private static final Set<String> UNIVERSELS = Set.of("IS_EMPTY", "IS_NOT_EMPTY");
    private static final Set<String> SANS_VALEUR = Set.of("IS_EMPTY", "IS_NOT_EMPTY", "TODAY_OR_BEFORE");

    private Compiled compileLeaf(Map<String, Object> leaf, int[] count,
                                 Map<String, RechercheAgentSchema.Champ> champs, String liste) {
        if (++count[0] > MAX_NODES) throw new IllegalArgumentException("La requête dépasse 40 critères.");
        rejectUnknown(leaf, Set.of("field", "operator", "value", "upperValue"), "critère");
        String field = required(leaf.get("field"), "field").toLowerCase(Locale.ROOT);
        String op = required(leaf.get("operator"), "operator").toUpperCase(Locale.ROOT);
        Object value = leaf.get("value");
        Object upper = leaf.get("upperValue");
        RechercheAgentSchema.Champ champ = champs.get(field);
        if (champ == null) {
            throw new IllegalArgumentException(liste == null
                    ? "Champ de recherche non autorisé : " + field + ". Champs de l'agent : "
                    + String.join(", ", champs.keySet()) + " ; listes (clé collection) : "
                    + String.join(", ", RechercheAgentSchema.LISTES.keySet()) + "."
                    : "Champ inconnu dans la liste " + liste + " : " + field + ". Champs : "
                    + String.join(", ", champs.keySet()) + ".");
        }
        RechercheAgentSchema.Type type = champ.type();
        if (!UNIVERSELS.contains(op) && !OPERATEURS.get(type).contains(op)) {
            throw new IllegalArgumentException("Opérateur " + op + " non autorisé pour " + field
                    + ". Opérateurs : " + String.join(", ", new TreeSet<>(OPERATEURS.get(type)))
                    + ", IS_EMPTY, IS_NOT_EMPTY.");
        }
        boolean sansValeur = SANS_VALEUR.contains(op);
        if (sansValeur ? value != null : value == null) {
            throw new IllegalArgumentException(sansValeur ? op + " s'utilise sans value." : "value est obligatoire.");
        }
        if (!op.equals("BETWEEN") && upper != null) throw new IllegalArgumentException("upperValue est réservé à BETWEEN.");

        String expression = switch (type) {
            case TEXTE, PERSONNE -> normalized(champ.sql());
            case CODE, IDENTIFIANT, SEXE -> "UPPER(BTRIM(" + champ.sql() + "))";
            case NOMBRE, DATE, RANG -> champ.sql();
        };
        List<Object> args = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        List<Object> scoreArgs = new ArrayList<>();
        String sql;

        if (UNIVERSELS.contains(op)) {
            sql = "NULLIF(BTRIM((" + champ.sql() + ")::text), '') IS "
                    + (op.equals("IS_EMPTY") ? "" : "NOT ") + "NULL";
        } else if (op.equals("FUZZY_PERSON")) {
            String query = normalize(required(value, "value"));
            if (query.length() < 3 || query.length() > 150) {
                throw new IllegalArgumentException("Une identité approximative doit contenir 3 à 150 caractères.");
            }
            String nom = normalized("e.nom");
            String prenom = normalized("e.prenom");
            String full = normalized("concat_ws(' ', e.nom, e.prenom)");
            String reverse = normalized("concat_ws(' ', e.prenom, e.nom)");
            // Les candidats les plus proches, classés par ressemblance, au-dessus d'un plancher bas :
            // un nom mal retenu doit faire apparaître la bonne personne parmi eux.
            String ressemblance = "GREATEST(similarity(" + full + ", ?), similarity(" + reverse
                    + ", ?), word_similarity(?, " + nom + "), word_similarity(?, " + prenom + "))";
            sql = ressemblance + " >= " + PLANCHER_IDENTITE;
            for (int i = 0; i < 4; i++) args.add(query);
            scores.add(ressemblance);
            for (int i = 0; i < 4; i++) scoreArgs.add(query);
        } else if (op.equals("FUZZY")) {
            String query = normalize(required(value, "value"));
            if (type == RechercheAgentSchema.Type.CODE) query = query.toUpperCase(Locale.ROOT);
            if (query.length() < 3 || query.length() > 150) {
                throw new IllegalArgumentException("FUZZY exige 3 à 150 caractères.");
            }
            sql = fuzzyCondition(expression);
            addFuzzyArguments(args, query);
            scores.add("GREATEST(similarity(" + expression + ", ?), word_similarity(?, " + expression + "))");
            scoreArgs.add(query); scoreArgs.add(query);
        } else if (type == RechercheAgentSchema.Type.NOMBRE || type == RechercheAgentSchema.Type.RANG) {
            boolean rang = type == RechercheAgentSchema.Type.RANG;
            if (op.equals("BETWEEN")) {
                int low = rang ? rang(champ, field, value) : integer(value, field);
                int high = rang ? rang(champ, field, upper) : integer(upper, "upperValue");
                if (low < 0 || high < low) throw new IllegalArgumentException("Bornes BETWEEN incohérentes.");
                sql = expression + " BETWEEN ? AND ?"; args.add(low); args.add(high);
            } else {
                int n = rang ? rang(champ, field, value) : integer(value, field);
                if (n < 0) throw new IllegalArgumentException("Valeur numérique hors limites.");
                sql = expression + " " + comparison(op) + " ?"; args.add(n);
            }
        } else if (type == RechercheAgentSchema.Type.DATE) {
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
                    sql = expression + " BETWEEN CURRENT_DATE - (? * INTERVAL '1 month') AND CURRENT_DATE";
                    args.add(periode(value, 1200, "mois"));
                }
                case "WITHIN_LAST_YEARS" -> {
                    sql = expression + " BETWEEN CURRENT_DATE - (? * INTERVAL '1 year') AND CURRENT_DATE";
                    args.add(periode(value, 100, "années"));
                }
                case "WITHIN_NEXT_MONTHS" -> {
                    sql = expression + " BETWEEN CURRENT_DATE AND CURRENT_DATE + (? * INTERVAL '1 month')";
                    args.add(periode(value, 1200, "mois"));
                }
                case "OLDER_THAN_YEARS" -> {
                    sql = expression + " <= CURRENT_DATE - (? * INTERVAL '1 year')";
                    args.add(periode(value, 100, "années"));
                }
                default -> sql = expression + " <= CURRENT_DATE";
            }
        } else {
            String normalizedValue = normalize(required(value, "value"));
            if (type == RechercheAgentSchema.Type.SEXE) {
                normalizedValue = normalizeSex(normalizedValue);
            } else if (type == RechercheAgentSchema.Type.CODE || type == RechercheAgentSchema.Type.IDENTIFIANT) {
                normalizedValue = normalizedValue.toUpperCase(Locale.ROOT);
            }
            switch (op) {
                case "EQ" -> { sql = expression + " = ?"; args.add(normalizedValue); }
                case "NE" -> { sql = expression + " <> ?"; args.add(normalizedValue); }
                default -> { sql = expression + " LIKE ? ESCAPE '!'"; args.add("%" + escapeLike(normalizedValue) + "%"); }
            }
        }
        // Sur l'agent, la valeur du champ filtré explique le choix ; le nom et le
        // prénom sont déjà dans le résultat, et un élément de liste s'explique par sa liste.
        boolean explique = liste == null && type != RechercheAgentSchema.Type.PERSONNE
                && !Set.of("nom", "prenom").contains(field);
        List<Explication> explications = explique
                ? List.of(new Explication(field, type == RechercheAgentSchema.Type.RANG
                        ? champ.libelleSql() : champ.sql(), List.of()))
                : List.of();
        return new Compiled(sql, args, scores, scoreArgs, true, explications);
    }

    /** Rang d'un libellé (ou d'un code) du référentiel ordonné ; un nombre est pris tel quel. */
    private int rang(RechercheAgentSchema.Champ champ, String field, Object value) {
        if (value instanceof Number) return integer(value, field);
        String voulu = normalize(required(value, "value"));
        List<Map<String, Object>> valeurs = jdbc.queryForList(champ.valeursSql());
        for (Map<String, Object> valeur : valeurs) {
            if (voulu.equals(normalize(String.valueOf(valeur.get("libelle"))))
                    || voulu.equalsIgnoreCase(String.valueOf(valeur.get("code")))) {
                return ((Number) valeur.get("rang")).intValue();
            }
        }
        throw new IllegalArgumentException("Valeur inconnue pour " + field + " : " + value
                + ". Valeurs, de la plus basse à la plus haute : "
                + String.join(", ", valeurs.stream().map(v -> String.valueOf(v.get("libelle"))).toList()) + ".");
    }

    private int periode(Object value, int maximum, String unite) {
        int n = integer(value, "value");
        if (n < 1 || n > maximum) throw new IllegalArgumentException("Période en " + unite + " invalide.");
        return n;
    }

    private String fuzzyCondition(String expression) {
        return "((" + expression + " % ? AND similarity(" + expression + ", ?) >= 0.30)"
                + " OR word_similarity(?, " + expression + ") >= 0.55"
                + " OR (length(" + expression + ") <= 255 AND length(?) <= 255"
                + " AND levenshtein(" + expression + ", ?) <= GREATEST(1, length(?) / 5))"
                // Phonetics are only a corroborating signal, never a standalone identity match.
                + " OR (dmetaphone(" + expression + ") <> ''"
                + " AND dmetaphone(" + expression + ") = dmetaphone(?)"
                + " AND similarity(" + expression + ", ?) >= 0.20))";
    }

    /** Le critère contient-il quelque part cet opérateur ? */
    private static boolean contientOperateur(Object noeud, String operateur) {
        if (noeud instanceof Map<?, ?> map) {
            if (operateur.equals(map.get("operator"))) return true;
            return map.values().stream().anyMatch(valeur -> contientOperateur(valeur, operateur));
        }
        if (noeud instanceof List<?> liste) {
            return liste.stream().anyMatch(valeur -> contientOperateur(valeur, operateur));
        }
        return false;
    }

    private void addFuzzyArguments(List<Object> args, String query) {
        for (int i = 0; i < 8; i++) args.add(query);
    }

    private String normalized(String sql) {
        return "translate(lower(coalesce(" + sql + ", '')), "
                + "U&'\\00e0\\00e1\\00e2\\00e3\\00e4\\00e5\\00e8\\00e9\\00ea"
                + "\\00eb\\00ec\\00ed\\00ee\\00ef\\00f2\\00f3\\00f4\\00f5"
                + "\\00f6\\00f9\\00fa\\00fb\\00fc\\00e7\\00f1\\00fd\\00ff', "
                + "'aaaaaaeeeeiiiiooooouuuucnyy')";
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
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
                            boolean hasPositiveCriterion, List<Explication> explications) {
        Compiled(String sql, List<Object> arguments, List<String> scoreExpressions,
                 List<Object> scoreArguments, boolean hasPositiveCriterion) {
            this(sql, arguments, scoreExpressions, scoreArguments, hasPositiveCriterion, List.of());
        }
    }

    /** Ce qui explique qu'un agent est retenu : un nom et le SQL de sa valeur pour cet agent. */
    private record Explication(String nom, String sql, List<Object> arguments) {}
    public record Resultat(int total, int limite, List<Map<String, Object>> agents,
                           int offset, boolean hasMore, List<Map<String, Object>> groupes) {
        public Resultat(int total, int limite, List<Map<String, Object>> agents, int offset, boolean hasMore) {
            this(total, limite, agents, offset, hasMore, List.of());
        }
    }
}
