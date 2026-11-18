package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Notification;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    /** Les brouillons d'e-mail à l'agent (facultatif : absent dans les tests unitaires). */
    private MessageAgentService messages;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMessages(MessageAgentService messages) {
        this.messages = messages;
    }

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * L'e-mail à l'agent concerné par cette notification : celui déjà préparé pour
     * la situation, sinon un brouillon rédigé à partir de la notification. Rien ne
     * part : l'e-mail s'ouvre pour être relu.
     */
    public java.util.Map<String, Object> emailPourNotification(long notificationId, Integer destinataireId) {
        java.util.Map<String, Object> notification = notificationRepository
                .pourDestinataire(notificationId, destinataireId)
                .orElseThrow(() -> new IllegalArgumentException("Cette notification n'existe pas."));
        if (notification.get("employe_id") == null) {
            throw new IllegalArgumentException("Cette notification ne concerne aucun agent.");
        }
        if (messages == null) {
            throw new IllegalStateException("Les e-mails aux agents ne sont pas disponibles.");
        }
        return messages.brouillonPourNotification(notification, destinataireId);
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

    public boolean supprimer(long notificationId, Integer destinataireId) {
        if (notificationId <= 0 || destinataireId == null || destinataireId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de notification ou d'utilisateur est invalide."
            );
        }
        return notificationRepository.supprimer(notificationId, destinataireId);
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
        notificationRepository.creerNotificationsRetraiteProche();
        notificationRepository.creerNotificationsDemandesApplicables();
        if (messages != null) {
            messages.proposerEcheances();
        }
    }
}
