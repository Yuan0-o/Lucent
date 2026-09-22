import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))


def read(path):
    with open(os.path.join(REPO, path), encoding="utf-8") as handle:
        return handle.read()


def first(pattern, text):
    match = re.search(pattern, text, re.MULTILINE)
    return match.group(1) if match else None


def main():
    app = first(r'^val MARKETING_VERSION = "([^"]+)"', read("app/build.gradle.kts"))
    desktop = first(r'packageVersion = "([^"]+)"', read("desktop/build.gradle.kts"))
    shared = first(r'const val VERSION = "([^"]+)"', read("shared/src/main/kotlin/com/lucent/app/LucentBuild.kt"))

    found = {
        "app/build.gradle.kts MARKETING_VERSION": app,
        "desktop/build.gradle.kts packageVersion": desktop,
        "shared LucentBuild.VERSION": shared,
    }
    for label, value in found.items():
        print(f"{label} = {value}")
        if not value:
            print(f"::error::Could not read the version from {label}")
            return 1

    if len(set(found.values())) != 1:
        print("::error::The version numbers disagree: " + ", ".join(f"{k}={v}" for k, v in found.items()))
        return 1

    version = app
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        print(f"::error::Version '{version}' is not in x.y.z form")
        return 1

    print(f"All three agree on {version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
