package com.assistantrh.assistant_rh_api.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalyseCarriereService {

    private final SituationCarriereService situationCarriereService;
    private final RegleRhService regleRhService;

    public AnalyseCarriereService(
            SituationCarriereService situationCarriereService,
            RegleRhService regleRhService
    ) {
        this.situationCarriereService = situationCarriereService;
        this.regleRhService = regleRhService;
    }

    public Optional<Map<String, Object>> analyser(Integer employeId) {

        // ---------------------------------------------------------
        // 1. Récupérer la situation actuelle
        // ---------------------------------------------------------

        Optional<Map<String, Object>> situationOpt =
                situationCarriereService.obtenirDonneesAnalyseActuelle(employeId);

        if (situationOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> situation = situationOpt.get();

        // ---------------------------------------------------------
        // 2. Récupérer la règle RH actuellement modélisée
        // ---------------------------------------------------------

        Optional<Map<String, Object>> regleOpt =
                regleRhService.obtenirRegleComplete(
                        "AVANCEMENT_ECHELON_CONCEPTEUR_2ANS"
                );

        if (regleOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> regle = regleOpt.get();

        // ---------------------------------------------------------
        // 3. Calculer l'ancienneté dans la situation actuelle
        // ---------------------------------------------------------

        LocalDate dateDebut = convertirEnLocalDate(
                situation.get("date_debut")
        );

        LocalDate aujourdHui = LocalDate.now();

        long ancienneteMois =
                ChronoUnit.MONTHS.between(dateDebut, aujourdHui);

        // ---------------------------------------------------------
        // 4. Lire la condition d'ancienneté depuis la règle
        // ---------------------------------------------------------

        List<Map<String, Object>> conditions =
                (List<Map<String, Object>>) regle.get("conditions");

        Map<String, Object> conditionAnciennete =
                trouverConditionAnciennete(conditions);

        boolean conditionAncienneteSatisfaite = false;

        Long ancienneteRequise = null;
        String operateur = null;

        if (conditionAnciennete != null) {

            ancienneteRequise =
                    convertirEnLong(conditionAnciennete.get("valeur"));

            operateur =
                    String.valueOf(
                            conditionAnciennete.get("operateur")
                    );

            conditionAncienneteSatisfaite =
                    comparer(
                            ancienneteMois,
                            operateur,
                            ancienneteRequise
                    );
        }

        // ---------------------------------------------------------
        // 5. Vérifier l'échelon suivant
        // ---------------------------------------------------------

        Integer classeId =
                convertirEnInteger(situation.get("classe_id"));

        Integer ordreEchelon =
                convertirEnInteger(situation.get("echelon_ordre"));

        Optional<Map<String, Object>> echelonSuivantOpt =
                situationCarriereService.obtenirEchelonSuivant(
                        classeId,
                        ordreEchelon
                );

        // ---------------------------------------------------------
        // 6. Construire le résultat
        // ---------------------------------------------------------

        Map<String, Object> resultat = new LinkedHashMap<>();

        resultat.put("employe_id", employeId);

        resultat.put(
                "situation_actuelle",
                situation
        );

        resultat.put(
                "regle_appliquee",
                regle
        );

        Map<String, Object> anciennete = new LinkedHashMap<>();

        anciennete.put(
                "date_debut",
                dateDebut
        );

        anciennete.put(
                "date_calcul",
                aujourdHui
        );

        anciennete.put(
                "nombre_mois",
                ancienneteMois
        );

        anciennete.put(
                "condition_requise_mois",
                ancienneteRequise
        );

        anciennete.put(
                "operateur",
                operateur
        );

        anciennete.put(
                "condition_satisfaite",
                conditionAncienneteSatisfaite
        );

        resultat.put(
                "anciennete",
                anciennete
        );

        resultat.put(
                "echelon_suivant_existe",
                echelonSuivantOpt.isPresent()
        );

        resultat.put(
                "echelon_suivant",
                echelonSuivantOpt.orElse(null)
        );

        // ---------------------------------------------------------
        // 7. Résultat général
        // ---------------------------------------------------------

        boolean ancienneteSuffisante =
                conditionAncienneteSatisfaite;

        boolean echelonSuivantExiste =
                echelonSuivantOpt.isPresent();

        String conclusion;

        if (!ancienneteSuffisante) {

            conclusion =
                    "La condition d'ancienneté n'est pas encore satisfaite.";

        } else if (echelonSuivantExiste) {

            conclusion =
                    "La condition d'ancienneté est satisfaite et un échelon suivant existe dans la classe actuelle.";

        } else {

            conclusion =
                    "La condition d'ancienneté est satisfaite, mais aucun échelon suivant n'existe dans la classe actuelle. Les règles de passage à la classe suivante doivent être examinées.";
        }

        resultat.put(
                "conclusion",
                conclusion
        );

        return Optional.of(resultat);
    }

    private Map<String, Object> trouverConditionAnciennete(
            List<Map<String, Object>> conditions
    ) {

        if (conditions == null) {
            return null;
        }

        for (Map<String, Object> condition : conditions) {

            Object type =
                    condition.get("type_condition");

            if (type != null &&
                    "ANCIENNETE".equalsIgnoreCase(
                            type.toString()
                    )) {

                return condition;
            }
        }

        return null;
    }

    private boolean comparer(
            long valeurActuelle,
            String operateur,
            long valeurRequise
    ) {

        return switch (operateur) {

            case ">=" ->
                    valeurActuelle >= valeurRequise;

            case ">" ->
                    valeurActuelle > valeurRequise;

            case "=" ->
                    valeurActuelle == valeurRequise;

            case "<=" ->
                    valeurActuelle <= valeurRequise;

            case "<" ->
                    valeurActuelle < valeurRequise;

            default ->
                    false;
        };
    }

    private LocalDate convertirEnLocalDate(Object valeur) {

        if (valeur instanceof LocalDate) {
            return (LocalDate) valeur;
        }

        if (valeur instanceof java.sql.Date) {
            return ((java.sql.Date) valeur).toLocalDate();
        }

        if (valeur instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) valeur)
                    .toLocalDateTime()
                    .toLocalDate();
        }

        if (valeur instanceof java.util.Date) {
            return new java.sql.Date(
                    ((java.util.Date) valeur).getTime()
            ).toLocalDate();
        }

        if (valeur instanceof String) {
            return LocalDate.parse((String) valeur);
        }

        throw new IllegalArgumentException(
                "Impossible de convertir la date : " + valeur
        );
    }

    private Integer convertirEnInteger(Object valeur) {

        if (valeur == null) {
            return null;
        }

        if (valeur instanceof Number) {
            return ((Number) valeur).intValue();
        }

        return Integer.valueOf(valeur.toString());
    }

    private Long convertirEnLong(Object valeur) {

        if (valeur == null) {
            return null;
        }

        if (valeur instanceof Number) {
            return ((Number) valeur).longValue();
        }

        return Long.valueOf(valeur.toString());
    }
}