import glob
import os
import sys
import xml.etree.ElementTree as ET


def main():
    if len(sys.argv) < 3:
        print("usage: test_report_summary.py <results-dir> <label>")
        return 0
    folder, label = sys.argv[1], sys.argv[2]
    files = sorted(glob.glob(os.path.join(folder, "*.xml")))
    rows = []
    totals = [0, 0, 0, 0]
    failures = []
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            name = suite.get("name", "?").split(".")[-1]
            tests = int(suite.get("tests", 0))
            failed = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            skipped = int(suite.get("skipped", 0))
            seconds = float(suite.get("time", 0) or 0)
            rows.append((name, tests, failed, skipped, seconds))
            totals[0] += tests
            totals[1] += failed
            totals[2] += skipped
            totals[3] += seconds
            for case in suite.findall("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    failures.append(f"{name}.{case.get('name')}")
    lines = [f"### {label} unit tests", ""]
    if not rows:
        lines.append("No test results were produced.")
    else:
        lines.append(f"**{totals[0]} tests** in {len(rows)} suites, {totals[1]} failed, {totals[2]} skipped, {totals[3]:.1f}s")
        lines.append("")
        lines.append("| Suite | Tests | Failed | Skipped |")
        lines.append("|---|---:|---:|---:|")
        for name, tests, failed, skipped, _ in sorted(rows):
            lines.append(f"| {name} | {tests} | {failed} | {skipped} |")
        if failures:
            lines.append("")
            lines.append("Failing cases:")
            lines.extend(f"- `{f}`" for f in failures)
    text = "\n".join(lines) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
