package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import com.assistantrh.assistant_rh_api.service.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/utilisateurs")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    public UtilisateurController(
            UtilisateurService utilisateurService
    ) {
        this.utilisateurService = utilisateurService;
    }

    @GetMapping("/par-email")
    public ResponseEntity<Map<String, Object>> trouverParEmail(
            @RequestParam String email
    ) {

        return utilisateurService
                .trouverIdParEmail(email)
                .map(id -> {

                    Map<String, Object> utilisateur = new HashMap<>();

                    utilisateur.put("id", id);
                    utilisateur.put("email", email.trim());

                    return ResponseEntity.ok(utilisateur);
                })
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest request
    ) {

        return utilisateurService
                .authentifier(request.email(), request.password())
                .map(utilisateur -> {

                    Map<String, Object> response = new HashMap<>();

                    response.put("id", utilisateur.id());
                    response.put("email", utilisateur.email());

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .build()
                );
    }

    public record LoginRequest(
            String email,
            String password
    ) {
    }
}