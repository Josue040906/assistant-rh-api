package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RechercheAgentService;
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

    public RechercheAgentController(
            RechercheAgentService rechercheAgentService
    ) {
        this.rechercheAgentService = rechercheAgentService;
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> rechercherAgents(
            @RequestParam String query
    ) {
        return rechercheAgentService.rechercherAgents(query);
    }
}