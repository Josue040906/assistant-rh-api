package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Ville;
import com.assistantrh.assistant_rh_api.repository.VilleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VilleServiceTests {

    private final VilleRepository repository = mock(VilleRepository.class);
    private final VilleService service = new VilleService(repository);

    @Test
    void exposeLesVillesDuReferentielUrbain() {
        when(repository.findAll()).thenReturn(List.of(new Ville("Toamasina")));

        assertEquals(List.of(new Ville("Toamasina")), service.obtenirVilles());
    }

    @Test
    void normaliseLeNomSelectionneEtRefuseUneValeurLibre() {
        when(repository.findName("Toamasina"))
                .thenReturn(Optional.of("Toamasina"));

        assertEquals(
                "Toamasina",
                service.validerVille(" Toamasina ", "Le lieu de travail")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validerVille("Village inconnu", "Le lieu de travail")
        );
    }
}
