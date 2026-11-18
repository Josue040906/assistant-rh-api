-- Données d'essai pour la Gestion des postes (2026-11-18, accord de l'utilisateur) :
-- exigences de quatre postes, langues / compétences / formations de quelques agents,
-- et deux anciens postes (« a déjà occupé ce poste »).
-- Sauvegarde avant ajout : D:\Projets\sauvegardes\assistant_rh-avant-donnees-exigences-20261118.dump
-- Rejouable : chaque ajout vérifie qu'il n'existe pas déjà.
BEGIN;

-- ---------------------------------------------------------------- exigences des postes
-- 32 Juriste (Service Juridique) · 1 Chef de service (Administration Générale)
-- 5 Chef de service (Service du Personnel, Lova part en 2031) · 6 Chef de bureau (Service du Personnel, libre)
INSERT INTO poste_exigence (poste_id, type, valeur, niveau, indispensable, ordre)
SELECT v.poste_id, v.type, v.valeur, v.niveau, v.indispensable, v.ordre
FROM (VALUES
    (32, 'DIPLOME', 'Droit', 'LICENCE', TRUE, 0),
    (32, 'COMPETENCE', 'Rédaction juridique', NULL, FALSE, 1),
    (32, 'LANGUE', 'Français', 'COURANT', FALSE, 2),
    (1, 'DIPLOME', NULL, 'MASTER', TRUE, 0),
    (1, 'COMPETENCE', 'Management d''équipe', NULL, FALSE, 1),
    (1, 'LANGUE', 'Anglais', 'INTERMEDIAIRE', FALSE, 2),
    (5, 'DIPLOME', NULL, 'MASTER', TRUE, 0),
    (5, 'COMPETENCE', 'Gestion des carrières', NULL, TRUE, 1),
    (5, 'FORMATION', 'Droit de la fonction publique', NULL, FALSE, 2),
    (5, 'LANGUE', 'Anglais', 'INTERMEDIAIRE', FALSE, 3),
    (6, 'DIPLOME', NULL, 'LICENCE', TRUE, 0),
    (6, 'COMPETENCE', 'Gestion des dossiers du personnel', NULL, FALSE, 1)
) AS v(poste_id, type, valeur, niveau, indispensable, ordre)
WHERE NOT EXISTS (SELECT 1 FROM poste_exigence x
                  WHERE x.poste_id = v.poste_id AND x.type = v.type
                    AND x.valeur IS NOT DISTINCT FROM v.valeur AND x.niveau IS NOT DISTINCT FROM v.niveau);

-- ---------------------------------------------------------------- langues
INSERT INTO employe_langue (employe_id, langue, niveau)
SELECT v.employe_id, v.langue, v.niveau
FROM (VALUES
    (1, 'Malagasy', 'MATERNELLE'), (1, 'Français', 'COURANT'), (1, 'Anglais', 'INTERMEDIAIRE'),
    (2, 'Malagasy', 'MATERNELLE'), (2, 'Français', 'COURANT'), (2, 'Anglais', 'COURANT'),
    (4, 'Malagasy', 'MATERNELLE'), (4, 'Français', 'COURANT'), (4, 'Anglais', 'INTERMEDIAIRE'),
    (5, 'Malagasy', 'MATERNELLE'), (5, 'Français', 'COURANT'), (5, 'Anglais', 'NOTIONS'),
    (6, 'Malagasy', 'MATERNELLE'), (6, 'Français', 'COURANT'), (6, 'Anglais', 'COURANT'),
    (7, 'Malagasy', 'MATERNELLE'), (7, 'Français', 'INTERMEDIAIRE'),
    (8, 'Malagasy', 'MATERNELLE'), (8, 'Français', 'COURANT'),
    (11, 'Malagasy', 'MATERNELLE'), (11, 'Français', 'COURANT'), (11, 'Anglais', 'COURANT'),
    (23, 'Malagasy', 'MATERNELLE'), (23, 'Français', 'COURANT'), (23, 'Anglais', 'INTERMEDIAIRE'),
    (24, 'Malagasy', 'MATERNELLE'), (24, 'Français', 'COURANT'),
    (108, 'Malagasy', 'MATERNELLE'), (108, 'Français', 'INTERMEDIAIRE')
) AS v(employe_id, langue, niveau)
WHERE NOT EXISTS (SELECT 1 FROM employe_langue l WHERE l.employe_id = v.employe_id AND l.langue = v.langue);

