package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Notification;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> listerMesNotifications(
            Integer destinataireId,
            boolean nonLuesSeulement,
            int limite
    ) {
        if (destinataireId == null || destinataireId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'utilisateur connecté est invalide."
            );
        }
        if (limite < 1 || limite > 100) {
            throw new IllegalArgumentException(
                    "La limite doit être comprise entre 1 et 100."
            );
        }
        return notificationRepository.findForRecipient(
                destinataireId,
                nonLuesSeulement,
                limite
        );
    }

    public long compterMesNotificationsNonLues(Integer destinataireId) {
        if (destinataireId == null || destinataireId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'utilisateur connecté est invalide."
            );
        }
        return notificationRepository.countUnread(destinataireId);
    }

    public boolean marquerLue(long notificationId, Integer destinataireId) {
        if (notificationId <= 0 || destinataireId == null || destinataireId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de notification ou d'utilisateur est invalide."
            );
        }
        return notificationRepository.marquerLue(
                notificationId,
                destinataireId
        );
    }

    public int marquerToutesLues(Integer destinataireId) {
        if (destinataireId == null || destinataireId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'utilisateur connecté est invalide."
            );
        }
        return notificationRepository.marquerToutesLues(destinataireId);
    }

    @Scheduled(
            cron = "${notifications.anciennete.cron:0 0 8 * * *}",
            zone = "${notifications.zone:Indian/Antananarivo}"
    )
    public void verifierAnciennete() {
        notificationRepository.creerNotificationsAnciennete();
    }
}
