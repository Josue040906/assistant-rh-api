package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.ReclassementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
            Integer acteurId,
            @RequestBody(required = false) DateEffetRequest request
    ) {
        return reclassementService.confirmer(
                id, acteurId, request == null ? null : request.dateEffet()
        );
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
        return reclassementService.obtenirRegles(acteurId);
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

    @PutMapping("/api/referentiels/reclassements/{niveauId:\\d+}")
    public ResponseEntity<Map<String, Object>> configurerRegle(
            @PathVariable Integer niveauId,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestBody RegleReclassementRequest request
    ) {
        reclassementService.configurerRegle(
                acteurId,
                niveauId,
                request.ordreAcademique(),
                request.classeId(),
                request.echelonId(),
                request.referenceJuridique(),
                request.actif()
        );
        return ResponseEntity.ok(Map.of("message", "Règle de reclassement enregistrée."));
    }

    public record DateEffetRequest(LocalDate dateEffet) {
    }

    public record OrdreNiveauRequest(Integer ordreAcademique) {
    }

    public record RegleReclassementRequest(
            Integer ordreAcademique,
            Integer classeId,
            Integer echelonId,
            String referenceJuridique,
            Boolean actif
    ) {
    }
}
