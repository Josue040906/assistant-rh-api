-- ============================================================
-- REFONTE DE LA BASE DE DONNEES
-- Projet : Assistant RH - MEF
-- ============================================================

BEGIN;

-- ============================================================
-- 1. SUPPRESSION DES ANCIENNES TABLES
-- ============================================================

DROP TABLE IF EXISTS notification CASCADE;
DROP TABLE IF EXISTS tache CASCADE;
DROP TABLE IF EXISTS employe CASCADE;
DROP TABLE IF EXISTS poste CASCADE;
DROP TABLE IF EXISTS service CASCADE;

DROP TABLE IF EXISTS grade CASCADE;
DROP TABLE IF EXISTS categorie CASCADE;
DROP TABLE IF EXISTS type_emploi CASCADE;
DROP TABLE IF EXISTS utilisateur CASCADE;
DROP TABLE IF EXISTS direction CASCADE;

-- Anciennes tables liées aux compétences
DROP TABLE IF EXISTS poste_competence CASCADE;
DROP TABLE IF EXISTS employe_competence CASCADE;
DROP TABLE IF EXISTS domaine_competence_relation CASCADE;
DROP TABLE IF EXISTS domaine_competence CASCADE;
DROP TABLE IF EXISTS competence CASCADE;


-- ============================================================
-- 2. TABLE DIRECTION
-- ============================================================

CREATE TABLE direction (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    description TEXT
);


-- ============================================================
-- 3. TABLE UTILISATEUR
-- ============================================================

CREATE TABLE utilisateur (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255)
);


-- ============================================================
-- 4. TABLE TYPE_EMPLOI
-- ============================================================

CREATE TABLE type_emploi (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom VARCHAR(100) NOT NULL UNIQUE
);


-- ============================================================
-- 5. TABLE CATEGORIE
-- ============================================================

CREATE TABLE categorie (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    diplome VARCHAR(200)
);


-- ============================================================
-- 6. TABLE GRADE
-- ============================================================

CREATE TABLE grade (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code_grade VARCHAR(50) NOT NULL UNIQUE,
    type_emploi_id INTEGER,

    CONSTRAINT fk_grade_type_emploi
        FOREIGN KEY (type_emploi_id)
        REFERENCES type_emploi(id)
);


-- ============================================================
-- 7. TABLE SERVICE
-- ============================================================

CREATE TABLE service (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    nom VARCHAR(200) NOT NULL,
    description TEXT,

    direction_id INTEGER,

    CONSTRAINT fk_service_direction
        FOREIGN KEY (direction_id)
        REFERENCES direction(id)
);


-- ============================================================
-- 8. TABLE POSTE
-- ============================================================

CREATE TABLE poste (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    intitule VARCHAR(150) NOT NULL,
    description TEXT,
    service_id INTEGER NOT NULL,

    CONSTRAINT fk_poste_service
        FOREIGN KEY (service_id)
        REFERENCES service(id)
);


-- ============================================================
-- 9. TABLE EMPLOYE
-- ============================================================

CREATE TABLE employe (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id INTEGER UNIQUE,

    matricule VARCHAR(30) NOT NULL UNIQUE,

    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,

    sexe VARCHAR(20),

    adresse VARCHAR(255),
    cin VARCHAR(30) UNIQUE,
    telephone VARCHAR(30),

    date_naissance DATE,
    lieu_naissance VARCHAR(150),
    date_embauche DATE,

    poste_id INTEGER NOT NULL,
    service_id INTEGER NOT NULL,

    type_emploi_id INTEGER,
    categorie_id INTEGER,
    grade_id INTEGER,

    lieu_travail VARCHAR(200),
    photo VARCHAR(500),

    CONSTRAINT fk_employe_utilisateur
        FOREIGN KEY (user_id)
        REFERENCES utilisateur(id),

    CONSTRAINT fk_employe_poste
        FOREIGN KEY (poste_id)
        REFERENCES poste(id),

    CONSTRAINT fk_employe_service
        FOREIGN KEY (service_id)
        REFERENCES service(id),

    CONSTRAINT fk_employe_type_emploi
        FOREIGN KEY (type_emploi_id)
        REFERENCES type_emploi(id),

    CONSTRAINT fk_employe_categorie
        FOREIGN KEY (categorie_id)
        REFERENCES categorie(id),

    CONSTRAINT fk_employe_grade
        FOREIGN KEY (grade_id)
        REFERENCES grade(id)
);


