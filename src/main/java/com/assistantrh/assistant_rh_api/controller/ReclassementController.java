package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.ReclassementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ReclassementController {

    private final ReclassementService reclassementService;

    public ReclassementController(ReclassementService reclassementService) {
        this.reclassementService = reclassementService;
    }

    @GetMapping("/api/carrieres/employes/{id:\\d+}/reclassement")
    public Map<String, Object> proposerReclassement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.proposer(id, acteurId);
    }

    @PostMapping({
            "/api/employes/{id:\\d+}/reclassement",
            "/api/carrieres/employes/{id:\\d+}/reclassement"
    })
    public Map<String, Object> confirmerReclassement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.confirmer(id, acteurId, null);
    }

    @GetMapping("/api/carrieres/employes/{id:\\d+}/historique-reclassements")
    public List<Map<String, Object>> obtenirHistorique(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.obtenirHistorique(id, acteurId);
    }

    @GetMapping("/api/referentiels/reclassements")
    public List<Map<String, Object>> obtenirRegles(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.obtenirCorrespondancesCategorie(acteurId);
    }

    @GetMapping("/api/referentiels/reclassements/options")
    public List<Map<String, Object>> obtenirCiblesCarriere(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.obtenirCiblesCarriere(acteurId);
    }

    @PutMapping("/api/referentiels/niveaux-diplome/{id:\\d+}/ordre")
    public ResponseEntity<Map<String, Object>> configurerOrdreAcademique(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestBody OrdreNiveauRequest request
    ) {
        reclassementService.configurerOrdreNiveau(
                acteurId, id, request.ordreAcademique()
        );
        return ResponseEntity.ok(Map.of("message", "Ordre académique enregistré."));
    }

    @GetMapping({"/api/reclassements", "/api/referentiels/reclassements/historique"})
    public List<Map<String, Object>> obtenirHistoriqueGlobal(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.obtenirHistoriqueGlobal(acteurId);
    }

}
