#!/usr/bin/env python3
"""Convert a flat algorithm list into a validated Cubearoni CFOP DB file."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parent.parent
DB_DIR = ROOT / "db"
CLASSES_DIR = ROOT / "target" / "classes"
CUBEARONI_CLASS = "org.selliott.cubearoni.Cubearoni"
EXTRACT_SIGNATURE = ROOT / "bin" / "extract-signature.py"
SOLVED_NAMES = {"0", "O"}


def normalize_name(name: str) -> str:
    variant = Path(name).name
    if variant.endswith(".txt"):
        variant = variant[:-4]
    if variant.startswith("cfop-"):
        variant = variant[5:]
    if not variant or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", variant):
        raise ValueError(f"Invalid CFOP variant name {name!r}.")
    return variant


def java_command(*args: str) -> list[str]:
    return ["java", "-cp", str(CLASSES_DIR), CUBEARONI_CLASS, *args]


def run(command: list[str], *, input_text: str | None = None) -> str:
    result = subprocess.run(
        command,
        cwd=ROOT,
        input=input_text,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(
            f"Command failed ({result.returncode}): {' '.join(command)}"
            + (f"\n{detail}" if detail else "")
        )
    return result.stdout


def get_signature(algorithm: str) -> str:
    cube_text = run(java_command("-I", "-m", algorithm, "-C"))
    signature = run(
        [sys.executable, str(EXTRACT_SIGNATURE), "-a"],
        input_text=cube_text,
    ).strip()
    if not signature:
        raise RuntimeError(f"No signature produced for algorithm {algorithm!r}.")
    return signature


def get_name(algorithm: str, phase: str) -> str:
    output = run(java_command(
        "-I", "-m", algorithm, "-v", "-n", "cfop", "-l"
    ))
    names = []
    for line in output.splitlines():
        match = re.match(r"^\s*(OLL|PLL)\s+\d+\s+(\S+)", line)
        if (match and match.group(1) == phase
                and match.group(2) not in SOLVED_NAMES):
            names.append(match.group(2))
    if len(names) != 1:
        raise RuntimeError(
            f"Expected one non-solved {phase} name for {algorithm!r}, "
            f"but found {names!r}."
        )
    return names[0]


def classify(signature: str) -> str:
    return "OLL" if "U" in signature else "PLL"


def validate_name(phase: str, signature: str, name: str) -> None:
    if (phase == "OLL") != name.isdigit():
        raise RuntimeError(
            f"Signature {signature!r} indicates {phase}, but algorithm name "
            f"{name!r} has the wrong form."
        )


def read_algorithms(path: Path) -> list[str]:
    algorithms = []
    for line_number, line in enumerate(
            path.read_text(encoding="utf-8").splitlines(), 1):
        algorithm = line.strip()
        if not algorithm or algorithm.startswith("#"):
            continue
        if algorithm == "none":
            raise ValueError(
                f"{path}:{line_number}: solved entries are added automatically."
            )
        algorithms.append(algorithm)
    if not algorithms:
        raise ValueError(f"No algorithms found in {path}.")
    return algorithms


def make_rows(algorithms: list[str]) -> tuple[dict[int, str], dict[str, str]]:
    oll_rows = {
        0: "OLL  ***,***,***,***  none  d  0",
    }
    pll_rows = {
        "O": "PLL  BBB,RRR,FFF,LLL  none  d  O",
    }

    for index, algorithm in enumerate(algorithms, 1):
        compact_algorithm = re.sub(r"\s+", "", algorithm)
        signature = get_signature(compact_algorithm)
        phase = classify(signature)
        name = get_name(compact_algorithm, phase)
        validate_name(phase, signature, name)
        row = f"{phase}  {signature}  {compact_algorithm}  d  {name}"
        rows = oll_rows if phase == "OLL" else pll_rows
        key = int(name) if phase == "OLL" else name
        if key in rows:
            raise RuntimeError(f"Duplicate {phase} algorithm name {name!r}.")
        rows[key] = row
        print(
            f"[{index}/{len(algorithms)}] {phase} {name}: "
            f"{compact_algorithm}",
            file=sys.stderr,
        )

    return oll_rows, pll_rows


def write_intermediate(
        path: Path,
        variant: str,
        source: str,
        oll_rows: dict[int, str],
        pll_rows: dict[str, str]) -> None:
    lines = [
        f"# {variant} CFOP algorithms.",
        "#",
        f"# Generated from {source} by bin/algorithm-list-to-cfop-db.py.",
        "# See cfop-help.txt for CFOP DB file format details.",
        "",
        "# OLL algorithms",
        "",
        "# phase signature           algorithm                       flag    name    mname   probability",
    ]
    lines.extend(oll_rows[key] for key in sorted(oll_rows))
    lines.extend([
        "",
        "# PLL algorithms",
        "",
        "# phase signature           algorithm                       flag    name    mname   probability",
    ])
    lines.extend(pll_rows[key] for key in sorted(pll_rows))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def validate_and_install(staging_path: Path, destination: Path) -> None:
    solver_name = staging_path.stem
    result = subprocess.run(
        java_command("-t", "-n", solver_name),
        cwd=ROOT,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"CFOP validation failed. Intermediate file retained at "
            f"{staging_path}."
        )

    canonical_path = Path(str(staging_path) + ".new")
    if not canonical_path.exists():
        canonical_path = staging_path
    os.replace(canonical_path, destination)
    destination.chmod(0o644)
    if staging_path.exists():
        staging_path.unlink()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert a flat algorithm list into db/cfop-<name>.txt."
    )
    parser.add_argument(
        "-f",
        "--force",
        action="store_true",
        help="replace an existing destination after successful validation",
    )
    parser.add_argument("name", help="CFOP variant name or cfop-*.txt basename")
    parser.add_argument("algorithm_list", type=Path,
                        help="one-algorithm-per-line input file")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    staging_path = None
    try:
        variant = normalize_name(args.name)
        source = args.algorithm_list.resolve()
        destination = DB_DIR / f"cfop-{variant}.txt"
        if destination.exists() and not args.force:
            raise FileExistsError(
                f"{destination} already exists; use -f/--force to replace it."
            )
        if not CLASSES_DIR.is_dir():
            raise FileNotFoundError(
                f"{CLASSES_DIR} does not exist; run `mvn package` first."
            )

        algorithms = read_algorithms(source)
        oll_rows, pll_rows = make_rows(algorithms)
        try:
            source_description = str(source.relative_to(ROOT))
        except ValueError:
            source_description = str(source)
        fd, staging_name = tempfile.mkstemp(
            prefix=f"cfop-{variant}-building-",
            suffix=".txt",
            dir=DB_DIR,
        )
        os.close(fd)
        staging_path = Path(staging_name)
        write_intermediate(
            staging_path, variant, source_description, oll_rows, pll_rows
        )
        validate_and_install(staging_path, destination)
        print(
            f"Wrote {destination} with {len(oll_rows)} OLL and "
            f"{len(pll_rows)} PLL entries."
        )
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        if staging_path is not None and staging_path.exists():
            print(f"Intermediate file retained at {staging_path}.",
                  file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
