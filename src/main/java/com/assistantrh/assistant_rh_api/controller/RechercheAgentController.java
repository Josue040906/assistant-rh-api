package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RechercheAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheApproximativeAgentService;
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

    public RechercheAgentController(
            RechercheAgentService rechercheAgentService,
            RechercheApproximativeAgentService rechercheApproximativeAgentService
    ) {
        this.rechercheAgentService = rechercheAgentService;
        this.rechercheApproximativeAgentService =
                rechercheApproximativeAgentService;
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

    public record RechercheApproximativeAgentsResponse(
            String query,
            int total,
            List<Map<String, Object>> agents
    ) {
    }
}