package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.LocaliteRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
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

    private final SituationCarriereRepository situationCarriereRepository;
    private final EmployeRepository employeRepository;
    private final AffectationRepository affectationRepository;
    private final ActiviteService activiteService;
    private final UtilisateurRepository utilisateurRepository;
    private final LocaliteService localiteService;
    private final TelephoneService telephoneService;
    private final VilleService villeService;
    private final NotificationRepository notificationRepository;

    public EmployeService(
            SituationCarriereRepository situationCarriereRepository,
            EmployeRepository employeRepository,
            AffectationRepository affectationRepository,
            ActiviteService activiteService,
            UtilisateurRepository utilisateurRepository,
            LocaliteService localiteService,
            TelephoneService telephoneService,
            VilleService villeService,
            NotificationRepository notificationRepository
    ) {
        this.situationCarriereRepository = situationCarriereRepository;
        this.employeRepository = employeRepository;
        this.affectationRepository = affectationRepository;
        this.activiteService = activiteService;
        this.utilisateurRepository = utilisateurRepository;
        this.localiteService = localiteService;
        this.telephoneService = telephoneService;
        this.villeService = villeService;
        this.notificationRepository = notificationRepository;
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
        // Une seule identité exacte, jamais un homonyme choisi au hasard.
        List<Map<String, Object>> matches =
                employeRepository.findExactProfileMatches(query);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
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
            Long localiteResidenceId,
            Integer echelonId
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
                dateEmbauche,
                typeEmploiId
        );
        localiteService.validerLocaliteResidence(localiteResidenceId);
        validerGradeDeDepart(typeEmploiId, categorieId, echelonId);
        affectationRepository.verifierPlaceLibre(posteId, null);

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
        if (echelonId != null && situationCarriereRepository.creerSituation(
                employeId, categorieId, echelonId, sqlDateEmbauche) != 1) {
            throw new IllegalStateException(
                    "La situation de carrière initiale de l'agent n'a pas pu être créée."
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
        notificationRepository.notifierNouvelAgent(employeId, acteurId);

        return employeId;
    }

    /**
     * Un agent qui a un type d'emploi commence au grade choisi dans le parcours
     * de ce type ; sa catégorie (diplôme) est alors indispensable : avec le grade,
     * elle donne l'indice.
     */
    private void validerGradeDeDepart(Integer typeEmploiId, Integer categorieId, Integer echelonId) {
        if (typeEmploiId == null && echelonId == null) {
            return;
        }
        if (typeEmploiId == null) {
            throw new IllegalArgumentException("Choisissez le type d'emploi avant le grade de départ.");
        }
        if (echelonId == null) {
            throw new IllegalArgumentException(
                    "Choisissez le grade de départ de l'agent dans le parcours de son type d'emploi.");
        }
        if (categorieId == null) {
            throw new IllegalArgumentException(
                    "La catégorie est obligatoire avec un grade : ensemble, ils donnent l'indice.");
        }
        Map<String, Object> grade = situationCarriereRepository.findEchelon(echelonId)
                .orElseThrow(() -> new IllegalArgumentException("Le grade de départ choisi n'existe pas."));
        Object typeDuGrade = grade.get("type_emploi_id");
        if (!(typeDuGrade instanceof Number nombre) || nombre.intValue() != typeEmploiId) {
            throw new IllegalArgumentException(
                    "Le grade " + grade.get("grade_code") + " n'appartient pas au parcours de ce type d'emploi.");
        }
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
        if (dateNaissance != null) {
            // La nouvelle date de naissance face à l'embauche déjà enregistrée.
            employeRepository.findById(employeId).ifPresent(enregistre -> {
                Object embauche = enregistre.get("date_embauche");
                Object type = enregistre.get("type_emploi_id");
                validerAgeALEmbauche(dateNaissance,
                        embauche instanceof java.sql.Date date ? date.toLocalDate()
                                : embauche instanceof LocalDate date ? date : null,
                        type instanceof Number nombre ? nombre.intValue() : null);
            });
        }
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
            LocalDate dateEmbauche,
            Integer typeEmploiId
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
        validerAgeALEmbauche(dateNaissance, dateEmbauche, typeEmploiId);
    }

    /**
     * À sa date d'embauche, l'agent a au moins l'âge minimum de son type
     * d'emploi (donnée) : il ne peut donc pas être mineur une fois embauché.
     */
    private void validerAgeALEmbauche(LocalDate dateNaissance, LocalDate dateEmbauche, Integer typeEmploiId) {
        if (dateNaissance == null || dateEmbauche == null) {
            return;
        }
        employeRepository.ageMinimumEmbauche(typeEmploiId).ifPresent(age -> {
            if (dateEmbauche.isBefore(dateNaissance.plusYears(age))) {
                java.time.format.DateTimeFormatter jour = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                throw new IllegalArgumentException(
                        "L'agent doit avoir au moins " + age + " ans à son embauche : avec une naissance le "
                                + dateNaissance.format(jour) + ", l'embauche ne peut pas précéder le "
                                + dateNaissance.plusYears(age).format(jour)
                                + " (embauche indiquée : " + dateEmbauche.format(jour) + ")."
                );
            }
        });
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