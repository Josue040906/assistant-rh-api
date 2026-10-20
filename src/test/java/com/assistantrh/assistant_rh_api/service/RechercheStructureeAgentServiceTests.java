package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.RechercheStructureeAgentRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RechercheStructureeAgentServiceTests {
    private final RechercheStructureeAgentRepository repository =
            mock(RechercheStructureeAgentRepository.class);
    private final RechercheStructureeAgentService service =
            new RechercheStructureeAgentService(new ObjectMapper(), repository);

    @Test
    void valideEtTransmetDesFiltresComposesEtLaPeriodeRelative() {
        when(repository.rechercher(
                org.mockito.ArgumentMatchers.anyList(),
                anyInt(),
                anyString(),
                anyString()
        )).thenReturn(List.of(Map.of("nom", "RAKOTO")));

        RechercheStructureeAgentService.ResultatRecherche result =
                service.rechercher("""
                        {
                          "filters": [
                            {"field":"age","operator":"LT","value":30},
                            {"field":"sexe","operator":"EQ","value":"femmes"},
                            {"field":"date_embauche","operator":"WITHIN_LAST_YEARS","value":3}
                          ],
                          "limit": 10,
                          "sortBy": "age",
                          "sortDirection": "DESC"
                        }
                        """);

        assertEquals(1, result.total());
        assertEquals(10, result.limite());
        org.mockito.ArgumentCaptor<List<RechercheStructureeAgentService.Filtre>>
                filtersCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).rechercher(
                filtersCaptor.capture(),
                eq(10),
                eq("age"),
                eq("DESC")
        );
        assertEquals(3, filtersCaptor.getValue().size());
        assertEquals("F", filtersCaptor.getValue().get(1).valeur());
        assertEquals(
                RechercheStructureeAgentService.Operateur.WITHIN_LAST_YEARS,
                filtersCaptor.getValue().get(2).operateur()
        );
    }

    @Test
    void refuseChampsEtOperateursNonAutorises() {
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[{"field":"e.id) OR 1=1 --","operator":"EQ","value":1}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[{"field":"nom","operator":"SQL","value":"Rakoto"}]}
                """));
    }

    @Test
    void refuseRechercheApproxImprecisePeriodeAmbigueEtDatesInvalides() {
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[{"field":"nom","operator":"FUZZY","value":"Rak"}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[{"field":"date_embauche","operator":"WITHIN_LAST_YEARS","value":0}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[{"field":"date_embauche","operator":"BEFORE","value":"2020-02-30"}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[{"field":"age","operator":"BETWEEN","value":40,"upperValue":30}]}
                """));
    }

    @Test
    void refuseRequeteSansFiltresEtTriNonAutorise() {
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> service.rechercher("""
                {"filters":[{"field":"age","operator":"LT","value":30}],"sortBy":"e.id"}
                """));
    }
}
