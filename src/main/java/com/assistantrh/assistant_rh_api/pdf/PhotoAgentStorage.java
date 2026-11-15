package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.stockage.StockageFichiers;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Les photos des agents, rangées dans « employes/ » du stockage des fichiers.
 * Seules les images JPEG, PNG et WebP sont admises, reconnues à leur contenu.
 * Le lien enregistré pour l'agent reste « /uploads/employes/<nom> ».
 */
@Component
public class PhotoAgentStorage {

    public static final String PREFIXE_LIEN = "/uploads/employes/";
    private static final int TAILLE_MAX = 5 * 1024 * 1024;
    private static final Pattern NOM_PHOTO = Pattern.compile("agent-[0-9]+-[A-Za-z0-9-]+\\.(?:jpg|jpeg|png|webp)");

    private final StockageFichiers stockage;

    public PhotoAgentStorage(StockageFichiers stockage) {
        this.stockage = Objects.requireNonNull(stockage);
    }

    /** Enregistre la photo et renvoie son lien (« /uploads/employes/agent-… »). */
    public String enregistrer(Integer agentId, byte[] contenu) {
        if (contenu == null || contenu.length == 0 || contenu.length > TAILLE_MAX) {
            throw new IllegalArgumentException("La photo doit faire au plus 5 Mo.");
        }
        String typeMime = PieceScanneeStorage.typeReconnu(contenu)
                .filter(type -> type.startsWith("image/"))
                .orElseThrow(() -> new IllegalArgumentException("La photo doit être une image JPEG, PNG ou WebP."));
        String nom = "agent-" + agentId + "-" + UUID.randomUUID() + "." + extension(typeMime);
        stockage.ecrire("employes/" + nom, contenu, typeMime);
        return PREFIXE_LIEN + nom;
    }

    /** La photo d'après son nom de fichier, ou rien. */
    public Optional<byte[]> lire(String nom) {
        return stockage.lire("employes/" + verifierNom(nom));
    }

    /** Supprime la photo désignée par son lien, s'il en est un. */
    public void supprimer(String lien) {
        if (lien != null && lien.startsWith(PREFIXE_LIEN)) {
            stockage.supprimer("employes/" + verifierNom(lien.substring(PREFIXE_LIEN.length())));
        }
    }

    /** Le type de l'image d'après son contenu. */
    public static String typeMime(byte[] contenu) {
        return PieceScanneeStorage.typeReconnu(contenu).orElse("application/octet-stream");
    }

    private static String verifierNom(String nom) {
        if (nom == null || !NOM_PHOTO.matcher(nom).matches()) {
            throw new IllegalArgumentException("Le nom de la photo est invalide.");
        }
        return nom;
    }

    private static String extension(String typeMime) {
        return switch (typeMime) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
