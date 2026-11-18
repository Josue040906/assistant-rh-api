package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.MessageAgentRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Les e-mails aux agents. Un événement de carrière prépare un brouillon, déjà
 * rédigé ; rien ne part tant qu'un agent du Service du Personnel ne l'a pas
 * relu et envoyé (il peut le modifier, ou l'écarter).
 *
 * Le brouillon est proposé une fois l'action validée : une action annulée ne
 * laisse aucun brouillon, et un brouillon qui échouerait ne peut pas annuler
 * l'action.
 */
@Service
public class MessageAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(MessageAgentService.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SIGNATURE = "\n\nPour toute question, le Service du Personnel reste à votre disposition."
            + "\n\nCordialement,\nLe Service du Personnel";

    private final MessageAgentRepository repository;
    private final EmployeRepository employeRepository;
    private final DocumentRhRepository documentRhRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EnvoiEmail envoiEmail;

    public MessageAgentService(MessageAgentRepository repository, EmployeRepository employeRepository,
                               DocumentRhRepository documentRhRepository,
                               UtilisateurRepository utilisateurRepository, EnvoiEmail envoiEmail) {
        this.repository = repository;
        this.employeRepository = employeRepository;
        this.documentRhRepository = documentRhRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.envoiEmail = envoiEmail;
    }

    // ---------------------------------------------------------------- événements

    public void mutationEnregistree(Integer employeId, LocalDate effet) {
        apresValidation(() -> agent(employeId).ifPresent(agent -> proposer(employeId, "MUTATION",
                "Votre nouvelle affectation",
                salutation(agent) + "Votre affectation change à compter du " + jour(effet) + " : vous occuperez le poste de "
                        + texte(agent.get("poste")) + " au " + texte(agent.get("service"))
                        + lieu(agent) + "." + SIGNATURE,
                "mutation:" + employeId + ":" + effet)));
    }

    public void avancementEnregistre(Integer employeId, LocalDate effet) {
        apresValidation(() -> agent(employeId).ifPresent(agent -> proposer(employeId, "AVANCEMENT",
                "Votre avancement d'échelon",
                salutation(agent) + "Nous avons le plaisir de vous informer de votre avancement : à compter du "
                        + jour(effet) + ", vous êtes au grade " + grade(agent) + indice(agent) + "." + SIGNATURE,
                "avancement:" + employeId + ":" + effet)));
    }

    public void reclassementEnregistre(Integer employeId, LocalDate effet) {
        apresValidation(() -> agent(employeId).ifPresent(agent -> proposer(employeId, "RECLASSEMENT",
                "Votre reclassement",
                salutation(agent) + "Suite à la reconnaissance de votre diplôme, vous êtes reclassé"
                        + accord(agent) + " à compter du " + jour(effet) + " : catégorie " + texte(agent.get("categorie"))
                        + ", grade " + grade(agent) + indice(agent) + "." + SIGNATURE,
                "reclassement:" + employeId + ":" + effet)));
    }

    public void titularisationEnregistree(Integer employeId, LocalDate effet) {
        apresValidation(() -> agent(employeId).ifPresent(agent -> proposer(employeId, "TITULARISATION",
                "Votre titularisation",
                salutation(agent) + "Nous avons le plaisir de vous informer de votre titularisation : à compter du "
                        + jour(effet) + ", vous êtes fonctionnaire, au grade " + grade(agent) + indice(agent) + "."
                        + SIGNATURE,
                "titularisation:" + employeId + ":" + effet)));
    }

    public void retraiteEnregistree(Integer employeId, LocalDate effet) {
        apresValidation(() -> agent(employeId).ifPresent(agent -> proposer(employeId, "RETRAITE",
                "Votre départ à la retraite",
                salutation(agent) + "Votre départ à la retraite est enregistré au " + jour(effet)
                        + ". Le Service du Personnel vous remercie pour les années consacrées au ministère."
                        + SIGNATURE,
                "retraite:" + employeId + ":" + effet)));
    }

    /** La réponse reçue sur papier à une demande de l'agent : acceptée (avec sa date d'effet) ou refusée. */
    public void decisionSurDemande(long documentId, Integer employeId, String objet, String reference,
                                   boolean acceptee, LocalDate effet, String motif) {
        apresValidation(() -> agent(employeId).ifPresent(agent -> proposer(employeId,
                acceptee ? "DEMANDE_ACCEPTEE" : "DEMANDE_REFUSEE",
                (acceptee ? "Votre demande est acceptée : " : "Votre demande n'a pas été retenue : ")
                        + objet.toLowerCase(java.util.Locale.FRENCH),
                salutation(agent) + "Votre " + objet.toLowerCase(java.util.Locale.FRENCH) + " (" + reference + ") "
                        + (acceptee
                        ? "a été acceptée" + (effet == null ? "" : " ; elle prendra effet le " + jour(effet)) + "."
                        : "n'a pas été retenue" + (motif == null || motif.isBlank() ? "." : " : " + motif.strip()
                        + (motif.strip().endsWith(".") ? "" : ".")))
                        + SIGNATURE,
                (acceptee ? "demande-acceptee:" : "demande-refusee:") + documentId)));
    }

    /** Chaque jour : ancienneté d'échelon atteinte ou dans le mois, retraite possible dans les six mois. */
    public void proposerEcheances() {
        LocalDate aujourdhui = LocalDate.now();
        for (Map<String, Object> ligne : repository.anciennetesProches()) {
            Integer employeId = ((Number) ligne.get("employe_id")).intValue();
            LocalDate date = date(ligne.get("date_eligibilite"));
            boolean atteinte = !date.isAfter(aujourdhui);
            agent(employeId).ifPresent(agent -> proposer(employeId,
                    atteinte ? "AVANCEMENT_POSSIBLE" : "AVANCEMENT_BIENTOT",
                    atteinte ? "Vous remplissez la condition d'ancienneté pour un avancement"
                            : "Votre avancement d'échelon approche",
                    salutation(agent) + (atteinte
                            ? "Depuis le " + jour(date) + ", vous remplissez la condition d'ancienneté dans votre grade ("
                            : "Le " + jour(date) + ", vous remplirez la condition d'ancienneté dans votre grade (")
                            + texte(ligne.get("grade_libelle")) + ") pour prétendre à un avancement d'échelon."
                            + " Le Service du Personnel prépare votre dossier." + SIGNATURE,
                    "anciennete:" + ligne.get("situation_id") + ":" + (atteinte ? "atteinte" : "un-mois")));
        }
        documentRhRepository.ancienneteRetraiteAnnees().ifPresent(annees -> {
            for (Map<String, Object> ligne : repository.retraitesProches(annees)) {
                Integer employeId = ((Number) ligne.get("employe_id")).intValue();
                LocalDate date = date(ligne.get("date_possible"));
                agent(employeId).ifPresent(agent -> proposer(employeId, "RETRAITE_PROCHE",
                        "Votre départ à la retraite sera possible le " + jour(date),
                        salutation(agent) + "Le " + jour(date) + ", vous aurez " + annees
                                + " ans de service et pourrez faire valoir vos droits à la retraite."
                                + " Si vous le souhaitez, le Service du Personnel vous accompagne pour préparer votre demande."
                                + SIGNATURE,
                        "retraite-proche:" + employeId + ":" + date));
            }
        });
    }

    /**
     * L'e-mail d'une notification : celui déjà lié à la situation s'il existe, sinon un
     * brouillon rédigé à partir d'elle (bienvenue pour un nouvel agent, sinon un message
     * à compléter). Rien ne part : l'e-mail s'ouvre pour être relu.
     */
    public Map<String, Object> brouillonPourNotification(Map<String, Object> notification, Integer acteurId) {
        verifierPersonnel(acteurId);
        if (notification.get("message_id") instanceof Number lie) {
            return repository.trouver(lie.longValue())
                    .orElseThrow(() -> new IllegalArgumentException("Cet e-mail n'existe plus."));
        }
        Integer employeId = ((Number) notification.get("employe_id")).intValue();
        Map<String, Object> agent = agent(employeId)
                .orElseThrow(() -> new IllegalArgumentException("L'agent de cette notification n'existe plus."));
        String cle = "notification:" + notification.get("id");
        String type = String.valueOf(notification.get("type"));
        String cleNotification = texte(notification.get("cle_deduplication"));
        String message = texte(notification.get("message"));
        java.util.regex.Matcher date = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(message);
        String jourVu = date.find() ? date.group(1) : null;
        if (jourVu != null && ("ANCIENNETE_ATTEINTE".equals(type) || "ANCIENNETE_UN_MOIS".equals(type))) {
            // Le même e-mail que la tâche quotidienne, sous la même clé : jamais deux pour une situation.
            boolean atteinte = "ANCIENNETE_ATTEINTE".equals(type);
            cle = cleNotification.replace(":anciennete_atteinte", ":atteinte").replace(":anciennete_un_mois", ":un-mois");
            repository.proposer(employeId, atteinte ? "AVANCEMENT_POSSIBLE" : "AVANCEMENT_BIENTOT",
                    atteinte ? "Vous remplissez la condition d'ancienneté pour un avancement"
                            : "Votre avancement d'échelon approche",
                    salutation(agent) + (atteinte ? "Depuis le " + jourVu + ", vous remplissez"
                            : "Le " + jourVu + ", vous remplirez")
                            + " la condition d'ancienneté dans votre grade pour prétendre à un avancement d'échelon."
                            + " Le Service du Personnel prépare votre dossier." + SIGNATURE, cle);
        } else if (jourVu != null && "RETRAITE_PROCHE".equals(type)) {
            cle = cleNotification;
            java.util.regex.Matcher annees = java.util.regex.Pattern.compile("après (\\d+) ans").matcher(message);
            repository.proposer(employeId, "RETRAITE_PROCHE", "Votre départ à la retraite sera possible le " + jourVu,
                    salutation(agent) + "Le " + jourVu + ", vous "
                            + (annees.find() ? "aurez " + annees.group(1) + " ans de service et pourrez" : "pourrez")
                            + " faire valoir vos droits à la retraite."
                            + " Si vous le souhaitez, le Service du Personnel vous accompagne pour préparer votre demande."
                            + SIGNATURE, cle);
        } else if ("NOUVEL_AGENT".equals(type)) {
            repository.proposer(employeId, "BIENVENUE", "Bienvenue au ministère",
                    salutation(agent) + "Nous avons le plaisir de vous accueillir au sein du ministère, au poste de "
                            + texte(agent.get("poste")) + " (" + texte(agent.get("service")) + ")."
                            + " Votre dossier administratif est désormais enregistré auprès du Service du Personnel."
                            + SIGNATURE, cle);
        } else {
            repository.proposer(employeId, "INFORMATION", texte(notification.get("titre")),
                    salutation(agent) + "Nous revenons vers vous au sujet de votre dossier : "
                            + texte(notification.get("titre")).toLowerCase(java.util.Locale.FRENCH) + "."
                            + "\n\n[Complétez ce message avant de l'envoyer.]" + SIGNATURE, cle);
        }
        return repository.trouverParCle(cle)
                .orElseThrow(() -> new IllegalStateException("L'e-mail n'a pas pu être préparé."));
    }

    // ---------------------------------------------------------------- décisions

    public List<Map<String, Object>> lister(String statut, Integer employeId, Integer recentsMinutes, Integer acteurId) {
        verifierPersonnel(acteurId);
        return repository.lister(statut == null || statut.isBlank() ? null : statut.trim().toUpperCase(),
                employeId, recentsMinutes, 200);
    }

    public int compterAEnvoyer() {
        return repository.compterProposes();
    }

    public Map<String, Object> mode() {
        return Map.of("mode", envoiEmail.reel() ? "ENVOI" : "JOURNAL", "expediteur", envoiEmail.expediteur());
    }

    /**
     * Envoie le message, tel que relu (sujet et corps éventuellement modifiés), à
     * l'adresse actuelle de l'agent. L'issue (envoyé ou échec et sa raison) est gardée.
     */
    public Map<String, Object> envoyer(long id, Integer acteurId, String sujet, String corps) {
        verifierPersonnel(acteurId);
        Map<String, Object> message = repository.trouver(id)
                .orElseThrow(() -> new IllegalArgumentException("Le message demandé n'existe pas."));
        if (!"PROPOSE".equals(message.get("statut")) && !"ECHEC".equals(message.get("statut"))) {
            throw new IllegalArgumentException("Ce message a déjà été traité.");
        }
        String adresse = texte(message.get("email_agent"));
        if (adresse.isEmpty()) {
            throw new IllegalArgumentException("Cet agent n'a pas d'adresse e-mail : ajoutez-la sur sa fiche, puis envoyez.");
        }
        String sujetFinal = obligatoire(sujet == null ? texte(message.get("sujet")) : sujet, 200, "Le sujet");
        String corpsFinal = obligatoire(corps == null ? texte(message.get("corps")) : corps, 10_000, "Le message");
        String destinataire = (texte(message.get("prenom")) + " " + texte(message.get("nom"))).strip();
        try {
            String reference = envoiEmail.envoyer(adresse, destinataire, sujetFinal, corpsFinal);
            repository.enregistrerEnvoi(id, "ENVOYE", adresse, sujetFinal, corpsFinal, acteurId, reference, null);
        } catch (RuntimeException echec) {
            LOG.warn("Envoi du message {} à {} refusé : {}", id, adresse, echec.getMessage());
            repository.enregistrerEnvoi(id, "ECHEC", adresse, sujetFinal, corpsFinal, acteurId, null,
                    echec.getMessage());
            throw new IllegalStateException("L'e-mail n'a pas pu être envoyé : " + echec.getMessage(), echec);
        }
        return repository.trouver(id).orElseThrow();
    }

    public void ecarter(long id, Integer acteurId) {
        verifierPersonnel(acteurId);
        if (!repository.ecarter(id, acteurId)) {
            throw new IllegalArgumentException("Ce message a déjà été traité.");
        }
    }

    // ---------------------------------------------------------------- outils

    private void proposer(Integer employeId, String type, String sujet, String corps, String cle) {
        try {
            repository.proposer(employeId, type, sujet, corps, cle);
        } catch (RuntimeException erreur) {
            // Un brouillon manqué ne doit jamais faire échouer l'action qui l'a déclenché.
            LOG.warn("Brouillon d'e-mail {} non proposé pour l'agent {} : {}", type, employeId, erreur.getMessage());
        }
    }

    /** Après la validation de l'action en cours (immédiatement s'il n'y en a pas). */
    private static void apresValidation(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private java.util.Optional<Map<String, Object>> agent(Integer employeId) {
        return employeId == null ? java.util.Optional.empty() : employeRepository.findById(employeId);
    }

    private void verifierPersonnel(Integer acteurId) {
        if (acteurId == null || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException("Seul un agent actif du Service du Personnel gère les e-mails aux agents.");
        }
    }

    private static String salutation(Map<String, Object> agent) {
        String civilite = "F".equals(agent.get("sexe")) ? "Madame" : "M".equals(agent.get("sexe")) ? "Monsieur" : "";
        String nom = (texte(agent.get("prenom")) + " " + texte(agent.get("nom"))).strip();
        return "Bonjour " + (civilite.isEmpty() ? nom : civilite + " " + nom) + ",\n\n";
    }

    private static String accord(Map<String, Object> agent) {
        return "F".equals(agent.get("sexe")) ? "e" : "";
    }

    private static String grade(Map<String, Object> agent) {
        String libelle = texte(agent.get("grade_libelle"));
        String code = texte(agent.get("grade_code"));
        return code.isEmpty() ? libelle : libelle + " (" + code + ")";
    }

    private static String indice(Map<String, Object> agent) {
        return agent.get("indice") == null ? "" : ", indice " + agent.get("indice");
    }

    private static String lieu(Map<String, Object> agent) {
        String lieu = texte(agent.get("lieu_travail"));
        return lieu.isEmpty() ? "" : ", à " + lieu;
    }

    private static String obligatoire(String valeur, int longueurMax, String libelle) {
        String propre = valeur == null ? "" : valeur.strip();
        if (propre.isEmpty()) {
            throw new IllegalArgumentException(libelle + " est obligatoire.");
        }
        if (propre.length() > longueurMax) {
            throw new IllegalArgumentException(libelle + " ne peut pas dépasser " + longueurMax + " caractères.");
        }
        return propre;
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString().strip();
    }

    private static String jour(LocalDate date) {
        return date == null ? "" : date.format(JOUR);
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate jour) {
            return jour;
        }
        if (valeur instanceof java.sql.Date jour) {
            return jour.toLocalDate();
        }
        return LocalDate.parse(valeur.toString().substring(0, 10));
    }
}
