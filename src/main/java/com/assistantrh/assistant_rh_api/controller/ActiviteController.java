package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Activite;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestAttribute;
import com.assistantrh.assistant_rh_api.service.ActiviteService;

import java.util.List;

@RestController
@RequestMapping("/api/activites")
public class ActiviteController {

    private final ActiviteService activiteService;

    public ActiviteController(ActiviteService activiteService) {
        this.activiteService = activiteService;
    }

    @GetMapping
    public List<Activite> getMesActivites(
            @RequestAttribute(
                    ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE
            ) Integer utilisateurId
    ) {
        return activiteService.listerParActeur(utilisateurId);
    }
}