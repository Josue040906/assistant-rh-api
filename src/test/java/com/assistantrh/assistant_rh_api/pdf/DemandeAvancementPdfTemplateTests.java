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

class DemandeAvancementPdfTemplateTests {

    private final DemandeAvancementPdfTemplate template =
            new DemandeAvancementPdfTemplate();

    @Test
    void construitEtGenereLePdfDepuisLesSituationsStockees() throws Exception {
        Document document = documentAvancement();
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "Rakoto",
                "Jean",
                "AG-042",
                "Service RH",
                "Direction RH",
                "Gestionnaire",
                "III",
                "Corps administratif",
                "1re classe",
                1,
                LocalDate.of(2010, 1, 1)
        );

        PdfDocumentContent contenu = template.creer(document, agent);

        assertEquals("Situation actuelle", contenu.champs().get(2).label());
        assertEquals("1re classe - échelon 1", contenu.champs().get(2).value());
        assertEquals("Situation demandée", contenu.champs().get(3).label());
        assertEquals("1re classe - échelon 2", contenu.champs().get(3).value());
        assertTrue(contenu.texte().contains("Motif : Ancienneté requise"));

        byte[] pdf = new PdfDocumentGenerator().generer(contenu);
        PdfReader reader = new PdfReader(pdf);
        try {
            String textePdf = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(textePdf.contains("DEMANDE D'AVANCEMENT"));
            assertTrue(textePdf.contains("Jean Rakoto"));
            assertTrue(textePdf.contains("Corps administratif"));
            assertTrue(textePdf.contains("1re classe - échelon 1"));
            assertTrue(textePdf.contains("1re classe - échelon 2"));
            assertTrue(textePdf.contains("Ancienneté requise"));
        } finally {
            reader.close();
        }
    }

    @Test
    void refuseUnDocumentSansSituationDemandee() {
        Document document = new Document(
                15,
                "DOC-2026-00015",
                4,
                42,
                "Chef du personnel",
                "Demande d'avancement",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                null,
                null,
                null,
                Map.of("situationActuelle", Map.of())
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

    private Document documentAvancement() {
        Map<String, Object> situationActuelle = Map.of(
                "echelonId", 4,
                "echelonOrdre", 1,
                "classeId", 2,
                "classeLibelle", "1re classe",
                "classeOrdre", 3
        );
        Map<String, Object> situationDemandee = Map.of(
                "echelonId", 5,
                "echelonOrdre", 2,
                "classeId", 2,
                "classeLibelle", "1re classe",
                "classeOrdre", 3
        );
        return new Document(
                15,
                "DOC-2026-00015",
                4,
                42,
                "Chef du Service du Personnel",
                "Demande d'avancement",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                null,
                null,
                null,
                Map.of(
                        "motif",
                        "Ancienneté requise",
                        "situationActuelle",
                        situationActuelle,
                        "situationDemandee",
                        situationDemandee
                )
        );
    }
}
