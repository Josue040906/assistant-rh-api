package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.LocaliteRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final AffectationRepository affectationRepository;
    private final ActiviteService activiteService;
    private final UtilisateurRepository utilisateurRepository;
    private final LocaliteService localiteService;
    private final TelephoneService telephoneService;
    private final VilleService villeService;

    public EmployeService(
            EmployeRepository employeRepository,
            AffectationRepository affectationRepository,
            ActiviteService activiteService,
            UtilisateurRepository utilisateurRepository,
            LocaliteService localiteService,
            TelephoneService telephoneService,
            VilleService villeService
    ) {
        this.employeRepository = employeRepository;
        this.affectationRepository = affectationRepository;
        this.activiteService = activiteService;
        this.utilisateurRepository = utilisateurRepository;
        this.localiteService = localiteService;
        this.telephoneService = telephoneService;
        this.villeService = villeService;
    }

    public List<Map<String, Object>> getAllEmployes() {
        return employeRepository.findAll();
    }

    public Optional<Map<String, Object>> getEmployeById(Integer id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        return employeRepository.findById(id);
    }

    public Optional<Map<String, Object>> getEmployeByUserId(
            Integer userId
    ) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }

        return employeRepository.findByUserId(userId);
    }

    public Optional<Map<String, Object>> getProfilByUserId(
            Integer userId
    ) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }

        return employeRepository.findProfilByUserId(userId);
    }

    public Optional<Map<String, Object>> rechercherProfilEmploye(
            String query
    ) {
        return employeRepository.findProfile(query);
    }

    public List<Map<String, Object>> rechercherEmployes(
            String query
    ) {
        return employeRepository.search(query);
    }

    @Transactional
    public Integer creerEmploye(
            Integer acteurId,
            String matricule,
            String nom,
            String prenom,
            String sexe,
            String adresse,
            String cin,
            String telephone,
            LocalDate dateNaissance,
            String lieuNaissance,
            LocalDate dateEmbauche,
            Integer posteId,
            Integer serviceId,
            Integer typeEmploiId,
            Integer categorieId,
            String lieuTravail,
            String photo,
            Integer userId,
            Long localiteResidenceId
    ) {

        if (acteurId == null || acteurId <= 0
                || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel."
            );
        }

        validerEmploye(
                matricule,
                nom,
                prenom,
                cin,
                posteId,
                serviceId,
                dateNaissance,
                dateEmbauche
        );
        localiteService.validerLocaliteResidence(localiteResidenceId);

        String nomNormalise = validerNom(nom, "Le nom", true);
        String prenomNormalise = validerNom(prenom, "Le prénom", false);
        String cinNormalise = validerCin(cin);
        String lieuNaissanceNormalise = validerTexteOptionnel(
                villeService.validerVille(lieuNaissance, "Le lieu de naissance"),
                "Le lieu de naissance",
                150
        );
        String lieuTravailNormalise = validerTexteOptionnel(
                villeService.validerVille(lieuTravail, "Le lieu de travail"),
                "Le lieu de travail",
                255
        );
        Date sqlDateNaissance =
                convertirDate(dateNaissance);

        Date sqlDateEmbauche =
                convertirDate(dateEmbauche);

        Integer employeId =
                employeRepository.create(
                        matricule.trim(),
                        nomNormalise,
                        prenomNormalise,
                        normaliserTexte(sexe),
                        normaliserTexte(adresse),
                        cinNormalise,
                        telephoneService.normaliser(telephone),
                        sqlDateNaissance,
                        lieuNaissanceNormalise,
                        sqlDateEmbauche,
                        typeEmploiId,
                        categorieId,
                        lieuTravailNormalise,
                        normaliserTexte(photo),
                        userId,
                        localiteResidenceId
                );

        int affectationCreee =
                affectationRepository.creer(
                        employeId,
                        posteId,
                        serviceId,
                        sqlDateEmbauche,
                        null,
                        null
                );

        if (affectationCreee != 1) {
            throw new IllegalStateException(
                    "L'affectation initiale de l'agent n'a pas pu être créée."
            );
        }

        activiteService.enregistrer(
                acteurId,
                employeId,
                "CREATION_AGENT",
                "Création de l'agent "
                        + matricule.trim()
                        + " - "
                        + (prenomNormalise == null ? "" : prenomNormalise + " ")
                        + nomNormalise
        );

        return employeId;
    }

    @Transactional
    public void modifierProfil(
            Integer userId,
            String nom,
            String prenom,
            String sexe,
            String cin,
            LocalDate dateNaissance,
            String lieuNaissance,
            String adresse,
            String telephone,
            Long localiteResidenceId,
            boolean effacerLocaliteResidence
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "L'utilisateur est invalide."
            );
        }

        Optional<Map<String, Object>> employe =
                employeRepository.findByUserId(userId);

        if (employe.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucun agent associé à cet utilisateur."
            );
        }

        Map<String, Object> agent = employe.get();

        Integer employeId =
                ((Number) agent.get("id")).intValue();

        modifierDonneesPersonnelles(
                employeId,
                userId,
                nom,
                prenom,
                sexe,
                cin,
                telephone,
                dateNaissance,
                lieuNaissance,
                adresse,
                localiteResidenceId,
                effacerLocaliteResidence,
                "MODIFICATION_PROFIL",
                "Modification des informations personnelles de l'agent "
                        + agent.get("matricule")
        );
    }

    @Transactional
    public void modifierEmploye(
            Integer id,
            Integer acteurId,
            String nom,
            String prenom,
            String sexe,
            String adresse,
            String cin,
            String telephone,
            LocalDate dateNaissance,
            String lieuNaissance,
            Long localiteResidenceId,
            boolean effacerLocaliteResidence
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }

        if (acteurId == null || acteurId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'acteur est obligatoire."
            );
        }

        if (!utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel."
            );
        }

        Optional<Map<String, Object>> employe =
                employeRepository.findById(id);
        if (employe.isEmpty()) {
            throw new IllegalArgumentException(
                    "L'agent demandé n'existe pas."
            );
        }

        Map<String, Object> agent = employe.get();
        modifierDonneesPersonnelles(
                id,
                acteurId,
                nom,
                prenom,
                sexe,
                cin,
                telephone,
                dateNaissance,
                lieuNaissance,
                adresse,
                localiteResidenceId,
                effacerLocaliteResidence,
                "MODIFICATION_AGENT",
                "Modification des informations personnelles de l'agent "
                        + agent.get("matricule")
        );
    }

    private void modifierDonneesPersonnelles(
            Integer employeId,
            Integer acteurId,
            String nom,
            String prenom,
            String sexe,
            String cin,
            String telephone,
            LocalDate dateNaissance,
            String lieuNaissance,
            String adresse,
            Long localiteResidenceId,
            boolean effacerLocaliteResidence,
            String typeAction,
            String description
    ) {
        String nomNormalise = validerNom(nom, "Le nom", true);
        String prenomNormalise = validerNom(prenom, "Le prénom", false);
        String sexeNormalise = validerTexteOptionnel(sexe, "Le sexe", 20);
        String adresseNormalisee = validerTexteOptionnel(adresse, "L'adresse", 255);
        String cinNormalise = validerCin(cin);
        String telephoneNormalise = telephoneService.normaliser(telephone);
        String lieuNaissanceNormalise = validerTexteOptionnel(
                villeService.validerVille(lieuNaissance, "Le lieu de naissance"),
                "Le lieu de naissance",
                150
        );
        validerDateNaissance(dateNaissance, false);
        if (effacerLocaliteResidence && localiteResidenceId != null) {
            throw new LocaliteInvalideException(
                    "Fournissez une localité ou demandez sa suppression, pas les deux."
            );
        }
        localiteService.validerLocaliteResidence(localiteResidenceId);

        if (cinNormalise != null
                && employeRepository.existsByCinAndIdNot(cinNormalise, employeId)) {
            throw new CinDejaUtiliseException();
        }

        int lignesModifiees;
        try {
            lignesModifiees = employeRepository.update(
                    employeId,
                    nomNormalise,
                    prenomNormalise,
                    sexeNormalise,
                    adresseNormalisee,
                    cinNormalise,
                    telephoneNormalise,
                    convertirDate(dateNaissance),
                    lieuNaissanceNormalise,
                    localiteResidenceId,
                    effacerLocaliteResidence
            );
        } catch (DuplicateKeyException e) {
            throw new CinDejaUtiliseException();
        }

        if (lignesModifiees == 0) {
            throw new IllegalArgumentException(
                    "L'agent demandé n'existe pas."
            );
        }

        activiteService.enregistrer(
                acteurId,
                employeId,
                typeAction,
                description
        );
    }

    public String enregistrerPhoto(
            Integer id,
            Integer acteurId,
            MultipartFile file
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }

        if (acteurId == null || acteurId <= 0
                || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel."
            );
        }

        Map<String, Object> agent = employeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'agent demandé n'existe pas."
                ));

        return enregistrerPhoto(
                id,
                acteurId,
                file,
                "TELEVERSEMENT_PHOTO",
                "Téléversement de la photo de l'agent " + agent.get("matricule")
        );
    }

    public String enregistrerPhotoProfil(
            Integer userId,
            MultipartFile file
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "L'utilisateur est invalide."
            );
        }

        Map<String, Object> agent = employeRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun agent associé à cet utilisateur."
                ));

        return enregistrerPhoto(
                ((Number) agent.get("id")).intValue(),
                userId,
                file,
                "TELEVERSEMENT_PHOTO",
                "Téléversement de la photo de l'agent " + agent.get("matricule")
        );
    }

    private String enregistrerPhoto(
            Integer id,
            Integer acteurId,
            MultipartFile file,
            String typeAction,
            String description
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucune photo n'a été sélectionnée."
            );
        }

        String originalFilename =
                file.getOriginalFilename();

        String extension = "";

        if (originalFilename != null) {
            int index =
                    originalFilename.lastIndexOf('.');

            if (index >= 0) {
                extension =
                        originalFilename.substring(index);
            }
        }

        String filename =
                "agent-"
                        + id
                        + "-"
                        + UUID.randomUUID()
                        + extension;

        Path uploadDirectory =
                Paths.get("uploads", "employes");

        try {

            Files.createDirectories(uploadDirectory);

            Path destination =
                    uploadDirectory.resolve(filename);

            Files.write(
                    destination,
                    file.getBytes()
            );

            String photoPath =
                    "/uploads/employes/" + filename;

            int lignesModifiees =
                    employeRepository.updatePhoto(
                            id,
                            photoPath
                    );

            if (lignesModifiees == 0) {

                Files.deleteIfExists(destination);

                throw new IllegalArgumentException(
                        "L'agent demandé n'existe pas."
                );
            }

            activiteService.enregistrer(
                    acteurId,
                    id,
                    typeAction,
                    description
            );

            return photoPath;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Impossible d'enregistrer la photo.",
                    e
            );
        }
    }

    private void validerEmploye(
            String matricule,
            String nom,
            String prenom,
            String cin,
            Integer posteId,
            Integer serviceId,
            LocalDate dateNaissance,
            LocalDate dateEmbauche
    ) {

        if (matricule == null || matricule.isBlank()) {
            throw new IllegalArgumentException(
                    "Le matricule est obligatoire."
            );
        }

        if (matricule.trim().length() > 30) {
            throw new IllegalArgumentException(
                    "Le matricule ne peut pas dépasser 30 caractères."
            );
        }

        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom est obligatoire."
            );
        }

        validerNom(nom, "Le nom", true);
        validerNom(prenom, "Le prénom", false);
        validerCin(cin);

        if (posteId == null || posteId <= 0) {
            throw new IllegalArgumentException(
                    "Le poste est obligatoire."
            );
        }

        if (serviceId == null || serviceId <= 0) {
            throw new IllegalArgumentException(
                    "Le service est obligatoire."
            );
        }

        if (dateEmbauche == null) {
            throw new IllegalArgumentException(
                    "La date d'embauche est obligatoire."
            );
        }
        validerDateNaissance(dateNaissance, true);
        if (dateEmbauche.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La date d'embauche ne peut pas être future."
            );
        }
        if (dateEmbauche.isBefore(dateNaissance.plusYears(18))) {
            throw new IllegalArgumentException(
                    "La date d'embauche doit être au moins 18 ans après la date de naissance."
            );
        }
    }

    private void validerDateNaissance(LocalDate dateNaissance, boolean obligatoire) {
        if (dateNaissance == null) {
            if (obligatoire) {
                throw new IllegalArgumentException(
                        "La date de naissance est obligatoire."
                );
            }
            return;
        }
        if (dateNaissance.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La date de naissance ne peut pas être future."
            );
        }
        if (dateNaissance.isAfter(LocalDate.now().minusYears(18))) {
            throw new IllegalArgumentException(
                    "L'agent doit avoir au moins 18 ans."
            );
        }
    }

    private String validerNom(String value, String label, boolean obligatoire) {
        String normalise = normaliserTexte(value);
        if (normalise == null) {
            if (obligatoire) {
                throw new IllegalArgumentException(label + " est obligatoire.");
            }
            return null;
        }
        if (normalise.length() > 100) {
            throw new IllegalArgumentException(
                    label + " ne peut pas dépasser 100 caractères."
            );
        }
        long nombreLettres = normalise.codePoints()
                .filter(Character::isLetter)
                .count();
        if (nombreLettres < 2) {
            throw new IllegalArgumentException(
                    label + " doit contenir au moins deux lettres."
            );
        }
        if (normalise.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    label + " contient des caractères non autorisés."
            );
        }
        return normalise;
    }

    private String validerCin(String cin) {
        String normalise = normaliserTexte(cin);
        if (normalise != null && !normalise.matches("[0-9]{12}")) {
            throw new IllegalArgumentException(
                    "Le CIN doit contenir exactement 12 chiffres."
            );
        }
        return normalise;
    }

    private Date convertirDate(LocalDate date) {
        if (date == null) {
            return null;
        }

        return Date.valueOf(date);
    }

    private String validerTexteOptionnel(
            String value,
            String libelle,
            int longueurMaximale
    ) {
        String normalise = normaliserTexte(value);
        if (normalise != null && normalise.length() > longueurMaximale) {
            throw new IllegalArgumentException(
                    libelle + " ne peut pas dépasser "
                            + longueurMaximale + " caractères."
            );
        }
        return normalise;
    }

    private String normaliserTexte(String value) {

        if (value == null) {
            return null;
        }

        String normalise =
                value.trim();

        return normalise.isEmpty()
                ? null
                : normalise;
    }
}