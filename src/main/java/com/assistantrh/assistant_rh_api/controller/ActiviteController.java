package com.assistantrh.assistant_rh_api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.assistantrh.assistant_rh_api.service.ActiviteService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activites")
public class ActiviteController {

    private final ActiviteService activiteService;

    public ActiviteController(ActiviteService activiteService) {
        this.activiteService = activiteService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllActivites() {
        return activiteService.listerToutes();
    }
}