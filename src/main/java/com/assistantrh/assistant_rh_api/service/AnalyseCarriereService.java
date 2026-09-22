
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

        if (employeId == null) {
            return Optional.empty();
        }

        // ---------------------------------------------------------
        // 1. Récupérer la situation actuelle
        // ---------------------------------------------------------

        Optional<Map<String, Object>> situationOpt =
                situationCarriereService.obtenirDonneesAnalyseActuelle(
                        employeId
                );

        // ---------------------------------------------------------
        // 2. Aucune situation de carrière disponible
        // ---------------------------------------------------------

        if (situationOpt.isEmpty()) {

            Map<String, Object> resultat = new LinkedHashMap<>();

            resultat.put(
                    "employe_id",
                    employeId
            );

            resultat.put(
                    "situation_disponible",
                    false
            );

            resultat.put(
                    "situation_actuelle",
                    null
            );

            resultat.put(
                    "regle_applicable",
                    false
            );

            resultat.put(
                    "regle_appliquee",
                    null
            );

            resultat.put(
                    "anciennete",
                    null
            );

            resultat.put(
                    "echelon_suivant_existe",
                    false
            );

            resultat.put(
                    "echelon_suivant",
                    null
            );

            resultat.put(
                    "classe_suivante_existe",
                    false
            );

            resultat.put(
                    "classe_suivante",
                    null
            );

            resultat.put(
                    "conclusion",
                    "Aucune situation de carrière n'est actuellement enregistrée pour cet agent. L'analyse de carrière ne peut pas être effectuée."
            );

            return Optional.of(resultat);
        }

        Map<String, Object> situation = situationOpt.get();

        // ---------------------------------------------------------
        // 3. Situation disponible
        // ---------------------------------------------------------

        Map<String, Object> resultat = new LinkedHashMap<>();

        resultat.put(
                "employe_id",
                employeId
        );

        resultat.put(
                "situation_disponible",
                true
        );

        resultat.put(
                "situation_actuelle",
                situation
        );

        // ---------------------------------------------------------
        // 4. Récupérer la règle RH actuellement modélisée
        // ---------------------------------------------------------

        Optional<Map<String, Object>> regleOpt =
                regleRhService.obtenirRegleComplete(
                        "AVANCEMENT_ECHELON_CONCEPTEUR_2ANS"
                );

        if (regleOpt.isEmpty()) {

            resultat.put(
                    "regle_applicable",
                    false
            );

            resultat.put(
                    "regle_appliquee",
                    null
            );

            resultat.put(
                    "anciennete",
                    null
            );

            resultat.put(
                    "echelon_suivant_existe",
                    false
            );

            resultat.put(
                    "echelon_suivant",
                    null
            );

            resultat.put(
                    "classe_suivante_existe",
                    false
            );

            resultat.put(
                    "classe_suivante",
                    null
            );

            resultat.put(
                    "conclusion",
                    "Aucune règle RH active correspondant à l'analyse actuellement modélisée n'est disponible."
            );

            return Optional.of(resultat);
        }

        Map<String, Object> regle = regleOpt.get();

        // ---------------------------------------------------------
        // 5. Vérifier que la règle est applicable à l'agent
        // ---------------------------------------------------------

        boolean regleApplicable =
                regleRhService.estApplicable(
                        regle,
                        situation
                );

        resultat.put(
                "regle_applicable",
                regleApplicable
        );

        // ---------------------------------------------------------
        // 6. Si la règle ne s'applique pas
        // ---------------------------------------------------------

        if (!regleApplicable) {

            resultat.put(
                    "regle_appliquee",
                    null
            );

            resultat.put(
                    "anciennete",
                    null
            );

            resultat.put(
                    "echelon_suivant_existe",
                    false
            );

            resultat.put(
                    "echelon_suivant",
                    null
            );

            resultat.put(
                    "classe_suivante_existe",
                    false
            );

            resultat.put(
                    "classe_suivante",
                    null
            );

            resultat.put(
                    "conclusion",
                    "La règle d'avancement actuellement modélisée ne s'applique pas à la situation de carrière de cet agent."
            );

            return Optional.of(resultat);
        }

        // ---------------------------------------------------------
        // 7. La règle est applicable
        // ---------------------------------------------------------

        resultat.put(
                "regle_appliquee",
                regle
        );

        // ---------------------------------------------------------
        // 8. Calculer l'ancienneté
        // ---------------------------------------------------------

        LocalDate dateDebut = convertirEnLocalDate(
                situation.get("date_debut")
        );

        LocalDate aujourdHui = LocalDate.now();

        long ancienneteMois =
                ChronoUnit.MONTHS.between(
                        dateDebut,
                        aujourdHui
                );

        // ---------------------------------------------------------
        // 9. Lire la condition d'ancienneté
        // ---------------------------------------------------------

        Object conditionsObj =
                regle.get("conditions");

        List<Map<String, Object>> conditions = null;

        if (conditionsObj instanceof List<?>) {

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> liste =
                    (List<Map<String, Object>>) conditionsObj;

            conditions = liste;
        }

        Map<String, Object> conditionAnciennete =
                trouverConditionAnciennete(conditions);

        boolean conditionAncienneteSatisfaite = false;

        Long ancienneteRequise = null;
        String operateur = null;

        if (conditionAnciennete != null) {

            ancienneteRequise =
                    convertirEnLong(
                            conditionAnciennete.get("valeur")
                    );

            Object operateurObj =
                    conditionAnciennete.get("operateur");

            if (operateurObj != null) {
                operateur = operateurObj.toString();
            }

            if (ancienneteRequise != null &&
                    operateur != null) {

                conditionAncienneteSatisfaite =
                        comparer(
                                ancienneteMois,
                                operateur,
                                ancienneteRequise
                        );
            }
        }

        // ---------------------------------------------------------
        // 10. Vérifier l'échelon suivant
        // ---------------------------------------------------------

        Integer classeId =
                convertirEnInteger(
                        situation.get("classe_id")
                );

        Integer ordreEchelon =
                convertirEnInteger(
                        situation.get("echelon_ordre")
                );

        Optional<Map<String, Object>> echelonSuivantOpt =
                situationCarriereService.obtenirEchelonSuivant(
                        classeId,
                        ordreEchelon
                );

        // ---------------------------------------------------------
        // 11. Vérifier la classe suivante
        // ---------------------------------------------------------

        Integer gradeCarriereId =
                convertirEnInteger(
                        situation.get("grade_carriere_id")
                );

        Integer ordreClasse =
                convertirEnInteger(
                        situation.get("classe_ordre")
                );

        Optional<Map<String, Object>> classeSuivanteOpt =
                situationCarriereService.obtenirClasseSuivante(
                        gradeCarriereId,
                        ordreClasse
                );

        // ---------------------------------------------------------
        // 12. Ajouter les informations d'ancienneté
        // ---------------------------------------------------------

        Map<String, Object> anciennete =
                new LinkedHashMap<>();

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

        // ---------------------------------------------------------
        // 13. Ajouter l'échelon suivant
        // ---------------------------------------------------------

        resultat.put(
                "echelon_suivant_existe",
                echelonSuivantOpt.isPresent()
        );

        resultat.put(
                "echelon_suivant",
                echelonSuivantOpt.orElse(null)
        );

        // ---------------------------------------------------------
        // 14. Ajouter la classe suivante
        // ---------------------------------------------------------

        resultat.put(
                "classe_suivante_existe",
                classeSuivanteOpt.isPresent()
        );

        resultat.put(
                "classe_suivante",
                classeSuivanteOpt.orElse(null)
        );

        // ---------------------------------------------------------
        // 15. Déterminer la conclusion
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

        } else if (classeSuivanteOpt.isPresent()) {

            conclusion =
                    "La condition d'ancienneté est satisfaite, aucun échelon suivant n'existe dans la classe actuelle et une classe suivante existe. Les règles de passage à cette classe doivent être examinées.";

        } else {

            conclusion =
                    "La condition d'ancienneté est satisfaite, mais aucun échelon ni aucune classe suivante n'existe dans la carrière actuelle.";
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

        return Integer.valueOf(
                valeur.toString()
        );
    }

    private Long convertirEnLong(Object valeur) {

        if (valeur == null) {
            return null;
        }

        if (valeur instanceof Number) {
            return ((Number) valeur).longValue();
        }

        return Long.valueOf(
                valeur.toString()
        );
    }
}

