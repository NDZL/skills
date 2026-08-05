#!/usr/bin/env python3
"""Derive bytes-per-record for a Kotlin/Java data holder, and project a cache footprint.

Optional accelerator for appquality-memory-assessment-android. The manual fallback is the byte
table and worked example in references/quantification.md section 2 -- this script only automates
that arithmetic.

The output is a MODEL, not a measurement. It is accurate to roughly a factor of two on absolute
bytes and exact on the scaling exponent. Calibrate the per-record constant against one heap dump
and keep the ratio.

Usage:
  python estimate_object_size.py --file Item.kt [--container hashmap|arraylist|none]
                                 [--rows 5000,200000,2000000] [--json]
  python estimate_object_size.py --self-test
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HEADER = 8
REF = 4
ALIGN = 8
STRING_BASE = 16
HASHMAP_ENTRY = 30
ARRAYLIST_SLOT = 6  # 4-byte slot plus growth slack

PRIMITIVES = {
    "boolean": 1, "Boolean": 1, "byte": 1, "Byte": 1,
    "char": 2, "Char": 2, "short": 2, "Short": 2,
    "int": 4, "Int": 4, "float": 4, "Float": 4,
    "long": 8, "Long": 8, "double": 8, "Double": 8,
}

# Default assumed character counts when a length cannot be derived from a schema.
DEFAULT_STRING_CHARS = 24

FIELD_RE = re.compile(
    r"^\s*(?:val|var)\s+(\w+)\s*:\s*([A-Za-z0-9_<>, ?]+?)\s*(?:=|,|$)",
    re.MULTILINE,
)
JAVA_FIELD_RE = re.compile(
    r"^\s*(?:private|public|protected)?\s*(?:final\s+)?"
    r"([A-Za-z0-9_<>, ]+?)\s+(\w+)\s*(?:=[^;]*)?;",
    re.MULTILINE,
)
# @Column(length = 40) / VARCHAR(40) / // chars: 40
LENGTH_HINT_RE = re.compile(r"(?:length\s*=\s*(\d+))|(?:VARCHAR\(\s*(\d+)\s*\))|(?:chars:\s*(\d+))")


def align(value: int) -> int:
    remainder = value % ALIGN
    return value if remainder == 0 else value + (ALIGN - remainder)


def string_bytes(chars: int, latin1: bool = True) -> int:
    return align(STRING_BASE + chars * (1 if latin1 else 2))


def parse_fields(text: str) -> list[tuple[str, str, int | None]]:
    """Return (name, type, explicit_char_length_or_None)."""
    fields: list[tuple[str, str, int | None]] = []
    for line in text.splitlines():
        hint = LENGTH_HINT_RE.search(line)
        length = None
        if hint:
            length = int(next(group for group in hint.groups() if group))
        match = FIELD_RE.match(line)
        if match:
            fields.append((match.group(1), match.group(2).strip().rstrip(","), length))
            continue
        match = JAVA_FIELD_RE.match(line)
        if match:
            fields.append((match.group(2), match.group(1).strip(), length))
    return fields


def size_instance(fields: list[tuple[str, str, int | None]]) -> tuple[int, int, list[dict]]:
    """Return (inline_bytes_aligned, referenced_bytes, per-field breakdown)."""
    inline = HEADER
    referenced = 0
    breakdown: list[dict] = []
    for name, type_name, length in fields:
        bare = type_name.rstrip("?").strip()
        if bare in PRIMITIVES:
            width = PRIMITIVES[bare]
            inline += width
            breakdown.append({"field": name, "type": bare, "inline": width, "referenced": 0})
        elif bare == "String":
            chars = length if length is not None else DEFAULT_STRING_CHARS
            payload = string_bytes(chars)
            inline += REF
            referenced += payload
            breakdown.append({
                "field": name, "type": f"String({chars} chars)",
                "inline": REF, "referenced": payload,
                "assumed_length": length is None,
            })
        else:
            inline += REF
            breakdown.append({
                "field": name, "type": bare, "inline": REF, "referenced": 0,
                "note": "reference only; nested cost not counted -- recurse manually",
            })
    return align(inline), referenced, breakdown


def container_overhead(kind: str) -> int:
    return {"hashmap": HASHMAP_ENTRY, "arraylist": ARRAYLIST_SLOT, "none": 0}[kind]


def analyse(text: str, container: str, rows: list[int]) -> dict:
    fields = parse_fields(text)
    if not fields:
        return {"error": "No fields parsed. Check the declaration, or compute by hand "
                         "using references/quantification.md section 2."}
    inline, referenced, breakdown = size_instance(fields)
    overhead = container_overhead(container)
    per_row = inline + referenced + overhead
    assumed = [item["field"] for item in breakdown if item.get("assumed_length")]
    return {
        "model": True,
        "calibrated": False,
        "tolerance": "approximately +/-2x on absolute bytes; exact on the scaling exponent",
        "fields": breakdown,
        "instance_bytes_aligned": inline,
        "referenced_bytes": referenced,
        "container": container,
        "container_overhead_per_entry": overhead,
        "bytes_per_row": per_row,
        "projection": [
            {"rows": count, "bytes": per_row * count,
             "megabytes": round(per_row * count / 1_048_576, 1)}
            for count in rows
        ],
        "assumed_string_lengths": assumed,
        "warnings": ([
            "String lengths were assumed for: " + ", ".join(assumed)
            + f" (default {DEFAULT_STRING_CHARS} chars). Derive real lengths from database schema "
              "column widths, not from test data -- this term can move the result by 10x."
        ] if assumed else []) + [
            "Shared or interned strings are counted once per holder here; verify they are not "
            "double-counted across rows.",
            "Nested collections are counted as a reference only. Recurse manually.",
        ],
    }


def render(result: dict) -> str:
    if "error" in result:
        return result["error"]
    lines = [
        "DERIVED MODEL -- not a measurement. Calibrate against one heap dump.",
        f"Tolerance: {result['tolerance']}",
        "",
        f"{'field':<20} {'type':<24} {'inline':>7} {'referenced':>11}",
    ]
    for item in result["fields"]:
        lines.append(
            f"{item['field']:<20} {item['type']:<24} {item['inline']:>7} {item['referenced']:>11}"
        )
    lines += [
        "",
        f"instance (aligned)          : {result['instance_bytes_aligned']} B",
        f"referenced payload          : {result['referenced_bytes']} B",
        f"container overhead / entry  : {result['container_overhead_per_entry']} B "
        f"({result['container']})",
        f"BYTES PER ROW               : ~{result['bytes_per_row']} B (model; uncalibrated)",
        "",
        "Projection:",
    ]
    for point in result["projection"]:
        lines.append(f"  {point['rows']:>12,} rows -> {point['megabytes']:>8} MB")
    if result["warnings"]:
        lines.append("")
        lines.append("Warnings:")
        lines.extend(f"  - {warning}" for warning in result["warnings"])
    return "\n".join(lines)


SELF_TEST_SOURCE = """
data class Item(
    val sku: String,          // chars: 24
    val description: String,  // chars: 40
    val price: Double,
    val qtyOnHand: Int,
    val locationId: Long,
    val active: Boolean,
)
"""


def self_test() -> int:
    result = analyse(SELF_TEST_SOURCE, "hashmap", [200_000])
    expected = 166
    actual = result["bytes_per_row"]
    ok = actual == expected
    print(f"self-test bytes_per_row: expected {expected}, got {actual} -> "
          f"{'PASS' if ok else 'FAIL'}")
    print("Fixture parity: assets/test-fixtures/sizing/expected.json")
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", type=Path)
    parser.add_argument("--container", choices=["hashmap", "arraylist", "none"], default="hashmap")
    parser.add_argument("--rows", default="5000,200000,2000000")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return self_test()
    if args.file is None:
        parser.error("--file is required unless --self-test is used")
    if not args.file.is_file():
        print(f"No such file: {args.file}", file=sys.stderr)
        return 2

    rows = [int(value) for value in args.rows.split(",") if value.strip()]
    result = analyse(args.file.read_text(encoding="utf-8"), args.container, rows)
    print(json.dumps(result, indent=2) if args.json else render(result))
    return 0 if "error" not in result else 1


if __name__ == "__main__":
    sys.exit(main())
