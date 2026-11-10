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
    private final com.assistantrh.assistant_rh_api.service.RetraiteService retraiteService;

    public SituationCarriereController(
            SituationCarriereService situationCarriereService,
            com.assistantrh.assistant_rh_api.service.RetraiteService retraiteService
    ) {
        this.situationCarriereService = situationCarriereService;
        this.retraiteService = retraiteService;
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

    /** Les parcours de chaque type d'emploi et leurs grades (lecture seule). */
    @GetMapping("/api/parcours")
    public List<Map<String, Object>> listerGrades() {
        return situationCarriereService.listerGrades();
    }

    /** La grille indiciaire fictive : catégorie × grade → indice. */
    @GetMapping("/api/parcours/grille")
    public List<Map<String, Object>> grilleIndiciaire() {
        return situationCarriereService.grilleIndiciaire();
    }

    /** Années de service exigées, date à laquelle l'agent les atteint, départ déjà pris. */
    @GetMapping("/api/carrieres/employes/{id:\\d+}/retraite")
    public Map<String, Object> analyserRetraite(@PathVariable Integer id) {
        return retraiteService.analyser(id);
    }

    /** Bouton « Prendre sa retraite » : même effet qu'une demande de retraite appliquée. */
    @PostMapping("/api/carrieres/employes/{id:\\d+}/retraite")
    public ResponseEntity<Map<String, Object>> prendreRetraite(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId,
            @RequestBody Map<String, String> corps
    ) {
        try {
            LocalDate date = corps.get("dateEffet") == null ? LocalDate.now() : LocalDate.parse(corps.get("dateEffet"));
            return ResponseEntity.ok(retraiteService.prendreRetraite(id, acteurId, date, null));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException erreur) {
            return ResponseEntity.badRequest().body(Map.of("message", erreur.getMessage()));
        }
    }

    @GetMapping("/api/carriere/historique")
    public List<Map<String, Object>> obtenirHistorique(
            @RequestParam Integer employeId
    ) {
        return situationCarriereService.obtenirHistorique(employeId);
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