package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.CongeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CongeServiceTests {
    private final CongeRepository conges = mock(CongeRepository.class);
    private final SituationCarriereRepository situations = mock(SituationCarriereRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final ActiviteService activites = mock(ActiviteService.class);
    private final CongeService service = new CongeService(conges, situations, utilisateurs, activites);

    private final LocalDate debut = LocalDate.of(2026, 12, 10);

    @BeforeEach
    void agentEtActeur() {
        when(utilisateurs.estAgentSpersActif(5)).thenReturn(true);
        when(situations.verrouillerEmploye(42)).thenReturn(true);
        when(conges.congesDeLAnnee(any(), anyInt())).thenReturn(List.of());
        when(conges.enCongeJusquau(42)).thenReturn(Optional.empty());
    }

    private void solde(String acquis, String pris) {
        when(conges.droits(42, debut)).thenReturn(Optional.of(Map.of(
                "jours_par_mois", new BigDecimal("2.50"), "acquis", new BigDecimal(acquis), "pris", new BigDecimal(pris))));
    }

    @Test
    void lAbsenceEstEnregistreeEnJoursCalendairesQuandLeSoldeSuffit() {
        solde("27.50", "20");
        when(conges.enregistrer(42, 9L, debut, debut.plusDays(2), 3)).thenReturn(1);

        Map<String, Object> resultat = service.prendreConge(42, 5, debut, debut.plusDays(2), 9L, "DOC-9");

        assertEquals(3, resultat.get("jours"));
    }

    @Test
    void unSoldeInsuffisantEstRefuseAvecLesChiffres() {
        solde("27.50", "26");

        assertEquals("Solde insuffisant : 1.5 jours disponibles au 10/12/2026, 3 demandés.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.prendreConge(42, 5, debut, debut.plusDays(2), 9L, "DOC-9")).getMessage());
        verify(conges, never()).enregistrer(any(), anyLong(), any(), any(), anyInt());
    }

    @Test
    void unCongeNeChevaucheNiUnAutreCongeNiDeuxAnnees() {
        solde("27.50", "0");
        when(conges.chevauche(42, debut, debut.plusDays(2))).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.prendreConge(42, 5, debut, debut.plusDays(2), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.prendreConge(42, 5, debut, LocalDate.of(2027, 1, 3), null, null));
    }
}
