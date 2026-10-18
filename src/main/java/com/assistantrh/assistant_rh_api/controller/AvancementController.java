package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.AvancementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
public class AvancementController {

    private final AvancementService avancementService;

    public AvancementController(AvancementService avancementService) {
        this.avancementService = avancementService;
    }

    @GetMapping("/api/carrieres/employes/{id:\\d+}/avancement")
    public ResponseEntity<Map<String, Object>> analyserAvancement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return avancementService.analyser(id, acteurId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/carrieres/employes/{id:\\d+}/avancement")
    public Map<String, Object> confirmerAvancement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestBody DateEffetRequest request
    ) {
        return avancementService.confirmer(id, acteurId, request.dateEffet());
    }

    public record DateEffetRequest(LocalDate dateEffet) {
    }
}
