package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PosteReleveTests {
    private final PosteRepository repository = mock(PosteRepository.class);
    private final PosteService service = new PosteService(repository);
    private final LocalDate aujourdhui = LocalDate.now();

    private Map<String, Object> candidat(int id, String nom, boolean memeService, LocalDate retraite, int categorieId) {
        Map<String, Object> candidat = new HashMap<>(Map.of("id", id, "nom", nom, "prenom", "A", "meme_service", memeService,
                "service", memeService ? "Service du Personnel" : "Service de la Formation",
                "categorie", "VI", "categorie_id", categorieId, "categorie_min", 6, "categorie_requise", "VI",
                "date_embauche", aujourdhui.minusYears(12)));
        candidat.put("retraite_possible", retraite);
        return candidat;
    }

    @Test
    void lesCandidatsSontClassesAvecLeursRaisonsEtCeuxQuiPartentTropTotEcartes() {
        when(repository.findById(5L)).thenReturn(Map.of("id", 5));
        when(repository.titulairesDuPoste(5L)).thenReturn(List.of(
                new HashMap<>(Map.of("id", 4, "retraite_possible", aujourdhui.plusYears(4)))));
        when(repository.candidatsReleve(5L)).thenReturn(List.of(
                candidat(10, "AUTRE-SERVICE", false, aujourdhui.plusYears(20), 6),
                candidat(11, "MEME-SERVICE", true, aujourdhui.plusYears(20), 6),
                // Part un an après le titulaire : pas une relève durable.
                candidat(12, "PART-BIENTOT", true, aujourdhui.plusYears(5), 6)));
        when(repository.competences(anyList())).thenReturn(List.of(
                Map.of("employe_id", 4, "libelle", "Gestion de la paie"),
                Map.of("employe_id", 10, "libelle", "gestion de la paie"),
                Map.of("employe_id", 4, "libelle", "Rédaction administrative"),
                Map.of("employe_id", 10, "libelle", "Rédaction administrative")));
        when(repository.diplomes(anyList())).thenReturn(List.of(
                Map.of("employe_id", 4, "domaine", "Gestion"),
                Map.of("employe_id", 10, "domaine", "Gestion")));
        when(repository.formations(anyList())).thenReturn(List.of(
                Map.of("employe_id", 10, "intitule", "Gestion des carrières")));

        List<Map<String, Object>> classes = service.candidatsReleve(5L);

        assertEquals(List.of("AUTRE-SERVICE", "MEME-SERVICE"), classes.stream().map(c -> c.get("nom")).toList());
        @SuppressWarnings("unchecked")
        List<String> raisons = (List<String>) classes.get(0).get("raisons");
        assertTrue(raisons.contains("2 compétences en commun avec le titulaire : gestion de la paie, Rédaction administrative"),
                raisons.toString());
        assertTrue(raisons.contains("Diplôme en Gestion, comme le titulaire"), raisons.toString());
        assertTrue(raisons.contains("Formation : Gestion des carrières"), raisons.toString());
        assertTrue(raisons.contains("12 ans de service"), raisons.toString());
        @SuppressWarnings("unchecked")
        List<String> raisonsMemeService = (List<String>) classes.get(1).get("raisons");
        assertEquals("Même service", raisonsMemeService.get(0));
    }
}
