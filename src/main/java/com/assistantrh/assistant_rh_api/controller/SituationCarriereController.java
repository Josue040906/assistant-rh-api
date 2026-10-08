package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.SituationCarriereService;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @PostMapping("/api/carrieres/employes/{id}/situations")
    public ResponseEntity<Map<String, Object>> ajouterSituationCarriere(
            @PathVariable Integer id,
            @RequestAttribute(
                    ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE
            ) Integer acteurId,
            @RequestBody SituationCarriereRequest request
    ) {
        situationCarriereService.ajouterSituationCarriere(
                id,
                acteurId,
                request.echelonId(),
                request.dateDebut()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "employeId", id,
                        "message", "Situation de carrière ajoutée avec succès."
                )
        );
    }

    public record SituationCarriereRequest(
            Integer echelonId,
            LocalDate dateDebut
    ) {
    }
}