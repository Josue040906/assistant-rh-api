package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cycle de vie d'une demande. Créée (CREE), elle produit la lettre qui est
 * signée et tamponnée hors de l'application ; la réponse reçue sur papier
 * s'enregistre ensuite : ACCEPTE ou REFUSE, avec le signataire, la référence de
 * l'acte et sa date. Une demande acceptée devient applicable à la date d'effet
 * que l'accord fixe (ce n'est pas un statut enregistré), puis APPLIQUE quand un
 * agent du Service du Personnel l'applique — son effet est produit dans la même
 * opération, par la même action que les boutons directs. Une demande créée peut
 * être ANNULE ; appliquée, refusée ou annulée, elle se range en ARCHIVE.
 */
@Service
public class DocumentCycleService {
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentRhRepository documentRhRepository;
    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;
    private final SituationCarriereRepository situationRepository;
    private final AffectationRepository affectationRepository;
    private final AvancementService avancementService;
    private final AffectationService affectationService;
    private final RetraiteService retraiteService;
    private final CongeService congeService;
    private final TitularisationService titularisationService;

    public DocumentCycleService(
            DocumentRhRepository documentRhRepository,
            NotificationRepository notificationRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService,
            SituationCarriereRepository situationRepository,
            AffectationRepository affectationRepository,
            AvancementService avancementService,
            AffectationService affectationService,
            RetraiteService retraiteService,
            CongeService congeService,
            TitularisationService titularisationService
    ) {
        this.documentRhRepository = documentRhRepository;
        this.notificationRepository = notificationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
        this.situationRepository = situationRepository;
        this.affectationRepository = affectationRepository;
        this.avancementService = avancementService;
        this.affectationService = affectationService;
        this.retraiteService = retraiteService;
        this.congeService = congeService;
        this.titularisationService = titularisationService;
    }

    /**
     * La réponse reçue sur papier : la demande est acceptée ou refusée. Tout agent
     * actif du Service du Personnel la saisit ; l'application garde qui et quand.
     * Une acceptation peut fixer la date d'effet accordée (sinon, celle demandée).
     */
    @Transactional
    public void deciderDocument(Long id, Integer acteurId, boolean acceptee, String signataire,
                                String reference, LocalDate dateDecision, String motif, LocalDate dateEffet) {
        Map<String, Object> document = documentPourCycle(id);
        verifierPersonnel(acteurId);
        String signe = texte(signataire, 255);
        String acte = texte(reference, 150);
        String raison = texte(motif, 2000);
        if (signe == null) {
            throw new IllegalArgumentException("Indiquez qui a signé la réponse.");
        }
        if (acceptee && acte == null) {
            throw new IllegalArgumentException("Indiquez la référence de l'acte d'accord (arrêté, décision…).");
        }
        if (dateDecision == null || dateDecision.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date de la réponse est obligatoire et ne peut pas être future.");
        }
        if (!"CREE".equals(document.get("statut"))) {
            throw new IllegalArgumentException("La réponse d'une demande ne se saisit qu'une fois, tant qu'elle est créée.");
        }
        if (acceptee && dateEffet != null && "CONGE".equals(document.get("type_code"))) {
            throw new IllegalArgumentException("Les dates d'un congé sont celles de la demande.");
        }
        if (!documentRhRepository.enregistrerDecision(id, acceptee ? "ACCEPTE" : "REFUSE", signe, acte,
                dateDecision, raison, acceptee ? dateEffet : null, acteurId)) {
            throw new IllegalArgumentException("La réponse d'une demande ne se saisit qu'une fois, tant qu'elle est créée.");
        }
        notificationRepository.notifierDecision(id, acteurId, acceptee);
        journaliser(document, acteurId, acceptee ? "ACCEPTATION_DOCUMENT" : "REFUS_DOCUMENT",
                (acceptee ? "Acceptation de la demande " : "Refus de la demande ")
                        + "(signée par " + signe + (acte == null ? "" : ", " + acte) + ") : ");
    }

