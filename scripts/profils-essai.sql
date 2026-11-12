-- Profils d'agents FICTIFS pour les essais : un cas par fonctionnalité à tester.
--
--   ESSAI-01  stagiaire qui vient d'arriver
--   ESSAI-02  stagiaire en fin de stage, titularisable
--   ESSAI-03  contractuel débutant
--   ESSAI-04  contractuel dont l'ancienneté d'échelon est atteinte (avancement possible)
--   ESSAI-05  contractuel ancien, au dernier échelon, titularisable
--   ESSAI-06  parcours complet : stagiaire, puis contractuel, puis titularisé fonctionnaire
--   ESSAI-07  ancien contractuel titularisé en 2019
--   ESSAI-08  fonctionnaire reclassé deux fois (historique des reclassements)
--   ESSAI-09  fonctionnaire reclassable : diplôme plus élevé enregistré, pas encore appliqué
--   ESSAI-10  fonctionnaire qui peut partir à la retraite
--   ESSAI-11  fonctionnaire qui pourra partir à la retraite dans quelques mois
--   ESSAI-12  fonctionnaire parti à la retraite
--   ESSAI-13  fonctionnaire en congé en ce moment
--   ESSAI-14  fonctionnaire dont l'ancienneté d'échelon est atteinte (avancement possible)
--
-- Rejouable : les agents ESSAI-* existants sont d'abord supprimés avec tout ce qui
-- les concerne (documents, congés, activités…), puis recréés. Les carrières suivent
-- les durées minimales des grades ; une titularisation prend le premier grade de
-- fonctionnaire d'indice égal ou supérieur, comme l'application.
-- Les photos (illustrations) sont posées ensuite par scripts/photos-profils-essai.py.

BEGIN;

-- ---------------------------------------------------------------- remise à zéro
CREATE TEMP TABLE essai_ids ON COMMIT DROP AS
    SELECT id FROM employe WHERE matricule LIKE 'ESSAI-%';
DELETE FROM reclassement WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM conge WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM avancement WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM notification WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM piece_scannee WHERE cible_type = 'DOCUMENT'
    AND cible_id IN (SELECT id FROM document WHERE employe_id IN (SELECT id FROM essai_ids));
DELETE FROM document WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM activite WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM historique_carriere WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM piece_scannee WHERE cible_type = 'DIPLOME'
    AND cible_id IN (SELECT id FROM employe_diplome WHERE employe_id IN (SELECT id FROM essai_ids));
DELETE FROM employe_diplome WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM affectation WHERE employe_id IN (SELECT id FROM essai_ids);
DELETE FROM piece_scannee WHERE cible_type = 'AGENT' AND cible_id IN (SELECT id FROM essai_ids);
DELETE FROM employe WHERE id IN (SELECT id FROM essai_ids);

-- ---------------------------------------------------------------- outils
-- L'ordre des grades de chaque parcours.
CREATE FUNCTION pg_temp.ordre(code TEXT) RETURNS TEXT[] LANGUAGE sql AS $$
    SELECT CASE
        WHEN code LIKE 'A1%' THEN ARRAY['A11E','A12E','A13E','A14E','A15E','A16E','A17E','A18E','A19E']
        WHEN code LIKE 'ST%' THEN ARRAY['ST01E']
        ELSE ARRAY['3C1E','3C2E','3C3E','2C1E','2C2E','2C3E','1C1E','1C2E','1C3E',
                   'PC1E','PC2E','PC3E','EC1E','EC2E','EC3E'] END
$$;

-- Un agent, son diplôme principal (qui fixe sa catégorie) et son affectation.
CREATE FUNCTION pg_temp.agent(mat TEXT, p_nom TEXT, p_prenom TEXT, p_sexe TEXT, naissance DATE,
                              p_type TEXT, niveau INT, obtention DATE, embauche DATE, p_poste INT)
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE
    v_id INT; v_cat INT; v_rang INT;
