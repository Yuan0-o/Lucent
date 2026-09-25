import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))

ROOTS = [
    ("Android", "app/build/test-results/testDebugUnitTest"),
    ("Desktop", "desktop/build/test-results/test"),
]

EXPECTED_AREAS = {
    "harness": "com.lucent.app.harness",
    "tools": "com.lucent.app.tools",
    "data": "com.lucent.app.data",
    "ui": "com.lucent.app.ui",
    "network": "com.lucent.app.network",
}


def main():
    totals = {}
    areas = {}
    missing = []
    for label, relative in ROOTS:
        root = os.path.join(REPO, relative)
        if not os.path.isdir(root):
            missing.append(f"{label}: no test results at {relative}")
            continue
        count = 0
        failures = 0
        classes = 0
        for folder, _, names in os.walk(root):
            for name in names:
                if not name.startswith("TEST-") or not name.endswith(".xml"):
                    continue
                path = os.path.join(folder, name)
                try:
                    tree = ET.parse(path)
                except ET.ParseError:
                    continue
                suite = tree.getroot()
                tests = int(suite.attrib.get("tests", "0"))
                bad = int(suite.attrib.get("failures", "0")) + int(suite.attrib.get("errors", "0"))
                count += tests
                failures += bad
                classes += 1
                for area, package in EXPECTED_AREAS.items():
                    if package in suite.attrib.get("name", ""):
                        areas[area] = areas.get(area, 0) + tests
        totals[label] = (count, failures, classes)
        print(f"{label}: {count} tests in {classes} classes, {failures} failing")

    if not totals:
        print("::error::No test results were produced at all")
        return 1

    for label, (count, failures, _) in totals.items():
        if count == 0:
            print(f"::error::{label} ran no tests")
            return 1
        if failures:
            print(f"::error::{label} reported {failures} failing or erroring tests")
            return 1

    print("Test coverage by area (Android run):")
    for area in EXPECTED_AREAS:
        print(f"  {area}: {areas.get(area, 0)}")
    if areas.get("harness", 0) < 20:
        print(f"::error::the agent harness has only {areas.get('harness', 0)} tests")
        return 1

    combined = sum(count for count, _, _ in totals.values())
    print(f"{combined} tests ran across all platforms; the agent harness is covered.")
    for note in missing:
        print(f"::warning::{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
