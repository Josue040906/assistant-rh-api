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
        LocalDateTime lueLe
) {
}
