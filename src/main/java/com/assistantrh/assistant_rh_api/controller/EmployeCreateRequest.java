package com.assistantrh.assistant_rh_api.controller;

import java.time.LocalDate;

public record EmployeCreateRequest(
        Integer acteurId,
        String matricule,
        String nom,
        String prenom,
        String sexe,
        String adresse,
        String cin,
        String telephone,
        LocalDate dateNaissance,
        String lieuNaissance,
        LocalDate dateEmbauche,

        Integer posteId,
        Integer serviceId,

        Integer typeEmploiId,
        Integer categorieId,

        String lieuTravail,
        String photo,

        Integer userId,
        Long localiteResidenceId,
        // Grade de départ, dans le parcours du type d'emploi : l'agent commence sa carrière à son embauche.
        Integer echelonId
) {
}