package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.CvAgentService;
import com.assistantrh.assistant_rh_api.service.ProfilComplementaireService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Situation familiale, langues, compétences et formations continues d'un agent.
 * Corps du PUT : {"situationMatrimoniale", "nombreEnfants", "langues": [{"langue",
 * "niveau"}], "competences": ["…"], "formations": [{"intitule", "organisme",
 * "annee", "dureeJours"}]} — il remplace tout le profil complémentaire.
 */
@RestController
public class ProfilComplementaireController {

    private final ProfilComplementaireService service;
    private final CvAgentService cvAgentService;

    public ProfilComplementaireController(ProfilComplementaireService service, CvAgentService cvAgentService) {
        this.service = service;
        this.cvAgentService = cvAgentService;
    }

    /** Le CV de l'agent en PDF (Service du Personnel, ou l'agent lui-même). */
    @GetMapping("/api/employes/{id:\\d+}/cv")
    public ResponseEntity<byte[]> cv(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        Map.Entry<String, byte[]> cv = cvAgentService.generer(id, acteurId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(cv.getKey()).build().toString())
                .body(cv.getValue());
    }

    @GetMapping("/api/employes/{id:\\d+}/profil-complementaire")
    public Map<String, Object> lirePourAgent(@PathVariable Integer id) {
        return service.lire(id);
    }

    @PutMapping("/api/employes/{id:\\d+}/profil-complementaire")
    public Map<String, Object> enregistrerPourAgent(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId,
            @RequestBody Map<String, Object> corps
    ) {
        return service.enregistrerPourAgent(id, acteurId, corps);
    }

    @GetMapping("/api/profil/complementaire")
    public Map<String, Object> lirePourSoi(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer utilisateurId
    ) {
        return service.lire(service.employeDe(utilisateurId));
    }

    @PutMapping("/api/profil/complementaire")
    public Map<String, Object> enregistrerPourSoi(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer utilisateurId,
            @RequestBody Map<String, Object> corps
    ) {
        return service.enregistrerPourSoi(utilisateurId, corps);
    }

    /** Les compétences déjà saisies, pour les suggérer. */
    @GetMapping("/api/referentiels/competences")
    public List<String> competences() {
        return service.competencesConnues();
    }
}
