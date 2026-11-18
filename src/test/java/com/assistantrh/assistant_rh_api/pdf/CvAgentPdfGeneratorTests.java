package com.assistantrh.assistant_rh_api.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CvAgentPdfGeneratorTests {

    private final CvAgentPdfGenerator generateur = new CvAgentPdfGenerator();

    private static String texte(byte[] pdf) throws Exception {
        PdfReader lecteur = new PdfReader(pdf);
        StringBuilder texte = new StringBuilder();
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            texte.append(extracteur.getTextFromPage(page)).append('\n');
        }
        return texte.toString();
    }

    @Test
    void leCvRassembleLesRubriquesRenseigneesDansLOrdre() throws Exception {
        Map<String, Object> agent = new HashMap<>(Map.of(
                "nom", "ANDRIANINAY", "prenom", "Lova", "matricule", "SPERS-001",
                "poste", "Chef de service", "service", "Service du Personnel",
                "telephone", "+261321200099", "email_agent", "lova@mef.gov.mg",
                "date_embauche", LocalDate.of(2006, 1, 16), "grade_libelle", "1re classe, 3e échelon"));
        agent.put("grade_code", "1C3E");
        Map<String, Object> complement = Map.of(
                "situationMatrimoniale", "MARIE", "nombreEnfants", 2,
                "langues", List.of(Map.of("langue", "Malagasy", "niveau", "MATERNELLE")),
                "competences", List.of("Gestion de la paie"),
                "formations", List.of(Map.of("intitule", "Gestion des carrières", "annee", 2019)));

        byte[] pdf = generateur.generer(new CvAgentPdfGenerator.CvAgent(agent,
                List.of(Map.of("date_debut", LocalDate.of(2006, 1, 16), "poste", "Chef de service",
                        "service", "Service du Personnel")),
                List.of(Map.of("date_debut", LocalDate.of(2024, 1, 16), "grade_libelle", "1re classe, 3e échelon",
                        "grade_code", "1C3E", "indice", 760)),
                List.of(Map.of("date_obtention", LocalDate.of(2004, 7, 1), "niveau", "Master", "domaine", "Gestion")),
                complement, null));

        String texte = texte(pdf);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"));
        for (String attendu : List.of("Lova ANDRIANINAY", "IDENTITÉ", "SITUATION ACTUELLE", "PARCOURS PROFESSIONNEL",
                "CARRIÈRE", "PARCOURS ACADÉMIQUE", "FORMATIONS CONTINUES", "COMPÉTENCES", "LANGUES",
                "Marié·e, 2 enfants", "Gestion de la paie", "Malagasy (langue maternelle)", "lova@mef.gov.mg")) {
            assertTrue(texte.contains(attendu), "Absent du CV : " + attendu + "\n" + texte);
        }
        assertTrue(texte.indexOf("IDENTITÉ") < texte.indexOf("SITUATION ACTUELLE"));
    }

    @Test
    void uneRubriqueSansDonneeNApparaitPas() throws Exception {
        byte[] pdf = generateur.generer(new CvAgentPdfGenerator.CvAgent(
                Map.of("nom", "RABE", "prenom", "Mialitiana", "matricule", "DEMO-002"),
                List.of(), List.of(), List.of(), Map.of(), null));

        String texte = texte(pdf);
        assertTrue(texte.contains("Mialitiana RABE"));
        for (String absent : List.of("PARCOURS PROFESSIONNEL", "CARRIÈRE", "COMPÉTENCES", "LANGUES", "FORMATIONS")) {
            assertFalse(texte.contains(absent), "Rubrique vide affichée : " + absent);
        }
    }
}
