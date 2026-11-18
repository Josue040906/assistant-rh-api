-- Descriptions des directions, services et postes : leurs missions concrètes,
-- avec les mots que les agents emploient (paie, congés, réseaux, marchés…),
-- pour que chacun — et Band'IAkam — reconnaisse une structure à ce qu'elle fait.
-- Remplace aussi les accents abîmés de cinq services (« pr‚paration »).
-- Données métier : à appliquer à la main, avec accord (local, puis Neon).
-- Sauvegarde des anciennes descriptions : D:\Projets\sauvegardes\descriptions-organisation-avant-20261118.csv
BEGIN;

-- ---------------------------------------------------------------- directions
UPDATE direction SET description = 'Coordonne l''administration générale du ministère et la gestion de son personnel : ressources humaines (recrutement, carrières, avancements, congés, retraites, dossiers des agents), formation des agents, logistique, courrier, archives et moyens généraux.'
WHERE nom = 'Direction Générale de l''Administration';
UPDATE direction SET description = 'Prépare et exécute le budget du ministère et en tient la comptabilité : élaboration et suivi budgétaire, engagements et paiements des dépenses, recettes, comptabilité, contrôle financier et comptes rendus d''exécution.'
WHERE nom = 'Direction Financière';
UPDATE direction SET description = 'Conçoit, développe, exploite et sécurise les systèmes d''information du ministère : applications métiers et services numériques, réseaux, serveurs, postes de travail, messagerie, sécurité informatique et assistance aux utilisateurs.'
WHERE nom = 'Direction des Systèmes d''Information';
UPDATE direction SET description = 'Conseille le ministère sur toutes les questions juridiques : rédaction et contrôle des textes, contrats et conventions, contentieux et litiges, veille réglementaire et appui juridique aux autres directions.'
WHERE nom = 'Direction des Affaires Juridiques';

-- ---------------------------------------------------------------- services
UPDATE service SET description = 'Gère le personnel du ministère : dossiers individuels des agents, recrutements et prises de poste, carrières (avancements d''échelon, reclassements, titularisations), mutations et affectations, congés et absences, départs à la retraite, demandes et décisions administratives, états du personnel.'
WHERE code = 'SPERS';
UPDATE service SET description = 'Assure le fonctionnement administratif courant : courrier entrant et sortant, secrétariat, accueil, archives et classement, logistique, fournitures, entretien des locaux et gestion des moyens généraux (véhicules, salles, matériel).'
WHERE code = 'SAG';
UPDATE service SET description = 'Organise la formation des agents : recensement des besoins, plan de formation annuel, sessions et stages, inscriptions, suivi des participants, évaluation des formations et relations avec les organismes de formation.'
WHERE code = 'SFORM';
UPDATE service SET description = 'Prépare et suit le budget : élaboration du budget annuel, répartition des crédits, suivi de l''exécution budgétaire, engagements de dépenses, modifications budgétaires, prévisions et rapports d''exécution.'
WHERE code = 'SBUD';
UPDATE service SET description = 'Tient la comptabilité : enregistrement des opérations, mandatement et paiement des dépenses, suivi des recettes, rapprochements bancaires, tenue des comptes, états financiers et préparation des contrôles et audits.'
WHERE code = 'SCOMPT';
UPDATE service SET description = 'Développe et maintient les applications du ministère : analyse des besoins, conception, programmation, tests, mise en service, évolution et correction des logiciels métiers, sites et services numériques.'
WHERE code = 'SDEV';
UPDATE service SET description = 'Gère les infrastructures informatiques : réseaux et connexion Internet, serveurs, messagerie, postes de travail et imprimantes, sauvegardes, sécurité informatique (antivirus, accès, mots de passe) et assistance technique aux utilisateurs.'
WHERE code = 'SINFRA';
UPDATE service SET description = 'Apporte l''appui juridique : analyse et rédaction des textes, avis juridiques, contrats et conventions, suivi des contentieux et litiges, veille réglementaire et conseil aux autres services.'
WHERE code = 'SJUR';

-- ---------------------------------------------------------------- postes
-- Les postes de responsable : le titulaire dirige le service.
UPDATE poste p SET description = d.texte FROM (VALUES
    ('SPERS', 'Dirige le Service du Personnel : organise la gestion des agents, valide les actes et décisions de carrière (avancements, mutations, congés, retraites), répond aux demandes et rend compte à la direction.'),
    ('SAG',   'Dirige le Service de l''Administration Générale : organise le courrier, le secrétariat, la logistique et les moyens généraux, et coordonne les agents du service.'),
    ('SFORM', 'Dirige le Service de la Formation : arrête le plan de formation, pilote les actions de formation des agents et en évalue les résultats.'),
    ('SBUD',  'Dirige le Service du Budget : coordonne l''élaboration et le suivi du budget, valide les engagements et prépare les rapports d''exécution budgétaire.'),
    ('SCOMPT','Dirige le Service de la Comptabilité : supervise la tenue des comptes, les paiements et les états financiers, et prépare les contrôles.'),
    ('SDEV',  'Dirige le Service du Développement : planifie les projets d''applications, encadre l''équipe de développement et veille à la qualité des logiciels.'),
    ('SINFRA','Dirige le Service des Infrastructures : supervise les réseaux, serveurs et postes de travail, la sécurité informatique et l''assistance aux utilisateurs.'),
    ('SJUR',  'Dirige le Service Juridique : coordonne les avis, la rédaction des textes et le suivi des contentieux, et conseille la direction.')
) AS d(code, texte)
WHERE p.intitule = 'Chef de service' AND p.service_id = (SELECT id FROM service WHERE code = d.code);

