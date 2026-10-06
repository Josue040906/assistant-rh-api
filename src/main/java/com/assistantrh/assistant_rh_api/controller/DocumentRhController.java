package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Document;
import com.assistantrh.assistant_rh_api.model.TypeDocument;
import com.assistantrh.assistant_rh_api.service.DocumentRhService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @GetMapping("/types")
    public List<TypeDocument> getTypesDocumentsActifs() {
        return documentRhService.getTypesDocumentsActifs();
    }

    @GetMapping("/demandes")
    public Map<String, Object> getDocumentsMetier(
            @RequestParam(required = false) Integer employeId,
            @RequestParam(required = false) Integer typeDocumentId
    ) {
        List<Document> documents =
                documentRhService.listerDocuments(
                        employeId,
                        typeDocumentId
                );

        return Map.of(
                "value", documents,
                "Count", documents.size()
        );
    }

    @GetMapping("/demandes/{id:\\d+}")
    public ResponseEntity<?> getDocumentMetierById(
            @PathVariable Integer id
    ) {
        if (id <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "L'identifiant du document est invalide.")
            );
        }

        return documentRhService.getDocumentMetierById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/demandes")
    public ResponseEntity<?> creerDocumentMetier(
            @RequestBody CreateDocumentMetierRequest request
    ) {
        try {
            Document document = documentRhService.creerDocumentMetier(
                    request.typeDocumentId(),
                    request.employeId(),
                    request.destinataire(),
                    request.donnees()
            );
            return ResponseEntity.status(201).body(document);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", exception.getMessage())
            );
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(409).body(
                    Map.of(
                            "message",
                            "Impossible de créer le document : une donnée référencée est invalide."
                    )
            );
        }
    }

    @GetMapping("/demandes/{id:\\d+}/pdf")
    public ResponseEntity<?> getDocumentPdf(
            @PathVariable Integer id
    ) {
        if (id <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "L'identifiant du document est invalide.")
            );
        }

        try {
            return documentRhService.obtenirPdf(id)
                    .<ResponseEntity<?>>map(pdf ->
                            ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_PDF)
                                    .header(
                                            HttpHeaders.CONTENT_DISPOSITION,
                                            ContentDisposition.attachment()
                                                    .filename("document-" + id + ".pdf")
                                                    .build()
                                                    .toString()
                                    )
                                    .body(pdf)
                    )
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", exception.getMessage())
            );
        } catch (IllegalStateException exception) {
            return ResponseEntity.internalServerError().body(
                    Map.of("message", "Impossible de générer ou lire le PDF.")
            );
        }
    }

    @GetMapping
    public Map<String, Object> getAllDocuments() {

        List<Map<String, Object>> documents =
                documentRhService.getAllDocuments();

        return Map.of(
                "value", documents,
                "Count", documents.size()
        );
    }

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

    @GetMapping("/agent/{agentId:\\d+}")
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

    @GetMapping("/historique")
    public Map<String, Object> getHistorique() {

        List<Map<String, Object>> documents =
                documentRhService.getAllDocuments();

        return Map.of(
                "value", documents,
                "Count", documents.size()
        );
    }

    @GetMapping("/{id:\\d+}")
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
                .orElseGet(
                        () -> ResponseEntity.notFound().build()
                );
    }

    @PostMapping
    public ResponseEntity<?> creerDocument(
            @RequestBody CreateDocumentRequest request
    ) {

        try {

            Long id =
                    documentRhService.creerDocument(
                            request.acteurId(),
                            request.typeDocumentId(),
                            request.employeId(),
                            request.objet(),
                            request.dateDocument(),
                            request.dateEffet(),
                            request.statut(),
                            request.fichierPath(),
                            request.observation()
                    );

            return documentRhService
                    .getDocumentById(id)
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
                                                    "id",
                                                    id,
                                                    "message",
                                                    "Document créé avec succès."
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

            return ResponseEntity
                    .status(409)
                    .body(
                            Map.of(
                                    "message",
                                    "Impossible de créer le document : une donnée référencée est invalide."
                            )
                    );
        }
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<?> modifierDocument(
            @PathVariable Long id,
            @RequestBody UpdateDocumentRequest request
    ) {

        try {

            documentRhService.modifierDocument(
                    id,
                    request.acteurId(),
                    request.typeDocumentId(),
                    request.employeId(),
                    request.objet(),
                    request.dateDocument(),
                    request.dateEffet(),
                    request.statut(),
                    request.fichierPath(),
                    request.observation()
            );

            return documentRhService
                    .getDocumentById(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(
                            () -> ResponseEntity.notFound().build()
                    );

        } catch (IllegalArgumentException e) {

            if ("Le document demandé n'existe pas."
                    .equals(e.getMessage())) {

                return ResponseEntity
                        .notFound()
                        .build();
            }

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            e.getMessage()
                    )
            );

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(409)
                    .body(
                            Map.of(
                                    "message",
                                    "Impossible de modifier le document : une donnée référencée est invalide."
                            )
                    );
        }
    }

    @PutMapping("/{id:\\d+}/archiver")
    public ResponseEntity<?> archiverDocument(
            @PathVariable Long id,
            @RequestParam Integer acteurId
    ) {

        try {

            documentRhService.archiverDocument(
                    id,
                    acteurId
            );

            return documentRhService
                    .getDocumentById(id)
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
                    .orElseGet(
                            () -> ResponseEntity.notFound().build()
                    );

        } catch (IllegalArgumentException e) {

            if ("Le document demandé n'existe pas."
                    .equals(e.getMessage())) {

                return ResponseEntity
                        .notFound()
                        .build();
            }

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            e.getMessage()
                    )
            );
        }
    }

    public record CreateDocumentRequest(
            Integer acteurId,
            Integer typeDocumentId,
            Long employeId,
            String objet,
            LocalDate dateDocument,
            LocalDate dateEffet,
            String statut,
            String fichierPath,
            String observation
    ) {
    }

    public record CreateDocumentMetierRequest(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees
    ) {
    }

    public record UpdateDocumentRequest(
            Integer acteurId,
            Integer typeDocumentId,
            Long employeId,
            String objet,
            LocalDate dateDocument,
            LocalDate dateEffet,
            String statut,
            String fichierPath,
            String observation
    ) {
    }
}