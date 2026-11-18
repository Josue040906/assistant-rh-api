package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.ProfilComplementaireRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfilComplementaireServiceTests {
    private final ProfilComplementaireRepository repository = mock(ProfilComplementaireRepository.class);
    private final EmployeRepository employes = mock(EmployeRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final ProfilComplementaireService service =
            new ProfilComplementaireService(repository, employes, utilisateurs);

    @BeforeEach
    void agentExistant() {
        when(employes.findById(42)).thenReturn(Optional.of(Map.of("id", 42)));
        when(repository.situationFamiliale(42)).thenReturn(Map.of());
        when(utilisateurs.estAgentSpersActif(5)).thenReturn(true);
    }

    private Map<String, Object> corps() {
        Map<String, Object> corps = new HashMap<>();
        corps.put("situationMatrimoniale", "MARIE");
        corps.put("nombreEnfants", 2);
        corps.put("langues", List.of(Map.of("langue", " Malagasy ", "niveau", "MATERNELLE"),
                Map.of("langue", "Français", "niveau", "COURANT")));
        corps.put("competences", List.of("Gestion de paie", "gestion de paie", " ", "Rédaction administrative"));
        corps.put("formations", List.of(Map.of("intitule", "Gestion des carrières", "organisme", "ENAM",
                "annee", 2019, "dureeJours", "5")));
        return corps;
    }

    @Test
    void leServiceDuPersonnelEnregistreToutDUnBlocNettoye() {
        service.enregistrerPourAgent(42, 5, corps());

        verify(repository).enregistrerSituationFamiliale(42, "MARIE", 2);
        verify(repository).remplacerListes(
                42,
                List.of(Map.of("langue", "Malagasy", "niveau", "MATERNELLE"),
                        Map.of("langue", "Français", "niveau", "COURANT")),
                List.of("Gestion de paie", "Rédaction administrative"),
                List.of(new java.util.LinkedHashMap<>(Map.of("intitule", "Gestion des carrières",
                        "organisme", "ENAM", "annee", 2019, "dureeJours", 5))));
    }

    @Test
    void horsDuPersonnelOnNeModifiePasLeProfilDUnAutre() {
        assertEquals("Seul un agent actif du Service du Personnel peut modifier ce profil.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.enregistrerPourAgent(42, 8, corps())).getMessage());
        verify(repository, never()).remplacerListes(anyInt(), anyList(), anyList(), anyList());
    }

    @Test
    void uneLangueSansNiveauOuEnDoubleEstRefusee() {
        Map<String, Object> sansNiveau = corps();
        sansNiveau.put("langues", List.of(Map.of("langue", "Anglais")));
        assertEquals("Choisissez le niveau de la langue « Anglais ».",
                assertThrows(IllegalArgumentException.class,
                        () -> service.enregistrerPourAgent(42, 5, sansNiveau)).getMessage());

        Map<String, Object> enDouble = corps();
        enDouble.put("langues", List.of(Map.of("langue", "Anglais", "niveau", "COURANT"),
                Map.of("langue", "anglais", "niveau", "NOTIONS")));
        assertEquals("La langue « anglais » figure deux fois.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.enregistrerPourAgent(42, 5, enDouble)).getMessage());
    }

    @Test
    void uneFormationDansLeFuturOuUneSituationInconnueEstRefusee() {
        Map<String, Object> future = corps();
        future.put("formations", List.of(Map.of("intitule", "Excel", "annee", 2999)));
        assertThrows(IllegalArgumentException.class, () -> service.enregistrerPourAgent(42, 5, future));

        Map<String, Object> situation = corps();
        situation.put("situationMatrimoniale", "PACSE");
        assertEquals("La situation matrimoniale est invalide.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.enregistrerPourAgent(42, 5, situation)).getMessage());
        verify(repository, never()).enregistrerSituationFamiliale(anyInt(), any(), any());
    }
}
