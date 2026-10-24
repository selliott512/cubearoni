#!/usr/bin/env python3
import argparse
import subprocess
import sys
import urllib.request
from html.parser import HTMLParser
from urllib.parse import urlparse
from urllib.request import url2pathname

DEFAULT_TEMPLATE = "https://frostburn.github.io/%s.html"
PHASES = ("oll", "pll")
DATA_FORMAT = "{phase:<7} {signature:<19} {algorithm:<31} {flags:<3} # {comment:<28} p= {probability:>8}"
DATA_HEADER_FORMAT = "{phase:<7} {signature:<19} {algorithm:<31} {flags:<3} # {comment:<28} {probability}"


class TableParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.in_tr = False
        self.in_cell = False
        self.rows = []
        self.row = []
        self.cell = []
        self.cell_parts = []

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.in_tr = True
            self.row = []
        elif tag in ("td", "th") and self.in_tr:
            self.in_cell = True
            self.cell = []
            self.cell_parts = []
        elif tag == "br" and self.in_cell:
            self._finish_cell_part()

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.in_cell:
            self._finish_cell_part()
            text = ", ".join(self.cell_parts)
            self.row.append(text)
            self.in_cell = False
        elif tag == "tr" and self.in_tr:
            if self.row:
                self.rows.append(self.row)
            self.in_tr = False

    def handle_data(self, data):
        if self.in_cell:
            self.cell.append(data)

    def _finish_cell_part(self):
        text = " ".join("".join(self.cell).split())
        if text:
            self.cell_parts.append(text)
        self.cell = []


def read_source(src):
    if src.startswith(("http://", "https://", "file://")):
        with urllib.request.urlopen(src) as f:
            return f.read().decode("utf-8")
    if src.startswith("file:"):
        path = url2pathname(urlparse(src).path)
        with open(path, encoding="utf-8") as f:
            return f.read()
    else:
        with open(src, encoding="utf-8") as f:
            return f.read()


def emit_algorithms(phase, src, args):
    parser = TableParser()
    parser.feed(read_source(src))

    if args.mode == "data":
        print()
        print(
            f"# {phase.upper()} algorithms. These were originally taken from "
            f"https://frostburn.github.io/{phase}.html"
        )
        print()
        print(format_data_header())
        print(format_data_row(phase, "", "", args.unknown))

    columns = None
    for row in parser.rows:
        if columns is None:
            columns = get_columns(row)
            if columns is None:
                continue
            continue
        if len(row) <= columns["algorithm"]:
            continue

        name = get_cell(row, columns["name"])
        alg = get_cell(row, columns["algorithm"])
        move_count = get_cell(row, columns["move_count"])
        move_complexity = normalize_move_complexity(
                get_cell(row, columns["move_complexity"]))
        if args.mode == "data" and not alg:
            continue
        if alg or move_count == "0":
            name_base = name.split()[0]
            if args.base and name_base != name:
                continue
            alg = select_part(alg, args.algorithm_index)
            move_complexity = select_part(move_complexity, args.algorithm_index)
            if args.mode == "data":
                print(format_data_row(phase, name, alg, args.unknown))
            else:
                print(f"{name_base}\t{name}\t{alg}\t{move_count}\t{move_complexity}")


def get_columns(row):
    normalized = [cell.lower() for cell in row]
    if "algorithm" not in normalized:
        return None
    return {
        "name": normalized.index("name") if "name" in normalized else 0,
        "algorithm": normalized.index("algorithm"),
        "move_count": normalized.index("move count"),
        "move_complexity": normalized.index("move complexity"),
    }


def get_cell(row, index):
    return row[index] if index < len(row) else ""


def normalize_move_complexity(value):
    parts = []
    for part in value.split(","):
        part = part.strip()
        parts.append("0" if part.lower() == "-nan" else part)
    return ", ".join(parts)


def select_part(value, index):
    if index is None:
        return value
    parts = [part.strip() for part in value.split(",")]
    return parts[index] if index < len(parts) else value


def format_data_row(phase, name, alg, unknown):
    phase_upper = phase.upper()
    algorithm = alg.replace(" ", "") if alg else "none"
    signature, probability = get_signature_and_probability(phase, name, alg, unknown)
    comment = f"{phase_upper} solved." if not alg else f"{name}."
    return DATA_FORMAT.format(
        phase=phase_upper,
        signature=signature,
        algorithm=algorithm,
        flags="d",
        comment=comment,
        probability=probability,
    )


