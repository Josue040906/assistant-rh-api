-- Audit des dates enregistrées : chaque ligne est une incohérence (aucune ligne = tout est cohérent).
-- L'âge minimum vient du type d'emploi (type_emploi.age_minimum_embauche).
SELECT 'embauché trop jeune' AS anomalie, e.matricule, e.date_naissance::text AS d1, e.date_embauche::text AS d2
FROM employe e JOIN type_emploi te ON te.id = e.type_emploi_id
WHERE e.date_embauche < e.date_naissance + make_interval(years => te.age_minimum_embauche)
UNION ALL SELECT 'embauche future', e.matricule, e.date_embauche::text, NULL
FROM employe e WHERE e.date_embauche > CURRENT_DATE
UNION ALL SELECT 'diplôme avant naissance', e.matricule, e.date_naissance::text, d.date_obtention::text
FROM employe_diplome d JOIN employe e ON e.id = d.employe_id WHERE d.date_obtention < e.date_naissance
UNION ALL SELECT 'diplôme futur', e.matricule, d.date_obtention::text, NULL
FROM employe_diplome d JOIN employe e ON e.id = d.employe_id WHERE d.date_obtention > CURRENT_DATE
UNION ALL SELECT 'carrière avant embauche', e.matricule, e.date_embauche::text, min(h.date_debut)::text
FROM historique_carriere h JOIN employe e ON e.id = h.employe_id GROUP BY e.id HAVING min(h.date_debut) < e.date_embauche
UNION ALL SELECT 'affectation avant embauche', e.matricule, e.date_embauche::text, min(a.date_debut)::text
FROM affectation a JOIN employe e ON e.id = a.employe_id GROUP BY e.id HAVING min(a.date_debut) < e.date_embauche
UNION ALL SELECT 'période fin < début', e.matricule, h.date_debut::text, h.date_fin::text
FROM historique_carriere h JOIN employe e ON e.id = h.employe_id WHERE h.date_fin < h.date_debut
UNION ALL SELECT 'retraite avant embauche', e.matricule, e.date_embauche::text, e.date_retraite::text
FROM employe e WHERE e.date_retraite < e.date_embauche
UNION ALL SELECT 'congé hors service', e.matricule, c.date_debut::text, c.date_fin::text
FROM conge c JOIN employe e ON e.id = c.employe_id WHERE c.date_debut < e.date_embauche OR c.date_fin > e.date_retraite
UNION ALL SELECT 'périodes qui se chevauchent', e.matricule, h1.date_debut::text, h2.date_debut::text
FROM historique_carriere h1
JOIN historique_carriere h2 ON h1.employe_id = h2.employe_id AND h1.id < h2.id
     AND h1.date_debut <= COALESCE(h2.date_fin, DATE '9999-12-31')
     AND h2.date_debut <= COALESCE(h1.date_fin, DATE '9999-12-31')
JOIN employe e ON e.id = h1.employe_id
UNION ALL SELECT 'réponse avant la demande', d.reference_document, d.date_document::text, d.decision_date::text
FROM document d WHERE d.decision_date < d.date_document
ORDER BY 1, 2;
