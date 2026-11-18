package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AffectationService {

    /** Les brouillons d'e-mail à l'agent (facultatif : absent dans les tests unitaires). */
    private MessageAgentService messages;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMessages(MessageAgentService messages) {
        this.messages = messages;
    }

    private final AffectationRepository affectationRepository;
    private final EmployeRepository employeRepository;
    private final ActiviteService activiteService;
    private final PosteService posteService;
    private final ServiceService serviceService;
    private final UtilisateurService utilisateurService;
    private final VilleService villeService;

    public AffectationService(
            AffectationRepository affectationRepository,
            EmployeRepository employeRepository,
            ActiviteService activiteService,
            PosteService posteService,
            ServiceService serviceService,
            UtilisateurService utilisateurService,
            VilleService villeService
    ) {
        this.affectationRepository = affectationRepository;
        this.employeRepository = employeRepository;
        this.activiteService = activiteService;
        this.posteService = posteService;
        this.serviceService = serviceService;
        this.utilisateurService = utilisateurService;
        this.villeService = villeService;
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

        if (!employeRepository.verrouillerPourAffectation(employeId)) {
            throw new IllegalArgumentException(
                    "L'agent demandé n'existe pas."
            );
        }

        Map<String, Object> employe =
                employeRepository.findById(employeId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "L'agent demandé n'existe pas."
                                )
                        );

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

        // Les mêmes règles qu'une demande de mutation vérifie dès sa création.
        empechementMutation(employeId, posteId, serviceId, lieuTravail, dateEffet)
                .ifPresent(raison -> {
                    throw new IllegalArgumentException(raison);
                });

        Map<String, Object> affectationActuelle =
                affectationRepository.findActiveByEmployeId(
                        employeId
                );

        Integer ancienneAffectationId =
                toInteger(
                        affectationActuelle.get("id")
                );

        String ancienLieuTravail =
                employe.get("lieu_travail") == null
                        ? null
                        : employe.get("lieu_travail").toString();

        String nouveauLieuTravail = lieuTravail == null || lieuTravail.isBlank()
                ? ancienLieuTravail
                : villeService.validerVille(lieuTravail, "Le lieu de travail");

        Date sqlDateEffet =
                Date.valueOf(dateEffet);

        /*
         * 1. Clôture de l'ancienne affectation.
         */
        int fermeture =
                affectationRepository.fermerAffectation(
                        ancienneAffectationId,
                        Date.valueOf(dateEffet.minusDays(1))
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
        if (messages != null) {
            messages.mutationEnregistree(employeId, dateEffet);
        }
    }

    /**
     * Ce qui empêche, aujourd'hui, de muter l'agent vers ce poste et ce service
     * (et ce lieu, s'il est donné) à cette date : rien si la mutation est possible.
     * Une seule source pour la mutation directe et pour une demande de mutation,
     * de sa création à son application.
     */
    public Optional<String> empechementMutation(
            Integer employeId,
            Integer posteId,
            Integer serviceId,
            String lieuTravail,
            LocalDate dateEffet
    ) {
        Map<String, Object> actuelle = affectationRepository.findActiveByEmployeId(employeId);
        if (actuelle == null) {
            return Optional.of("L'agent n'a pas d'affectation en cours à modifier.");
        }
        Map<String, Object> poste = posteService.getPosteById(Long.valueOf(posteId));
        if (poste == null) {
            return Optional.of("Le poste demandé n'existe pas.");
        }
        Object posteServiceId = poste.get("service_id");
        if (posteServiceId == null || Integer.parseInt(posteServiceId.toString()) != serviceId) {
            return Optional.of("Le poste sélectionné n'appartient pas au service sélectionné.");
        }
        LocalDate debutActuel = toLocalDate(actuelle.get("date_debut"));
        if (dateEffet != null && debutActuel != null && !dateEffet.isAfter(debutActuel)) {
            return Optional.of("La date d'effet doit être postérieure au début de l'affectation actuelle (le "
                    + debutActuel.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ").");
        }
        String lieuActuel = employeRepository.findById(employeId)
                .map(employe -> employe.get("lieu_travail"))
                .map(Object::toString)
                .orElse(null);
        String lieuVise;
        try {
            lieuVise = lieuTravail == null || lieuTravail.isBlank()
                    ? lieuActuel
                    : villeService.validerVille(lieuTravail, "Le lieu de travail");
        } catch (IllegalArgumentException villeInconnue) {
            return Optional.of(villeInconnue.getMessage());
        }
        if (posteId.equals(toInteger(actuelle.get("poste_id")))
                && serviceId.equals(toInteger(actuelle.get("service_id")))
                && equalsNullable(lieuActuel, lieuVise)) {
            return Optional.of("L'agent occupe déjà ce poste dans ce service"
                    + (lieuActuel == null ? "" : ", à " + lieuActuel) + " : rien ne changerait.");
        }
        try {
            // Un poste complet n'accueille personne de plus (l'agent lui-même n'est pas compté).
            affectationRepository.verifierPlaceLibre(posteId, employeId);
        } catch (IllegalArgumentException complet) {
            return Optional.of(complet.getMessage());
        }
        return Optional.empty();
    }

    public Map<String, Object> getAffectationActuelle(
            Integer employeId,
            Integer acteurId
    ) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }
        verifierActeur(acteurId);

        return affectationRepository.findActiveByEmployeId(
                employeId
        );
    }

    public List<Map<String, Object>> getHistorique(
            Integer employeId,
            Integer acteurId
    ) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }
        verifierActeur(acteurId);

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

        verifierActeur(acteurId);

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

    private void verifierActeur(Integer acteurId) {
        if (acteurId == null || acteurId <= 0
                || !utilisateurService.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas autorisé à effectuer cette opération dans SYGPERS."
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
        if (dateEffet.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La date d'effet ne peut pas être future."
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