-- ============================================================
-- 10. TABLE TACHE
-- ============================================================

CREATE TABLE tache (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id INTEGER NOT NULL,
    employe_id INTEGER,

    titre VARCHAR(200) NOT NULL,
    description TEXT,

    statut VARCHAR(30) NOT NULL DEFAULT 'A_FAIRE',
    priorite VARCHAR(30) NOT NULL DEFAULT 'NORMALE',

    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_echeance TIMESTAMP,

    CONSTRAINT fk_tache_utilisateur
        FOREIGN KEY (user_id)
        REFERENCES utilisateur(id),

    CONSTRAINT fk_tache_employe
        FOREIGN KEY (employe_id)
        REFERENCES employe(id),

    CONSTRAINT chk_tache_statut
        CHECK (
            statut IN (
                'A_FAIRE',
                'EN_COURS',
                'TERMINEE',
                'ANNULEE'
            )
        ),

    CONSTRAINT chk_tache_priorite
        CHECK (
            priorite IN (
                'FAIBLE',
                'NORMALE',
                'HAUTE',
                'URGENTE'
            )
        )
);


-- ============================================================
-- 11. TABLE NOTIFICATION
-- ============================================================

CREATE TABLE notification (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id INTEGER NOT NULL,

    titre VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,

    lu BOOLEAN NOT NULL DEFAULT FALSE,

    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_utilisateur
        FOREIGN KEY (user_id)
        REFERENCES utilisateur(id)
);


-- ============================================================
-- 12. DIRECTIONS
-- ============================================================
--
-- Données de démonstration.
-- Les rattachements devront être validés avec la documentation
-- administrative réelle du MEF.
-- ============================================================

INSERT INTO direction (nom, description) VALUES
(
    'Direction Générale du Budget et des Finances',
    'Direction de démonstration représentant un niveau organisationnel supérieur.'
),
(
    'Direction du Budget',
    'Gestion et suivi des activités liées au budget.'
),
(
    'Direction du Patrimoine de l''Etat',
    'Gestion administrative du patrimoine de l''Etat.'
),
(
    'Direction de la Gestion des Effectifs des Agents de l''Etat',
    'Gestion administrative des effectifs des agents de l''Etat.'
),
(
    'Direction de la Solde et des Pensions',
    'Gestion de la solde et des pensions des agents de l''Etat.'
);


-- ============================================================
-- 13. TYPES D'EMPLOI
-- ============================================================

INSERT INTO type_emploi (nom) VALUES
('Fonctionnaire'),
('Agent contractuel'),
('Agent temporaire'),
('Personnel d''appui');


-- ============================================================
-- 14. CATEGORIES
-- ============================================================
--
-- Les diplômes sont volontairement laissés génériques.
-- La correspondance officielle catégorie <-> diplôme devra
-- être renseignée à partir des documents RH réels.
-- ============================================================

INSERT INTO categorie (code, diplome) VALUES
('I',   'À renseigner selon la nomenclature officielle'),
('II',  'À renseigner selon la nomenclature officielle'),
('III', 'À renseigner selon la nomenclature officielle'),
('IV',  'À renseigner selon la nomenclature officielle'),
('V',   'À renseigner selon la nomenclature officielle'),
('VI',  'À renseigner selon la nomenclature officielle'),
('VII', 'À renseigner selon la nomenclature officielle'),
('VIII','À renseigner selon la nomenclature officielle'),
('IX',  'À renseigner selon la nomenclature officielle'),
('X',   'À renseigner selon la nomenclature officielle');


-- ============================================================
-- 15. GRADES
-- ============================================================
--
-- Codes de démonstration uniquement.
-- ============================================================

INSERT INTO grade (code_grade, type_emploi_id) VALUES
('GRADE-01', 1),
('GRADE-02', 1),
('GRADE-03', 1),
('GRADE-04', 1),
('GRADE-05', 1);


-- ============================================================
-- 16. SERVICES
-- ============================================================
--
-- Les services fournis par le projet.
-- Certains rattachements à une direction sont laissés NULL
-- lorsqu'ils ne sont pas établis dans notre jeu de données.
-- ============================================================

