package com.assistantrh.assistant_rh_api.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentGeneratorTests {

    private final PdfDocumentGenerator generator =
            new PdfDocumentGenerator();

    @Test
    void produitUnPdfLisibleAvecLesSectionsGeneriques() throws Exception {
        PdfDocumentContent contenu = new PdfDocumentContent(
                "Administration",
                null,
                "Demande administrative",
                "DOC-2026-00001",
                "Jean Rakoto",
                "AG-001",
                "Service RH",
                "Gestionnaire",
                "Chef du personnel",
                "Demande de document",
                "Texte administratif de la demande.",
                List.of(new PdfDocumentField("Date de debut", "10/10/2026")),
                LocalDate.of(2026, 10, 7)
        );

        byte[] pdf = generator.generer(contenu);

        assertTrue(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
                .startsWith("%PDF"));

        PdfReader reader = new PdfReader(pdf);
        try {
            assertEquals(1, reader.getNumberOfPages());
            String textePdf = new PdfTextExtractor(reader).getTextFromPage(1);

            assertTrue(textePdf.contains("Administration"));
            assertTrue(textePdf.contains("Jean Rakoto"));
            assertTrue(textePdf.contains("Chef du personnel"));
            assertTrue(textePdf.contains("Date de debut"));
            assertTrue(textePdf.contains("10/10/2026"));
            assertTrue(textePdf.contains("Signature"));
        } finally {
            reader.close();
        }
    }
}
