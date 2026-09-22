package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SituationCarriereService {

    private final SituationCarriereRepository situationCarriereRepository;

    public SituationCarriereService(
            SituationCarriereRepository situationCarriereRepository
    ) {
        this.situationCarriereRepository = situationCarriereRepository;
    }

    public Optional<Map<String, Object>> obtenirSituationActuelle(
            Integer employeId
    ) {
        if (employeId == null) {
            return Optional.empty();
        }

        return situationCarriereRepository
                .findSituationActuelle(employeId);
    }
    public List<Map<String, Object>> obtenirHistorique(
            Integer employeId
    ) {
        if (employeId == null) {
            return List.of();
        }

        return situationCarriereRepository.findHistorique(employeId);
    }
    public Optional<Map<String, Object>> obtenirEchelonSuivant(
            Integer classeId,
            Integer ordreActuel
    ) {
        if (classeId == null || ordreActuel == null) {
            return Optional.empty();
        }

        return situationCarriereRepository.findEchelonSuivant(
                classeId,
                ordreActuel
        );
    }
    public Optional<Map<String, Object>> obtenirDonneesAnalyseActuelle(
            Integer employeId
    ) {
        return situationCarriereRepository.findDonneesAnalyseActuelle(employeId);
    }

    public Optional<Map<String, Object>> obtenirClasseSuivante(
            Integer gradeCarriereId,
            Integer ordreActuel
    ) {
        if (gradeCarriereId == null || ordreActuel == null) {
            return Optional.empty();
        }

        return situationCarriereRepository.findClasseSuivante(
                gradeCarriereId,
                ordreActuel
        );
    }
}