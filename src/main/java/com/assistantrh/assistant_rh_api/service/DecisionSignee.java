package com.assistantrh.assistant_rh_api.service;

/**
 * Une action directe (avancement, mutation, reclassement, titularisation,
 * retraite) enregistre une décision déjà signée sur papier : on indique qui
 * l'a signée. Avec une demande en cours, on passe par la demande ; sans
 * demande, c'est cette décision qui fait foi.
 */
public final class DecisionSignee {

    private DecisionSignee() {
    }

    /** « Décision signée par Lova ANDRIANINAY — Chef de service, Service du Personnel ». */
    public static String texte(String signataire) {
        String signe = signataire == null ? "" : signataire.trim();
        if (signe.isEmpty()) {
            throw new IllegalArgumentException(
                    "Indiquez qui a signé la décision : une action directe enregistre une décision déjà signée.");
        }
        if (signe.length() > 255) {
            throw new IllegalArgumentException("Le nom du signataire est trop long.");
        }
        return "Décision signée par " + signe;
    }
}
