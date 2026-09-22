package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.RegleRhRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RegleRhService {

    private final RegleRhRepository regleRhRepository;

    public RegleRhService(RegleRhRepository regleRhRepository) {
        this.regleRhRepository = regleRhRepository;
    }

    /**
     * Récupère une règle RH active ainsi que
     * sa population, ses conditions, ses effets
     * et ses références juridiques.
     */
    public Optional<Map<String, Object>> obtenirRegleComplete(
            String code
    ) {

        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        Optional<Map<String, Object>> regle =
                regleRhRepository.findRegleActiveByCode(code.trim());

        if (regle.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> resultat = regle.get();

        Integer regleId =
                ((Number) resultat.get("id")).intValue();

        resultat.put(
                "population",
                regleRhRepository.findPopulationByRegleId(regleId)
        );

        resultat.put(
                "conditions",
                regleRhRepository.findConditionsByRegleId(regleId)
        );

        resultat.put(
                "effets",
                regleRhRepository.findEffetsByRegleId(regleId)
        );

        resultat.put(
                "references_juridiques",
                regleRhRepository.findReferencesJuridiquesByRegleId(regleId)
        );

        return Optional.of(resultat);
    }

    /**
     * Vérifie si une règle est applicable à une situation
     * de carrière donnée.
     */
    public boolean estApplicable(
            Map<String, Object> regle,
            Map<String, Object> situation
    ) {

        if (regle == null || situation == null) {
            return false;
        }

        Object populationObj = regle.get("population");

        if (!(populationObj instanceof List<?> populations)
                || populations.isEmpty()) {
            return false;
        }

        for (Object populationObjItem : populations) {

            if (!(populationObjItem instanceof Map<?, ?> population)) {
                continue;
            }

            if (correspond(
                    population.get("statut_agent"),
                    situation.get("statut_agent_code")
            )
                    && correspond(
                    population.get("cadre"),
                    situation.get("cadre_code")
            )
                    && correspond(
                    population.get("echelle"),
                    situation.get("echelle_code")
            )
                    && correspond(
                    population.get("corps"),
                    situation.get("corps_code")
            )
                    && correspond(
                    population.get("grade"),
                    situation.get("grade_code")
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean correspond(
            Object valeurRegle,
            Object valeurSituation
    ) {

        if (valeurRegle == null) {
            return true;
        }

        if (valeurSituation == null) {
            return false;
        }

        return valeurRegle
                .toString()
                .equalsIgnoreCase(
                        valeurSituation.toString()
                );
    }
    public List<Map<String, Object>> obtenirReglesActives() {

        List<Map<String, Object>> regles =
                regleRhRepository.findReglesActives();

        for (Map<String, Object> regle : regles) {

            Integer regleId =
                    ((Number) regle.get("id")).intValue();

            regle.put(
                    "population",
                    regleRhRepository.findPopulationByRegleId(regleId)
            );

            regle.put(
                    "conditions",
                    regleRhRepository.findConditionsByRegleId(regleId)
            );

            regle.put(
                    "effets",
                    regleRhRepository.findEffetsByRegleId(regleId)
            );

            regle.put(
                    "references_juridiques",
                    regleRhRepository.findReferencesJuridiquesByRegleId(regleId)
            );
        }

        return regles;
    }
}