-- Les autres postes, par service.
UPDATE poste p SET description = d.texte FROM (VALUES
    ('SPERS', 'Chef de bureau',            'Encadre un bureau du Service du Personnel : répartit et contrôle le traitement des dossiers des agents, prépare les actes de carrière et suit les demandes.'),
    ('SPERS', 'Gestionnaire RH',           'Gère les carrières des agents : avancements, reclassements, titularisations, mutations, congés et retraites ; prépare les actes et tient les dossiers à jour.'),
    ('SPERS', 'Gestionnaire de dossiers',  'Tient les dossiers individuels des agents : pièces administratives, diplômes, mises à jour d''état civil et de situation, saisie et classement des actes.'),
    ('SPERS', 'Assistant RH',              'Appuie la gestion du personnel : accueil des agents, saisie des demandes (congés, attestations), préparation de courriers et suivi des pièces.'),
    ('SPERS', 'Agent administratif',       'Traite les opérations administratives du Service du Personnel : enregistrement du courrier, saisie, classement et suivi des dossiers.'),
    ('SAG',   'Chef de bureau',            'Encadre un bureau de l''administration générale : courrier, archives ou logistique, et coordonne les agents qui y travaillent.'),
    ('SAG',   'Secrétaire',                'Assure le secrétariat : accueil, téléphone, agendas, rédaction et frappe de courriers, réception et envoi du courrier.'),
    ('SAG',   'Agent administratif',       'Traite les opérations administratives courantes : enregistrement et distribution du courrier, classement, archives et appui logistique.'),
    ('SFORM', 'Chargé de formation',       'Conçoit et anime les actions de formation : recense les besoins, organise les sessions, suit les participants et évalue les formations.'),
    ('SFORM', 'Gestionnaire de formation', 'Gère l''organisation pratique des formations : inscriptions, convocations, salles, relations avec les organismes de formation et suivi des présences.'),
    ('SFORM', 'Assistant administratif',   'Appuie le Service de la Formation : courriers, convocations, saisie des inscriptions et classement des dossiers de formation.'),
    ('SBUD',  'Analyste budgétaire',       'Analyse les données budgétaires : prévisions, suivi des consommations de crédits, écarts, tableaux de bord et notes d''aide à la décision.'),
    ('SBUD',  'Gestionnaire budgétaire',   'Suit l''exécution du budget : engagements de dépenses, disponibilités de crédits, modifications budgétaires et saisie des opérations.'),
    ('SBUD',  'Assistant administratif',   'Appuie le Service du Budget : courriers, classement des pièces budgétaires, saisie et préparation des dossiers.'),
    ('SCOMPT','Comptable',                 'Tient la comptabilité : enregistre les opérations, prépare les paiements, suit les recettes, effectue les rapprochements bancaires et produit les états.'),
    ('SCOMPT','Gestionnaire comptable',    'Traite les dossiers comptables : vérification des pièces de dépense, mandatement, suivi des paiements et des fournisseurs.'),
    ('SCOMPT','Assistant comptable',       'Appuie les opérations comptables : saisie des écritures, classement des pièces justificatives et préparation des rapprochements.'),
    ('SDEV',  'Analyste développeur',      'Analyse les besoins des services, conçoit et programme les applications, rédige la documentation et assure leur évolution.'),
    ('SDEV',  'Développeur logiciel',      'Programme et maintient les applications et services numériques : nouvelles fonctions, corrections, mises à jour et déploiements.'),
    ('SDEV',  'Testeur logiciel',          'Vérifie la qualité des applications : plans de tests, recherche et signalement des anomalies, validation avant mise en service.'),
    ('SINFRA','Administrateur réseaux',    'Administre les réseaux : connexions, Internet, Wi-Fi, équipements réseau, accès à distance et sécurité des échanges.'),
    ('SINFRA','Administrateur systèmes',   'Administre les serveurs et systèmes : installation, messagerie, comptes et droits d''accès, sauvegardes, mises à jour et sécurité.'),
    ('SINFRA','Technicien informatique',   'Installe et dépanne les postes de travail, imprimantes et logiciels, et assiste les utilisateurs au quotidien.'),
    ('SJUR',  'Juriste',                   'Analyse les questions juridiques, rédige avis, textes et contrats, et suit les contentieux et litiges du ministère.'),
    ('SJUR',  'Chargé d''études juridiques','Mène des études et de la veille juridique et réglementaire, et prépare les projets de textes et notes juridiques.'),
    ('SJUR',  'Assistant juridique',       'Appuie le Service Juridique : recherche documentaire, préparation et classement des dossiers, suivi des échéances des contentieux.')
) AS d(code, intitule, texte)
WHERE p.intitule = d.intitule AND p.service_id = (SELECT id FROM service WHERE code = d.code);

COMMIT;

-- Vérification : aucune description vide, aucun accent abîmé.
SELECT 'vides' AS controle, COUNT(*) FROM (
    SELECT description FROM direction UNION ALL SELECT description FROM service UNION ALL SELECT description FROM poste
) t WHERE COALESCE(BTRIM(description), '') = ''
UNION ALL
SELECT 'accents abîmés', COUNT(*) FROM (
    SELECT description FROM direction UNION ALL SELECT description FROM service UNION ALL SELECT description FROM poste
) t WHERE description ~ U&'\201A';
