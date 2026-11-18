package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.ReleveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Le plan de relève (départs, postes à risque, services touchés), réservé au
 * Service du Personnel : qui part et qui succède sont des sujets sensibles.
 */
@RestController
public class ReleveController {

    private final ReleveService releveService;
    private final UtilisateurRepository utilisateurRepository;

    public ReleveController(ReleveService releveService, UtilisateurRepository utilisateurRepository) {
        this.releveService = releveService;
        this.utilisateurRepository = utilisateurRepository;
    }

    /** Désigne le successeur pressenti : {"successeurId", "statut", "note"}. */
    @org.springframework.web.bind.annotation.PutMapping("/api/releve/postes/{posteId:\\d+}")
    public Map<String, Object> designer(
            @org.springframework.web.bind.annotation.PathVariable Integer posteId,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId,
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> corps
    ) {
        verifierPersonnel(acteurId);
        Object successeur = corps.get("successeurId");
        releveService.designer(posteId,
                successeur == null ? null : Integer.valueOf(successeur.toString()),
                corps.get("statut") == null ? null : corps.get("statut").toString(),
                corps.get("note") == null ? null : corps.get("note").toString(), acteurId);
        return Map.of("message", "Relève enregistrée.", "posteId", posteId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/releve/postes/{posteId:\\d+}")
    public Map<String, Object> retirer(
            @org.springframework.web.bind.annotation.PathVariable Integer posteId,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        verifierPersonnel(acteurId);
        releveService.retirer(posteId);
        return Map.of("message", "Relève retirée.", "posteId", posteId);
    }

    private void verifierPersonnel(Integer acteurId) {
        if (acteurId == null || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException("Le plan de relève est réservé au Service du Personnel.");
        }
    }

    /** horizon : nombre d'années à venir (1 à 10, 2 par défaut). */
    @GetMapping("/api/releve")
    public Map<String, Object> tableau(
            @RequestParam(defaultValue = "2") int horizon,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        if (acteurId == null || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException("Le plan de relève est réservé au Service du Personnel.");
        }
        return releveService.tableau(horizon);
    }
}
