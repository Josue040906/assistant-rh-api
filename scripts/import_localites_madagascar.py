#!/usr/bin/env python3
"""Import HDX/OCHA Madagascar administrative units into PostgreSQL."""

import argparse
import csv
import datetime
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


SOURCE_URL = (
    "https://data.humdata.org/dataset/"
    "26fa506b-0727-4d9d-a590-d2abee21ee22/resource/"
    "44c25ef4-e858-48e3-8d93-f0d4d08bdc9c/download/"
    "mdg_admin_boundaries.xlsx"
)
SOURCE_NAME = "HDX_COD_AB_MDG"
SOURCE_LICENSE = "CC BY 3.0 IGO"
EXPECTED_COUNTS = {1: 24, 2: 120, 3: 1645, 4: 17465}
EXCEL_EPOCH = datetime.date(1899, 12, 30)
SHEETS = {1: "mdg_admin1", 2: "mdg_admin2", 3: "mdg_admin3", 4: "mdg_admin4"}
NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "rel": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "pkg": "http://schemas.openxmlformats.org/package/2006/relationships",
}


def column_index(cell_reference):
    letters = re.match(r"[A-Z]+", cell_reference).group(0)
    value = 0
    for letter in letters:
        value = value * 26 + ord(letter) - ord("A") + 1
    return value - 1


def read_shared_strings(archive):
    root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
    return [
        "".join(text.text or "" for text in item.findall(".//main:t", NS))
        for item in root.findall("main:si", NS)
    ]


def read_sheet_rows(archive, sheet_name, shared_strings):
    workbook = ET.fromstring(archive.read("xl/workbook.xml"))
    relationships = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
    relation_targets = {
        relation.attrib["Id"]: relation.attrib["Target"]
        for relation in relationships.findall("pkg:Relationship", NS)
    }
    sheet = next(
        sheet for sheet in workbook.findall("main:sheets/main:sheet", NS)
        if sheet.attrib["name"] == sheet_name
    )
    target = relation_targets[sheet.attrib[f"{{{NS['rel']}}}id"]]
    sheet_path = Path("xl") / target

    root = ET.fromstring(archive.read(sheet_path.as_posix()))
    rows = []
    for row in root.findall("main:sheetData/main:row", NS):
        cells = {}
        for cell in row.findall("main:c", NS):
            value = cell.find("main:v", NS)
            if value is None:
                inline = cell.find("main:is", NS)
                cell_value = (
                    "".join(text.text or "" for text in inline.findall(".//main:t", NS))
                    if inline is not None else ""
                )
            elif cell.attrib.get("t") == "s":
                cell_value = shared_strings[int(value.text)]
            else:
                cell_value = value.text or ""
            cells[column_index(cell.attrib["r"])] = cell_value

        if cells:
            rows.append(cells)
    return rows


