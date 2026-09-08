# Lucent Upgrade Plan — the road to 2.8

> **执行摘要（中文）**
>
> 这份方案基于对 `Lucent-main` 全库（407 个文件、约 69 300 行 Kotlin/Rust）的实读审计，不是通用最佳实践清单。
> 核心结论有五条：**(1)** `shared/` 目前不是真正的多平台模块，而是被两端 `srcDir` 引入的普通目录，靠桌面端伪造
> `android.*` 包来对齐 API，代价是 `NotesScreen`/`TasksScreen`/`SplashScreen` 等约 7 400 行代码各存两份、
> 差异却只有 1%–7%；**(2)** 桌面端数据库确实是加密的（SQLite3MultipleCiphers），但整条信任链的根
> ——`keys/master.key`——以明文躺在数据目录旁，代码自己称之为 "honest local obfuscation"，这是当前最大的安全落差；
> **(3)** 120 个单元测试全部只存在于 `:desktop` 模块、且只在 Windows 工作流里运行，Android 侧连
> `app/src/test` 目录都不存在；**(4)** 两个 workflow 都只有 `workflow_dispatch`，没有 PR 触发、没有 lint gate，
> 意味着错误只能在手动构建时被发现；**(5)** 仓库没有 Gradle wrapper、没有 version catalog，且 Android 与桌面
> 的 OkHttp（4.12.0 / 5.5.0）和协程（1.8.1 / 1.11.0）版本不一致——今天能编译只因共享代码恰好只用了交集 API。
>
> 方案分四个阶段（P0 加固 → P1 结构 → P2 能力 → P3 长期），每条给出问题、证据、改法（文件级）、
> 工作量与验证方式。阶段一到三可独立交付，互不阻塞。正文用英文，遵守 `LLMs Working Guide` 第 1 条。

---

## 0. How this plan was produced, and what it is not

Every claim below was verified by reading the file named beside it in the archive of the current
`main` (407 files, ~69,300 lines of Kotlin + Rust). Where something could not be verified in this
environment — because there is no JDK, no Android SDK and no device here — it is marked
**[unverified]** rather than asserted.

What this plan deliberately does *not* do:

- It does not propose a rewrite. Lucent's core engineering is sound: the backup format is properly
  authenticated, the assistant uses native function calling rather than string scraping, the schema
  migration walker is tested against a real v11 store, and the desktop cipher is proven in CI before
  packaging. Those are load-bearing and stay.
- It does not re-litigate settled decisions. The Kotlin i18n catalogue instead of `res/values-*`,
  the one-connection-plus-mutex desktop database, and the `.lcb` format are all documented with
  their reasoning and are correct for this project.
- It does not propose work that cannot be validated. The previous cycle correctly parked the
  stateful UI refactors for exactly this reason; this plan re-sequences that work behind a test
  harness that makes it validatable, instead of simply re-proposing it.

### Current inventory

| Module | Kotlin/Rust LOC | Role |
|---|---|---|
| `shared/` | 31,165 | The tree both platforms compile |
| `app/` | 27,689 | Android-bound code — *and duplicated screens* |
| `desktop/` | 27,261 | Desktop shell, `android.*` shims, JDBC core — *and duplicated screens* |
| `rust/` | 351 | PBKDF2 / AES-GCM / animation maths over JNI |

The headline number: `desktop/` and `app/` together hold **27,261 + 27,689 lines for what is
substantially one application**, and roughly 7,400 of those lines are near-identical twins.

---

## Phase P0 — Correctness and safety gates (do these first)

These are the items where the current state can actively lose or expose user data, or let a defect
reach a release unnoticed. None of them changes visible behaviour, so all are low-risk to ship.

### P0-1 — Bind the desktop master key to the machine

**Problem.** `desktop/src/main/kotlin/com/lucent/app/data/LocalSecrets.kt` generates a 32-byte
master key and writes it, Base64-encoded and **unprotected**, to `keys/master.key` in the same
directory as the data it protects. `DataKeys.kt` then wraps both the attachment key and the
database passphrase with it. The file's own KDoc is honest about this:

> "That is honest local obfuscation rather than hardware binding: someone with full access to the
> user's profile directory can recover the values."

The consequence is precise and worth stating plainly: on Windows, **the encrypted database, the
wrapped keys, and the key that unwraps them all sit in `%APPDATA%\Lucent`**. Anything that copies
that folder — a sync client, a backup agent, malware running as the user, a stolen unlocked laptop —
gets everything. Android does not have this weakness: `app/.../DataKeys.kt` wraps keys with the
hardware Keystore, which cannot be exported.

