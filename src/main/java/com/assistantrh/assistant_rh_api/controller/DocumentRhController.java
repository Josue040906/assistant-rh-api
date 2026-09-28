package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.DocumentRhService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentRhController {

    private final DocumentRhService documentRhService;

    public DocumentRhController(
            DocumentRhService documentRhService
    ) {
        this.documentRhService = documentRhService;
    }

    // =========================================================
    // LISTE
    // =========================================================

    @GetMapping
    public Map<String, Object> getAllDocuments() {

        List<Map<String, Object>> documents =
                documentRhService.getAllDocuments();

        return Map.of(
                "value", documents,
                "Count", documents.size()
        );
    }

    // =========================================================
    // DETAIL
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentById(
            @PathVariable Long id
    ) {

        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "L'identifiant du document est invalide."
                    )
            );
        }

        return documentRhService.getDocumentById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    // =========================================================
    // RECHERCHE
    // =========================================================

    @GetMapping("/recherche")
    public ResponseEntity<?> rechercherDocuments(
            @RequestParam String query
    ) {

        try {

            List<Map<String, Object>> documents =
                    documentRhService.rechercherDocuments(query);

            return ResponseEntity.ok(
                    Map.of(
                            "value", documents,
                            "Count", documents.size()
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            e.getMessage()
                    )
            );
        }
    }

    // =========================================================
    // DOCUMENTS D'UN AGENT
    // =========================================================

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<?> getDocumentsByAgent(
            @PathVariable Long agentId
    ) {

        try {

            List<Map<String, Object>> documents =
                    documentRhService.getDocumentsByAgent(agentId);

            return ResponseEntity.ok(
                    Map.of(
                            "value", documents,
                            "Count", documents.size()
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            e.getMessage()
                    )
            );
        }
    }

    // =========================================================
    // HISTORIQUE
    // =========================================================

    @GetMapping("/historique")
    public Map<String, Object> getHistorique() {

        List<Map<String, Object>> documents =
                documentRhService.getAllDocuments();

        return Map.of(
                "value", documents,
                "Count", documents.size()
        );
    }

    // =========================================================
    // CREATION
    // =========================================================

    @PostMapping
    public ResponseEntity<?> creerDocument(
            @RequestBody CreateDocumentRequest request
    ) {

        try {

            Long id = documentRhService.creerDocument(
                    request.type(),
                    request.objet(),
                    request.contenu(),
                    request.agentId(),
                    request.dossierId(),
                    request.auteur(),
                    request.dateDocument()
            );

            return documentRhService.getDocumentById(id)
                    .map(document ->
                            ResponseEntity
                                    .status(201)
                                    .body(document)
                    )
                    .orElseGet(() ->
                            ResponseEntity
                                    .status(201)
                                    .body(
                                            Map.of(
                                                    "id", id,
                                                    "message",
                                                    "Document créé avec succès"
                                            )
                                    )
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            e.getMessage()
                    )
            );

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity.status(409).body(
                    Map.of(
                            "message",
                            "Impossible de créer le document : une donnée référencée est invalide ou existe déjà."
                    )
            );
        }
    }

    // =========================================================
    // MODIFICATION
    // =========================================================

    @PutMapping("/{id}")
    public ResponseEntity<?> modifierDocument(
            @PathVariable Long id,
            @RequestBody UpdateDocumentRequest request
    ) {

        try {

            documentRhService.modifierDocument(
                    id,
                    request.type(),
                    request.objet(),
                    request.contenu(),
                    request.agentId(),
                    request.dossierId(),
                    request.statut(),
                    request.auteur(),
                    request.dateDocument()
            );

            return documentRhService.getDocumentById(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() ->
                            ResponseEntity.notFound().build()
                    );

        } catch (IllegalArgumentException e) {

            if ("Le document demandé n'existe pas."
                    .equals(e.getMessage())) {

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            e.getMessage()
                    )
            );

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity.status(409).body(
                    Map.of(
                            "message",
                            "Impossible de modifier le document : une donnée référencée est invalide."
                    )
            );
        }
    }

    // =========================================================
    // ARCHIVAGE
    // =========================================================

    @PutMapping("/{id}/archiver")
    public ResponseEntity<?> archiverDocument(
            @PathVariable Long id
    ) {

        try {

            documentRhService.archiverDocument(id);

            return documentRhService.getDocumentById(id)
                    .map(document ->
                            ResponseEntity.ok(
                                    Map.of(
                                            "message",
                                            "Document archivé avec succès.",
                                            "document",
                                            document
                                    )
                            )
                    )
                    .orElseGet(() ->
                            ResponseEntity.notFound().build()
                    );

        } catch (IllegalArgumentException e) {

            if ("Le document demandé n'existe pas."
                    .equals(e.getMessage())) {

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            e.getMessage()
                    )
            );
        }
    }

    // =========================================================
    // DTO CREATION
    // =========================================================

    public record CreateDocumentRequest(
            String type,
            String objet,
            String contenu,
            Long agentId,
            Long dossierId,
            String auteur,
            LocalDate dateDocument
    ) {
    }

    // =========================================================
    // DTO MODIFICATION
    // =========================================================

    public record UpdateDocumentRequest(
            String type,
            String objet,
            String contenu,
            Long agentId,
            Long dossierId,
            String statut,
            String auteur,
            LocalDate dateDocument
    ) {
    }
}