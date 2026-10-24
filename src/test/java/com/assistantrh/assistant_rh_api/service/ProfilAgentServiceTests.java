package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.dto.ProfilAgentPersonnelDto;
import com.assistantrh.assistant_rh_api.dto.AssistantAgentProfileDto;
import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;
import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.AgentProfileRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
    private final AgentProfileRepository agentProfileRepository =
            mock(AgentProfileRepository.class);
    private final DiplomeRepository diplomeRepository =
            mock(DiplomeRepository.class);
    private final AffectationRepository affectationRepository =
            mock(AffectationRepository.class);
    private final SituationCarriereRepository situationCarriereRepository =
            mock(SituationCarriereRepository.class);
    private final UtilisateurRepository utilisateurRepository =
            mock(UtilisateurRepository.class);
    private final ProfilAgentService service = new ProfilAgentService(
            employeRepository,
            agentProfileRepository,
            diplomeRepository,
            affectationRepository,
            situationCarriereRepository,
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

    @Test
    void returnsAConciseGlobalProfileWithoutPrivateContactDetails() {
        when(agentProfileRepository.findProfileCandidates("Mialy RAKOTO")).thenReturn(
                List.of(Map.ofEntries(
                        Map.entry("id", 19),
                        Map.entry("matricule", "AG-19"),
                        Map.entry("nom", "RAKOTO"),
                        Map.entry("prenom", "Mialy"),
                        Map.entry("sexe", "F"),
                        Map.entry("date_naissance", Date.valueOf("1990-03-01")),
                        Map.entry("type_emploi", "Fonctionnaire"),
                        Map.entry("categorie", "A"),
                        Map.entry("corps", "Administrateur"),
                        Map.entry("poste", "Gestionnaire"),
                        Map.entry("service", "Service RH"),
                        Map.entry("direction", "Administration"),
                        Map.entry("telephone", "NE-DOIT-PAS-SORTIR")
                ))
        );
        when(diplomeRepository.findByEmployeId(19)).thenReturn(List.of(Map.of(
                "intitule", "Master",
                "domaine", "Informatique",
                "mention", "Bien",
                "etablissement", "Université",
                "date_obtention", Date.valueOf("2015-06-30")
        )));
        when(situationCarriereRepository.findHistorique(19)).thenReturn(
                List.of(Map.of(
                        "date_debut", Date.valueOf("2021-01-01"),
                        "date_fin", Date.valueOf("2023-06-30"),
                        "categorie_code", "B",
                        "classe_libelle", "Classe II",
                        "echelon_ordre", 3
                ))
        );
        when(affectationRepository.findAssistantHistoryByEmployeId(19))
                .thenReturn(List.of(Map.of(
                        "poste", "Gestionnaire",
                        "service", "Service RH",
                        "direction", "Administration",
                        "date_debut", Date.valueOf("2022-01-01"),
                        "date_fin", Date.valueOf("2024-01-01")
                )));

        AssistantAgentProfileDto profile =
                service.obtenirProfilAssistantAgent("Mialy RAKOTO")
                        .orElseThrow();

        assertEquals("RAKOTO", profile.nom());
        assertEquals("AG-19", profile.matricule());
        assertEquals(1, profile.diplomes().size());
        assertEquals("Informatique", profile.diplomes().get(0).domaine());
        assertEquals(1, profile.parcoursCarriere().size());
        assertEquals(1, profile.affectations().size());
        assertEquals("Service RH", profile.affectations().get(0).service());
        assertEquals("Gestionnaire", profile.poste());
        assertEquals(false, profile.competencesDisponibles());
        assertEquals(List.of(), profile.competences());
    }

    @Test
    void doesNotReturnAProfileForAnAmbiguousPartialName() {
        when(agentProfileRepository.findProfileCandidates("Mialy")).thenReturn(
                List.of(Map.of(
                        "id", 19,
                        "prenom", "Mialy",
                        "nom", "RAKOTO",
                        "matricule", "AG-19"
                ))
        );

        assertEquals(
                Optional.empty(),
                service.obtenirProfilAssistantAgent("Mialy")
        );
    }

    @Test
    void rejectsDuplicateFullNamesInsteadOfSelectingAnArbitraryAgent() {
        when(agentProfileRepository.findProfileCandidates("Mialy RAKOTO")).thenReturn(
                List.of(
                        Map.of(
                                "id", 19,
                                "prenom", "Mialy",
                                "nom", "RAKOTO",
                                "matricule", "AG-19"
                        ),
                        Map.of(
                                "id", 20,
                                "prenom", "Mialy",
                                "nom", "RAKOTO",
                                "matricule", "AG-20"
                        )
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.obtenirProfilAssistantAgent("Mialy RAKOTO")
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(
                "Plusieurs agents correspondent à ce nom. "
                        + "Veuillez préciser le matricule.",
                exception.getReason()
        );
    }
}
