package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.EmployeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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
                request.nom(),
                request.prenom(),
                request.sexe(),
                request.cin(),
                request.dateNaissance(),
                request.lieuNaissance(),
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

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploaderPhotoProfil(
            @RequestParam Integer userId,
            @RequestParam("file") MultipartFile file
    ) {
        String photoPath = employeService.enregistrerPhotoProfil(userId, file);
        return ResponseEntity.ok(
                Map.of(
                        "userId", userId,
                        "photo", photoPath,
                        "message", "Photo enregistrée avec succès."
                )
        );
    }

    public record ProfilUpdateRequest(
            Integer userId,
            String nom,
            String prenom,
            String sexe,
            String cin,
            LocalDate dateNaissance,
            String lieuNaissance,
            String adresse,
            String telephone
    ) {}
}