#!/usr/bin/env python3
"""Import GeoNames populated places for Madagascar into PostgreSQL."""

import argparse
import csv
import http.client
import io
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile


SOURCE_URLS = (
    "https://download.geonames.org/export/dump/MG.zip",
    "https://download.geonames.org/export/dump/cities500.zip",
)
NOMBRE_TENTATIVES_PAR_SOURCE = 4
EXCLUSIONS = {"PPLH", "PPLQ", "PPLW", "PPLX"}
VILLES_ATTENDUES = {"antananarivo", "fianarantsoa", "ihosy"}
TAILLE_BLOC = 64 * 1024
SIEGES_ADMINISTRATIFS = {"PPLC", "PPLA", "PPLA2"}
POPULATION_MINIMALE_VILLE = 5000


def extraire_villes(fichier):
    villes_par_nom = {}
    with zipfile.ZipFile(fichier) as archive:
        fichiers_texte = [
            nom
            for nom in archive.namelist()
            if nom.lower().endswith(".txt")
        ]
        fichier_donnees = next(
            (
                nom for nom in fichiers_texte
                if os.path.basename(nom).casefold()
                in {"mg.txt", "cities500.txt"}
            ),
            None,
        )
        if fichier_donnees is None:
            raise ValueError(
                "L'archive GeoNames ne contient ni MG.txt ni cities500.txt."
            )
        with archive.open(fichier_donnees) as source:
            lignes = io.TextIOWrapper(source, encoding="utf-8")
            for champs in csv.reader(lignes, delimiter="\t"):
                if len(champs) < 15:
                    raise ValueError("Une ligne GeoNames est incomplète.")
                geoname_id, nom = champs[0], champs[1].strip()
                classe, code, pays = champs[6], champs[7], champs[8]
                try:
                    population = int(champs[14] or 0)
                except ValueError as error:
                    raise ValueError(
                        f"Population GeoNames invalide pour {geoname_id}."
                    ) from error
                if (
                    pays == "MG"
                    and classe == "P"
                    and code not in EXCLUSIONS
                    and nom
                    and (
                        code in SIEGES_ADMINISTRATIFS
                        or (
                            code == "PPL"
                            and population >= POPULATION_MINIMALE_VILLE
                        )
                    )
                ):
                    cle = nom.casefold()
                    rang_type = 0 if code in SIEGES_ADMINISTRATIFS else 1
                    candidat = (
                        rang_type,
                        -population,
                        int(geoname_id),
                        ("geonames", geoname_id, nom),
                    )
                    precedent = villes_par_nom.get(cle)
                    if precedent is None or candidat[:3] < precedent[:3]:
                        villes_par_nom[cle] = candidat

    villes = sorted(
        (candidat[3] for candidat in villes_par_nom.values()),
        key=lambda ville: (ville[2].casefold(), ville[2]),
    )
    if not villes:
        raise ValueError(
            "GeoNames ne contient aucune localité habitée à Madagascar."
        )
    noms = {nom.casefold() for _, _, nom in villes}
    absentes = VILLES_ATTENDUES - noms
    if absentes:
        raise ValueError(
            "L'extrait GeoNames ne contient pas les localités attendues : "
            + ", ".join(sorted(absentes))
        )
    return villes


