package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.RetraiteRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Départ à la retraite. La durée de service exigée est une donnée du type de
 * document RETRAITE : le code la lit, il ne la fixe jamais.
 */
@Service
public class RetraiteService {
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentRhRepository documentRhRepository;
    private final RetraiteRepository retraiteRepository;
    private final SituationCarriereRepository situationRepository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;
    private final DemandesLieesService demandesLiees;

    public RetraiteService(
            DocumentRhRepository documentRhRepository,
            RetraiteRepository retraiteRepository,
            SituationCarriereRepository situationRepository,
            EmployeRepository employeRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService,
            DemandesLieesService demandesLiees
    ) {
        this.documentRhRepository = documentRhRepository;
        this.retraiteRepository = retraiteRepository;
        this.situationRepository = situationRepository;
        this.employeRepository = employeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
        this.demandesLiees = demandesLiees;
    }

    /** Où en est l'agent : années exigées, date à laquelle il les atteint, départ déjà pris. */
    public Map<String, Object> analyser(Integer employeId) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'agent est invalide.");
        }
        LocalDate aujourdhui = LocalDate.now();
        LocalDate possible = documentRhRepository.dateRetraitePossible(employeId).orElse(null);
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("employeId", employeId);
        resultat.put("anneesRequises", documentRhRepository.ancienneteRetraiteAnnees().orElse(null));
        resultat.put("datePossible", possible);
        resultat.put("joursRestants", possible == null || !possible.isAfter(aujourdhui)
                ? 0 : ChronoUnit.DAYS.between(aujourdhui, possible));
        resultat.put("possible", possible != null && !possible.isAfter(aujourdhui));
        resultat.put("dateRetraite", retraiteRepository.dateRetraite(employeId).orElse(null));
        return resultat;
    }

    /** Le départ, par le bouton direct ou par une demande appliquée (referenceActe). */
    @Transactional
    public Map<String, Object> prendreRetraite(
            Integer employeId, Integer acteurId, LocalDate dateEffet, String referenceActe
    ) {
        if (acteurId == null || acteurId <= 0 || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel.");
        }
        if (dateEffet == null || dateEffet.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date de départ est obligatoire et ne peut pas être future.");
        }
        if (employeId == null || !situationRepository.verrouillerEmploye(employeId)) {
            throw new IllegalArgumentException("L'agent demandé n'existe pas.");
        }
        if (retraiteRepository.dateRetraite(employeId).isPresent()) {
            throw new IllegalArgumentException("Cet agent est déjà parti à la retraite.");
        }
        LocalDate possible = documentRhRepository.dateRetraitePossible(employeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La date d'embauche de l'agent ou la durée de service exigée n'est pas renseignée."));
        if (dateEffet.isBefore(possible)) {
            throw new IllegalArgumentException("La durée de service exigée sera atteinte le "
                    + possible.format(JOUR) + " : le départ ne peut pas la précéder.");
        }
        if (retraiteRepository.enregistrerRetraite(employeId, dateEffet) != 1) {
            throw new IllegalStateException("Le départ à la retraite n'a pas pu être enregistré.");
        }
        Object matricule = employeRepository.findById(employeId)
                .map(agent -> agent.get("matricule")).orElse(employeId);
        activiteService.enregistrer(acteurId, employeId, "RETRAITE",
                "Départ à la retraite de l'agent " + matricule + " le " + dateEffet.format(JOUR)
                        + (referenceActe == null ? "" : " (" + referenceActe + ")"));
        demandesLiees.retraite(employeId, acteurId);
        return Map.of("employeId", employeId, "message", "Départ à la retraite enregistré.");
    }
}
