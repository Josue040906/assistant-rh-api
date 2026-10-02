package com.assistantrh.assistant_rh_api.controller;


import com.assistantrh.assistant_rh_api.service.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
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
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .build()
                );
    }
    @PutMapping("/mot-de-passe")
    public ResponseEntity<Map<String, Object>> changerMotDePasse(
            @RequestBody ChangementMotDePasseRequest request
    ) {

        if (request == null || request.userId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "L'identifiant utilisateur est obligatoire."
                    ));
        }

        boolean modifie = utilisateurService.changerMotDePasse(
                request.userId(),
                request.ancienMotDePasse(),
                request.nouveauMotDePasse()
        );

        if (!modifie) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Ancien mot de passe incorrect."
                    ));
        }

        return ResponseEntity.ok(
                Map.of(
                        "message", "Mot de passe modifié avec succès."
                )
        );
    }

    @PostMapping("/inscription")
    public ResponseEntity<Map<String, Object>> inscrire(
            @RequestBody InscriptionRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "Les données d'inscription sont obligatoires."
                    ));
        }

        if (request.matricule() == null || request.matricule().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "Le matricule est obligatoire."
                    ));
        }

        if (request.email() == null || request.email().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "L'adresse e-mail est obligatoire."
                    ));
        }

        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "Le mot de passe est obligatoire."
                    ));
        }

        Optional<Map<String, Object>> resultat =
                utilisateurService.inscrire(
                        request.matricule(),
                        request.email(),
                        request.password()
                );

        if (resultat.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Impossible de créer le compte. Vérifiez le matricule, l'adresse e-mail ou si un compte existe déjà pour cet agent."
                    ));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resultat.get());
    }


    @GetMapping("/en-attente")
    public ResponseEntity<?> listerComptesEnAttente(
            @RequestParam Integer acteurId
    ) {

        if (acteurId == null || acteurId <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "L'identifiant de l'acteur est obligatoire."
                    ));
        }

        if (!utilisateurService.estChefActif(acteurId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Seul un SPERS_CHEF actif peut consulter les comptes en attente."
                    ));
        }

        return ResponseEntity.ok(
                utilisateurService.listerComptesEnAttente(acteurId)
        );
    }

    @PutMapping("/approuver")
    public ResponseEntity<Map<String, Object>> approuverCompte(
            @RequestBody ValidationCompteRequest request
    ) {

        if (request == null
                || request.acteurId() == null
                || request.utilisateurId() == null) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "L'acteur et l'utilisateur à valider sont obligatoires."
                    ));
        }

        boolean approuve = utilisateurService.approuverCompte(
                request.acteurId(),
                request.utilisateurId()
        );

        if (!approuve) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Le compte ne peut pas être approuvé."
                    ));
        }

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Compte approuvé avec succès.",
                        "statutCompte",
                        "ACTIF"
                )
        );
    }

    @PutMapping("/refuser")
    public ResponseEntity<Map<String, Object>> refuserCompte(
            @RequestBody ValidationCompteRequest request
    ) {

        if (request == null
                || request.acteurId() == null
                || request.utilisateurId() == null) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "L'acteur et l'utilisateur à refuser sont obligatoires."
                    ));
        }

        boolean refuse = utilisateurService.refuserCompte(
                request.acteurId(),
                request.utilisateurId()
        );

        if (!refuse) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Le compte ne peut pas être refusé."
                    ));
        }

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Compte refusé avec succès.",
                        "statutCompte",
                        "REFUSE"
                )
        );
    }

    public record ValidationCompteRequest(
            Integer acteurId,
            Integer utilisateurId
    ) {
    }
    public record InscriptionRequest(
            String matricule,
            String email,
            String password
    ) {

    }

    public record ChangementMotDePasseRequest(
            Integer userId,
            String ancienMotDePasse,
            String nouveauMotDePasse
    ) {
    }

    public record LoginRequest(
            String email,
            String password
    ) {
    }
}