BEGIN
    v_rang := substr(mat, 7)::int;
    SELECT categorie_id INTO v_cat FROM niveau_diplome WHERE id = niveau;
    INSERT INTO employe (matricule, nom, prenom, sexe, adresse, cin, telephone, date_naissance,
                         lieu_naissance, date_embauche, type_emploi_id, categorie_id, lieu_travail)
    VALUES (mat, p_nom, p_prenom, p_sexe, 'Antananarivo', '1019' || LPAD((7700 + v_rang)::text, 8, '0'),
            '03320' || LPAD(v_rang::text, 5, '0'), naissance, 'Antananarivo', embauche,
            (SELECT te.id FROM type_emploi te WHERE te.nom = p_type), v_cat, 'Siège administratif')
    RETURNING id INTO v_id;
    PERFORM pg_temp.diplome(v_id, niveau, obtention, TRUE);
    INSERT INTO affectation (employe_id, poste_id, service_id, date_debut, observation)
    VALUES (v_id, p_poste, (SELECT po.service_id FROM poste po WHERE po.id = p_poste), embauche, 'Profil d''essai');
    RETURN v_id;
END $$;

-- Un diplôme : une combinaison niveau / domaine / établissement déjà connue.
CREATE FUNCTION pg_temp.diplome(emp INT, niveau INT, obtention DATE, principal BOOLEAN)
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE v_dom INT; v_etab INT; v_id INT;
BEGIN
    SELECT domaine_id, etablissement_id INTO v_dom, v_etab
    FROM employe_diplome WHERE niveau_id = niveau
    GROUP BY domaine_id, etablissement_id ORDER BY COUNT(*) DESC LIMIT 1;
    IF v_dom IS NULL THEN
        SELECT domaine_id, etablissement_id INTO v_dom, v_etab FROM employe_diplome LIMIT 1;
    END IF;
    INSERT INTO employe_diplome (employe_id, date_obtention, numero_diplome, principal, observation,
                                 niveau_id, domaine_id, etablissement_id)
    VALUES (emp, obtention, 'ESSAI-DIP-' || emp || '-' || niveau, principal, 'Diplôme fictif (profil d''essai)',
            niveau, v_dom, v_etab)
    RETURNING id INTO v_id;
    RETURN v_id;
END $$;

-- Une nouvelle période : la période ouverte se ferme la veille.
CREATE FUNCTION pg_temp.periode(emp INT, code TEXT, cat INT, debut DATE)
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE v_id INT;
BEGIN
    UPDATE historique_carriere SET date_fin = debut - 1 WHERE employe_id = emp AND date_fin IS NULL;
    INSERT INTO historique_carriere (employe_id, date_debut, echelon_id, categorie_id)
    VALUES (emp, debut, (SELECT id FROM echelon WHERE echelon.code = periode.code), cat)
    RETURNING id INTO v_id;
    RETURN v_id;
END $$;

-- Avance d'un grade à chaque durée minimale, tant que le grade suivant commence au plus tard « jusqua ».
CREATE FUNCTION pg_temp.suite(emp INT, jusqua DATE) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE v_code TEXT; v_cat INT; v_debut DATE; v_duree INT; v_ordre TEXT[]; v_pos INT;
BEGIN
    LOOP
        SELECT ec.code, hc.categorie_id, hc.date_debut, ec.duree_min
          INTO v_code, v_cat, v_debut, v_duree
        FROM historique_carriere hc JOIN echelon ec ON ec.id = hc.echelon_id
        WHERE hc.employe_id = emp AND hc.date_fin IS NULL;
        v_ordre := pg_temp.ordre(v_code);
        v_pos := array_position(v_ordre, v_code);
        EXIT WHEN v_duree IS NULL OR v_pos >= array_length(v_ordre, 1)
               OR (v_debut + make_interval(years => v_duree))::date > jusqua;
        PERFORM pg_temp.periode(emp, v_ordre[v_pos + 1], v_cat, (v_debut + make_interval(years => v_duree))::date);
    END LOOP;
END $$;

-- Une carrière qui commence à ce grade et avance jusqu'à « jusqua ».
CREATE FUNCTION pg_temp.carriere(emp INT, code TEXT, debut DATE, jusqua DATE DEFAULT CURRENT_DATE)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_temp.periode(emp, code, (SELECT categorie_id FROM employe WHERE id = emp), debut);
    PERFORM pg_temp.suite(emp, jusqua);
END $$;