**Fix.** Wrap `master.key` with Windows DPAPI (`CryptProtectData` with
`CRYPTPROTECT_LOCAL_MACHINE` off, so the ciphertext is bound to the *user account*). Reachable from
a pure JVM without shipping native code, via JNA:

1. Add `net.java.dev.jna:jna-platform:5.19.0` (exact pin) to `desktop/build.gradle.kts`. It already
   binds `Crypt32.CryptProtectData` / `CryptUnprotectData`, so no custom JNI is needed.
2. In `LocalSecrets.kt`, introduce a stored-form prefix beside the existing `v1:` / `p1:` scheme —
   call it `d1:` — for "master key was DPAPI-wrapped". Follow the existing prefix discipline exactly;
   that discipline is why this migration can be silent.
3. `writeMasterKeyDurably` wraps the fresh key through DPAPI before the existing fsync-then-rename.
   Keep the fsync: the reasoning in its KDoc (NTFS can order the rename ahead of the bytes) is
   correct and still applies.
4. On load: a `d1:`-prefixed file is unwrapped through DPAPI; a legacy bare-Base64 file is read as
   today, then **re-wrapped in place** and rewritten. Existing installs migrate on first launch with
   no user action and no data touched.
5. If DPAPI is unavailable (non-Windows developer machines — the shim's `filesDir` already supports
   macOS/Linux), fall back to today's behaviour and report it through the existing
   `EncryptionStatus.reportSecrets(...)` channel so the Settings → Security banner tells the truth.
   That channel already exists and is already wired to the UI; reuse it rather than inventing a
   second signal.

**Why this shape.** It preserves the project's established policy — *degrade one notch, never strand
the data* — while removing the plaintext root. It also keeps `.lcb` untouched: backups are keyed by
password or app key through `BackupCrypto`, not by `master.key`, so a DPAPI-wrapped master key does
**not** make a backup machine-specific. Verify this explicitly during implementation.

**Effort.** 4–6 h. **Risk.** Medium — it touches the key path. Mitigate with the test in P0-4 and by
keeping the legacy read path permanently, never gated behind a version check.

**Validation.** A new `LocalSecretsMigrationTest`: legacy bare key is read, re-wrapped, and the value
still opens; a `d1:` file round-trips; a corrupted wrapper degrades to `p1:` and *reports* it rather
than returning plaintext. Manually: seal a value, copy `%APPDATA%\Lucent` to a second Windows user
account, confirm the API key no longer opens there.

### P0-2 — Make CI a gate, not a button

**Problem.** Both `.github/workflows/build.yml:30-31` and `build-windows.yml:30-31` declare exactly
one trigger:

```yaml
on:
  workflow_dispatch:
```

Nothing runs on push or on a pull request. Combined with P0-3, the practical effect is that a commit
that breaks compilation can sit on `main` until somebody manually dispatches a 10-minute release
build to find out. The 120 existing tests only run when the *Windows installer* workflow is
manually dispatched — i.e. the cheapest, fastest signal in the repository is behind the most
expensive job.

**Fix.** Add a third workflow, `.github/workflows/check.yml`, that is deliberately *not* a build:

```yaml
name: check
on:
  push:
    branches: [main]
  pull_request:
jobs:
  jvm-check:
    runs-on: ubuntu-latest      # not windows — this needs no DLL, no WiX, no SDK
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { java-version: '17', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v6
        with:
          gradle-version: '9.7.1'
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      - run: gradle :desktop:test --no-daemon --console=plain
      - uses: actions/upload-artifact@v6
        if: always()
        with:
          name: test-results
          path: desktop/build/reports/tests/test
```

`:desktop:test` compiles the whole shared tree and runs all 120 tests on a plain JVM in a few
minutes on Ubuntu, with no Android SDK, no NDK, no llama.cpp clone and no WiX. It is the highest
value-per-second signal available and it currently runs almost never.

Then remove the duplicated `:desktop:test` invocation from `build-windows.yml:160` once `check.yml`
is green, so the installer workflow stops paying for it on a Windows runner.

**Effort.** 1–2 h. **Risk.** None — additive. **Validation.** Open a throwaway PR with a deliberate
compile error and confirm it goes red.

### P0-3 — Give Android a test source set at all

**Problem.** `app/src/test` and `app/src/androidTest` **do not exist**. Verified: `ls` on both paths
fails. Every one of the 120 tests lives in `desktop/src/test`. The consequence is that
`AppDatabase.kt` (377 lines), `DatabaseEncryption.kt` (312 lines) and the Android `DataKeys` /
`LocalSecrets` — that is, *the entire Android Keystore and SQLCipher path, the most security-critical
code in the product* — have **zero test coverage on the platform that actually ships them**. The
desktop tests cover the desktop twins, which use completely different implementations.

