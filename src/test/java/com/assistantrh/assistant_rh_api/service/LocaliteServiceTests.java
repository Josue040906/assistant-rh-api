package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Localite;
import com.assistantrh.assistant_rh_api.repository.LocaliteRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocaliteServiceTests {

    private final LocaliteRepository repository = mock(LocaliteRepository.class);
    private final LocaliteService service = new LocaliteService(repository);

    @Test
    void retourneLesEnfantsSeulementAuNiveauCorrespondant() {
        when(repository.findById(4)).thenReturn(Optional.of(
                new Localite(4L, "REGION", "Analamanga", null, "MG11", "HDX_OCHA")
        ));
        when(repository.findChildren(4, "DISTRICT")).thenReturn(List.of(
                new Localite(5L, "DISTRICT", "Antananarivo", 4L, "MG111", "HDX_OCHA")
        ));

        assertEquals(1, service.districts(4).size());
        verify(repository).findChildren(4, "DISTRICT");
    }

    @Test
    void refuseUnIdentifiantParentDeNiveauIncoherent() {
        when(repository.findById(4)).thenReturn(Optional.of(
                new Localite(4L, "DISTRICT", "Antananarivo", 3L, "MG111", "HDX_OCHA")
        ));

        assertThrows(
                LocaliteInvalideException.class,
                () -> service.districts(4)
        );
    }

    @Test
    void valideLaSelectionDesCommunesEtFokontany() {
        when(repository.existsSelectableById(12)).thenReturn(true);
        when(repository.existsSelectableById(13)).thenReturn(false);

        service.validerLocaliteResidence(12L);
        assertThrows(
                LocaliteInvalideException.class,
                () -> service.validerLocaliteResidence(13L)
        );
    }

    @Test
    void limiteLaPaginationDeRecherche() {
        assertThrows(
                LocaliteInvalideException.class,
                () -> service.recherche("Anta", 0, 101)
        );
    }
}
