package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Activite;
import com.assistantrh.assistant_rh_api.repository.ActiviteRepository;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
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

        verify(repository).enregistrer(
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
    void neRetourneLesActivitesQuePourUnActeurValide() {
        when(repository.findByActeurId(42)).thenReturn(List.of(new Activite(
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

        service.listerParActeur(42);

        verify(repository).findByActeurId(42);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listerParActeur(null)
        );
    }
}
