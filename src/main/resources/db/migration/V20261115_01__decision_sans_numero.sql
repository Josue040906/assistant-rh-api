-- La réponse à une demande ne garde que son signataire (et sa date) : le numéro
-- de la décision n'est plus demandé. Le texte des décisions déjà appliquées
-- reste lisible dans la carrière (avancement.reference_acte, activités).

ALTER TABLE document DROP COLUMN IF EXISTS decision_reference;
