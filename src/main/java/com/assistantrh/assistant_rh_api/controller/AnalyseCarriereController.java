package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.AnalyseCarriereService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/carriere")
public class AnalyseCarriereController {

    private final AnalyseCarriereService analyseCarriereService;

    public AnalyseCarriereController(
            AnalyseCarriereService analyseCarriereService
    ) {
        this.analyseCarriereService = analyseCarriereService;
    }

    @GetMapping("/analyse")
    public ResponseEntity<Map<String, Object>> analyser(
            @RequestParam Integer employeId
    ) {

        return analyseCarriereService
                .analyser(employeId)
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity.notFound().build()
                );
    }
}