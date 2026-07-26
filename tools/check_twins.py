#!/usr/bin/env python3
"""PHASE 3 (codebase review C-1, step 1) -- the twin-file drift guard.

The repository keeps two source trees (app/ and desktop/) in which many files are
INTENTIONALLY byte-identical and synced by hand. Nothing used to check that they
actually are, which made the failure mode silent: a fix landing in one tree but
not the other ships two different behaviours under one file name.

This script reads tools/twins.txt -- an explicit manifest of "these two paths are
the same file on purpose" -- hashes every pair, and exits non-zero with a unified
diff for any pair that has drifted. The manifest doubles as documentation: a file
NOT listed here is allowed to differ (a platform seam), and adding a file to the
manifest is a statement of intent.

Run it locally (python3 tools/check_twins.py) or as a CI step in both workflows.
NOTE FOR THE LEAD: wiring it into the workflow YAML is one line per pipeline
(python3 tools/check_twins.py) but touches the YAML, which is outside what this
delivery may change -- see the phase-3 report.
"""
import hashlib
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = pathlib.Path(__file__).resolve().parent / "twins.txt"

ANDROID_PREFIX = "app/src/main/java/"
DESKTOP_PREFIX = "desktop/src/main/kotlin/"


def main() -> int:
    if not MANIFEST.exists():
        print(f"check_twins: manifest not found at {MANIFEST}", file=sys.stderr)
        return 2
    failures = 0
    checked = 0
    for raw in MANIFEST.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        a = ROOT / ANDROID_PREFIX / line
        d = ROOT / DESKTOP_PREFIX / line
        missing = [p for p in (a, d) if not p.exists()]
        if missing:
            # A missing side is drift of the loudest kind: the twin was deleted or
            # moved on one side only.
            print(f"TWIN MISSING: {line} -> {', '.join(str(m) for m in missing)}")
            failures += 1
            continue
        ha = hashlib.sha256(a.read_bytes()).hexdigest()
        hd = hashlib.sha256(d.read_bytes()).hexdigest()
        checked += 1
        if ha != hd:
            failures += 1
            print(f"TWIN DRIFT: {line}")
            # Best-effort diff so the CI log answers "drifted HOW" without a checkout.
            try:
                subprocess.run(["diff", "-u", str(a), str(d)], check=False)
            except OSError:
                pass
    print(f"check_twins: {checked} pairs checked, {failures} problem(s).")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