    /**
     * Un agent du Service du Personnel applique une demande acceptée, une fois
     * sa date d'effet atteinte : l'effet est produit (avancement, mutation,
     * retraite…), le chef et l'auteur sont notifiés et l'activité journalisée.
     * Si l'effet est impossible, rien ne change et la raison est donnée.
     */
    @Transactional
    public void appliquerDocument(Long id, Integer acteurId) {
        Map<String, Object> document = documentPourCycle(id);
        verifierPersonnel(acteurId);
        if (!"ACCEPTE".equals(document.get("statut"))) {
            throw new IllegalArgumentException("Seule une demande acceptée peut être appliquée.");
        }
        LocalDate dateEffet = date(document.get("date_effet"));
        if (dateEffet != null && dateEffet.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Cette demande sera applicable à partir du " + dateEffet.format(JOUR) + ".");
        }
        LocalDate effet = dateEffet == null ? LocalDate.now() : dateEffet;
        changerStatut(document, "APPLIQUE", List.of("ACCEPTE"), "Seule une demande acceptée peut être appliquée.");
        produireEffet(document, acteurId, effet);
        notificationRepository.notifierDocumentApplique(id, acteurId);
        journaliser(document, acteurId, "APPLICATION_DOCUMENT", "Application de la demande ");
    }

    /** L'auteur (ou un chef) renonce à une demande créée : elle ne prendra jamais effet. */
    @Transactional
    public void annulerDocument(Long id, Integer acteurId) {
        Map<String, Object> document = documentPourCycle(id);
        verifierAuteur(document, acteurId);
        changerStatut(document, "ANNULE", List.of("CREE"),
                "Seule une demande créée peut être annulée.");
        journaliser(document, acteurId, "ANNULATION_DOCUMENT", "Annulation du document ");
    }

    /** Une demande appliquée, refusée ou annulée se range aux archives. */
    @Transactional
    public void archiverDocument(Long id, Integer acteurId) {
        Map<String, Object> document = documentPourCycle(id);
        changerStatut(document, "ARCHIVE", List.of("APPLIQUE", "REFUSE", "ANNULE"),
                "Seule une demande appliquée, refusée ou annulée peut être archivée.");
        journaliser(document, acteurId, "ARCHIVAGE_DOCUMENT", "Archivage du document ");
    }

    private void produireEffet(Map<String, Object> document, Integer acteurId, LocalDate effet) {
        Integer employeId = entier(document.get("employe_id"));
        Object acte = document.get("decision_reference");
        String reference = (acte == null ? "" : acte + " — ") + "demande " + document.get("reference_document");
        Map<String, Object> donnees = carte(document.get("donnees"));
        switch (String.valueOf(document.get("type_code"))) {
            case "AVANCEMENT" -> {
                Integer echelonDemande = entier(carte(donnees.get("situationActuelle")).get("echelonId"));
                if (echelonDemande == null) {
                    throw new IllegalArgumentException(
                            "Cette demande ne précise pas la situation d'avancement : elle ne peut pas être appliquée.");
                }
                Integer echelonActuel = situationRepository.findSituationActuelle(employeId)
                        .map(situation -> entier(situation.get("echelon_id"))).orElse(null);
                if (!echelonDemande.equals(echelonActuel)) {
                    throw new IllegalArgumentException(
                            "La situation de carrière de l'agent a changé depuis la demande : elle ne peut plus être appliquée.");
                }
                avancementService.confirmer(employeId, acteurId, effet, reference);
            }
            case "TITULARISATION" -> {
                // Le grade visé découle de la situation : si elle a changé, la demande ne vaut plus.
                Integer echelonAvant = entier(carte(donnees.get("situationActuelle")).get("echelonId"));
                Integer echelonActuel = situationRepository.findSituationActuelle(employeId)
                        .map(situation -> entier(situation.get("echelon_id"))).orElse(null);
                if (!Objects.equals(echelonAvant, echelonActuel)) {
                    throw new IllegalArgumentException(
                            "La situation de carrière de l'agent a changé depuis la demande : elle ne peut plus être appliquée.");
                }
                titularisationService.titulariser(employeId, acteurId, effet, reference);
            }
            case "MUTATION" -> appliquerMutation(employeId, acteurId, effet, reference,
                    carte(donnees.get("situationDemandee")));
            case "RETRAITE" -> retraiteService.prendreRetraite(employeId, acteurId, effet, reference);
            case "CONGE" -> {
                LocalDate debut = date(donnees.get("dateDebut"));
                LocalDate fin = date(donnees.get("dateFin"));
                if (debut == null || fin == null) {
                    throw new IllegalArgumentException("Cette demande de congé ne précise pas ses dates.");
                }
                congeService.prendreConge(employeId, acteurId, debut, fin,
                        ((Number) document.get("id")).longValue(), reference);
            }
            default -> {
                // Les autres types n'ont pas d'effet automatique : ils sont seulement marqués appliqués.
            }
        }
    }

