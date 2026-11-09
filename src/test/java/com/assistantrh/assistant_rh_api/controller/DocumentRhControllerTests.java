package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.DocumentRhService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentRhControllerTests {

    private final DocumentRhService documentRhService =
            mock(DocumentRhService.class);
    private final com.assistantrh.assistant_rh_api.service.IdempotenceService idempotence =
            mock(com.assistantrh.assistant_rh_api.service.IdempotenceService.class);
    private final DocumentRhController controller =
            new DocumentRhController(documentRhService, idempotence);

    @Test
    void lApercuDUneDemandeNeCreeRienEtNePassePasParLIdempotence() {
        java.util.Map<String, Object> donnees = java.util.Map.of("dateDebut", "2026-12-01", "dateFin", "2026-12-15");
        when(documentRhService.apercuDocumentMetier(3, 28, "DRH", donnees, null))
                .thenReturn(java.util.Map.of("Agent", "BEFANAMBINANA Hasina"));

        ResponseEntity<?> response = controller.creerDocumentMetier(7, "cle-1", true,
                new DocumentRhController.CreateDocumentMetierRequest(3, 28, "DRH", donnees));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(java.util.Map.of("apercu", true, "resume", java.util.Map.of("Agent", "BEFANAMBINANA Hasina")),
                response.getBody());
        org.mockito.Mockito.verifyNoInteractions(idempotence);
        org.mockito.Mockito.verify(documentRhService, org.mockito.Mockito.never())
                .creerDocumentMetier(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unApercuRefuseRepond400AvecLeMessageDeLaValidation() {
        when(documentRhService.apercuDocumentMetier(3, 28, "DRH", java.util.Map.of(), null))
                .thenThrow(new IllegalArgumentException("Le champ 'x' n'est pas prévu."));

        ResponseEntity<?> response = controller.creerDocumentMetier(7, null, true,
                new DocumentRhController.CreateDocumentMetierRequest(3, 28, "DRH", java.util.Map.of()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(java.util.Map.of("message", "Le champ 'x' n'est pas prévu."), response.getBody());
    }

    @Test
    void retourneLePdfAvecLeTypeDeContenuApproprie() {
        byte[] pdf = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(documentRhService.obtenirPdf(12)).thenReturn(Optional.of(pdf));

        ResponseEntity<?> response = controller.getDocumentPdf(12);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals(
                "attachment; filename=\"document-12.pdf\"",
                response.getHeaders().getFirst("Content-Disposition")
        );
        assertArrayEquals(pdf, (byte[]) response.getBody());
    }

    @Test
    void retourneUnNotFoundSiLePdfEstAbsent() {
        when(documentRhService.obtenirPdf(12)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getDocumentPdf(12);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
