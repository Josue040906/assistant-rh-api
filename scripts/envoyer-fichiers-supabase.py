"""Envoie les fichiers du dossier uploads/ (photos, pièces scannées, PDF) vers
le compartiment PRIVÉ de Supabase Storage utilisé par l'API en ligne.

Les liens déjà enregistrés dans la base (« /uploads/employes/agent-… ») restent
valables : chaque fichier garde son nom, sous le même dossier.

À lancer depuis la racine de l'API, avec les mêmes valeurs que dans Render :

    $env:SUPABASE_URL = 'https://xxxx.supabase.co'
    $env:SUPABASE_SECRET_KEY = '...'
    $env:SUPABASE_BUCKET = 'sygpers'      # facultatif, « sygpers » par défaut
    python scripts/envoyer-fichiers-supabase.py

Rejouable : un fichier déjà présent est remplacé par celui du dossier local.
"""
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

DOSSIERS = ('employes', 'pieces', 'documents')
NOM = re.compile(r'^[A-Za-z0-9][A-Za-z0-9._-]*$')
TYPES = {'.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp', '.pdf': 'application/pdf'}


def main():
    url = os.environ.get('SUPABASE_URL', '').strip().rstrip('/')
    cle = os.environ.get('SUPABASE_SECRET_KEY', '').strip()
    compartiment = os.environ.get('SUPABASE_BUCKET', 'sygpers').strip()
    if not url or not cle:
        sys.exit('Renseignez SUPABASE_URL et SUPABASE_SECRET_KEY (voir l\'en-tête du script).')

    racine = Path('uploads')
    envoyes, ignores, erreurs = 0, 0, 0
    for dossier in DOSSIERS:
        for fichier in sorted((racine / dossier).glob('*')):
            type_mime = TYPES.get(fichier.suffix.lower())
            if not fichier.is_file() or not NOM.match(fichier.name) or type_mime is None:
                ignores += 1
                continue
            requete = urllib.request.Request(
                f'{url}/storage/v1/object/{compartiment}/{dossier}/{fichier.name}',
                data=fichier.read_bytes(), method='POST')
            requete.add_header('apikey', cle)
            if cle.startswith('eyJ'):
                requete.add_header('Authorization', 'Bearer ' + cle)
            requete.add_header('Content-Type', type_mime)
            requete.add_header('x-upsert', 'true')
            try:
                with urllib.request.urlopen(requete, timeout=60):
                    envoyes += 1
            except urllib.error.HTTPError as erreur:
                erreurs += 1
                print(f'  refusé : {dossier}/{fichier.name} (HTTP {erreur.code}) {erreur.read()[:200]!r}')
            except urllib.error.URLError as erreur:
                sys.exit(f'Supabase injoignable : {erreur.reason}')
        print(f'{dossier} : terminé')
    print(f'\n{envoyes} fichier(s) envoyé(s), {ignores} ignoré(s), {erreurs} refusé(s).')


if __name__ == '__main__':
    main()
