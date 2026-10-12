package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.LocaliteRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeServiceTests {

    private final EmployeRepository employeRepository =
            mock(EmployeRepository.class);
    private final AffectationRepository affectationRepository =
            mock(AffectationRepository.class);
    private final ActiviteService activiteService =
            mock(ActiviteService.class);
    private final UtilisateurRepository utilisateurRepository =
            mock(UtilisateurRepository.class);
    private final LocaliteRepository localiteRepository =
            mock(LocaliteRepository.class);
    private final LocaliteService localiteService =
            new LocaliteService(localiteRepository);
    private final TelephoneService telephoneService =
            new TelephoneService("MG");
    private final EmployeService service = new EmployeService(
            employeRepository,
            affectationRepository,
            activiteService,
            utilisateurRepository,
            localiteService,
            telephoneService
    );

    @Test
    void profilPersonnelPeutConserverSonPropreCin() {
        when(employeRepository.findByUserId(9))
                .thenReturn(Optional.of(Map.of("id", 42, "matricule", "AG-42")));
        when(employeRepository.existsByCinAndIdNot("CIN-42", 42))
                .thenReturn(false);
        when(employeRepository.update(
                42, "Nom", "Prenom", "F", "Adresse", "CIN-42",
                "+261341234567", Date.valueOf(LocalDate.of(1990, 1, 2)), "Ville",
                null, false
        )).thenReturn(1);

        service.modifierProfil(
                9,
                " Nom ",
                " Prenom ",
                "F",
                " CIN-42 ",
                LocalDate.of(1990, 1, 2),
                "Ville",
                "Adresse",
                "034 12 345 67",
                null,
                false
        );

        verify(employeRepository).existsByCinAndIdNot("CIN-42", 42);
        verify(activiteService).enregistrer(
                9, 42, "MODIFICATION_PROFIL",
                "Modification des informations personnelles de l'agent AG-42"
        );
    }

    @Test
    void enregistreLocaliteValideeEtTelephoneNormaliseDansLeProfil() {
        when(employeRepository.findByUserId(9))
                .thenReturn(Optional.of(Map.of("id", 42, "matricule", "AG-42")));
        when(localiteRepository.existsSelectableById(500L)).thenReturn(true);
        when(employeRepository.existsByCinAndIdNot("CIN-42", 42))
                .thenReturn(false);
        when(employeRepository.update(
                42, "Nom", "Prenom", null, null, "CIN-42",
                "+261341234567", null, null, 500L, false
        )).thenReturn(1);

        service.modifierProfil(
                9, "Nom", "Prenom", null, "CIN-42",
                null, null, null, "0341234567", 500L, false
        );

        verify(localiteRepository).existsSelectableById(500L);
        verify(employeRepository).update(
                42, "Nom", "Prenom", null, null, "CIN-42",
                "+261341234567", null, null, 500L, false
        );
    }

    @Test
    void rejetteUnTelephoneInvalideAvantDeModifierLeProfil() {
        when(employeRepository.findByUserId(9))
                .thenReturn(Optional.of(Map.of("id", 42, "matricule", "AG-42")));

        assertThrows(
                TelephoneInvalideException.class,
                () -> service.modifierProfil(
                        9, "Nom", "Prenom", null, null,
                        null, null, null, "034 1", null, false
                )
        );

        verify(employeRepository, never()).update(
                eq(42), eq("Nom"), eq("Prenom"), eq(null), eq(null),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(false)
        );
    }

    @Test
    void profilPersonnelRefuseUnCinDejaAttribue() {
        when(employeRepository.findByUserId(9))
                .thenReturn(Optional.of(Map.of("id", 42, "matricule", "AG-42")));
        when(employeRepository.existsByCinAndIdNot("CIN-7", 42))
                .thenReturn(true);

        assertThrows(
                CinDejaUtiliseException.class,
                () -> service.modifierProfil(
                        9,
                        "Nom",
                        "Prenom",
                        null,
                        "CIN-7",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false
                )
        );

        verify(employeRepository, never()).update(
                eq(42), eq("Nom"), eq("Prenom"), eq(null), eq(null),
                eq("CIN-7"), eq(null), eq(null), eq(null), eq(null),
                eq(false)
        );
    }

    @Test
    void agentDuServicePersonnelUtiliseLaMemeValidationCin() {
        when(utilisateurRepository.estAgentSpersActif(3)).thenReturn(true);
        when(employeRepository.findById(42))
                .thenReturn(Optional.of(Map.of("id", 42, "matricule", "AG-42")));
        when(employeRepository.existsByCinAndIdNot("CIN-7", 42))
                .thenReturn(true);

        assertThrows(
                CinDejaUtiliseException.class,
                () -> service.modifierEmploye(
                        42, 3, "Nom", "Prenom", null,
                        null, "CIN-7", null, null, null, null, false
                )
        );

        verify(employeRepository, never()).update(
                eq(42), eq("Nom"), eq("Prenom"), eq(null), eq(null),
                eq("CIN-7"), eq(null), eq(null), eq(null), eq(null),
                eq(false)
        );
    }

    @Test
    void convertitUneCollisionCinEnBaseEnErreurMetier() {
        when(employeRepository.findByUserId(9))
                .thenReturn(Optional.of(Map.of("id", 42, "matricule", "AG-42")));
        when(employeRepository.existsByCinAndIdNot("CIN-42", 42))
                .thenReturn(false);
        when(employeRepository.update(
                42, "Nom", "Prenom", null, null, "CIN-42",
                null, null, null, null, false
        )).thenThrow(new DuplicateKeyException("unique cin"));

        assertThrows(
                CinDejaUtiliseException.class,
                () -> service.modifierProfil(
                        9, "Nom", "Prenom", null, "CIN-42",
                        null, null, null, null, null, false
                )
        );
    }
}
