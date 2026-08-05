#!/usr/bin/env python3
"""Apply rule-catalogue search signatures across an Android project tree.

Optional accelerator for appquality-memory-assessment-android. The manual fallback is to apply each
signature listed per rule in references/rule-catalogue.md by hand; this script only automates the
search and never modifies the target project.

IMPORTANT: this reports CANDIDATES, not confirmed defects. Every rule in the catalogue has a
false-positive clause that requires reading the surrounding code. Treat output as a worklist.

Usage:
  python scan_memory_rules.py --project <path> [--min-sdk 24] [--json]
  python scan_memory_rules.py --self-test
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

SKIP_DIRS = {".git", "build", ".gradle", ".idea", "node_modules", ".agents", "generated"}
CODE_SUFFIXES = {".kt", ".java"}
BUILD_SUFFIXES = {".gradle", ".kts", ".properties", ".pro", ".xml"}

# rule_id -> (severity, pattern, suffixes, note, min_sdk_gate)
RULES: dict[str, tuple[str, str, set[str], str, int | None]] = {
    "MEM-BUILD-001": ("BLOCKER", r"largeHeap\s*=\s*[\"']true", {".xml"},
                      "Remove it and fix the allocation; it converts a traceable error into an "
                      "untraceable kill.", None),
    "MEM-BUILD-002": ("HIGH", r"isMinifyEnabled\s*=\s*false|minifyEnabled\s+false|"
                              r"enableR8\.fullMode\s*=\s*false", BUILD_SUFFIXES,
                      "Check this is a release-shaped build type before reporting.", None),
    "MEM-BUILD-003": ("MEDIUM", r"-dontoptimize|-dontshrink|-dontobfuscate|"
                                r"-keep\s+class\s+[\w.]*\*\*\s*\{\s*\*\s*;\s*\}", {".pro", ".txt"},
                      "A reflection-driven dependency may justify this; require a naming comment.",
                      None),
    "MEM-BUILD-005": ("MEDIUM", r"extractNativeLibs\s*=\s*[\"']true", {".xml"},
                      "Skip entirely for pure-JVM projects. Vendor archives are not your defect.",
                      None),
    "MEM-BITMAP-001": ("BLOCKER", r"BitmapFactory\.decode(File|Stream|ByteArray|Resource)",
                       CODE_SUFFIXES,
                       "Confirm no inSampleSize / inJustDecodeBounds / setTargetSampleSize in the "
                       "same function. Known-small bundled assets are false positives.", None),
    "MEM-BITMAP-002": ("HIGH", r"ARGB_8888", CODE_SUFFIXES,
                       "False positive wherever transparency is genuinely needed.", None),
    "MEM-CACHE-001": ("BLOCKER", r"(?:val|var)\s+\w+\s*(?::\s*(?:Mutable)?(?:Map|List|Set)"
                                 r"[^=\n]*)?=\s*(?:mutableMapOf|mutableListOf|HashMap|ArrayList|"
                                 r"LinkedHashMap|mutableStateListOf)\s*[<(]", CODE_SUFFIXES,
                      "Only a defect if the bound comes from customer data rather than from code. "
                      "Cost it with quantification.md section 2.", None),
    "MEM-DATA-001": ("BLOCKER", r"\.body\(\)\s*\??\.\s*string\(\)|bodyAsText\(\)|"
                                r"\.readText\(\)|\.readBytes\(\)|ByteArrayOutputStream",
                     CODE_SUFFIXES,
                     "Small bounded responses are false positives. Gate on whether the payload "
                     "scales with customer data.", None),
    "MEM-DATA-002": ("HIGH", r"SELECT\s+\*|@Query\s*\(\s*[\"'][^\"']*SELECT(?![^\"']*LIMIT)",
                     CODE_SUFFIXES,
                     "A genuine small bound is a false positive.", None),
    "MEM-DATA-004": ("MEDIUM", r"rawQuery\s*\(|\.query\s*\(", CODE_SUFFIXES,
                     "Confirm there is no scoped-use or finally close, and no adapter owner.", None),
    "MEM-LIFECYCLE-001": ("BLOCKER", r"registerReceiver\s*\(", CODE_SUFFIXES,
                          "Look for a symmetric unregister in the mirror lifecycle method. "
                          "Application-scoped registration with no activity captured is fine.",
                          None),
    "MEM-LIFECYCLE-003": ("BLOCKER", r"(?:companion\s+object|object)\s*\{[^}]*"
                                     r"(?:Context|Activity|View|Fragment)\s*[?]?\s*=|"
                                     r"static\s+.*(?:Context|Activity)\s+\w+\s*;",
                          CODE_SUFFIXES,
                          "The Application object held statically is conventional and safe.", None),
    "MEM-LIFECYCLE-004": ("HIGH", r"GlobalScope\.launch|Thread\s*\(\s*\)?\s*\{|"
                                  r"Executors\.new\w+|\bTimer\s*\(", CODE_SUFFIXES,
                          "A deliberately application-scoped supervisor is a false positive.", None),
    "MEM-PROC-002": ("MEDIUM", r"android:process\s*=", {".xml"},
                     "Deliberate isolation is a valid answer; report as justify-this.", None),
    "MEM-PRESSURE-001": ("MEDIUM", r"TRIM_MEMORY_(RUNNING_LOW|RUNNING_CRITICAL|RUNNING_MODERATE|"
                                   r"MODERATE|COMPLETE)|onLowMemory", CODE_SUFFIXES,
                         "DEAD CODE only when minSdk >= 34. Live on Android 13 and below.", 34),
    "MEM-OBS-001": ("HIGH", r"ApplicationExitInfo|getHistoricalProcessExitReasons", CODE_SUFFIXES,
                    "Inverted rule: a finding means the check IS present.", None),
}

INVERTED = {"MEM-OBS-001"}


def iter_files(root: Path):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix in CODE_SUFFIXES | BUILD_SUFFIXES:
            yield path


def scan(root: Path, min_sdk: int | None) -> dict:
    findings: list[dict] = []
    gated: list[dict] = []
    seen_inverted: set[str] = set()

    for path in iter_files(root):
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for rule_id, (severity, pattern, suffixes, note, gate) in RULES.items():
            if path.suffix not in suffixes:
                continue
            for match in re.finditer(pattern, text, re.IGNORECASE | re.MULTILINE):
                line = text.count("\n", 0, match.start()) + 1
                if rule_id in INVERTED:
                    seen_inverted.add(rule_id)
                    continue
                record = {
                    "rule": rule_id,
                    "severity": severity,
                    "file": str(path.relative_to(root)).replace("\\", "/"),
                    "line": line,
                    "match": match.group(0)[:80],
                    "verify": note,
                }
                if gate is not None and min_sdk is not None and min_sdk < gate:
                    record["gated"] = (
                        f"NOT REPORTED: rule needs minSdk >= {gate}; project minSdk is {min_sdk}. "
                        "These branches are live on that fleet."
                    )
                    gated.append(record)
                else:
                    if gate is not None and min_sdk is None:
                        record["verify"] += (" minSdk unknown -- confirm before reporting; "
                                             "this rule is version-gated.")
                    findings.append(record)
                break  # one finding per rule per file keeps the worklist readable

    order = {"BLOCKER": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
    findings.sort(key=lambda item: (order[item["severity"]], item["rule"], item["file"]))

    missing_observability = [
        rule for rule in INVERTED if rule not in seen_inverted
    ]

    return {
        "candidates_not_defects": True,
        "project": str(root),
        "min_sdk": min_sdk,
        "findings": findings,
        "suppressed_by_version_gate": gated,
        "absent_but_required": [
            {"rule": rule, "severity": RULES[rule][0],
             "note": "No occurrence found. Without it, memory-limit kills are invisible."}
            for rule in missing_observability
        ],
        "reminder": (
            "Static candidates only. No footprint was measured. Baseline, accumulation rate, "
            "record counts and the enforced ceiling remain UNKNOWN -- see references/measurement.md."
        ),
    }


SELF_TEST_FILES = {
    "positive.kt": 'val cache = HashMap<String, Item>()\n'
                   'val bmp = BitmapFactory.decodeFile(path)\n',
    "AndroidManifest.xml": '<application android:largeHeap="true" />\n',
}


def self_test() -> int:
    import tempfile
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        for name, content in SELF_TEST_FILES.items():
            (root / name).write_text(content, encoding="utf-8")
        result = scan(root, min_sdk=34)
    rules = {item["rule"] for item in result["findings"]}
    expected = {"MEM-CACHE-001", "MEM-BITMAP-001", "MEM-BUILD-001"}
    missing = expected - rules
    absent = {item["rule"] for item in result["absent_but_required"]}
    ok = not missing and "MEM-OBS-001" in absent
    print(f"detected: {sorted(rules)}")
    print(f"absent-but-required: {sorted(absent)}")
    print(f"self-test -> {'PASS' if ok else 'FAIL (missing ' + str(sorted(missing)) + ')'}")
    return 0 if ok else 1


def render(result: dict) -> str:
    lines = [
        "CANDIDATES, NOT CONFIRMED DEFECTS -- each rule has a false-positive clause.",
        "",
    ]
    if not result["findings"]:
        lines.append("No candidates matched.")
    for item in result["findings"]:
        lines.append(f"{item['severity']:<8} {item['rule']:<20} "
                     f"{item['file']}:{item['line']}")
        lines.append(f"         verify: {item['verify']}")
    for item in result["suppressed_by_version_gate"]:
        lines.append(f"GATED    {item['rule']:<20} {item['file']}:{item['line']}")
        lines.append(f"         {item['gated']}")
    for item in result["absent_but_required"]:
        lines.append(f"{item['severity']:<8} {item['rule']:<20} ABSENT -- {item['note']}")
    lines += ["", result["reminder"]]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path)
    parser.add_argument("--min-sdk", type=int, default=None)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return self_test()
    if args.project is None:
        parser.error("--project is required unless --self-test is used")
    if not args.project.is_dir():
        print(f"No such directory: {args.project}", file=sys.stderr)
        return 2

    result = scan(args.project, args.min_sdk)
    print(json.dumps(result, indent=2) if args.json else render(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
