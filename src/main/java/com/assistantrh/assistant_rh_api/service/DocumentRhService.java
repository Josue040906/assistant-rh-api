        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DocumentRhService {

    private final DocumentRhRepository documentRhRepository;
    private final NotificationService notificationService;

    public DocumentRhService(
            DocumentRhRepository documentRhRepository,
            NotificationService notificationService
    ) {
        this.documentRhRepository = documentRhRepository;
        this.notificationService = notificationService;
    }

    public List<Map<String, Object>> getAllDocuments() {
        return documentRhRepository.findAll();
    }

    public Optional<Map<String, Object>> getDocumentById(Long id) {
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
            Long agentId
    ) {
        if (agentId == null || agentId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }

        return documentRhRepository.findByAgentId(agentId);
    }

    public Long creerDocument(
            String type,
            String objet,
            String contenu,
            Long agentId,
            Long dossierId,
            String auteur,
            LocalDate dateDocument
    ) {

        validerDocument(
                type,
                objet,
                contenu,
                agentId,
                auteur,
                dateDocument
        );

        String reference = genererReference();

        Date sqlDate = convertirDate(dateDocument);

        Long documentId =
                documentRhRepository.create(
                        reference,
                        type.trim(),
                        objet.trim(),
                        contenu.trim(),
                        agentId,
                        dossierId,
                        normaliserTexte(auteur),
                        sqlDate
                );

        /*
         * Le document a été créé avec succès.
         * On crée maintenant une notification pour tous les utilisateurs.
         */
        notificationService.notifierNouveauDocument(
                reference,
                objet
        );

        return documentId;
    }

    public void modifierDocument(
            Long id,
            String type,
            String objet,
            String contenu,
            Long agentId,
            Long dossierId,
            String statut,
            String auteur,
            LocalDate dateDocument
    ) {

        verifierExistence(id);

        validerDocument(
                type,
                objet,
                contenu,
                agentId,
                auteur,
                dateDocument
        );

        String statutNormalise = normaliserStatut(statut);

        Date sqlDate = convertirDate(dateDocument);

        int lignesModifiees =
                documentRhRepository.update(
                        id,
                        type.trim(),
                        objet.trim(),
                        contenu.trim(),
                        agentId,
                        dossierId,
                        statutNormalise,
                        normaliserTexte(auteur),
                        sqlDate
                );

        if (lignesModifiees == 0) {
            throw new IllegalArgumentException(
                    "Le document demandé n'existe pas."
            );
        }
    }

    public void archiverDocument(Long id) {

        verifierExistence(id);

        int lignesModifiees =
                documentRhRepository.archive(id);

        if (lignesModifiees == 0) {
            throw new IllegalArgumentException(
                    "Le document demandé n'existe pas."
            );
        }
    }

    public long compterDocuments() {
        return documentRhRepository.countDocuments();
    }

    private void validerDocument(
            String type,
            String objet,
            String contenu,
            Long agentId,
            String auteur,
            LocalDate dateDocument
    ) {

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Le type du document est obligatoire."
            );
        }

        if (!estTypeValide(type)) {
            throw new IllegalArgumentException(
                    "Le type du document est invalide."
            );
        }

        if (objet == null || objet.isBlank()) {
            throw new IllegalArgumentException(
                    "L'objet du document est obligatoire."
            );
        }

        if (objet.trim().length() > 255) {
            throw new IllegalArgumentException(
                    "L'objet du document ne peut pas dépasser 255 caractères."
            );
        }

        if (contenu == null || contenu.isBlank()) {
            throw new IllegalArgumentException(
                    "Le contenu du document est obligatoire."
            );
        }

        if (agentId == null || agentId <= 0) {
            throw new IllegalArgumentException(
                    "L'agent concerné est obligatoire."
            );
        }

        if (auteur != null && auteur.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "L'auteur ne peut pas dépasser 150 caractères."
            );
        }

        if (dateDocument != null
                && dateDocument.isAfter(LocalDate.now())) {

            throw new IllegalArgumentException(
                    "La date du document ne peut pas être dans le futur."
            );
        }
    }

    private boolean estTypeValide(String type) {

        return switch (type.trim().toUpperCase()) {
            case "DEMANDE", "COURRIER", "ACTE" -> true;
            default -> false;
        };
    }

    private String normaliserStatut(String statut) {

        if (statut == null || statut.isBlank()) {
            return "BROUILLON";
        }

        return switch (statut.trim().toUpperCase()) {
            case "BROUILLON",
                 "A_VERIFIER",
                 "VALIDE",
                 "SIGNE",
                 "ARCHIVE" ->
                    statut.trim().toUpperCase();

            default ->
                    throw new IllegalArgumentException(
                            "Le statut du document est invalide."
                    );
        };
    }

    private String normaliserTexte(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private Date convertirDate(LocalDate date) {

        return date != null
                ? Date.valueOf(date)
                : null;
    }

    private void verifierExistence(Long id) {

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
                documentRhRepository.countDocuments() + 1;

        return String.format(
                "DOC-%d-%05d",
                Year.now().getValue(),
                prochainNumero
        );
    }
}
