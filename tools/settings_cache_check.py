import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))

CACHE_FILE = "shared/src/main/kotlin/com/lucent/app/data/SettingsCache.kt"

MODULES = {
    "app": ["app/src", "shared/src"],
    "desktop": ["desktop/src", "shared/src"],
}


def sources(roots):
    found = []
    for root in roots:
        for directory, _, files in os.walk(os.path.join(REPO, root)):
            if os.sep + "build" + os.sep in directory + os.sep:
                continue
            found += [os.path.join(directory, f) for f in files if f.endswith(".kt")]
    return found


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def main():
    cache_fields = set(re.findall(r"var (\w+):", read(os.path.join(REPO, CACHE_FILE))))
    failures = []

    for module, roots in MODULES.items():
        reads = set()
        writes = set()
        for path in sources(roots):
            text = read(path)
            reads |= {name for name in re.findall(r"SettingsCache\.(\w+)", text) if name in cache_fields}
            writes |= {name for name in re.findall(r"SettingsCache\.(\w+)\s*=", text) if name in cache_fields}
        stale = sorted(reads - writes)
        print(f"{module}: {len(reads)} cached values read by the UI, {len(writes)} kept in sync")
        if stale:
            failures.append((module, stale))
            for name in stale:
                print(f"::error::{module} reads SettingsCache.{name} but never writes it - "
                      f"the settings UI would flash the default value")

    if failures:
        return 1
    print("Every cached setting the UI reads is written back on change.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
