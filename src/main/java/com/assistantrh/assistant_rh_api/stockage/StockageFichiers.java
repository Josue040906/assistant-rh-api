package com.assistantrh.assistant_rh_api.stockage;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Le stockage des fichiers de l'application : photos des agents, pièces
 * scannées, PDF des demandes. Une clé désigne un fichier, sous la forme
 * « dossier/nom » (par exemple « employes/agent-3-….jpg »).
 * <p>
 * Sur un poste de développement, les fichiers vont dans le dossier uploads/ ;
 * en ligne, dans un stockage privé (Supabase), car le disque de l'hébergeur
 * est effacé à chaque redémarrage. Voir {@link StockageConfig}.
 */
public interface StockageFichiers {

    /** Les seules clés admises : un dossier connu, puis un nom sans chemin. */
    Pattern CLE = Pattern.compile("(?:documents|pieces|employes)/[A-Za-z0-9][A-Za-z0-9._-]*");

    /** Enregistre un nouveau fichier ; une clé déjà prise est refusée. */
    void ecrire(String cle, byte[] contenu, String typeMime);

    /** Le contenu du fichier, ou rien s'il n'existe pas. */
    Optional<byte[]> lire(String cle);

    /** Supprime le fichier s'il existe. */
    void supprimer(String cle);

    /** Où vont les fichiers, pour le journal de démarrage. */
    String description();

    static String verifierCle(String cle) {
        if (cle == null || !CLE.matcher(cle).matches() || cle.contains("..")) {
            throw new IllegalArgumentException("Le chemin du fichier stocké est invalide.");
        }
        return cle;
    }
}
