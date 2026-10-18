BEGIN;

DO $$
DECLARE
    lignes_modifiees INTEGER;
BEGIN
    UPDATE etablissement AS e
    SET ville = v.ville
    FROM (VALUES
        ('Institut Supérieur de Gestion d''Antananarivo', 'Antananarivo'),
        ('Institut Supérieur de Technologie', 'Antananarivo'),
        ('Lycée d''Antananarivo', 'Antananarivo'),
        ('Lycée d''Antsirabe', 'Antsirabe'),
        ('Lycée de Fianarantsoa', 'Fianarantsoa'),
        ('Lycée de Mahajanga', 'Mahajanga'),
        ('Lycée de Toamasina', 'Toamasina'),
        ('Lycée Moderne d''Antananarivo', 'Antananarivo'),
        ('Lycée Toamasina', 'Toamasina'),
        ('Université d''Antananarivo', 'Antananarivo'),
        ('Université de Fianarantsoa', 'Fianarantsoa'),
        ('Université de Toamasina', 'Toamasina')
    ) AS v(nom, ville)
    WHERE lower(btrim(e.nom)) = lower(btrim(v.nom));

    GET DIAGNOSTICS lignes_modifiees = ROW_COUNT;
    IF lignes_modifiees <> 12 THEN
        RAISE EXCEPTION
            'Expected to update 12 historical diploma institutions, updated %.',
            lignes_modifiees;
    END IF;
END
$$;

COMMIT;
