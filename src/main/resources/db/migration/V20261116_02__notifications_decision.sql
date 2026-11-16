-- La réponse signée d'une demande notifie l'auteur et le chef : DOCUMENT_ACCEPTE ou
-- DOCUMENT_REFUSE. Ces deux types manquaient à la contrainte : la saisie d'une réponse
-- échouait (503) dès qu'une autre personne que celle qui la saisit devait être notifiée.
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'NOUVEL_AGENT', 'ANCIENNETE_UN_MOIS', 'ANCIENNETE_ATTEINTE',
    'DOCUMENT_ACCEPTE', 'DOCUMENT_REFUSE', 'DOCUMENT_APPLIQUE', 'DOCUMENT_APPLICABLE'));
