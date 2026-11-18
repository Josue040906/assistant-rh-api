package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.ExigencePosteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Les exigences d'un poste : lues par tous, modifiées par le Service du Personnel. */
@RestController
public class ExigencePosteController {

    private final ExigencePosteService exigencePosteService;
    private final UtilisateurRepository utilisateurRepository;

    public ExigencePosteController(ExigencePosteService exigencePosteService, UtilisateurRepository utilisateurRepository) {
        this.exigencePosteService = exigencePosteService;
        this.utilisateurRepository = utilisateurRepository;
    }

    @GetMapping("/api/postes/{posteId:\\d+}/exigences")
    public ResponseEntity<?> lister(@PathVariable Long posteId) {
        try {
            return ResponseEntity.ok(exigencePosteService.lister(posteId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Remplace les exigences du poste : {"exigences": [{type, valeur, niveau, indispensable}]}. */
    @PutMapping("/api/postes/{posteId:\\d+}/exigences")
    public ResponseEntity<?> enregistrer(
            @PathVariable Long posteId,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId,
            @RequestBody Map<String, Object> corps
    ) {
        if (acteurId == null || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            return ResponseEntity.status(403).body(Map.of(
                    "message", "Les exigences des postes sont réservées au Service du Personnel."));
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exigences = corps.get("exigences") instanceof List<?> liste
                    ? (List<Map<String, Object>>) liste : List.of();
            return ResponseEntity.ok(exigencePosteService.enregistrer(posteId, exigences));
        } catch (IllegalArgumentException | ClassCastException e) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    e instanceof IllegalArgumentException ? e.getMessage() : "Les exigences envoyées sont mal formées."));
        }
    }
}
