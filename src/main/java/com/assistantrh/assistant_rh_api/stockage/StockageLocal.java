package com.assistantrh.assistant_rh_api.stockage;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

/** Les fichiers sur le disque, sous un dossier racine (uploads/ par défaut). */
public final class StockageLocal implements StockageFichiers {

    private final Path racine;

    public StockageLocal(Path racine) {
        this.racine = Objects.requireNonNull(racine, "Le dossier de stockage est obligatoire.")
                .toAbsolutePath().normalize();
    }

    @Override
    public void ecrire(String cle, byte[] contenu, String typeMime) {
        Path fichier = chemin(cle);
        try {
            Files.createDirectories(fichier.getParent());
            Files.write(fichier, contenu, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException exception) {
            throw new IllegalStateException("Un fichier porte déjà ce nom.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible d'enregistrer le fichier.", exception);
        }
    }

    @Override
    public Optional<byte[]> lire(String cle) {
        Path fichier = chemin(cle);
        if (!Files.isRegularFile(fichier, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(fichier));
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de lire le fichier.", exception);
        }
    }

    @Override
    public void supprimer(String cle) {
        try {
            Files.deleteIfExists(chemin(cle));
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de supprimer le fichier.", exception);
        }
    }

    @Override
    public String description() {
        return "dossier local " + racine;
    }

    private Path chemin(String cle) {
        Path fichier = racine.resolve(StockageFichiers.verifierCle(cle)).normalize();
        if (!fichier.startsWith(racine)) {
            throw new IllegalArgumentException("Le chemin du fichier stocké sort du dossier autorisé.");
        }
        return fichier;
    }
}
