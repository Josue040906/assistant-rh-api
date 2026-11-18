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
