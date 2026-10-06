package com.assistantrh.assistant_rh_api.pdf;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class PdfDocumentStorage {

    private static final Pattern NOM_FICHIER_PDF =
            Pattern.compile(
                    "document-[1-9][0-9]*-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.pdf"
            );

    private final Path racineUploads;

    public PdfDocumentStorage() {
        this(Path.of("uploads"));
    }

    PdfDocumentStorage(Path racineUploads) {
        this.racineUploads = Objects.requireNonNull(
                racineUploads,
                "Le répertoire de stockage est obligatoire."
        ).toAbsolutePath().normalize();
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

        String nomFichier = "document-"
                + documentId
                + "-"
                + UUID.randomUUID()
                + ".pdf";
        Path dossierDocuments = racineUploads.resolve("documents");
        Path destination = dossierDocuments.resolve(nomFichier);

        try {
            Files.createDirectories(dossierDocuments);
            Files.write(
                    destination,
                    contenuPdf,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible d'enregistrer le PDF du document.",
                    exception
            );
        }

        return "documents/" + nomFichier;
    }

    public Optional<byte[]> lire(String cheminStocke) throws IOException {
        Path fichier = resoudreChemin(cheminStocke);
        if (!Files.isRegularFile(fichier, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }

        return Optional.of(Files.readAllBytes(fichier));
    }

    public void supprimer(String cheminStocke) throws IOException {
        Files.deleteIfExists(resoudreChemin(cheminStocke));
    }

    private Path resoudreChemin(String cheminStocke) {
        if (cheminStocke == null || !cheminStocke.startsWith("documents/")) {
            throw new IllegalArgumentException(
                    "Le chemin du PDF stocké est invalide."
            );
        }

        String nomFichier = cheminStocke.substring("documents/".length());
        if (!NOM_FICHIER_PDF.matcher(nomFichier).matches()) {
            throw new IllegalArgumentException(
                    "Le chemin du PDF stocké est invalide."
            );
        }

        Path dossierDocuments =
                racineUploads.resolve("documents").normalize();
        Path fichier = dossierDocuments.resolve(nomFichier).normalize();
        if (!fichier.startsWith(dossierDocuments)) {
            throw new IllegalArgumentException(
                    "Le chemin du PDF stocké sort du répertoire autorisé."
            );
        }

        return fichier;
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