def parse_source(path):
    try:
        archive = zipfile.ZipFile(path)
    except (OSError, zipfile.BadZipFile) as error:
        raise ValueError("Le fichier source n'est pas un classeur XLSX valide.") from error

    with archive:
        shared_strings = read_shared_strings(archive)
        result = []
        codes_by_level = {level: set() for level in SHEETS}
        counts = {level: 0 for level in SHEETS}

        for level, sheet_name in SHEETS.items():
            rows = read_sheet_rows(archive, sheet_name, shared_strings)
            if not rows:
                raise ValueError(f"La feuille source {sheet_name} est vide.")

            headers = {
                index: name.strip()
                for index, name in rows[0].items()
                if name.strip()
            }
            required = {f"adm{level}_name", f"adm{level}_pcode"}
            if level > 1:
                required.add(f"adm{level - 1}_pcode")
            missing = required - set(headers.values())
            if missing:
                raise ValueError(
                    f"Colonnes manquantes dans {sheet_name}: {sorted(missing)}"
                )
            indexes = {name: index for index, name in headers.items()}

            for row in rows[1:]:
                values = {
                    name: row.get(indexes[name], "").strip()
                    for name in indexes
                }
                code = values.get(f"adm{level}_pcode", "")
                name = values.get(f"adm{level}_name", "")
                parent_code = (
                    values.get(f"adm{level - 1}_pcode", "")
                    if level > 1 else ""
                )
                if not code and not name:
                    continue
                if not code or not name:
                    raise ValueError(
                        f"Enregistrement incomplet dans {sheet_name}: {values}"
                    )
                if code in codes_by_level[level]:
                    raise ValueError(
                        f"Code source dupliqué au niveau {level}: {code}"
                    )
                if level > 1 and not parent_code:
                    raise ValueError(f"Parent absent pour le code {code}.")

                for date_column in ("valid_on", "valid_to"):
                    date_value = values.get(date_column, "")
                    if date_value:
                        try:
                            values[date_column] = (
                                EXCEL_EPOCH
                                + datetime.timedelta(days=int(float(date_value)))
                            ).isoformat()
                        except (TypeError, ValueError, OverflowError):
                            try:
                                datetime.date.fromisoformat(date_value)
                            except ValueError as error:
                                raise ValueError(
                                    f"Date source invalide pour {code}: {date_value}"
                                ) from error

                codes_by_level[level].add(code)
                counts[level] += 1
                result.append((
                    level,
                    code,
                    parent_code,
                    name,
                    values.get("valid_on", ""),
                    values.get("valid_to", ""),
                    values.get("version", ""),
                ))

        for level, expected in EXPECTED_COUNTS.items():
            if counts[level] != expected:
                raise ValueError(
                    f"Effectif admin{level} inattendu: {counts[level]} "
                    f"(attendu {expected}); vérifier/versionner la source."
                )

        for level in (2, 3, 4):
            missing_parents = {
                row[2] for row in result if row[0] == level
            } - codes_by_level[level - 1]
            if missing_parents:
                raise ValueError(
                    f"Codes parents admin{level - 1} inconnus: "
                    f"{sorted(missing_parents)[:10]}"
                )

    return result, counts


