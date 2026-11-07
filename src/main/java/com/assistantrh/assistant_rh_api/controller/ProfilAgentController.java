package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.ProfilAgentService;
import org.springframework.http.HttpStatus;
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

    /**
     * Profil d'une identité exacte (matricule ou nom complet). 404 si aucune
     * correspondance ; 409 (ProfilAgentAmbiguException) si plusieurs homonymes.
     */
    @GetMapping("/agent")
    public ResponseEntity<Map<String, Object>> obtenirProfilAgent(
            @RequestParam String query
    ) {
        return profilAgentService.obtenirProfilAgent(query)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "AGENT_INTROUVABLE",
                        "message", "Aucun agent ne correspond exactement à « "
                                + query.trim() + " ». Indiquez son matricule ou son "
                                + "nom complet, ou recherchez-le d'abord."
                )));
    }
}
