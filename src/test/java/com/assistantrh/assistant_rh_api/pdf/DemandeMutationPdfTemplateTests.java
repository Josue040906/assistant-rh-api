package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemandeMutationPdfTemplateTests {

    private final DemandeMutationPdfTemplate template =
            new DemandeMutationPdfTemplate();

    @Test
    void construitLeContenuAvecLesSituationsEtLeMotif() throws java.io.IOException {
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "Rakoto",
                "Jean",
                "AG-042",
                "Service actuel",
                "Direction actuelle",
                "Poste actuel",
                null,
                null,
                null,
                null,
                null,
                "Antananarivo"
        );
        Document document = new Document(
                15,
                "DOC-2026-00015",
                9,
                42,
                "Chef du personnel",
                "Demande de mutation",
                LocalDate.of(2026, 10, 7),
                LocalDate.of(2027, 2, 1),
                "BROUILLON",
                null,
                null,
                null,
                Map.of(
                        "situationActuelle",
                        Map.of(
                                "direction", "Direction au dépôt",
                                "service", "Service au dépôt",
                                "poste", "Poste au dépôt",
                                "lieuTravail", "Lieu au dépôt"
                        ),
                        "situationDemandee",
                        Map.of(
                                "direction", "Direction cible",
                                "service", "Service cible",
                                "poste", "Poste cible",
                                "lieuTravail", "Toamasina"
                        ),
                        "dateEffetSouhaitee",
                        "2027-02-01",
                        "motif",
                        "Rapprochement familial"
                )
        );

        PdfDocumentContent content = template.creer(document, agent);

        assertEquals("DEMANDE DE MUTATION", content.titre());
        assertTrue(content.texte().contains("Service cible"));
        assertTrue(content.texte().contains("Rapprochement familial"));
        assertTrue(content.champs().stream().anyMatch(
                field -> field.label().equals("Service actuel")
                        && field.value().equals("Service au dépôt")
        ));
        assertTrue(content.champs().stream().anyMatch(
                field -> field.label().equals("Lieu de travail souhaité")
                        && field.value().equals("Toamasina")
        ));

        byte[] pdf = new PdfDocumentGenerator().generer(content);
        PdfReader reader = new PdfReader(pdf);
        try {
            String textePdf = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(textePdf.contains("DEMANDE DE MUTATION"));
            assertTrue(textePdf.contains("Jean Rakoto"));
            assertTrue(textePdf.contains("Service actuel"));
            assertTrue(textePdf.contains("Service cible"));
            assertTrue(textePdf.contains("Toamasina"));
            assertTrue(textePdf.contains("Rapprochement familial"));
            assertTrue(textePdf.contains("01/02/2027"));
        } finally {
            reader.close();
        }
    }
}
