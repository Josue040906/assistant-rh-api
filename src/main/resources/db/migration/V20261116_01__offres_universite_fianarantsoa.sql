-- Formations de l'Université de Fianarantsoa : Licence et Master en Droit, Économie,
-- Gestion (faculté DEGS) et Informatique (dont l'ENI). Jusqu'ici, seules la Licence en
-- Droit et la Licence en Gestion étaient enregistrées : un diplôme de Licence en
-- Informatique de Fianarantsoa ne trouvait pas son établissement.
-- Rejouable : une offre déjà présente n'est pas dupliquée (contrainte uq_offre_formation).
INSERT INTO offre_formation (niveau_id, domaine_id, etablissement_id)
SELECT n.id, d.id, e.id
FROM etablissement e
CROSS JOIN niveau_diplome n
CROSS JOIN domaine_etude d
WHERE e.nom = 'Université de Fianarantsoa'
  AND n.code IN ('LICENCE', 'MASTER')
  AND d.libelle IN ('Droit', 'Économie', 'Gestion', 'Informatique')
ON CONFLICT ON CONSTRAINT uq_offre_formation DO NOTHING;
