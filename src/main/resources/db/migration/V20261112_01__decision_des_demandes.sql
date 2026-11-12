-- Une demande ne sert d'abord qu'à produire la lettre, signée et tamponnée hors
-- de l'application. La réponse reçue sur papier s'enregistre ensuite : la demande
-- est ACCEPTE ou REFUSE, avec le signataire, la référence de l'acte, sa date et
-- l'utilisateur qui l'a saisie. Seule une demande acceptée s'applique, à la date
-- d'effet que l'accord fixe — elle peut précéder la demande (accord rétroactif).
--
-- Cycle : CREE → ACCEPTE → APPLIQUE → ARCHIVE
--         CREE → REFUSE → ARCHIVE
--         CREE → ANNULE → ARCHIVE

ALTER TABLE document
    ADD COLUMN decision_acceptee BOOLEAN,
    ADD COLUMN decision_signataire VARCHAR(255),
    ADD COLUMN decision_reference VARCHAR(150),
    ADD COLUMN decision_date DATE,
    ADD COLUMN decision_motif TEXT,
    ADD COLUMN decision_saisie_par INTEGER REFERENCES utilisateur(id) ON DELETE SET NULL,
    ADD COLUMN decision_saisie_le TIMESTAMPTZ;

ALTER TABLE document DROP CONSTRAINT ck_document_statut;
ALTER TABLE document ADD CONSTRAINT ck_document_statut
    CHECK (statut IN ('CREE', 'ACCEPTE', 'REFUSE', 'APPLIQUE', 'ANNULE', 'ARCHIVE'));

-- Une décision a toujours un sens, un signataire et une date ; elle reste lisible une fois archivée.
ALTER TABLE document ADD CONSTRAINT ck_document_decision
    CHECK ((statut <> 'ACCEPTE' OR decision_acceptee IS TRUE)
           AND (statut <> 'REFUSE' OR decision_acceptee IS FALSE)
           AND (decision_acceptee IS NULL
                OR (decision_signataire IS NOT NULL AND decision_date IS NOT NULL)));

-- La date d'effet vient désormais de l'accord, qui peut être rétroactif.
ALTER TABLE document DROP CONSTRAINT ck_document_dates;
