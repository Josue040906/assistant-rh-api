package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.SituationCarriereService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SituationCarriereController {

    private final SituationCarriereService situationCarriereService;

    public SituationCarriereController(
            SituationCarriereService situationCarriereService
    ) {
        this.situationCarriereService = situationCarriereService;
    }

    @GetMapping("/api/carriere/situation-actuelle")
    public ResponseEntity<Map<String, Object>> obtenirSituationActuelle(
            @RequestParam Integer employeId
    ) {
        return situationCarriereService
                .obtenirSituationActuelle(employeId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/carriere/historique")
    public List<Map<String, Object>> obtenirHistorique(
            @RequestParam Integer employeId
    ) {
        return situationCarriereService.obtenirHistorique(employeId);
    }

    @GetMapping("/api/carriere/classe-suivante")
    public ResponseEntity<Map<String, Object>> obtenirClasseSuivante(
            @RequestParam Integer ordreActuel
    ) {
        return situationCarriereService
                .obtenirClasseSuivante(ordreActuel)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/carrieres/employes/{id}/analyse")
    public ResponseEntity<Map<String, Object>> analyserEvolutionCarriere(
            @PathVariable Integer id
    ) {
        return situationCarriereService
                .analyserEvolutionCarriere(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}