    /**
     * Une affectation exige un poste et son service : un nouveau service sans
     * poste ne peut pas être appliqué. Un simple changement de lieu garde
     * le poste et le service actuels.
     */
    private void appliquerMutation(Integer employeId, Integer acteurId, LocalDate effet,
                                   String reference, Map<String, Object> cible) {
        Map<String, Object> actuelle = affectationRepository.findActiveByEmployeId(employeId);
        if (actuelle == null) {
            throw new IllegalArgumentException("L'agent n'a pas d'affectation en cours à modifier.");
        }
        Integer posteId = entier(cible.get("posteId"));
        Integer serviceId = entier(cible.get("serviceId"));
        String lieu = cible.get("lieuTravail") == null ? null : cible.get("lieuTravail").toString();
        if (posteId == null) {
            if (serviceId != null && !Objects.equals(serviceId, entier(actuelle.get("service_id")))) {
                throw new IllegalArgumentException(
                        "Précisez le poste dans le nouveau service : la demande ne l'indique pas.");
            }
            if (serviceId == null && lieu == null) {
                throw new IllegalArgumentException(
                        "Précisez le service et le poste : la demande n'indique qu'une direction.");
            }
            posteId = entier(actuelle.get("poste_id"));
            serviceId = entier(actuelle.get("service_id"));
        }
        affectationService.modifierAffectation(employeId, acteurId, posteId, serviceId, lieu,
                effet, reference, reference);
    }

    private Map<String, Object> documentPourCycle(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("L'identifiant du document est invalide.");
        }
        return documentRhRepository.trouverPourCycle(id)
                .orElseThrow(() -> new IllegalArgumentException("Le document demandé n'existe pas."));
    }

    /** L'auteur renonce à sa demande ; un chef peut aussi annuler celle d'un autre. */
    private void verifierAuteur(Map<String, Object> document, Integer acteurId) {
        if (acteurId == null || acteurId <= 0) {
            throw new IllegalArgumentException("L'utilisateur connecté est inconnu.");
        }
        Object auteur = document.get("auteur_id");
        boolean estAuteur = auteur instanceof Number number && number.intValue() == acteurId;
        if (!estAuteur && !utilisateurRepository.estChefActif(acteurId)) {
            throw new IllegalArgumentException("Seul l'auteur de la demande ou un chef peut l'annuler.");
        }
    }

    /** Saisir une réponse ou appliquer une demande acceptée : tout agent actif du Service du Personnel. */
    private void verifierPersonnel(Integer acteurId) {
        if (acteurId == null || acteurId <= 0 || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException("Seul un agent actif du Service du Personnel peut le faire.");
        }
    }

    private static String texte(String valeur, int longueurMax) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String propre = valeur.trim();
        if (propre.length() > longueurMax) {
            throw new IllegalArgumentException("Un texte dépasse " + longueurMax + " caractères.");
        }
        return propre;
    }

    private void changerStatut(Map<String, Object> document, String vers, List<String> depuis, String refus) {
        Long id = ((Number) document.get("id")).longValue();
        if (!documentRhRepository.changerStatut(id, vers, depuis)) {
            throw new IllegalArgumentException(refus);
        }
    }

    private void journaliser(Map<String, Object> document, Integer acteurId, String type, String libelle) {
        Object reference = document.get("reference_document");
        activiteService.enregistrer(
                acteurId,
                entier(document.get("employe_id")),
                type,
                libelle + (reference == null ? document.get("id") : reference)
        );
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
        return valeur == null ? null : LocalDate.parse(valeur.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> carte(Object valeur) {
        return valeur instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
