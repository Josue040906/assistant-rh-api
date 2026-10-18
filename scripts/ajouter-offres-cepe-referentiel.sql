BEGIN;

INSERT INTO etablissement (nom, ville, actif, statut_validation)
VALUES
    ('EPP d''Antananarivo', 'Antananarivo', TRUE, 'VALIDE'),
    ('EPP d''Antsirabe', 'Antsirabe', TRUE, 'VALIDE'),
    ('EPP d''Antsiranana', 'Antsiranana', TRUE, 'VALIDE'),
    ('EPP de Fianarantsoa', 'Fianarantsoa', TRUE, 'VALIDE'),
    ('EPP de Mahajanga', 'Mahajanga', TRUE, 'VALIDE'),
    ('EPP de Morondava', 'Morondava', TRUE, 'VALIDE'),
    ('EPP de Toamasina', 'Toamasina', TRUE, 'VALIDE'),
    ('EPP de Toliara', 'Toliara', TRUE, 'VALIDE')
ON CONFLICT (lower(btrim(nom))) DO NOTHING;

INSERT INTO offre_formation (niveau_id, domaine_id, etablissement_id, actif)
SELECT n.id, d.id, e.id, TRUE
FROM (VALUES
    ('EPP d''Antananarivo'),
    ('EPP d''Antsirabe'),
    ('EPP d''Antsiranana'),
    ('EPP de Fianarantsoa'),
    ('EPP de Mahajanga'),
    ('EPP de Morondava'),
    ('EPP de Toamasina'),
    ('EPP de Toliara')
) AS epp(nom)
JOIN etablissement e
    ON lower(btrim(e.nom)) = lower(btrim(epp.nom))
   AND e.actif
   AND e.statut_validation = 'VALIDE'
JOIN niveau_diplome n ON n.code = 'CEPE'
JOIN domaine_etude d ON d.libelle = 'Enseignement général' AND d.actif
ON CONFLICT (niveau_id, domaine_id, etablissement_id)
DO UPDATE SET actif = TRUE;

COMMIT;
