package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Ville;
import com.assistantrh.assistant_rh_api.service.VilleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/villes")
public class VilleController {

    private final VilleService villeService;

    public VilleController(VilleService villeService) {
        this.villeService = villeService;
    }

    @GetMapping
    public List<Ville> obtenirVilles() {
        return villeService.obtenirVilles();
    }
}
