"""Portraits ILLUSTRÉS des profils d'essai (agents ESSAI-*), posés comme photo de profil.

Ce ne sont pas des photos de personnes réelles : un buste dessiné, dont le fond
reprend la couleur du type d'emploi (vert fonctionnaire, ambre contractuel, bleu
stagiaire). À lancer après scripts/profils-essai.sql, depuis la racine de l'API :

    python scripts/photos-profils-essai.py

Variables : PGHOST (localhost), PGPORT (5433), PGUSER (postgres), PGDATABASE (assistant_rh).
"""
import os
import random
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw

DOSSIER = Path('uploads/employes')
FONDS = {'Fonctionnaire': (214, 232, 220), 'Agent contractuel': (243, 228, 202), 'Stagiaire': (214, 226, 238)}
PEAUX = [(141, 85, 54), (166, 106, 70), (120, 72, 46), (188, 128, 86), (98, 60, 40)]
CHEVEUX = [(28, 22, 20), (45, 32, 26), (60, 44, 34), (20, 18, 18)]
HABITS = [(46, 84, 120), (120, 52, 48), (52, 96, 74), (88, 70, 120), (150, 110, 50), (60, 60, 70)]


def psql(sql):
    commande = ['psql', '-h', os.environ.get('PGHOST', 'localhost'), '-p', os.environ.get('PGPORT', '5433'),
                '-U', os.environ.get('PGUSER', 'postgres'), '-d', os.environ.get('PGDATABASE', 'assistant_rh'),
                '-At', '-F', '|', '-c', sql]
    return subprocess.run(commande, check=True, capture_output=True, text=True, encoding='utf-8').stdout


def portrait(matricule, sexe, type_emploi, age):
    hasard = random.Random(matricule)
    taille = 512
    image = Image.new('RGB', (taille, taille), FONDS.get(type_emploi, (225, 225, 225)))
    d = ImageDraw.Draw(image)
    peau, cheveux, habit = hasard.choice(PEAUX), hasard.choice(CHEVEUX), hasard.choice(HABITS)
    if age >= 58:
        cheveux = (150, 146, 140)
    femme = sexe == 'F'

    # Cheveux longs derrière la tête.
    if femme:
        d.rounded_rectangle((150, 120, 362, 380), radius=100, fill=cheveux)
    # Buste et col.
    d.ellipse((86, 360, 426, 700), fill=habit)
    d.polygon([(220, 372), (256, 430), (292, 372)], fill=(245, 242, 236))
    d.rectangle((228, 300, 284, 380), fill=peau)
    # Tête, oreilles.
    d.ellipse((166, 140, 346, 336), fill=peau)
    d.ellipse((152, 222, 182, 266), fill=peau)
    d.ellipse((330, 222, 360, 266), fill=peau)
    # Cheveux sur le haut de la tête.
    if femme:
        d.chord((160, 120, 352, 280), 180, 360, fill=cheveux)
    else:
        d.chord((164, 128, 348, 262), 185, 355, fill=cheveux)
    # Yeux, sourcils, sourire.
    for x in (218, 294):
        d.ellipse((x - 9, 236, x + 9, 254), fill=(30, 24, 22))
        d.line((x - 16, 222, x + 14, 220), fill=cheveux, width=5)
    d.arc((222, 262, 290, 306), 20, 160, fill=(90, 40, 36), width=5)
    if age >= 50 and not femme:
        d.rounded_rectangle((200, 230, 236, 260), radius=8, outline=(40, 40, 44), width=4)
        d.rounded_rectangle((276, 230, 312, 260), radius=8, outline=(40, 40, 44), width=4)
        d.line((236, 244, 276, 244), fill=(40, 40, 44), width=4)
    return image


def main():
    DOSSIER.mkdir(parents=True, exist_ok=True)
    lignes = psql("SELECT e.id, e.matricule, e.sexe, te.nom, date_part('year', age(e.date_naissance))::int "
                  "FROM employe e JOIN type_emploi te ON te.id = e.type_emploi_id "
                  "WHERE e.matricule LIKE 'ESSAI-%' ORDER BY e.matricule")
    for ligne in filter(None, lignes.splitlines()):
        identifiant, matricule, sexe, type_emploi, age = ligne.split('|')
        nom = f'agent-{identifiant}-essai.png'
        portrait(matricule, sexe, type_emploi, int(age)).save(DOSSIER / nom, optimize=True)
        psql(f"UPDATE employe SET photo = '/uploads/employes/{nom}' WHERE id = {int(identifiant)}")
        print(matricule, '->', nom)


if __name__ == '__main__':
    main()
