package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.pdf.PieceScanneeStorage;
import com.assistantrh.assistant_rh_api.repository.PieceScanneeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pièces scannées d'où viennent un document ou un diplôme. Les droits sont
 * décidés comme pour les documents et les diplômes eux-mêmes : par
 * l'intercepteur d'authentification (compte actif du Service du Personnel).
 */
@Service
public class PieceScanneeService {
    private static final Set<String> CIBLES = Set.of("DOCUMENT", "DIPLOME");

    private final PieceScanneeRepository repository;
    private final PieceScanneeStorage storage;

    public PieceScanneeService(PieceScanneeRepository repository, PieceScanneeStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /** Une pièce lue : son nom, son type et son contenu. */
    public record Contenu(String nomFichier, String typeMime, byte[] octets) {
    }

    @Transactional
    public Map<String, Object> deposer(
            Integer acteurId, String cibleType, Integer cibleId, String nomFichier, byte[] contenu
    ) {
        String type = verifierCible(cibleType, cibleId);
        String nom = nomFichier == null || nomFichier.isBlank()
                ? "piece-scannee"
                : nomFichier.strip().replaceAll("[\\p{Cntrl}\\\\/]", "_");
        if (nom.length() > 200) {
            nom = nom.substring(0, 200);
        }
        PieceScanneeStorage.Fichier fichier = storage.enregistrer(contenu);
        try {
            Integer id = repository.ajouter(type, cibleId, nom, fichier.typeMime(),
                    contenu.length, fichier.chemin(), acteurId);
            return Map.of("id", id, "nomFichier", nom, "typeMime", fichier.typeMime(), "taille", contenu.length);
        } catch (RuntimeException echec) {
            // Pas de fichier orphelin si l'enregistrement en base échoue.
            try {
                storage.supprimer(fichier.chemin());
            } catch (IOException ignored) {
                // Le refus d'origine reste le plus utile.
            }
            throw echec;
        }
    }

    public List<Map<String, Object>> lister(String cibleType, Integer cibleId) {
        return repository.lister(verifierCible(cibleType, cibleId), cibleId);
    }

    public Optional<Contenu> lire(Integer id) throws IOException {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("L'identifiant de la pièce est invalide.");
        }
        Optional<Map<String, Object>> piece = repository.trouver(id);
        if (piece.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> ligne = piece.get();
        return storage.lire(String.valueOf(ligne.get("chemin")))
                .map(octets -> new Contenu(String.valueOf(ligne.get("nom_fichier")),
                        String.valueOf(ligne.get("type_mime")), octets));
    }

    private String verifierCible(String cibleType, Integer cibleId) {
        String type = cibleType == null ? "" : cibleType.trim().toUpperCase(Locale.ROOT);
        if (!CIBLES.contains(type)) {
            throw new IllegalArgumentException("La pièce s'attache à un DOCUMENT ou à un DIPLOME.");
        }
        if (cibleId == null || cibleId <= 0 || !repository.cibleExiste(type, cibleId)) {
            throw new IllegalArgumentException(
                    type.equals("DOCUMENT") ? "Le document n'existe pas." : "Le diplôme n'existe pas."
            );
        }
        return type;
    }
}