**Fix.** Two source sets, in this order:

1. `app/src/test` (plain JVM, Robolectric-free where possible) with `testImplementation(kotlin("test"))`.
   Start with the pure-logic Android files that need no device: `DataKeys` key-form validation,
   `RecoverableSecret` envelope round-trip, `LocalSecrets` prefix handling.
2. `app/src/androidTest` for what genuinely needs a device or emulator: Room migration validation via
   `MigrationTestHelper`, and a real SQLCipher open/rekey cycle. Run these in CI on an emulator only
   on `main`, not on every PR — they are slow. **[unverified]** whether the current CI budget
   tolerates an emulator job; measure before committing to it.

Critically, add a Room **exported schema** (`room.schemaLocation` KSP arg) and commit the JSON. Right
now the Android schema history exists only as hand-written `MIGRATION_*` objects, with no machine-
checkable record of what each version's schema actually was — so Room cannot validate a migration and
neither can a reviewer.

**Effort.** 8–12 h for the JVM layer; +6 h for the instrumented layer. **Risk.** Low.
**Validation.** The new tests are themselves the validation; confirm they fail when a migration is
deliberately broken.

### P0-4 — Test the two paths that can destroy data silently

**Problem.** Coverage is well-chosen but has two conspicuous holes, both in code that fails *quietly*:

- **The desktop plaintext-rekey path** (`Db.kt`, `openConnection`, case 2). It opens a legacy
  plaintext store, sets the cipher shape, and `PRAGMA rekey`s in place. A failure here "degrades to
  plaintext with a loud StartupLog line". There is no test that a rekey actually produces an
  encrypted header, nor that a *failed* rekey leaves the data readable.
- **Key-file corruption.** `DataKeys.getOrCreate` correctly throws rather than minting a fresh key
  over an unreadable one — a genuinely good decision, since minting would orphan every encrypted
  byte. Untested.

**Fix.** Add to `desktop/src/test/kotlin/com/lucent/app/data/`:

| Test | Asserts |
|---|---|
| `DbEncryptionTest` | fresh store's page 1 does **not** begin `SQLite format 3\0`; a plaintext store is rekeyed in place and every row survives; a wrong key on an existing encrypted store throws an actionable message rather than creating a second empty database |
| `DataKeysTest` | unreadable key file throws instead of re-minting; a zero-length file (the power-loss case the fsync exists for) is treated as unreadable; `resetCacheForTesting` genuinely re-reads |
| `LocalSecretsDegradeTest` | degraded path returns `p1:` ciphertext and **never** the caller's plaintext, and reports through `EncryptionStatus` |
| `BackupRoundTripTest` | an export → import cycle preserves notes, tasks, chats, attachments and settings; a truncated `.lcb` is refused without partially applying |

The last one matters most against the Working Guide's rule 5 (*backup must keep up with every
change*): today nothing mechanically enforces that a newly added settings key is actually carried by
`BackupManifestBuilder`. Consider a reflective test that enumerates `DataKeys`' declared preference
keys and fails when one is absent from the manifest builder's key list — that turns rule 5 from a
discipline into a gate.

**Effort.** 10–14 h. **Risk.** None. **Validation.** Self-validating.

### P0-5 — Pin the toolchain: wrapper + version catalogue

**Problem, part one: no wrapper.** There is no `gradle/wrapper/` directory and no `gradlew`. Both
workflows instead install Gradle through `gradle/actions/setup-gradle@v6` with
`gradle-version: '9.7.1'`. Consequences:

- The build version is declared in two places (`build.yml:90`, `build-windows.yml`) that can drift.
- No contributor can build the project the standard way, and `./gradlew` — the first thing anyone
  tries — does not exist.
- Nothing verifies the Gradle distribution's checksum.

**Fix.** Commit `gradle/wrapper/gradle-wrapper.properties` (with `distributionSha256Sum`),
`gradle-wrapper.jar`, `gradlew`, `gradlew.bat`; switch both workflows from `gradle …` to `./gradlew …`
and drop `gradle-version`. **[unverified]** — generating a wrapper requires a JDK, which this
container lacks; this must be produced on a machine with Gradle, or by committing the four files from
a known-good 9.7.1 distribution.

**Problem, part two: no version catalogue, and a real inconsistency.** Versions are hard-coded across
`build.gradle.kts`, `app/build.gradle.kts` and `desktop/build.gradle.kts`. This is not merely untidy —
it has already produced a genuine divergence in libraries that the **shared** tree compiles against:

| Library | `:app` | `:desktop` |
|---|---|---|
| OkHttp | `4.12.0` | `5.5.0` |
| kotlinx-coroutines | `1.8.1` | `1.11.0` |