def download_source():
    temporary = tempfile.NamedTemporaryFile(
        prefix="mdg_admin_boundaries_", suffix=".xlsx", delete=False
    )
    path = Path(temporary.name)
    try:
        request = urllib.request.Request(
            SOURCE_URL,
            headers={"User-Agent": "AssistantRH-Locality-Importer/1.0"},
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            shutil.copyfileobj(response, temporary)
        temporary.close()
        return path
    except (OSError, urllib.error.URLError):
        temporary.close()
        path.unlink(missing_ok=True)
        raise


def build_psql_script(rows):
    csv_data = io.StringIO(newline="")
    writer = csv.writer(csv_data, lineterminator="\n")
    writer.writerow(
        ["niveau", "source_code", "parent_code", "nom", "valid_on", "valid_to", "version"]
    )
    for level, code, parent, name, valid_on, valid_to, version in rows:
        writer.writerow([level, code, parent, name, valid_on, valid_to, version])
    csv_payload = csv_data.getvalue()
    if csv_payload.endswith("\n"):
        csv_payload = csv_payload[:-1]

    statements = [
        "\\set ON_ERROR_STOP on",
        "BEGIN;",
        """CREATE TEMP TABLE localite_import_stage (
            level INTEGER NOT NULL,
            source_code TEXT NOT NULL,
            parent_code TEXT,
            name TEXT NOT NULL,
            valid_on DATE,
            valid_to DATE,
            version TEXT
        ) ON COMMIT DROP;""",
        "COPY localite_import_stage FROM STDIN WITH (FORMAT CSV, HEADER TRUE);",
        csv_payload,
        r"\.",
    ]

    levels = [
        ("REGION", 1, None),
        ("DISTRICT", 2, "REGION"),
        ("COMMUNE", 3, "DISTRICT"),
        ("FOKONTANY", 4, "COMMUNE"),
    ]
    for name, source_level, parent_level in levels:
        if parent_level is None:
            parent_join = "NULL::BIGINT, NULL::VARCHAR(16)"
            parent_from = ""
        else:
            parent_join = "parent.id, parent.niveau"
            parent_from = f"""
                JOIN localite parent
                  ON parent.source_name = '{SOURCE_NAME}'
                 AND parent.source_code = stage.parent_code
                 AND parent.niveau = '{parent_level}'"""

        statements.append(f"""
            INSERT INTO localite (
                niveau, nom, parent_id, parent_niveau, origine,
                source_name, source_code, source_version,
                source_valid_on, source_valid_to, source_license, imported_at
            )
            SELECT
                '{name}', stage.name, {parent_join}, 'HDX_OCHA',
                '{SOURCE_NAME}', stage.source_code, stage.version,
                stage.valid_on, stage.valid_to, '{SOURCE_LICENSE}', CURRENT_TIMESTAMP
            FROM localite_import_stage stage
            {parent_from}
            WHERE stage.level = {source_level}
            ON CONFLICT (source_name, source_code) WHERE source_code IS NOT NULL
            DO UPDATE SET
                niveau = EXCLUDED.niveau,
                nom = EXCLUDED.nom,
                parent_id = EXCLUDED.parent_id,
                parent_niveau = EXCLUDED.parent_niveau,
                source_version = EXCLUDED.source_version,
                source_valid_on = EXCLUDED.source_valid_on,
                source_valid_to = EXCLUDED.source_valid_to,
                source_license = EXCLUDED.source_license,
                imported_at = CURRENT_TIMESTAMP;""")

    statements.extend([
        """DO $$
        BEGIN
            IF (SELECT count(*) FROM localite WHERE source_name = 'HDX_COD_AB_MDG'
                AND niveau = 'REGION') <> 24
               OR (SELECT count(*) FROM localite WHERE source_name = 'HDX_COD_AB_MDG'
                AND niveau = 'DISTRICT') <> 120
               OR (SELECT count(*) FROM localite WHERE source_name = 'HDX_COD_AB_MDG'
                AND niveau = 'COMMUNE') <> 1645
               OR (SELECT count(*) FROM localite WHERE source_name = 'HDX_COD_AB_MDG'
                AND niveau = 'FOKONTANY') <> 17465 THEN
                RAISE EXCEPTION 'Les effectifs importés ne correspondent pas aux effectifs vérifiés.';
            END IF;
        END
        $$;""",
        "COMMIT;",
    ])
    return "\n".join(statements)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xlsx", type=Path, help="Classeur HDX déjà téléchargé")
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--database", default=os.getenv("PGDATABASE", "assistant_rh"))
    parser.add_argument("--host", default=os.getenv("PGHOST", "localhost"))
    parser.add_argument("--port", default=os.getenv("PGPORT", "5433"))
    parser.add_argument("--user", default=os.getenv("PGUSER", "postgres"))
    parser.add_argument("--psql", default="psql")
    args = parser.parse_args()

    downloaded = None
    try:
        source = args.xlsx
        if source is None:
            downloaded = download_source()
            source = downloaded
        rows, counts = parse_source(source)
        print(
            "Source validée: "
            + ", ".join(f"admin{level}={counts[level]}" for level in sorted(counts))
        )
        if args.validate_only:
            return 0

        command = [
            args.psql, "-X", "-w", "-v", "ON_ERROR_STOP=1",
            "-h", args.host, "-p", args.port, "-U", args.user, "-d", args.database,
        ]
        completed = subprocess.run(
            command,
            input=build_psql_script(rows),
            text=True,
            encoding="utf-8",
            capture_output=True,
            check=False,
        )
        if completed.returncode != 0:
            if completed.stdout:
                print(completed.stdout, end="", file=sys.stderr)
            if completed.stderr:
                print(completed.stderr, end="", file=sys.stderr)
            raise RuntimeError("L'import PostgreSQL a échoué; la transaction a été annulée.")
        print(completed.stdout, end="")
        print("Import terminé (transactionnel et réexécutable).")
        return 0
    finally:
        if downloaded is not None:
            downloaded.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, urllib.error.URLError, RuntimeError) as error:
        print(f"Erreur d'import des localités: {error}", file=sys.stderr)
        raise SystemExit(1) from error
