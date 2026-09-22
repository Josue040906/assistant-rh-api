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

        Integer regleId = ((Number) resultat.get("id")).intValue();

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
}