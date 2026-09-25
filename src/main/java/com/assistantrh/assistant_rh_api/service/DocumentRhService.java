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

    public DocumentRhService(DocumentRhRepository documentRhRepository) {
        this.documentRhRepository = documentRhRepository;
    }

    public List<Map<String, Object>> getAllDocuments() {
        return documentRhRepository.findAll();
    }

    public Optional<Map<String, Object>> getDocumentById(Long id) {
        return documentRhRepository.findById(id);
    }

    public List<Map<String, Object>> rechercherDocuments(String query) {
        return documentRhRepository.search(query);
    }

    public List<Map<String, Object>> getDocumentsByAgent(Long agentId) {
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

        String reference = genererReference();

        Date sqlDate = dateDocument != null
                ? Date.valueOf(dateDocument)
                : null;

        return documentRhRepository.create(
                reference,
                type,
                objet,
                contenu,
                agentId,
                dossierId,
                auteur,
                sqlDate
        );
    }

    public long compterDocuments() {
        return documentRhRepository.countDocuments();
    }

    private String genererReference() {

        long prochainNumero = documentRhRepository.countDocuments() + 1;

        return String.format(
                "DOC-%d-%05d",
                Year.now().getValue(),
                prochainNumero
        );
    }
}
