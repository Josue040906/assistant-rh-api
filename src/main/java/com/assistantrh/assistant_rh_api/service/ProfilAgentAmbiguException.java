package com.assistantrh.assistant_rh_api.service;

import java.util.List;
import java.util.Map;

/**
 * Plusieurs agents portent exactement l'identité demandée : l'API ne choisit
 * pas à la place de l'utilisateur et renvoie les candidats pour qu'il précise.
 */
public class ProfilAgentAmbiguException extends RuntimeException {
    private final List<Map<String, Object>> candidats;

    public ProfilAgentAmbiguException(String requete, List<Map<String, Object>> candidats) {
        super("Plusieurs agents correspondent à « " + requete
                + " ». Précisez le matricule de l'agent recherché.");
        this.candidats = List.copyOf(candidats);
    }

    public List<Map<String, Object>> getCandidats() {
        return candidats;
    }
}
