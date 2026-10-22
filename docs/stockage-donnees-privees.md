# Stockage prive des donnees RH

Ne versionnez pas les photos d'employes, documents RH, exports de production ni
sauvegardes de bases contenant des donnees personnelles. Conservez-les dans un
stockage prive avec des controles d'acces adaptes; chiffrez les sauvegardes et
limitez leur retention. Gardez les migrations et scripts de reference non
sensibles dans Git.

Les regles `.gitignore` evitent certains ajouts accidentels, mais ne retirent
pas les fichiers deja suivis ni leur historique. En cas d'exposition, traitez
les clones et forks comme des copies persistantes et faites tourner les
identifiants compromis.
