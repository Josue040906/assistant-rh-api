package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simulation indicative d'une carrière : le passé (historique réel), puis une
 * projection où l'agent avance à chaque durée minimale, dans l'ordre de son
 * parcours (échelon suivant, puis classe suivante), jusqu'à son droit à la
 * retraite. Une date de titularisation facultative fait passer un contractuel
 * ou un stagiaire au parcours des fonctionnaires, au grade que son indice
 * désigne. Tout vient des données (grades, durées, grille) : aucune règle ajoutée.
 */
@Service
public class SimulationCarriereService {
    static final String FONCTIONNAIRE = "FONCTIONNAIRE";
    private static final int HORIZON_SANS_RETRAITE_ANNEES = 15;

    private final SituationCarriereRepository situationRepository;
    private final DocumentRhRepository documentRhRepository;

    public SimulationCarriereService(
            SituationCarriereRepository situationRepository,
            DocumentRhRepository documentRhRepository
    ) {
        this.situationRepository = situationRepository;
        this.documentRhRepository = documentRhRepository;
    }

    public Map<String, Object> simuler(Integer employeId, LocalDate titularisation) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'agent est invalide.");
        }
        Map<String, Object> actuelle = situationRepository.findSituationActuelle(employeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'agent n'a pas de situation de carrière en cours : rien à simuler."));
        LocalDate aujourdhui = LocalDate.now();
        String categorie = (String) actuelle.get("categorie_code");

        // Les grades, dans l'ordre de progression de chaque parcours, et leur indice pour cette catégorie.
        List<Map<String, Object>> grades = situationRepository.findGradesParParcours();
        Map<String, Integer> indices = new HashMap<>();
        for (Map<String, Object> cellule : situationRepository.findGrilleIndiciaire()) {
            if (Objects.equals(cellule.get("categorie"), categorie)) {
                indices.put((String) cellule.get("grade_code"), entier(cellule.get("indice")));
            }
        }
        Map<Integer, Integer> position = new HashMap<>();
        for (int i = 0; i < grades.size(); i++) {
            position.put(entier(grades.get(i).get("grade_id")), i);
        }

        LocalDate retraite = documentRhRepository.dateRetraitePossible(employeId).orElse(null);
        LocalDate horizon = retraite != null ? retraite : aujourdhui.plusYears(HORIZON_SANS_RETRAITE_ANNEES);

        // 1. Le passé : chaque période réelle de l'historique.
        List<Map<String, Object>> etapes = new ArrayList<>();
        Map<String, Object> precedent = null;
        List<Map<String, Object>> historique = situationRepository.findHistorique(employeId);
        for (Map<String, Object> periode : historique) {
            Map<String, Object> grade = grades.get(position.get(entier(periode.get("echelon_id"))));
            boolean enCours = periode.get("date_fin") == null;
            etapes.add(etape(date(periode.get("date_debut")), grade, indices, enCours ? "ACTUEL" : "PASSE",
                    evenement(precedent, grade)));
            precedent = grade;
        }

        // 2. La projection, depuis la situation en cours.
        Map<String, Object> courant = grades.get(position.get(entier(actuelle.get("echelon_id"))));
        LocalDate depuis = date(actuelle.get("date_debut"));
        boolean titularisable = !FONCTIONNAIRE.equals(courant.get("parcours_code"));
        LocalDate dateTitularisation = null;
        if (titularisation != null && titularisable) {
            dateTitularisation = titularisation.isAfter(depuis) ? titularisation : depuis.plusDays(1);
            if (dateTitularisation.isBefore(aujourdhui)) {
                dateTitularisation = aujourdhui;
            }
        }
        LocalDate aTitulariser = dateTitularisation;
        while (true) {
            Object duree = courant.get("duree_min");
            LocalDate prochaine = duree == null ? null : depuis.plusYears(((Number) duree).longValue());
            if (aTitulariser != null && (prochaine == null || !aTitulariser.isAfter(prochaine))
                    && !aTitulariser.isAfter(horizon)) {
                Map<String, Object> cible = gradeDeTitularisation(grades, indices, indices.get((String) courant.get("code")));
                if (cible == null) {
                    break;
                }
                etapes.add(etape(aTitulariser, cible, indices, "PROJETE", "TITULARISATION"));
                courant = cible;
                depuis = aTitulariser;
                aTitulariser = null;
                continue;
            }
            int suivant = position.get(entier(courant.get("grade_id"))) + 1;
            if (prochaine == null || prochaine.isAfter(horizon) || suivant >= grades.size()
                    || !Objects.equals(grades.get(suivant).get("parcours_code"), courant.get("parcours_code"))) {
                break;
            }
            Map<String, Object> grade = grades.get(suivant);
            etapes.add(etape(prochaine, grade, indices, "PROJETE",
                    Objects.equals(grade.get("classe"), courant.get("classe")) ? "AVANCEMENT" : "CLASSE"));
            courant = grade;
            depuis = prochaine;
        }
        if (retraite != null) {
            etapes.add(etape(retraite.isBefore(aujourdhui) ? aujourdhui : retraite, courant, indices, "PROJETE", "RETRAITE"));
        }

        // Les couloirs : un par type d'emploi, avec ses grades et leur indice pour cette catégorie.
        Map<String, Map<String, Object>> couloirs = new LinkedHashMap<>();
        for (Map<String, Object> grade : grades) {
            Map<String, Object> couloir = couloirs.computeIfAbsent((String) grade.get("type_emploi"), type -> {
                Map<String, Object> nouveau = new LinkedHashMap<>();
                nouveau.put("typeEmploi", type);
                nouveau.put("parcours", grade.get("parcours"));
                nouveau.put("grades", new ArrayList<Map<String, Object>>());
                return nouveau;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> liste = (List<Map<String, Object>>) couloir.get("grades");
            Map<String, Object> repere = new LinkedHashMap<>();
            repere.put("code", grade.get("code"));
            repere.put("libelle", grade.get("libelle"));
            repere.put("indice", indices.get((String) grade.get("code")));
            liste.add(repere);
        }

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("employeId", employeId);
        resultat.put("categorie", categorie);
        resultat.put("aujourdhui", aujourdhui);
        resultat.put("retraite", retraite);
        resultat.put("horizon", horizon);
        resultat.put("titularisable", titularisable);
        resultat.put("dateTitularisation", dateTitularisation);
        resultat.put("couloirs", new ArrayList<>(couloirs.values()));
        resultat.put("etapes", etapes);
        resultat.put("indiceFinal", etapes.isEmpty() ? null : etapes.get(etapes.size() - 1).get("indice"));
        return resultat;
    }

    /** Le grade de fonctionnaire d'indice égal, sinon le premier supérieur (même règle que la titularisation). */
    private static Map<String, Object> gradeDeTitularisation(
            List<Map<String, Object>> grades, Map<String, Integer> indices, Integer indiceActuel
    ) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> grade : grades) {
            Integer indice = indices.get((String) grade.get("code"));
            if (FONCTIONNAIRE.equals(grade.get("parcours_code")) && indice != null) {
                Map<String, Object> option = new LinkedHashMap<>(grade);
                option.put("indice", indice);
                options.add(option);
            }
        }
        options.sort((a, b) -> Integer.compare(entier(a.get("indice")), entier(b.get("indice"))));
        return TitularisationService.gradePropose(options, indiceActuel).orElse(null);
    }

    private static String evenement(Map<String, Object> precedent, Map<String, Object> grade) {
        if (precedent == null) {
            return "EMBAUCHE";
        }
        if (!Objects.equals(precedent.get("type_emploi"), grade.get("type_emploi"))) {
            return "TITULARISATION";
        }
        return Objects.equals(precedent.get("classe"), grade.get("classe")) ? "AVANCEMENT" : "CLASSE";
    }

    private static Map<String, Object> etape(
            LocalDate date, Map<String, Object> grade, Map<String, Integer> indices, String nature, String evenement
    ) {
        Map<String, Object> etape = new LinkedHashMap<>();
        etape.put("date", date);
        etape.put("typeEmploi", grade.get("type_emploi"));
        etape.put("parcours", grade.get("parcours"));
        etape.put("gradeCode", grade.get("code"));
        etape.put("gradeLibelle", grade.get("libelle"));
        etape.put("indice", indices.get((String) grade.get("code")));
        etape.put("nature", nature);
        etape.put("evenement", evenement);
        return etape;
    }

    private static Integer entier(Object valeur) {
        return valeur instanceof Number nombre ? nombre.intValue() : valeur == null ? null : Integer.valueOf(valeur.toString());
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate jour) {
            return jour;
        }
        return valeur instanceof java.sql.Date sql ? sql.toLocalDate() : LocalDate.parse(String.valueOf(valeur));
    }
}
