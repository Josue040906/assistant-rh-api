-- Une alerte de carrière de plus dans la cloche : le départ à la retraite devient
-- possible dans moins de six mois (l'e-mail « retraite proche » existait déjà).
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'NOUVEL_AGENT', 'ANCIENNETE_UN_MOIS', 'ANCIENNETE_ATTEINTE', 'RETRAITE_PROCHE',
    'DOCUMENT_ACCEPTE', 'DOCUMENT_REFUSE', 'DOCUMENT_APPLIQUE', 'DOCUMENT_APPLICABLE'
));
