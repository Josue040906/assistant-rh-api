package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.pdf.PhotoAgentStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Les photos des agents, lues dans le stockage des fichiers (dossier local ou
 * Supabase). L'accès reste réservé aux utilisateurs connectés : l'intercepteur
 * d'authentification protège /uploads/employes/**.
 */
@RestController
public class PhotoAgentController {

    private final PhotoAgentStorage photos;

    public PhotoAgentController(PhotoAgentStorage photos) {
        this.photos = photos;
    }

    @GetMapping("/uploads/employes/{nom:.+}")
    public ResponseEntity<byte[]> photo(@PathVariable String nom) {
        try {
            return photos.lire(nom)
                    .map(contenu -> ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(PhotoAgentStorage.typeMime(contenu)))
                            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                            .header("X-Content-Type-Options", "nosniff")
                            .body(contenu))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException nomInvalide) {
            return ResponseEntity.notFound().build();
        }
    }
}
