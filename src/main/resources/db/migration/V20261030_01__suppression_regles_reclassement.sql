-- Remove the obsolete administrative mapping table. Automatic reclassement
-- derives its target category and corps from niveau_diplome.categorie_id and
-- continues to rank diplomas using niveau_diplome.ordre_academique.
DROP TABLE IF EXISTS regle_reclassement;
