package com.assistantrh.assistant_rh_api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RechercheAgentAvanceeServiceTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RechercheAgentAvanceeService service =
            new RechercheAgentAvanceeService(jdbc, new ObjectMapper());

    @Test
    void withoutApproximateCriterionTheScoreIsAbsentNotZero() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.rechercher("""
                {"must":[{"field":"service","operator":"CONTAINS","value":"Infrastructures"}]}
                """, "LIST");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("NULL::double precision"));
        assertTrue(!sql.getValue().contains("0.0::double precision"));
    }

    private static final List<Map<String, Object>> NIVEAUX = List.of(
            Map.of("libelle", "Baccalauréat", "code", "BACCALAUREAT", "rang", 3),
            Map.of("libelle", "Licence", "code", "LICENCE", "rang", 5),
            Map.of("libelle", "Master", "code", "MASTER", "rang", 6));

    private Object[] compterEtCapturer(String ast, org.mockito.ArgumentCaptor<String> sql) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        service.rechercher(ast, "COUNT");
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), arguments.capture());
        assertEquals(sql.getValue().chars().filter(ch -> ch == '?').count(), arguments.getValue().length);
        return arguments.getValue();
    }

    @Test
    void unDiplomeEnInformatiqueDeNiveauLicenceAuMoinsSeChercheDansLaListeDesDiplomes() {
        when(jdbc.queryForList(anyString())).thenReturn(NIVEAUX);
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);

        Object[] arguments = compterEtCapturer("""
                {"must":[{"collection":"diplomes","must":[
                  {"field":"domaine","operator":"CONTAINS","value":"Informatique"},
                  {"field":"niveau","operator":"GTE","value":"licence"}]}]}
                """, sql);

        assertTrue(sql.getValue().contains("EXISTS (SELECT 1 FROM employe_diplome ed"));
        assertTrue(sql.getValue().contains("ed.employe_id = e.id"));
        assertTrue(sql.getValue().contains("n.ordre_academique >= ?"));
        assertEquals(List.of("%informatique%", 5), List.of(arguments));
    }

    @Test
    void uneListeSansCritereVeutDireAuMoinsUnElementEtSesSimilaritesNeComptentPasDansLeScore() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.rechercher("""
                {"must":[{"collection":"avancements"},
                         {"collection":"diplomes","must":[{"field":"etablissement","operator":"FUZZY","value":"Ankatso"}]}]}
                """, "LIST");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("EXISTS (SELECT 1 FROM avancement av WHERE av.employe_id = e.id AND (TRUE))"));
        assertTrue(sql.getValue().contains("NULL::double precision"));
    }

    @Test
    void videEtRenseigneValentPourToutChampSansValeur() {
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);

        Object[] arguments = compterEtCapturer("""
                {"must":[{"field":"telephone","operator":"IS_EMPTY"},
                         {"field":"date_embauche","operator":"IS_NOT_EMPTY"}]}
                """, sql);

        assertTrue(sql.getValue().contains("NULLIF(BTRIM((e.telephone)::text), '') IS NULL"));
        assertTrue(sql.getValue().contains("NULLIF(BTRIM((e.date_embauche)::text), '') IS NOT NULL"));
        assertEquals(0, arguments.length);
    }

    @Test
    void unRefusNommeCeQuiEstPermisPourQueLeModeleSeCorrige() {
        when(jdbc.queryForList(anyString())).thenReturn(NIVEAUX);

        String champ = assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"must":[{"field":"diplome","operator":"EQ","value":"Licence"}]}
                """, "COUNT")).getMessage();
        assertTrue(champ.contains("listes (clé collection) : affectations, avancements, carriere, diplomes, documents"));

        String dansListe = assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"must":[{"collection":"diplomes","must":[{"field":"specialite","operator":"EQ","value":"x"}]}]}
                """, "COUNT")).getMessage();
        assertTrue(dansListe.contains("Champs : date_obtention, domaine, etablissement, mention, niveau, ville."));

        String valeur = assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"must":[{"collection":"diplomes","must":[{"field":"niveau","operator":"EQ","value":"bacc"}]}]}
                """, "COUNT")).getMessage();
        assertTrue(valeur.contains("Baccalauréat, Licence, Master"));

        String operateur = assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"must":[{"field":"sexe","operator":"FUZZY","value":"femme"}]}
                """, "COUNT")).getMessage();
        assertTrue(operateur.contains("Opérateurs : EQ, NE, IS_EMPTY, IS_NOT_EMPTY."));

        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"must":[{"collection":"diplomes","must":[{"collection":"carriere"}]}]}
                """, "COUNT"));
    }

    @Test
    void conditionDAncienneteDEchelonRemplieSeCompareALaDateDuJourSansValeur() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        service.rechercher("""
                {"must":[{"field":"date_anciennete_echelon","operator":"TODAY_OR_BEFORE"}]}
                """, "COUNT");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), arguments.capture());
        assertTrue(sql.getValue().contains(
                com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository.DATE_ANCIENNETE_ECHELON
                        + " <= CURRENT_DATE"));
        assertEquals(0, arguments.getValue().length);
    }

    @Test
    void ancienneteDansLEchelonEtEcheanceDansLesProchainsMois() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        service.rechercher("""
                {"must":[{"field":"anciennete_echelon","operator":"GTE","value":2},
                         {"field":"date_anciennete_echelon","operator":"WITHIN_NEXT_MONTHS","value":3}]}
                """, "COUNT");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), arguments.capture());
        assertTrue(sql.getValue().contains("age(CURRENT_DATE, hc.date_debut))::INTEGER >= ?"));
        assertTrue(sql.getValue().contains("BETWEEN CURRENT_DATE AND CURRENT_DATE + (? * INTERVAL '1 month')"));
        assertEquals(List.of(2, 3), List.of(arguments.getValue()));
    }

    @Test
    void uneValeurEstRefuseeAvecTodayOrBefore() {
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"must":[{"field":"date_embauche","operator":"TODAY_OR_BEFORE","value":"2026-01-01"}]}
                """, "COUNT"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void combineMustShouldAndMustNotAndReturnsOnlyCandidateProjection() {
        String ast = """
                {
                  "must":[{"field":"age","operator":"BETWEEN","value":25,"upperValue":35}],
                  "should":[{"field":"personne","operator":"FUZZY_PERSON","value":"Josié"}],
                  "must_not":[{"field":"service","operator":"CONTAINS","value":"Personnel"}],
                  "minimum_should_match":1,
                  "limit":10
                }
                """;
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 29,
                        "nom", "TSIVOERY",
                        "prenom", "Josué Césaire",
                        "poste", "Gestionnaire",
                        "service", "Finances",
                        "direction", "Administration",
                        "score_correspondance", 0.91
                )));

        RechercheAgentAvanceeService.Resultat result = service.rechercher(ast, "LIST");

        assertEquals(1, result.total());
        assertEquals(1, result.agents().size());
        assertEquals(29, result.agents().get(0).get("id"));
        assertEquals(0.91, result.agents().get(0).get("score_correspondance"));
        assertTrue(!result.agents().get(0).containsKey("matricule"));
        assertTrue(!result.agents().get(0).containsKey("telephone"));

        org.mockito.ArgumentCaptor<String> countSql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> countArguments =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(countSql.capture(), eq(Integer.class), countArguments.capture());
        assertTrue(countSql.getValue().contains("NOT ("));
        assertTrue(countSql.getValue().contains("BETWEEN"));
        assertTrue(countSql.getValue().contains("word_similarity"));
        assertTrue(countSql.getValue().contains("similarity("));
        assertTrue(countSql.getValue().contains(">= 0.20"));
        assertEquals(countSql.getValue().chars().filter(ch -> ch == '?').count(),
                countArguments.getValue().length);

        org.mockito.ArgumentCaptor<String> listSql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> listArguments =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(listSql.capture(), listArguments.capture());
        assertTrue(listSql.getValue().contains("GREATEST"));
        assertTrue(listSql.getValue().contains("DISTINCT ON (e.id)"));
        assertEquals(listSql.getValue().chars().filter(ch -> ch == '?').count(),
                listArguments.getValue().length);
    }

    @Test
    void requiresAPositiveClauseAndRejectsUntrustedFieldsBeforeSqlExecution() {
        assertThrows(IllegalArgumentException.class,
                () -> service.rechercher(
                        "{\"must_not\":[{\"field\":\"service\",\"operator\":\"EQ\",\"value\":\"RH\"}]}",
                        "LIST"
                ));
        assertThrows(IllegalArgumentException.class,
                () -> service.rechercher(
                        "{\"must\":[{\"field\":\"nom; DROP TABLE employe\",\"operator\":\"EQ\",\"value\":\"X\"}]}",
                        "LIST"
                ));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsExtraKeysOnNestedBooleanEnvelope() {
        assertThrows(IllegalArgumentException.class,
                () -> service.rechercher(
                        """
                        {"must":[{"bool":{"must":[{"field":"age","operator":"GT","value":20}]},"limit":5}]}
                        """,
                        "COUNT"
                ));
        verifyNoInteractions(jdbc);
    }

    @Test
    void escapesLikeWildcardsAndUsesOneCharacterEscape() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        service.rechercher(
                "{\"must\":[{\"field\":\"service\",\"operator\":\"CONTAINS\",\"value\":\"50%_!\"}]}",
                "COUNT"
        );

        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> arguments =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), arguments.capture());
        assertTrue(sql.getValue().contains("LIKE ? ESCAPE '!'"));
        assertEquals("%50!%!_!!%", arguments.getValue()[0]);
        assertEquals(sql.getValue().chars().filter(ch -> ch == '?').count(),
                arguments.getValue().length);
    }

    @Test
    void requiresOneShouldClauseWhenThereIsNoMustClause() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        RechercheAgentAvanceeService.Resultat result = service.rechercher(
                "{\"should\":[{\"field\":\"service\",\"operator\":\"CONTAINS\",\"value\":\"Finances\"}]}",
                "COUNT"
        );
        assertEquals(0, result.total());
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }
}
