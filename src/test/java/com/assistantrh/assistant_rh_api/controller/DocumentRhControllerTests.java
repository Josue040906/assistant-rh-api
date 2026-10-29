package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.DocumentRhService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRhControllerTests {

    private final DocumentRhService documentRhService =
            mock(DocumentRhService.class);
    private final DocumentRhController controller =
            new DocumentRhController(documentRhService);

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

    @Test
    void creerDocumentUtiliseIdentiteAuthentifieeEtIgnoreActeurDuCorps() {
        LocalDate date = LocalDate.of(2026, 10, 30);
        when(documentRhService.creerDocument(
                17, 3, 42L, "Attestation", date, date,
                "EN_ATTENTE", null, "Observation"
        )).thenReturn(12L);
        when(documentRhService.getDocumentById(12L)).thenReturn(Optional.empty());

        DocumentRhController.CreateDocumentRequest request =
                new DocumentRhController.CreateDocumentRequest(
                        999, 3, 42L, "Attestation", date, date,
                        "EN_ATTENTE", null, "Observation"
                );

        ResponseEntity<?> response = controller.creerDocument(17, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(documentRhService).creerDocument(
                17, 3, 42L, "Attestation", date, date,
                "EN_ATTENTE", null, "Observation"
        );
    }

    @Test
    void modifierDocumentUtiliseIdentiteAuthentifieeEtIgnoreActeurDuCorps() {
        LocalDate date = LocalDate.of(2026, 10, 30);
        when(documentRhService.getDocumentById(12L)).thenReturn(Optional.empty());

        DocumentRhController.UpdateDocumentRequest request =
                new DocumentRhController.UpdateDocumentRequest(
                        999, 3, 42L, "Attestation modifiée", date, date,
                        "EN_ATTENTE", null, "Observation"
                );

        ResponseEntity<?> response = controller.modifierDocument(12L, 17, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(documentRhService).modifierDocument(
                12L, 17, 3, 42L, "Attestation modifiée", date, date,
                "EN_ATTENTE", null, "Observation"
        );
    }

    @Test
    void archiverDocumentUtiliseIdentiteAuthentifiee() {
        when(documentRhService.getDocumentById(12L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.archiverDocument(12L, 17);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(documentRhService).archiverDocument(12L, 17);
    }
}
