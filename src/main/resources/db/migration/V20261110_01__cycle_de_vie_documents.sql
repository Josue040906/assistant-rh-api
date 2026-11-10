-- Cycle de vie des documents : BROUILLON → VALIDE (par son auteur, l'effet
-- s'applique) ou ANNULE ; VALIDE ou ANNULE → ARCHIVE. Les statuts « à vérifier »
-- et « signé » disparaissent. Le document garde le compte qui l'a créé.
ALTER TABLE document ADD COLUMN IF NOT EXISTS auteur_id INTEGER REFERENCES utilisateur(id) ON DELETE SET NULL;

-- Aucun document n'est dans ces statuts aujourd'hui ; par sûreté :
UPDATE document SET statut = 'BROUILLON' WHERE statut = 'A_VERIFIER';
UPDATE document SET statut = 'VALIDE' WHERE statut = 'SIGNE';

ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_document_statut;
ALTER TABLE document ADD CONSTRAINT ck_document_statut
    CHECK (statut IN ('BROUILLON', 'VALIDE', 'ANNULE', 'ARCHIVE'));

-- Le chef du Service du Personnel est notifié de chaque validation.
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('NOUVEL_AGENT', 'ANCIENNETE_UN_MOIS', 'ANCIENNETE_ATTEINTE', 'DOCUMENT_VALIDE'));
