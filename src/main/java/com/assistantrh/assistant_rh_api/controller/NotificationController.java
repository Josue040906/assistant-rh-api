package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Notification;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Map<String, Object> lister(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer destinataireId,
            @RequestParam(defaultValue = "false") boolean nonLuesSeulement,
            @RequestParam(defaultValue = "50") int limite
    ) {
        List<Notification> notifications =
                notificationService.listerMesNotifications(
                        destinataireId,
                        nonLuesSeulement,
                        limite
                );
        return Map.of(
                "notifications", notifications,
                "nonLues", notificationService
                        .compterMesNotificationsNonLues(destinataireId)
        );
    }

    @PatchMapping("/{id:\\d+}/lue")
    public ResponseEntity<Void> marquerLue(
            @PathVariable long id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer destinataireId
    ) {
        return notificationService.marquerLue(id, destinataireId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/lues")
    public Map<String, Integer> marquerToutesLues(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer destinataireId
    ) {
        return Map.of(
                "modifiees",
                notificationService.marquerToutesLues(destinataireId)
        );
    }
}