`shared/.../network/LlmClient.kt`, `WebSearchClient.kt`, `data/CloudSync.kt` and `data/ReplyFiles.kt`
all compile against **whichever version their host module supplies**. This works today only because
the shared code happens to use the API subset common to OkHttp 4 and 5 (`OkHttpClient`, `Request`,
`toRequestBody`, `toMediaType`, `Credentials`, `FormBody`). That is a coincidence, not a constraint:
the day someone uses an OkHttp 5 idiom in `shared/`, Android breaks — and per P0-2 it breaks
invisibly, because the Android workflow is a manual button.

**Fix.** Create `gradle/libs.versions.toml` with three groups — shared (must be identical on both
platforms: OkHttp, coroutines, Haze, org.json), Android-only (Compose BOM, Room, SQLCipher,
DataStore, biometric), desktop-only (sqlite-jdbc, PDFBox, JNA) — and migrate both module files to
`libs.*` accessors. Unify OkHttp and coroutines to one version each in the shared group. Keep every
existing explanatory comment; several encode expensive lessons (the `sqlite-jdbc` hard-pin comment
documents a real CI failure caused by a `prefer` constraint naming an unpublished version, and the
"NEVER fall back to org.xerial" warning prevents silently shipping an unencrypted store).

**Effort.** 4–6 h. **Risk.** Low, but it must be proved by both workflows going green, since a
resolution change is exactly the class of bug this repository has been bitten by before.

### P0-6 — Static analysis and a lint baseline

**Problem.** No `lint` configuration, no detekt, no ktlint anywhere in either module (verified by
grep). No `baselineProfile` either. For ~69,000 lines with no PR gate, the only reviewer is a human
reading a diff on a phone.

**Fix.** In `check.yml` from P0-2, add `gradle :app:lintRelease` behind
`lint { abortOnError = true; baseline = file("lint-baseline.xml") }`, generating the baseline once so
existing findings do not block work while new ones do. Add detekt with a deliberately minimal
starting rule set (complexity and empty-catch only — the codebase has many intentional
`catch (t: Throwable)` degradation paths, and a maximalist config would drown the signal in
false positives on code that is correct by design).

**Effort.** 3–5 h. **Risk.** Low.

---

## Phase P1 — Structure: stop maintaining the app twice

This is the phase with the largest long-term payoff, and it is where the previous cycle stopped for
good reason. The sequencing below exists so that each step is verifiable *before* the risky one.

### P1-1 — Adopt real multiplatform source sets (`expect`/`actual`)

**Problem.** `shared/` is not a multiplatform module. It is a plain directory added to two
compilations:

```kotlin
// app/build.gradle.kts:180
kotlin.srcDir(rootProject.file("shared/src/main/kotlin"))
```

There is **not one `expect` or `actual` declaration in the repository** (verified by grep). Platform
differences are instead bridged by *impersonating Android on the desktop*: `desktop/src/main/kotlin/`
contains hand-written `android.content.Context`, `android.util.Log`, `android.util.Base64`,
`android.os.SystemClock`, `android.text.format.DateFormat`,
`androidx.compose.ui.platform.LocalContext` and `androidx.activity.compose.BackHandler` — real
Android package names, fake implementations.

This is clever and it works. It is also structurally fragile in a specific way: the shims must
guess which members the shared code touches, and a shared file that starts using one more `Context`
method compiles on Android and fails on desktop — or worse, compiles on both while the shim's
behaviour silently differs from Android's. The `BackHandler` shim is the clearest example: it maps
Android's back gesture onto an Esc-key dispatcher and its own comment concedes this reproduces
Android semantics only "closely enough".

The measured cost of *not* having proper multiplatform seams:

| File | `:app` | `:desktop` | Differing lines | Divergence |
|---|---|---|---|---|
| `ui/SplashScreen.kt` | 634 | 634 | 8 | 1% |
| `ui/ExportSelectionScreen.kt` | 451 | 470 | 19 | 4% |
| `ui/NotesScreen.kt` | 3,014 | 3,007 | 121 | 4% |
| `local/LocalModelStore.kt` | 560 | 557 | 35 | 6% |
| `ui/TasksScreen.kt` | 2,358 | 2,323 | 175 | 7% |
| `data/FontStore.kt` | 380 | 376 | 32 | 8% |

**About 7,400 duplicated lines at 1–8% divergence.** `NotesScreen` — 3,000 lines, the editorial heart
of the product — is maintained twice, and every feature touching it must be written twice and kept
in sync by hand. That directly taxes Working Guide rules 2 and 3.

