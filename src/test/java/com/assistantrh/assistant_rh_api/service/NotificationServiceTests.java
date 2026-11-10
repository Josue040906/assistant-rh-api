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
    void leTraitementQuotidienDelegueLaDetectionDesAnciennetes() {
        service.verifierAnciennete();

        verify(repository).creerNotificationsAnciennete();
    }

    @Test
    void neSupprimeQueLaNotificationDeSonDestinataire() {
        org.mockito.Mockito.when(repository.supprimer(5L, 42)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertTrue(service.supprimer(5L, 42));
        org.junit.jupiter.api.Assertions.assertFalse(service.supprimer(5L, 7));
        assertThrows(IllegalArgumentException.class, () -> service.supprimer(0L, 42));
        verify(repository).supprimer(5L, 42);
    }
}
