package com.assistantrh.assistant_rh_api.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chaque type de notification écrit par le code doit être permis par la contrainte
 * notification_type_check de la dernière migration qui la définit. Les tests
 * n'utilisent pas PostgreSQL : sans ce contrôle, un type oublié ne se voit qu'en
 * production (la réponse signée d'une demande échouait ainsi en 503).
 */
class NotificationTypesTests {

    private static final Pattern TYPE = Pattern.compile("[\"'](NOUVEL_AGENT|ANCIENNETE_[A-Z_]+|DOCUMENT_[A-Z_]+)[\"']");

    @Test
    void chaqueTypeEcritParLeCodeEstPermisParLaBase() throws IOException {
        Set<String> utilises = types(Files.readString(
                Path.of("src/main/java/com/assistantrh/assistant_rh_api/repository/NotificationRepository.java")));

        Path derniere;
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            derniere = migrations
                    .filter(fichier -> lire(fichier).contains("ADD CONSTRAINT notification_type_check"))
                    .max(Comparator.comparing(fichier -> fichier.getFileName().toString()))
                    .orElseThrow();
        }
        String contrainte = lire(derniere).substring(lire(derniere).indexOf("ADD CONSTRAINT notification_type_check"));
        Set<String> permis = types(contrainte.substring(0, contrainte.indexOf(';')));

        Set<String> manquants = new TreeSet<>(utilises);
        manquants.removeAll(permis);
        assertTrue(!utilises.isEmpty() && manquants.isEmpty(),
                "Types absents de notification_type_check (" + derniere.getFileName() + ") : " + manquants);
    }

    private static Set<String> types(String texte) {
        Set<String> types = new TreeSet<>();
        Matcher m = TYPE.matcher(texte);
        while (m.find()) {
            types.add(m.group(1));
        }
        return types;
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException erreur) {
            throw new IllegalStateException(erreur);
        }
    }
}
