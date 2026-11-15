package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.stockage.StockageFichiers;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fichiers des pièces scannées, rangés dans « pieces/ » du stockage des
 * fichiers : nom tiré au hasard, chemin contrôlé, et type reconnu à son
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

    private final StockageFichiers stockage;

    public PieceScanneeStorage(StockageFichiers stockage) {
        this.stockage = Objects.requireNonNull(stockage);
    }

    public Fichier enregistrer(byte[] contenu) {
        if (contenu == null || contenu.length == 0 || contenu.length > TAILLE_MAX) {
            throw new IllegalArgumentException("La pièce scannée doit faire au plus 4 Mo.");
        }
        String typeMime = typeReconnu(contenu)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La pièce scannée doit être une image JPEG, PNG, WebP ou un PDF."
                ));
        String cle = "pieces/piece-" + UUID.randomUUID() + "." + extension(typeMime);
        try {
            stockage.ecrire(cle, contenu, typeMime);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Impossible d'enregistrer la pièce scannée.", exception);
        }
        return new Fichier(cle, typeMime);
    }

    public Optional<byte[]> lire(String cheminStocke) throws IOException {
        return stockage.lire(verifierChemin(cheminStocke));
    }

    public void supprimer(String cheminStocke) throws IOException {
        stockage.supprimer(verifierChemin(cheminStocke));
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

    private String verifierChemin(String cheminStocke) {
        if (cheminStocke == null || !cheminStocke.startsWith("pieces/")
                || !NOM_FICHIER.matcher(cheminStocke.substring("pieces/".length())).matches()) {
            throw new IllegalArgumentException("Le chemin de la pièce scannée est invalide.");
        }
        return cheminStocke;
    }
}
