-- Lot 4 : une demande est Créée, devient applicable à sa date d'effet, puis est
-- Appliquée par un humain (son effet est alors produit). « Applicable » n'est
-- pas un statut enregistré : c'est CREE avec une date d'effet atteinte.

-- 1. Statuts : BROUILLON → CREE, VALIDE → APPLIQUE.
ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_document_statut;
UPDATE document SET statut = 'CREE' WHERE statut = 'BROUILLON';
UPDATE document SET statut = 'APPLIQUE' WHERE statut = 'VALIDE';
ALTER TABLE document ADD CONSTRAINT ck_document_statut
    CHECK (statut IN ('CREE', 'APPLIQUE', 'ANNULE', 'ARCHIVE'));

-- 2. Chaque demande créée a une date d'effet : celle qu'elle demande, jamais
--    avant la date du document ; à défaut, la date du document.
UPDATE document d
SET date_effet = GREATEST(
        COALESCE(
            CASE t.code
                WHEN 'CONGE' THEN NULLIF(d.donnees ->> 'dateDebut', '')::date
                WHEN 'MUTATION' THEN NULLIF(d.donnees ->> 'dateEffetSouhaitee', '')::date
                WHEN 'RETRAITE' THEN NULLIF(d.donnees ->> 'dateDepartSouhaitee', '')::date
            END,
            d.date_document,
            d.date_creation::date
        ),
        COALESCE(d.date_document, d.date_creation::date)
    )
FROM type_document t
WHERE t.id = d.type_document_id
  AND d.statut = 'CREE'
  AND d.date_effet IS NULL;

-- 3. Notifications : « appliquée » remplace « validée » ; « applicable » prévient l'auteur.
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
UPDATE notification SET type = 'DOCUMENT_APPLIQUE' WHERE type = 'DOCUMENT_VALIDE';
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'NOUVEL_AGENT', 'ANCIENNETE_UN_MOIS', 'ANCIENNETE_ATTEINTE',
    'DOCUMENT_APPLIQUE', 'DOCUMENT_APPLICABLE'));

-- 4. Durée de service exigée par un type de demande : une donnée, pas une règle du code.
ALTER TABLE type_document ADD COLUMN IF NOT EXISTS anciennete_service_min_annees INTEGER
    CHECK (anciennete_service_min_annees IS NULL OR anciennete_service_min_annees > 0);
UPDATE type_document SET anciennete_service_min_annees = 25 WHERE code = 'RETRAITE';

-- 5. Date à laquelle l'agent est parti à la retraite.
ALTER TABLE employe ADD COLUMN IF NOT EXISTS date_retraite DATE;
