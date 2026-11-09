package com.assistantrh.assistant_rh_api.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PieceScanneeStorageTests {

    @TempDir
    Path racine;

    @Test
    void enregistreUnePhotoOuUnPdfSelonSonContenuEtLaRelit() throws Exception {
        PieceScanneeStorage storage = new PieceScanneeStorage(racine);
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

        PieceScanneeStorage.Fichier fichier = storage.enregistrer(jpeg);

        assertEquals("image/jpeg", fichier.typeMime());
        assertTrue(fichier.chemin().matches("pieces/piece-[0-9a-f-]{36}\\.jpg"));
        assertArrayEquals(jpeg, storage.lire(fichier.chemin()).orElseThrow());
        assertEquals("application/pdf",
                storage.enregistrer("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII)).typeMime());
        assertEquals("image/webp",
                storage.enregistrer("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)).typeMime());

        storage.supprimer(fichier.chemin());
        assertFalse(Files.exists(racine.resolve(fichier.chemin())));
    }

    @Test
    void refuseCeQuiNEstNiImageNiPdfLesFichiersTropLourdsEtLesCheminsDouteux() {
        PieceScanneeStorage storage = new PieceScanneeStorage(racine);

        assertThrows(IllegalArgumentException.class,
                () -> storage.enregistrer("MZ executable".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> storage.enregistrer(new byte[0]));
        byte[] tropLourd = new byte[PieceScanneeStorage.TAILLE_MAX + 1];
        tropLourd[0] = '%'; tropLourd[1] = 'P'; tropLourd[2] = 'D'; tropLourd[3] = 'F'; tropLourd[4] = '-';
        assertThrows(IllegalArgumentException.class, () -> storage.enregistrer(tropLourd));
        assertThrows(IllegalArgumentException.class, () -> storage.lire("pieces/../../secret.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.lire("documents/document-1-x.pdf"));
    }
}