def format_data_header():
    return DATA_HEADER_FORMAT.format(
        phase="# phase",
        signature="signature",
        algorithm="algorithm",
        flags="flg",
        comment="comment",
        probability="probability",
    )


def format_global_data_header():
    return "\n".join([
        "# A CFOP DB of Frostburn (https://github.com/frostburn) algorithms generated by:",
        "#   " + " ".join(sys.argv),
        "# See cfop-badmephisto-mirrored.txt for details.",
    ])


def get_signature_and_probability(phase, name, alg, unknown):
    denominator = 216 if phase == "oll" else 72
    if unknown:
        return "unknown", f"x/{denominator}"

    if not alg:
        if phase == "oll":
            return "***,***,***,***", "1/216"
        return "BBB,RRR,FFF,LLL", "1/72"

    # Alternative algorithms in one cell should produce the same signature.
    # Use the first one for metadata when raw mode has kept the full list.
    alg_single = select_part(alg, 0).replace(" ", "")
    signature = get_signature(phase, alg_single)

    # Hard coded treatment for N case for now.
    if phase == "pll" and name.startswith("N"):
        return signature, "1/72"

    if get_signature(phase, "U" + alg_single + "U'") == signature:
        return signature, f"1/{denominator}"
    if get_signature(phase, "U2" + alg_single + "U2'") == signature:
        return signature, f"2/{denominator}"
    return signature, f"4/{denominator}"


def get_signature(phase, moves):
    cubearoni = subprocess.run(
        [
            "java",
            "-cp",
            "target/classes",
            "org.selliott.cubearoni.Cubearoni",
            "-I",
            "-m",
            moves,
            "-C",
        ],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if cubearoni.returncode != 0:
        raise SystemExit(
            "Cubearoni failed while extracting signature. Run `mvn package` first.\n"
            + cubearoni.stdout + cubearoni.stderr
        )

    mode = "-o" if phase == "oll" else "-p"
    extractor = subprocess.run(
        ["bin/extract-signature.py", mode],
        input=cubearoni.stdout,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if extractor.returncode != 0:
        raise SystemExit(
            "extract-signature.py failed while extracting signature.\n"
            + extractor.stdout + extractor.stderr
        )
    return extractor.stdout.strip()


def parse_args():
    parser = argparse.ArgumentParser(
        description=(
            "Extract Frostburn OLL and PLL algorithms from matching HTML tables."
        )
    )
    mode_group = parser.add_mutually_exclusive_group()
    mode_group.add_argument(
        "-r",
        "--raw",
        action="store_const",
        const="raw",
        dest="mode",
        default="raw",
        help="raw tab-separated output (default)",
    )
    mode_group.add_argument(
        "-d",
        "--data",
        action="store_const",
        const="data",
        dest="mode",
        help="database-oriented output",
    )
    parser.add_argument(
        "-b",
        "--base",
        action="store_true",
        help="only include base moves",
    )
    parser.add_argument(
        "-u",
        "--unknown",
        action="store_true",
        help="use unknown signatures and probabilities in data mode",
    )
    algorithm_group = parser.add_mutually_exclusive_group()
    algorithm_group.add_argument(
        "-1",
        action="store_const",
        const=0,
        dest="algorithm_index",
        help="use only the first algorithm when a row has multiple algorithms",
    )
    algorithm_group.add_argument(
        "-2",
        action="store_const",
        const=1,
        dest="algorithm_index",
        help="use only the second algorithm when a row has multiple algorithms",
    )
    parser.add_argument(
        "template",
        nargs="?",
        default=DEFAULT_TEMPLATE,
        help=(
            "source URL or file template containing %%s, replaced with 'oll' "
            f"and 'pll' (default: {DEFAULT_TEMPLATE.replace('%', '%%')})"
        ),
    )
    return parser.parse_args()


def main():
    args = parse_args()
    if "%s" not in args.template:
        raise SystemExit("Template must contain %s so it can be expanded to oll and pll.")

    if args.mode == "data":
        print(format_global_data_header())

    for phase in PHASES:
        emit_algorithms(phase, args.template % phase, args)


if __name__ == "__main__":
    main()