-- Titularisation : le premier grade de fonctionnaire d'indice égal ou supérieur, même catégorie.
CREATE FUNCTION pg_temp.titulariser(emp INT, quand DATE) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE v_cat INT; v_indice INT; v_code TEXT;
BEGIN
    SELECT hc.categorie_id, gi.indice INTO v_cat, v_indice
    FROM historique_carriere hc
    JOIN grille_indiciaire gi ON gi.categorie_id = hc.categorie_id AND gi.echelon_id = hc.echelon_id
    WHERE hc.employe_id = emp AND hc.date_fin IS NULL;
    SELECT ec.code INTO v_code
    FROM grille_indiciaire gi JOIN echelon ec ON ec.id = gi.echelon_id
    JOIN classe cl ON cl.id = ec.classe_id JOIN parcours pa ON pa.id = cl.parcours_id
    WHERE pa.code = 'FONCTIONNAIRE' AND gi.categorie_id = v_cat AND gi.indice >= v_indice
    ORDER BY gi.indice LIMIT 1;
    PERFORM pg_temp.periode(emp, v_code, v_cat, quand);
    UPDATE employe SET type_emploi_id = (SELECT id FROM type_emploi WHERE nom = 'Fonctionnaire') WHERE id = emp;
END $$;

-- Passage de stagiaire à contractuel, au premier échelon.
CREATE FUNCTION pg_temp.contractualiser(emp INT, quand DATE) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_temp.periode(emp, 'A11E', (SELECT categorie_id FROM employe WHERE id = emp), quand);
    UPDATE employe SET type_emploi_id = (SELECT id FROM type_emploi WHERE nom = 'Agent contractuel') WHERE id = emp;
END $$;

-- Reclassement : un diplôme plus élevé, enregistré, change la catégorie à cette date (le grade reste).
CREATE FUNCTION pg_temp.reclasser(emp INT, niveau INT, obtention DATE, quand DATE)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_dip INT; v_avant INT; v_apres INT; v_code TEXT; v_hist_avant INT; v_hist_apres INT; v_act BIGINT;
BEGIN
    UPDATE employe_diplome SET principal = FALSE WHERE employe_id = emp;
    v_dip := pg_temp.diplome(emp, niveau, obtention, TRUE);
    SELECT hc.id, hc.categorie_id, ec.code INTO v_hist_avant, v_avant, v_code
    FROM historique_carriere hc JOIN echelon ec ON ec.id = hc.echelon_id
    WHERE hc.employe_id = emp AND hc.date_fin IS NULL;
    SELECT categorie_id INTO v_apres FROM niveau_diplome WHERE id = niveau;
    v_hist_apres := pg_temp.periode(emp, v_code, v_apres, quand);
    UPDATE employe SET categorie_id = v_apres WHERE id = emp;
    INSERT INTO activite (acteur_id, employe_id, type_action, description, date_heure)
    VALUES ((SELECT id FROM utilisateur WHERE role = 'SPERS_CHEF' ORDER BY id LIMIT 1), emp, 'MODIFICATION',
            'Reclassement selon le diplôme ' || (SELECT libelle FROM niveau_diplome WHERE id = niveau)
            || ' : catégorie ' || (SELECT code FROM categorie WHERE id = v_avant)
            || ' vers catégorie ' || (SELECT code FROM categorie WHERE id = v_apres) || ' (profil d''essai).',
            quand + TIME '09:00')
    RETURNING id INTO v_act;
    INSERT INTO reclassement (employe_id, id_activite, employe_diplome_id, historique_avant_id, historique_apres_id,
                              categorie_avant_id, categorie_apres_id, corps_avant_id, corps_apres_id,
                              date_effet, date_creation)
    VALUES (emp, v_act, v_dip, v_hist_avant, v_hist_apres, v_avant, v_apres,
            (SELECT corps_id FROM categorie WHERE id = v_avant), (SELECT corps_id FROM categorie WHERE id = v_apres),
            quand, quand + TIME '09:00');
END $$;

-- Départ à la retraite : la carrière et l'affectation se ferment ce jour-là.
CREATE FUNCTION pg_temp.retraite(emp INT, quand DATE) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    UPDATE historique_carriere SET date_fin = quand WHERE employe_id = emp AND date_fin IS NULL;
    UPDATE affectation SET date_fin = quand WHERE employe_id = emp AND date_fin IS NULL;
    UPDATE employe SET date_retraite = quand WHERE id = emp;
END $$;

