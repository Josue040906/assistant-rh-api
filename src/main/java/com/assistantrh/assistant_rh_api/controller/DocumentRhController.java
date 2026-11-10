package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Document;
import com.assistantrh.assistant_rh_api.model.TypeDocument;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.DocumentCycleService;
import com.assistantrh.assistant_rh_api.service.DocumentRhService;
import com.assistantrh.assistant_rh_api.service.IdempotenceService;
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
    private final IdempotenceService idempotenceService;
    private final DocumentCycleService documentCycleService;

    public DocumentRhController(
            DocumentRhService documentRhService,
            IdempotenceService idempotenceService,
            DocumentCycleService documentCycleService
    ) {
        this.documentRhService = documentRhService;
        this.idempotenceService = idempotenceService;
        this.documentCycleService = documentCycleService;
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

    /**
     * Idempotency-Key facultatif : un rejeu de la même demande ne crée pas de doublon.
     * apercu=true valide la demande comme une création, sans rien enregistrer,
     * et renvoie son résumé en clair.
     */
    @PostMapping("/demandes")
    public ResponseEntity<?> creerDocumentMetier(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer authenticatedUserId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(defaultValue = "false") boolean apercu,
            @RequestBody CreateDocumentMetierRequest request
    ) {
        if (apercu) {
            try {
                return ResponseEntity.ok(Map.of("apercu", true, "resume",
                        documentRhService.apercuDocumentMetier(
                                request.typeDocumentId(),
                                request.employeId(),
                                request.destinataire(),
                                request.donnees(),
                                request.constatsPiece()
                        )));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
            }
        }
        return idempotenceService.executer(
                authenticatedUserId,
                idempotencyKey,
                "POST /api/documents/demandes",
                request,
                () -> {
                    try {
                        Document document = documentRhService.creerDocumentMetier(
                                request.typeDocumentId(),
                                request.employeId(),
                                request.destinataire(),
                                request.donnees(),
                                request.constatsPiece(),
                                authenticatedUserId
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
        );
    }

    /** Aperçu PDF d'une demande en cours de saisie : rien n'est enregistré. */
    @PostMapping("/demandes/apercu-pdf")
    public ResponseEntity<?> apercuPdf(@RequestBody CreateDocumentMetierRequest request) {
        try {
            byte[] pdf = documentRhService.apercuPdfDocumentMetier(
                    request.typeDocumentId(),
                    request.employeId(),
                    request.destinataire(),
                    request.donnees()
            );
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"apercu.pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    /** L'auteur valide sa demande : elle prend effet, le chef en est notifié. */
    @PostMapping("/{id:\\d+}/appliquer")
    public ResponseEntity<?> appliquer(
            @PathVariable Long id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        return cycle(id, () -> documentCycleService.appliquerDocument(id, acteurId), "Demande appliquée.");
    }

    /** L'auteur (ou un chef) renonce à un brouillon. */
    @PostMapping("/{id:\\d+}/annuler")
    public ResponseEntity<?> annuler(
            @PathVariable Long id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        return cycle(id, () -> documentCycleService.annulerDocument(id, acteurId), "Document annulé.");
    }

    /** Un document validé ou annulé se range aux archives. */
    @PutMapping("/{id:\\d+}/archiver")
    public ResponseEntity<?> archiver(
            @PathVariable Long id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        return cycle(id, () -> documentCycleService.archiverDocument(id, acteurId), "Document archivé.");
    }

    /** Une étape du cycle de vie : 404 si le document n'existe pas, 400 si l'étape est refusée. */
    private ResponseEntity<?> cycle(Long id, Runnable etape, String message) {
        try {
            etape.run();
        } catch (IllegalArgumentException refus) {
            return "Le document demandé n'existe pas.".equals(refus.getMessage())
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.badRequest().body(Map.of("message", refus.getMessage()));
        }
        return ResponseEntity.ok(Map.of("message", message, "id", id));
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

    /**
     * Les documents, filtrés au besoin : statut (CREE, APPLIQUE, ANNULE, ARCHIVE ou
     * APPLICABLE), agent concerné, type (CONGE, MUTATION…), période de création du..au.
     */
    @GetMapping
    public Map<String, Object> getAllDocuments(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Integer employeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) java.time.LocalDate du,
            @RequestParam(required = false) java.time.LocalDate au
    ) {

        List<Map<String, Object>> documents =
                documentRhService.getDocuments(statut, employeId, type, du, au);

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

    /** constatsPiece : facultatif, ce qui a été constaté sur la pièce scannée d'où vient la demande. */
    public record CreateDocumentMetierRequest(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees,
            String constatsPiece
    ) {
        public CreateDocumentMetierRequest(
                Integer typeDocumentId, Integer employeId, String destinataire, Map<String, Object> donnees) {
            this(typeDocumentId, employeId, destinataire, donnees, null);
        }
    }


}
