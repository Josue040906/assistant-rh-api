package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PosteReleveTests {
    private final PosteRepository repository = mock(PosteRepository.class);
    private final PosteService service = new PosteService(repository);
    private final LocalDate aujourdhui = LocalDate.now();

    private Map<String, Object> agent(int id, String nom, boolean memeService, LocalDate retraite, int categorieId) {
        Map<String, Object> agent = new HashMap<>(Map.of("id", id, "nom", nom, "prenom", "A", "meme_service", memeService,
                "meme_direction", true, "service", memeService ? "Service du Personnel" : "Service de la Formation",
                "categorie", categorieId == 6 ? "VI" : "IV", "categorie_id", categorieId, "categorie_min", 6,
                "categorie_requise", "VI"));
        agent.put("date_embauche", aujourdhui.minusYears(12));
        agent.put("retraite_possible", retraite);
        agent.put("poste", "Gestionnaire");
        agent.put("occupants_poste_actuel", 2);
        return agent;
    }

    private void sansProfils() {
        when(repository.competences(anyList())).thenReturn(List.of());
        when(repository.diplomes(anyList())).thenReturn(List.of());
        when(repository.formations(anyList())).thenReturn(List.of());
    }

    @Test
    void lesAgentsSontClassesAvecLeursAtoutsEtCeuxQuiPartentTropTotRestentAvecUnPointDAttention() {
        when(repository.findById(5L)).thenReturn(Map.of("id", 5, "nombre_places", 1));
        when(repository.titulairesDuPoste(5L)).thenReturn(List.of(
                new HashMap<>(Map.of("id", 4, "retraite_possible", aujourdhui.plusYears(4)))));
        when(repository.agentsPourPoste(5L, null)).thenReturn(List.of(
                agent(10, "AUTRE-SERVICE", false, aujourdhui.plusYears(20), 6),
                agent(11, "MEME-SERVICE", true, aujourdhui.plusYears(20), 6),
                // Part un an après le titulaire : choisissable, mais signalé et classé plus bas.
                agent(12, "PART-BIENTOT", true, aujourdhui.plusYears(5), 6)));
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

        assertEquals(List.of("AUTRE-SERVICE", "MEME-SERVICE", "PART-BIENTOT"), classes.stream().map(c -> c.get("nom")).toList());
        @SuppressWarnings("unchecked")
        List<String> raisons = (List<String>) classes.get(0).get("raisons");
        assertTrue(raisons.contains("Même direction, Service de la Formation"), raisons.toString());
        assertTrue(raisons.contains("2 compétences en commun avec le titulaire : gestion de la paie, Rédaction administrative"),
                raisons.toString());
        assertTrue(raisons.contains("Diplôme en Gestion, comme le titulaire"), raisons.toString());
        assertTrue(raisons.contains("Formation : Gestion des carrières"), raisons.toString());
        assertTrue(raisons.contains("12 ans de service"), raisons.toString());
        @SuppressWarnings("unchecked")
        List<String> attentions = (List<String>) classes.get(2).get("attentions");
        assertTrue(attentions.get(0).startsWith("Part lui-même à la retraite le "), attentions.toString());
    }

    @Test
    void celuiQuiADejaOccupeLePosteEstEnTeteEtLesPointsDAttentionSontExpliques() {
        when(repository.findById(5L)).thenReturn(Map.of("id", 5, "nombre_places", 1));
        when(repository.titulairesDuPoste(5L)).thenReturn(List.of());
        Map<String, Object> ancien = agent(20, "ANCIEN", false, aujourdhui.plusYears(20), 4);
        ancien.put("meme_direction", false);
        ancien.put("deja_occupe_depuis", aujourdhui.minusYears(9));
        ancien.put("deja_occupe_jusquau", aujourdhui.minusYears(4));
        ancien.put("deja_choisi_pour", "Chef de bureau");
        ancien.put("occupants_poste_actuel", 1);
        ancien.put("poste", "Chef de division");
        when(repository.agentsPourPoste(5L, null)).thenReturn(List.of(
                agent(21, "VOISIN", false, aujourdhui.plusYears(20), 6), ancien));
        sansProfils();

        List<Map<String, Object>> classes = service.candidatsReleve(5L);

        assertEquals("ANCIEN", classes.get(0).get("nom"));
        @SuppressWarnings("unchecked")
        List<String> raisons = (List<String>) classes.get(0).get("raisons");
        assertEquals("A déjà occupé ce poste (" + aujourdhui.minusYears(9).getYear() + "–"
                + aujourdhui.minusYears(4).getYear() + ")", raisons.get(0));
        assertEquals(List.of("Catégorie IV, en dessous de celle du poste (VI)", "Déjà choisi pour le poste Chef de bureau",
                "Laisserait son poste actuel (Chef de division) sans titulaire"), classes.get(0).get("attentions"));
    }

    @Test
    void unePlaceDejaLibreSeLibereAujourdhuiMemeSiUnAutreTitulaireResteLongtemps() {
        when(repository.findById(7L)).thenReturn(Map.of("id", 7, "nombre_places", 2));
        // Un seul titulaire sur deux places : la seconde est libre maintenant.
        when(repository.titulairesDuPoste(7L)).thenReturn(List.of(
                new HashMap<>(Map.of("id", 4, "retraite_possible", aujourdhui.plusYears(15)))));
        when(repository.agentsPourPoste(7L, null)).thenReturn(List.of(agent(40, "TRANQUILLE", true, aujourdhui.plusYears(6), 6)));
        sansProfils();

        assertEquals(List.of(), service.candidatsReleve(7L).get(0).get("attentions"));
    }

    @Test
    void nImporteQuelAgentPeutEtreAnalyseMaisPasLeTitulaire() {
        when(repository.findById(5L)).thenReturn(Map.of("id", 5, "nombre_places", 1));
        when(repository.titulairesDuPoste(5L)).thenReturn(List.of());
        when(repository.agentsPourPoste(5L, 30)).thenReturn(List.of(agent(30, "LIBRE", false, null, 6)));
        when(repository.agentsPourPoste(5L, 4)).thenReturn(List.of());
        sansProfils();

        assertEquals("LIBRE", service.analyserCandidat(5L, 30).get("nom"));
        assertEquals("Cet agent ne peut pas être choisi : il occupe déjà ce poste, ou il n'est plus en activité.",
                assertThrows(IllegalArgumentException.class, () -> service.analyserCandidat(5L, 4)).getMessage());
    }
}
