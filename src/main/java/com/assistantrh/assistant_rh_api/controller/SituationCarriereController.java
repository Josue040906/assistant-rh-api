package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.SituationCarriereService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/carriere")
public class SituationCarriereController {

    private final SituationCarriereService situationCarriereService;

    public SituationCarriereController(
            SituationCarriereService situationCarriereService
    ) {
        this.situationCarriereService = situationCarriereService;
    }

    @GetMapping("/situation-actuelle")
    public ResponseEntity<Map<String, Object>> obtenirSituationActuelle(
            @RequestParam Integer employeId
    ) {
        return situationCarriereService
                .obtenirSituationActuelle(employeId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/historique")
    public List<Map<String, Object>> obtenirHistorique(
            @RequestParam Integer employeId
    ) {
        return situationCarriereService.obtenirHistorique(employeId);
    }
}