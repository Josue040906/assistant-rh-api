package com.assistantrh.assistant_rh_api.service;

/**
 * Une action directe (avancement, mutation, reclassement, titularisation,
 * retraite) enregistre une décision déjà signée sur papier : son signataire est
 * obligatoire, son numéro (arrêté, décision…) l'est seulement s'il existe. Avec
 * une demande en cours, on passe par la demande ; sans demande, c'est cette
 * décision qui fait foi.
 */
public final class DecisionSignee {

    private DecisionSignee() {
    }

    /** « Décision n° 123/2026, signée par le Directeur des RH », ou « Décision signée par … » sans numéro. */
    public static String texte(String numero, String signataire) {
        String signe = signataire == null ? "" : signataire.trim();
        String reference = numero == null ? "" : numero.trim();
        if (signe.isEmpty()) {
            throw new IllegalArgumentException(
                    "Indiquez qui a signé la décision : une action directe enregistre une décision déjà signée.");
        }
        String texte = reference.isEmpty()
                ? "Décision signée par " + signe
                : "Décision " + reference + ", signée par " + signe;
        if (texte.length() > 300) {
            throw new IllegalArgumentException("Le numéro de la décision et son signataire sont trop longs.");
        }
        return texte;
    }
}
