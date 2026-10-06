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

class DemandeCongePdfTemplateTests {

    private final DemandeCongePdfTemplate template =
            new DemandeCongePdfTemplate();

    @Test
    void construitLeModeleAvecUneDureeCalendaireInclusive() {
        Document document = new Document(
                15,
                "DOC-2026-00015",
                3,
                42,
                "Chef du Service du Personnel",
                "Demande de congé",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                null,
                null,
                null,
                Map.of(
                        "dateDebut",
                        "2026-10-10",
                        "dateFin",
                        "2026-10-12"
                )
        );
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "Jean Rakoto",
                "AG-042",
                "Service RH",
                "Gestionnaire"
        );

        PdfDocumentContent contenu = template.creer(document, agent);

        assertEquals("MINISTERE DE L'ECONOMIE ET FINANCES", contenu.administration());
        assertEquals("Fitiavana tanindrazana fandrosoana", contenu.devise());
        assertEquals("DEMANDE DE CONG\u00c9", contenu.titre());
        assertEquals("Chef du Service du Personnel", contenu.destinataire());
        assertEquals("10/10/2026", contenu.champs().get(0).value());
        assertEquals("12/10/2026", contenu.champs().get(1).value());
        assertEquals("3", contenu.champs().get(2).value());
    }

    @Test
    void genereUnPdfDeCongeAvecLeTitreEtLaDuree() throws Exception {
        Document document = new Document(
                15,
                "DOC-2026-00015",
                3,
                42,
                "Chef du Service du Personnel",
                "Demande de congé",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                null,
                null,
                null,
                Map.of(
                        "dateDebut",
                        "2026-10-10",
                        "dateFin",
                        "2026-10-12"
                )
        );
        PdfDocumentContent contenu = template.creer(
                document,
                new AgentDocumentInfo(
                        "Jean Rakoto",
                        "AG-042",
                        "Service RH",
                        "Gestionnaire"
                )
        );
        byte[] pdf = new PdfDocumentGenerator().generer(contenu);
        PdfReader reader = new PdfReader(pdf);

        try {
            String textePdf = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(textePdf.contains("DEMANDE DE CONG\u00c9"));
            assertTrue(textePdf.contains("Nombre de jours"));
            assertTrue(textePdf.contains("3"));
        } finally {
            reader.close();
        }
    }
}
