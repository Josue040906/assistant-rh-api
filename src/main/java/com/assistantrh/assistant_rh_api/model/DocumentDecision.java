package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * La réponse reçue sur papier à une demande : qui l'a signée, le numéro de la
 * décision s'il existe, sa date, le motif d'un refus — et qui l'a saisie.
 */
public record DocumentDecision(
        boolean acceptee,
        String signataire,
        String reference,
        LocalDate date,
        String motif,
        Integer saisiePar,
        String saisieParNom,
        OffsetDateTime saisieLe
) {
}