INSERT INTO service
    (code, nom, description, direction_id)
VALUES
(
    'SCSD',
    'SERVICE DE LA COORDINATION DES SERVICES DECONCENTRES',
    'Coordination et suivi des services déconcentrés.',
    NULL
),
(
    'SGEAE',
    'SERVICE DE LA GESTION DES EFFECTIFS DES AGENTS DE L''ETAT',
    'Gestion administrative des effectifs des agents de l''Etat.',
    4
),
(
    'SLE',
    'SERVICE DE LA LEGISLATION ET DES ETUDES',
    'Etudes et travaux relatifs aux questions législatives et administratives.',
    NULL
),
(
    'SPPAE',
    'SERVICE DE LA POLITIQUE DE PROTECTION DES AGENTS DE L''ETAT',
    'Suivi des questions relatives à la protection des agents de l''Etat.',
    NULL
),
(
    'CESE',
    'CELLULE D''ETUDES, DE SUIVI ET D''EVALUATION',
    'Etudes, suivi et évaluation des activités.',
    NULL
),
(
    'SCPAE',
    'SERVICE CENTRAL DE LA PREVOYANCE DES AGENTS DE L''ETAT',
    'Gestion et suivi des questions de prévoyance des agents de l''Etat.',
    NULL
),
(
    'SCS',
    'SERVICE CENTRAL DE LA SOLDE',
    'Gestion et traitement des opérations relatives à la solde.',
    5
),
(
    'SL',
    'SERVICE DE LA LIQUIDATION DES PENSIONS',
    'Traitement et liquidation des dossiers de pensions.',
    5
);


-- ============================================================
-- 17. POSTES
-- ============================================================

-- SCSD
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de service',
       'Responsable de la coordination et du suivi du service.',
       id
FROM service WHERE code = 'SCSD';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de bureau',
       'Responsable d''un bureau administratif.',
       id
FROM service WHERE code = 'SCSD';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Secrétaire',
       'Assure les activités administratives et le secrétariat.',
       id
FROM service WHERE code = 'SCSD';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Agent administratif',
       'Assure le traitement des opérations administratives.',
       id
FROM service WHERE code = 'SCSD';


-- SGEAE
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de service',
       'Responsable de la gestion administrative des effectifs.',
       id
FROM service WHERE code = 'SGEAE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Gestionnaire de dossiers',
       'Gestion et suivi des dossiers administratifs des agents.',
       id
FROM service WHERE code = 'SGEAE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Agent administratif',
       'Traitement des opérations administratives liées aux effectifs.',
       id
FROM service WHERE code = 'SGEAE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Secrétaire',
       'Assure les tâches de secrétariat du service.',
       id
FROM service WHERE code = 'SGEAE';


-- SLE
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de service',
       'Responsable des études et travaux du service.',
       id
FROM service WHERE code = 'SLE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Juriste',
       'Participe aux études et analyses juridiques.',
       id
FROM service WHERE code = 'SLE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Chargé d''études',
       'Réalise des études et analyses administratives.',
       id
FROM service WHERE code = 'SLE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Secrétaire',
       'Assure les activités de secrétariat.',
       id
FROM service WHERE code = 'SLE';


-- SPPAE
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de service',
       'Responsable des activités du service.',
       id
FROM service WHERE code = 'SPPAE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Chargé d''études',
       'Participe aux études relatives à la protection des agents.',
       id
FROM service WHERE code = 'SPPAE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Agent administratif',
       'Assure le traitement administratif des dossiers.',
       id
FROM service WHERE code = 'SPPAE';


-- CESE
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de cellule',
       'Coordonne les activités de la cellule.',
       id
FROM service WHERE code = 'CESE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Chargé d''études',
       'Réalise les études et analyses nécessaires au suivi.',
       id
FROM service WHERE code = 'CESE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Agent de suivi',
       'Participe au suivi des activités et indicateurs.',
       id
FROM service WHERE code = 'CESE';


-- SCPAE
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de service',
       'Responsable des activités du service.',
       id
FROM service WHERE code = 'SCPAE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Gestionnaire de dossiers',
       'Gestion et suivi des dossiers de prévoyance.',
       id
FROM service WHERE code = 'SCPAE';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Agent administratif',
       'Traitement administratif des dossiers.',
       id