def telecharger_villes(urls=SOURCE_URLS):
    erreurs = []
    for url in urls:
        with tempfile.NamedTemporaryFile(
            prefix="geonames_mg_", suffix=".zip", delete=False
        ) as temporaire:
            chemin = temporaire.name
        attendu = None
        try:
            for tentative in range(1, NOMBRE_TENTATIVES_PAR_SOURCE + 1):
                telecharge = os.path.getsize(chemin)
                try:
                    headers = {"User-Agent": "AssistantRH-GeoNamesImporter/1.0"}
                    if telecharge:
                        headers["Range"] = f"bytes={telecharge}-"
                    request = urllib.request.Request(url, headers=headers)
                    with urllib.request.urlopen(request, timeout=20) as response:
                        statut = getattr(response, "status", 200)
                        if telecharge and statut == 206:
                            content_range = response.headers.get(
                                "Content-Range", ""
                            )
                            if not content_range.startswith(
                                    f"bytes {telecharge}-"
                            ):
                                raise ValueError(
                                    "Le serveur a retourné une plage d'octets incohérente."
                                )
                        elif statut == 200:
                            telecharge = 0
                            attendu = None
                        elif telecharge:
                            raise ValueError(
                                f"Réponse HTTP {statut} pour la reprise du téléchargement."
                            )
                        taille_reponse = response.headers.get("Content-Length")
                        if taille_reponse is not None:
                            taille_reponse = int(taille_reponse)
                            attendu = (
                                telecharge + taille_reponse
                                if statut == 206
                                else taille_reponse
                            )
                        type_contenu = response.headers.get("Content-Type", "")
                        if (
                            "zip" not in type_contenu.lower()
                            and not type_contenu.startswith(
                                "application/octet-stream"
                            )
                        ):
                            extrait = response.read(120)
                            raise ValueError(
                                f"Réponse non-archive ({type_contenu or 'type inconnu'}), "
                                f"début={extrait!r}"
                            )
                        with open(chemin, "ab" if telecharge else "wb") as sortie:
                            while bloc := response.read(TAILLE_BLOC):
                                sortie.write(bloc)
                                total = os.path.getsize(chemin)
                                if attendu:
                                    pourcentage = min(100, total * 100 // attendu)
                                    print(
                                        f"Téléchargement GeoNames : {total:,} / "
                                        f"{attendu:,} octets ({pourcentage} %)",
                                        end="\r",
                                        flush=True,
                                    )

                    taille_finale = os.path.getsize(chemin)
                    if attendu is not None and taille_finale != attendu:
                        raise EOFError(
                            f"téléchargement incomplet ({taille_finale} / "
                            f"{attendu} octets)"
                        )
                    villes = extraire_villes(chemin)
                    print(f"\nSource GeoNames téléchargée : {url}")
                    return villes
                except (
                    OSError,
                    urllib.error.URLError,
                    http.client.HTTPException,
                    ValueError,
                    EOFError,
                    zipfile.BadZipFile,
                ) as error:
                    erreurs.append(f"{url} (essai {tentative}) : {error}")
                    print(
                        f"\nÉchec GeoNames (essai "
                        f"{tentative}/{NOMBRE_TENTATIVES_PAR_SOURCE}) : {error}",
                        file=sys.stderr,
                    )
                    if tentative < NOMBRE_TENTATIVES_PAR_SOURCE:
                        time.sleep(2)
        finally:
            if os.path.exists(chemin):
                os.unlink(chemin)

    raise RuntimeError(
        "Tous les téléchargements GeoNames ont échoué. Réessayez plus tard. "
        + " ; ".join(erreurs)
    )


def construire_script_psql(villes):
    donnees = io.StringIO(newline="")
    writer = csv.writer(donnees, lineterminator="\n")
    writer.writerow(["source_type", "source_id", "nom"])
    writer.writerows(villes)
    contenu_csv = donnees.getvalue().removesuffix("\n")
    return "\n".join([
        r"\set ON_ERROR_STOP on",
        "BEGIN;",
        """CREATE TEMP TABLE ville_import_stage (
            source_type VARCHAR(16) NOT NULL,
            source_id VARCHAR(32) NOT NULL,
            nom VARCHAR(200) NOT NULL
        ) ON COMMIT DROP;""",
        "COPY ville_import_stage FROM STDIN WITH (FORMAT CSV, HEADER TRUE);",
        contenu_csv,
        r"\.",
        "DELETE FROM ville;",
        """INSERT INTO ville (source_type, source_id, nom)
        SELECT source_type, source_id, nom
        FROM ville_import_stage
        ON CONFLICT (source_type, source_id)
        DO UPDATE SET nom = EXCLUDED.nom;""",
        "COMMIT;",
        "",
    ])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--psql", default="psql")
    parser.add_argument("--database", default=os.getenv("PGDATABASE", "assistant_rh"))
    parser.add_argument("--host", default=os.getenv("PGHOST", "localhost"))
    parser.add_argument("--port", default=os.getenv("PGPORT", "5433"))
    parser.add_argument("--user", default=os.getenv("PGUSER", "postgres"))
    arguments = parser.parse_args()

    villes = telecharger_villes()
    environnement = os.environ.copy()
    environnement["PGCLIENTENCODING"] = "UTF8"
    subprocess.run(
        [
            arguments.psql,
            "-X",
            "-w",
            "-v",
            "ON_ERROR_STOP=1",
            "-h",
            arguments.host,
            "-p",
            arguments.port,
            "-U",
            arguments.user,
            "-d",
            arguments.database,
        ],
        input=construire_script_psql(villes),
        text=True,
        encoding="utf-8",
        env=environnement,
        check=True,
    )
    print(f"{len(villes)} lieux habités GeoNames importés.")


if __name__ == "__main__":
    try:
        main()
    except (
        OSError,
        ValueError,
        RuntimeError,
        zipfile.BadZipFile,
        subprocess.CalledProcessError,
    ) as error:
        print(f"Échec de l'import GeoNames : {error}", file=sys.stderr)
        raise SystemExit(1) from error
