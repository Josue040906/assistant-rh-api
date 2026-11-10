package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import com.assistantrh.assistant_rh_api.model.ProgressionCarriere;
import com.assistantrh.assistant_rh_api.model.SituationCarriereSnapshot;
import com.assistantrh.assistant_rh_api.model.TypeDocument;
import com.assistantrh.assistant_rh_api.pdf.DemandeAvancementPdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.DemandeCongePdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.DemandeMutationPdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.DemandeRetraitePdfTemplate;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentContent;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentGenerator;
import com.assistantrh.assistant_rh_api.pdf.PdfDocumentStorage;
import com.assistantrh.assistant_rh_api.repository.AgentDocumentRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.VilleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentRhServiceTests {

    private final DocumentRhRepository documentRhRepository =
            mock(DocumentRhRepository.class);
    private final AgentDocumentRepository agentDocumentRepository =
            mock(AgentDocumentRepository.class);
    private final SituationCarriereService situationCarriereService =
            mock(SituationCarriereService.class);
    private final ActiviteService activiteService =
            mock(ActiviteService.class);
    private final PdfDocumentGenerator pdfDocumentGenerator =
            mock(PdfDocumentGenerator.class);
    private final DemandeCongePdfTemplate demandeCongePdfTemplate =
            mock(DemandeCongePdfTemplate.class);
    private final DemandeAvancementPdfTemplate demandeAvancementPdfTemplate =
            mock(DemandeAvancementPdfTemplate.class);
    private final DemandeRetraitePdfTemplate demandeRetraitePdfTemplate =
            mock(DemandeRetraitePdfTemplate.class);
    private final DemandeMutationPdfTemplate demandeMutationPdfTemplate =
            mock(DemandeMutationPdfTemplate.class);
    private final PdfDocumentStorage pdfDocumentStorage =
            mock(PdfDocumentStorage.class);
    private final VilleRepository villeRepository = mock(VilleRepository.class);
    private final DocumentRhService service = new DocumentRhService(
            documentRhRepository,
            agentDocumentRepository,
            situationCarriereService,
            activiteService,
            pdfDocumentGenerator,
            demandeCongePdfTemplate,
            demandeAvancementPdfTemplate,
            demandeRetraitePdfTemplate,
            demandeMutationPdfTemplate,
            pdfDocumentStorage,
            new VilleService(villeRepository),
            mock(com.assistantrh.assistant_rh_api.pdf.DemandeTitularisationPdfTemplate.class),
            mock(TitularisationService.class)
    );

    @Test
    void filtreLesDocumentsParStatutSansDistinguerLaCasse() {
        when(documentRhRepository.findAll()).thenReturn(List.of(
                Map.of("id", 1, "statut", "CREE"),
                Map.of("id", 2, "statut", "APPLIQUE"),
                Map.of("id", 3, "statut", "CREE")
        ));

        assertEquals(List.of(1, 3), service.getDocumentsAuStatut(" cree ").stream()
                .map(document -> document.get("id")).toList());
        assertEquals(3, service.getDocumentsAuStatut(null).size());
        assertEquals(0, service.getDocumentsAuStatut("ARCHIVE").size());
    }

    @Test
    void filtreLesDocumentsParAgentTypeEtPeriodeDeCreation() {
        when(documentRhRepository.findAll()).thenReturn(List.of(
                Map.of("id", 1, "statut", "CREE", "employe_id", 2, "type_document_code", "CONGE",
                        "date_document", java.sql.Date.valueOf("2026-11-03")),
                Map.of("id", 2, "statut", "CREE", "employe_id", 2, "type_document_code", "AVANCEMENT",
                        "date_document", java.sql.Date.valueOf("2026-11-04")),
                Map.of("id", 3, "statut", "CREE", "employe_id", 5, "type_document_code", "CONGE",
                        "date_document", java.sql.Date.valueOf("2026-10-01"))
        ));

        assertEquals(List.of(1), service.getDocuments(null, 2, "conge",
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)).stream().map(d -> d.get("id")).toList());
        assertEquals(List.of(1, 2), service.getDocuments(null, 2, null, null, null).stream()
                .map(d -> d.get("id")).toList());
    }

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
        assertEquals("CREE", document.statut());
        assertEquals(donnees, document.donnees());
        verify(documentRhRepository).insertDocument(any(Document.class));
        verify(activiteService).enregistrer(
                null,
                42,
                "CREATION",
                "Création du document " + document.referenceDocument()
                        + " (" + document.objet() + ") pour l'agent 42"
        );
    }

    @Test
    void creeUneDemandeMutationAvecSnapshotsEtReferencesValides() {
        when(villeRepository.findName("Toamasina"))
                .thenReturn(Optional.of("Toamasina"));
        TypeDocument type = new TypeDocument(
                9,
                "MUTATION",
                "Demande de mutation",
                null,
                null,
                null
        );
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "Rakoto",
                "Jean",
                "AG-042",
                "Service actuel",
                "Direction actuelle",
                "Poste actuel",
                null,
                null,
                null,
                null,
                null,
                "Antananarivo"
        );
        when(documentRhRepository.findTypeDocumentById(9))
                .thenReturn(Optional.of(type));
        when(documentRhRepository.existsEmploye(42L)).thenReturn(true);
        when(documentRhRepository.findMutationDirection(2L))
                .thenReturn(Optional.of(Map.of("id", 2, "nom", "Direction cible")));
        when(documentRhRepository.findMutationService(5L))
                .thenReturn(Optional.of(Map.of(
                        "id", 5,
                        "nom", "Service cible",
                        "direction_id", 2,
                        "direction", "Direction cible"
                )));
        when(documentRhRepository.findMutationPoste(8L))
                .thenReturn(Optional.of(Map.of(
                        "id", 8,
                        "intitule", "Poste cible",
                        "service_id", 5,
                        "service", "Service cible",
                        "direction_id", 2,
                        "direction", "Direction cible"
                )));
        when(agentDocumentRepository.findDocumentInfoById(42))
                .thenReturn(Optional.of(agent));
        when(documentRhRepository.countDocuments()).thenReturn(0L);
        when(documentRhRepository.insertDocument(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Document document = service.creerDocumentMetier(
                9,
                42,
                "Chef du personnel",
                Map.of(
                        "directionSouhaiteeId", 2,
                        "serviceSouhaiteId", 5,
                        "posteSouhaiteId", 8,
                        "lieuTravailSouhaite", "Toamasina",
                        "dateEffetSouhaitee", LocalDate.now().toString(),
                        "motif", "Rapprochement familial"
                )
        );

        assertEquals(LocalDate.now(), document.dateEffet());
        assertEquals(
                "Direction actuelle",
                ((Map<?, ?>) document.donnees().get("situationActuelle"))
                        .get("direction")
        );
        assertEquals(
                "Service cible",
                ((Map<?, ?>) document.donnees().get("situationDemandee"))
                        .get("service")
        );
        assertEquals(
                "Rapprochement familial",
                document.donnees().get("motif")
        );
        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void refuseUneMutationSansDestinationOuMotif() {
        TypeDocument type = new TypeDocument(
                9,
                "MUTATION",
                "Demande de mutation",
                null,
                null,
                null
        );
        when(documentRhRepository.findTypeDocumentById(9))
                .thenReturn(Optional.of(type));
        when(documentRhRepository.existsEmploye(42L)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        9,
                        42,
                        "Chef du personnel",
                        Map.of("motif", "Demande")
                )
        );
        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void accepteUneMutationDeLieuSansModifierLesDonneesDeSituationActuelle() {
        preparerMutation();
        when(villeRepository.findName("Fianarantsoa"))
                .thenReturn(Optional.of("Fianarantsoa"));

        Document document = service.creerDocumentMetier(
                9,
                42,
                "Chef du personnel",
                Map.of(
                        "lieuTravailSouhaite", "Fianarantsoa",
                        "motif", "Rapprochement familial", "dateEffetSouhaitee", LocalDate.now().plusMonths(1).toString()
                )
        );

        Map<?, ?> situationDemandee =
                (Map<?, ?>) document.donnees().get("situationDemandee");
        Map<?, ?> situationActuelle =
                (Map<?, ?>) document.donnees().get("situationActuelle");
        assertEquals("Fianarantsoa", situationDemandee.get("lieuTravail"));
        assertEquals("Service actuel", situationActuelle.get("service"));
        assertEquals("Lieu actuel", situationActuelle.get("lieuTravail"));
        assertEquals(LocalDate.now().plusMonths(1), document.dateEffet());
        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void accepteUnChangementDeServiceOuDePosteSeul() {
        preparerMutation();
        when(documentRhRepository.findMutationService(5L))
                .thenReturn(Optional.of(Map.of(
                        "id", 5,
                        "nom", "Service cible",
                        "direction_id", 2,
                        "direction", "Direction cible"
                )));
        when(documentRhRepository.findMutationPoste(8L))
                .thenReturn(Optional.of(Map.of(
                        "id", 8,
                        "intitule", "Poste cible",
                        "service_id", 5,
                        "service", "Service cible",
                        "direction_id", 2,
                        "direction", "Direction cible"
                )));

        Document demandeService = service.creerDocumentMetier(
                9,
                42,
                "Chef du personnel",
                Map.of(
                        "serviceSouhaiteId", 5,
                        "motif", "Changement de service", "dateEffetSouhaitee", LocalDate.now().plusMonths(1).toString()
                )
        );
        Document demandePoste = service.creerDocumentMetier(
                9,
                42,
                "Chef du personnel",
                Map.of(
                        "posteSouhaiteId", 8,
                        "motif", "Changement de poste", "dateEffetSouhaitee", LocalDate.now().plusMonths(1).toString()
                )
        );

        assertEquals(
                "Service cible",
                ((Map<?, ?>) demandeService.donnees().get("situationDemandee"))
                        .get("service")
        );
        assertEquals(
                "Poste cible",
                ((Map<?, ?>) demandePoste.donnees().get("situationDemandee"))
                        .get("poste")
        );
        verify(documentRhRepository, org.mockito.Mockito.times(2))
                .insertDocument(any(Document.class));
    }

    @Test
    void refuseUneDateMutationInvalideEtUneReferenceDeDestinationInexistante() {
        preparerMutation();
        when(villeRepository.findName("Fianarantsoa"))
                .thenReturn(Optional.of("Fianarantsoa"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        9,
                        42,
                        "Chef du personnel",
                        Map.of(
                                "lieuTravailSouhaite", "Fianarantsoa",
                                "dateEffetSouhaitee", "31/12/2027",
                                "motif", "Demande"
                        )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        9,
                        42,
                        "Chef du personnel",
                        Map.of(
                                "serviceSouhaiteId", 999,
                                "motif", "Demande"
                        )
                )
        );

        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void unCongePeutEtrePlanifieDansLeFutur() {
        preparerCreationType("CONGE");

        Document document = service.creerDocumentMetier(
                3,
                42,
                "Directeur des Ressources Humaines",
                Map.of(
                        "dateDebut", LocalDate.now().plusMonths(1).toString(),
                        "dateFin", LocalDate.now().plusMonths(1).plusDays(14).toString()
                )
        );

        assertEquals("CREE", document.statut());
        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void lApercuValideCommeLaCreationEtResumeEnClairSansRienEnregistrer() {
        preparerCreationType("CONGE");
        when(agentDocumentRepository.findDocumentInfoById(42)).thenReturn(Optional.of(new AgentDocumentInfo(
                "BEFANAMBINANA", "Hasina", "AG-028", null, null, null, null, null, null, null, null)));

        Map<String, Object> resume = service.apercuDocumentMetier(
                3,
                42,
                " Directeur des Ressources Humaines ",
                Map.of("dateDebut", "2026-12-01", "dateFin", "2026-12-15", "motif", "Congé annuel")
        );

        assertEquals("CONGE", resume.get("Document"));
        assertEquals("BEFANAMBINANA Hasina (AG-028)", resume.get("Agent"));
        assertEquals("Directeur des Ressources Humaines", resume.get("Destinataire"));
        assertEquals("01/12/2026", resume.get("Du"));
        assertEquals("15/12/2026", resume.get("Au"));
        assertEquals("Congé annuel", resume.get("Motif"));
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(resume.get("Statut")).startsWith("Créée, applicable"));
        verify(documentRhRepository, never()).insertDocument(any(Document.class));
        verify(documentRhRepository, never()).countDocuments();

        assertThrows(IllegalArgumentException.class, () -> service.apercuDocumentMetier(
                3, 42, "DRH", Map.of("dateDebut", "2026-12-15", "dateFin", "2026-12-01")));
    }

    @Test
    void lesConstatsSurLaPieceSontGardesAvecLeBrouillonSansValiderQuoiQueCeSoit() {
        preparerCreationType("CONGE");
        Map<String, Object> donnees = Map.of("dateDebut", "2026-12-01", "dateFin", "2026-12-15");
        String constats = "Tampon visible, signature visible. Le nom écrit diffère du dossier : « Hasina B. ».";

        Document document = service.creerDocumentMetier(3, 42, "DRH", donnees, "  " + constats + "  ");
        Map<String, Object> resume = service.apercuDocumentMetier(3, 42, "DRH", donnees, constats);

        assertEquals("CREE", document.statut());
        assertEquals(constats, document.donnees().get("constatsPiece"));
        assertEquals(constats, resume.get("Constats sur la pièce"));
        assertEquals(null, service.creerDocumentMetier(3, 42, "DRH", donnees).donnees().get("constatsPiece"));
        assertThrows(IllegalArgumentException.class,
                () -> service.creerDocumentMetier(3, 42, "DRH", donnees, "x".repeat(2001)));
    }

    @Test
    void lApercuPdfRendLeModeleDuTypeSansRienEnregistrer() {
        preparerCreationType("CONGE");
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "BEFANAMBINANA", "Hasina", "AG-028", null, null, null, null, null, null, null, null);
        when(agentDocumentRepository.findDocumentInfoById(42)).thenReturn(Optional.of(agent));
        com.assistantrh.assistant_rh_api.pdf.PdfDocumentContent contenu =
                mock(com.assistantrh.assistant_rh_api.pdf.PdfDocumentContent.class);
        org.mockito.ArgumentCaptor<Document> apercu = org.mockito.ArgumentCaptor.forClass(Document.class);
        when(demandeCongePdfTemplate.creer(apercu.capture(), org.mockito.ArgumentMatchers.eq(agent))).thenReturn(contenu);
        when(pdfDocumentGenerator.generer(contenu)).thenReturn(new byte[]{'%', 'P', 'D', 'F'});

        byte[] pdf = service.apercuPdfDocumentMetier(3, 42, "DRH",
                Map.of("dateDebut", "2026-12-01", "dateFin", "2026-12-15"));

        assertEquals(4, pdf.length);
        assertEquals("APERÇU – non enregistré", apercu.getValue().referenceDocument());
        assertEquals("CREE", apercu.getValue().statut());
        verify(documentRhRepository, never()).insertDocument(any(Document.class));
        verify(documentRhRepository, never()).countDocuments();
        verifyNoInteractions(activiteService);
    }

    @Test
    void laDemandeGardeSonAuteurQuiSeulPourraLaValider() {
        preparerCreationType("CONGE");
        // Comme la base : l'insertion attribue un identifiant au document.
        when(documentRhRepository.insertDocument(any(Document.class))).thenAnswer(invocation -> {
            Document d = invocation.getArgument(0);
            return new Document(9, d.referenceDocument(), d.typeDocumentId(), d.employeId(), d.destinataire(),
                    d.objet(), d.dateDocument(), d.dateEffet(), d.statut(), d.fichierPath(), d.observation(),
                    d.dateCreation(), d.donnees());
        });

        Document document = service.creerDocumentMetier(3, 42, "DRH",
                Map.of("dateDebut", "2026-12-01", "dateFin", "2026-12-05"), null, 5);

        verify(documentRhRepository).enregistrerAuteur(9, 5);
        verify(activiteService).enregistrer(org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.eq(42),
                org.mockito.ArgumentMatchers.eq("CREATION"), any());
    }

    @Test
    void refuseUnCongeQuiFinitAvantDeCommencer() {
        preparerCreationType("CONGE");

        IllegalArgumentException refus = assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Directeur des Ressources Humaines",
                        Map.of(
                                "dateDebut", LocalDate.now().plusDays(10).toString(),
                                "dateFin", LocalDate.now().plusDays(9).toString()
                        )
                )
        );
        assertEquals("La date de fin du congé ne peut pas précéder sa date de début.", refus.getMessage());
        verify(documentRhRepository, never()).insertDocument(any(Document.class));
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
    void memoriseLesSituationsCalculeesPourUneDemandeAvancementEligible() {
        preparerCreationType("AVANCEMENT");
        preparerProgression(true, true);

        Document document = service.creerDocumentMetier(
                3,
                42,
                "Chef du Service du Personnel",
                Map.of("motif", "Ancienneté requise atteinte", "dateEffetSouhaitee", LocalDate.now().toString())
        );

        assertEquals("1re classe", ((Map<?, ?>) document.donnees()
                .get("situationActuelle")).get("classeLibelle"));
        assertEquals(2, ((Map<?, ?>) document.donnees()
                .get("situationDemandee")).get("echelonOrdre"));
        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void refuseUnAvancementQuandLAgentNestPasEligible() {
        preparerCreationType("AVANCEMENT");
        preparerProgression(true, false);

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
    void refuseUnAvancementQuandLEligibiliteNestPasDeterminee() {
        preparerCreationType("AVANCEMENT");
        preparerProgression(false, null);

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
    void refuseUnAvancementSansSituationSuivante() {
        preparerCreationType("AVANCEMENT");
        when(situationCarriereService.obtenirProgressionCarriere(42))
                .thenReturn(Optional.of(new ProgressionCarriere(
                        new SituationCarriereSnapshot(
                                15,
                                3,
                                4,
                                "Exceptionnelle",
                                5,
                                null,
                                LocalDate.now().minusYears(5)
                        ),
                        null,
                        true,
                        true,
                        "AUCUNE"
                )));

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
    void refuseLesSituationsDeCarriereFourniesParLeClient() {
        preparerCreationType("AVANCEMENT");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of(
                                "situationDemandee",
                                Map.of("classeId", 999, "echelonId", 999)
                        )
                )
        );

        verify(situationCarriereService, never())
                .obtenirProgressionCarriere(42);
        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void creeUneDemandeRetraiteAvecUniquementSesDonneesSpecifiques() {
        preparerCreationType("RETRAITE");
        Map<String, Object> donnees = Map.of(
                "dateDepartSouhaitee",
                LocalDate.now().toString(),
                "motif",
                "Fin de carrière",
                "observation",
                "Transmission au service compétent"
        );

        Document document = service.creerDocumentMetier(
                3,
                42,
                "Chef du Service du Personnel",
                donnees
        );

        assertEquals(donnees, document.donnees());
        assertEquals("RETRAITE", document.objet());
        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void refuseUneDemandeRetraiteSansDateSouhaitee() {
        preparerCreationType("RETRAITE");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of("motif", "Fin de carrière")
                )
        );

        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void uneMutationPeutViserUneDateDEffetFuture() {
        preparerMutation();
        when(villeRepository.findName("Fianarantsoa"))
                .thenReturn(Optional.of("Fianarantsoa"));
        String dateEffet = LocalDate.now().plusMonths(3).toString();

        Document document = service.creerDocumentMetier(
                9,
                42,
                "Chef du personnel",
                Map.of(
                        "lieuTravailSouhaite", "Fianarantsoa",
                        "dateEffetSouhaitee", dateEffet,
                        "motif", "Rapprochement familial"
                )
        );

        assertEquals(LocalDate.parse(dateEffet), document.dateEffet());
        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void unDepartALaRetraiteSouhaiteEstAVenir() {
        preparerCreationType("RETRAITE");

        service.creerDocumentMetier(
                3,
                42,
                "Chef du Service du Personnel",
                Map.of("dateDepartSouhaitee", LocalDate.now().plusYears(1).toString())
        );

        verify(documentRhRepository).insertDocument(any(Document.class));
    }

    @Test
    void refuseUneDateDeDepartInvalide() {
        preparerCreationType("RETRAITE");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of("dateDepartSouhaitee", "07/10/2027")
                )
        );

        verify(documentRhRepository, never()).insertDocument(any(Document.class));
    }

    @Test
    void refuseLesChampsQuiNeFontPasPartieDeLaDemandeRetraite() {
        preparerCreationType("RETRAITE");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.creerDocumentMetier(
                        3,
                        42,
                        "Chef du Service du Personnel",
                        Map.of(
                                "dateDepartSouhaitee",
                                LocalDate.now().plusYears(1).toString(),
                                "nom",
                                "Autre agent"
                        )
                )
        );

        verify(documentRhRepository, never()).insertDocument(any(Document.class));
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
                "Rakoto",
                "Jean",
                "AG-042",
                "Service RH",
                "Direction RH",
                "Gestionnaire",
                null,
                null,
                null,
                null,
                null
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
        verify(activiteService).enregistrer(
                null,
                42,
                "GENERATION",
                "Génération du PDF du document DOC-2026-00015"
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
                "CREE",
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
                "CREE",
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
                "CREE",
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

    @Test
    void lAvancementSeJugeALaDateDEffetDemandee() {
        preparerCreationType("AVANCEMENT");
        preparerProgression(true, false);

        // Durée minimale de 2 ans commencée il y a 2 ans : possible dès aujourd'hui, pas avant.
        Document document = service.creerDocumentMetier(3, 42, "Chef du Service du Personnel",
                Map.of("dateEffetSouhaitee", LocalDate.now().plusMonths(2).toString()));
        assertEquals(LocalDate.now().plusMonths(2), document.dateEffet());
        assertEquals("CREE", document.statut());
    }

    @Test
    void uneMutationNePeutPasPrendreEffetDansLePasse() {
        preparerMutation();
        when(villeRepository.findName("Fianarantsoa")).thenReturn(Optional.of("Fianarantsoa"));

        IllegalArgumentException refus = assertThrows(IllegalArgumentException.class,
                () -> service.creerDocumentMetier(9, 42, "Chef du personnel", Map.of(
                        "lieuTravailSouhaite", "Fianarantsoa", "motif", "Rapprochement",
                        "dateEffetSouhaitee", LocalDate.now().minusDays(1).toString())));
        assertEquals("La date d'effet de la mutation ne peut pas être passée.", refus.getMessage());
    }

    @Test
    void uneRetraiteNeDevientApplicableQuApresLaDureeDeServiceExigee() {
        preparerCreationType("RETRAITE");
        LocalDate possible = LocalDate.now().plusYears(3);
        when(documentRhRepository.dateRetraitePossible(42)).thenReturn(Optional.of(possible));

        Document document = service.creerDocumentMetier(3, 42, "Chef du Service du Personnel",
                Map.of("dateDepartSouhaitee", LocalDate.now().plusMonths(1).toString()));

        assertEquals(possible, document.dateEffet());
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
        when(documentRhRepository.dateRetraitePossible(42))
                .thenReturn(Optional.of(LocalDate.now().minusYears(1)));
        when(documentRhRepository.countDocuments()).thenReturn(0L);
        when(documentRhRepository.insertDocument(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void preparerMutation() {
        when(documentRhRepository.findTypeDocumentById(9))
                .thenReturn(Optional.of(new TypeDocument(
                        9,
                        "MUTATION",
                        "Demande de mutation",
                        null,
                        null,
                        null
                )));
        when(documentRhRepository.existsEmploye(42L)).thenReturn(true);
        when(agentDocumentRepository.findDocumentInfoById(42))
                .thenReturn(Optional.of(new AgentDocumentInfo(
                        "Rakoto",
                        "Jean",
                        "AG-042",
                        "Service actuel",
                        "Direction actuelle",
                        "Poste actuel",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Lieu actuel"
                )));
        when(documentRhRepository.countDocuments()).thenReturn(0L);
        when(documentRhRepository.insertDocument(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void preparerProgression(
            boolean eligibiliteDeterminee,
            Boolean eligible
    ) {
        when(situationCarriereService.obtenirProgressionCarriere(42))
                .thenReturn(Optional.of(new ProgressionCarriere(
                        new SituationCarriereSnapshot(
                                4,
                                1,
                                2,
                                "1re classe",
                                3,
                                2,
                                LocalDate.now().minusYears(2)
                        ),
                        new SituationCarriereSnapshot(
                                5,
                                2,
                                2,
                                "1re classe",
                                3,
                                3,
                                null
                        ),
                        eligibiliteDeterminee,
                        eligible,
                        "ECHELON"
                )));
    }
}