I read those diffs. The divergence is not diffuse; it is a handful of named seams:

- **File picking** — Android's `rememberLauncherForActivityResult(GetMultipleContents())` vs the
  desktop's synchronous AWT dialog plus `importPickedFiles(List<File>)`.
- **Lifecycle** — Android's `LifecycleEventObserver` on `ON_STOP` vs the desktop's
  `LocalWindowInfo.current.isWindowFocused`.
- **Insets** — `statusBarsPadding()` present on Android, absent on desktop.
- **Background composable** — `IsolatedBlobBackground` vs `FluidGlassBackground`.
- **Toast context** — `context` vs `context.applicationContext`.
- **Sharing** — `Intent` vs `DesktopShare`.

Every one of those is a textbook `expect`/`actual` boundary.

**Fix, in order.** Do *not* start with `NotesScreen`.

1. **Convert `:shared` into a Kotlin Multiplatform module** with `androidMain` and `jvmMain` (or
   `desktopMain`) source sets, `:app` and `:desktop` depending on it normally instead of
   `srcDir`-ing it. **[unverified]** — this interacts with AGP 9's Variant API and the
   `android.sourceset.disallowProvider=false` escape currently set in `gradle.properties`; prove it
   on a branch with both workflows before migrating any code.
2. **Declare the seams.** In `commonMain`, add roughly six expects, e.g.
   ```kotlin
   @Composable expect fun rememberFilePicker(onPicked: (List<PlatformFile>) -> Unit): FilePicker
   @Composable expect fun OnAppHidden(action: () -> Unit)
   @Composable expect fun Modifier.topInsetPadding(): Modifier
   expect fun sharePlainText(context: Context, text: String)
   ```
   with `actual`s in each platform set. Keep them narrow and composable-shaped; a wide "PlatformApi"
   interface would just relocate the problem.
3. **Migrate the cheapest file first: `SplashScreen.kt` (8 differing lines).** One file, one commit,
   both workflows green. It proves the whole mechanism at almost no risk, and it deletes 634
   duplicated lines.
4. Then `ExportSelectionScreen` (19), `FontStore` (32), `LocalModelStore` (35) — each its own commit.
5. Then, and only then, `TasksScreen` (175) and `NotesScreen` (121). By this point the seam
   vocabulary exists and these become mechanical. **These two need device validation**: they are the
   primary editing surfaces and a subtle inset or focus regression is exactly the kind of defect a
   compiler will not catch.
6. Retire each `android.*` shim as its last consumer migrates. `Log` and `Base64` can go early;
   `Context` should go last, since roughly thirty files take it purely as "where do my files live" —
   replace it with a proper `expect class PlatformPaths` rather than keeping a fake `Context` forever.

**Expected outcome.** `:desktop` shrinks by ~7,400 lines. New features touching the main screens are
written once. The shim layer shrinks toward zero.

**Effort.** 30–50 h across ~10 commits. **Risk.** Low per step, high in aggregate — which is exactly
why the steps are ordered by ascending divergence and each ships independently. **Validation.** Both
workflows per commit; device smoke-test of note and task editing before steps 5 and 6 merge.

### P1-2 — Lift `AssistantController` out of a global object

**Problem.** `shared/.../ui/AssistantController.kt` is a **singleton `object`** (line 58) holding all
assistant state directly as Compose snapshot state:

```kotlin
object AssistantController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val genScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var errorText by mutableStateOf("")
    var pendingConfirmation by mutableStateOf<PendingConfirmation?>(null)
    var currentConversationId by mutableStateOf<Long?>(null)
    private val turns = mutableStateListOf<Turn>()
    private var appContextRef: Context? = null
    ...
}
```

It was cut from 2,801 to 1,990 lines by extracting three pure modules — good work, and those
extractions are tested. What remains is the hard part, and the specific costs are:

- **It holds a `Context` in a process-lifetime static** (`appContextRef`). Benign for an application
  context; still a leak channel and an obstacle to testing.
- **Compose state in a singleton is untestable off the UI thread.** Two of the three coroutine scopes
  live for the process lifetime and cannot be substituted, so there is no way to unit-test a send →
  tool-call → confirm → persist cycle. This is why the assistant's *stateful* logic has no tests
  while its pure helpers have 38.
- **Snapshot state as the public API.** `sending`, `messages`, `errorText` are read directly by both
  platforms' `AssistantScreen`, so any state change is inherently a UI-coupled change.

**Fix.** Not "adopt ViewModel" — that is Android-only and would break the desktop. Instead:

1. Convert the state surface to `StateFlow` behind an `AssistantUiState` data class, exposed as
   `val state: StateFlow<AssistantUiState>`. Both platforms read it with `collectAsState()`, which
   works identically on Android and Compose for Desktop.
