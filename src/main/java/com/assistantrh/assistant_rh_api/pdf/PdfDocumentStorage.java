package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.stockage.StockageFichiers;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Les PDF des documents, rangés dans « documents/ » du stockage des fichiers. */
@Component
public class PdfDocumentStorage {

    private static final Pattern NOM_FICHIER_PDF =
            Pattern.compile(
                    "document-[1-9][0-9]*-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.pdf"
            );

    private final StockageFichiers stockage;

    public PdfDocumentStorage(StockageFichiers stockage) {
        this.stockage = Objects.requireNonNull(stockage, "Le stockage des fichiers est obligatoire.");
    }

    public String enregistrer(Integer documentId, byte[] contenuPdf) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du document est invalide."
            );
        }

        if (!estPdf(contenuPdf)) {
            throw new IllegalArgumentException(
                    "Le contenu à enregistrer n'est pas un PDF valide."
            );
        }

        String cle = "documents/document-" + documentId + "-" + UUID.randomUUID() + ".pdf";
        try {
            stockage.ecrire(cle, contenuPdf, "application/pdf");
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Impossible d'enregistrer le PDF du document.", exception);
        }
        return cle;
    }

    public Optional<byte[]> lire(String cheminStocke) throws IOException {
        return stockage.lire(verifierChemin(cheminStocke));
    }

    public void supprimer(String cheminStocke) throws IOException {
        stockage.supprimer(verifierChemin(cheminStocke));
    }

    private String verifierChemin(String cheminStocke) {
        if (cheminStocke == null || !cheminStocke.startsWith("documents/")
                || !NOM_FICHIER_PDF.matcher(cheminStocke.substring("documents/".length())).matches()) {
            throw new IllegalArgumentException(
                    "Le chemin du PDF stocké est invalide."
            );
        }
        return cheminStocke;
    }

    private boolean estPdf(byte[] contenu) {
        if (contenu == null || contenu.length < 5) {
            return false;
        }

        return "%PDF-".equals(
                new String(
                        contenu,
                        0,
                        5,
                        StandardCharsets.US_ASCII
                )
        );
    }
}
