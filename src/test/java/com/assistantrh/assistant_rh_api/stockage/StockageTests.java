package com.assistantrh.assistant_rh_api.stockage;

import com.assistantrh.assistant_rh_api.pdf.PhotoAgentStorage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockageTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @TempDir
    Path racine;

    @Test
    void leStockageLocalNAccepteQueDesClesSuresEtNEcrasePasUnFichier() {
        StockageLocal stockage = new StockageLocal(racine);
        stockage.ecrire("employes/agent-1-a.png", PNG, "image/png");

        assertArrayEquals(PNG, stockage.lire("employes/agent-1-a.png").orElseThrow());
        assertThrows(IllegalStateException.class, () -> stockage.ecrire("employes/agent-1-a.png", PNG, "image/png"));
        assertThrows(IllegalArgumentException.class, () -> stockage.lire("employes/../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> stockage.lire("autre/fichier.png"));
        assertThrows(IllegalArgumentException.class, () -> stockage.lire("employes/sous/dossier.png"));
        stockage.supprimer("employes/agent-1-a.png");
        assertTrue(stockage.lire("employes/agent-1-a.png").isEmpty());
    }

    @Test
    void unePhotoDoitEtreUneImageEtGardeSonLienHabituel() {
        PhotoAgentStorage photos = new PhotoAgentStorage(new StockageLocal(racine));

        String lien = photos.enregistrer(7, PNG);

        assertTrue(lien.matches("/uploads/employes/agent-7-[0-9a-f-]{36}\\.png"), lien);
        assertArrayEquals(PNG, photos.lire(lien.substring("/uploads/employes/".length())).orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> photos.enregistrer(7, "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class,
                () -> photos.enregistrer(7, "<html>".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> photos.lire("../../secret.png"));
        assertTrue(photos.lire("agent-7-essai.png").isEmpty());
    }

    @Test
    void supabaseRecoitLaCleEtLeCheminDuCompartimentPrive() throws IOException {
        Map<String, byte[]> fichiers = new HashMap<>();
        List<String> appels = new ArrayList<>();
        HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/storage/v1/object/", echange -> {
            String chemin = echange.getRequestURI().getPath();
            appels.add(echange.getRequestMethod() + " " + chemin + " apikey=" + echange.getRequestHeaders().getFirst("apikey")
                    + " auth=" + echange.getRequestHeaders().getFirst("Authorization"));
            String cle = chemin.replaceFirst("^/storage/v1/object/(?:authenticated/)?sygpers/", "");
            byte[] reponse = new byte[0];
            int statut;
            switch (echange.getRequestMethod()) {
                case "POST" -> {
                    fichiers.put(cle, echange.getRequestBody().readAllBytes());
                    statut = 200;
                }
                case "GET" -> {
                    reponse = fichiers.get(cle);
                    statut = reponse == null ? 400 : 200;
                    if (reponse == null) {
                        reponse = "{\"statusCode\":\"404\",\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8);
                    }
                }
                default -> {
                    fichiers.remove(cle);
                    statut = 200;
                }
            }
            echange.sendResponseHeaders(statut, reponse.length == 0 ? -1 : reponse.length);
            if (reponse.length > 0) {
                echange.getResponseBody().write(reponse);
            }
            echange.close();
        });
        serveur.start();
        try {
            String url = "http://127.0.0.1:" + serveur.getAddress().getPort() + "/";
            StockageSupabase stockage = new StockageSupabase(url, "sb_secret_essai", "sygpers");

            stockage.ecrire("pieces/piece-1.png", PNG, "image/png");
            assertArrayEquals(PNG, stockage.lire("pieces/piece-1.png").orElseThrow());
            assertTrue(stockage.lire("pieces/absente.png").isEmpty());
            stockage.supprimer("pieces/piece-1.png");

            assertEquals("POST /storage/v1/object/sygpers/pieces/piece-1.png apikey=sb_secret_essai auth=null", appels.get(0));
            assertEquals("GET /storage/v1/object/authenticated/sygpers/pieces/piece-1.png apikey=sb_secret_essai auth=null",
                    appels.get(1));
            assertEquals("DELETE /storage/v1/object/sygpers/pieces/piece-1.png apikey=sb_secret_essai auth=null", appels.get(3));

            new StockageSupabase(url, "eyJancienne", "sygpers").lire("pieces/piece-1.png");
            assertTrue(appels.get(4).endsWith("auth=Bearer eyJancienne"));
        } finally {
            serveur.stop(0);
        }
    }
}