2. Make it an ordinary **class** taking its dependencies — `CoroutineScope`, `AppDatabase`,
   `LlmClient`, `PlatformPaths` — as constructor parameters. Keep a thin `object AssistantController`
   holder initially that delegates to one instance, so no call site changes in the same commit that
   changes the structure.
3. Split the remainder along the seams the previous cycle already identified: a turn registry /
   generation coordinator (the `turns` list, `stopGeneration`, `stopAllGeneration`, `retryLast`,
   `MAX_LOCAL_TOOL_ROUNDS`) and the persistence helpers.
4. **Then** write the tests that were previously impossible: a fake `LlmClient` returning a scripted
   tool call, asserting that the confirmation is raised with the right editable arguments, that
   declining writes nothing, and that accepting writes exactly once (idempotence under a double-tap).

**Do step 4 before step 3 lands on `main`.** The reason the previous cycle parked this work was the
absence of validation; injecting dependencies first is what creates it.

**Effort.** 20–30 h. **Risk.** Medium-high; it is live UI state on both platforms. Mitigation: the
delegating holder in step 2 keeps the diff mechanical, and P0-2's PR gate means a break surfaces in
minutes rather than at release time.

### P1-3 — Decompose the two `SettingsScreen` monsters

**Problem.** `app/.../SettingsScreen.kt` is 5,106 lines and `desktop/.../SettingsScreen.kt` is 4,963,
with 1,017 differing lines. Together **10,069 lines for one screen**, in a single composable per
platform. It is the largest file in the project by a wide margin and, per the previous cycle's own
notes, its pages are near-identical structures nested inside one function.

**Fix.** Sequence matters — the previous plan was right that this follows the state holder:

1. Extract a shared `SettingsState` holder (same `StateFlow` shape as P1-2) so pages stop reading
   repository state ad hoc.
2. Move page bodies to per-section files under `shared/.../ui/settings/` — `AppearancePage`,
   `AssistantPage`, `SecurityPage`, `BackupPage`, `LocalModelPage`, `AboutPage` — one commit per
   page, **no visual change**, keeping the platform-specific remainder in each module's file.
3. Once the pages are shared, most of the 1,017-line divergence should resolve to the same handful of
   seams as P1-1. **[unverified]** — I have not read both 5,000-line files end to end; a per-page
   diff should be done as the first task of this item to size it properly.

**Effort.** 25–40 h. **Risk.** Medium — settings touch encryption, backup and local-model state, so
each page needs the Working Guide's rule 4 and 5 checks applied individually.

---

## Phase P2 — Capability: what users will actually notice

### P2-1 — Full-text search on an encrypted store

**Problem.** There is **no FTS anywhere** (verified: no `fts` or `MATCH` usage in any module).
Search works in two stages — a coarse SQL `LIKE` from the strongest fragment, then in-memory
structural filtering:

```kotlin
// shared/.../data/SearchQuery.kt:627
fun List<Note>.filterBySearch(query: SearchQuery): List<Note> = filter { query.matches(it) }
```

The two-stage design is sound and the query grammar is genuinely good (15 tests). But `LIKE '%term%'`
cannot use an index, so cost grows linearly with the note count, and the in-memory stage
deserialises every candidate. Fine at 200 notes; visibly slow at 5,000. Indices exist and are
correct (`Entities.kt:14,84,155,184,215,237`; mirrored in `Db.kt:542-543`) — but they index
`updatedAt`, `archived`, `trashedAt`, not text.

**Fix.** Add an FTS5 index as a *derived* table, never as the source of truth:

- Both SQLCipher (Android) and SQLite3MultipleCiphers (desktop) support FTS5, and an FTS5 table
  inside an encrypted database is encrypted with it — no plaintext index leaks. **Verify this
  explicitly on both platforms** before relying on it; it is the crux of the design.
- Schema v18: `CREATE VIRTUAL TABLE notes_fts USING fts5(title, content, content='notes', content_rowid='id')`
  plus triggers, and the same for tasks. Room supports `@Fts5`; the desktop side adds it in
  `migrateSchema` beside the existing `CREATE INDEX IF NOT EXISTS` statements, which is exactly where
  it belongs.
- `SearchQuery` gains an FTS branch for the text fragment and keeps the existing structural matcher
  untouched — so the grammar, its 15 tests, and the ranking all survive.
- Rebuild the index on backup import (`BackupImport.kt`) and after a rekey.
- Keep the `LIKE` path as a fallback when the FTS table is missing, matching the project's existing
  degradation policy.

