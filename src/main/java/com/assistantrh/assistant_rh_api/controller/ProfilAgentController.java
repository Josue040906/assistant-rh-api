package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.ProfilAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profil")
public class ProfilAgentController {

    private final ProfilAgentService profilAgentService;

    public ProfilAgentController(
            ProfilAgentService profilAgentService
    ) {
        this.profilAgentService = profilAgentService;
    }

    @GetMapping("/agent")
    public ResponseEntity<Map<String, Object>> obtenirProfilAgent(
            @RequestParam String query
    ) {
        return profilAgentService.obtenirProfilAgent(query)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}