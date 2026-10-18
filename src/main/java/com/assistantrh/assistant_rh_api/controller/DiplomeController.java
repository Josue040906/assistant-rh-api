package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.DiplomeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class DiplomeController {

    private final DiplomeService diplomeService;

    public DiplomeController(DiplomeService diplomeService) {
        this.diplomeService = diplomeService;
    }

    @GetMapping("/api/referentiels/diplomes/options")
    public Map<String, Object> obtenirOptionsFormulaire(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestParam(required = false) Integer niveauId,
            @RequestParam(required = false) Integer domaineId
    ) {
        return diplomeService.obtenirOptionsFormulaire(
                acteurId, niveauId, domaineId
        );
    }

    @GetMapping("/api/employes/{id:\\d+}/diplomes")
    public List<Map<String, Object>> obtenirDiplomes(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return diplomeService.obtenirDiplomes(id, acteurId);
    }

    @PostMapping("/api/employes/{id:\\d+}/diplomes")
    public ResponseEntity<Map<String, Object>> ajouterDiplome(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestBody AjouterDiplomeRequest request
    ) {
        diplomeService.ajouterDiplome(
                id,
                acteurId,
                request.niveauId(),
                request.domaineId(),
                request.etablissementId(),
                request.dateObtention(),
                request.mentionId(),
                request.observation()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "employeId", id,
                "message", "Diplôme ajouté à l'historique de l'agent."
        ));
    }

    public record AjouterDiplomeRequest(
            Integer niveauId,
            Integer domaineId,
            Integer etablissementId,
            LocalDate dateObtention,
            Integer mentionId,
            String observation
    ) {
    }

    public record DemandeEtablissementRequest(
            String nom,
            String ville
    ) {
    }

    @PostMapping("/api/referentiels/etablissements/demandes")
    public ResponseEntity<Map<String, String>> demanderAjoutEtablissement(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestBody DemandeEtablissementRequest request
    ) {
        diplomeService.demanderAjoutEtablissement(
                acteurId, request.nom(), request.ville()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "La demande d'ajout de l'établissement a été transmise."
        ));
    }
}
