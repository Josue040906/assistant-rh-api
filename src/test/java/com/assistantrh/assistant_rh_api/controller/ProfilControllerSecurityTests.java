package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.EmployeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfilControllerSecurityTests {

    private final EmployeService employeService = mock(EmployeService.class);
    private final ProfilController controller = new ProfilController(employeService);

    @Test
    void refuseLaConsultationDuProfilDUnAutreUtilisateur() {
        var response = controller.getProfil(42, 99);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(employeService);
    }

    @Test
    void refuseLaModificationDuProfilDUnAutreUtilisateur() {
        var request = new ProfilController.ProfilUpdateRequest(
                99, "NOM", "Prenom", null, null, null, null,
                null, null, null, false
        );

        var response = controller.modifierProfil(42, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(employeService);
    }

    @Test
    void refuseLeTeleversementDePhotoPourUnAutreUtilisateur() {
        var file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1, 2, 3}
        );

        var response = controller.uploaderPhotoProfil(42, 99, file);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(employeService);
    }

    @Test
    void utiliseLIdentiteAuthentifieePourLireSonProfil() {
        when(employeService.getProfilByUserId(42))
                .thenReturn(Optional.of(Map.of("userId", 42)));

        var response = controller.getProfil(42, 42);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(42, response.getBody().get("userId"));
    }
}
