package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.pdf.CvAgentPdfGenerator;
import com.assistantrh.assistant_rh_api.pdf.PhotoAgentStorage;
import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * Le CV d'un agent en PDF, d'après son dossier : identité, situation, parcours,
 * carrière, diplômes, formations, compétences, langues. Le Service du Personnel
 * l'obtient pour tout agent ; un agent, pour lui-même.
 */
@Service
public class CvAgentService {

    private final EmployeRepository employeRepository;
    private final AffectationRepository affectationRepository;
    private final SituationCarriereRepository situationRepository;
    private final DiplomeRepository diplomeRepository;
    private final ProfilComplementaireService profilComplementaireService;
    private final UtilisateurRepository utilisateurRepository;
    private final PhotoAgentStorage photoStorage;
    private final CvAgentPdfGenerator generateur;

    public CvAgentService(EmployeRepository employeRepository, AffectationRepository affectationRepository,
                          SituationCarriereRepository situationRepository, DiplomeRepository diplomeRepository,
                          ProfilComplementaireService profilComplementaireService,
                          UtilisateurRepository utilisateurRepository, PhotoAgentStorage photoStorage,
                          CvAgentPdfGenerator generateur) {
        this.employeRepository = employeRepository;
        this.affectationRepository = affectationRepository;
        this.situationRepository = situationRepository;
        this.diplomeRepository = diplomeRepository;
        this.profilComplementaireService = profilComplementaireService;
        this.utilisateurRepository = utilisateurRepository;
        this.photoStorage = photoStorage;
        this.generateur = generateur;
    }

    /** Le nom du fichier et le PDF du CV. */
    public Map.Entry<String, byte[]> generer(Integer employeId, Integer acteurId) {
        Map<String, Object> agent = employeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException("L'agent demandé n'existe pas."));
        boolean lui = acteurId != null && Objects.equals(toInteger(agent.get("user_id")), acteurId);
        if (!lui && (acteurId == null || !utilisateurRepository.estAgentSpersActif(acteurId))) {
            throw new IllegalArgumentException("Seul le Service du Personnel, ou l'agent lui-même, obtient ce CV.");
        }
        byte[] pdf = generateur.generer(new CvAgentPdfGenerator.CvAgent(
                agent,
                affectationRepository.findByEmployeId(employeId),
                situationRepository.findHistorique(employeId),
                diplomeRepository.findByEmployeId(employeId),
                profilComplementaireService.lire(employeId),
                photo(agent.get("photo"))));
        String nomFichier = ("CV-" + agent.get("matricule") + "-" + agent.get("nom"))
                .replaceAll("[^A-Za-z0-9-]+", "_") + ".pdf";
        return Map.entry(nomFichier, pdf);
    }

    /** La photo enregistrée par l'application (lien /uploads/employes/…), sinon rien. */
    private byte[] photo(Object lien) {
        if (lien == null || !lien.toString().startsWith(PhotoAgentStorage.PREFIXE_LIEN)) {
            return null;
        }
        try {
            return photoStorage.lire(lien.toString().substring(PhotoAgentStorage.PREFIXE_LIEN.length())).orElse(null);
        } catch (RuntimeException illisible) {
            return null;
        }
    }

    private static Integer toInteger(Object valeur) {
        return valeur instanceof Number nombre ? nombre.intValue() : null;
    }
}