**Effort.** 12–18 h. **Risk.** Medium — a schema migration on both platforms. `DbMigrationTest`
already proves the walker handles v11→v17, so extend it to v18.

### P2-2 — Semantic recall for the assistant

**Problem.** `MemoryTier` offers single-turn, per-conversation, or a digest of other conversations.
There is no retrieval: the assistant cannot answer "what did I write about the Osaka trip" without
the user finding the note first. Token budgeting exists (`TokenEstimator`, script-aware, 6 tests) but
budgeting is rationing, not recall.

**Fix — but only after P2-1, and framed honestly as optional.**

- Embeddings must not become a privacy regression. Two sources, user's choice, default off:
  a local embedding model through the existing llama.cpp bridge (`llama_embedding`; the mmproj
  precedent shows a second model file is already an accepted pattern), or the configured cloud
  provider's embedding endpoint — with an explicit warning, since it means note text leaves the
  device, which the README currently promises it does not without cause.
- Storage: table `note_embeddings(noteId, model, dim, vec BLOB, updatedAt)` inside the encrypted
  database, so vectors inherit at-rest encryption. Brute-force cosine over a few thousand rows is
  milliseconds; no vector index needed at this scale, and adding one would be premature.
- Must be represented in `.lcb` — either backed up or explicitly and visibly excluded as
  regenerable. Per Working Guide rule 5, "we forgot the new table" is the failure mode to design out.

**Effort.** 25–35 h. **Risk.** Medium. **Sequence.** Ship P2-1 first: FTS alone resolves most
"find my note" cases at a fraction of the cost, and it is the honest baseline against which semantic
search must justify itself.

### P2-3 — Collapse the provider `when` chains

