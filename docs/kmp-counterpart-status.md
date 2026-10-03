# KMP Counterpart Status — Appendix A (42 pairs)

Tracks conversion of the 42 same-path counterpart pairs (app vs desktop) to
`expect`/`actual` in `:shared` (or a unified `platformMain` implementation).

- **converted** — pair deleted from both app modules; contract enforced in `:shared`
- **blocked** — cannot convert cleanly; reason verified, do not force
- **pending** — still duplicated in `app/src/main/java` and `desktop/src/main/kotlin`

## Converted (26)

| Pair | Mechanism |
|---|---|
| data/AppDatabase.kt | expect/actual |
| data/AttachmentAccess.kt | expect/actual |
| data/AttachmentStore.kt | expect/actual |
| data/CrashShield.kt | expect/actual |
| data/Daos.kt | expect/actual |
| data/DataKeys.kt | expect/actual |
| data/DatabaseEncryption.kt | expect/actual (PlatformContext params) |
| data/Entities.kt | expect/actual |
| data/LocalSecrets.kt | expect/actual |
| data/PlatformFont.kt | actuals in androidMain/desktopMain |
| data/SettingsRepository.kt | expect/actual |
| data/ShareIntegration.kt | expect/actual |
| data/StartupLog.kt | expect/actual |
| data/UsageTracker.kt | expect/actual |
| local/PlatformModel.kt | actuals in androidMain/desktopMain |
| nativebridge/LucentNative.kt | expect/actual |
| reminders/Notifications.kt | expect/actual |
| reminders/ReminderScheduler.kt | expect/actual (PlatformContext in fire()) |
| ui/AppLockScreen.kt | unified platformMain (copies still present in app/desktop, shadowed) |
| ui/ClipboardUtil.kt | expect/actual |
| ui/Haptics.kt | expect/actual (PlatformContext params) |
| ui/LucentFonts.kt | unified shared/src/main + expect/actual PlatformFontCompat.fontFamily(fontKey, path) |
| ui/PlatformExport.kt | unified shared/src/main + expect/actual exportPdfFontHintEnabled flag |
| ui/PlatformNotebookCover.kt | unified shared/src/main + expect/actual decodeCoverBitmap(bytes) |
| ui/RightClickModifier.kt | expect/actual |
| ui/Toasts.kt | expect/actual |

## Blocked (4) — verified, do not force

| Pair | Reason |
|---|---|
| GenerationService.kt | Android `Service` + `ILocalLlmEngine.Stub()` AIDL binder, foreground service with `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`. No desktop equivalent; AIDL IPC stack is Android-only. |
| local/LocalLlm.kt | Delegates to `LocalLlmProxy`, which exists only in `app` (Android AIDL client of the GenerationService interface). The pair cannot convert without the Android-only IPC stack. |
| data/AutoBackupRunner.kt | Backup destination logic is Android SAF (`DocumentsContract`, `ContentResolver`) vs `java.io.File`; calls `BackupManager.exportEncrypted`, and `BackupManager` itself still carries Android imports — converting this pair requires converting `BackupManager` first (cascade). |
| data/DocumentExport.kt | 477-line diff between copies; doodle export path (`DoodleExport.canvasesOf`, doodle bitmap embedding into docx/pdf) is platform-specific canvas rendering. Needs DoodleCanvas multiplatform work first. |
| ui/PlatformSplash.kt | :shared androidMain has neither `androidx.compose.foundation:foundation-layout` (for `statusBarsPadding()`) nor the app module's `R` (for the font resource). `platform(libs.compose.bom)` is unresolvable inside KMP source-set dependency blocks (KotlinDependencyHandler has no `platform()`), and pinning foundation-layout to the BOM version string (2026.04.01) fails — the BOM manages it to 1.11.0. Two fix attempts failed 2026-10-03; reverted. Candidate for a future retry: `implementation(project.dependencies.platform(...))` workaround or a BOM-free status-bar inset in androidMain, plus moving great_vibes.ttf into :shared androidMain res. |

## Pending (12)

| Pair | Notes |
|---|---|
| ui/AssistantScreen.kt | TANGLED CLUSTER — circular deps AppNavigation ↔ SettingsScreen ↔ AssistantController ↔ LocalLlm/AIDL. Leave for last. |
| ui/SettingsScreen.kt | TANGLED CLUSTER (2549 lines). Leave for last. |
| ui/AttachmentUi.kt | 504 lines; after leaves |
| ui/AttachmentViewer.kt | 349 lines; after leaves |
| ui/ExpandableTextField.kt | 285 lines; after leaves |
| ui/ImageEditor.kt | 461 lines; after leaves |
| ui/UiComponents.kt | 628 lines; after leaves |
| ui/Dictation.kt | 156 lines |
| ui/PlatformDiffuseBackground.kt | 81 lines; Android ValueAnimator/ContentObserver/Lifecycle vs desktop |
| ui/PlatformNotes.kt | 63 lines; divergent pickers/share — medium |
| ui/PlatformSettings.kt | 117 lines |

## Rules for converters

- No behavior changes; NO code comments (code_hygiene_check enforces).
- Version stays 3.1.0 in all three places; no tags/releases/PRs.
- Never hand-edit `I18n.kt` — regenerate via `tools/i18n/gen_i18n.py` only.
- Commits small, English, one pair per commit.
- Before EVERY push, from repo root run all 6 checks:
  `python3 tools/code_hygiene_check.py`, `python3 tools/version_check.py`,
  `python3 tools/settings_cache_check.py`, `python3 tools/harness_coverage_check.py`,
  `python3 tools/compose_scroll_check.py`, `python3 tools/i18n/gen_i18n.py --check`.
- After pushing each pair: `gh workflow run check.yml` (check.yml is dispatch-only),
  wait for FOUR-GREEN (source-hygiene, android-jvm-check, desktop-jvm-check,
  windows-jvm-check). Red → fix forward or revert that pair; never leave main red.
