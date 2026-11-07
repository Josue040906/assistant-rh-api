package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Activite;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.assistantrh.assistant_rh_api.service.ActiviteService;

import java.util.List;

@RestController
@RequestMapping("/api/activites")
public class ActiviteController {

    private final ActiviteService activiteService;

    public ActiviteController(ActiviteService activiteService) {
        this.activiteService = activiteService;
    }

    /**
     * Activités de l'utilisateur connecté, de la plus récente à la plus
     * ancienne. Sans limit, la liste est complète (comportement historique) ;
     * avec limit (1 à 100) et offset, seule la page demandée est renvoyée.
     */
    @GetMapping
    public List<Activite> getMesActivites(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return activiteService.listerMesActivites(limit, offset);
    }
}
