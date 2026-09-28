BEGIN;

-- ============================================================
-- 1. POSTES DE DIRECTEUR
-- ============================================================

INSERT INTO poste (
    intitule,
    description,
    service_id
)
SELECT
    'Directeur',
    'Responsable de la direction.',
    2
WHERE NOT EXISTS (
    SELECT 1
    FROM poste
    WHERE LOWER(intitule) = 'directeur'
      AND service_id = 2
);

INSERT INTO poste (
    intitule,
    description,
    service_id
)
SELECT
    'Directeur',
    'Responsable de la direction.',
    7
WHERE NOT EXISTS (
    SELECT 1
    FROM poste
    WHERE LOWER(intitule) = 'directeur'
      AND service_id = 7
);


-- ============================================================
-- 2. DIRECTEUR DGEAE
--    Direction 4
--    Service de rattachement technique : SGEAE (id 2)
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-DIR-001',
    'RAKOTOMALALA',
    'Hanta',
    'F',
    '1980-06-15',
    '2008-01-02',
    p.id,
    2
FROM poste p
WHERE LOWER(p.intitule) = 'directeur'
  AND p.service_id = 2
  AND NOT EXISTS (
      SELECT 1
      FROM employe
      WHERE matricule = 'MEF-DIR-001'
  );


-- ============================================================
-- 3. DIRECTEUR DSP
--    Direction 5
--    Service de rattachement technique : SCS (id 7)
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-DIR-002',
    'RAZAFINDRAIBE',
    'Tahina',
    'M',
    '1979-11-23',
    '2007-03-12',
    p.id,
    7
FROM poste p
WHERE LOWER(p.intitule) = 'directeur'
  AND p.service_id = 7
  AND NOT EXISTS (
      SELECT 1
      FROM employe
      WHERE matricule = 'MEF-DIR-002'
  );


-- ============================================================
-- 4. CHEF DE SERVICE SGEAE
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-001',
    'ANDRIANINA',
    'Lova',
    'F',
    '1984-04-18',
    '2012-02-06',
    5,
    2
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-001'
);


-- ============================================================
-- 5. CHEF DE SERVICE SCS
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-002',
    'RAKOTOARISOA',
    'Faly',
    'M',
    '1982-09-11',
    '2010-07-19',
    22,
    7
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-002'
);


-- ============================================================
-- 6. CHEF DE SERVICE SL
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-003',
    'ANDRIANASOLO',
    'Mamy',
    'F',
    '1985-02-27',
    '2013-05-13',
    26,
    8
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-003'
);


-- ============================================================
-- 7. CHEF DE SERVICE SLE
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-004',
    'RAZAFIARISON',
    'Tiana',
    'F',
    '1986-08-09',
    '2014-09-01',
    9,
    3
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-004'
);


-- ============================================================
-- 8. CHEF DE CELLULE CESE
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-005',
    'RAKOTONDRINA',
    'Voahirana',
    'F',
    '1987-03-21',
    '2015-01-12',
    16,
    5
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-005'
);


-- ============================================================
-- 9. CHEF DE SERVICE SCSD
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-006',
    'ANDRIAMBOLOLONA',
    'Niry',
    'M',
    '1983-12-05',
    '2011-11-07',
    1,
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-006'
);


-- ============================================================
-- 10. CHEF DE SERVICE SPPAE
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-007',
    'RASOAMANANA',
    'Aina',
    'F',
    '1988-10-14',
    '2016-06-20',
    13,
    4
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-007'
);


-- ============================================================
-- 11. CHEF DE SERVICE SCPAE
-- ============================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    date_embauche,
    poste_id,
    service_id
)
SELECT
    'MEF-CHEF-008',
    'RAKOTOZANDRY',
    'Fetra',
    'M',
    '1984-07-30',
    '2012-10-15',
    19,
    6
WHERE NOT EXISTS (
    SELECT 1
    FROM employe
    WHERE matricule = 'MEF-CHEF-008'
);


COMMIT;