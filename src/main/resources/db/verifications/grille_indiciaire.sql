-- Vérifications de la grille indiciaire (chaque ligne doit compter 0 anomalie).
-- 1. Même catégorie, même parcours : un grade plus haut a un indice plus haut.
-- 2. Même grade : une catégorie plus haute a un indice plus haut.
-- 3. Même catégorie, à l'entrée de chaque parcours : fonctionnaire > contractuel > stagiaire.
-- 4. Titularisation : un contractuel plus avancé ne reçoit jamais un grade de fonctionnaire
--    plus bas qu'un contractuel moins avancé (premier indice égal ou supérieur).
WITH g AS (
    SELECT gi.indice, ca.id AS cat, ec.id AS ech, pa.code AS parcours, te.nom AS type_emploi,
           ROW_NUMBER() OVER (PARTITION BY ca.id, pa.id ORDER BY cl.ordre, ec.ordre) AS rang_grade,
           ca.code AS categorie, ec.code AS grade
    FROM grille_indiciaire gi
    JOIN categorie ca ON ca.id = gi.categorie_id
    JOIN echelon ec ON ec.id = gi.echelon_id
    JOIN classe cl ON cl.id = ec.classe_id
    JOIN parcours pa ON pa.id = cl.parcours_id
    JOIN type_emploi te ON te.id = pa.type_emploi_id
),
rang_type(type_emploi, rang) AS (VALUES ('Stagiaire', 1), ('Agent contractuel', 2), ('Fonctionnaire', 3)),
cible AS (
    SELECT a.cat, a.grade, a.rang_grade, a.parcours,
           (SELECT MIN(f.rang_grade) FROM g f
            WHERE f.cat = a.cat AND f.type_emploi = 'Fonctionnaire' AND f.indice >= a.indice) AS rang_cible
    FROM g a WHERE a.type_emploi <> 'Fonctionnaire'
)
SELECT '1. même catégorie, grade plus haut, indice pas plus haut' AS verification, count(*) AS anomalies,
       string_agg(DISTINCT a.categorie || ' ' || a.grade || '>' || b.grade, ', ') AS exemples
FROM g a JOIN g b ON a.cat = b.cat AND a.parcours = b.parcours AND a.rang_grade > b.rang_grade
WHERE a.indice <= b.indice
UNION ALL
SELECT '2. même grade, catégorie plus haute, indice pas plus haut', count(*),
       string_agg(DISTINCT a.grade || ' ' || a.categorie || '>' || b.categorie, ', ')
FROM g a JOIN g b ON a.ech = b.ech AND a.cat > b.cat
WHERE a.indice <= b.indice
UNION ALL
SELECT '3. entrée de parcours, type d''emploi plus haut, indice pas plus haut', count(*),
       string_agg(DISTINCT a.categorie || ' ' || a.grade || ' <= ' || b.grade, ', ')
FROM g a JOIN g b ON a.cat = b.cat AND a.rang_grade = 1 AND b.rang_grade = 1
JOIN rang_type ta ON ta.type_emploi = a.type_emploi
JOIN rang_type tb ON tb.type_emploi = b.type_emploi
WHERE ta.rang > tb.rang AND a.indice <= b.indice
UNION ALL
SELECT '4. titularisation, plus avancé mais grade cible plus bas', count(*),
       string_agg(DISTINCT a.grade || ' → rang ' || a.rang_cible, ', ')
FROM cible a JOIN cible b ON a.cat = b.cat AND a.parcours = b.parcours AND a.rang_grade > b.rang_grade
WHERE a.rang_cible < b.rang_cible
UNION ALL
SELECT 'cases de la grille', count(*), NULL FROM g;
