package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DocumentRhService {

    private final DocumentRhRepository documentRhRepository;
    private final ActiviteService activiteService;

    public DocumentRhService(
            DocumentRhRepository documentRhRepository,
            ActiviteService activiteService
    ) {
        this.documentRhRepository = documentRhRepository;
        this.activiteService = activiteService;
    }

    public List<Map<String, Object>> getAllDocuments() {
        return documentRhRepository.findAll();
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

    @Transactional
    public Long creerDocument(
            Integer acteurId,
            Integer typeDocumentId,
            Long employeId,
            String objet,
            LocalDate dateDocument,
            LocalDate dateEffet,
            String statut,
            String fichierPath,
            String observation
    ) {

        validerDocument(
                acteurId,
                typeDocumentId,
                employeId,
                objet,
                dateDocument,
                dateEffet
        );

        String statutNormalise =
                normaliserStatut(statut);

        Date sqlDateDocument =
                convertirDate(dateDocument);

        Date sqlDateEffet =
                convertirDate(dateEffet);

        String reference =
                genererReference();

        Long documentId =
                documentRhRepository.create(
                        reference,
                        typeDocumentId,
                        employeId,
                        objet.trim(),
                        sqlDateDocument,
                        sqlDateEffet,
                        statutNormalise,
                        normaliserTexte(fichierPath),
                        normaliserTexte(observation)
                );

        activiteService.enregistrer(
                acteurId,
                employeId != null
                        ? employeId.intValue()
                        : null,
                "NOUVEAU_DOCUMENT",
                "Création du document "
                        + reference
                        + " : "
                        + objet.trim()
        );

        return documentId;
    }

    @Transactional
    public void modifierDocument(
            Long id,
            Integer acteurId,
            Integer typeDocumentId,
            Long employeId,
            String objet,
            LocalDate dateDocument,
            LocalDate dateEffet,
            String statut,
            String fichierPath,
            String observation
    ) {

        verifierExistence(id);

        validerDocument(
                acteurId,
                typeDocumentId,
                employeId,
                objet,
                dateDocument,
                dateEffet
        );

        String statutNormalise =
                normaliserStatut(statut);

        int lignesModifiees =
                documentRhRepository.update(
                        id,
                        typeDocumentId,
                        employeId,
                        objet.trim(),
                        convertirDate(dateDocument),
                        convertirDate(dateEffet),
                        statutNormalise,
                        normaliserTexte(fichierPath),
                        normaliserTexte(observation)
                );

        if (lignesModifiees == 0) {
            throw new IllegalArgumentException(
                    "Le document demandé n'existe pas."
            );
        }

        activiteService.enregistrer(
                acteurId,
                employeId != null
                        ? employeId.intValue()
                        : null,
                "MODIFICATION_DOCUMENT",
                "Modification du document ID "
                        + id
        );
    }

    @Transactional
    public void archiverDocument(
            Long id,
            Integer acteurId
    ) {

        verifierExistence(id);

        if (acteurId == null || acteurId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'acteur est obligatoire."
            );
        }

        Optional<Map<String, Object>> document =
                documentRhRepository.findById(id);

        Long employeId = null;

        if (document.isPresent()) {
            Object valeur =
                    document.get().get("employe_id");

            if (valeur instanceof Number number) {
                employeId =
                        number.longValue();
            }
        }

        int lignesModifiees =
                documentRhRepository.archive(id);

        if (lignesModifiees == 0) {
            throw new IllegalArgumentException(
                    "Le document demandé n'existe pas."
            );
        }

        activiteService.enregistrer(
                acteurId,
                employeId != null
                        ? employeId.intValue()
                        : null,
                "ARCHIVAGE_DOCUMENT",
                "Archivage du document ID "
                        + id
        );
    }

    public long compterDocuments() {
        return documentRhRepository.countDocuments();
    }

    private void validerDocument(
            Integer acteurId,
            Integer typeDocumentId,
            Long employeId,
            String objet,
            LocalDate dateDocument,
            LocalDate dateEffet
    ) {

        if (acteurId == null || acteurId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'acteur est obligatoire."
            );
        }

        if (typeDocumentId == null || typeDocumentId <= 0) {
            throw new IllegalArgumentException(
                    "Le type du document est obligatoire."
            );
        }

        if (!documentRhRepository.existsTypeDocument(
                typeDocumentId
        )) {
            throw new IllegalArgumentException(
                    "Le type de document demandé n'existe pas."
            );
        }

        if (employeId != null &&
                employeId > 0 &&
                !documentRhRepository.existsEmploye(employeId)) {

            throw new IllegalArgumentException(
                    "L'agent concerné n'existe pas."
            );
        }

        if (employeId != null && employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
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

        if (dateDocument != null &&
                dateDocument.isAfter(LocalDate.now())) {

            throw new IllegalArgumentException(
                    "La date du document ne peut pas être dans le futur."
            );
        }

        if (dateEffet != null &&
                dateDocument != null &&
                dateEffet.isBefore(dateDocument)) {

            throw new IllegalArgumentException(
                    "La date d'effet ne peut pas être antérieure à la date du document."
            );
        }
    }

    private String normaliserStatut(
            String statut
    ) {

        if (statut == null || statut.isBlank()) {
            return "BROUILLON";
        }

        return switch (
                statut.trim().toUpperCase()
                ) {
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