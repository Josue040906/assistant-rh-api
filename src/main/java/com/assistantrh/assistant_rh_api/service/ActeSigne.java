package com.assistantrh.assistant_rh_api.service;

/**
 * Une action directe (avancement, mutation, reclassement, titularisation,
 * retraite) enregistre une décision déjà signée sur papier : elle exige la
 * référence de l'acte et son signataire. Avec une demande acceptée, on applique
 * la demande ; sans demande, c'est cet acte qui fait foi.
 */
public final class ActeSigne {

    private ActeSigne() {
    }

    /** « Arrêté n° 123/2026, signé par le Directeur des RH » ; refuse un acte incomplet. */
    public static String texte(String reference, String signataire) {
        String acte = reference == null ? "" : reference.trim();
        String signe = signataire == null ? "" : signataire.trim();
        if (acte.isEmpty() || signe.isEmpty()) {
            throw new IllegalArgumentException(
                    "Indiquez la référence de l'acte signé et son signataire : "
                            + "une action directe enregistre une décision déjà signée.");
        }
        String texte = acte + ", signé par " + signe;
        if (texte.length() > 300) {
            throw new IllegalArgumentException("La référence de l'acte et son signataire sont trop longs.");
        }
        return texte;
    }
}
