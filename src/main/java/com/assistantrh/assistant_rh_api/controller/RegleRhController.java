package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RegleRhService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regles-rh")
public class RegleRhController {

    private final RegleRhService regleRhService;

    public RegleRhController(RegleRhService regleRhService) {
        this.regleRhService = regleRhService;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> obtenirRegle(
            @PathVariable String code
    ) {
        return regleRhService
                .obtenirRegleComplete(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/actives")
    public List<Map<String, Object>> obtenirReglesActives() {
        return regleRhService.obtenirReglesActives();
    }
}