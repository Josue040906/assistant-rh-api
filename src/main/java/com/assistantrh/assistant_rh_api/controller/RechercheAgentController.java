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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Locale;
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
            @RequestParam String query,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String poste,
            @RequestParam(required = false) String direction
    ) {
        List<Map<String, Object>> agents =
                rechercheApproximativeAgentService
                        .rechercherAgentsApproximatifs(
                                query,
                                service,
                                poste,
                                direction
                        );
        return new RechercheApproximativeAgentsResponse(
                query.trim(),
                agents.size(),
                agents
        );
    }

    @PostMapping("/agents/criteres")
    public ResponseEntity<?> rechercherAgentsParCriteres(
            @RequestBody RechercheStructureeRequest body
    ) {
        if (body == null || body.criteriaJson() == null) {
            throw new IllegalArgumentException(
                    "Le paramètre criteriaJson est obligatoire."
            );
        }
        if (body.criteriaJson().isBlank()) {
            throw new IllegalArgumentException(
                    "Le paramètre criteriaJson doit contenir les critères de recherche au format JSON."
            );
        }
        String resultMode = body.resultMode() == null
                ? "LIST"
                : body.resultMode().trim().toUpperCase(Locale.ROOT);
        return switch (resultMode) {
            case "LIST" -> ResponseEntity.ok(
                    rechercheStructureeAgentService.rechercher(body.criteriaJson())
            );
            case "COUNT" -> ResponseEntity.ok(
                    rechercheStructureeAgentService.compter(body.criteriaJson())
            );
            default -> throw new IllegalArgumentException(
                    "resultMode doit être LIST ou COUNT."
            );
        };
    }

    public record RechercheStructureeRequest(
            String criteriaJson,
            String resultMode
    ) {
    }

    public record RechercheApproximativeAgentsResponse(
            String query,
            int total,
            List<Map<String, Object>> agents
    ) {
    }
}