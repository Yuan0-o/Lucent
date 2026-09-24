import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))

SOURCE_ROOTS = ["app/src", "desktop/src", "shared/src", "baselineprofile/src"]
MAX_LINES = 3000
GENERATED = {"shared/src/main/kotlin/com/lucent/app/i18n/I18n.kt"}
PAIRS = {")": "(", "]": "[", "}": "{"}


def kotlin_files():
    for root in SOURCE_ROOTS:
        base = os.path.join(REPO, root)
        for folder, _, names in os.walk(base):
            for name in names:
                if name.endswith(".kt") or name.endswith(".kts"):
                    path = os.path.join(folder, name)
                    yield os.path.relpath(path, REPO).replace(os.sep, "/"), path


def skip_string(text, i, n, problems, rel):
    if text.startswith('"""', i):
        end = text.find('"""', i + 3)
        if end < 0:
            problems.append(f"{rel}: unterminated raw string")
            return n
        end += 3
        while end < n and text[end] == '"':
            end += 1
        return end
    i += 1
    while i < n:
        ch = text[i]
        if ch == "\\":
            i += 2
            continue
        if ch == '"':
            return i + 1
        if ch == "\n":
            problems.append(f"{rel}: string literal crosses a line break at offset {i}")
            return i + 1
        if ch == "$" and i + 1 < n and text[i + 1] == "{":
            i = scan(text, i + 2, n, problems, rel, stop_on_close=True)
            continue
        i += 1
    problems.append(f"{rel}: unterminated string literal")
    return n


def scan(text, i, n, problems, rel, stop_on_close=False):
    stack = []
    line = 1 + text.count("\n", 0, i)
    while i < n:
        ch = text[i]
        if ch == "\n":
            line += 1
            i += 1
            continue
        if ch == '"':
            start_line = line
            j = skip_string(text, i, n, problems, rel)
            line = start_line + text.count("\n", i, j)
            i = j
            continue
        if ch == "'":
            j = i + 1
            if j < n and text[j] == "\\":
                j += 2
                while j < n and text[j] != "'" and j - i < 10:
                    j += 1
            else:
                j += 1
            if j < n and text[j] == "'":
                i = j + 1
                continue
            i += 1
            continue
        if ch == "`":
            end = text.find("`", i + 1)
            i = n if end < 0 else end + 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] in "/*":
            problems.append(f"{rel}:{line}: comment found (the codebase carries no comments)")
            if text[i + 1] == "/":
                end = text.find("\n", i)
                i = n if end < 0 else end
            else:
                end = text.find("*/", i + 2)
                i = n if end < 0 else end + 2
            continue
        if ch in "([{":
            stack.append((ch, line))
        elif ch in ")]}":
            if not stack:
                if stop_on_close and ch == "}":
                    return i + 1
                problems.append(f"{rel}:{line}: unmatched '{ch}'")
            else:
                opener, open_line = stack.pop()
                if opener != PAIRS[ch]:
                    problems.append(f"{rel}:{line}: '{ch}' closes '{opener}' opened on line {open_line}")
        i += 1
    if stop_on_close:
        problems.append(f"{rel}: unterminated string template")
        return n
    for opener, open_line in stack:
        problems.append(f"{rel}:{open_line}: '{opener}' is never closed")
    return n


def main():
    problems = []
    checked = 0
    largest = (0, "")
    for rel, path in sorted(kotlin_files()):
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        checked += 1
        lines = text.count("\n") + 1
        if rel not in GENERATED:
            if lines > largest[0]:
                largest = (lines, rel)
            if lines > MAX_LINES:
                problems.append(f"{rel}: {lines} lines exceeds the {MAX_LINES}-line cap; split it")
        if rel in GENERATED:
            continue
        if "\t" in text:
            problems.append(f"{rel}: contains tab characters")
        scan(text, 0, len(text), problems, rel)
    print(f"Kotlin files checked: {checked}")
    print(f"Largest hand-written file: {largest[1]} ({largest[0]} lines)")
    if problems:
        for p in problems:
            print(f"::error::{p}")
        print(f"{len(problems)} hygiene problem(s) found")
        return 1
    print("No comments, balanced brackets, and every file under the size cap.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
