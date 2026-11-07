package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.IdempotenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rend une écriture rejouable sans double effet. Avec un en-tête
 * Idempotency-Key, la même requête renvoie la même réponse sans réécrire ;
 * sans en-tête, l'écriture s'exécute normalement.
 */
@Service
public class IdempotenceService {

    private static final int LONGUEUR_MAX_CLE = 100;

    private final IdempotenceRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotenceService(IdempotenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<?> executer(
            Integer utilisateurId,
            String cle,
            String route,
            Object corps,
            Supplier<ResponseEntity<?>> ecriture
    ) {
        if (cle == null || cle.isBlank()) {
            return ecriture.get();
        }
        String cleNormalisee = cle.trim();
        if (cleNormalisee.length() > LONGUEUR_MAX_CLE) {
            return refus(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "L'en-tête Idempotency-Key dépasse " + LONGUEUR_MAX_CLE + " caractères.");
        }
        String empreinte = empreinte(route, corps);

        if (!repository.reserver(utilisateurId, cleNormalisee, route, empreinte)) {
            return rejeu(utilisateurId, cleNormalisee, empreinte);
        }

        ResponseEntity<?> reponse;
        try {
            reponse = ecriture.get();
        } catch (RuntimeException echec) {
            repository.liberer(utilisateurId, cleNormalisee);
            throw echec;
        }
        if (reponse.getStatusCode().is2xxSuccessful()) {
            repository.terminer(utilisateurId, cleNormalisee,
                    reponse.getStatusCode().value(), objectMapper.writeValueAsString(reponse.getBody()));
        } else {
            // Un refus n'est pas mémorisé : la requête corrigée pourra réutiliser la clé.
            repository.liberer(utilisateurId, cleNormalisee);
        }
        return reponse;
    }

    private ResponseEntity<?> rejeu(Integer utilisateurId, String cle, String empreinte) {
        IdempotenceRepository.Enregistrement enregistrement =
                repository.trouver(utilisateurId, cle).orElse(null);
        if (enregistrement == null) {
            // La réservation concurrente vient d'être libérée après un échec.
            return refus(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_RETRY",
                    "Cette requête vient d'échouer ; réessayez.");
        }
        if (!enregistrement.empreinte().equals(empreinte)) {
            return refus(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Cette clé Idempotency-Key a déjà servi pour une autre requête.");
        }
        if (enregistrement.statutHttp() == null) {
            return refus(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_IN_PROGRESS",
                    "Cette requête est déjà en cours de traitement.");
        }
        // Même requête déjà réussie : même réponse, aucune nouvelle écriture.
        return ResponseEntity.status(enregistrement.statutHttp())
                .body(objectMapper.readValue(enregistrement.reponse(), Object.class));
    }

    private String empreinte(String route, Object corps) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(
                    (route + "\n" + objectMapper.writeValueAsString(corps))
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible.", impossible);
        }
    }

    private static ResponseEntity<?> refus(HttpStatus statut, String code, String message) {
        return ResponseEntity.status(statut).body(Map.of("code", code, "message", message));
    }
}
