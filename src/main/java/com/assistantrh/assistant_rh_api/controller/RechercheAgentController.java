package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RechercheAgentAvanceeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** La seule recherche d'agents : le moteur avancé, piloté par un AST JSON. */
@RestController
@RequestMapping("/api/recherche")
public class RechercheAgentController {

    private final RechercheAgentAvanceeService rechercheAgentAvanceeService;

    public RechercheAgentController(RechercheAgentAvanceeService rechercheAgentAvanceeService) {
        this.rechercheAgentAvanceeService = rechercheAgentAvanceeService;
    }

    @PostMapping("/agents/avancee")
    public RechercheAgentAvanceeService.Resultat rechercherAgentsAvancee(
            @RequestBody RechercheAvanceeRequest body
    ) {
        if (body == null || body.criteriaJson() == null || body.criteriaJson().isBlank()) {
            throw new IllegalArgumentException("Le paramètre criteriaJson est obligatoire.");
        }
        return rechercheAgentAvanceeService.rechercher(body.criteriaJson(), body.resultMode());
    }

    public record RechercheAvanceeRequest(String criteriaJson, String resultMode) {
    }
}