FROM service WHERE code = 'SCPAE';


-- SCS
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de service',
       'Responsable des opérations relatives à la solde.',
       id
FROM service WHERE code = 'SCS';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Agent de solde',
       'Traitement et suivi des opérations relatives à la solde.',
       id
FROM service WHERE code = 'SCS';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Gestionnaire de dossiers',
       'Gestion et suivi des dossiers de solde.',
       id
FROM service WHERE code = 'SCS';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Secrétaire',
       'Assure les activités de secrétariat.',
       id
FROM service WHERE code = 'SCS';


-- SL
INSERT INTO poste (intitule, description, service_id)
SELECT 'Chef de service',
       'Responsable du traitement des dossiers de pensions.',
       id
FROM service WHERE code = 'SL';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Agent de liquidation',
       'Traitement administratif des dossiers de liquidation.',
       id
FROM service WHERE code = 'SL';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Gestionnaire de dossiers',
       'Gestion et suivi des dossiers de pensions.',
       id
FROM service WHERE code = 'SL';

INSERT INTO poste (intitule, description, service_id)
SELECT 'Secrétaire',
       'Assure les activités de secrétariat.',
       id
FROM service WHERE code = 'SL';


-- ============================================================
-- 18. UTILISATEUR DE DEMONSTRATION
-- ============================================================

INSERT INTO utilisateur (email, password_hash)
VALUES
('rh.demo@mef.local', NULL);


-- ============================================================
-- 19. EMPLOYES DE DEMONSTRATION
-- ============================================================
--
-- Données fictives destinées aux tests de l'application.
-- ============================================================

-- Josette RAZANAMAHASOA - SGEAE
INSERT INTO employe (
    user_id,
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail,
    photo
)
SELECT
    1,
    'MEF001',
    'RAZANAMAHASOA',
    'Josette',
    'F',
    'Antananarivo',
    'CIN-DEMO-001',
    '0320000001',
    '1988-03-15',
    'Antananarivo',
    '2014-01-10',
    p.id,
    s.id,
    1,
    4,
    2,
    'Antananarivo',
    NULL
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'SGEAE'
AND p.intitule = 'Gestionnaire de dossiers'
LIMIT 1;


-- Andry RAKOTONDRABE - SCS
INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail
)
SELECT
    'MEF002',
    'RAKOTONDRABE',
    'Andry',
    'M',
    'Antananarivo',
    'CIN-DEMO-002',
    '0320000002',
    '1985-07-22',
    'Antsirabe',
    '2011-06-01',
    p.id,
    s.id,
    1,
    5,
    3,
    'Antananarivo'
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'SCS'
AND p.intitule = 'Agent de solde'
LIMIT 1;


-- Fara RASOLOARISON - SL
INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail
)
SELECT
    'MEF003',
    'RASOLOARISON',
    'Fara',
    'F',
    'Antananarivo',
    'CIN-DEMO-003',
    '0320000003',
    '1990-01-18',
    'Fianarantsoa',
    '2016-02-15',
    p.id,
    s.id,
    1,
    5,
    2,
    'Antananarivo'
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'SL'
AND p.intitule = 'Agent de liquidation'
LIMIT 1;


-- Hery RAKOTO - SLE
INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail
)
SELECT
    'MEF004',
    'RAKOTO',
    'Hery',
    'M',
    'Antananarivo',
    'CIN-DEMO-004',
    '0320000004',
    '1987-11-02',
    'Toamasina',
    '2013-09-12',
    p.id,
    s.id,
    1,
    4,
    3,
    'Antananarivo'
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'SLE'
AND p.intitule = 'Juriste'
LIMIT 1;


-- Mialy RAZAFINDRAKOTO - CESE
INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail
)
SELECT
    'MEF005',
    'RAZAFINDRAKOTO',
    'Mialy',
    'F',
    'Antananarivo',
    'CIN-DEMO-005',
    '0320000005',
    '1991-05-26',
    'Mahajanga',
    '2017-04-03',
    p.id,
    s.id,
    1,
    5,
    2,
    'Antananarivo'
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'CESE'
AND p.intitule = 'Chargé d''études'
LIMIT 1;


