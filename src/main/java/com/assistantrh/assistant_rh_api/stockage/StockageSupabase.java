package com.assistantrh.assistant_rh_api.stockage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Les fichiers dans un compartiment PRIVÉ de Supabase Storage. Seule l'API
 * possède la clé secrète : les fichiers ne sont jamais accessibles directement,
 * l'API les sert après avoir vérifié l'utilisateur, comme avec le disque local.
 */
public final class StockageSupabase implements StockageFichiers {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String base;
    private final String cleSecrete;
    private final String compartiment;

    public StockageSupabase(String url, String cleSecrete, String compartiment) {
        this.base = Objects.requireNonNull(url).trim().replaceAll("/+$", "");
        this.cleSecrete = Objects.requireNonNull(cleSecrete).trim();
        this.compartiment = Objects.requireNonNull(compartiment).trim();
    }

    @Override
    public void ecrire(String cle, byte[] contenu, String typeMime) {
        HttpResponse<byte[]> reponse = envoyer(requete(cle, "/storage/v1/object/")
                .header("Content-Type", typeMime == null ? "application/octet-stream" : typeMime)
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(contenu))
                .build());
        if (reponse.statusCode() / 100 != 2) {
            throw new IllegalStateException("Le stockage des fichiers a refusé l'envoi (HTTP "
                    + reponse.statusCode() + ").");
        }
    }

    @Override
    public Optional<byte[]> lire(String cle) {
        HttpResponse<byte[]> reponse = envoyer(requete(cle, "/storage/v1/object/authenticated/").GET().build());
        if (reponse.statusCode() == 200) {
            return Optional.of(reponse.body());
        }
        // Un fichier absent répond 400 ou 404 selon les versions de Supabase.
        if (reponse.statusCode() == 400 || reponse.statusCode() == 404) {
            return Optional.empty();
        }
        throw new IllegalStateException("Le stockage des fichiers ne répond pas (HTTP " + reponse.statusCode() + ").");
    }

    @Override
    public void supprimer(String cle) {
        HttpResponse<byte[]> reponse = envoyer(requete(cle, "/storage/v1/object/").DELETE().build());
        if (reponse.statusCode() / 100 != 2 && reponse.statusCode() != 400 && reponse.statusCode() != 404) {
            throw new IllegalStateException("Le stockage des fichiers a refusé la suppression (HTTP "
                    + reponse.statusCode() + ").");
        }
    }

    @Override
    public String description() {
        return "Supabase Storage, compartiment « " + compartiment + " »";
    }

    private HttpRequest.Builder requete(String cle, String chemin) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(base + chemin + compartiment + "/" + StockageFichiers.verifierCle(cle)))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", cleSecrete);
        // Une ancienne clé « service_role » est un jeton : elle va aussi dans Authorization.
        if (cleSecrete.startsWith("eyJ")) {
            builder.header("Authorization", "Bearer " + cleSecrete);
        }
        return builder;
    }

    private HttpResponse<byte[]> envoyer(HttpRequest requete) {
        try {
            return client.send(requete, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Le stockage des fichiers est injoignable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("L'accès au stockage des fichiers a été interrompu.", exception);
        }
    }
}
