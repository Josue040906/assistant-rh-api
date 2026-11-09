package com.assistantrh.assistant_rh_api.pdf;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fichiers des pièces scannées, rangés dans uploads/pieces comme les PDF des
 * documents : nom tiré au hasard, chemin contrôlé, et type reconnu à son
 * contenu (jamais à ce que déclare le client).
 */
@Component
public class PieceScanneeStorage {

    public static final int TAILLE_MAX = 4 * 1024 * 1024;
    private static final Pattern NOM_FICHIER = Pattern.compile(
            "piece-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(?:jpg|png|webp|pdf)"
    );

    /** Le fichier enregistré : son chemin stocké et son type reconnu. */
    public record Fichier(String chemin, String typeMime) {
    }

    private final Path racineUploads;

    public PieceScanneeStorage() {
        this(Path.of("uploads"));
    }

    PieceScanneeStorage(Path racineUploads) {
        this.racineUploads = Objects.requireNonNull(racineUploads).toAbsolutePath().normalize();
    }

    public Fichier enregistrer(byte[] contenu) {
        if (contenu == null || contenu.length == 0 || contenu.length > TAILLE_MAX) {
            throw new IllegalArgumentException("La pièce scannée doit faire au plus 4 Mo.");
        }
        String typeMime = typeReconnu(contenu)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La pièce scannée doit être une image JPEG, PNG, WebP ou un PDF."
                ));
        String nomFichier = "piece-" + UUID.randomUUID() + "." + extension(typeMime);
        Path dossier = racineUploads.resolve("pieces");
        try {
            Files.createDirectories(dossier);
            Files.write(dossier.resolve(nomFichier), contenu,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible d'enregistrer la pièce scannée.", exception);
        }
        return new Fichier("pieces/" + nomFichier, typeMime);
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

    /** Le type d'après les premiers octets : JPEG, PNG, WebP ou PDF ; rien d'autre. */
    static Optional<String> typeReconnu(byte[] contenu) {
        if (commencePar(contenu, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of("image/jpeg");
        }
        if (commencePar(contenu, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of("image/png");
        }
        if (contenu.length >= 12 && commencePar(contenu, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && Arrays.equals(Arrays.copyOfRange(contenu, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of("image/webp");
        }
        if (commencePar(contenu, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of("application/pdf");
        }
        return Optional.empty();
    }

    private static boolean commencePar(byte[] contenu, byte[] debut) {
        return contenu.length >= debut.length
                && Arrays.equals(Arrays.copyOf(contenu, debut.length), debut);
    }

    private static String extension(String typeMime) {
        return switch (typeMime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "pdf";
        };
    }

    private Path resoudreChemin(String cheminStocke) {
        if (cheminStocke == null || !cheminStocke.startsWith("pieces/")
                || !NOM_FICHIER.matcher(cheminStocke.substring("pieces/".length())).matches()) {
            throw new IllegalArgumentException("Le chemin de la pièce scannée est invalide.");
        }
        Path dossier = racineUploads.resolve("pieces").normalize();
        Path fichier = dossier.resolve(cheminStocke.substring("pieces/".length())).normalize();
        if (!fichier.startsWith(dossier)) {
            throw new IllegalArgumentException("Le chemin de la pièce scannée sort du répertoire autorisé.");
        }
        return fichier;
    }
}
