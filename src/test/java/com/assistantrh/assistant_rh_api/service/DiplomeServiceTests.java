package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiplomeServiceTests {

    private final DiplomeRepository diplomeRepository = mock(DiplomeRepository.class);
    private final EmployeRepository employeRepository = mock(EmployeRepository.class);
    private final UtilisateurRepository utilisateurRepository =
            mock(UtilisateurRepository.class);
    private final ActiviteService activiteService = mock(ActiviteService.class);
    private final DiplomeService service = new DiplomeService(
            diplomeRepository,
            employeRepository,
            utilisateurRepository,
            activiteService
    );

    @Test
    void ajouteUnDiplomeSansDeclencherDeMiseAJourDeCarriere() {
        LocalDate dateObtention = LocalDate.now().minusYears(1);
        when(employeRepository.findById(9))
                .thenReturn(Optional.of(Map.of("matricule", "AG-9")));
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(diplomeRepository.verrouillerEmploye(9)).thenReturn(true);
        when(diplomeRepository.findOffreFormulaire(2, 3, 5))
                .thenReturn(Optional.of(Map.of(
                        "niveau", "Licence",
                        "domaine", "Gestion",
                        "etablissement", "Université"
                )));
        when(diplomeRepository.findMentionFormulaire(6))
                .thenReturn(Optional.of("Bien"));
        when(diplomeRepository.ajouter(
                eq(9), eq(2), eq(3), eq(5),
                eq(java.sql.Date.valueOf(dateObtention)), eq(6), eq(null)
        )).thenReturn(1);

        service.ajouterDiplome(
                9, 4, 2, 3, 5, dateObtention, 6, null
        );

        verify(activiteService).enregistrer(
                eq(4), eq(9), eq("CREATION"), any()
        );
        verify(employeRepository, never()).updateCategorie(any(), any(), any());
    }

    @Test
    void rejetteLaDateFutureAvantTouteEcriture() {
        when(employeRepository.findById(9))
                .thenReturn(Optional.of(Map.of("matricule", "AG-9")));
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ajouterDiplome(
                        9, 4, 2, 3, 5, LocalDate.now().plusDays(1),
                        null, null
                )
        );
        verify(diplomeRepository, never()).ajouter(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void refuseUnEtablissementHorsOffrePourLeNiveauEtLeDomaine() {
        when(employeRepository.findById(9))
                .thenReturn(Optional.of(Map.of("matricule", "AG-9")));
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(diplomeRepository.verrouillerEmploye(9)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ajouterDiplome(
                        9, 4, 2, 3, 5, LocalDate.now().minusYears(1),
                        null, null
                )
        );
        verify(diplomeRepository, never()).ajouter(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void enregistreUneDemandeDEtablissementEnAttenteDeValidation() {
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(diplomeRepository.ajouterDemandeEtablissement(
                "Lycée B", "Antananarivo"
        )).thenReturn(1);

        service.demanderAjoutEtablissement(4, "  Lycée B ", " Antananarivo ");

        verify(diplomeRepository).ajouterDemandeEtablissement(
                "Lycée B", "Antananarivo"
        );
    }

    @Test
    void signaleUnDoublonExactSansLeMasquer() {
        LocalDate dateObtention = LocalDate.now().minusYears(1);
        when(employeRepository.findById(9))
                .thenReturn(Optional.of(Map.of("matricule", "AG-9")));
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(diplomeRepository.verrouillerEmploye(9)).thenReturn(true);
        when(diplomeRepository.findOffreFormulaire(2, 3, 5))
                .thenReturn(Optional.of(Map.of(
                        "niveau", "Licence",
                        "domaine", "Gestion",
                        "etablissement", "Université"
                )));
        when(diplomeRepository.ajouter(
                any(), any(), any(), any(), any(), any(), any()
        )).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(
                DiplomeDejaEnregistreException.class,
                () -> service.ajouterDiplome(
                        9, 4, 2, 3, 5, dateObtention, null, null
                )
        );
        verify(activiteService, never()).enregistrer(
                any(), any(), any(), any()
        );
    }
}
