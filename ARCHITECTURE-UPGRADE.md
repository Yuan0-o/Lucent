# Lucent 2.7.7 architecture upgrade — execution log

Status of the approved P0-1..P0-7 upgrade (see the 深度代码体检与升级方案 review). Every item
below has been verified by both CI workflows (signed release APK on Ubuntu, desktop unit tests +
installer build on Windows) unless marked otherwise.

## P0-1 AssistantController decomposition — in progress

The 2801-line controller has been cut to ~1990 lines by extracting three pure, JVM-testable
modules (behaviour unchanged; controllers delegate). All commits verified green on both platforms.

| Commit | Extraction | Tests added |
|---|---|---|
| `a674051` | `assistant/tools/LocalToolCallParser` — GGUF text-protocol tool-call parsing (JSON candidate scan, alias resolution, blank-argument stripping) | `LocalToolCallParserTest` (13 tests) |
| `4d6663b` | `assistant/text/ReplyPolish` — deRobotify markdown scrub, refusal/terse detection, reply-content fallback chain | `ReplyPolishTest` (11 tests) |
| `6a34873` | `assistant/prompts/SystemPrompts` — local/compact/full system-prompt builders | `SystemPromptsTest` (8 tests) |

P0-5 test guardrail work done so far (all JVM, run by the Windows workflow on every push):

- `b89aaad` + `7254db6` — `BackupCryptoTest`: .lcb round trips in APP_KEY and PASSWORD modes,
  wrong/missing password → `WrongPasswordException`, foreign/truncated bytes refused, v2 recovery
  envelope semantics, and the real password-mode contract (isNullOrEmpty decides; whitespace-only
  passwords still engage PASSWORD mode — CI caught the initial wrong assumption).
- `b80f6d6` + `b82cb46` — `SettingsRepositoryTest`: plain-key disk round trip, secret keys
  encrypted at rest (no plaintext in the JSON file), corrupt-file degradation to defaults,
  local-model park/restore. (CI green at `b82cb46`, 64 tests.)
- `7bb5b9d` — P0-6: `DbMigrationTest` + `runSchemaMigrations` extraction: a real v11 SQLite store
  walks to v17 with every row preserved, new columns default correctly, re-runs are silent no-ops
  and partial stores with later columns still finish. (CI green.)
- `1abf544` — `AppToolsTest`: web_search withholding, read-only vs mutating split, editable-argument
  ordering/multiline, blank-edit skipping and malformed-JSON fallbacks. (CI green.)
- `33579e9` — `DueParsingTest` / `TokenEstimatorTest` / `SearchQueryTest`: due-date grammar and
  round-trip, script-aware token math, search grammar + matcher + ranking. (CI pending at time of
  writing.)

Notes:

- `desktop/src/test` runs inside the Windows workflow (`:desktop:test`), so these tests run in CI
  on every push. Android compiles the same shared sources, so compile errors surface in the APK
  workflow.
- CI caught one real defect during this phase: a plain Kotlin string with embedded double quotes
  in `LocalToolCallParserTest` failed `compileTestKotlin` (fixed in `0839fe4`), and the renderer
  tests were hardened against org.json key order (`a3a361b`).
- Repository root file rename (`LLMs Assistant Working Guide` → `LLM Assistants Working Guide`,
  `471c384`) was authored on GitHub by the owner and fast-forward merged; unrelated to this work.

Remaining P0-1 work (stateful core, higher risk, planned next): observable UI state holder, the
turn registry / generation coordinator, and the persistence helpers — each still entangled with
Compose snapshot state, the foreground service and per-turn confirmation.

## P0-2..P0-7

Not started. Both `SettingsScreen.kt` files (app 5461 LOC / desktop 5319 LOC) are near-identical
page structures nested inside one composable; their decomposition will follow a shared
state-holder (P0-4) so pages can move to per-section files without visual change.

## Constraints honoured so far

- Both platforms compile the same shared sources; no platform drift introduced.
- No UI, encryption or `.lcb` backup format changes.
- No Gradle wrapper exists in the repo and this container has no Java/Gradle: all compilation and
  test verification goes through the GitHub Actions workflows (manual dispatch).
