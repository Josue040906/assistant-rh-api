package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Activite;
import com.assistantrh.assistant_rh_api.repository.ActiviteRepository;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiviteServiceTests {

    private final ActiviteRepository repository =
            mock(ActiviteRepository.class);
    private final ActiviteService service = new ActiviteService(repository);

    @AfterEach
    void nettoyerContexteRequete() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void utiliseToujoursLActeurAuthentifiePlutotQueCeluiFourni() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE,
                42
        );
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );

        service.enregistrer(
                5,
                12,
                "MODIFICATION_AGENT",
                "Profil modifié"
        );

        verify(repository).enregistrerEtRetournerId(
                42,
                12,
                "MODIFICATION",
                "Profil modifié"
        );
    }

    @Test
    void refuseLesTypesActionNonStandardises() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.enregistrer(5, null, "OUVERTURE_PAGE", "Accueil")
        );
    }

    @Test
    void neRetourneQueLesActivitesDeLUtilisateurAuthentifie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE,
                42
        );
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
        when(repository.findByActeurId(42, null, 0)).thenReturn(List.of(new Activite(
                1L,
                42,
                null,
                "CONNEXION",
                "Connexion",
                LocalDateTime.now(),
                "agent@example.test",
                null,
                null,
                null
        )));

        // Sans pagination, toute la liste, comme avant.
        service.listerMesActivites(null, null);

        verify(repository).findByActeurId(42, null, 0);

        // Avec pagination, seule la page demandée.
        service.listerMesActivites(10, 20);
        verify(repository).findByActeurId(42, 10, 20);

        for (Integer[] invalide : new Integer[][]{{0, null}, {101, null}, {10, -1}}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.listerMesActivites(invalide[0], invalide[1])
            );
        }
    }

    @Test
    void refuseDeListerLesActivitesSansUtilisateurAuthentifie() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listerMesActivites(null, null)
        );
    }

    /** Chaque type passé par le code doit être accepté : un refus ferait échouer toute l'opération. */
    @Test
    void accepteChaqueTypeDActiviteUtiliseParLApplication() {
        for (String type : List.of(
                "CREATION", "MODIFICATION", "VALIDATION", "GENERATION",
                "CREATION_AGENT", "CREATION_COMPTE", "APPROBATION_COMPTE", "REFUS_COMPTE",
                "MODIFICATION_AFFECTATION", "APPLICATION_DOCUMENT", "ANNULATION_DOCUMENT",
                "ARCHIVAGE_DOCUMENT", "CONGE", "RETRAITE", "TITULARISATION")) {
            service.enregistrer(5, 12, type, "Activité " + type);
        }
        verify(repository, org.mockito.Mockito.times(15))
                .enregistrerEtRetournerId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
