package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDateTime;

public record Notification(
        long id,
        String type,
        String titre,
        String message,
        Integer employeId,
        Integer acteurId,
        LocalDateTime creeLe,
        LocalDateTime lueLe,
        // Le brouillon d'e-mail qui attend une décision pour l'agent concerné, s'il y en a un.
        Long messageId,
        // Prénom et nom de l'agent concerné : l'interface en fait un lien vers son profil.
        String employeNom
) {
    public Notification(long id, String type, String titre, String message, Integer employeId,
                        Integer acteurId, LocalDateTime creeLe, LocalDateTime lueLe) {
        this(id, type, titre, message, employeId, acteurId, creeLe, lueLe, null, null);
    }

    public Notification(long id, String type, String titre, String message, Integer employeId,
                        Integer acteurId, LocalDateTime creeLe, LocalDateTime lueLe, Long messageId) {
        this(id, type, titre, message, employeId, acteurId, creeLe, lueLe, messageId, null);
    }
}