-- Toky ANDRIAMAMPIANINA - SCSD
INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail
)
SELECT
    'MEF006',
    'ANDRIAMAMPIANINA',
    'Toky',
    'M',
    'Antananarivo',
    'CIN-DEMO-006',
    '0320000006',
    '1983-09-14',
    'Antananarivo',
    '2010-01-11',
    p.id,
    s.id,
    1,
    4,
    3,
    'Antananarivo'
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'SCSD'
AND p.intitule = 'Chef de service'
LIMIT 1;


-- Soa RAVAOARISOA - SPPAE
INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail
)
SELECT
    'MEF007',
    'RAVAOARISOA',
    'Soa',
    'F',
    'Antananarivo',
    'CIN-DEMO-007',
    '0320000007',
    '1992-12-08',
    'Antsirabe',
    '2019-08-19',
    p.id,
    s.id,
    2,
    6,
    1,
    'Antananarivo'
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'SPPAE'
AND p.intitule = 'Chargé d''études'
LIMIT 1;


-- Solofo RABEMANANJARA - SCPAE
INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    adresse,
    cin,
    telephone,
    date_naissance,
    lieu_naissance,
    date_embauche,
    poste_id,
    service_id,
    type_emploi_id,
    categorie_id,
    grade_id,
    lieu_travail
)
SELECT
    'MEF008',
    'RABEMANANJARA',
    'Solofo',
    'M',
    'Antananarivo',
    'CIN-DEMO-008',
    '0320000008',
    '1989-06-30',
    'Fianarantsoa',
    '2015-03-16',
    p.id,
    s.id,
    1,
    5,
    2,
    'Antananarivo'
FROM poste p
JOIN service s ON p.service_id = s.id
WHERE s.code = 'SCPAE'
AND p.intitule = 'Gestionnaire de dossiers'
LIMIT 1;


-- ============================================================
-- 20. TACHES DE DEMONSTRATION
-- ============================================================

INSERT INTO tache (
    user_id,
    employe_id,
    titre,
    description,
    statut,
    priorite,
    date_echeance
)
SELECT
    1,
    e.id,
    'Vérifier le dossier de l''agent',
    'Vérifier les informations administratives du dossier.',
    'A_FAIRE',
    'HAUTE',
    CURRENT_TIMESTAMP + INTERVAL '3 days'
FROM employe e
WHERE e.matricule = 'MEF001';


INSERT INTO tache (
    user_id,
    employe_id,
    titre,
    description,
    statut,
    priorite,
    date_echeance
)
SELECT
    1,
    e.id,
    'Contrôler les informations de carrière',
    'Vérifier les informations relatives à la carrière de l''agent.',
    'EN_COURS',
    'NORMALE',
    CURRENT_TIMESTAMP + INTERVAL '7 days'
FROM employe e
WHERE e.matricule = 'MEF002';


INSERT INTO tache (
    user_id,
    titre,
    description,
    statut,
    priorite,
    date_echeance
)
VALUES (
    1,
    'Préparer un état des effectifs',
    'Préparer un état récapitulatif des effectifs par service.',
    'A_FAIRE',
    'NORMALE',
    CURRENT_TIMESTAMP + INTERVAL '10 days'
);


-- ============================================================
-- 21. NOTIFICATIONS DE DEMONSTRATION
-- ============================================================

INSERT INTO notification (
    user_id,
    titre,
    message,
    lu
)
VALUES
(
    1,
    'Nouvelle tâche',
    'Une nouvelle tâche vous a été attribuée.',
    FALSE
),
(
    1,
    'Bienvenue',
    'Bienvenue dans l''Assistant RH du MEF.',
    TRUE
);


-- ============================================================
-- 22. INDEX UTILES POUR LES RECHERCHES
-- ============================================================

CREATE INDEX idx_employe_nom
    ON employe(nom);

CREATE INDEX idx_employe_prenom
    ON employe(prenom);

CREATE INDEX idx_employe_service
    ON employe(service_id);

CREATE INDEX idx_employe_poste
    ON employe(poste_id);

CREATE INDEX idx_poste_service
    ON poste(service_id);

CREATE INDEX idx_service_direction
    ON service(direction_id);

CREATE INDEX idx_tache_user
    ON tache(user_id);

CREATE INDEX idx_notification_user
    ON notification(user_id);


-- ============================================================
-- 23. FIN DE LA TRANSACTION
-- ============================================================

COMMIT;