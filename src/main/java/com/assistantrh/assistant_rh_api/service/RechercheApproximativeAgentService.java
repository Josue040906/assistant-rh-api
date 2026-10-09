package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.RechercheApproximativeAgentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RechercheApproximativeAgentService {

    private final RechercheApproximativeAgentRepository repository;

    public RechercheApproximativeAgentService(
            RechercheApproximativeAgentRepository repository
    ) {
        this.repository = repository;
    }

    public List<Map<String, Object>> rechercherAgentsApproximatifs(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return repository.rechercher(query.trim());
    }
}
