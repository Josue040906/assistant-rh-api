package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RechercheAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheApproximativeAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheStructureeAgentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recherche")
public class RechercheAgentController {

    private final RechercheAgentService rechercheAgentService;
    private final RechercheApproximativeAgentService rechercheApproximativeAgentService;
    private final RechercheStructureeAgentService rechercheStructureeAgentService;

    public RechercheAgentController(
            RechercheAgentService rechercheAgentService,
            RechercheApproximativeAgentService rechercheApproximativeAgentService,
            RechercheStructureeAgentService rechercheStructureeAgentService
    ) {
        this.rechercheAgentService = rechercheAgentService;
        this.rechercheApproximativeAgentService =
                rechercheApproximativeAgentService;
        this.rechercheStructureeAgentService = rechercheStructureeAgentService;
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> rechercherAgents(
            @RequestParam String query
    ) {
        return rechercheAgentService.rechercherAgents(query);
    }

    @GetMapping("/agents/approximative")
    public RechercheApproximativeAgentsResponse rechercherAgentsApproximativement(
            @RequestParam String query
    ) {
        List<Map<String, Object>> agents =
                rechercheApproximativeAgentService
                        .rechercherAgentsApproximatifs(query);
        return new RechercheApproximativeAgentsResponse(
                query.trim(),
                agents.size(),
                agents
        );
    }

    @PostMapping("/agents/criteres")
    public RechercheStructureeAgentService.ResultatRecherche rechercherAgentsParCriteres(
            @RequestBody Map<String, Object> body
    ) {
        if (body == null) {
            throw new IllegalArgumentException(
                    "Le corps JSON de la recherche est obligatoire."
            );
        }
        Object criteriaJson = body.get("criteriaJson");
        if (!(criteriaJson instanceof String json) || json.isBlank()) {
            throw new IllegalArgumentException(
                    "Le paramètre criteriaJson doit contenir les critères de recherche au format JSON."
            );
        }
        return rechercheStructureeAgentService.rechercher(json);
    }

    public record RechercheApproximativeAgentsResponse(
            String query,
            int total,
            List<Map<String, Object>> agents
    ) {
    }
}