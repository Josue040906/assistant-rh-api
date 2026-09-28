package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RegleRhService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regles-rh")
@CrossOrigin(origins = "*")
public class RegleRhController {

    private final RegleRhService regleRhService;

    public RegleRhController(RegleRhService regleRhService) {
        this.regleRhService = regleRhService;
    }

    // =========================================================
    // CRUD ADMINISTRATION
    // =========================================================

    /**
     * Récupérer toutes les règles RH.
     */
    @GetMapping
    public List<Map<String, Object>> getAllRegles() {
        return regleRhService.getAllRegles();
    }

    /**
     * Récupérer une règle RH par son ID.
     */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> getRegleById(
            @PathVariable Integer id
    ) {
        return regleRhService
                .getRegleById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Rechercher des règles RH.
     */
    @GetMapping("/recherche")
    public List<Map<String, Object>> rechercherRegles(
            @RequestParam(required = false) String query
    ) {
        return regleRhService.rechercherRegles(query);
    }

    /**
     * Récupérer les types de règles RH.
     */
    @GetMapping("/types")
    public List<Map<String, Object>> getTypesRegles() {
        return regleRhService.getTypesRegles();
    }

    /**
     * Créer une règle RH.
     */
    @PostMapping
    public ResponseEntity<?> createRegle(
            @RequestBody RegleRhRequest request
    ) {
        try {

            Map<String, Object> regle =
                    regleRhService.createRegle(
                            request.typeRegleId(),
                            request.code(),
                            request.libelle(),
                            request.description(),
                            request.populationConcernee(),
                            request.referenceJuridique(),
                            request.article(),
                            request.dateDebutValidite(),
                            request.dateFinValidite(),
                            request.priorite(),
                            request.active()
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(regle);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Impossible de créer cette règle RH. Vérifiez notamment que le code n'est pas déjà utilisé et que le type de règle existe."
                    ));
        }
    }

    /**
     * Modifier une règle RH.
     */
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<?> updateRegle(
            @PathVariable Integer id,
            @RequestBody RegleRhRequest request
    ) {
        try {

            return regleRhService
                    .updateRegle(
                            id,
                            request.typeRegleId(),
                            request.code(),
                            request.libelle(),
                            request.description(),
                            request.populationConcernee(),
                            request.referenceJuridique(),
                            request.article(),
                            request.dateDebutValidite(),
                            request.dateFinValidite(),
                            request.priorite(),
                            request.active()
                    )
                    .map(ResponseEntity::ok)
                    .orElseGet(() ->
                            ResponseEntity.notFound().build()
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Impossible de modifier cette règle RH. Vérifiez notamment que le code n'est pas déjà utilisé et que le type de règle existe."
                    ));
        }
    }

    /**
     * Supprimer une règle RH.
     */
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<?> deleteRegle(
            @PathVariable Integer id
    ) {
        try {

            boolean deleted =
                    regleRhService.deleteRegle(id);

            if (!deleted) {
                return ResponseEntity
                        .notFound()
                        .build();
            }

            return ResponseEntity.noContent().build();

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Cette règle RH ne peut pas être supprimée car elle est utilisée par d'autres données."
                    ));
        }
    }

    // =========================================================
    // FONCTIONS MÉTIER
    // =========================================================

    /**
     * Récupérer une règle active par son code.
     *
     * Exemple :
     * GET /api/regles-rh/code/AVANCEMENT_ECHELON_CONCEPTEUR_2ANS
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Map<String, Object>> obtenirRegle(
            @PathVariable String code
    ) {
        return regleRhService
                .obtenirRegleComplete(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Récupérer les règles actuellement actives et valides.
     */
    @GetMapping("/actives")
    public List<Map<String, Object>> obtenirReglesActives() {
        return regleRhService.obtenirReglesActives();
    }

    // =========================================================
    // DTO
    // =========================================================

    public record RegleRhRequest(
            Integer typeRegleId,
            String code,
            String libelle,
            String description,
            String populationConcernee,
            String referenceJuridique,
            String article,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite,
            Integer priorite,
            Boolean active
    ) {
    }
}