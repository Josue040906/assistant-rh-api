-- Lot 6 : la grille fictive est réorganisée pour que l'indice guide vraiment la
-- titularisation. Un contractuel avancé ne doit pas repartir du premier grade de
-- fonctionnaire : les grilles se recouvrent. Restent vrais, dans chaque catégorie :
--   - un grade plus haut a un indice plus haut ;
--   - une catégorie plus haute a un indice plus haut, pour un même grade ;
--   - à l'entrée, fonctionnaire (3C1E) > contractuel (A11E) > stagiaire (ST01E).
-- Les trois parcours ont le même écart entre catégories (50), si bien qu'un même
-- grade de contractuel mène au même grade de fonctionnaire quelle que soit la catégorie.
-- Fonctionnaires : inchangés (350 + 50 par catégorie + 20 par grade).
-- Contractuels : 300 + 50 par catégorie + 15 par échelon (A11E 300 … A19E 420 en catégorie I).
-- Stagiaires : 250 + 50 par catégorie.

WITH rang_categorie(code, rang) AS (VALUES
    ('I', 1), ('II', 2), ('III', 3), ('IV', 4), ('V', 5),
    ('VI', 6), ('VII', 7), ('VIII', 8), ('IX', 9), ('X', 10)
),
regle(parcours_code, base, ecart_categorie, pas_grade) AS (VALUES
    ('AUXILIAIRE_ECHELLE_I', 300, 50, 15),
    ('STAGE', 250, 50, 0)
),
grade AS (
    SELECT ec.id AS echelon_id, pa.code AS parcours_code,
           ROW_NUMBER() OVER (PARTITION BY pa.id ORDER BY cl.ordre, ec.ordre) AS rang
    FROM echelon ec
    JOIN classe cl ON cl.id = ec.classe_id
    JOIN parcours pa ON pa.id = cl.parcours_id
)
UPDATE grille_indiciaire gi
SET indice = r.base + r.ecart_categorie * (rc.rang - 1) + r.pas_grade * (g.rang - 1)
FROM categorie ca
JOIN rang_categorie rc ON rc.code = ca.code
CROSS JOIN grade g
JOIN regle r ON r.parcours_code = g.parcours_code
WHERE gi.categorie_id = ca.id
  AND gi.echelon_id = g.echelon_id;

-- La demande de titularisation : passage d'un contractuel ou d'un stagiaire au statut de fonctionnaire.
INSERT INTO type_document (code, libelle, description)
VALUES ('TITULARISATION', 'Demande de titularisation',
        'Passage d''un agent contractuel ou stagiaire au statut de fonctionnaire.')
ON CONFLICT (code) DO NOTHING;
