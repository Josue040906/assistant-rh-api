package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteExigenceRepository;
import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExigencePosteServiceTests {
    private final PosteExigenceRepository exigences = mock(PosteExigenceRepository.class);
    private final PosteRepository postes = mock(PosteRepository.class);
    private final ExigencePosteService service = new ExigencePosteService(exigences, postes);

    private static Map<String, Object> exigence(String type, String valeur, String niveau, boolean indispensable) {
        Map<String, Object> exigence = new HashMap<>();
        exigence.put("type", type);
        exigence.put("valeur", valeur);
        exigence.put("niveau", niveau);
        exigence.put("indispensable", indispensable);
        if ("DIPLOME".equals(type)) {
            exigence.put("niveau_libelle", "Licence");
            exigence.put("niveau_ordre", 5);
        }
        return exigence;
    }

    @Test
    void chaqueExigenceEstRemplieManquanteOuAVerifierSelonLaFiche() {
        when(exigences.exigences(9L)).thenReturn(List.of(
                exigence("LANGUE", "Anglais", "COURANT", true),
                exigence("DIPLOME", "Droit", "LICENCE", false),
                exigence("COMPETENCE", "Gestion de la paie", null, false)));
        when(exigences.langues(anyList())).thenReturn(List.of(
                Map.of("employe_id", 1, "langue", "anglais", "niveau", "COURANT"),
                Map.of("employe_id", 2, "langue", "Anglais", "niveau", "NOTIONS")));
        when(postes.diplomes(anyList())).thenReturn(List.of(
                Map.of("employe_id", 1, "domaine", "Droit", "niveau", "Master", "ordre", 6),
                Map.of("employe_id", 2, "domaine", "Gestion", "niveau", "Licence", "ordre", 5)));
        when(postes.competences(anyList())).thenReturn(List.of(
                Map.of("employe_id", 1, "libelle", "gestion de la paie et des primes")));
        when(postes.formations(anyList())).thenReturn(List.of());

        Map<Integer, List<Map<String, Object>>> resultat = service.evaluer(9L, List.of(1, 2, 3));

        assertEquals(List.of("REMPLIE", "REMPLIE", "REMPLIE"), resultat.get(1).stream().map(e -> e.get("etat")).toList());
        assertEquals("Master en Droit", resultat.get(1).get(1).get("detail"));
        assertEquals(List.of("MANQUANTE", "MANQUANTE", "A_VERIFIER"), resultat.get(2).stream().map(e -> e.get("etat")).toList());
        assertEquals("Anglais : notions seulement", resultat.get(2).get(0).get("detail"));
        // Rien sur sa fiche : on ne conclut pas, on demande de vérifier.
        assertEquals(List.of("A_VERIFIER", "A_VERIFIER", "A_VERIFIER"), resultat.get(3).stream().map(e -> e.get("etat")).toList());
        assertEquals("Anglais (courant minimum)", resultat.get(1).get(0).get("libelle"));
        assertEquals("Licence minimum en Droit", resultat.get(1).get(1).get("libelle"));
    }

    @Test
    void lesExigencesSontVerifieesAvantDEtreEnregistrees() {
        when(postes.findById(9L)).thenReturn(Map.of("id", 9));
        when(exigences.niveauDiplomeExiste("LICENCE")).thenReturn(true);

        assertEquals("Indiquez la langue demandée.", assertThrows(IllegalArgumentException.class,
                () -> service.enregistrer(9L, List.of(Map.of("type", "LANGUE", "niveau", "COURANT")))).getMessage());
        assertEquals("Choisissez le niveau de diplôme demandé.", assertThrows(IllegalArgumentException.class,
                () -> service.enregistrer(9L, List.of(Map.of("type", "DIPLOME", "niveau", "BREVET")))).getMessage());

        service.enregistrer(9L, List.of(Map.of("type", "diplome", "niveau", "licence", "valeur", " Droit ", "indispensable", true)));
        verify(exigences).remplacer(eq(9L), eq(List.of(Map.of("type", "DIPLOME", "valeur", "Droit", "niveau", "LICENCE",
                "indispensable", true))));
    }
}