-- Un poste qui n'a plus de place libre en reçoit une de plus (les essais ne doivent pas bloquer).
CREATE FUNCTION pg_temp.place(p_poste INT) RETURNS INT LANGUAGE plpgsql AS $$
BEGIN
    UPDATE poste p SET nombre_places = nombre_places + 1
    WHERE p.id = p_poste
      AND (SELECT COUNT(*) FROM affectation a WHERE a.poste_id = p.id AND a.date_fin IS NULL) >= p.nombre_places;
    RETURN p_poste;
END $$;

-- ---------------------------------------------------------------- les profils
-- Niveaux : 1 Doctorat, 2 Master, 3 Licence, 4 Bac +2, 5 Baccalauréat, 6 BEPC.
DO $$
DECLARE e INT;
BEGIN
    -- 01 Stagiaire qui vient d'arriver.
    e := pg_temp.agent('ESSAI-01', 'RAHARIMANANA', 'Ony', 'F', '2003-04-17', 'Stagiaire', 5, '2022-07-15', '2026-09-01', pg_temp.place(14));
    PERFORM pg_temp.carriere(e, 'ST01E', '2026-09-01');

    -- 02 Stagiaire en fin de stage : un an passé, titularisable.
    e := pg_temp.agent('ESSAI-02', 'RAKOTOARIVONY', 'Tsiry', 'M', '2000-12-03', 'Stagiaire', 3, '2024-11-20', '2025-10-06', pg_temp.place(18));
    PERFORM pg_temp.carriere(e, 'ST01E', '2025-10-06');

    -- 03 Contractuel débutant.
    e := pg_temp.agent('ESSAI-03', 'RAVAOARISOA', 'Hanta', 'F', '1999-08-22', 'Agent contractuel', 6, '2016-07-01', '2025-03-03', pg_temp.place(22));
    PERFORM pg_temp.carriere(e, 'A11E', '2025-03-03');

    -- 04 Contractuel : le 6e échelon a commencé il y a plus de deux ans, l'avancement est possible.
    e := pg_temp.agent('ESSAI-04', 'RANDRIAMANANTENA', 'Fidy', 'M', '1990-02-11', 'Agent contractuel', 4, '2012-10-05', '2014-05-05', pg_temp.place(25));
    PERFORM pg_temp.carriere(e, 'A11E', '2014-05-05', '2024-06-30');

    -- 05 Contractuel ancien, au dernier échelon, titularisable.
    e := pg_temp.agent('ESSAI-05', 'RAZAFINDRATSIMA', 'Lalaina', 'F', '1982-06-09', 'Agent contractuel', 2, '2007-03-30', '2008-02-04', pg_temp.place(26));
    PERFORM pg_temp.carriere(e, 'A11E', '2008-02-04');

    -- 06 Parcours complet : stagiaire (2009), contractuel (2010), fonctionnaire (2016).
    e := pg_temp.agent('ESSAI-06', 'ANDRIAMASINORO', 'Haja', 'M', '1985-01-28', 'Stagiaire', 3, '2008-06-12', '2009-01-05', pg_temp.place(30));
    PERFORM pg_temp.carriere(e, 'ST01E', '2009-01-05');
    PERFORM pg_temp.contractualiser(e, '2010-01-05');
    PERFORM pg_temp.suite(e, '2015-12-31');
    PERFORM pg_temp.titulariser(e, '2016-01-05');
    PERFORM pg_temp.suite(e, CURRENT_DATE);

    -- 07 Ancien contractuel titularisé en 2019.
    e := pg_temp.agent('ESSAI-07', 'RASOAMANARIVO', 'Nirina', 'F', '1988-09-14', 'Agent contractuel', 4, '2010-11-02', '2011-04-04', pg_temp.place(33));
    PERFORM pg_temp.carriere(e, 'A11E', '2011-04-04', '2019-03-31');
    PERFORM pg_temp.titulariser(e, '2019-04-01');
    PERFORM pg_temp.suite(e, CURRENT_DATE);

    -- 08 Fonctionnaire reclassé deux fois : Licence en 2012, Master en 2019.
    e := pg_temp.agent('ESSAI-08', 'RAKOTONDRAZAKA', 'Mamy', 'M', '1980-05-03', 'Fonctionnaire', 5, '2004-07-20', '2006-03-01', pg_temp.place(34));
    PERFORM pg_temp.carriere(e, '3C1E', '2006-03-01', '2013-01-13');
    PERFORM pg_temp.reclasser(e, 3, '2012-11-28', '2013-01-14');
    PERFORM pg_temp.suite(e, '2019-06-02');
    PERFORM pg_temp.reclasser(e, 2, '2019-03-08', '2019-06-03');
    PERFORM pg_temp.suite(e, CURRENT_DATE);

    -- 09 Fonctionnaire reclassable : une Licence obtenue en 2026, pas encore appliquée.
    e := pg_temp.agent('ESSAI-09', 'RAMANANTSOA', 'Voahirana', 'F', '1992-03-25', 'Fonctionnaire', 5, '2011-07-18', '2015-09-01', pg_temp.place(10));
    PERFORM pg_temp.carriere(e, '3C1E', '2015-09-01');
    PERFORM pg_temp.diplome(e, 3, '2026-07-10', FALSE);

    -- 10 Fonctionnaire qui peut partir à la retraite (plus de 25 ans de service).
    e := pg_temp.agent('ESSAI-10', 'RAZAFIMANDIMBY', 'Jean-Claude', 'M', '1964-10-30', 'Fonctionnaire', 3, '1996-12-10', '1998-04-01', pg_temp.place(28));
    PERFORM pg_temp.carriere(e, '3C1E', '1998-04-01');

    -- 11 Fonctionnaire qui pourra partir à la retraite en mars 2027.
    e := pg_temp.agent('ESSAI-11', 'RANDRIANARIVELO', 'Bako', 'F', '1968-01-19', 'Fonctionnaire', 4, '1999-06-30', '2002-03-18', pg_temp.place(24));
    PERFORM pg_temp.carriere(e, '3C1E', '2002-03-18');

    -- 12 Fonctionnaire parti à la retraite le 30 juin 2026.
    e := pg_temp.agent('ESSAI-12', 'RAKOTOMANGA', 'Philibert', 'M', '1960-02-14', 'Fonctionnaire', 2, '1994-09-15', '1996-01-08', pg_temp.place(32));
    PERFORM pg_temp.carriere(e, '3C1E', '1996-01-08', '2026-06-30');
    PERFORM pg_temp.retraite(e, '2026-06-30');

    -- 13 Fonctionnaire en congé en ce moment.
    e := pg_temp.agent('ESSAI-13', 'RAHELIMALALA', 'Sitraka', 'F', '1990-07-07', 'Fonctionnaire', 3, '2014-12-01', '2017-02-06', pg_temp.place(12));
    PERFORM pg_temp.carriere(e, '3C1E', '2017-02-06');
    INSERT INTO conge (employe_id, date_debut, date_fin, jours, date_creation)
    VALUES (e, CURRENT_DATE - 9, CURRENT_DATE + 9, 19, now());

    -- 14 Fonctionnaire : le grade actuel a commencé il y a plus de trois ans, l'avancement est possible.
    e := pg_temp.agent('ESSAI-14', 'ANDRIANTSOA', 'Rija', 'M', '1987-11-02', 'Fonctionnaire', 4, '2009-08-27', '2013-06-03', pg_temp.place(17));
    PERFORM pg_temp.carriere(e, '3C1E', '2013-06-03', '2023-06-01');
END $$;

COMMIT;

-- Contrôle : un profil par ligne.
SELECT e.matricule, e.nom, e.prenom, te.nom AS type_emploi, ca.code AS categorie, ec.code AS grade, hc.date_debut AS depuis,
       e.date_retraite, (SELECT COUNT(*) FROM historique_carriere h WHERE h.employe_id = e.id) AS periodes,
       (SELECT COUNT(*) FROM reclassement r WHERE r.employe_id = e.id) AS reclassements
FROM employe e
JOIN type_emploi te ON te.id = e.type_emploi_id
LEFT JOIN categorie ca ON ca.id = e.categorie_id
LEFT JOIN LATERAL (SELECT * FROM historique_carriere h WHERE h.employe_id = e.id ORDER BY h.date_debut DESC LIMIT 1) hc ON TRUE
LEFT JOIN echelon ec ON ec.id = hc.echelon_id
WHERE e.matricule LIKE 'ESSAI-%' ORDER BY e.matricule;
