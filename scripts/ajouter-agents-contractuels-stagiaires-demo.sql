-- Agents FICTIFS de démonstration : contractuels (Auxiliaire, échelle I) à tous
-- les échelons et stagiaires, pour montrer la titularisation guidée par l'indice.
-- Chacun a un diplôme qui fixe sa catégorie, une affectation, et une période de
-- carrière par échelon (deux ans chacune). Rejouable : un matricule déjà présent est ignoré.

DO $$
DECLARE
    -- matricule, nom, prénom, sexe, naissance, type (C/S), niveau de diplôme, échelon, poste
    agents TEXT[][] := ARRAY[
        ['DEMO-101','RAZAFINDRAKOTO','Tiana','F','1998-03-14','C','5','1','3'],
        ['DEMO-102','ANDRIAMAHEFA','Rado','M','1995-07-02','C','4','2','10'],
        ['DEMO-103','RAHARISOA','Volatiana','F','1993-11-23','C','3','3','14'],
        ['DEMO-104','RANDRIANASOLO','Haja','M','1990-01-30','C','5','4','18'],
        ['DEMO-105','RAKOTONIRINA','Mamy','M','1989-05-17','C','3','5','22'],
        ['DEMO-106','RASOLOFONIRINA','Lalao','F','1987-09-08','C','4','6','26'],
        ['DEMO-107','RAMAROSON','Fidy','M','1985-12-12','C','3','7','30'],
        ['DEMO-108','RAVELOJAONA','Hanitra','F','1983-04-25','C','2','8','34'],
        ['DEMO-109','ANDRIANIRINA','Toky','M','1981-08-19','C','5','9','9'],
        ['DEMO-110','RAZANAMPARANY','Nirina','F','1980-02-03','C','3','9','13'],
        ['DEMO-111','RAKOTOVAO','Andry','M','1986-10-28','C','2','6','17'],
        ['DEMO-112','RABEMANANJARA','Soa','F','1991-06-21','C','4','4','25'],
        ['DEMO-113','RAJAONARISON','Kanto','F','2001-02-11','S','3','1','12'],
        ['DEMO-114','RANDRIAMIHAJA','Lova','M','2000-09-05','S','2','1','16'],
        ['DEMO-115','RAHAJANIRINA','Mialy','F','2003-01-27','S','5','1','28'],
        ['DEMO-116','RATSIMBAZAFY','Hery','M','2002-07-14','S','4','1','32'],
        ['DEMO-117','RAKOTOMALALA','Fara','F','2001-11-30','S','3','1','20'],
        ['DEMO-118','ANDRIANJAFY','Tojo','M','2003-05-09','S','5','1','8']
    ];
    a TEXT[];
    n INTEGER := 0;
    v_employe INTEGER;
    v_niveau INTEGER;
    v_categorie INTEGER;
    v_echelons INTEGER;
    v_embauche DATE;
    v_debut DATE;
    v_poste INTEGER;
    v_service INTEGER;
    v_type INTEGER;
    v_echelon INTEGER;
    v_domaine INTEGER;
    v_etab INTEGER;
BEGIN
    FOREACH a SLICE 1 IN ARRAY agents LOOP
        n := n + 1;
        CONTINUE WHEN EXISTS (SELECT 1 FROM employe WHERE matricule = a[1]);

        v_niveau := a[7]::int;
        v_echelons := a[8]::int;
        v_poste := a[9]::int;
        SELECT categorie_id INTO v_categorie FROM niveau_diplome WHERE id = v_niveau;
        SELECT service_id INTO v_service FROM poste WHERE id = v_poste;
        SELECT id INTO v_type FROM type_emploi
            WHERE nom = CASE a[6] WHEN 'C' THEN 'Agent contractuel' ELSE 'Stagiaire' END;
        -- Un contractuel a passé deux ans par échelon ; un stagiaire est arrivé dans l'année.
        v_embauche := CASE a[6]
            WHEN 'C' THEN (DATE '2026-07-01' - make_interval(years => 2 * (v_echelons - 1), months => n))::date
            ELSE (DATE '2026-01-05' + make_interval(months => n % 6))::date
        END;

        INSERT INTO employe (matricule, nom, prenom, sexe, adresse, cin, telephone, date_naissance,
                             lieu_naissance, date_embauche, type_emploi_id, categorie_id, lieu_travail)
        VALUES (a[1], a[2], a[3], a[4], 'Antananarivo', '2010' || LPAD((880100 + n)::text, 8, '0'),
                '03419' || LPAD(n::text, 5, '0'), a[5]::date, 'Antananarivo', v_embauche,
                v_type, v_categorie, 'Siège administratif')
        RETURNING id INTO v_employe;

        -- Le diplôme qui fixe la catégorie : une combinaison niveau/domaine/établissement déjà connue.
        SELECT domaine_id, etablissement_id INTO v_domaine, v_etab
        FROM employe_diplome WHERE niveau_id = v_niveau
        GROUP BY domaine_id, etablissement_id ORDER BY COUNT(*) DESC LIMIT 1;
        INSERT INTO employe_diplome (employe_id, date_obtention, numero_diplome, principal, observation,
                                     niveau_id, domaine_id, etablissement_id)
        VALUES (v_employe, (v_embauche - INTERVAL '1 year')::date, 'DEMO-DIP-' || a[1], TRUE,
                'Diplôme fictif de démonstration', v_niveau, v_domaine, v_etab);

        INSERT INTO affectation (employe_id, poste_id, service_id, date_debut, reference_acte, observation)
        VALUES (v_employe, v_poste, v_service, v_embauche, NULL, 'Affectation fictive de démonstration');

        -- Une période par échelon : A11E, A12E… ou ST01E.
        FOR i IN 1..v_echelons LOOP
            SELECT id INTO v_echelon FROM echelon
            WHERE code = CASE a[6] WHEN 'C' THEN 'A1' || i || 'E' ELSE 'ST01E' END;
            v_debut := (v_embauche + make_interval(years => 2 * (i - 1)))::date;
            INSERT INTO historique_carriere (employe_id, date_debut, date_fin, echelon_id, categorie_id)
            VALUES (v_employe, v_debut,
                    CASE WHEN i < v_echelons
                         THEN (v_embauche + make_interval(years => 2 * i))::date - 1 END,
                    v_echelon, v_categorie);
        END LOOP;
    END LOOP;
END $$;
