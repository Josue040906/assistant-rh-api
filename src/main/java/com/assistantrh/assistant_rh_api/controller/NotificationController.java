        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Map<String, Object>> getNotifications(
            @RequestParam Integer userId
    ) {
        return notificationService.getNotifications(userId);
    }

    @GetMapping("/non-lues")
    public Map<String, Object> compterNonLues(
            @RequestParam Integer userId
    ) {
        long nombre =
                notificationService.compterNonLues(userId);

        return Map.of(
                "nombre", nombre
        );
    }

    @PutMapping("/{id}/lue")
    public ResponseEntity<Map<String, Object>> marquerCommeLue(
            @PathVariable Integer id,
            @RequestParam Integer userId
    ) {

        notificationService.marquerCommeLue(
                id,
                userId
        );

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Notification marquée comme lue."
                )
        );
    }

    @PutMapping("/lues")
    public ResponseEntity<Map<String, Object>> marquerToutesCommeLues(
            @RequestParam Integer userId
    ) {

        notificationService.marquerToutesCommeLues(
                userId
        );

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Toutes les notifications ont été marquées comme lues."
                )
        );
    }
}
