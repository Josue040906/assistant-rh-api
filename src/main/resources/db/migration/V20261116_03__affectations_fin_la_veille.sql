-- Une affectation se termine la veille de la suivante, comme une période de carrière :
-- l'agent n'occupe jamais deux postes le même jour. Les affectations closes le jour
-- même où la suivante commence sont ramenées à la veille. Rejouable.
UPDATE affectation a
SET date_fin = a.date_fin - 1
WHERE a.date_fin IS NOT NULL
  AND a.date_fin > a.date_debut
  AND EXISTS (
      SELECT 1 FROM affectation b
      WHERE b.employe_id = a.employe_id AND b.id <> a.id AND b.date_debut = a.date_fin
  );
