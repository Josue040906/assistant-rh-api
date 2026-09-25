        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.DocumentRhService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentRhController {

    private final DocumentRhService documentRhService;

    public DocumentRhController(DocumentRhService documentRhService) {
        this.documentRhService = documentRhService;
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentById(
            @PathVariable Long id
    ) {

        return documentRhService.getDocumentById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/recherche")
    public Map<String, Object> rechercherDocuments(
            @RequestParam String query
    ) {

        List<Map<String, Object>> documents =
                documentRhService.rechercherDocuments(query);

        return Map.of(
                "value", documents,
                "Count", documents.size()
        );
    }

    @GetMapping("/agent/{agentId}")
    public Map<String, Object> getDocumentsByAgent(
            @PathVariable Long agentId
    ) {

        List<Map<String, Object>> documents =
                documentRhService.getDocumentsByAgent(agentId);

        return Map.of(
                "value", documents,
                "Count", documents.size()
        );
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

    @PostMapping
    public ResponseEntity<Map<String, Object>> creerDocument(
            @RequestBody CreateDocumentRequest request
    ) {

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
                        ResponseEntity.status(201).body(document)
                )
                .orElseGet(() ->
                        ResponseEntity.status(201).body(
                                Map.of(
                                        "id", id,
                                        "message",
                                        "Document créé avec succès"
                                )
                        )
                );
    }

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
}
