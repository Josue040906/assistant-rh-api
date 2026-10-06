package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import com.assistantrh.assistant_rh_api.model.TypeDocument;
import com.assistantrh.assistant_rh_api.pdf.DemandeCongePdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentContent;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentGenerator;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentStorage;
import com.assistantrh.assistant_rh_api.repository.AgentDocumentRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRhServiceTests {

    private final DocumentRhRepository documentRhRepository =
            mock(DocumentRhRepository.class);
    private final AgentDocumentRepository agentDocumentRepository =
            mock(AgentDocumentRepository.class);
    private final ActiviteService activiteService =
            mock(ActiviteService.class);
    private final PdfDocumentGenerator pdfDocumentGenerator =
            mock(PdfDocumentGenerator.class);
    private final DemandeCongePdfTemplate demandeCongePdfTemplate =
            mock(DemandeCongePdfTemplate.class);
    private final PdfDocumentStorage pdfDocumentStorage =
            mock(PdfDocumentStorage.class);
    private final DocumentRhService service = new DocumentRhService(
            documentRhRepository,
            agentDocumentRepository,
            activiteService,
            pdfDocumentGenerator,
            demandeCongePdfTemplate,
            pdfDocumentStorage
    );

    @Test
    void creeUnDocumentAvecUnTypeActifEtUnAgentExistant() {
        TypeDocument type = new TypeDocument(
                3,
                "CONGE",
                "Demande de congé",
                null,
                null,
                null
        );
        Map<String, Object> donnees = Map.of(
                "dateDebut",
                "2026-10-10",
                "dateFin",
                "2026-10-12"
        );

        when(documentRhRepository.findTypeDocumentById(3))
                .thenReturn(Optional.of(type));
        when(documentRhRepository.existsEmploye(42L)).thenReturn(true);
        when(documentRhRepository.countDocuments()).thenReturn(4L);
        when(documentRhRepository.insertDocument(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Document document = service.creerDocumentMetier(
                3,
                42,
                "  Chef du Service du Personnel  ",
                donnees
        );

        assertEquals("DOC-" + LocalDate.now().getYear() + "-00005",
                document.referenceDocument());
        assertEquals(3, document.typeDocumentId());
        assertEquals(42, document.employeId());
        assertEquals("Chef du Service du Personnel", document.destinataire());
        assertEquals("BROUILLON", document.statut());
        assertEquals(donnees, document.donnees());
        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void refuseUnTypeInactifAvantDeVerifierLAgent() {
        TypeDocument type = new TypeDocument(
                3,
                "CONGE",
                "Demande de congé",
                null,
                null,
                LocalDate.now().minusDays(1)
        );
        when(documentRhRepository.findTypeDocumentById(3))
                .thenReturn(Optional.of(type));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of()
                )
        );

        verify(documentRhRepository, never()).existsEmploye(any(Long.class));
        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void refuseUnAgentInexistant() {
        when(documentRhRepository.findTypeDocumentById(3))
                .thenReturn(Optional.of(new TypeDocument(
                        3,
                        "CONGE",
                        "Demande de congé",
                        null,
                        null,
                        null
                )));
        when(documentRhRepository.existsEmploye(42L)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of()
                )
        );

        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void refuseUneDemandeCongeSansDatesObligatoires() {
        preparerCreationType("CONGE");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of("dateDebut", "2026-10-10")
                )
        );

        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void refuseUneDemandeCongeDontLaDateFinPrecedeLeDebut() {
        preparerCreationType("CONGE");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of(
                                "dateDebut",
                                "2026-10-13",
                                "dateFin",
                                "2026-10-12"
                        )
                )
        );

        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void differeLesReglesSpecifiquesDAvancementEtDeRetraite() {
        for (String code : List.of("AVANCEMENT", "RETRAITE")) {
            preparerCreationType(code);

            Document document = service.creerDocumentMetier(
                    3,
                    42,
                    "Chef du Service du Personnel",
                    Map.of("champMetierAConfirmer", "valeur")
            );

            assertEquals(code, document.objet());
        }
    }

    @Test
    void enregistreLeCheminPdfSurLeDocument() {
        byte[] pdf = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(pdfDocumentStorage.enregistrer(15, pdf))
                .thenReturn("documents/document-15-test.pdf");
        when(documentRhRepository.updatePdfPath(
                15,
                "documents/document-15-test.pdf"
        )).thenReturn(1);

        String chemin = service.enregistrerPdf(15, pdf);

        assertEquals("documents/document-15-test.pdf", chemin);
        verify(documentRhRepository).updatePdfPath(15, chemin);
    }

    @Test
    void supprimeLeFichierSiLeDocumentNExistePlus() throws Exception {
        byte[] pdf = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String chemin = "documents/document-15-test.pdf";
        when(pdfDocumentStorage.enregistrer(15, pdf)).thenReturn(chemin);
        when(documentRhRepository.updatePdfPath(15, chemin)).thenReturn(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.enregistrerPdf(15, pdf)
        );

        verify(pdfDocumentStorage).supprimer(chemin);
    }

    @Test
    void genereEtEnregistreLePdfAuPremierTelechargement() {
        Document document = documentSansPdf();
        TypeDocument type = new TypeDocument(
                3,
                "CONGE",
                "Demande de congé",
                null,
                null,
                null
        );
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "Jean Rakoto",
                "AG-042",
                "Service RH",
                "Gestionnaire"
        );
        PdfDocumentContent contenu = new PdfDocumentContent(
                "Administration",
                null,
                "Demande",
                document.referenceDocument(),
                agent.nomComplet(),
                agent.matricule(),
                agent.service(),
                agent.poste(),
                document.destinataire(),
                document.objet(),
                "Texte",
                List.of(),
                document.dateDocument()
        );
        byte[] pdf = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(documentRhRepository.findDocumentById(15))
                .thenReturn(Optional.of(document));
        when(documentRhRepository.findTypeDocumentById(3))
                .thenReturn(Optional.of(type));
        when(agentDocumentRepository.findDocumentInfoById(42))
                .thenReturn(Optional.of(agent));
        when(demandeCongePdfTemplate.creer(document, agent))
                .thenReturn(contenu);
        when(pdfDocumentGenerator.generer(contenu)).thenReturn(pdf);
        when(pdfDocumentStorage.enregistrer(15, pdf))
                .thenReturn("documents/document-15-test.pdf");
        when(documentRhRepository.updatePdfPath(
                15,
                "documents/document-15-test.pdf"
        )).thenReturn(1);

        assertEquals(
                Optional.of(pdf),
                service.obtenirPdf(15)
        );
        verify(documentRhRepository).updatePdfPath(
                15,
                "documents/document-15-test.pdf"
        );
    }

    @Test
    void retourneLePdfDejaEnregistreSansLeRegenerer() throws Exception {
        Document document = new Document(
                15,
                "DOC-2026-00015",
                3,
                42,
                "Chef du personnel",
                "Demande de congé",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                "documents/document-15-test.pdf",
                null,
                null,
                Map.of()
        );
        byte[] pdf = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(documentRhRepository.findDocumentById(15))
                .thenReturn(Optional.of(document));
        when(pdfDocumentStorage.lire(document.fichierPath()))
                .thenReturn(Optional.of(pdf));

        assertEquals(Optional.of(pdf), service.obtenirPdf(15));
        verify(pdfDocumentGenerator, never()).generer(any(PdfDocumentContent.class));
    }

    @Test
    void retourneVideSiLeFichierPdfAssocieEstAbsent() throws Exception {
        Document document = new Document(
                15,
                "DOC-2026-00015",
                3,
                42,
                "Chef du personnel",
                "Demande de congé",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                "documents/document-15-test.pdf",
                null,
                null,
                Map.of()
        );
        when(documentRhRepository.findDocumentById(15))
                .thenReturn(Optional.of(document));
        when(pdfDocumentStorage.lire(document.fichierPath()))
                .thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.obtenirPdf(15));
    }

    private Document documentSansPdf() {
        return new Document(
                15,
                "DOC-2026-00015",
                3,
                42,
                "Chef du personnel",
                "Demande de congé",
                LocalDate.of(2026, 10, 7),
                null,
                "BROUILLON",
                null,
                null,
                null,
                Map.of(
                        "dateDebut",
                        "2026-10-10",
                        "dateFin",
                        "2026-10-12"
                )
        );
    }

    private void preparerCreationType(String code) {
        when(documentRhRepository.findTypeDocumentById(3))
                .thenReturn(Optional.of(new TypeDocument(
                        3,
                        code,
                        code,
                        null,
                        null,
                        null
                )));
        when(documentRhRepository.existsEmploye(42L)).thenReturn(true);
        when(documentRhRepository.countDocuments()).thenReturn(0L);
        when(documentRhRepository.insertDocument(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
