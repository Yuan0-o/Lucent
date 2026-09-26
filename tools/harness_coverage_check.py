import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))

HARNESS = os.path.join(REPO, "shared", "src", "main", "kotlin", "com", "lucent", "app", "harness")
TESTS = [
    os.path.join(REPO, "shared", "src", "test", "kotlin"),
    os.path.join(REPO, "desktop", "src", "test", "kotlin"),
    os.path.join(REPO, "app", "src", "test"),
]

INDIRECTLY_COVERED = {
    "HarnessAsk.kt",
    "HarnessVault.kt",
    "SimplePdfText.kt",
    "Ooxml.kt",
    "McpClient.kt",
    "HarnessSpec.kt",
}

TOOL_NAME = re.compile(r'name\s*=\s*"([a-z][a-z0-9_]*)"')
HELPER_NAME = re.compile(r'\btool\(\s*"([a-z][a-z0-9_]*)"')


def kotlin_files(root):
    for folder, _, names in os.walk(root):
        for name in names:
            if name.endswith(".kt"):
                yield os.path.join(folder, name)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def harness_sources():
    for folder, _, names in os.walk(HARNESS):
        for name in sorted(names):
            if name.endswith(".kt"):
                yield os.path.join(folder, name)


def test_text():
    parts = []
    for root in TESTS:
        if not os.path.isdir(root):
            continue
        for path in kotlin_files(root):
            parts.append(read(path))
    return "\n".join(parts)


def main():
    tests = test_text()
    modules = {}
    tool_names = {}
    problems = []

    for path in harness_sources():
        rel = os.path.relpath(path, REPO).replace(os.sep, "/")
        text = read(path)
        base = os.path.basename(path)
        if "HarnessGroupTools" in text and re.search(r"object\s+\w+\s*:\s*HarnessGroupTools", text):
            name = re.search(r"object\s+(\w+)\s*:\s*HarnessGroupTools", text).group(1)
            names = TOOL_NAME.findall(text) + HELPER_NAME.findall(text)
            modules[name] = (rel, names)
            for tool in names:
                tool_names.setdefault(tool, []).append(name)
        covered = base.replace(".kt", "") in tests
        if not covered and base not in INDIRECTLY_COVERED:
            problems.append(f"{rel} has no test file covering it")

    duplicates = {name: owners for name, owners in tool_names.items() if len(owners) > 1}
    for name, owners in sorted(duplicates.items()):
        problems.append(f"tool {name} is declared by more than one module: {', '.join(owners)}")

    print(f"Harness modules: {len(modules)}, tools named in source: {len(tool_names)}")
    thin = []
    for name, (rel, names) in sorted(modules.items()):
        hits = [tool for tool in names if re.search(r'"%s"' % re.escape(tool), tests)]
        share = (len(hits) / len(names)) if names else 0
        print(f"  {name}: {len(names)} tools, {len(hits)} of them named in tests ({share:.0%})")
        if not hits:
            problems.append(f"{name} ({rel}) has no tool named in any test")
        elif share < 0.15:
            thin.append(f"{name}: only {len(hits)}/{len(names)} tools appear in tests")

    for note in thin:
        print(f"::warning::{note}")

    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        print(f"{len(problems)} harness coverage problem(s)")
        return 1
    print("Every harness module is exercised by tests, and no tool name is declared twice.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
