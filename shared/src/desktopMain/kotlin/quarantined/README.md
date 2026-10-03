# Quarantined Android shims (desktop)

These files declare `android.*` / `androidx.*` packages so that `shared/src/main`
(which the desktop Gradle module compiles directly into its own source set) can
use Android APIs on the JVM. They are compiled only into `:shared`'s desktop
variant; package names are unchanged, only the directory moved here.

Every shim below is still required — verified 2026-10-04 against all usages in
`shared/src/main`, `desktop/src` and `shared/src/desktopMain`:

- `android/content/Context.kt` — abstract Context plus DesktopContext backed by
  the OS app-data dir; `android.content.Context` is imported pervasively across
  `shared/src/main`.
- `android/os/SystemClock.kt` — `elapsedRealtime()` via `System.nanoTime`;
  used by the desktop actual of `platform/PlatformClock.kt`.
- `android/text/format/DateFormat.kt` — `is24HourFormat()` via `java.text`;
  used by the desktop actual of `ui/PlatformNotes.kt`.
- `android/util/Base64.kt` — `java.util.Base64` bridge; used by
  `data/Attachment.kt`, `data/BackupImport.kt`, `data/BackupManager.kt`,
  `data/BackupManifest.kt`, `data/ReplyFiles.kt`, `data/AttachmentMigration.kt`
  and several harness tools in `shared/src/main`.
- `android/util/Log.kt` — stderr bridge; used by `data/BackgroundWrite.kt`,
  `data/DataCache.kt` and the desktop actual of `data/CrashShield.kt`.
- `androidx/activity/compose/BackHandler.kt` — `DesktopBackDispatcher`-backed
  `BackHandler`; activity-compose is Android-only, used by many screens in
  `shared/src/main`.
- `androidx/compose/ui/platform/LocalContext.kt` — CompositionLocal providing
  `DesktopContext`; compose-ui ships no `LocalContext` on desktop, used by
  many settings pages in `shared/src/main`.

Retire a shim only when `shared/src/main` no longer references its API on the
desktop module's compile classpath; deleting a still-referenced shim breaks
`:desktop:compileKotlin`.
