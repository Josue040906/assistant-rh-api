package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationServiceTests {

    private final NotificationRepository repository =
            mock(NotificationRepository.class);
    private final NotificationService service =
            new NotificationService(repository);

    @Test
    void valideLesBornesDeListeAvantLaLecture() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listerMesNotifications(42, false, 101)
        );
        verify(repository, org.mockito.Mockito.never())
                .findForRecipient(42, false, 101);
    }

    @Test
    void laSuppressionEstLimiteeAuDestinataireConnecte() {
        service.supprimer(7L, 42);

        verify(repository).supprimerPourDestinataire(7L, 42);
    }

    @Test
    void leTraitementQuotidienDelegueLaDetectionDesAnciennetes() {
        service.verifierAnciennete();

        verify(repository).creerNotificationsAnciennete();
    }
}
