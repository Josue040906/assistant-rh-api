package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.dto.ProfilAgentPersonnelDto;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.ProfilAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonProfilAgentController {
    private final ProfilAgentService profilAgentService;

    public MonProfilAgentController(ProfilAgentService profilAgentService) {
        this.profilAgentService = profilAgentService;
    }

    @GetMapping("/api/profil/agent/mon-profil")
    public ResponseEntity<ProfilAgentPersonnelDto> obtenirMonProfilAgent(
            @RequestAttribute(
                    ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE
            ) Integer utilisateurId
    ) {
        return ResponseEntity.ok(
                profilAgentService.obtenirMonProfilAgent(utilisateurId)
        );
    }
}
