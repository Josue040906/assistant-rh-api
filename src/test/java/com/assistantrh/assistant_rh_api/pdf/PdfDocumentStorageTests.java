package com.assistantrh.assistant_rh_api.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentStorageTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void enregistreLitEtSupprimeUnPdfDansLeRepertoireConfigure()
            throws IOException {
        PdfDocumentStorage storage =
                new PdfDocumentStorage(temporaryDirectory);
        byte[] contenu = "%PDF-1.4\ncontenu".getBytes(StandardCharsets.US_ASCII);

        String chemin = storage.enregistrer(12, contenu);

        assertTrue(chemin.startsWith("documents/document-12-"));
        assertArrayEquals(contenu, storage.lire(chemin).orElseThrow());

        storage.supprimer(chemin);

        assertTrue(storage.lire(chemin).isEmpty());
    }

    @Test
    void refuseUnCheminQuiNestPasUnPdfGenereParLeService() throws IOException {
        PdfDocumentStorage storage =
                new PdfDocumentStorage(temporaryDirectory);

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.lire("documents/../../secret.pdf")
        );
        assertFalse(temporaryDirectory.resolve("secret.pdf").toFile().exists());
    }

    @Test
    void refuseUnContenuSansSignaturePdf() {
        PdfDocumentStorage storage =
                new PdfDocumentStorage(temporaryDirectory);

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.enregistrer(
                        12,
                        "not a pdf".getBytes(StandardCharsets.US_ASCII)
                )
        );
    }
}
