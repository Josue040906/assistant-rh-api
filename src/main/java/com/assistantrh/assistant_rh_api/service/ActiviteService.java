package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.ActiviteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ActiviteService {

    private final ActiviteRepository activiteRepository;

    public ActiviteService(ActiviteRepository activiteRepository) {
        this.activiteRepository = activiteRepository;
    }

    public void enregistrer(
            Integer acteurId,
            Integer employeId,
            String typeAction,
            String description
    ) {

        if (acteurId != null && acteurId <= 0) {
            return;
        }

        if (typeAction == null || typeAction.isBlank()) {
            return;
        }

        if (description == null || description.isBlank()) {
            return;
        }

        activiteRepository.enregistrer(
                acteurId,
                employeId,
                typeAction.trim(),
                description.trim()
        );
    }

    public List<Map<String, Object>> listerToutes() {
        return activiteRepository.findAll();
    }
}