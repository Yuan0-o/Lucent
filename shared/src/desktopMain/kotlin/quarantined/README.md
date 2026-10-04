# Quarantined Android shims (desktop) — 5 shims remain

These files declare `android.*` / `androidx.*` packages so that `shared/src/main`
(which the desktop Gradle module compiles directly into its own source set) can
use Android APIs on the JVM. They are compiled only into `:shared`'s desktop
variant; package names are unchanged, only the directory moved here.

Every shim below is still required — verified 2026-10-04 against all usages in
`shared/src/main`, `desktop/src` and `shared/src/desktopMain`:

- `android/content/Context.kt`: BLOCKED — 50 usages (27 in shared/src/main, 8 in shared/src/desktopMain, 15 in desktop/src). The desktop `PlatformContext` actual is a typealias to this stub; removing it requires redefining the desktop PlatformContext actual and updating all call sites. Blocked until shared/src/main is migrated out of the srcDir injection.
- `android/util/Base64.kt`: BLOCKED — 6 call sites in files compiled for BOTH Android (real API) and desktop (stub) via the shared/src/main srcDir injection. Replacing with java.util.Base64 would change shared data-path behavior; blocked until shared/src/main migration.
- `android/util/Log.kt`: BLOCKED — 4 call sites (2 in shared/src/main fully-qualified, 1 in shared/src/desktopMain CrashShield, 1 in desktop/src LocalLlm). No java.* equivalent; needs a PlatformLog expect/actual abstraction first.
- `androidx/activity/compose/BackHandler.kt`: BLOCKED — 12 usages (10 in shared/src/main, 2 in desktop/src). DesktopBackDispatcher-backed shim; blocked until shared/src/main migration.
- `androidx/compose/ui/platform/LocalContext.kt`: BLOCKED — 47 usages (42 in shared/src/main, 1 in shared/src/desktopMain, 4 in desktop/src). CompositionLocal providing DesktopContext; blocked until shared/src/main migration.

Retire a shim only when `shared/src/main` no longer references its API on the
desktop module's compile classpath; deleting a still-referenced shim breaks
`:desktop:compileKotlin`.
