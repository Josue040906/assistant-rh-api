package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.RetraiteRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ce qui empêche, définitivement, une demande de produire son effet : la
 * situation de l'agent a changé depuis, il est parti à la retraite, la mutation
 * ne changerait rien… Une seule vérification, appelée à la création de la
 * demande, à l'acceptation, à l'application, et montrée sur les demandes en
 * cours pour qu'on puisse les annuler à temps.
 *
 * Seuls les empêchements définitifs comptent : une date d'effet pas encore
 * atteinte n'en est pas un (la demande attend simplement son jour).
 */
@Service
public class DemandeApplicabiliteService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SituationCarriereRepository situationRepository;
    private final AffectationRepository affectationRepository;
    private final RetraiteRepository retraiteRepository;
    private final DocumentRhRepository documentRhRepository;
    private final AffectationService affectationService;
    private final CongeService congeService;
    private final TitularisationService titularisationService;

    public DemandeApplicabiliteService(
            SituationCarriereRepository situationRepository,
            AffectationRepository affectationRepository,
            RetraiteRepository retraiteRepository,
            DocumentRhRepository documentRhRepository,
            AffectationService affectationService,
            CongeService congeService,
            TitularisationService titularisationService
    ) {
        this.situationRepository = situationRepository;
        this.affectationRepository = affectationRepository;
        this.retraiteRepository = retraiteRepository;
        this.documentRhRepository = documentRhRepository;
        this.affectationService = affectationService;
        this.congeService = congeService;
        this.titularisationService = titularisationService;
    }

    /** La même vérification pour une demande enregistrée (ligne de trouverPourCycle). */
    public Optional<String> empechement(Map<String, Object> document) {
        Object id = document.get("id");
        return empechement(
                String.valueOf(document.get("type_code")),
                entier(document.get("employe_id")),
                carte(document.get("donnees")),
                date(document.get("date_effet")),
                id instanceof Number numero ? numero.longValue() : null);
    }

    /**
     * typeCode, agent, données de la demande et date d'effet (demandée ou accordée) ;
     * documentId : la demande elle-même, à ne pas compter contre elle (congé), ou null.
     */
    public Optional<String> empechement(String typeCode, Integer employeId, Map<String, Object> donnees,
                                        LocalDate dateEffet, Long documentId) {
        if (employeId == null) {
            return Optional.of("La demande ne désigne aucun agent.");
        }
        Optional<LocalDate> retraite = retraiteRepository.dateRetraite(employeId);
        if (retraite.isPresent()) {
            return Optional.of("L'agent est parti à la retraite le " + retraite.get().format(JOUR) + ".");
        }
        return switch (typeCode) {
            case "AVANCEMENT" -> empechementSurLaSituation(employeId, donnees, dateEffet, "de l'échelon actuel");
            case "TITULARISATION" -> empechementTitularisation(employeId, donnees, dateEffet);
            case "MUTATION" -> empechementMutation(employeId, donnees, dateEffet);
            case "RETRAITE" -> empechementRetraite(employeId, dateEffet);
            case "CONGE" -> empechementConge(employeId, donnees, documentId);
            default -> Optional.empty();
        };
    }

    /**
     * Le poste, le service et le lieu qu'une demande de mutation vise, complétés
     * par l'affectation actuelle quand la demande ne change que le lieu.
     */
    public record CibleMutation(Integer posteId, Integer serviceId, String lieuTravail) { }

    public CibleMutation cibleMutation(Integer employeId, Map<String, Object> donnees) {
        Map<String, Object> cible = carte(donnees.get("situationDemandee"));
        Integer posteId = entier(cible.get("posteId"));
        Integer serviceId = entier(cible.get("serviceId"));
        String lieu = cible.get("lieuTravail") == null || cible.get("lieuTravail").toString().isBlank()
                ? null : cible.get("lieuTravail").toString();
        if (posteId != null) {
            return new CibleMutation(posteId, serviceId, lieu);
        }
        Map<String, Object> actuelle = affectationRepository.findActiveByEmployeId(employeId);
        if (actuelle == null) {
            throw new IllegalArgumentException("L'agent n'a pas d'affectation en cours à modifier.");
        }
        if (serviceId != null && !Objects.equals(serviceId, entier(actuelle.get("service_id")))) {
            throw new IllegalArgumentException(
                    "Précisez le poste dans le nouveau service : une affectation exige un poste.");
        }
        if (serviceId == null && lieu == null) {
            throw new IllegalArgumentException(
                    "Précisez le service et le poste : une direction seule ne suffit pas.");
        }
        return new CibleMutation(entier(actuelle.get("poste_id")), entier(actuelle.get("service_id")), lieu);
    }

    private Optional<String> empechementMutation(Integer employeId, Map<String, Object> donnees, LocalDate dateEffet) {
        CibleMutation cible;
        try {
            cible = cibleMutation(employeId, donnees);
        } catch (IllegalArgumentException incomplete) {
            return Optional.of(incomplete.getMessage());
        }
        if (cible.serviceId() == null) {
            return Optional.of("Le service visé par la demande est inconnu.");
        }
        return affectationService.empechementMutation(
                employeId, cible.posteId(), cible.serviceId(), cible.lieuTravail(), dateEffet);
    }

    /**
     * Avancement et titularisation partent de la situation enregistrée dans la
     * demande : un avancement, un reclassement ou une titularisation survenu
     * depuis la rend caduque.
     */
    private Optional<String> empechementSurLaSituation(Integer employeId, Map<String, Object> donnees,
                                                       LocalDate dateEffet, String debutDe) {
        Map<String, Object> situation = situationRepository.findSituationActuelle(employeId).orElse(null);
        if (situation == null) {
            return Optional.of("L'agent n'a pas de situation de carrière en cours.");
        }
        Integer echelonDemande = entier(carte(donnees.get("situationActuelle")).get("echelonId"));
        if (echelonDemande == null) {
            return Optional.of("La demande ne précise pas la situation de départ.");
        }
        if (!echelonDemande.equals(entier(situation.get("echelon_id")))) {
            Object code = situation.get("grade_code");
            Object libelle = situation.get("grade_libelle");
            return Optional.of("La situation de carrière de l'agent a changé depuis la demande"
                    + (code == null ? "" : " (grade actuel : " + code + (libelle == null ? "" : " — " + libelle) + ")")
                    + " : elle ne peut plus être appliquée.");
        }
        LocalDate debut = date(situation.get("date_debut"));
        if (dateEffet != null && debut != null && !dateEffet.isAfter(debut)) {
            return Optional.of("La date d'effet (" + dateEffet.format(JOUR) + ") doit être postérieure au début "
                    + debutDe + " (" + debut.format(JOUR) + ").");
        }
        return Optional.empty();
    }

    private Optional<String> empechementTitularisation(Integer employeId, Map<String, Object> donnees,
                                                       LocalDate dateEffet) {
        Optional<String> situation = empechementSurLaSituation(employeId, donnees, dateEffet,
                "de la situation actuelle");
        if (situation.isPresent()) {
            return situation;
        }
        Map<String, Object> analyse = titularisationService.analyser(employeId);
        return Boolean.TRUE.equals(analyse.get("possible"))
                ? Optional.empty()
                : Optional.of(String.valueOf(analyse.get("raison")));
    }

    private Optional<String> empechementRetraite(Integer employeId, LocalDate dateEffet) {
        Optional<LocalDate> possible = documentRhRepository.dateRetraitePossible(employeId);
        if (possible.isEmpty()) {
            return Optional.of("La date d'embauche de l'agent ou la durée de service exigée n'est pas renseignée.");
        }
        if (dateEffet != null && dateEffet.isBefore(possible.get())) {
            return Optional.of("La durée de service exigée sera atteinte le " + possible.get().format(JOUR)
                    + " : le départ ne peut pas la précéder.");
        }
        return Optional.empty();
    }

    private Optional<String> empechementConge(Integer employeId, Map<String, Object> donnees, Long documentId) {
        LocalDate debut = date(donnees.get("dateDebut"));
        LocalDate fin = date(donnees.get("dateFin"));
        if (debut == null || fin == null) {
            return Optional.of("Cette demande de congé ne précise pas ses dates.");
        }
        Optional<String> conflit = documentRhRepository.congeEnConflit(employeId, debut, fin, documentId);
        if (conflit.isPresent()) {
            return Optional.of("L'agent a déjà un congé " + conflit.get() + " sur ces dates.");
        }
        int jours = CongeService.joursCalendaires(debut, fin);
        Object soldeValeur = congeService.analyser(employeId, debut).get("solde");
        if (soldeValeur instanceof BigDecimal solde && solde.compareTo(BigDecimal.valueOf(jours)) < 0) {
            return Optional.of("Solde insuffisant : " + solde.stripTrailingZeros().toPlainString()
                    + " jours disponibles au " + debut.format(JOUR) + ", " + jours + " demandés.");
        }
        return Optional.empty();
    }

    private static Integer entier(Object valeur) {
        if (valeur instanceof Number nombre) {
            return nombre.intValue();
        }
        return valeur == null || valeur.toString().isBlank() ? null : Integer.valueOf(valeur.toString());
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate jour) {
            return jour;
        }
        if (valeur instanceof java.sql.Date jour) {
            return jour.toLocalDate();
        }
        return valeur == null || valeur.toString().isBlank() ? null : LocalDate.parse(valeur.toString().substring(0, 10));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> carte(Object valeur) {
        return valeur instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