-- ---------------------------------------------------------------- compétences
INSERT INTO employe_competence (employe_id, libelle)
SELECT v.employe_id, v.libelle
FROM (VALUES
    (1, 'Management d''équipe'), (1, 'Gestion des carrières'),
    (2, 'Management d''équipe'), (2, 'Gestion du courrier'),
    (4, 'Gestion des carrières'), (4, 'Management d''équipe'), (4, 'Gestion des dossiers du personnel'),
    (5, 'Rédaction juridique'), (5, 'Gestion des dossiers du personnel'),
    (6, 'Gestion des carrières'), (6, 'Systèmes d''information RH'),
    (7, 'Gestion des dossiers du personnel'), (7, 'Gestion des carrières'),
    (8, 'Gestion des dossiers du personnel'),
    (11, 'Rédaction juridique'), (11, 'Ingénierie de formation'),
    (23, 'Management d''équipe'), (23, 'Rédaction juridique'),
    (24, 'Rédaction juridique'), (24, 'Contentieux administratif'),
    (108, 'Rédaction juridique')
) AS v(employe_id, libelle)
WHERE NOT EXISTS (SELECT 1 FROM employe_competence c WHERE c.employe_id = v.employe_id AND c.libelle = v.libelle);

-- ---------------------------------------------------------------- formations continues
INSERT INTO employe_formation (employe_id, intitule, organisme, annee, duree_jours)
SELECT v.employe_id, v.intitule, v.organisme, v.annee, v.duree_jours
FROM (VALUES
    (1, 'Management public', 'ENAM', 2019, 10),
    (2, 'Management des équipes', 'ENAM', 2022, 5),
    (5, 'Droit de la fonction publique', 'ENAM', 2021, 5),
    (6, 'Systèmes d''information RH', 'CNFPT', 2024, 3),
    (7, 'Droit de la fonction publique', 'ENAM', 2019, 5),
    (8, 'Gestion des carrières', 'CNFPT', 2023, 4)
) AS v(employe_id, intitule, organisme, annee, duree_jours)
WHERE NOT EXISTS (SELECT 1 FROM employe_formation f WHERE f.employe_id = v.employe_id AND f.intitule = v.intitule);

-- ---------------------------------------------------------------- anciens postes
-- Andry RAKOTONDRABE (7) : Chef de bureau du Service du Personnel (poste 6) de 2017 à 2020,
-- puis Gestionnaire de dossiers depuis le 01/01/2021.
INSERT INTO affectation (employe_id, poste_id, service_id, date_debut, date_fin, observation)
SELECT 7, 6, (SELECT service_id FROM poste WHERE id = 6), DATE '2017-09-11', DATE '2020-12-31', 'Données d''essai'
WHERE NOT EXISTS (SELECT 1 FROM affectation WHERE employe_id = 7 AND poste_id = 6);
UPDATE affectation SET date_debut = DATE '2021-01-01'
WHERE employe_id = 7 AND date_fin IS NULL AND date_debut = DATE '2017-09-11';

-- Noro RAZAFIMAHATRATRA (11) : Juriste du Service Juridique (poste 32) de 2016 à 2019,
-- puis Chargée de formation depuis le 01/01/2020.
INSERT INTO affectation (employe_id, poste_id, service_id, date_debut, date_fin, observation)
SELECT 11, 32, (SELECT service_id FROM poste WHERE id = 32), DATE '2016-06-20', DATE '2019-12-31', 'Données d''essai'
WHERE NOT EXISTS (SELECT 1 FROM affectation WHERE employe_id = 11 AND poste_id = 32);
UPDATE affectation SET date_debut = DATE '2020-01-01'
WHERE employe_id = 11 AND date_fin IS NULL AND date_debut = DATE '2016-06-20';

COMMIT;

-- Contrôle
SELECT 'exigences' AS quoi, COUNT(*) FROM poste_exigence
UNION ALL SELECT 'langues', COUNT(*) FROM employe_langue
UNION ALL SELECT 'compétences', COUNT(*) FROM employe_competence
UNION ALL SELECT 'formations', COUNT(*) FROM employe_formation
UNION ALL SELECT 'anciens postes', COUNT(*) FROM affectation WHERE observation = 'Données d''essai';
