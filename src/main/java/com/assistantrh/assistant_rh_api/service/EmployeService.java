        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final NotificationService notificationService;

    public EmployeService(
            EmployeRepository employeRepository,
            NotificationService notificationService
    ) {
        this.employeRepository = employeRepository;
        this.notificationService = notificationService;
    }

    public List<Map<String, Object>> getAllEmployes() {
        return employeRepository.findAll();
    }

    public Optional<Map<String, Object>> getEmployeById(Integer id) {
        return employeRepository.findById(id);
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

    public Integer creerEmploye(
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
            Integer gradeId,
            String lieuTravail,
            String photo,
            Integer userId
    ) {

        validerEmploye(
                matricule,
                nom,
                prenom,
                posteId,
                serviceId
        );

        Date sqlDateNaissance =
                convertirDate(dateNaissance);

        Date sqlDateEmbauche =
                convertirDate(dateEmbauche);

        Integer employeId =
                employeRepository.create(
                        matricule.trim(),
                        nom.trim(),
                        prenom.trim(),
                        normaliserTexte(sexe),
                        normaliserTexte(adresse),
                        normaliserTexte(cin),
                        normaliserTexte(telephone),
                        sqlDateNaissance,
                        normaliserTexte(lieuNaissance),
                        sqlDateEmbauche,
                        posteId,
                        serviceId,
                        typeEmploiId,
                        categorieId,
                        gradeId,
                        normaliserTexte(lieuTravail),
                        normaliserTexte(photo),
                        userId
                );

        /*
         * L'agent a été créé avec succès.
         * On informe tous les utilisateurs.
         */
        notificationService.notifierNouvelAgent(
                prenom,
                nom
        );

        return employeId;
    }

    public void modifierEmploye(
            Integer id,
            String nom,
            String prenom,
            String sexe,
            String adresse,
            String cin,
            String telephone,
            LocalDate dateNaissance,
            String lieuNaissance,
            LocalDate dateEmbauche,
            String lieuTravail,
            String photo
    ) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }

        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom est obligatoire."
            );
        }

        if (prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException(
                    "Le prénom est obligatoire."
            );
        }

        if (nom.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Le nom ne peut pas dépasser 100 caractères."
            );
        }

        if (prenom.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Le prénom ne peut pas dépasser 100 caractères."
            );
        }

        Date sqlDateNaissance =
                convertirDate(dateNaissance);

        Date sqlDateEmbauche =
                convertirDate(dateEmbauche);

        int lignesModifiees =
                employeRepository.update(
                        id,
                        nom.trim(),
                        prenom.trim(),
                        normaliserTexte(sexe),
                        normaliserTexte(adresse),
                        normaliserTexte(cin),
                        normaliserTexte(telephone),
                        sqlDateNaissance,
                        normaliserTexte(lieuNaissance),
                        sqlDateEmbauche,
                        normaliserTexte(lieuTravail),
                        normaliserTexte(photo)
                );

        if (lignesModifiees == 0) {
            throw new IllegalArgumentException(
                    "L'agent demandé n'existe pas."
            );
        }
    }

    public String enregistrerPhoto(
            Integer id,
            MultipartFile file
    ) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucune photo n'a été sélectionnée."
            );
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "La photo ne doit pas dépasser 5 Mo."
            );
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !(
                        contentType.equalsIgnoreCase("image/jpeg") ||
                                contentType.equalsIgnoreCase("image/png") ||
                                contentType.equalsIgnoreCase("image/webp")
                )
        ) {
            throw new IllegalArgumentException(
                    "Format de photo non autorisé. Utilisez JPG, PNG ou WEBP."
            );
        }

        String extension;

        switch (contentType.toLowerCase()) {
            case "image/jpeg" -> extension = ".jpg";
            case "image/png" -> extension = ".png";
            case "image/webp" -> extension = ".webp";
            default -> throw new IllegalArgumentException(
                    "Format de photo non autorisé."
            );
        }

        String filename =
                "agent-" + id + "-" + UUID.randomUUID() + extension;

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
            Integer posteId,
            Integer serviceId
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

        if (nom.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Le nom ne peut pas dépasser 100 caractères."
            );
        }

        if (prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException(
                    "Le prénom est obligatoire."
            );
        }

        if (prenom.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Le prénom ne peut pas dépasser 100 caractères."
            );
        }

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
    }

    private String normaliserTexte(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private Date convertirDate(LocalDate date) {

        return date != null
                ? Date.valueOf(date)
                : null;
    }
}
