package com.assistantrh.assistant_rh_api.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class PdfDocumentGenerator {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generer(PdfDocumentContent contenu) {
        Objects.requireNonNull(contenu, "Le contenu PDF est obligatoire.");

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 45, 50);

        try {
            PdfWriter.getInstance(document, sortie);
            document.open();
            ajouterContenu(document, contenu);
        } catch (DocumentException exception) {
            throw new IllegalStateException(
                    "La génération du PDF a échoué.",
                    exception
            );
        } finally {
            document.close();
        }

        return sortie.toByteArray();
    }

    private void ajouterContenu(
            Document document,
            PdfDocumentContent contenu
    ) throws DocumentException {
        Font titreAdministration = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font titreDocument = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font texteGras = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font texteNormal = new Font(Font.HELVETICA, 10, Font.NORMAL);

        Paragraph administration =
                new Paragraph(contenu.administration(), titreAdministration);
        administration.setAlignment(Element.ALIGN_CENTER);
        document.add(administration);

        ajouterDevise(document, contenu.devise());

        Paragraph titre = new Paragraph(contenu.titre(), titreDocument);
        titre.setAlignment(Element.ALIGN_CENTER);
        titre.setSpacingBefore(24);
        titre.setSpacingAfter(12);
        document.add(titre);

        ajouterLigneOptionnelle(
                document,
                "Référence : ",
                contenu.reference(),
                texteGras,
                texteNormal
        );
        ajouterLigne(document, "Agent : ", contenu.nomAgent(), texteGras, texteNormal);
        ajouterLigneOptionnelle(
                document,
                "Matricule : ",
                contenu.matricule(),
                texteGras,
                texteNormal
        );
        ajouterLigneOptionnelle(
                document,
                "Service : ",
                contenu.service(),
                texteGras,
                texteNormal
        );
        ajouterLigneOptionnelle(
                document,
                "Poste : ",
                contenu.poste(),
                texteGras,
                texteNormal
        );

        Paragraph destinataire =
                new Paragraph("À l'attention de : " + contenu.destinataire(), texteNormal);
        destinataire.setSpacingBefore(12);
        document.add(destinataire);

        ajouterLigne(document, "Objet : ", contenu.objet(), texteGras, texteNormal);

        Paragraph texte = new Paragraph(contenu.texte(), texteNormal);
        texte.setSpacingBefore(18);
        texte.setLeading(16);
        document.add(texte);

        if (!contenu.champs().isEmpty()) {
            PdfPTable tableau = new PdfPTable(2);
            tableau.setWidthPercentage(100);
            tableau.setWidths(new float[]{1, 2});
            tableau.setSpacingBefore(12);

            for (PdfDocumentField champ : contenu.champs()) {
                tableau.addCell(creerCellule(champ.label(), texteGras));
                tableau.addCell(creerCellule(champ.value(), texteNormal));
            }

            document.add(tableau);
        }

        Paragraph date = new Paragraph(
                "Fait le " + FORMAT_DATE.format(contenu.date()),
                texteNormal
        );
        date.setAlignment(Element.ALIGN_RIGHT);
        date.setSpacingBefore(24);
        document.add(date);

        PdfPTable signature = new PdfPTable(1);
        signature.setWidthPercentage(42);
        signature.setHorizontalAlignment(Element.ALIGN_RIGHT);
        signature.setSpacingBefore(16);
        PdfPCell celluleSignature = creerCellule(
                "Signature\n\n\n",
                texteGras
        );
        celluleSignature.setHorizontalAlignment(Element.ALIGN_CENTER);
        signature.addCell(celluleSignature);
        document.add(signature);
    }

    private void ajouterDevise(
            Document document,
            String devise
    ) throws DocumentException {
        if (devise == null || devise.isBlank()) {
            return;
        }

        Paragraph paragraphe = new Paragraph(
                devise,
                new Font(Font.HELVETICA, 9, Font.ITALIC)
        );
        paragraphe.setAlignment(Element.ALIGN_CENTER);
        paragraphe.setSpacingBefore(3);
        document.add(paragraphe);
    }

    private void ajouterLigne(
            Document document,
            String libelle,
            String valeur,
            Font texteGras,
            Font texteNormal
    ) throws DocumentException {
        Paragraph ligne = new Paragraph();
        ligne.add(new Phrase(libelle, texteGras));
        ligne.add(new Phrase(valeur, texteNormal));
        ligne.setSpacingAfter(4);
        document.add(ligne);
    }

    private void ajouterLigneOptionnelle(
            Document document,
            String libelle,
            String valeur,
            Font texteGras,
            Font texteNormal
    ) throws DocumentException {
        if (valeur != null && !valeur.isBlank()) {
            ajouterLigne(document, libelle, valeur, texteGras, texteNormal);
        }
    }

    private PdfPCell creerCellule(String texte, Font police) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police));
        cellule.setBorder(Rectangle.BOX);
        cellule.setPadding(6);
        return cellule;
    }
}
