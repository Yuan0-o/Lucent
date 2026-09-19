import os, re, sys, importlib.util

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))

spec = importlib.util.spec_from_file_location("catalog", os.path.join(_HERE, "catalog.py"))
cat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cat)

ENTRIES = cat.ENTRIES

spec_d = importlib.util.spec_from_file_location("catalog_desktop", os.path.join(_HERE, "catalog_desktop.py"))
cat_d = importlib.util.module_from_spec(spec_d)
spec_d.loader.exec_module(cat_d)

ANDROID_ONLY = set(cat_d.ANDROID_ONLY)
DESKTOP_ONLY = cat_d.DESKTOP_ONLY

def esc(s: str) -> str:
    s = s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    s = s.replace("\n", "\\n")
    return re.sub(r"\\?\{(\w+)\}", r"${\1}", s)

def is_fn(key: str) -> bool:
    return "(" in key

def fn_name(key: str) -> str:
    return key.split("(")[0]

seen = set()
for e in list(ENTRIES) + list(DESKTOP_ONLY):
    if isinstance(e, str):
        continue
    k = fn_name(e[0]) if is_fn(e[0]) else e[0]
    if k in seen:
        sys.exit(f"DUPLICATE KEY: {k}")
    seen.add(k)
for k in ANDROID_ONLY:
    if k not in seen:
        sys.exit(f"ANDROID_ONLY names a key that is not in catalog.py: {k}")

def base_decl(key, en):
    if is_fn(key):
        return f'    open fun {key}: String = "{esc(en)}"'
    return f'    open val {key}: String = "{esc(en)}"'

def override_decl(key, val):
    if val is None:
        return None
    if is_fn(key):
        return f'    override fun {key}: String = "{esc(val)}"'
    return f'    override val {key}: String = "{esc(val)}"'

def lang_object(name, idx):
    lines = [f"object {name} : Tr() {{"]
    for e in ENTRIES:
        if isinstance(e, str):
            continue
        d = override_decl(e[0], e[idx])
        if d:
            lines.append(d)
    lines.append("}")
    return "\n".join(lines)

header = '''package com.lucent.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val key: String, val label: String) {
    SYSTEM("system", "System"),
    EN("en", "English"),
    ZH("zh", "\\u4e2d\\u6587"),
    JA("ja", "\\u65e5\\u672c\\u8a9e"),
    KO("ko", "\\ud55c\\uad6d\\uc5b4");

    companion object {
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: SYSTEM

        fun systemDefault(): AppLanguage {
            val tag = java.util.Locale.getDefault().language.lowercase()
            return when {
                tag.startsWith("zh") -> ZH
                tag.startsWith("ja") -> JA
                tag.startsWith("ko") -> KO
                else -> EN
            }
        }
    }
}

object L {
    var current: Tr by mutableStateOf(resolve(AppLanguage.SYSTEM))
        private set

    var language: AppLanguage = AppLanguage.SYSTEM
        private set

    fun apply(key: String?) {
        language = AppLanguage.fromKey(key)
        current = resolve(language)
    }

    private fun resolve(lang: AppLanguage): Tr = when (
        if (lang == AppLanguage.SYSTEM) AppLanguage.systemDefault() else lang
    ) {
        AppLanguage.ZH -> Zh
        AppLanguage.JA -> Ja
        AppLanguage.KO -> Ko
        else -> En
    }
}

val S: Tr get() = L.current

fun lucentLocale(): java.util.Locale = when (
    if (L.language == AppLanguage.SYSTEM) AppLanguage.systemDefault() else L.language
) {
    AppLanguage.ZH -> java.util.Locale.SIMPLIFIED_CHINESE
    AppLanguage.JA -> java.util.Locale.JAPANESE
    AppLanguage.KO -> java.util.Locale.KOREAN
    else -> java.util.Locale.ENGLISH
}

object LDates {
    private var cachedFor: Tr? = null
    private val cache = HashMap<String, java.time.format.DateTimeFormatter>()

    fun of(pattern: String): java.time.format.DateTimeFormatter {
        val now = L.current
        if (cachedFor !== now) {
            cache.clear()
            cachedFor = now
        }
        return cache.getOrPut(pattern) { java.time.format.DateTimeFormatter.ofPattern(pattern, lucentLocale()) }
    }
}

'''

def lang_object_for(entries, name, idx):
    lines = [f"object {name} : Tr() {{"]
    for e in entries:
        if isinstance(e, str):
            continue
        d = override_decl_for(e, idx)
        if d:
            lines.append(d)
    lines.append("}")
    return "\n".join(lines)

def emit(entries, path):
    out = [header]
    out.append("open class Tr {")
    for e in entries:
        if isinstance(e, str):
            out.append("    " + e if e else "")
        else:
            out.append(base_decl_for(e))
    out.append("}")
    out.append("")
    out.append("object En : Tr()")
    out.append("")
    out.append(lang_object_for(entries, "Zh", 2))
    out.append("")
    out.append(lang_object_for(entries, "Ja", 3))
    out.append("")
    out.append(lang_object_for(entries, "Ko", 4))
    out.append("")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    n = sum(1 for e in entries if not isinstance(e, str))
    print(f"Wrote {path}: {n} entries")

CONDITIONAL = getattr(cat, "CONDITIONAL_ENTRIES", {})

def base_decl_for(e):
    key = e[0]
    if key in CONDITIONAL:
        return f"    open fun {key}: String = {CONDITIONAL[key][0]}"
    return base_decl(key, e[1])

def override_decl_for(e, idx):
    key = e[0]
    if key in CONDITIONAL:
        expr = CONDITIONAL[key][idx - 1]
        if expr is None:
            return None
        return f"    override fun {key}: String = {expr}"
    return override_decl(key, e[idx])

all_entries = list(ENTRIES)
all_entries.append("")
all_entries.extend(DESKTOP_ONLY)
emit(all_entries, os.path.join(_REPO, "shared", "src", "main", "kotlin", "com", "lucent", "app", "i18n", "I18n.kt"))
