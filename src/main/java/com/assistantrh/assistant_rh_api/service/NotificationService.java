        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository
    ) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Récupère toutes les notifications d'un utilisateur.
     */
    public List<Map<String, Object>> getNotifications(
            Integer userId
    ) {

        verifierUserId(userId);

        return notificationRepository.findByUserId(userId);
    }

    /**
     * Compte les notifications non lues.
     */
    public long compterNonLues(Integer userId) {

        verifierUserId(userId);

        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Marque une notification comme lue.
     */
    public void marquerCommeLue(
            Integer notificationId,
            Integer userId
    ) {

        verifierUserId(userId);

        if (notificationId == null || notificationId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de la notification est invalide."
            );
        }

        int lignesModifiees =
                notificationRepository.markAsRead(
                        notificationId,
                        userId
                );

        if (lignesModifiees == 0) {
            throw new IllegalArgumentException(
                    "La notification n'existe pas ou "
                            + "n'appartient pas à cet utilisateur."
            );
        }
    }

    /**
     * Marque toutes les notifications d'un utilisateur comme lues.
     */
    public void marquerToutesCommeLues(Integer userId) {

        verifierUserId(userId);

        notificationRepository.markAllAsRead(userId);
    }

    /**
     * Notifie tous les utilisateurs lorsqu'un nouvel agent est créé.
     */
    public void notifierNouvelAgent(
            String prenom,
            String nom
    ) {

        String nomComplet = construireNomComplet(
                prenom,
                nom
        );

        String titre = "Nouvel agent ajouté";

        String message =
                nomComplet
                        + " a été ajouté au système.";

        notifierTousLesUtilisateurs(
                titre,
                message
        );
    }

    /**
     * Notifie tous les utilisateurs lorsqu'un nouveau document est créé.
     */
    public void notifierNouveauDocument(
            String reference,
            String objet
    ) {

        String titre = "Nouveau document";

        String message;

        if (objet == null || objet.isBlank()) {
            message =
                    "Un nouveau document RH a été ajouté"
                            + (reference != null && !reference.isBlank()
                            ? " : " + reference
                            : ".");
        } else {
            message =
                    "Un nouveau document RH a été ajouté : "
                            + objet.trim()
                            + (reference != null && !reference.isBlank()
                            ? " (" + reference + ")."
                            : ".");
        }

        notifierTousLesUtilisateurs(
                titre,
                message
        );
    }

    /**
     * Crée la même notification pour chaque utilisateur.
     */
    private void notifierTousLesUtilisateurs(
            String titre,
            String message
    ) {

        List<Integer> userIds =
                notificationRepository.findAllUserIds();

        for (Integer userId : userIds) {

            notificationRepository.create(
                    userId,
                    titre,
                    message
            );
        }
    }

    private String construireNomComplet(
            String prenom,
            String nom
    ) {

        String prenomNormalise =
                prenom != null ? prenom.trim() : "";

        String nomNormalise =
                nom != null ? nom.trim() : "";

        String nomComplet =
                (prenomNormalise + " " + nomNormalise).trim();

        if (nomComplet.isBlank()) {
            return "Un nouvel agent";
        }

        return nomComplet;
    }

    private void verifierUserId(Integer userId) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'utilisateur est invalide."
            );
        }
    }
}
