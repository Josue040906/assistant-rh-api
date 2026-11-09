package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.pdf.PieceScanneeStorage;
import com.assistantrh.assistant_rh_api.repository.PieceScanneeRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PieceScanneeServiceTests {
    private final PieceScanneeRepository repository = mock(PieceScanneeRepository.class);
    private final PieceScanneeStorage storage = mock(PieceScanneeStorage.class);
    private final PieceScanneeService service = new PieceScanneeService(repository, storage);
    private final byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @Test
    void deposeLaPieceSurUnDiplomeExistantAvecUnNomSain() {
        when(repository.cibleExiste("DIPLOME", 17)).thenReturn(true);
        when(storage.enregistrer(photo)).thenReturn(new PieceScanneeStorage.Fichier("pieces/piece-x.jpg", "image/jpeg"));
        when(repository.ajouter("DIPLOME", 17, "dossier_diplome.jpg", "image/jpeg", 3, "pieces/piece-x.jpg", 4))
                .thenReturn(8);

        Map<String, Object> piece = service.deposer(4, "diplome", 17, "dossier/diplome.jpg", photo);

        assertEquals(8, piece.get("id"));
        assertEquals("image/jpeg", piece.get("typeMime"));
    }

    @Test
    void refuseUneCibleInconnueOuAbsenteAvantDeStockerQuoiQueCeSoit() {
        when(repository.cibleExiste("DOCUMENT", 99)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.deposer(4, "CIN", 1, "a.jpg", photo));
        assertThrows(IllegalArgumentException.class, () -> service.deposer(4, "DOCUMENT", 99, "a.jpg", photo));
        verifyNoInteractions(storage);
    }

    @Test
    void neLaissePasDeFichierOrphelinSiLaBaseRefuse() throws Exception {
        when(repository.cibleExiste("DOCUMENT", 5)).thenReturn(true);
        when(storage.enregistrer(photo)).thenReturn(new PieceScanneeStorage.Fichier("pieces/piece-x.jpg", "image/jpeg"));
        when(repository.ajouter(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("base indisponible"));

        assertThrows(IllegalStateException.class, () -> service.deposer(4, "DOCUMENT", 5, "a.jpg", photo));
        verify(storage).supprimer("pieces/piece-x.jpg");
    }

    @Test
    void litLaPieceAvecSonNomEtSonType() throws Exception {
        when(repository.trouver(8)).thenReturn(Optional.of(Map.of(
                "id", 8, "cible_type", "DIPLOME", "cible_id", 17,
                "nom_fichier", "diplome.jpg", "type_mime", "image/jpeg", "chemin", "pieces/piece-x.jpg")));
        when(storage.lire("pieces/piece-x.jpg")).thenReturn(Optional.of(photo));

        PieceScanneeService.Contenu contenu = service.lire(8).orElseThrow();

        assertEquals("diplome.jpg", contenu.nomFichier());
        assertEquals("image/jpeg", contenu.typeMime());
        assertArrayEquals(photo, contenu.octets());
        assertEquals(Optional.empty(), service.lire(9));
    }
}
