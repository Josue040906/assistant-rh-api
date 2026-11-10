package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Document;
import com.assistantrh.assistant_rh_api.model.ProgressionCarriere;
import com.assistantrh.assistant_rh_api.model.SituationCarriereSnapshot;
import com.assistantrh.assistant_rh_api.model.TypeDocument;
import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.pdf.DemandeAvancementPdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.DemandeCongePdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.DemandeMutationPdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.DemandeRetraitePdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentContent;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentGenerator;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentStorage;
import com.assistantrh.assistant_rh_api.repository.AgentDocumentRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class DocumentRhService {

    private static final Set<String> CHAMPS_CONGE =
            Set.of("dateDebut", "dateFin", "motif");
    private static final Set<String> CHAMPS_AVANCEMENT =
            Set.of("motif", "dateEffetSouhaitee");
    private static final java.time.format.DateTimeFormatter JOUR =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<String> CHAMPS_RETRAITE =
            Set.of("dateDepartSouhaitee", "motif", "observation");
    private static final Set<String> CHAMPS_MUTATION = Set.of(
            "directionSouhaiteeId",
            "serviceSouhaiteId",
            "posteSouhaiteId",
            "lieuTravailSouhaite",
            "dateEffetSouhaitee",
            "motif"
    );

    private final DocumentRhRepository documentRhRepository;
    private final AgentDocumentRepository agentDocumentRepository;
    private final SituationCarriereService situationCarriereService;
    private final ActiviteService activiteService;
    private final PdfDocumentGenerator pdfDocumentGenerator;
    private final DemandeCongePdfTemplate demandeCongePdfTemplate;
    private final DemandeAvancementPdfTemplate demandeAvancementPdfTemplate;
    private final DemandeRetraitePdfTemplate demandeRetraitePdfTemplate;
    private final DemandeMutationPdfTemplate demandeMutationPdfTemplate;
    private final PdfDocumentStorage pdfDocumentStorage;
    private final VilleService villeService;

    public DocumentRhService(
            DocumentRhRepository documentRhRepository,
            AgentDocumentRepository agentDocumentRepository,
            SituationCarriereService situationCarriereService,
            ActiviteService activiteService,
            PdfDocumentGenerator pdfDocumentGenerator,
            DemandeCongePdfTemplate demandeCongePdfTemplate,
            DemandeAvancementPdfTemplate demandeAvancementPdfTemplate,
            DemandeRetraitePdfTemplate demandeRetraitePdfTemplate,
            DemandeMutationPdfTemplate demandeMutationPdfTemplate,
            PdfDocumentStorage pdfDocumentStorage,
            VilleService villeService
    ) {
        this.documentRhRepository = documentRhRepository;
        this.agentDocumentRepository = agentDocumentRepository;
        this.situationCarriereService = situationCarriereService;
        this.activiteService = activiteService;
        this.pdfDocumentGenerator = pdfDocumentGenerator;
        this.demandeCongePdfTemplate = demandeCongePdfTemplate;
        this.demandeAvancementPdfTemplate = demandeAvancementPdfTemplate;
        this.demandeRetraitePdfTemplate = demandeRetraitePdfTemplate;
        this.demandeMutationPdfTemplate = demandeMutationPdfTemplate;
        this.pdfDocumentStorage = pdfDocumentStorage;
        this.villeService = villeService;
    }

    public List<TypeDocument> getTypesDocumentsActifs() {
        return documentRhRepository.findAllActiveTypes(LocalDate.now());
    }

    public List<Document> listerDocuments(
            Integer employeId,
            Integer typeDocumentId
    ) {
        return documentRhRepository.findDocuments(
                employeId,
                typeDocumentId
        );
    }

    public Optional<Document> getDocumentMetierById(Integer id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        return documentRhRepository.findDocumentById(id);
    }

    @Transactional
    public Optional<byte[]> obtenirPdf(Integer documentId) {
        if (documentId == null || documentId <= 0) {
            return Optional.empty();
        }

        Optional<Document> documentOption =
                documentRhRepository.findDocumentById(documentId);
        if (documentOption.isEmpty()) {
            return Optional.empty();
        }

        Document document = documentOption.get();
        if (document.fichierPath() != null && !document.fichierPath().isBlank()) {
            try {
                return pdfDocumentStorage.lire(document.fichierPath());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Impossible de lire le PDF du document.",
                        exception
                );
            }
        }

        TypeDocument typeDocument =
                documentRhRepository.findTypeDocumentById(
                        document.typeDocumentId()
                ).orElseThrow(() -> new IllegalStateException(
                        "Le type associé au document n'existe plus."
                ));
        if (document.employeId() == null) {
            throw new IllegalStateException(
                    "Le document ne possède pas d'agent associé."
            );
        }

        AgentDocumentInfo agent =
                agentDocumentRepository.findDocumentInfoById(document.employeId())
                        .orElseThrow(() -> new IllegalStateException(
                                "L'agent associé au document n'existe plus."
                        ));
        byte[] pdf = pdfDocumentGenerator.generer(contenuPdf(typeDocument.code(), document, agent));

        enregistrerPdf(documentId, pdf);
        activiteService.enregistrer(
                null,
                document.employeId(),
                "GENERATION",
                "Génération du PDF du document " + document.referenceDocument()
        );
        return Optional.of(pdf);
    }

    /** Le modèle PDF du type de document, rempli avec le document et l'agent. */
    private PdfDocumentContent contenuPdf(String codeType, Document document, AgentDocumentInfo agent) {
        return switch (codeType) {
            case "CONGE" -> demandeCongePdfTemplate.creer(document, agent);
            case "AVANCEMENT" -> demandeAvancementPdfTemplate.creer(document, agent);
            case "RETRAITE" -> demandeRetraitePdfTemplate.creer(document, agent);
            case "MUTATION" -> demandeMutationPdfTemplate.creer(document, agent);
            default -> throw new IllegalArgumentException(
                    "Le modèle PDF de ce type de document n'est pas encore disponible."
            );
        };
    }

    /**
     * Aperçu PDF d'une demande en cours de saisie : la validation de la
     * création, puis le même modèle PDF, sans rien enregistrer ni journaliser.
     */
    public byte[] apercuPdfDocumentMetier(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees
    ) {
        Preparation preparation = preparerDocumentMetier(typeDocumentId, employeId, destinataire, donnees, null);
        AgentDocumentInfo agent = agentDocumentRepository.findDocumentInfoById(employeId)
                .orElseThrow(() -> new IllegalArgumentException("L'agent concerné n'existe pas."));
        Document apercu = new Document(
                null,
                "APERÇU – non enregistré",
                typeDocumentId,
                employeId,
                preparation.destinataire(),
                preparation.type().libelle(),
                LocalDate.now(),
                preparation.dateEffet(),
                "CREE",
                null,
                null,
                null,
                preparation.donnees()
        );
        return pdfDocumentGenerator.generer(contenuPdf(preparation.type().code(), apercu, agent));
    }

    @Transactional
    public Document creerDocumentMetier(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees
    ) {
        return creerDocumentMetier(typeDocumentId, employeId, destinataire, donnees, null, null);
    }

    @Transactional
    public Document creerDocumentMetier(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees,
            String constatsPiece
    ) {
        return creerDocumentMetier(typeDocumentId, employeId, destinataire, donnees, constatsPiece, null);
    }

    /**
     * constatsPiece (facultatif) : ce qui a été constaté sur la pièce scannée
     * d'où vient la demande (tampon, signature, écarts avec le dossier). Il est
     * gardé avec le brouillon pour la personne qui le vérifiera ; il ne valide rien.
     */
    /** auteurId : le compte qui crée la demande ; lui seul pourra la valider. */
    @Transactional
    public Document creerDocumentMetier(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees,
            String constatsPiece,
            Integer auteurId
    ) {
        Preparation preparation = preparerDocumentMetier(
                typeDocumentId, employeId, destinataire, donnees, constatsPiece);
        Document document = new Document(
                null,
                genererReference(),
                typeDocumentId,
                employeId,
                preparation.destinataire(),
                preparation.type().libelle(),
                LocalDate.now(),
                preparation.dateEffet(),
                "CREE",
                null,
                null,
                null,
                preparation.donnees()
        );

        Document documentCree = documentRhRepository.insertDocument(document);
        if (auteurId != null && documentCree.id() != null) {
            documentRhRepository.enregistrerAuteur(documentCree.id(), auteurId);
        }
        activiteService.enregistrer(
                auteurId,
                employeId,
                "CREATION",
                "Création du document " + documentCree.referenceDocument()
                        + " (" + documentCree.objet() + ") pour l'agent "
                        + employeId
        );
        return documentCree;
    }

    /**
     * Aperçu d'une demande : exactement la validation de la création, sans
     * rien enregistrer. Le résumé dit en clair ce qui serait créé (type,
     * agent, destinataire, données validées), pour qu'on confirme en
     * connaissance de cause.
     */
    public Map<String, Object> apercuDocumentMetier(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees
    ) {
        return apercuDocumentMetier(typeDocumentId, employeId, destinataire, donnees, null);
    }

    public Map<String, Object> apercuDocumentMetier(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees,
            String constatsPiece
    ) {
        Preparation preparation = preparerDocumentMetier(
                typeDocumentId, employeId, destinataire, donnees, constatsPiece);
        Map<String, Object> resume = new LinkedHashMap<>();
        resume.put("Document", preparation.type().libelle());
        resume.put("Agent", agentDocumentRepository.findDocumentInfoById(employeId)
                .map(agent -> String.join(" ", agent.nom(), agent.prenom())
                        + (agent.matricule() == null ? "" : " (" + agent.matricule() + ")"))
                .orElse("Agent n° " + employeId));
        resume.put("Destinataire", preparation.destinataire());
        preparation.donnees().forEach((cle, valeur) -> {
            String texte = enClair(valeur);
            if (!texte.isBlank()) {
                resume.put(LIBELLES_APERCU.getOrDefault(cle, cle), texte);
            }
        });
        resume.put("Statut", "CREE");
        return resume;
    }

    private static final Map<String, String> LIBELLES_APERCU = Map.of(
            "dateDebut", "Du",
            "dateFin", "Au",
            "motif", "Motif",
            "dateDepartSouhaitee", "Départ souhaité",
            "observation", "Observation",
            "lieuTravailSouhaite", "Lieu de travail souhaité",
            "dateEffetSouhaitee", "Effet souhaité",
            "situationActuelle", "Situation actuelle",
            "situationDemandee", "Situation demandée",
            "constatsPiece", "Constats sur la pièce"
    );

    /** Une donnée validée, lisible : date au format JJ/MM/AAAA, situation en libellés (sans ses identifiants). */
    private static String enClair(Object valeur) {
        if (valeur instanceof Map<?, ?> situation) {
            return String.join(" · ", situation.entrySet().stream()
                    .filter(entry -> !String.valueOf(entry.getKey()).endsWith("Id"))
                    .map(entry -> enClair(entry.getValue()))
                    .filter(texte -> !texte.isBlank())
                    .toList());
        }
        if (valeur == null) {
            return "";
        }
        String texte = String.valueOf(valeur).trim();
        try {
            return LocalDate.parse(texte).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException pasUneDate) {
            return texte;
        }
    }

    /** Ce qu'une demande valide donne : son type, son destinataire, ses données vérifiées. */
    private record Preparation(
            TypeDocument type,
            String destinataire,
            Map<String, Object> donnees,
            LocalDate dateEffet
    ) {
    }

    private Preparation preparerDocumentMetier(
            Integer typeDocumentId,
            Integer employeId,
            String destinataire,
            Map<String, Object> donnees,
            String constatsPiece
    ) {
        if (typeDocumentId == null || typeDocumentId <= 0) {
            throw new IllegalArgumentException(
                    "Le type du document est obligatoire."
            );
        }

        TypeDocument typeDocument =
                documentRhRepository.findTypeDocumentById(typeDocumentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Le type de document demandé n'existe pas."
                        ));

        LocalDate dateDocument = LocalDate.now();
        if (!typeDocument.estActifA(dateDocument)) {
            throw new IllegalArgumentException(
                    "Le type de document n'est pas actif."
            );
        }

        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est obligatoire et doit être valide."
            );
        }

        if (!documentRhRepository.existsEmploye(employeId.longValue())) {
            throw new IllegalArgumentException(
                    "L'agent concerné n'existe pas."
            );
        }

        if (destinataire == null || destinataire.isBlank()) {
            throw new IllegalArgumentException(
                    "Le destinataire du document est obligatoire."
            );
        }

        String destinataireNormalise = destinataire.trim();
        if (destinataireNormalise.length() > 255) {
            throw new IllegalArgumentException(
                    "Le destinataire ne peut pas dépasser 255 caractères."
            );
        }

        if (donnees == null) {
            throw new IllegalArgumentException(
                    "Les données spécifiques du document sont obligatoires."
            );
        }

        Map<String, Object> donneesDocument =
                validerDonneesSpecifiques(
                        typeDocument,
                        employeId,
                        donnees
                );
        LocalDate dateEffet = dateEffetDeLaDemande(typeDocument.code(), employeId, donneesDocument);
        if (constatsPiece != null && !constatsPiece.isBlank()) {
            String constats = constatsPiece.strip();
            if (constats.length() > 2000) {
                throw new IllegalArgumentException(
                        "Les constats sur la pièce ne peuvent pas dépasser 2000 caractères."
                );
            }
            // Ajouté après la validation propre au type : ce n'est pas une donnée de la demande.
            donneesDocument.put("constatsPiece", constats);
        }
        return new Preparation(typeDocument, destinataireNormalise, donneesDocument, dateEffet);
    }

    @Transactional
    public String enregistrerPdf(Integer documentId, byte[] contenuPdf) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du document est invalide."
            );
        }

        String chemin = pdfDocumentStorage.enregistrer(
                documentId,
                contenuPdf
        );

        int lignesModifiees;
        try {
            lignesModifiees = documentRhRepository.updatePdfPath(
                    documentId,
                    chemin
            );
        } catch (DataAccessException exception) {
            supprimerPdfApresEchec(chemin, exception);
            throw exception;
        }

        if (lignesModifiees == 0) {
            IllegalArgumentException exception = new IllegalArgumentException(
                    "Le document demandé n'existe pas."
            );
            supprimerPdfApresEchec(chemin, exception);
            throw exception;
        }

        return chemin;
    }

    private void supprimerPdfApresEchec(
            String chemin,
            RuntimeException exceptionInitiale
    ) {
        try {
            pdfDocumentStorage.supprimer(chemin);
        } catch (IOException exceptionSuppression) {
            exceptionInitiale.addSuppressed(exceptionSuppression);
        }
    }

    private Map<String, Object> validerDonneesSpecifiques(
            TypeDocument typeDocument,
            Integer employeId,
            Map<String, Object> donnees
    ) {
        Map<String, Object> resultat = new LinkedHashMap<>(donnees);

        if ("CONGE".equals(typeDocument.code())) {
            for (String champ : donnees.keySet()) {
                if (!CHAMPS_CONGE.contains(champ)) {
                    throw new IllegalArgumentException(
                            "Le champ '" + champ
                                    + "' n'est pas prévu pour une demande de congé."
                    );
                }
            }

            validerDatesConge(donnees);
        } else if ("AVANCEMENT".equals(typeDocument.code())) {
            validerDonneesAvancement(employeId, donnees, resultat);
        } else if ("RETRAITE".equals(typeDocument.code())) {
            validerDonneesRetraite(donnees);
        } else if ("MUTATION".equals(typeDocument.code())) {
            validerDonneesMutation(employeId, donnees, resultat);
        }

        return resultat;
    }

    private void validerDonneesMutation(
            Integer employeId,
            Map<String, Object> donnees,
            Map<String, Object> resultat
    ) {
        for (String champ : donnees.keySet()) {
            if (!CHAMPS_MUTATION.contains(champ)) {
                throw new IllegalArgumentException(
                        "Le champ '" + champ
                                + "' n'est pas prévu pour une demande de mutation."
                );
            }
        }

        Object motifValeur = donnees.get("motif");
        if (!(motifValeur instanceof String motif) || motif.isBlank()) {
            throw new IllegalArgumentException(
                    "Le motif de mutation est obligatoire."
            );
        }
        if (motif.trim().length() > 2000) {
            throw new IllegalArgumentException(
                    "Le motif de mutation ne peut pas dépasser 2000 caractères."
            );
        }

        Long directionId = lireIdentifiantMutation(
                donnees,
                "directionSouhaiteeId"
        );
        Long serviceId = lireIdentifiantMutation(
                donnees,
                "serviceSouhaiteId"
        );
        Long posteId = lireIdentifiantMutation(
                donnees,
                "posteSouhaiteId"
        );
        String lieuTravail = villeService.validerVille(
                lireLieuTravailMutation(donnees),
                "Le lieu de travail souhaité"
        );
        if (directionId == null && serviceId == null
                && posteId == null && lieuTravail == null) {
            throw new IllegalArgumentException(
                    "Indiquez au moins un élément de la situation souhaitée."
            );
        }

        Map<String, Object> direction = directionId == null
                ? null
                : documentRhRepository.findMutationDirection(directionId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "La direction souhaitée n'existe pas."
                        ));
        Map<String, Object> service = serviceId == null
                ? null
                : documentRhRepository.findMutationService(serviceId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Le service souhaité n'existe pas."
                        ));
        Map<String, Object> poste = posteId == null
                ? null
                : documentRhRepository.findMutationPoste(posteId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Le poste souhaité n'existe pas."
                        ));

        verifierCoherenceMutation(directionId, serviceId, posteId, service, poste);

        Map<String, Object> situationDemandee = new LinkedHashMap<>();
        if (direction != null) {
            situationDemandee.put("directionId", direction.get("id"));
            situationDemandee.put("direction", direction.get("nom"));
        } else if (service != null && service.get("direction_id") != null) {
            situationDemandee.put("directionId", service.get("direction_id"));
            situationDemandee.put("direction", service.get("direction"));
        } else if (poste != null && poste.get("direction_id") != null) {
            situationDemandee.put("directionId", poste.get("direction_id"));
            situationDemandee.put("direction", poste.get("direction"));
        } else {
            situationDemandee.put("directionId", null);
            situationDemandee.put("direction", null);
        }

        if (service != null) {
            situationDemandee.put("serviceId", service.get("id"));
            situationDemandee.put("service", service.get("nom"));
        } else if (poste != null) {
            situationDemandee.put("serviceId", poste.get("service_id"));
            situationDemandee.put("service", poste.get("service"));
        } else {
            situationDemandee.put("serviceId", null);
            situationDemandee.put("service", null);
        }

        situationDemandee.put(
                "posteId",
                poste == null ? null : poste.get("id")
        );
        situationDemandee.put(
                "poste",
                poste == null ? null : poste.get("intitule")
        );
        situationDemandee.put("lieuTravail", lieuTravail);

        AgentDocumentInfo agent = agentDocumentRepository
                .findDocumentInfoById(employeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Les informations actuelles de l'agent sont introuvables."
                ));
        Map<String, Object> situationActuelle = new LinkedHashMap<>();
        situationActuelle.put("directionId", agent.directionId());
        situationActuelle.put("direction", agent.direction());
        situationActuelle.put("serviceId", agent.serviceId());
        situationActuelle.put("service", agent.service());
        situationActuelle.put("posteId", agent.posteId());
        situationActuelle.put("poste", agent.poste());
        situationActuelle.put("lieuTravail", agent.lieuTravail());

        String dateEffet = lireDateEffetMutation(donnees);
        resultat.clear();
        resultat.put("situationActuelle", situationActuelle);
        resultat.put("situationDemandee", situationDemandee);
        resultat.put("dateEffetSouhaitee", dateEffet);
        resultat.put("motif", motif.trim());
    }

    private Long lireIdentifiantMutation(
            Map<String, Object> donnees,
            String champ
    ) {
        Object valeur = donnees.get(champ);
        if (valeur == null || valeur instanceof String texte && texte.isBlank()) {
            return null;
        }
        if (!(valeur instanceof Number nombre)
                || nombre.doubleValue() != nombre.longValue()
                || nombre.longValue() <= 0) {
            throw new IllegalArgumentException(
                    "Le champ '" + champ + "' doit contenir un identifiant valide."
            );
        }
        return nombre.longValue();
    }

    private String lireLieuTravailMutation(Map<String, Object> donnees) {
        Object valeur = donnees.get("lieuTravailSouhaite");
        if (valeur == null) {
            return null;
        }
        if (!(valeur instanceof String lieu)) {
            throw new IllegalArgumentException(
                    "Le lieu de travail souhaité doit être une chaîne de caractères."
            );
        }
        String lieuNormalise = lieu.trim();
        if (lieuNormalise.length() > 255) {
            throw new IllegalArgumentException(
                    "Le lieu de travail souhaité ne peut pas dépasser 255 caractères."
            );
        }
        return lieuNormalise.isEmpty() ? null : lieuNormalise;
    }

    private String lireDateEffetMutation(Map<String, Object> donnees) {
        return lireDateAVenir(donnees, "dateEffetSouhaitee", "La date d'effet de la mutation").toString();
    }

    /** Une date d'effet obligatoire, aujourd'hui ou plus tard : une demande vise l'avenir. */
    private LocalDate lireDateAVenir(Map<String, Object> donnees, String champ, String libelle) {
        Object valeur = donnees.get(champ);
        if (!(valeur instanceof String texte) || texte.isBlank()) {
            throw new IllegalArgumentException(libelle + " est obligatoire (format AAAA-MM-JJ).");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(texte);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    libelle + " doit être une date valide au format AAAA-MM-JJ.", exception);
        }
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(libelle + " ne peut pas être passée.");
        }
        return date;
    }

    /**
     * Le jour où la demande devient applicable. Retraite : le départ souhaité,
     * mais jamais avant que la durée de service exigée (donnée) soit atteinte.
     */
    private LocalDate dateEffetDeLaDemande(String code, Integer employeId, Map<String, Object> donnees) {
        LocalDate aujourdhui = LocalDate.now();
        return switch (code) {
            case "AVANCEMENT", "MUTATION" -> LocalDate.parse(String.valueOf(donnees.get("dateEffetSouhaitee")));
            case "CONGE" -> {
                LocalDate debut = LocalDate.parse(String.valueOf(donnees.get("dateDebut")));
                yield debut.isBefore(aujourdhui) ? aujourdhui : debut;
            }
            case "RETRAITE" -> {
                LocalDate depart = lireDateAVenir(donnees, "dateDepartSouhaitee", "La date souhaitée de départ");
                LocalDate possible = documentRhRepository.dateRetraitePossible(employeId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "La date d'embauche de l'agent ou la durée de service exigée n'est pas renseignée."));
                yield depart.isBefore(possible) ? possible : depart;
            }
            default -> null;
        };
    }

    private void verifierCoherenceMutation(
            Long directionId,
            Long serviceId,
            Long posteId,
            Map<String, Object> service,
            Map<String, Object> poste
    ) {
        if (directionId != null && service != null
                && !correspondA(service.get("direction_id"), directionId)) {
            throw new IllegalArgumentException(
                    "Le service choisi n'appartient pas à la direction sélectionnée."
            );
        }
        if (serviceId != null && poste != null
                && !correspondA(poste.get("service_id"), serviceId)) {
            throw new IllegalArgumentException(
                    "Le poste choisi n'appartient pas au service sélectionné."
            );
        }
        if (directionId != null && poste != null
                && !correspondA(poste.get("direction_id"), directionId)) {
            throw new IllegalArgumentException(
                    "Le poste choisi n'appartient pas à la direction sélectionnée."
            );
        }
    }

    private boolean correspondA(Object valeur, Long identifiant) {
        return valeur instanceof Number nombre
                && nombre.longValue() == identifiant;
    }

    private void validerDonneesRetraite(Map<String, Object> donnees) {
        for (String champ : donnees.keySet()) {
            if (!CHAMPS_RETRAITE.contains(champ)) {
                throw new IllegalArgumentException(
                        "Le champ '" + champ
                                + "' n'est pas prévu pour une demande de retraite."
                );
            }
        }

        // Un départ souhaité est à venir : lireDateRetraite ne vérifie que la date elle-même.
        lireDateRetraite(donnees);

        validerTexteOptionnel(donnees, "motif", 2000);
        validerTexteOptionnel(donnees, "observation", 2000);
    }

    private LocalDate lireDateRetraite(Map<String, Object> donnees) {
        Object valeur = donnees.get("dateDepartSouhaitee");
        if (!(valeur instanceof String dateTexte) || dateTexte.isBlank()) {
            throw new IllegalArgumentException(
                    "La date souhaitée de départ est obligatoire et doit être au format AAAA-MM-JJ."
            );
        }

        try {
            return LocalDate.parse(dateTexte);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "La date souhaitée de départ doit être une date valide au format AAAA-MM-JJ.",
                    exception
            );
        }
    }

    private void validerTexteOptionnel(
            Map<String, Object> donnees,
            String champ,
            int longueurMax
    ) {
        Object valeur = donnees.get(champ);
        if (valeur != null && !(valeur instanceof String)) {
            throw new IllegalArgumentException(
                    "Le champ '" + champ + "' doit être une chaîne de caractères."
            );
        }
        if (valeur instanceof String texte && texte.length() > longueurMax) {
            throw new IllegalArgumentException(
                    "Le champ '" + champ + "' ne peut pas dépasser "
                            + longueurMax + " caractères."
            );
        }
    }

    /** Un congé peut être planifié à l'avance : seule la fin ne doit pas précéder le début. */
    private void validerDatesConge(Map<String, Object> donnees) {
        LocalDate dateDebut = lireDateConge(donnees, "dateDebut");
        LocalDate dateFin = lireDateConge(donnees, "dateFin");
        if (dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException(
                    "La date de fin du congé ne peut pas précéder sa date de début."
            );
        }

        Object motif = donnees.get("motif");
        if (motif != null && !(motif instanceof String)) {
            throw new IllegalArgumentException(
                    "Le motif du congé doit être une chaîne de caractères."
            );
        }
    }

    private void validerDonneesAvancement(
            Integer employeId,
            Map<String, Object> donnees,
            Map<String, Object> resultat
    ) {
        for (String champ : donnees.keySet()) {
            if (!CHAMPS_AVANCEMENT.contains(champ)) {
                throw new IllegalArgumentException(
                        "Le champ '" + champ
                                + "' n'est pas prévu pour une demande d'avancement."
                );
            }
        }

        Object motif = donnees.get("motif");
        if (motif != null && !(motif instanceof String)) {
            throw new IllegalArgumentException(
                    "Le motif d'avancement doit être une chaîne de caractères."
            );
        }
        if (motif instanceof String texte && texte.length() > 2000) {
            throw new IllegalArgumentException(
                    "Le motif d'avancement ne peut pas dépasser 2000 caractères."
            );
        }

        ProgressionCarriere progression =
                situationCarriereService.obtenirProgressionCarriere(employeId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Aucune situation de carrière actuelle n'est disponible pour cet agent."
                        ));

        // L'éligibilité se juge à la date d'effet demandée, pas au jour de la demande.
        LocalDate dateEffet = lireDateAVenir(donnees, "dateEffetSouhaitee", "La date d'effet de l'avancement");
        SituationCarriereSnapshot actuelle = progression.situationActuelle();
        if (!progression.eligibiliteDeterminee()
                || actuelle.dureeMinAnnees() == null || actuelle.dateDebut() == null) {
            throw new IllegalArgumentException(
                    "L'éligibilité à l'avancement n'est pas établie pour cet agent."
            );
        }
        LocalDate possible = actuelle.dateDebut().plusYears(actuelle.dureeMinAnnees());
        if (dateEffet.isBefore(possible)) {
            throw new IllegalArgumentException(
                    "L'avancement sera possible à partir du " + possible.format(JOUR)
                            + " : choisissez une date d'effet à partir de ce jour."
            );
        }
        resultat.put("dateEffetSouhaitee", dateEffet.toString());

        SituationCarriereSnapshot suivante =
                progression.situationDemandee();
        if (suivante == null) {
            throw new IllegalArgumentException(
                    "Aucune situation d'avancement suivante n'est disponible."
            );
        }

        resultat.put(
                "situationActuelle",
                creerDonneesSituation(progression.situationActuelle())
        );
        resultat.put(
                "situationDemandee",
                creerDonneesSituation(suivante)
        );
    }

    private Map<String, Object> creerDonneesSituation(
            SituationCarriereSnapshot situation
    ) {
        Map<String, Object> donnees = new LinkedHashMap<>();
        donnees.put("echelonId", situation.echelonId());
        donnees.put("echelonOrdre", situation.echelonOrdre());
        donnees.put("classeId", situation.classeId());
        donnees.put("classeLibelle", situation.classeLibelle());
        donnees.put("classeOrdre", situation.classeOrdre());
        donnees.put("dureeMinAnnees", situation.dureeMinAnnees());
        donnees.put("dateDebut", situation.dateDebut());
        return donnees;
    }

    private LocalDate lireDateConge(
            Map<String, Object> donnees,
            String champ
    ) {
        Object valeur = donnees.get(champ);
        if (!(valeur instanceof String dateTexte) || dateTexte.isBlank()) {
            throw new IllegalArgumentException(
                    "Le champ '" + champ
                            + "' est obligatoire et doit être une date au format AAAA-MM-JJ."
            );
        }

        try {
            return LocalDate.parse(dateTexte);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Le champ '" + champ
                            + "' doit être une date valide au format AAAA-MM-JJ.",
                    exception
            );
        }
    }

    public List<Map<String, Object>> getAllDocuments() {
        return documentRhRepository.findAll();
    }

    /** Sans statut : tous les documents. */
    public List<Map<String, Object>> getDocumentsAuStatut(String statut) {
        if (statut == null || statut.isBlank()) {
            return getAllDocuments();
        }
        if ("APPLICABLE".equalsIgnoreCase(statut.trim())) {
            return getAllDocuments().stream()
                    .filter(document -> Boolean.TRUE.equals(document.get("applicable")))
                    .toList();
        }
        return getAllDocuments().stream()
                .filter(document -> statut.trim().equalsIgnoreCase(
                        String.valueOf(document.get("statut"))))
                .toList();
    }

    public Optional<Map<String, Object>> getDocumentById(
            Long id
    ) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        return documentRhRepository.findById(id);
    }

    public List<Map<String, Object>> rechercherDocuments(
            String query
    ) {

        if (query == null || query.isBlank()) {
            return getAllDocuments();
        }

        return documentRhRepository.search(query);
    }

    public List<Map<String, Object>> getDocumentsByAgent(
            Long employeId
    ) {

        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }

        return documentRhRepository.findByAgentId(
                employeId
        );
    }

    public long compterDocuments() {
        return documentRhRepository.countDocuments();
    }

    private String normaliserTexte(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private Date convertirDate(
            LocalDate date
    ) {

        return date != null
                ? Date.valueOf(date)
                : null;
    }

    private void verifierExistence(
            Long id
    ) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du document est invalide."
            );
        }

        if (documentRhRepository.findById(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "Le document demandé n'existe pas."
            );
        }
    }

    private String genererReference() {

        long prochainNumero =
                documentRhRepository.countDocuments()
                        + 1;

        return String.format(
                "DOC-%d-%05d",
                Year.now().getValue(),
                prochainNumero
        );
    }
}