package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.dto.ProfilAgentPersonnelDto;
import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProfilAgentService {

    private final EmployeRepository employeRepository;
    private final SituationCarriereRepository situationCarriereRepository;
    private final DiplomeRepository diplomeRepository;
    private final SituationCarriereService situationCarriereService;
    private final UtilisateurRepository utilisateurRepository;

    public ProfilAgentService(
            EmployeRepository employeRepository,
            SituationCarriereRepository situationCarriereRepository,
            DiplomeRepository diplomeRepository,
            SituationCarriereService situationCarriereService,
            UtilisateurRepository utilisateurRepository
    ) {
        this.employeRepository = employeRepository;
        this.situationCarriereRepository = situationCarriereRepository;
        this.diplomeRepository = diplomeRepository;
        this.situationCarriereService = situationCarriereService;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Retourne le profil détaillé d'un agent identifié par matricule ou nom.
     * Les données complémentaires sont chargées à partir de l'identifiant
     * employe_id, puis assemblées sans modifier les clés déjà exposées par l'API.
     */
    public Optional<Map<String, Object>> obtenirProfilAgent(String requete) {
        if (requete == null || requete.isBlank()) {
            return Optional.empty();
        }

        List<Map<String, Object>> candidats =
                employeRepository.findProfileCandidates(requete.trim());
        if (candidats.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> meilleur = candidats.get(0);
        int rang = integerValue(meilleur.get("match_rank")) == null
                ? 4 : integerValue(meilleur.get("match_rank"));

        // Seuls le matricule exact et le nom complet exact permettent
        // d'ouvrir directement un profil. Les correspondances partielles ou
        // approximatives doivent être confirmées pour éviter une erreur d'identité.
        if (rang > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Correspondance approximative : utilisez d'abord la recherche d'agents par critères pour afficher et comparer les candidats, puis confirmez l'identité avec "
                            + "le matricule ou le nom complet exact."
            );
        }

        long candidatsDeMemeRang = candidats.stream()
                .filter(candidat -> {
                    Integer candidatRang = integerValue(candidat.get("match_rank"));
                    return candidatRang != null && candidatRang == rang;
                })
                .count();
        if (candidatsDeMemeRang > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Plusieurs agents correspondent à cette identité. "
                            + "Précisez le matricule ou un autre élément distinctif."
            );
        }

        Map<String, Object> profil = new LinkedHashMap<>(meilleur);
        profil.remove("match_rank");
        profil.remove("match_score");
        Integer employeId = integerValue(profil.get("id"));
        if (employeId == null || employeId <= 0) {
            return Optional.of(profil);
        }

        // findById contient les informations détaillées déjà utilisées ailleurs.
        Map<String, Object> details = employeRepository.findById(employeId)
                .orElse(Map.of());

        Map<String, Object> informationsPersonnelles = new LinkedHashMap<>();
        informationsPersonnelles.put("sexe", details.get("sexe"));
        informationsPersonnelles.put("adresse", details.get("adresse"));
        informationsPersonnelles.put("telephone", details.get("telephone"));
        informationsPersonnelles.put("dateNaissance", details.get("date_naissance"));
        informationsPersonnelles.put("lieuNaissance", details.get("lieu_naissance"));
        informationsPersonnelles.put("localiteResidenceId", details.get("localite_residence_id"));
        informationsPersonnelles.put("localiteResidenceNom", details.get("localite_residence_nom"));
        informationsPersonnelles.put("localiteResidenceNiveau", details.get("localite_residence_niveau"));
        informationsPersonnelles.put("lieuTravail", details.get("lieu_travail"));
        profil.put("informationsPersonnelles", informationsPersonnelles);

        Map<String, Object> situationProfessionnelle = new LinkedHashMap<>();
        situationProfessionnelle.put("dateEmbauche", details.get("date_embauche"));
        situationProfessionnelle.put("typeEmploi", details.get("type_emploi"));
        situationProfessionnelle.put("categorie", details.get("categorie"));
        situationProfessionnelle.put("corps", details.get("corps"));
        situationProfessionnelle.put("corpsLibelle", details.get("corps_libelle"));
        situationProfessionnelle.put("classe", details.get("classe_libelle"));
        situationProfessionnelle.put("classeOrdre", details.get("classe_ordre"));
        situationProfessionnelle.put("echelon", details.get("echelon_ordre"));
        situationProfessionnelle.put("dateDebutSituation", details.get("carriere_date_debut"));
        situationProfessionnelle.put("poste", details.get("poste"));
        situationProfessionnelle.put("service", details.get("service"));
        situationProfessionnelle.put("direction", details.get("direction"));
        situationProfessionnelle.put("dateDebutAffectation", details.get("affectation_date_debut"));
        profil.put("situationProfessionnelle", situationProfessionnelle);

        profil.put(
                "historiqueCarriere",
                situationCarriereRepository.findHistorique(employeId)
                        .stream()
                        .map(this::formatCareerStep)
                        .toList()
        );
        profil.put("diplomes", diplomeRepository.findByEmployeId(employeId));

        // Cette analyse existante calcule l'ancienneté, la durée requise,
        // l'éligibilité indicative et l'évolution selon les règles du backend.
        situationCarriereService.analyserEvolutionCarriere(employeId)
                .ifPresent(analyse -> profil.put("evolutionCarriere", analyse));

        return Optional.of(profil);
    }

    public ProfilAgentPersonnelDto obtenirMonProfilAgent(Integer utilisateurId) {
        if (utilisateurId == null || utilisateurId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Identité utilisateur invalide."
            );
        }
        if (!utilisateurRepository.estAgentSpersActif(utilisateurId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Un compte agent SPERS actif est requis."
            );
        }

        Map<String, Object> agent = employeRepository.findByUserId(utilisateurId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucun profil agent n’est associé à ce compte."
                ));
        Integer employeId = integerValue(agent.get("id"));
        List<ProfilAgentPersonnelDto.EtapeCarriere> parcours =
                situationCarriereRepository.findHistorique(employeId).stream()
                        .map(this::careerStep)
                        .toList();

        return new ProfilAgentPersonnelDto(
                textValue(agent.get("matricule")),
                textValue(agent.get("nom")),
                textValue(agent.get("prenom")),
                textValue(agent.get("email")),
                dateValue(agent.get("date_embauche")),
                textValue(agent.get("type_emploi")),
                textValue(agent.get("categorie")),
                textValue(agent.get("corps_libelle")),
                textValue(agent.get("poste")),
                textValue(agent.get("service")),
                textValue(agent.get("direction")),
                parcours
        );
    }

    private Map<String, Object> formatCareerStep(Map<String, Object> situation) {
        Map<String, Object> etape = new LinkedHashMap<>();
        etape.put("id", situation.get("id"));
        etape.put("dateDebut", situation.get("date_debut"));
        etape.put("dateFin", situation.get("date_fin"));
        etape.put("categorie", situation.get("categorie_code"));
        etape.put("classe", situation.get("classe_libelle"));
        etape.put("classeOrdre", situation.get("classe_ordre"));
        etape.put("echelon", situation.get("echelon_ordre"));
        etape.put("dureeMinAnnees", situation.get("echelon_duree_min"));
        return etape;
    }

    private ProfilAgentPersonnelDto.EtapeCarriere careerStep(
            Map<String, Object> situation
    ) {
        return new ProfilAgentPersonnelDto.EtapeCarriere(
                dateValue(situation.get("date_debut")),
                dateValue(situation.get("date_fin")),
                textValue(situation.get("categorie_code")),
                textValue(situation.get("classe_libelle")),
                integerValue(situation.get("echelon_ordre"))
        );
    }

    private String textValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private LocalDate dateValue(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        return null;
    }
}
