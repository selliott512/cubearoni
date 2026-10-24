#!/usr/bin/env python3

# Extract the first listed OLL and PLL algorithms from:
#   https://www.speedsolving.com/wiki
# After preprocessing web page with:
#   grep -P '^(OLL \d+|Name: .*-PLL.*$|\t(OLL|PLL|Alg\S+)\t)'

import re
import sys

ROW_FORMAT = "{phase:<7} {signature:<19} {algorithm:<31} {flag:<7} {name:<7} {mname:<7} {probability}"

current_phase = None
current_name = None
seen = set()

for line in sys.stdin:
    line = line.strip()

    m = re.fullmatch(r"OLL\s+(\d+)|Name:\s*(.*PLL.*)", line)
    if m:
        current_phase = "OLL" if m.group(1) is not None else "PLL"
        current_name = m.group(1)
        if current_name is None:
            current_name = m.group(2)
        current_name = current_name.replace("-PLL", "")
        current_name = current_name.replace(" ", "")
        current_name = re.sub(r",.*$", "", current_name)
        continue

    if current_name is None or current_name in seen:
        continue

    # Match algorithm lines like:
    # OLL    (y') R U2 R' U' R U' R'
    m = re.match(r"([OP]LL|Alg\S*)\s+(.+)$", line)
    if not m:
        print("c3")
        continue

    alg = m.group(2).strip()

    # Discard leading parenthesized AUF / rotation prefix, e.g. "(U')" or "(y')"
    alg = re.sub(r"^\([Uy'2)]*\)\s*", "", alg)

    # Remove all whitespace and parens from the algorithm
    alg = re.sub(r"[\s()]+", "", alg)

    # Cubearoni uses uppercase letters for whole-cube rotations.
    alg = alg.translate(str.maketrans("xyz", "XYZ"))

    # Three quarter turns are equivalent to one turn in the inverse direction.
    alg = re.sub(r"([RLUDFBMSErludfbXYZ])3", r"\1'", alg)

    # Remove trailing footnotes like [1]
    alg = re.sub(r"(\[\d+\])+$", "", alg)

    # A trailing ASCII digit from 3 through 9 repeats the preceding algorithm.
    if alg and "3" <= alg[-1] <= "9":
        repeat_count = int(alg[-1])
        alg = alg[:-1] * repeat_count

    print(ROW_FORMAT.format(
        phase=current_phase,
        signature="",
        algorithm=alg,
        flag="d",
        name=current_name,
        mname="",
        probability="",
    ).rstrip())
    seen.add(current_name)
