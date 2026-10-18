BEGIN;

INSERT INTO etablissement (nom, ville, actif, statut_validation)
VALUES
    ('CEG d''Antananarivo', 'Antananarivo', TRUE, 'VALIDE'),
    ('CEG d''Antsirabe', 'Antsirabe', TRUE, 'VALIDE'),
    ('CEG de Fianarantsoa', 'Fianarantsoa', TRUE, 'VALIDE'),
    ('CEG de Mahajanga', 'Mahajanga', TRUE, 'VALIDE'),
    ('CEG de Toamasina', 'Toamasina', TRUE, 'VALIDE'),
    ('CEG de Toliara', 'Toliara', TRUE, 'VALIDE'),
    ('CEG d''Antsiranana', 'Antsiranana', TRUE, 'VALIDE'),
    ('CEG de Morondava', 'Morondava', TRUE, 'VALIDE'),
    ('Lycée public d''Antananarivo', 'Antananarivo', TRUE, 'VALIDE'),
    ('Lycée public d''Antsirabe', 'Antsirabe', TRUE, 'VALIDE'),
    ('Lycée public de Fianarantsoa', 'Fianarantsoa', TRUE, 'VALIDE'),
    ('Lycée public de Mahajanga', 'Mahajanga', TRUE, 'VALIDE'),
    ('Lycée public de Toamasina', 'Toamasina', TRUE, 'VALIDE'),
    ('Lycée public de Toliara', 'Toliara', TRUE, 'VALIDE'),
    ('Lycée public d''Antsiranana', 'Antsiranana', TRUE, 'VALIDE'),
    ('Lycée public de Morondava', 'Morondava', TRUE, 'VALIDE')
ON CONFLICT (lower(btrim(nom))) DO NOTHING;

INSERT INTO offre_formation (niveau_id, domaine_id, etablissement_id, actif)
SELECT n.id, d.id, e.id, TRUE
FROM (VALUES
    ('CEG d''Antananarivo', 'BEPC'),
    ('CEG d''Antsirabe', 'BEPC'),
    ('CEG de Fianarantsoa', 'BEPC'),
    ('CEG de Mahajanga', 'BEPC'),
    ('CEG de Toamasina', 'BEPC'),
    ('CEG de Toliara', 'BEPC'),
    ('CEG d''Antsiranana', 'BEPC'),
    ('CEG de Morondava', 'BEPC'),
    ('Lycée public d''Antananarivo', 'BACCALAUREAT'),
    ('Lycée public d''Antsirabe', 'BACCALAUREAT'),
    ('Lycée public de Fianarantsoa', 'BACCALAUREAT'),
    ('Lycée public de Mahajanga', 'BACCALAUREAT'),
    ('Lycée public de Toamasina', 'BACCALAUREAT'),
    ('Lycée public de Toliara', 'BACCALAUREAT'),
    ('Lycée public d''Antsiranana', 'BACCALAUREAT'),
    ('Lycée public de Morondava', 'BACCALAUREAT')
) AS offre(nom_etablissement, code_niveau)
JOIN etablissement e
    ON lower(btrim(e.nom)) = lower(btrim(offre.nom_etablissement))
   AND e.actif
   AND e.statut_validation = 'VALIDE'
JOIN niveau_diplome n ON n.code = offre.code_niveau
JOIN domaine_etude d ON d.libelle = 'Enseignement général' AND d.actif
ON CONFLICT (niveau_id, domaine_id, etablissement_id)
DO UPDATE SET actif = TRUE;

COMMIT;
