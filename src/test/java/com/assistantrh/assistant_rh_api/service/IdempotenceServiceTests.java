package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.IdempotenceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotenceServiceTests {

    private final IdempotenceRepository repository = mock(IdempotenceRepository.class);
    private final IdempotenceService service = new IdempotenceService(repository, new ObjectMapper());
    private final AtomicInteger ecritures = new AtomicInteger();

    private ResponseEntity<?> ecrire() {
        ecritures.incrementAndGet();
        return ResponseEntity.status(201).body(Map.of("id", 7, "statut", "CREE"));
    }

    @Test
    void sansCleLEcritureSExecuteNormalement() {
        ResponseEntity<?> reponse = service.executer(42, null, "POST /x", Map.of("a", 1), this::ecrire);

        assertEquals(201, reponse.getStatusCode().value());
        assertEquals(1, ecritures.get());
        verify(repository, never()).reserver(anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void premiereRequeteSExecuteEtSaReponseEstMemorisee() {
        when(repository.reserver(eq(42), eq("cle-1"), eq("POST /x"), anyString())).thenReturn(true);

        service.executer(42, "cle-1", "POST /x", Map.of("a", 1), this::ecrire);

        assertEquals(1, ecritures.get());
        verify(repository).terminer(eq(42), eq("cle-1"), eq(201), anyString());
    }

    @Test
    void rejeuDeLaMemeRequeteRenvoieLaMemeReponseSansReecrire() {
        ArgumentCaptor<String> empreinte = ArgumentCaptor.forClass(String.class);
        when(repository.reserver(eq(42), eq("cle-1"), eq("POST /x"), empreinte.capture()))
                .thenReturn(true, false);
        service.executer(42, "cle-1", "POST /x", Map.of("a", 1), this::ecrire);
        when(repository.trouver(42, "cle-1")).thenReturn(Optional.of(
                new IdempotenceRepository.Enregistrement(
                        "POST /x", empreinte.getAllValues().get(0), 201, "{\"id\":7,\"statut\":\"CREE\"}"
                )
        ));

        ResponseEntity<?> rejeu = service.executer(42, "cle-1", "POST /x", Map.of("a", 1), this::ecrire);

        assertEquals(1, ecritures.get());
        assertEquals(201, rejeu.getStatusCode().value());
        assertEquals(Map.of("id", 7, "statut", "CREE"), rejeu.getBody());
    }

    @Test
    void cleReutiliseePourUneAutreRequeteOuEnCoursRepond409() {
        when(repository.reserver(anyInt(), anyString(), anyString(), anyString())).thenReturn(false);
        when(repository.trouver(42, "cle-1")).thenReturn(Optional.of(
                new IdempotenceRepository.Enregistrement("POST /x", "autre-empreinte", 201, "{}")
        ));
        ResponseEntity<?> autre = service.executer(42, "cle-1", "POST /x", Map.of("a", 2), this::ecrire);
        assertEquals(409, autre.getStatusCode().value());
        assertEquals("IDEMPOTENCY_KEY_REUSED", ((Map<?, ?>) autre.getBody()).get("code"));

        when(repository.trouver(42, "cle-2")).thenAnswer(invocation -> Optional.of(
                new IdempotenceRepository.Enregistrement(
                        "POST /x", empreinteDe("POST /x", Map.of("a", 1)), null, null
                )
        ));
        ResponseEntity<?> enCours = service.executer(42, "cle-2", "POST /x", Map.of("a", 1), this::ecrire);
        assertEquals(409, enCours.getStatusCode().value());
        assertEquals("IDEMPOTENCY_KEY_IN_PROGRESS", ((Map<?, ?>) enCours.getBody()).get("code"));

        assertEquals(0, ecritures.get());
    }

    @Test
    void unRefusOuUneErreurLibereLaClePourUnNouvelEssai() {
        when(repository.reserver(anyInt(), anyString(), anyString(), anyString())).thenReturn(true);

        ResponseEntity<?> refus = service.executer(42, "cle-1", "POST /x", Map.of(),
                () -> ResponseEntity.badRequest().body(Map.of("message", "Type inconnu.")));
        assertEquals(400, refus.getStatusCode().value());
        verify(repository).liberer(42, "cle-1");

        assertThrows(IllegalStateException.class, () -> service.executer(42, "cle-2", "POST /x", Map.of(),
                () -> { throw new IllegalStateException("panne"); }));
        verify(repository).liberer(42, "cle-2");
        verify(repository, never()).terminer(anyInt(), anyString(), anyInt(), anyString());
    }

    @Test
    void cleTropLongueRepond400SansEcrire() {
        ResponseEntity<?> reponse = service.executer(42, "x".repeat(101), "POST /x", Map.of(), this::ecrire);

        assertEquals(400, reponse.getStatusCode().value());
        assertEquals(0, ecritures.get());
    }

    /** Même calcul que le service : empreinte de la route et du corps. */
    private String empreinteDe(String route, Object corps) {
        ArgumentCaptor<String> captee = ArgumentCaptor.forClass(String.class);
        IdempotenceRepository autre = mock(IdempotenceRepository.class);
        when(autre.reserver(anyInt(), anyString(), anyString(), captee.capture())).thenReturn(true);
        new IdempotenceService(autre, new ObjectMapper())
                .executer(1, "k", route, corps, () -> ResponseEntity.ok(Map.of()));
        return captee.getValue();
    }
}
