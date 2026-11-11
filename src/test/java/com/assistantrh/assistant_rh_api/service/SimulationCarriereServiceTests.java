package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationCarriereServiceTests {
    private final SituationCarriereRepository situations = mock(SituationCarriereRepository.class);
    private final DocumentRhRepository documents = mock(DocumentRhRepository.class);
    private final SimulationCarriereService service = new SimulationCarriereService(situations, documents);

    private static Map<String, Object> grade(int id, String type, String parcours, String classe, String code, Integer duree) {
        Map<String, Object> grade = new HashMap<>(Map.of("grade_id", id, "type_emploi", type, "parcours_code", parcours,
                "parcours", parcours, "classe", classe, "code", code, "libelle", code));
        grade.put("duree_min", duree);
        return grade;
    }

    @BeforeEach
    void grilleFictive() {
        when(situations.findGradesParParcours()).thenReturn(List.of(
                grade(1, "Fonctionnaire", "FONCTIONNAIRE", "3e classe", "3C1E", 2),
                grade(2, "Fonctionnaire", "FONCTIONNAIRE", "3e classe", "3C2E", 2),
                grade(3, "Fonctionnaire", "FONCTIONNAIRE", "2e classe", "2C1E", 2),
                grade(10, "Agent contractuel", "AUXILIAIRE", "Échelle I", "A11E", 2),
                grade(11, "Agent contractuel", "AUXILIAIRE", "Échelle I", "A12E", 2)));
        List<Map<String, Object>> grille = new ArrayList<>();
        for (Object[] cellule : new Object[][] {{"3C1E", 350}, {"3C2E", 370}, {"2C1E", 410}, {"A11E", 300}, {"A12E", 360}}) {
            grille.add(Map.of("categorie", "I", "grade_code", cellule[0], "indice", cellule[1]));
        }
        when(situations.findGrilleIndiciaire()).thenReturn(grille);
    }

    private void situation(int echelon, LocalDate debut, LocalDate retraite) {
        Map<String, Object> actuelle = new HashMap<>(Map.of("echelon_id", echelon, "categorie_code", "I",
                "date_debut", Date.valueOf(debut)));
        when(situations.findSituationActuelle(42)).thenReturn(Optional.of(actuelle));
        Map<String, Object> periode = new HashMap<>(Map.of("echelon_id", echelon, "date_debut", Date.valueOf(debut)));
        periode.put("date_fin", null);
        when(situations.findHistorique(42)).thenReturn(List.of(periode));
        when(documents.dateRetraitePossible(42)).thenReturn(Optional.ofNullable(retraite));
    }

    @SuppressWarnings("unchecked")
    private static List<String> parcours(Map<String, Object> simulation) {
        return ((List<Map<String, Object>>) simulation.get("etapes")).stream()
                .map(e -> e.get("evenement") + ":" + e.get("gradeCode") + ":" + e.get("indice")).toList();
    }

    @Test
    void unFonctionnaireAvanceAChaqueDureeMinimaleEtChangeDeClasseJusquALaRetraite() {
        LocalDate debut = LocalDate.now().minusYears(1);
        situation(1, debut, debut.plusYears(10));

        assertEquals(List.of("EMBAUCHE:3C1E:350", "AVANCEMENT:3C2E:370", "CLASSE:2C1E:410", "RETRAITE:2C1E:410"),
                parcours(service.simuler(42, null)));
    }

    @Test
    void sansTitularisationUnContractuelResteDansSonParcours() {
        LocalDate debut = LocalDate.now().minusYears(1);
        situation(10, debut, debut.plusYears(10));

        assertEquals(List.of("EMBAUCHE:A11E:300", "AVANCEMENT:A12E:360", "RETRAITE:A12E:360"),
                parcours(service.simuler(42, null)));
    }

    @Test
    void titulariseIlRejointLesFonctionnairesAuGradeQueSonIndiceDesigne() {
        LocalDate debut = LocalDate.now().minusYears(1);
        situation(10, debut, debut.plusYears(10));

        // A12E (360) dans un an : titularisé dans un an et demi, il entre en 3C2E (370), le premier indice supérieur.
        Map<String, Object> simulation = service.simuler(42, debut.plusYears(2).plusMonths(6));

        assertEquals(List.of("EMBAUCHE:A11E:300", "AVANCEMENT:A12E:360", "TITULARISATION:3C2E:370",
                "CLASSE:2C1E:410", "RETRAITE:2C1E:410"), parcours(simulation));
        assertEquals(410, simulation.get("indiceFinal"));
    }
}
