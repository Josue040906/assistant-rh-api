package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemandeRetraitePdfTemplateTests {

    private final DemandeRetraitePdfTemplate template =
            new DemandeRetraitePdfTemplate();

    @Test
    void produitLeContenuPdfAvecLaSituationAgentEtLaDemande() throws Exception {
        PdfDocumentContent content = template.creer(
                document(),
                new AgentDocumentInfo(
                        "Rakoto",
                        "Jean",
                        "AG-042",
                        "Service RH",
                        "Direction RH",
                        "Gestionnaire",
                        "III",
                        "Corps administratif",
                        "1re classe",
                        2,
                        LocalDate.of(2010, 1, 5)
                )
        );

        assertEquals("Demande de retraite", content.objet());
        assertTrue(content.texte().contains("15/06/2027"));
        assertTrue(content.texte().contains("Motif : Fin de carrière"));
        assertTrue(content.texte().contains("Observation : À traiter"));

        byte[] pdf = new PdfDocumentGenerator().generer(content);
        PdfReader reader = new PdfReader(pdf);
        try {
            String textePdf = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(textePdf.contains("DEMANDE DE RETRAITE"));
            assertTrue(textePdf.contains("Jean Rakoto"));
            assertTrue(textePdf.contains("Direction RH"));
            assertTrue(textePdf.contains("Corps administratif"));
            assertTrue(textePdf.contains("1re classe"));
            assertTrue(textePdf.contains("15/06/2027"));
            assertTrue(textePdf.contains("À traiter"));
        } finally {
            reader.close();
        }
    }

    @Test
    void refuseUneDateAbsente() {
        Document document = new Document(
                15,
                "DOC-2026-00015",
                5,
                42,
                "Chef du personnel",
                "Demande de retraite",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                null,
                null,
                null,
                Map.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> template.creer(
                        document,
                        new AgentDocumentInfo(
                                "Rakoto",
                                "Jean",
                                "AG-042",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                )
        );
    }

    private Document document() {
        return new Document(
                15,
                "DOC-2026-00015",
                5,
                42,
                "Chef du Service du Personnel",
                "Demande de retraite",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                null,
                null,
                null,
                Map.of(
                        "dateDepartSouhaitee", "2027-06-15",
                        "motif", "Fin de carrière",
                        "observation", "À traiter"
                )
        );
    }
}