**Problem.** Adding one provider touches `LlmClient.kt` in at least eight places: `urlFor` (304),
`bodyFor` (310), `addAuthHeaders` (317), the reply parser (91), the stream parser's `when (spec)`
(220), `fetchModels` (56), plus the per-spec content builders (`openAiContent` 344,
`anthropicContent` 359) and body builders (374, 437, and Google's). Miss one and the failure is a
runtime parse error, not a compile error.

**Fix.** A sealed `ProviderAdapter` with one implementation per spec:

```kotlin
sealed interface ProviderAdapter {
    fun chatUrl(baseUrl: String, model: String, streaming: Boolean): String
    fun modelsUrl(baseUrl: String): String
    fun authHeaders(apiKey: String): Map<String, String>
    fun buildBody(model: String, history: List<ChatTurn>, systemPrompt: String,
                  tools: List<ToolDefinition>, streaming: Boolean): JSONObject
    fun parseReply(json: JSONObject): ProviderReply
    fun parseStreamEvent(raw: String, acc: StreamAccumulator): StreamEvent
}
```

`LlmClient` keeps its public API (`sendChat`, `streamChat`, `fetchModels`) and becomes transport plus
retry only — its retry logic is careful and worth preserving verbatim, in particular the rule that a
stream is only retried before any text has been shown, so a silent retry cannot duplicate visible
output. Adding a provider then means one new file, and an unimplemented member is a compile error.

**Effort.** 10–14 h. **Risk.** Low-medium; behaviour must be byte-identical. **Validation.** New
`ProviderAdapterTest` asserting request-body shape per provider against fixtures captured from the
current implementation *before* refactoring.

### P2-4 — Android APK size and startup

**Problem.** R8 full mode, resource shrinking and a 69-line keep-rule file are all in place
(`app/build.gradle.kts:206-214`) — that part is done properly. Two gaps remain:

- **No baseline profile** (verified: no `baselineProfile` in any `.kts`). Compose apps typically gain
  a meaningful first-launch improvement from one, and `SplashScreen.kt` is 634 lines of animation
  running exactly when JIT is coldest.
- **No APK splits.** The default build ships `arm64-v8a` only, which keeps things small but excludes
  32-bit devices unless someone builds `-PcpuOnly`. The llama.cpp + Vulkan payload dominates APK
  size, and it is shipped to every user including those who never import a `.gguf`. **[unverified]** —
  I cannot measure the APK here. Measure before acting; if the engine is a large share of it,
  consider delivering it as an on-demand asset rather than a permanent tenant of every install.
- No `mapping.txt` upload in `build.yml`, so a release crash report cannot be deobfuscated. Cheap to
  fix: add it to the existing `upload-artifact` step at line 357.

**Effort.** 6–10 h. **Risk.** Low.

---

## Phase P3 — Longer-term, decide deliberately

### P3-1 — One database layer instead of two

`app/.../data/Daos.kt` is 563 lines of Room; `desktop/.../data/Daos.kt` is 850 lines of hand-written
JDBC reproducing the same reactive API (1,247 differing lines). `Db.kt` adds 646 more, including a
hand-rolled invalidation bus that deliberately mimics Room's behaviour. Two schema definitions are
kept in step by hand, with `SCHEMA_VERSION = 17` asserted equal in a comment.

The honest options:

1. **Keep both.** Zero risk. The duplication persists and every schema change is two migrations.
2. **Room KMP.** Room now supports multiplatform, which would let one set of `@Dao` definitions serve
   both. It would delete ~1,400 lines. **[unverified]** — whether Room KMP's JVM/desktop target
   works with a SQLCipher-scheme driver is the decisive question and must be prototyped before
   committing.
3. **SQLDelight.** Mature multiplatform story, but it would replace *both* layers and rewrite every
   query — the largest change in this document, for a benefit mostly identical to option 2.

**Recommendation:** spike option 2 for a day, on one entity, on a branch. If the encrypted driver
cooperates, it is the highest-leverage deletion in the codebase. If not, stay with option 1 and
revisit later — option 3's cost is not justified by its marginal gain over option 2.

### P3-2 — Isolate the native engine on Android

`LocalLlm.kt` loads models in-process. A `.gguf` OOM or a Vulkan driver fault therefore takes the
whole app down, mid-note. The desktop side already reasons carefully about this (`NativeLoader`
refuses the Vulkan DLL when `vulkan-1.dll` is absent, precisely so a bad load cannot take the local
model down with it) — Android deserves the same defensiveness. Running generation in an `:isolated`
process, with the existing `GenerationService` as the boundary, converts a crash into an error
message. **Effort.** 15–20 h. **Risk.** Medium; IPC for streaming tokens needs care.

### P3-3 — Generated i18n, checked mechanically

`I18n.kt` is 5,362 lines with 4,442 `val` declarations, generated by `tools/i18n/gen_i18n.py` (240
lines) from `catalog.py` (1,451 lines). The design is right and its rationale is well argued —
compile-time safety, no blank UI on a missing translation, instant switching. The gap is that nothing
verifies the generated file matches the catalogue. Add a CI step that regenerates and fails on any
diff; that is ~30 minutes of work and it removes an entire class of drift the file's own KDoc warns
about ("edit translations there, not here, or the two will drift").

---

## Sequencing and effort

| Phase | Items | Effort | Ship independently? |
|---|---|---|---|
| **P0** | Master-key binding, CI gate, Android tests, data-loss tests, wrapper + catalogue, lint | 30–45 h | Yes — each item alone |
| **P1** | Multiplatform seams, assistant state holder, settings decomposition | 75–120 h | Yes, per commit |
| **P2** | FTS, semantic recall, provider adapters, APK/startup | 55–80 h | Yes |
| **P3** | Unified DB, isolated engine, i18n check | 40–60 h + spike | Decide after spike |

**Recommended order.** P0-2 and P0-5 first — a PR gate and a pinned toolchain make everything after
them cheaper and safer to verify. Then P0-1 (the one item with a real security consequence today).
Then P0-3/P0-4 in parallel with P1-1's first three steps, which are individually trivial and
demonstrate the mechanism. P2-1 (FTS) is the item users will feel most, and it is independent of all
of P1 — it can jump the queue if visible improvement matters more than structural work this cycle.

### Version numbering

Per the Working Guide, each shipped increment bumps by +0.01 in both
`app/build.gradle.kts` (`MARKETING_VERSION`) and `desktop/build.gradle.kts` (`packageVersion`, so WiX
upgrades correctly). Suggested: P0 → **2.7.8**–**2.8.0**; P1 → **2.8.x**; P2 → **2.9.x**; a completed
P3-1 justifies **3.0**, since it changes how the product is built rather than what it does.

### Constraints this plan respects

- **Rule 2 (both platforms together).** Every item is specified for Android and desktop
  simultaneously; P1-1 exists specifically to make that cheaper.
- **Rule 3 (notes and tasks in step).** P2-1 indexes both; P1-3 keeps their settings pages symmetric.
- **Rule 4 (encryption preserved).** P0-1 strengthens it; P2-1 and P2-2 place all new tables inside
  the already-encrypted database; P0-4 adds the tests that prove it.
- **Rule 5 (backup keeps up).** P2-1's index is regenerable and excluded deliberately; P2-2's vectors
  must be carried or explicitly excluded; P0-4 proposes a reflective test that turns this rule into a
  build gate rather than a habit.
- **No local toolchain.** Nothing here requires a local build, except the two items marked
  **[unverified]** that genuinely cannot be settled without a JDK (the Gradle wrapper) or a device
  (APK measurement, Room KMP spike).
