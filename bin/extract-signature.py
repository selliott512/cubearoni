#!/usr/bin/env python3
"""
Extract signatures from Cubearoni unfolded text output.

Input is expected to be the standard non-wide vertical-cross format produced by
`Cubearoni -C`, for example:

    java -cp target/classes org.selliott.cubearoni.Cubearoni -Im "FRUR'U'F'" -C \
      | python3 bin/extract-signature.py -o

Three modes are supported:
  -a  Auto mode, select PLL if the U face is fully oriented; otherwise OLL.
  -o  OLL mode, output keyed signature in B,R,F,L with U/* normalization.
  -p  PLL mode, output raw stickers in B,R,F,L with no normalization.
"""

from __future__ import annotations

import argparse
import sys


def _read_text(path: str | None) -> str:
    """Read unfolded cube text from a file or standard input."""
    if path is None:
        return sys.stdin.read()
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def _split_sections(lines: list[str]) -> tuple[list[str], list[str], list[str], list[str]]:
    """Split a vertical unfolded cube into its four row sections."""
    sep_idxs = [i for i, line in enumerate(lines) if line.strip() == "..."]
    if len(sep_idxs) < 3:
        raise ValueError("Expected at least three separator lines containing '...'.")
    s1, s2, s3 = sep_idxs[:3]
    up = [line for line in lines[:s1] if line.strip()]
    mid = [line for line in lines[s1 + 1:s2] if line.strip()]
    _down = [line for line in lines[s2 + 1:s3] if line.strip()]
    back = [line for line in lines[s3 + 1:] if line.strip()]
    return up, mid, _down, back


def _extract_signature(text: str, normalize_oll: bool | None) -> str:
    """Extract a B,R,F,L signature, optionally normalizing it for OLL."""
    lines = [line.rstrip("\n") for line in text.splitlines() if line.strip()]
    up, mid, _, back = _split_sections(lines)
    if (len(up) < 3) or (len(mid) < 1) or (len(back) < 3):
        raise ValueError("Unexpected unfolded cube layout.")

    top_rows = [row.strip() for row in up[:3]]
    if any(len(row) != 3 for row in top_rows):
        raise ValueError("Top face rows must each have exactly 3 stickers.")
    top_char = top_rows[1][1]
    if normalize_oll is None:
        normalize_oll = not all(row == "UUU" for row in top_rows)

    top_mid = mid[0].strip()
    parts = top_mid.split(".")
    if len(parts) != 3:
        raise ValueError(
            "Expected 3 side groups (L.F.R) on the first middle-band row in non-wide format."
        )
    l_group, f_group, r_group = parts
    if not (len(l_group) == len(f_group) == len(r_group) == 3):
        raise ValueError("Each middle-band side group must have exactly 3 stickers.")

    b_group = back[-1].strip()
    if len(b_group) != 3:
        raise ValueError("Back-face row adjacent to U must have exactly 3 stickers.")

    # Canonical keyed order is B,R,F,L. For clockwise ordering per side:
    # - B is already oriented correctly in this layout.
    # - R, F and L need to be reversed.
    groups = [b_group, r_group[::-1], f_group[::-1], l_group[::-1]]

    if not normalize_oll:
        return ",".join(groups)

    def norm(ch: str) -> str:
        """Map the upper-face sticker to U and every other sticker to *."""
        return "U" if ch == top_char else "*"

    return ",".join("".join(norm(ch) for ch in grp) for grp in groups)


def main() -> int:
    """Parse command-line options, extract the signature, and print it."""
    parser = argparse.ArgumentParser(
        description="Extract signature (B,R,F,L) from Cubearoni unfolded text."
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument(
        "-a",
        action="store_true",
        help="Auto mode: select PLL when the top face is entirely U; otherwise OLL.",
    )
    mode.add_argument(
        "-o",
        action="store_true",
        help="OLL mode: normalize to U/* relative to top-center sticker.",
    )
    mode.add_argument(
        "-p",
        action="store_true",
        help="PLL mode: output raw sticker letters (no normalization).",
    )
    parser.add_argument(
        "file",
        nargs="?",
        help="Optional input file. If omitted, read unfolded cube text from stdin.",
    )
    args = parser.parse_args()

    try:
        text = _read_text(args.file)
        normalize_oll = None if args.a else args.o
        sig = _extract_signature(text, normalize_oll=normalize_oll)
    except Exception as exc:  # pragma: no cover
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(sig)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
