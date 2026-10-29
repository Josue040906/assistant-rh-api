package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.ActiviteService;
import com.assistantrh.assistant_rh_api.service.EmployeService;
import com.assistantrh.assistant_rh_api.service.UtilisateurService;
import com.assistantrh.assistant_rh_api.security.BearerTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticatedUserOwnershipTests {

    @Test
    void profileReadUsesAuthenticatedUserInsteadOfQueryParameter() {
        EmployeService service = mock(EmployeService.class);
        ProfilController controller = new ProfilController(service);
        when(service.getProfilByUserId(42)).thenReturn(
                Optional.of(Map.of("userId", 42))
        );

        ResponseEntity<Map<String, Object>> response =
                controller.getProfil(42);

        assertEquals(200, response.getStatusCode().value());
        verify(service).getProfilByUserId(42);
    }

    @Test
    void profileUpdateIgnoresUserIdSuppliedInRequestBody() {
        EmployeService service = mock(EmployeService.class);
        ProfilController controller = new ProfilController(service);
        ProfilController.ProfilUpdateRequest body =
                new ProfilController.ProfilUpdateRequest(
                        99, "Nom", "Prenom", "F", null, null, null,
                        null, null, null, null
                );

        ResponseEntity<Map<String, Object>> response =
                controller.modifierProfil(42, body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(42, response.getBody().get("userId"));
        verify(service).modifierProfil(
                eq(42), eq("Nom"), eq("Prenom"), eq("F"), eq(null),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(false)
        );
    }

    @Test
    void passwordChangeUsesAuthenticatedUserInsteadOfBodyUserId() {
        UtilisateurService users = mock(UtilisateurService.class);
        UtilisateurController controller = new UtilisateurController(
                users,
                mock(ActiviteService.class),
                mock(BearerTokenService.class)
        );
        when(users.changerMotDePasse(42, "ancien", "nouveau"))
                .thenReturn(true);

        ResponseEntity<Map<String, Object>> response =
                controller.changerMotDePasse(
                        42,
                        new UtilisateurController.ChangementMotDePasseRequest(
                                99, "ancien", "nouveau"
                        )
                );

        assertEquals(200, response.getStatusCode().value());
        verify(users).changerMotDePasse(42, "ancien", "nouveau");
    }
}
