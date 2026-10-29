package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.EmployeService;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
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
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer authenticatedUserId
    ) {

        Optional<Map<String, Object>> profil =
                employeService.getProfilByUserId(authenticatedUserId);

        if (profil.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(profil.get());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> modifierProfil(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer authenticatedUserId,
            @RequestBody ProfilUpdateRequest request
    ) {

        employeService.modifierProfil(
                authenticatedUserId,
                request.nom(),
                request.prenom(),
                request.sexe(),
                request.cin(),
                request.dateNaissance(),
                request.lieuNaissance(),
                request.adresse(),
                request.telephone(),
                request.localiteResidenceId(),
                Boolean.TRUE.equals(request.effacerLocaliteResidence())
        );

        return ResponseEntity.ok(
                Map.of(
                        "userId", authenticatedUserId,
                        "message", "Profil modifié avec succès."
                )
        );
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploaderPhotoProfil(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer authenticatedUserId,
            @RequestParam("file") MultipartFile file
    ) {
        String photoPath = employeService.enregistrerPhotoProfil(authenticatedUserId, file);
        return ResponseEntity.ok(
                Map.of(
                        "userId", authenticatedUserId,
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
            String telephone,
            Long localiteResidenceId,
            Boolean effacerLocaliteResidence
    ) {}
}