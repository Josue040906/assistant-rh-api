package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.EmployeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/profil")
public class ProfilController {

    private final EmployeService employeService;

    public ProfilController(EmployeService employeService) {
        this.employeService = employeService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfil(
            @RequestParam Integer userId
    ) {

        Optional<Map<String, Object>> profil =
                employeService.getProfilByUserId(userId);

        if (profil.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(profil.get());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> modifierProfil(
            @RequestBody ProfilUpdateRequest request
    ) {

        employeService.modifierProfil(
                request.userId(),
                request.adresse(),
                request.telephone()
        );

        return ResponseEntity.ok(
                Map.of(
                        "userId", request.userId(),
                        "message", "Profil modifié avec succès."
                )
        );
    }

    public record ProfilUpdateRequest(
            Integer userId,
            String adresse,
            String telephone
    ) {}
}