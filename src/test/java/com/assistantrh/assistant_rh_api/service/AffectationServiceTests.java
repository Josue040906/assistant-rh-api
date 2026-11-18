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
                eq(21), eq(Date.valueOf(dateEffet.minusDays(1)))
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
        // L'ancienne affectation se termine la veille de la nouvelle.
        verify(affectationRepository).fermerAffectation(
                21, Date.valueOf(dateEffet.minusDays(1))
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
    void lAgentQuiPrendLePostePourLequelIlEtaitPressentiCloLaReleve() {
        LocalDate dateEffet = LocalDate.now();
        com.assistantrh.assistant_rh_api.repository.PlanReleveRepository plans =
                mock(com.assistantrh.assistant_rh_api.repository.PlanReleveRepository.class);
        service.setPlansReleve(plans);
        when(plans.cloreSiPourvu(12, 9)).thenReturn(true);
        when(utilisateurService.estAgentSpersActif(7)).thenReturn(true);
        when(employeRepository.verrouillerPourAffectation(9)).thenReturn(true);
        when(employeRepository.findById(9)).thenReturn(Optional.of(Map.of(
                "matricule", "AG-9",
                "lieu_travail", "Antananarivo"
        )));
        when(serviceService.getServiceById(5L)).thenReturn(Map.of("nom", "Nouveau service"));
        when(posteService.getPosteById(12L)).thenReturn(Map.of("service_id", 5, "intitule", "Nouveau poste"));
        when(affectationRepository.findActiveByEmployeId(9)).thenReturn(affectationCourante());
        when(affectationRepository.fermerAffectation(eq(21), any())).thenReturn(1);
        when(affectationRepository.creer(9, 12, 5, Date.valueOf(dateEffet), null, null)).thenReturn(1);
        when(employeRepository.updateLieuTravail(9, "Antananarivo")).thenReturn(1);

        service.modifierAffectation(9, 7, 12, 5, "  ", dateEffet, null, null);

        verify(plans).cloreSiPourvu(12, 9);
        verify(activiteService).enregistrer(eq(7), eq(9), eq("VALIDATION"),
                eq("Poste attribué : l'agent AG-9 prend le poste Nouveau poste / Nouveau service pour lequel il avait été choisi."));
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

    @Test
    void uneMutationQuiNeChangeRienEstEmpecheeAvecSaRaison() {
        when(affectationRepository.findActiveByEmployeId(9)).thenReturn(affectationCourante());
        when(posteService.getPosteById(3L)).thenReturn(Map.of("service_id", 2));
        when(employeRepository.findById(9)).thenReturn(Optional.of(Map.of("lieu_travail", "Antananarivo")));

        org.junit.jupiter.api.Assertions.assertEquals(
                Optional.of("L'agent occupe déjà ce poste dans ce service, à Antananarivo : rien ne changerait."),
                service.empechementMutation(9, 3, 2, null, LocalDate.now()));
    }

    @Test
    void uneMutationDateeAvantLeDebutDeLAffectationActuelleEstEmpechee() {
        Map<String, Object> actuelle = affectationCourante();
        actuelle.put("date_debut", LocalDate.of(2026, 11, 17));
        when(affectationRepository.findActiveByEmployeId(9)).thenReturn(actuelle);
        when(posteService.getPosteById(12L)).thenReturn(Map.of("service_id", 5));

        org.junit.jupiter.api.Assertions.assertEquals(
                Optional.of("La date d'effet doit être postérieure au début de l'affectation actuelle (le 17/11/2026)."),
                service.empechementMutation(9, 12, 5, null, LocalDate.of(2026, 11, 17)));
    }

    @Test
    void uneMutationVersUnAutrePosteEstPossible() {
        when(affectationRepository.findActiveByEmployeId(9)).thenReturn(affectationCourante());
        when(posteService.getPosteById(12L)).thenReturn(Map.of("service_id", 5));
        when(employeRepository.findById(9)).thenReturn(Optional.of(Map.of("lieu_travail", "Antananarivo")));

        org.junit.jupiter.api.Assertions.assertEquals(Optional.empty(),
                service.empechementMutation(9, 12, 5, null, LocalDate.now()));
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
