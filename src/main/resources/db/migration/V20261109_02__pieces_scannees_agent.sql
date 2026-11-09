-- Une pièce scannée peut aussi être attachée à l'agent lui-même (par exemple
-- sa CIN, d'où viennent les champs de sa fiche). Aucune donnée n'est modifiée.
ALTER TABLE piece_scannee DROP CONSTRAINT IF EXISTS piece_scannee_cible_type_check;
ALTER TABLE piece_scannee ADD CONSTRAINT piece_scannee_cible_type_check
    CHECK (cible_type IN ('DOCUMENT', 'DIPLOME', 'AGENT'));
