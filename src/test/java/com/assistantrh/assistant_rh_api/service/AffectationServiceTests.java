package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffectationServiceTests {

    private final AffectationRepository affectationRepository =
            mock(AffectationRepository.class);
    private final EmployeRepository employeRepository =
            mock(EmployeRepository.class);
    private final ActiviteService activiteService = mock(ActiviteService.class);
    private final PosteService posteService = mock(PosteService.class);
    private final ServiceService serviceService = mock(ServiceService.class);
    private final UtilisateurService utilisateurService = mock(UtilisateurService.class);
    private final VilleService villeService = mock(VilleService.class);
    private final AffectationService service = new AffectationService(
            affectationRepository,
            employeRepository,
            activiteService,
            posteService,
            serviceService,
            utilisateurService,
            villeService
    );

    @Test
    void muteUnAgentDUnAutreServiceEtConserveSonLieuSansNouvelleVille() {
        LocalDate dateEffet = LocalDate.now();
        when(utilisateurService.estAgentSpersActif(7)).thenReturn(true);
        when(employeRepository.verrouillerPourAffectation(9)).thenReturn(true);
        when(employeRepository.findById(9)).thenReturn(Optional.of(Map.of(
                "matricule", "AG-9",
                "lieu_travail", "Antananarivo"
        )));
        when(serviceService.getServiceById(5L))
                .thenReturn(Map.of("nom", "Nouveau service"));
        when(posteService.getPosteById(12L))
                .thenReturn(Map.of("service_id", 5, "intitule", "Nouveau poste"));
        when(affectationRepository.findActiveByEmployeId(9))
                .thenReturn(affectationCourante());
        when(affectationRepository.fermerAffectation(
                eq(21), eq(Date.valueOf(dateEffet))
        )).thenReturn(1);
        when(affectationRepository.creer(
                9, 12, 5, Date.valueOf(dateEffet), null, null
        )).thenReturn(1);
        when(employeRepository.updateLieuTravail(9, "Antananarivo"))
                .thenReturn(1);

        service.modifierAffectation(
                9, 7, 12, 5, "  ", dateEffet, null, null
        );

        verify(employeRepository).verrouillerPourAffectation(9);
        verify(affectationRepository).fermerAffectation(
                21, Date.valueOf(dateEffet)
        );
        verify(affectationRepository).creer(
                9, 12, 5, Date.valueOf(dateEffet), null, null
        );
        verify(employeRepository).updateLieuTravail(9, "Antananarivo");
        verify(activiteService).enregistrer(
                eq(7), eq(9), eq("MODIFICATION_AFFECTATION"), any()
        );
        verify(villeService, never()).validerVille(any(), any());
    }

    @Test
    void refuseLaMutationSiActeurNonAutorise() {
        when(utilisateurService.estAgentSpersActif(7)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.modifierAffectation(
                        9, 7, 12, 5, null, LocalDate.now(), null, null
                )
        );

        verify(employeRepository, never()).verrouillerPourAffectation(any());
    }

    @Test
    void refuseUnPosteQuiNAppartientPasAuServiceChoisi() {
        when(utilisateurService.estAgentSpersActif(7)).thenReturn(true);
        when(employeRepository.verrouillerPourAffectation(9)).thenReturn(true);
        when(employeRepository.findById(9)).thenReturn(Optional.of(Map.of(
                "matricule", "AG-9",
                "lieu_travail", "Antananarivo"
        )));
        when(serviceService.getServiceById(5L))
                .thenReturn(Map.of("nom", "Nouveau service"));
        when(posteService.getPosteById(12L))
                .thenReturn(Map.of("service_id", 6, "intitule", "Nouveau poste"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.modifierAffectation(
                        9, 7, 12, 5, null, LocalDate.now(), null, null
                )
        );

        verify(affectationRepository, never()).fermerAffectation(any(), any());
    }

    @Test
    void refuseUneDateEffetFuture() {
        when(utilisateurService.estAgentSpersActif(7)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.modifierAffectation(
                        9, 7, 12, 5, null, LocalDate.now().plusDays(1), null, null
                )
        );

        verify(employeRepository, never()).verrouillerPourAffectation(any());
    }

    private Map<String, Object> affectationCourante() {
        Map<String, Object> current = new HashMap<>();
        current.put("id", 21);
        current.put("poste_id", 3);
        current.put("service_id", 2);
        current.put("date_debut", LocalDate.now().minusYears(1));
        current.put("poste", "Ancien poste");
        current.put("service", "Ancien service");
        return current;
    }
}
