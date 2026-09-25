#!/usr/bin/env python3
"""Finds scrollable containers that are nested inside another scrollable container.

Compose throws at measure time when a vertically scrollable node is measured with
an unbounded maximum height, which is exactly what happens when one scrolling
container is placed inside another one of the same axis. The screen then dies on
first draw, so the pattern is treated as a build failure here.

Two shapes are reported:
  1. a scrollable container inside another scrollable container of the same axis
     in one file, unless the inner one constrains its own height first;
  2. a function that renders its own unguarded scrollable container, called from
     inside a scrollable container, which is how the settings host wraps pages.

Run from the repository root:  python3 tools/compose_scroll_check.py
"""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SCROLL = re.compile(r"\b(verticalScroll|horizontalScroll|LazyColumn|LazyRow|LazyVerticalGrid|LazyHorizontalGrid)\s*\(")
GUARD = re.compile(r"\b(heightIn|height|fillMaxHeight|fillMaxSize|requiredHeight|defaultMinSize|size|weight)\s*\(")
FUN = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*?(?:internal |private |public )?fun\s+([A-Za-z_]\w*)\s*\(", re.M)

HORIZONTAL = {"LazyRow", "LazyHorizontalGrid", "horizontalScroll"}


def enclosing_call(src: str, pos: int):
    depth = 0
    i = pos
    while i > 0:
        c = src[i]
        if c == ")":
            depth += 1
        elif c == "(":
            if depth == 0:
                break
            depth -= 1
        i -= 1
    if i <= 0:
        return None
    m = re.search(r"([A-Za-z_][\w.]*)\s*$", src[:i])
    if not m:
        return None
    name = m.group(1)
    d = 0
    j = i
    while j < len(src):
        if src[j] == "(":
            d += 1
        elif src[j] == ")":
            d -= 1
            if d == 0:
                break
        j += 1
    k = j + 1
    while k < len(src) and src[k] in " \t\r\n":
        k += 1
    block = None
    if k < len(src) and src[k] == "{":
        d2 = 0
        e = k
        while e < len(src):
            if src[e] == "{":
                d2 += 1
            elif src[e] == "}":
                d2 -= 1
                if d2 == 0:
                    break
            e += 1
        block = (k, e)
    return name, i, j, block


def axis_of(kind: str) -> str:
    return "h" if kind in HORIZONTAL else "v"


def body_range(src: str, fun_match) -> tuple[int, int]:
    """Return the source range of a function body, so nesting stays inside it."""
    i = fun_match.end() - 1
    depth = 0
    while i < len(src):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                break
        i += 1
    i += 1
    while i < len(src) and src[i] in " \t\r\n":
        i += 1
    if i < len(src) and src[i] == "=":
        end = i + 1
        depth = 0
        while end < len(src):
            if src[end] in "([{":
                depth += 1
            elif src[end] in ")]}":
                if depth == 0:
                    break
                depth -= 1
            elif src[end] == "\n" and depth == 0 and src[end - 1] != ",":
                break
            end += 1
        return i, end
    if i < len(src) and src[i] == "{":
        depth = 0
        end = i
        while end < len(src):
            if src[end] == "{":
                depth += 1
            elif src[end] == "}":
                depth -= 1
                if depth == 0:
                    break
            end += 1
        return i, end
    return i, i


def scan_file(path: str):
    src = open(path, encoding="utf-8").read()
    occurrences = []
    for m in SCROLL.finditer(src):
        kind = m.group(1)
        enc = enclosing_call(src, m.start() - 1)
        if not enc:
            continue
        name, cstart, _, block = enc
        chain = src[cstart:m.end()]
        occurrences.append(
            {
                "kind": kind,
                "container": name,
                "axis": axis_of(kind),
                "guarded": bool(GUARD.search(chain)),
                "cstart": cstart,
                "line": src.count("\n", 0, cstart) + 1,
                "block": block,
                "chain": " ".join(chain.split())[-70:],
            }
        )
    return src, occurrences


def kt_files():
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in {".git", "build", ".gradle", ".idea"}]
        for name in files:
            if name.endswith(".kt"):
                yield os.path.join(base, name)


def main() -> int:
    rel = lambda p: os.path.relpath(p, ROOT)  # noqa: E731
    problems: list[str] = []
    pages: dict[str, tuple[str, int]] = {}
    files: dict[str, tuple[str, list]] = {}

    for path in kt_files():
        src, occurrences = scan_file(path)
        files[path] = (src, occurrences)
        for m in FUN.finditer(src):
            name = m.group(1)
            _, body_end = body_range(src, m)
            nested = [o for o in occurrences if m.end() < o["cstart"] < body_end]
            if not nested:
                continue
            if all(o["guarded"] or o["axis"] == "h" for o in nested):
                continue
            pages.setdefault(name, (path, src.count("\n", 0, m.start()) + 1))

    for path, (src, occurrences) in files.items():
        for outer in occurrences:
            if outer["axis"] != "v" or not outer["block"]:
                continue
            start, end = outer["block"]
            for inner in occurrences:
                if inner is outer or inner["axis"] != "v" or inner["guarded"]:
                    continue
                if start < inner["cstart"] < end:
                    problems.append(
                        f"{rel(path)}:{outer['line']} {outer['container']}({outer['kind']}) "
                        f"contains {inner['container']}({inner['kind']}) at line {inner['line']} "
                        f"without a height bound"
                    )
            for name, (page_path, page_line) in pages.items():
                for call in re.finditer(rf"(?<![\w.]){re.escape(name)}\s*\(", src):
                    if not (start < call.start() < end):
                        continue
                    owner = src.rfind("fun ", 0, start)
                    if owner != -1 and re.match(rf"\s*{re.escape(name)}\s*\(", src[call.end() - 1:]):
                        continue
                    problems.append(
                        f"{rel(path)}:{src.count(chr(10), 0, call.start()) + 1} calls {name} "
                        f"inside {outer['container']}({outer['kind']}) at line {outer['line']}, "
                        f"but {name} scrolls itself ({rel(page_path)}:{page_line})"
                    )

    unique = sorted(set(problems))
    for item in unique:
        print(item)
    print(f"nested scroll problems: {len(unique)}")
    return 1 if unique else 0


if __name__ == "__main__":
    sys.exit(main())
