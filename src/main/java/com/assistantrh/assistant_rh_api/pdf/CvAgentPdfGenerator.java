package com.assistantrh.assistant_rh_api.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Le CV d'un agent : sobre, une colonne, des rubriques titrées dans la couleur de
 * SYGPERS ; une rubrique sans donnée n'apparaît pas. Les données arrivent déjà
 * lues (CvAgentService) : ce générateur ne fait que la mise en page.
 */
@Component
public class CvAgentPdfGenerator {

    private static final Color BRUN = new Color(0x5C, 0x3A, 0x21);
    private static final Color OR = new Color(0xC9, 0x9A, 0x3E);
    private static final Color GRIS = new Color(0x6E, 0x56, 0x44);
    private static final Color FILET = new Color(0xE8, 0xDC, 0xC8);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final Font nom = new Font(Font.HELVETICA, 20, Font.BOLD, BRUN);
    private final Font sousTitre = new Font(Font.HELVETICA, 11, Font.BOLD, Color.BLACK);
    private final Font discret = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);
    private final Font rubrique = new Font(Font.HELVETICA, 11, Font.BOLD, BRUN);
    private final Font etiquette = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);
    private final Font valeur = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private final Font valeurGras = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);

    public byte[] generer(CvAgent cv) {
        Objects.requireNonNull(cv, "Le contenu du CV est obligatoire.");
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 46, 46, 42, 46);
        try {
            PdfWriter.getInstance(document, sortie);
            document.addTitle("CV — " + texte(cv.agent().get("prenom")) + " " + texte(cv.agent().get("nom")));
            document.open();
            entete(document, cv);
            identite(document, cv.agent(), cv.complement());
            situationActuelle(document, cv.agent());
            parcours(document, cv.affectations());
            carriere(document, cv.carriere());
            diplomes(document, cv.diplomes());
            formations(document, cv.complement());
            competencesEtLangues(document, cv.complement());
            pied(document);
        } catch (DocumentException exception) {
            throw new IllegalStateException("La génération du CV a échoué.", exception);
        } finally {
            document.close();
        }
        return sortie.toByteArray();
    }

    /** Ce que le CV rassemble ; photo : ses octets, ou null. */
    public record CvAgent(
            Map<String, Object> agent,
            List<Map<String, Object>> affectations,
            List<Map<String, Object>> carriere,
            List<Map<String, Object>> diplomes,
            Map<String, Object> complement,
            byte[] photo
    ) { }

    private void entete(Document document, CvAgent cv) throws DocumentException {
        Map<String, Object> agent = cv.agent();
        PdfPTable table = new PdfPTable(cv.photo() == null ? 1 : 2);
        table.setWidthPercentage(100);
        if (cv.photo() != null) {
            table.setWidths(new float[]{1.1f, 5f});
            try {
                Image image = Image.getInstance(cv.photo());
                image.scaleToFit(76, 76);
                PdfPCell photo = new PdfPCell(image, false);
                photo.setBorder(Rectangle.NO_BORDER);
                photo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(photo);
            } catch (Exception illisible) {
                // Une photo illisible n'empêche pas le CV.
                table = new PdfPTable(1);
                table.setWidthPercentage(100);
            }
        }
        PdfPCell texte = new PdfPCell();
        texte.setBorder(Rectangle.NO_BORDER);
        texte.setPaddingLeft(cv.photo() == null ? 0 : 10);
        texte.setVerticalAlignment(Element.ALIGN_MIDDLE);
        texte.addElement(new Paragraph(nomComplet(agent), nom));
        String poste = joindre(" · ", agent.get("poste"), agent.get("service"));
        if (!poste.isEmpty()) {
            Paragraph ligne = new Paragraph(poste, sousTitre);
            ligne.setSpacingBefore(2);
            texte.addElement(ligne);
        }
        String direction = joindre(" · ", agent.get("direction"), agent.get("lieu_travail"));
        if (!direction.isEmpty()) {
            texte.addElement(new Paragraph(direction, discret));
        }
        String contact = joindre("   ·   ", agent.get("telephone"), agent.get("email_agent"), agent.get("adresse"));
        if (!contact.isEmpty()) {
            Paragraph ligne = new Paragraph(contact, discret);
            ligne.setSpacingBefore(4);
            texte.addElement(ligne);
        }
        table.addCell(texte);
        document.add(table);

        LineSeparator filet = new LineSeparator(1.4f, 100, OR, Element.ALIGN_CENTER, -6);
        document.add(new Chunk(filet));
    }

    private void situationActuelle(Document document, Map<String, Object> agent) throws DocumentException {
        String grade = joindre(" ", agent.get("grade_libelle"),
                agent.get("grade_code") == null ? null : "(" + agent.get("grade_code") + ")");
        List<String[]> lignes = lignes(
                "Matricule", agent.get("matricule"),
                "Type d'emploi", agent.get("type_emploi"),
                "Catégorie · corps", joindre(" · ", agent.get("categorie"), agent.get("corps_libelle")),
                "Grade", grade,
                "Indice", agent.get("indice"),
                "Embauche", agent.get("date_embauche") == null ? null
                        : jour(agent.get("date_embauche")) + " (" + anneesDepuis(agent.get("date_embauche")) + ")");
        tableau(document, "Situation actuelle", lignes);
    }

    private void identite(Document document, Map<String, Object> agent, Map<String, Object> complement)
            throws DocumentException {
        Object sexe = agent.get("sexe");
        String famille = joindre(", ", situation(complement.get("situationMatrimoniale")),
                complement.get("nombreEnfants") == null ? null
                        : complement.get("nombreEnfants") + " enfant" + (((Number) complement.get("nombreEnfants")).intValue() > 1 ? "s" : ""));
        List<String[]> lignes = lignes(
                "Naissance", joindre(", ", agent.get("date_naissance") == null ? null : jour(agent.get("date_naissance")),
                        agent.get("lieu_naissance")),
                "Sexe", "M".equals(sexe) ? "Masculin" : "F".equals(sexe) ? "Féminin" : sexe,
                "CIN", agent.get("cin"),
                "Situation familiale", famille);
        tableau(document, "Identité", lignes);
    }

    private void parcours(Document document, List<Map<String, Object>> affectations) throws DocumentException {
        if (affectations.isEmpty()) {
            return;
        }
        titre(document, "Parcours professionnel");
        PdfPTable table = colonnes(1.6f, 4.4f);
        for (Map<String, Object> affectation : affectations) {
            ligne(table, periode(affectation.get("date_debut"), affectation.get("date_fin")),
                    texte(affectation.get("poste")), joindre(" · ", affectation.get("service"), affectation.get("reference_acte")));
        }
        document.add(table);
    }

    private void carriere(Document document, List<Map<String, Object>> carriere) throws DocumentException {
        if (carriere.isEmpty()) {
            return;
        }
        titre(document, "Carrière");
        PdfPTable table = colonnes(1.6f, 4.4f);
        List<Map<String, Object>> recente = new ArrayList<>(carriere);
        java.util.Collections.reverse(recente);
        for (Map<String, Object> etape : recente) {
            String grade = joindre(" ", etape.get("grade_libelle"),
                    etape.get("grade_code") == null ? null : "(" + etape.get("grade_code") + ")");
            ligne(table, periode(etape.get("date_debut"), etape.get("date_fin")), grade,
                    joindre(" · ", etape.get("categorie_code") == null ? null : "Catégorie " + etape.get("categorie_code"),
                            etape.get("indice") == null ? null : "indice " + etape.get("indice"),
                            etape.get("type_emploi")));
        }
        document.add(table);
    }

    private void diplomes(Document document, List<Map<String, Object>> diplomes) throws DocumentException {
        if (diplomes.isEmpty()) {
            return;
        }
        titre(document, "Parcours académique");
        PdfPTable table = colonnes(1.6f, 4.4f);
        for (Map<String, Object> diplome : diplomes) {
            ligne(table, annee(diplome.get("date_obtention")),
                    joindre(" — ", diplome.get("niveau"), diplome.get("domaine")),
                    joindre(" · ", diplome.get("etablissement"),
                            diplome.get("mention") == null ? null : "Mention " + diplome.get("mention")));
        }
        document.add(table);
    }

    @SuppressWarnings("unchecked")
    private void formations(Document document, Map<String, Object> complement) throws DocumentException {
        List<Map<String, Object>> formations = (List<Map<String, Object>>) complement.getOrDefault("formations", List.of());
        if (formations.isEmpty()) {
            return;
        }
        titre(document, "Formations continues");
        PdfPTable table = colonnes(1.6f, 4.4f);
        for (Map<String, Object> formation : formations) {
            ligne(table, texte(formation.get("annee")), texte(formation.get("intitule")),
                    joindre(" · ", formation.get("organisme"),
                            formation.get("dureeJours") == null ? null : formation.get("dureeJours") + " jours"));
        }
        document.add(table);
    }

    @SuppressWarnings("unchecked")
    private void competencesEtLangues(Document document, Map<String, Object> complement) throws DocumentException {
        List<String> competences = (List<String>) complement.getOrDefault("competences", List.of());
        List<Map<String, Object>> langues = (List<Map<String, Object>>) complement.getOrDefault("langues", List.of());
        if (!competences.isEmpty()) {
            titre(document, "Compétences");
            Paragraph liste = new Paragraph(String.join("   ·   ", competences), valeur);
            liste.setLeading(15);
            document.add(liste);
        }
        if (!langues.isEmpty()) {
            titre(document, "Langues");
            List<String> textes = langues.stream()
                    .map(langue -> langue.get("langue") + " (" + niveau(langue.get("niveau")) + ")")
                    .toList();
            document.add(new Paragraph(String.join("   ·   ", textes), valeur));
        }
    }

    private void pied(Document document) throws DocumentException {
        Paragraph pied = new Paragraph("CV établi par SYGPERS le " + LocalDate.now().format(JOUR)
                + " d'après le dossier de l'agent.", discret);
        pied.setSpacingBefore(18);
        pied.setAlignment(Element.ALIGN_RIGHT);
        document.add(pied);
    }

    private void titre(Document document, String libelle) throws DocumentException {
        Paragraph titre = new Paragraph(libelle.toUpperCase(Locale.FRENCH), rubrique);
        titre.setSpacingBefore(14);
        document.add(titre);
        document.add(new Chunk(new LineSeparator(0.6f, 100, FILET, Element.ALIGN_CENTER, -3)));
        Paragraph espace = new Paragraph(" ", new Font(Font.HELVETICA, 3));
        document.add(espace);
    }

    private void tableau(Document document, String libelle, List<String[]> lignes) throws DocumentException {
        if (lignes.isEmpty()) {
            return;
        }
        titre(document, libelle);
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.3f, 2.2f, 1.3f, 2.2f});
        for (String[] ligne : lignes) {
            table.addCell(cellule(ligne[0], etiquette));
            table.addCell(cellule(ligne[1], valeur));
        }
        if (lignes.size() % 2 == 1) {
            table.addCell(cellule("", etiquette));
            table.addCell(cellule("", valeur));
        }
        document.add(table);
    }

    private PdfPTable colonnes(float gauche, float droite) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{gauche, droite});
        return table;
    }

    private void ligne(PdfPTable table, String quand, String quoi, String detail) {
        table.addCell(cellule(quand, etiquette));
        PdfPCell cellule = new PdfPCell();
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setPaddingBottom(5);
        Phrase phrase = new Phrase(quoi.isEmpty() ? "—" : quoi, valeurGras);
        Paragraph principal = new Paragraph(phrase);
        cellule.addElement(principal);
        if (!detail.isEmpty()) {
            cellule.addElement(new Paragraph(detail, discret));
        }
        table.addCell(cellule);
    }

    private PdfPCell cellule(String contenu, Font police) {
        PdfPCell cellule = new PdfPCell(new Phrase(contenu, police));
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setPaddingBottom(5);
        return cellule;
    }

    /** Paires « étiquette, valeur » ; une valeur vide est omise. */
    private static List<String[]> lignes(Object... paires) {
        List<String[]> resultat = new ArrayList<>();
        for (int i = 0; i + 1 < paires.length; i += 2) {
            String contenu = texte(paires[i + 1]);
            if (!contenu.isEmpty()) {
                resultat.add(new String[]{texte(paires[i]), contenu});
            }
        }
        return resultat;
    }

    private static String nomComplet(Map<String, Object> agent) {
        return joindre(" ", agent.get("prenom"), agent.get("nom"));
    }

    private static String joindre(String separateur, Object... morceaux) {
        List<String> presents = new ArrayList<>();
        for (Object morceau : morceaux) {
            String contenu = texte(morceau);
            if (!contenu.isEmpty()) {
                presents.add(contenu);
            }
        }
        return String.join(separateur, presents);
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString().strip();
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate jour) {
            return jour;
        }
        if (valeur instanceof java.sql.Date jour) {
            return jour.toLocalDate();
        }
        return valeur == null ? null : LocalDate.parse(valeur.toString().substring(0, 10));
    }

    private static String jour(Object valeur) {
        LocalDate date = date(valeur);
        return date == null ? "" : date.format(JOUR);
    }

    private static String annee(Object valeur) {
        LocalDate date = date(valeur);
        return date == null ? "" : String.valueOf(date.getYear());
    }

    private static String periode(Object debut, Object fin) {
        LocalDate du = date(debut);
        LocalDate au = date(fin);
        String depuis = du == null ? "?" : du.format(MOIS);
        return au == null ? "Depuis " + depuis : depuis + " – " + au.format(MOIS);
    }

    private static String anneesDepuis(Object valeur) {
        LocalDate debut = date(valeur);
        if (debut == null) {
            return "";
        }
        long annees = java.time.temporal.ChronoUnit.YEARS.between(debut, LocalDate.now());
        return annees + " an" + (annees > 1 ? "s" : "") + " de service";
    }

    private static String situation(Object code) {
        if (code == null) {
            return null;
        }
        return switch (code.toString()) {
            case "CELIBATAIRE" -> "Célibataire";
            case "MARIE" -> "Marié·e";
            case "DIVORCE" -> "Divorcé·e";
            case "VEUF" -> "Veuf·ve";
            default -> code.toString();
        };
    }

    private static String niveau(Object code) {
        if (code == null) {
            return "";
        }
        return switch (code.toString()) {
            case "MATERNELLE" -> "langue maternelle";
            case "COURANT" -> "courant";
            case "INTERMEDIAIRE" -> "intermédiaire";
            case "NOTIONS" -> "notions";
            default -> code.toString().toLowerCase(Locale.FRENCH);
        };
    }
}
