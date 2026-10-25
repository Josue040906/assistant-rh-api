package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.dto.AgentRechercheDto;
import com.assistantrh.assistant_rh_api.observability.PerformanceTelemetry;
import com.assistantrh.assistant_rh_api.repository.RechercheStructureeAgentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RechercheStructureeAgentService {
    private static final int LIMITE_PAR_DEFAUT = 20;
    private static final int LIMITE_MAXIMALE = 50;
    private static final int OFFSET_MAXIMAL = 10000;
    private static final int NOMBRE_MAXIMUM_FILTRES = 10;
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {
            };
    private static final Set<String> SORT_FIELDS = Set.of(
            "matricule", "nom", "prenom", "age", "date_embauche",
            "poste", "service", "direction", "classe", "echelon", "localite"
    );

    private final ObjectMapper objectMapper;
    private final RechercheStructureeAgentRepository repository;
    private final PerformanceTelemetry performanceTelemetry;

    @Autowired
    public RechercheStructureeAgentService(
            ObjectMapper objectMapper,
            RechercheStructureeAgentRepository repository,
            PerformanceTelemetry performanceTelemetry
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.performanceTelemetry = performanceTelemetry;
    }

    public RechercheStructureeAgentService(
            ObjectMapper objectMapper,
            RechercheStructureeAgentRepository repository
    ) {
        this(objectMapper, repository, new PerformanceTelemetry());
    }

    public ResultatRecherche rechercher(String json) {
        long processingStartedAtNanos = System.nanoTime();
        if (json == null || json.isBlank() || json.length() > 8000) {
            throw new IllegalArgumentException(
                    "Les critères JSON sont obligatoires et ne doivent pas dépasser 8000 caractères."
            );
        }

        Map<String, Object> request;
        try {
            request = objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Les critères de recherche ne sont pas un objet JSON valide."
            );
        }
        if (request == null) {
            throw new IllegalArgumentException(
                    "Les critères de recherche doivent former un objet JSON."
            );
        }
        rejectUnknownKeys(request, Set.of(
                "filters", "limit", "offset", "sortBy", "sortDirection"
        ), "paramètre de recherche");

        Object rawFilters = request.get("filters");
        if (!(rawFilters instanceof List<?> filterList)
                || filterList.isEmpty()
                || filterList.size() > NOMBRE_MAXIMUM_FILTRES) {
            throw new IllegalArgumentException(
                    "Fournissez entre 1 et 10 critères dans filters."
            );
        }

        List<Filtre> filters = filterList.stream()
                .map(this::parseFilter)
                .toList();
        int limit = request.containsKey("limit")
                ? parseInteger(request.get("limit"), "limit")
                : LIMITE_PAR_DEFAUT;
        if (limit < 1 || limit > LIMITE_MAXIMALE) {
            throw new IllegalArgumentException(
                    "La limite doit être comprise entre 1 et " + LIMITE_MAXIMALE + "."
            );
        }
        int offset = request.containsKey("offset")
                ? parseInteger(request.get("offset"), "offset")
                : 0;
        if (offset < 0 || offset > OFFSET_MAXIMAL) {
            throw new IllegalArgumentException(
                    "offset doit être compris entre 0 et " + OFFSET_MAXIMAL + "."
            );
        }

        String sortBy = optionalString(request.get("sortBy"), "sortBy");
        if (sortBy != null && !SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Champ de tri non autorisé : " + sortBy);
        }
        String sortDirection = optionalString(
                request.get("sortDirection"),
                "sortDirection"
        );
        if (sortDirection == null) {
            sortDirection = "ASC";
        } else {
            sortDirection = sortDirection.toUpperCase(Locale.ROOT);
            if (!sortDirection.equals("ASC") && !sortDirection.equals("DESC")) {
                throw new IllegalArgumentException(
                        "sortDirection doit être ASC ou DESC."
                );
            }
        }

        performanceTelemetry.applicationProcessing(
                "structured_agent_search_validation",
                processingStartedAtNanos
        );
        RechercheStructureeAgentRepository.ResultatPage page =
                repository.rechercher(filters, limit, offset, sortBy, sortDirection);
        return new ResultatRecherche(
                page.total(),
                limit,
                page.agents(),
                offset,
                page.hasMore()
        );
    }

    private Filtre parseFilter(Object rawFilter) {
        if (!(rawFilter instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("Chaque critère doit être un objet JSON.");
        }
        rejectUnknownKeys(values, Set.of("field", "operator", "value", "upperValue"),
                "critère");
        String field = requiredString(values.get("field"), "field")
                .toLowerCase(Locale.ROOT);
        String operatorText = requiredString(values.get("operator"), "operator")
                .toUpperCase(Locale.ROOT);
        Object value = values.get("value");
        Object upperValue = values.get("upperValue");
        if (value == null) {
            throw new IllegalArgumentException("La valeur d’un critère est obligatoire.");
        }

        FieldType fieldType = fieldType(field);
        Operateur operator;
        try {
            operator = Operateur.valueOf(operatorText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Opérateur de recherche non autorisé : " + operatorText
            );
        }
        validerValeurs(field, fieldType, operator, value, upperValue);
        return new Filtre(
                field,
                operator,
                normaliserValeur(field, value),
                upperValue == null ? null : normaliserValeur(field, upperValue)
        );
    }

    private void validerValeurs(
            String field,
            FieldType type,
            Operateur operator,
            Object value,
            Object upperValue
    ) {
        boolean supported = switch (type) {
            case TEXT -> Set.of(Operateur.EQ, Operateur.NE, Operateur.CONTAINS,
                    Operateur.FUZZY).contains(operator);
            case NUMBER -> Set.of(Operateur.EQ, Operateur.NE, Operateur.GT,
                    Operateur.GTE, Operateur.LT, Operateur.LTE,
                    Operateur.BETWEEN).contains(operator);
            case DATE -> Set.of(Operateur.EQ, Operateur.NE, Operateur.BEFORE,
                    Operateur.AFTER, Operateur.BETWEEN,
                    Operateur.WITHIN_LAST_YEARS,
                    Operateur.OLDER_THAN_YEARS).contains(operator);
        };
        if (!supported) {
            throw new IllegalArgumentException(
                    "L’opérateur " + operator + " ne convient pas au champ " + field + "."
            );
        }
        if (type == FieldType.TEXT) {
            if (!(value instanceof String text) || text.isBlank() || text.length() > 150) {
                throw new IllegalArgumentException(
                        "Le critère textuel " + field + " doit contenir 1 à 150 caractères."
                );
            }
            if (operator == Operateur.FUZZY && text.trim().length() < 4) {
                throw new IllegalArgumentException(
                        "Un critère approximatif doit contenir au moins 4 caractères."
                );
            }
            if (operator == Operateur.FUZZY
                    && (Set.of("matricule", "sexe", "niveau_diplome", "domaine_diplome",
                    "etablissement_diplome", "mention_diplome", "numero_diplome").contains(field))) {
                throw new IllegalArgumentException(
                        "La recherche approximative n’est pas autorisée pour le matricule ou le sexe."
                );
            }
        } else if (type == FieldType.DATE) {
            if (operator == Operateur.WITHIN_LAST_YEARS
                    || operator == Operateur.OLDER_THAN_YEARS) {
                validateYearCount(value);
            } else {
                parseDate(value);
                if (operator == Operateur.BETWEEN) {
                    parseDate(upperValue);
                    if (LocalDate.parse((String) value)
                            .isAfter(LocalDate.parse((String) upperValue))) {
                        throw new IllegalArgumentException(
                                "La date de début doit précéder la date de fin."
                        );
                    }
                }
            }
        } else {
            int numericValue = parseInteger(value, field);
            if (numericValue < 0 || (field.equals("age") && numericValue > 120)) {
                throw new IllegalArgumentException(
                        "La valeur du critère numérique " + field + " est hors limites."
                );
            }
            if (operator == Operateur.BETWEEN) {
                int upper = parseInteger(upperValue, "upperValue");
                if (upper < numericValue
                        || (field.equals("age") && upper > 120)) {
                    throw new IllegalArgumentException(
                            "Les bornes numériques sont incohérentes ou hors limites."
                    );
                }
            }
        }
        if (operator != Operateur.BETWEEN && upperValue != null) {
            throw new IllegalArgumentException(
                    "upperValue ne s’utilise qu’avec l’opérateur BETWEEN."
            );
        }
    }

    private FieldType fieldType(String field) {
        return switch (field) {
            case "matricule", "nom", "prenom", "sexe", "adresse", "poste",
                    "service", "direction", "categorie", "corps",
                    "type_emploi", "classe", "localite", "niveau_diplome",
                    "domaine_diplome", "etablissement_diplome", "mention_diplome",
                    "numero_diplome" -> FieldType.TEXT;
            case "age", "echelon" -> FieldType.NUMBER;
            case "date_naissance", "date_embauche", "date_obtention_diplome" -> FieldType.DATE;
            default -> throw new IllegalArgumentException(
                    "Champ de recherche non autorisé : " + field
            );
        };
    }

    private Object normaliserValeur(String field, Object value) {
        if (value instanceof String text) {
            if (field.equals("matricule")) {
                return text.trim().toUpperCase(Locale.ROOT);
            }
            if (field.equals("categorie")) {
                return text.trim().toUpperCase(Locale.ROOT);
            }
            if (field.equals("sexe")) {
            String normalized = text.trim().toUpperCase(Locale.ROOT);
            if (Set.of("FEMME", "FEMMES", "FEMININ", "FÉMININ", "F").contains(normalized)) {
                return "F";
            }
            if (Set.of("HOMME", "HOMMES", "MASCULIN", "M").contains(normalized)) {
                return "M";
            }
                return normalized;
            }
            return Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private void validateYearCount(Object value) {
        int years = parseInteger(value, "value");
        if (years < 1 || years > 100) {
            throw new IllegalArgumentException(
                    "La période relative doit être comprise entre 1 et 100 ans."
            );
        }
    }

    private void parseDate(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Les dates doivent être au format AAAA-MM-JJ.");
        }
        try {
            LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Les dates doivent être au format AAAA-MM-JJ."
            );
        }
    }

    private int parseInteger(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " doit être un entier.");
        }
        try {
            return new java.math.BigDecimal(number.toString())
                    .intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(field + " doit être un entier.");
        }
    }

    private String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire.");
        }
        return text.trim();
    }

    private String optionalString(Object value, String field) {
        if (value == null) {
            return null;
        }
        return requiredString(value, field);
    }

    private void rejectUnknownKeys(
            Map<?, ?> values,
            Set<String> allowed,
            String description
    ) {
        for (Object key : values.keySet()) {
            if (!(key instanceof String text) || !allowed.contains(text)) {
                throw new IllegalArgumentException(
                        "Champ non autorisé dans le " + description + " : " + key
                );
            }
        }
    }

    public static boolean estChampDate(String field) {
        return field.equals("date_naissance") || field.equals("date_embauche")
                || field.equals("date_obtention_diplome");
    }

    private enum FieldType {
        TEXT,
        NUMBER,
        DATE
    }

    public enum Operateur {
        EQ,
        NE,
        CONTAINS,
        FUZZY,
        GT,
        GTE,
        LT,
        LTE,
        BETWEEN,
        BEFORE,
        AFTER,
        WITHIN_LAST_YEARS,
        OLDER_THAN_YEARS
    }

    public record Filtre(
            String champ,
            Operateur operateur,
            Object valeur,
            Object valeurSuperieure
    ) {
    }

    public record ResultatRecherche(
            int total,
            int limite,
            List<AgentRechercheDto> agents,
            int offset,
            boolean hasMore
    ) {
    }
}
