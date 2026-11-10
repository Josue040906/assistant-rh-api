-- Les connexions et déconnexions ne sont plus des activités : elles encombraient
-- le journal sans rien dire du travail RH. Les entrées existantes sont retirées.
DELETE FROM activite WHERE type_action IN ('CONNEXION', 'DECONNEXION');
