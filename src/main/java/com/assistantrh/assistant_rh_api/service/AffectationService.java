package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class AffectationService {

    private final AffectationRepository affectationRepository;
    private final EmployeRepository employeRepository;
    private final ActiviteService activiteService;
    private final PosteService posteService;
    private final ServiceService serviceService;
    private final UtilisateurService utilisateurService;

    public AffectationService(
            AffectationRepository affectationRepository,
            EmployeRepository employeRepository,
            ActiviteService activiteService,
            PosteService posteService,
            ServiceService serviceService,
            UtilisateurService utilisateurService
    ) {
        this.affectationRepository = affectationRepository;
        this.employeRepository = employeRepository;
        this.activiteService = activiteService;
        this.posteService = posteService;
        this.serviceService = serviceService;
        this.utilisateurService = utilisateurService;
    }

    /**
     * Modifie l'affectation courante d'un agent.
     *
     * L'ancienne affectation est clôturée et une nouvelle
     * affectation est créée dans la table affectation.
     */
    @Transactional
    public void modifierAffectation(
            Integer employeId,
            Integer acteurId,
            Integer posteId,
            Integer serviceId,
            String lieuTravail,
            LocalDate dateEffet,
            String referenceActe,
            String observation
    ) {

        validateIds(
                employeId,
                acteurId,
                posteId,
                serviceId
        );

        validateDateEffet(dateEffet);

        Map<String, Object> employe =
                employeRepository.findById(employeId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "L'agent demandé n'existe pas."
                                )
                        );

        /*
         * Le contrôle porte sur l'agent concerné :
         * il doit actuellement être rattaché au Service du Personnel.
         */
        Object employeServiceId =
                employe.get("service_id");

        if (!(employeServiceId instanceof Number)
                || ((Number) employeServiceId).intValue() != 2) {

            throw new IllegalArgumentException(
                    "Cet agent n'appartient pas au Service du Personnel."
            );
        }

        Map<String, Object> service =
                serviceService.getServiceById(
                        Long.valueOf(serviceId)
                );

        if (service == null) {
            throw new IllegalArgumentException(
                    "Le service demandé n'existe pas."
            );
        }

        Map<String, Object> poste =
                posteService.getPosteById(
                        Long.valueOf(posteId)
                );

        if (poste == null) {
            throw new IllegalArgumentException(
                    "Le poste demandé n'existe pas."
            );
        }

        /*
         * Vérifie que le poste appartient bien au service sélectionné.
         */
        Object posteServiceId =
                poste.get("service_id");

        if (posteServiceId == null
                || Integer.parseInt(
                posteServiceId.toString()
        ) != serviceId) {

            throw new IllegalArgumentException(
                    "Le poste sélectionné n'appartient pas au service sélectionné."
            );
        }

        Map<String, Object> affectationActuelle =
                affectationRepository.findActiveByEmployeId(
                        employeId
                );

        if (affectationActuelle == null) {
            throw new IllegalArgumentException(
                    "Aucune affectation active n'a été trouvée pour cet agent."
            );
        }

        Integer ancienneAffectationId =
                toInteger(
                        affectationActuelle.get("id")
                );

        Integer ancienPosteId =
                toInteger(
                        affectationActuelle.get("poste_id")
                );

        Integer ancienServiceId =
                toInteger(
                        affectationActuelle.get("service_id")
                );

        LocalDate ancienneDateDebut =
                toLocalDate(
                        affectationActuelle.get("date_debut")
                );

        String ancienLieuTravail =
                employe.get("lieu_travail") == null
                        ? null
                        : employe.get("lieu_travail").toString();

        String nouveauLieuTravail =
                normalize(lieuTravail);

        /*
         * On ne peut pas créer une nouvelle affectation
         * avant le début de l'affectation actuelle.
         */
        if (ancienneDateDebut != null
                && dateEffet.isBefore(ancienneDateDebut)) {

            throw new IllegalArgumentException(
                    "La date d'effet ne peut pas être antérieure au début de l'affectation actuelle."
            );
        }

        /*
         * Refuser une nouvelle affectation strictement identique.
         */
        if (ancienPosteId != null
                && ancienPosteId.equals(posteId)
                && ancienServiceId != null
                && ancienServiceId.equals(serviceId)
                && equalsNullable(
                ancienLieuTravail,
                nouveauLieuTravail
        )) {

            throw new IllegalArgumentException(
                    "La nouvelle affectation est identique à l'affectation actuelle."
            );
        }

        Date sqlDateEffet =
                Date.valueOf(dateEffet);

        /*
         * 1. Clôture de l'ancienne affectation.
         */
        int fermeture =
                affectationRepository.fermerAffectation(
                        ancienneAffectationId,
                        sqlDateEffet
                );

        if (fermeture != 1) {
            throw new IllegalStateException(
                    "L'affectation actuelle n'a pas pu être clôturée."
            );
        }

        /*
         * 2. Création de la nouvelle affectation.
         */
        int creation =
                affectationRepository.creer(
                        employeId,
                        posteId,
                        serviceId,
                        sqlDateEffet,
                        normalize(referenceActe),
                        normalize(observation)
                );

        if (creation != 1) {
            throw new IllegalStateException(
                    "La nouvelle affectation n'a pas pu être créée."
            );
        }

        /*
         * 3. Le lieu de travail reste une information directe
         * de l'agent. Le poste et le service, eux, sont uniquement
         * dans affectation.
         */
        int modificationLieu =
                employeRepository.updateLieuTravail(
                        employeId,
                        nouveauLieuTravail
                );

        if (modificationLieu != 1) {
            throw new IllegalStateException(
                    "Le lieu de travail de l'agent n'a pas pu être mis à jour."
            );
        }

        String ancienPoste =
                String.valueOf(
                        affectationActuelle.get("poste")
                );

        String ancienService =
                String.valueOf(
                        affectationActuelle.get("service")
                );

        String nouveauPoste =
                String.valueOf(
                        poste.get("intitule")
                );

        String nouveauService =
                String.valueOf(
                        service.get("nom")
                );

        activiteService.enregistrer(
                acteurId,
                employeId,
                "MODIFICATION_AFFECTATION",
                "Modification de l'affectation de l'agent "
                        + employe.get("matricule")
                        + " : "
                        + ancienPoste
                        + " / "
                        + ancienService
                        + " -> "
                        + nouveauPoste
                        + " / "
                        + nouveauService
        );
    }

    public Map<String, Object> getAffectationActuelle(
            Integer employeId
    ) {

        if (employeId == null || employeId <= 0) {
            return null;
        }

        return affectationRepository.findActiveByEmployeId(
                employeId
        );
    }

    public List<Map<String, Object>> getHistorique(
            Integer employeId
    ) {

        if (employeId == null || employeId <= 0) {
            return List.of();
        }

        return affectationRepository.findByEmployeId(
                employeId
        );
    }

    private void validateIds(
            Integer employeId,
            Integer acteurId,
            Integer posteId,
            Integer serviceId
    ) {

        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est obligatoire."
            );
        }

        if (acteurId == null || acteurId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'acteur est obligatoire."
            );
        }

        if (!utilisateurService.estAgentSpersActif(
                acteurId
        )) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas autorisé à effectuer cette opération dans SYGPERS."
            );
        }

        if (posteId == null || posteId <= 0) {
            throw new IllegalArgumentException(
                    "Le poste est obligatoire."
            );
        }

        if (serviceId == null || serviceId <= 0) {
            throw new IllegalArgumentException(
                    "Le service est obligatoire."
            );
        }
    }

    private void validateDateEffet(
            LocalDate dateEffet
    ) {

        if (dateEffet == null) {
            throw new IllegalArgumentException(
                    "La date d'effet est obligatoire."
            );
        }
    }

    private String normalize(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private boolean equalsNullable(
            String first,
            String second
    ) {

        if (first == null) {
            return second == null;
        }

        return first.equals(second);
    }

    private Integer toInteger(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        return Integer.parseInt(
                value.toString()
        );
    }

    private LocalDate toLocalDate(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }

        if (value instanceof java.time.LocalDate localDate) {
            return localDate;
        }

        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }

        return LocalDate.parse(
                value.toString()
        );
    }
}