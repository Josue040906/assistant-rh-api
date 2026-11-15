package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.AvancementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
public class AvancementController {

    private final AvancementService avancementService;
    private final com.assistantrh.assistant_rh_api.service.DemandesOuvertes demandesOuvertes;

    public AvancementController(AvancementService avancementService, com.assistantrh.assistant_rh_api.service.DemandesOuvertes demandesOuvertes) {
        this.avancementService = avancementService;
        this.demandesOuvertes = demandesOuvertes;
    }

    @GetMapping("/api/carrieres/employes/{id:\\d+}/avancement")
    public ResponseEntity<Map<String, Object>> analyserAvancement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return avancementService.analyser(id, acteurId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/carrieres/employes/{id:\\d+}/avancement")
    public Map<String, Object> confirmerAvancement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestBody DateEffetRequest request
    ) {
        demandesOuvertes.verifierAucune(id, "AVANCEMENT");
        return avancementService.confirmer(id, acteurId, request.dateEffet(),
                com.assistantrh.assistant_rh_api.service.DecisionSignee.texte(request.referenceActe(), request.signataire()));
    }

    /** La date d'effet et la décision signée (numéro facultatif, signataire obligatoire). */
    public record DateEffetRequest(LocalDate dateEffet, String referenceActe, String signataire) {
    }
}
