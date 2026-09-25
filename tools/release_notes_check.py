import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))

LANGUAGES = ["简体中文", "日本語", "한국어"]
MIN_WORDS = 300
MAX_WORDS = 700


def check(text):
    problems = []
    lines = text.split("\n")

    if not lines or not lines[0].startswith("# "):
        problems.append("the notes must open with a first-level title, '# Title'")
    if any(line.startswith("Lucent ") and line.startswith("# ") for line in lines[:3]):
        problems.append("the title is the bare subtitle, without the 'Lucent x.y.z —' prefix")

    details = re.findall(r"<details>\s*<summary>(.*?)</summary>(.*?)</details>", text, re.S)
    summaries = [name.strip() for name, _ in details]
    if summaries != LANGUAGES:
        problems.append(
            "expected three <details> blocks summarised 简体中文, 日本語, 한국어 in that order, found: "
            + (", ".join(summaries) if summaries else "none")
        )
    for name, body in details:
        if not re.search(r"^# .+$", body, re.M):
            problems.append(f"the {name} block needs its own first-level title")

    if "### 中文" in text or "### 日本語" in text or "### 한국어" in text:
        problems.append("languages belong in their own <details> blocks, not as ### sections of one block")

    separators = [index for index, line in enumerate(lines) if line.strip() == "---"]
    with_build_info = "**Build info**" in text
    needed = 4 if with_build_info else 3
    if len(separators) < needed:
        problems.append(
            f"expected a '---' rule after the English notes and after every language block "
            f"({needed} of them here), found {len(separators)}"
        )

    english = text.split("<details>")[0]
    words = len(re.findall(r"[A-Za-z][A-Za-z'-]*", english))
    if words < MIN_WORDS or words > MAX_WORDS:
        problems.append(f"the English notes run to {words} words; keep them between {MIN_WORDS} and {MAX_WORDS}")

    if "**Build info**" in text:
        tail = text[text.index("**Build info**"):]
        for field in ("Android APK", "Windows installer", "Built from commit"):
            if field not in tail:
                problems.append(f"the build info block is missing '{field}'")

    return problems, words


def main():
    if len(sys.argv) > 1:
        path = sys.argv[1]
    else:
        path = os.path.join(REPO, "docs", "RELEASE-NOTES-FORMAT.md")
    if not os.path.isfile(path):
        print(f"::error::no release notes at {path}")
        return 1
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    problems, words = check(text)
    print(f"Release notes: {words} English words, {text.count('<details>')} language blocks")
    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        return 1
    print("The notes match the house format: one title, three language blocks, build info last.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
