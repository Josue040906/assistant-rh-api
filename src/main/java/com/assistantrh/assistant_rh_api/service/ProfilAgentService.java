package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.dto.ProfilAgentPersonnelDto;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProfilAgentService {

    private final EmployeRepository employeRepository;
    private final SituationCarriereRepository situationCarriereRepository;
    private final UtilisateurRepository utilisateurRepository;

    public ProfilAgentService(
            EmployeRepository employeRepository,
            SituationCarriereRepository situationCarriereRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.employeRepository = employeRepository;
        this.situationCarriereRepository = situationCarriereRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    public Optional<Map<String, Object>> obtenirProfilAgent(String requete) {

        if (requete == null || requete.isBlank()) {
            return Optional.empty();
        }

        return employeRepository.findProfile(requete.trim());
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