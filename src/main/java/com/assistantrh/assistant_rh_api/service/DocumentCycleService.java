package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cycle de vie d'une demande. Créée (CREE), elle produit la lettre qui est
 * signée et tamponnée hors de l'application ; la réponse reçue sur papier
 * s'enregistre ensuite : ACCEPTE ou REFUSE, avec son signataire et sa date. Une demande acceptée devient applicable à la date d'effet
 * que l'accord fixe (ce n'est pas un statut enregistré), puis APPLIQUE quand un
 * agent du Service du Personnel l'applique — son effet est produit dans la même
 * opération, par la même action que les boutons directs. Une demande créée ou
 * acceptée peut être ANNULE (une demande acceptée, avec son motif) ; appliquée,
 * refusée ou annulée, elle se range en ARCHIVE. Ce qui empêche une demande de
 * produire son effet est vérifié par DemandeApplicabiliteService, à chaque étape.
 */
@Service
public class DocumentCycleService {

    /** Les brouillons d'e-mail à l'agent (facultatif : absent dans les tests unitaires). */
    private MessageAgentService messages;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMessages(MessageAgentService messages) {
        this.messages = messages;
    }
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentRhRepository documentRhRepository;
    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;
    private final DemandeApplicabiliteService applicabilite;
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
            DemandeApplicabiliteService applicabilite,
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
        this.applicabilite = applicabilite;
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
                                LocalDate dateDecision, String motif, LocalDate dateEffet) {
        Map<String, Object> document = documentPourCycle(id);
        verifierPersonnel(acteurId);
        String signe = texte(signataire, 255);
        String raison = texte(motif, 2000);
        if (signe == null) {
            throw new IllegalArgumentException("Indiquez qui a signé la réponse.");
        }
        if (dateDecision == null || dateDecision.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date de la réponse est obligatoire et ne peut pas être future.");
        }
        if (!"CREE".equals(document.get("statut"))) {
            throw new IllegalArgumentException("La réponse d'une demande ne se saisit qu'une fois, tant qu'elle est créée.");
        }
        LocalDate dateDemande = date(document.get("date_document"));
        if (dateDemande != null && dateDecision.isBefore(dateDemande)) {
            throw new IllegalArgumentException("La réponse ne peut pas précéder la demande (du "
                    + dateDemande.format(JOUR) + ").");
        }
        if (acceptee && dateEffet != null && "CONGE".equals(document.get("type_code"))) {
            throw new IllegalArgumentException("Les dates d'un congé sont celles de la demande.");
        }
        if (acceptee) {
            // Entre la demande et la réponse, la situation de l'agent a pu changer.
            LocalDate effetAccorde = dateEffet != null ? dateEffet : date(document.get("date_effet"));
            empechement(document, effetAccorde).ifPresent(obstacle -> {
                throw new IllegalArgumentException("Cette demande ne peut pas être acceptée : " + obstacle
                        + " Saisissez un refus ou annulez-la.");
            });
        }
        if (!documentRhRepository.enregistrerDecision(id, acceptee ? "ACCEPTE" : "REFUSE", signe,
                dateDecision, raison, acceptee ? dateEffet : null, acteurId)) {
            throw new IllegalArgumentException("La réponse d'une demande ne se saisit qu'une fois, tant qu'elle est créée.");
        }
        notificationRepository.notifierDecision(id, acteurId, acceptee);
        if (messages != null) {
            messages.decisionSurDemande(id, entier(document.get("employe_id")),
                    String.valueOf(document.get("objet")), String.valueOf(document.get("reference_document")),
                    acceptee, acceptee ? (dateEffet != null ? dateEffet : date(document.get("date_effet"))) : null,
                    raison);
        }
        journaliser(document, acteurId, acceptee ? "ACCEPTATION_DOCUMENT" : "REFUS_DOCUMENT",
                (acceptee ? "Acceptation de la demande " : "Refus de la demande ")
                        + "(signée par " + signe + ") : ");
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
        empechement(document, effet).ifPresent(raison -> {
            throw new IllegalArgumentException(raison + " Annulez cette demande : elle ne pourra pas être appliquée.");
        });
        changerStatut(document, "APPLIQUE", List.of("ACCEPTE"), "Seule une demande acceptée peut être appliquée.");
        produireEffet(document, acteurId, effet);
        notificationRepository.notifierDocumentApplique(id, acteurId);
        journaliser(document, acteurId, "APPLICATION_DOCUMENT", "Application de la demande ");
    }

    /**
     * L'auteur (ou un chef) renonce à une demande créée ou acceptée : elle ne
     * prendra jamais effet. Annuler une demande acceptée exige un motif (l'accord
     * signé existe), gardé dans ses données et dans le journal.
     */
    @Transactional
    public void annulerDocument(Long id, Integer acteurId, String motif) {
        Map<String, Object> document = documentPourCycle(id);
        verifierAuteur(document, acteurId);
        String raison = texte(motif, 2000);
        String statut = String.valueOf(document.get("statut"));
        if (!"CREE".equals(statut) && !"ACCEPTE".equals(statut)) {
            throw new IllegalArgumentException("Seule une demande créée ou acceptée peut être annulée.");
        }
        if ("ACCEPTE".equals(statut) && raison == null) {
            throw new IllegalArgumentException(
                    "Indiquez pourquoi vous annulez cette demande : elle a déjà été acceptée.");
        }
        if (!documentRhRepository.annuler(((Number) document.get("id")).longValue(), raison, acteurId,
                List.of("CREE", "ACCEPTE"))) {
            throw new IllegalArgumentException("Seule une demande créée ou acceptée peut être annulée.");
        }
        journaliser(document, acteurId, "ANNULATION_DOCUMENT", "Annulation de la demande ",
                raison == null ? "" : " : " + raison);
    }

    /** Ce qui empêche définitivement cette demande de produire son effet à cette date. */
    private Optional<String> empechement(Map<String, Object> document, LocalDate effet) {
        Map<String, Object> aVerifier = new java.util.HashMap<>(document);
        aVerifier.put("date_effet", effet);
        return applicabilite.empechement(aVerifier);
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
        Object signataire = document.get("decision_signataire");
        String reference = (signataire == null ? "" : "Décision signée par " + signataire + " — ")
                + "demande " + document.get("reference_document");
        Map<String, Object> donnees = carte(document.get("donnees"));
        switch (String.valueOf(document.get("type_code"))) {
            case "AVANCEMENT" -> avancementService.confirmer(employeId, acteurId, effet, reference);
            case "TITULARISATION" -> titularisationService.titulariser(employeId, acteurId, effet, reference);
            case "MUTATION" -> {
                DemandeApplicabiliteService.CibleMutation cible = applicabilite.cibleMutation(employeId, donnees);
                affectationService.modifierAffectation(employeId, acteurId, cible.posteId(), cible.serviceId(),
                        cible.lieuTravail(), effet, reference, reference);
            }
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
        journaliser(document, acteurId, type, libelle, "");
    }

    private void journaliser(Map<String, Object> document, Integer acteurId, String type, String libelle,
                             String suite) {
        Object reference = document.get("reference_document");
        activiteService.enregistrer(
                acteurId,
                entier(document.get("employe_id")),
                type,
                libelle + (reference == null ? document.get("id") : reference) + suite
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
