#!/usr/bin/env python3
"""Append 136 I18n.kt entries missing from catalog.py (P3-3 drift repair).

P3-3 requires that catalog.py is the single source of truth for all translations.
The committed I18n.kt carries 136 entries (cloud/notebooks/templates/palette groups, ...)
that were added directly to the generated file and never synced back. This script
extracts them and appends them to catalog.py's ENTRIES list, keeping the existing
catalog order intact.

Also handles the one conditional (if/else) entry, notebookItemsCount, which cannot
be expressed as a flat template — its per-language expressions go into
CONDITIONAL_ENTRIES.

Usage: python3 tools/i18n/append_missing_entries.py --write
"""
import re, sys, os, importlib.util

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
KT = os.path.join(_REPO, "shared", "src", "main", "kotlin", "com", "lucent", "app", "i18n", "I18n.kt")
CATALOG = os.path.join(_HERE, "catalog.py")

DESKTOP_KEYS = {
    "closeToTraySub", "closeToTrayTitle", "exportPdfFontHint", "exportPdfNoFontHint",
    "helloDesc", "helloTitle", "insightsActive", "insightsAllClear", "insightsCompleted",
    "insightsEmpty", "insightsNeedsAttention", "lockHelloFailed", "lockUseWindowsHello",
    "startWithWindowsSub", "startWithWindowsTitle", "tabInsights", "tabSearch",
    "trayExit", "trayOpen",
}

def q(v):
    """Quote a string for Python (catalog.py tuple). None -> None."""
    if v is None:
        return "None"
    esc = v.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
    return '"' + esc + '"'

def decode_kt(s):
    """Decode a Kotlin string literal body into plain text."""
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "n": out.append("\n"); i += 2
            elif n == "t": out.append("\t"); i += 2
            elif n == '"': out.append('"'); i += 2
            elif n == "\\": out.append("\\"); i += 2
            elif n == "$": out.append("$"); i += 2
            else: out.append(n); i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)

def to_catalog_template(s):
    """Convert Kotlin template form (${param}) to catalog form ({param}).
    Also unescapes any remaining Kotlin escapes so catalog stores plain text."""
    return re.sub(r'\$\{(\w+)\}', r'{\1}', s)

