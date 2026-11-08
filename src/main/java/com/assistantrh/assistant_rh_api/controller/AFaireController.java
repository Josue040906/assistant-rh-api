package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.AFaireService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AFaireController {
    private final AFaireService aFaireService;

    public AFaireController(AFaireService aFaireService) {
        this.aFaireService = aFaireService;
    }

    /** Ce qui attend une action de l'utilisateur connecté, selon ses droits. */
    @GetMapping("/api/a-faire")
    public AFaireService.AFaire aFaire(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer utilisateurId
    ) {
        return aFaireService.pour(utilisateurId);
    }
}
