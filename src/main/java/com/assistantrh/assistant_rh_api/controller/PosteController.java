package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.PosteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/postes")
public class PosteController {

    private final PosteService posteService;

    public PosteController(PosteService posteService) {
        this.posteService = posteService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllPostes() {
        return posteService.getAllPostes();
    }

    @GetMapping("/recherche")
    public List<Map<String, Object>> rechercherPostes(
            @RequestParam String query
    ) {
        return posteService.rechercherPostes(query);
    }

    @GetMapping("/service")
    public List<Map<String, Object>> rechercherPostesParService(
            @RequestParam String query
    ) {
        return posteService.rechercherPostesParService(query);
    }
}