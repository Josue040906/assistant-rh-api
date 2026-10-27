package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.dto.ProfilAgentPersonnelDto;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfilAgentServiceTests {
    private final EmployeRepository employeRepository =
            mock(EmployeRepository.class);
    private final SituationCarriereRepository situationCarriereRepository =
            mock(SituationCarriereRepository.class);
    private final DiplomeRepository diplomeRepository =
            mock(DiplomeRepository.class);

    private final SituationCarriereService situationCarriereService =
            mock(SituationCarriereService.class);
    private final UtilisateurRepository utilisateurRepository =
            mock(UtilisateurRepository.class);
    private final ProfilAgentService service = new ProfilAgentService(
            employeRepository,
            situationCarriereRepository,
            diplomeRepository,
            situationCarriereService,
            utilisateurRepository
    );

    @Test
    void returnsOnlyTheAuthenticatedAgentsIdentityAndCareerFields() {
        when(utilisateurRepository.estAgentSpersActif(7)).thenReturn(true);
        when(employeRepository.findByUserId(7)).thenReturn(Optional.of(
                Map.ofEntries(
                        Map.entry("id", 19),
                        Map.entry("user_id", 7),
                        Map.entry("matricule", "AG-19"),
                        Map.entry("nom", "RAKOTO"),
                        Map.entry("prenom", "Mialy"),
                        Map.entry("email", "mialy@example.test"),
                        Map.entry("date_embauche", Date.valueOf("2018-04-02")),
                        Map.entry("type_emploi", "Fonctionnaire"),
                        Map.entry("categorie", "A"),
                        Map.entry("corps_libelle", "Administrateur"),
                        Map.entry("poste", "Gestionnaire"),
                        Map.entry("service", "Service du Personnel"),
                        Map.entry("direction", "Administration"),
                        Map.entry("cin", "NE-DOIT-PAS-SORTIR"),
                        Map.entry("telephone", "NE-DOIT-PAS-SORTIR"),
                        Map.entry("adresse", "NE-DOIT-PAS-SORTIR")
                )
        ));
        when(situationCarriereRepository.findHistorique(19)).thenReturn(
                List.of(Map.of(
                        "date_debut", Date.valueOf("2021-01-01"),
                        "date_fin", Date.valueOf("2023-06-30"),
                        "categorie_code", "B",
                        "classe_libelle", "Classe II",
                        "echelon_ordre", 3,
                        "employe_id", 19
                ))
        );

        ProfilAgentPersonnelDto profile = service.obtenirMonProfilAgent(7);

        assertEquals("AG-19", profile.matricule());
        assertEquals("RAKOTO", profile.nom());
        assertEquals("Mialy", profile.prenom());
        assertEquals("mialy@example.test", profile.email());
        assertEquals(LocalDate.parse("2018-04-02"), profile.dateEmbauche());
        assertEquals("Service du Personnel", profile.service());
        assertEquals("Administration", profile.direction());
        assertEquals(1, profile.parcoursCarriere().size());
        assertEquals(
                LocalDate.parse("2021-01-01"),
                profile.parcoursCarriere().get(0).dateDebut()
        );
        assertEquals("Classe II", profile.parcoursCarriere().get(0).classe());
        verify(employeRepository).findByUserId(7);
    }

    @Test
    void refusesProfilesForInactiveAccounts() {
        when(utilisateurRepository.estAgentSpersActif(8)).thenReturn(false);

        assertThrows(
                ResponseStatusException.class,
                () -> service.obtenirMonProfilAgent(8)
        );
    }
}