def main():
    kt = open(KT, encoding="utf-8").read()
    lines = kt.split("\n")

    # Locate language objects
    def obj_lines(name):
        start = next(i for i,l in enumerate(lines) if l.strip() == f"object {name} : Tr() {{")
        end = next((i for i in range(start+1, len(lines)) if lines[i].strip() == "}"), len(lines))
        return lines[start+1:end]

    # Parse Tr base (en)
    tr_start = next(i for i,l in enumerate(lines) if l.strip() == "open class Tr {")
    tr_end = next(i for i,l in enumerate(lines) if l.strip() == "object En : Tr()")
    tr_lines = lines[tr_start+1:tr_end]

    # Parse Zh, Ja, Ko overrides
    def parse_overrides(l_obj_name):
        d = {}
        for l in obj_lines(l_obj_name):
            m = re.match(r'\s*override (?:val|fun)\s+(\w+)(\([^)]*\))?\s*:\s*String\s*=\s*"((?:[^"\\]|\\.)*)"\s*$', l)
            if m: d[m.group(1)+(m.group(2) or "")] = decode_kt(m.group(3))
        return d
    zh = parse_overrides("Zh")
    ja = parse_overrides("Ja")
    ko = parse_overrides("Ko")

    # Parse Tr entries: collect all sigs with en text (both simple and conditional)
    tr_sigs = {}
    tr_if = {}
    for l in tr_lines:
        m = re.match(r'\s*open fun\s+(\w+)(\([^)]*\))?\s*:\s*String\s*=\s*(if\s.*)$', l)
        if m:
            sig = m.group(1)+(m.group(2) or "")
            tr_if[sig] = m.group(3)
            continue
        m = re.match(r'\s*open (?:val|fun)\s+(\w+)(\([^)]*\))?\s*:\s*String\s*=\s*"((?:[^"\\]|\\.)*)"\s*$', l)
        if m:
            tr_sigs[m.group(1)+(m.group(2) or "")] = decode_kt(m.group(3))

    # Parse override fun if/else expressions from all language objects
    def if_expressions(l_obj_name):
        d = {}
        for l in obj_lines(l_obj_name):
            m = re.match(r'\s*override fun\s+(\w+)(\([^)]*\))?\s*:\s*String\s*=\s*(if\s.*)$', l)
            if m: d[m.group(1)+(m.group(2) or "")] = m.group(3)
        return d
    zh_if = if_expressions("Zh")
    ja_if = if_expressions("Ja")
    ko_if = if_expressions("Ko")

    # Load catalog
    spec = importlib.util.spec_from_file_location("cat", CATALOG)
    cat = importlib.util.module_from_spec(spec); spec.loader.exec_module(cat)
    spec_d = importlib.util.spec_from_file_location("catd", os.path.join(_HERE, "catalog_desktop.py"))
    catd = importlib.util.module_from_spec(spec_d); spec_d.loader.exec_module(catd)

    existing = set()
    for e in cat.ENTRIES:
        if not isinstance(e, str):
            existing.add(e[0])
    for e in catd.DESKTOP_ONLY:
        if not isinstance(e, str):
            existing.add(e[0])

    # Find missing signatures
    missing = []
    conditional = {}
    all_sigs = set(tr_sigs) | set(tr_if)
    for sig in sorted(all_sigs):
        if sig in existing:
            continue
        if sig in DESKTOP_KEYS:
            continue
        # Check if this is a conditional (if/else) entry
        if sig in tr_if or sig in zh_if or sig in ja_if or sig in ko_if:
            conditional[sig] = [tr_if.get(sig), zh_if.get(sig), ja_if.get(sig), ko_if.get(sig)]
            missing.append((sig, None, None, None, None))
            continue
        # Regular entry
        en = to_catalog_template(tr_sigs.get(sig, ""))
        z = to_catalog_template(zh.get(sig)) if sig in zh else None
        j = to_catalog_template(ja.get(sig)) if sig in ja else None
        k = to_catalog_template(ko.get(sig)) if sig in ko else None
        missing.append((sig, en, z, j, k))

    print(f"existing: {len(existing)}, missing found: {len(missing)}, conditional: {len(conditional)}")
    if not missing and not conditional:
        print("nothing to append")
        return

    if "--write" not in sys.argv:
        print("dry run — pass --write to append")
        print("first 5 missing:", [m[0] for m in missing[:5]])
        print("last 5 missing:", [m[0] for m in missing[-5:]])
        return

    # Read catalog.py and append entries before the closing "]" of ENTRIES
    cat_text = open(CATALOG, encoding="utf-8").read()
    # Find the last "]" that closes ENTRIES (before CONDITIONAL_ENTRIES if present)
    marker = "\n]"
    last_close = cat_text.rfind(marker)
    if last_close < 0:
        sys.exit("cannot find ENTRIES closing bracket")

    # Build new entries block
    new_entries = []
    for m in missing:
        new_entries.append(f"    ({q(m[0])}, {q(m[1])}, {q(m[2])}, {q(m[3])}, {q(m[4])}),")
    cat_text = cat_text[:last_close] + "\n" + "\n".join(new_entries) + "\n" + cat_text[last_close:]

    # Append CONDITIONAL_ENTRIES after ENTRIES and before any trailing content
    if conditional:
        cond_block = [
            "",
            "# ---- Conditional (if/else) entries --------------------------------",
            "# sig -> [en, zh, ja, ko] raw Kotlin expressions.",
            "CONDITIONAL_ENTRIES = {",
        ]
        for sig in sorted(conditional):
            exprs = conditional[sig]
            line = "    " + q(sig) + ": [" + ", ".join("None" if e is None else q(e) for e in exprs) + "],"
            cond_block.append(line)
        cond_block.append("}")
        # Insert after ENTRIES closing bracket
        insert_pos = cat_text.find("\n]") + 2
        if insert_pos < 2:
            sys.exit("cannot find ENTRIES end")
        cat_text = cat_text[:insert_pos] + "\n" + "\n".join(cond_block) + "\n" + cat_text[insert_pos:]

    with open(CATALOG, "w", encoding="utf-8") as f:
        f.write(cat_text)
    print(f"appended {len(missing)} entries, {len(conditional)} conditionals")

if __name__ == "__main__":
    main()