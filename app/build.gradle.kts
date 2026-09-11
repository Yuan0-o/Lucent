plugins {
    id("com.android.application")
    // Kotlin support is BUILT INTO AGP 9 (kotl.in/gradle/agp-built-in-kotlin): the
    // org.jetbrains.kotlin.android plugin is deliberately NOT applied any more.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ---- Versioning ----
//
// Two numbers, two jobs, and conflating them is what made releases look like they skipped ahead.
//
//   versionCode  An integer Android uses ONLY to decide "is this newer than what's installed?".
//                Users never see it. It must never go backwards, so CI passes the workflow run
//                number: it increases by one on every run, which is exactly what this field wants.
//
//   versionName  The string people actually read ("2.2.1"). It is NOT tied to the run number any
//                more. It comes from MARKETING_VERSION below, or from -PversionName=... which the
//                workflow's optional "Version name" box supplies. Build ten times in a row and it
//                stays 2.2.1; change it only when you decide a release deserves a new number.
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// The user-facing version. Edit this line (or type a value into the workflow box) to change it.
//
// 2.3.0 — the R3 maintenance release: sixteen fixes across the app plus four new Monet themes and
// the background-palette expansion to 126 colours. Highlights: hidden-area notes open again and
// keep their rounded-square cards; restoring writes the pre-restore state as a new history entry;
// chat attachments show file names instead of stacked previews; app-lock brute-force throttling
// and the self-destruct wipe are wired to the lock screen; small-model mode keeps a trimmed
// HIGH-tier cross-chat memory; the API model list gained fuzzy search; crash shield installs on
// launch and its "next launch" note disappears once installed. Realigned with the Windows
// installer (desktop packageVersion).
// 2.2.1 — patch release: no user-facing Android change. The number is realigned with the Windows
// installer (desktop/build.gradle.kts packageVersion) so both platforms of this release report the
// same version again.
// 2.0.0 — the three-group 1.x delivery, integrated. A major bump rather than 1.1.0 because the
// database moves from schema 11 to 15 across four migrations and the on-disk shape is no longer
// readable by a 1.0.0 build. The version people read should say that.
// 2.3.5 - the engineering release: true multi-file chat attachments end to end, the
// single-shared i18n catalogue (Android + Windows), the byte-identical twins moved into
// shared/, and the toolchain brought to the newest official versions (Gradle 9.7.1,
// AGP 9.4.0, Kotlin 2.4.0, KSP 2.3.11, Compose Multiplatform 1.12.0, NDK 28.2).
// 2.4.0 - themes 32, backgrounds in 8 families with Random mode, 1.3x faster launch, a
// UI-independent background clock, live lockout countdowns, and the single-i18n catalogue.
// 2.7.0: the v2.7 line (2.5.0/2.6.0 skipped as planned): two-level composers, Material You
// dynamic colour, attachment-picker fixes, date/language fix, unified password throttling and
// backup coverage v12. See the v2.7.0 release notes.
// 2.7.2: the v2.7.0 follow-through — composers' two-level fold actually wired, cooldowns disable
// biometrics/Windows Hello, display-frame-paced background, theme 18/background 36 pickers with
// Material You hiding, smooth random crossfade, user templates with interrupted-authoring draft,
// assistant "+" new conversation, backup v13 (dynamic-colour flag, chat multi-attachments,
// templates) and the GitHub Actions workflows restored. See the v2.7.2 release notes.
// 2.7.3: card dates re-key on the active language (no more stale-language captions after a
// language switch), composer fold spaced from attachments, custom template chips look exactly like
// the built-ins, an adaptive frame-budget governor keeps the blob background regular on weak GPUs,
// and the first shared-logic unit tests run in CI (code-review report, Phase 1). See the v2.7.3
// release notes.
// 2.7.4: every template chip is a real FilterChip (alignment/colour identical by construction),
// long-press opens edit/delete (built-ins deletable & restorable), the assistant's "keep refining"
// path asks what to change deterministically and re-proposes after your answer, the confirm dialog
// is one row on every device, the new-tag field is 0.8 tall, and on Android the blob background
// renders fully off the UI thread (isolated renderer at half resolution). See the v2.7.4 notes.
// 2.7.5: DeepSeek-style streams no longer type "null" while reasoning; the new-tag box is a true
// 0.8 with its label intact; a cloud storage module (WebDAV: Nutstore/Nextcloud/Koofr/custom) sits
// in Settings between Editor and Security with test, auto-backup mirroring, backup-now and
// restore-from-cloud; Huawei claim removed from the 2.7.0 notes. See the v2.7.5 notes.
// 2.7.6: Notebooks — select several notes or tasks and file them together under a custom name.
// A notebook is pure organization: it holds no content and never modifies the items inside, so the
// same note or task can live in several notebooks and archiving/editing/trashing keep working
// exactly as before. New notes-independent tables notebooks + notebook_items (schema v17, additive
// migration on both Android Room and desktop SQLite). A shared Notebooks screen (list + detail) is
// reachable from the overflow menu of both the Notes and Tasks pages, with per-row rename/delete
// and per-item remove. An "add to notebook" button on the Notes and Tasks selection bars opens a
// picker that files the selection into an existing notebook or creates one on the spot. Notebooks
// and their membership travel through .lcb backups and are re-linked to restored notes/tasks on
// import. Full English / 中文 / 日本語 / 한국어 strings. See the v2.7.6 notes.
// 2.7.7: the architecture release — no new user-facing features, and the point of it is that you
// cannot tell. AssistantController's pure logic moved into testable units (tool-call parsing,
// reply polishing, system prompts), the shared settings components stopped being two drifting
// copies, BackupManager's framing/manifest/import layers became separate files, desktop schema
// migrations and the .lcb round trip are now automated tests, and the desktop test suite grew
// from 4 files to 14 (121 tests) with every refactor verified byte-identical first. See the
// v2.7.7 notes and ARCHITECTURE-UPGRADE.md.
// 2.7.8: UPGRADE-PLAN-2.8, phase P0 closed out (desktop master key bound to the Windows user
// account via DPAPI, check.yml gates every push/PR, the wrapper and a real version catalogue are
// committed, detekt and a lint baseline run in CI, and the two data-loss paths — plaintext rekey
// and a corrupted key file — are now tests, not just careful code) plus the first two P1-1 seams:
// SplashScreen and the export picker moved into shared/, retiring their :app/:desktop twins behind
// small named platform functions (splashTopInset/SplashBackground, rememberExportPdfFontHint)
// instead of a wide impersonated-Android shim. Also fixed along the way, not proposed by the
// original plan: desktop full-text search tried FTS5 MATCH before LIKE and only fell back on a
// thrown exception, which a CJK query never raises — searchNotes/searchTasks are LIKE-only again,
// matching Android and SearchQuery's own documented design; the FTS5 schema and triggers stay,
// unread, for a future pass that indexes every searched column and is proven safe for CJK first.
val MARKETING_VERSION = "2.7.8"
val ciVersionName = (project.findProperty("versionName") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: MARKETING_VERSION

android {
    namespace = "com.lucent.app"
    compileSdk = 36 // Newest androidx requires API 37, whose platform is not on any SDK channel yet; 36 is the newest installable platform (AGP 9.4 supports it).

    // Native code arrives with the local-model feature (llama.cpp via CMake) and the Rust
    // acceleration library. NDK 28.2 is what AGP 9.4 defaults to; CI installs the same build.
    // auto-installs it locally when missing.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.jiaying.yuan.lucentapp"
        // The Vulkan GPU backend calls Vulkan 1.1 core functions (e.g. vkGetPhysicalDeviceFeatures2),
        // which Android only exposes from API 28 (Android 9) — the NDK's API-26 libvulkan.so stub
        // doesn't export them, so linking fails below 28. This is also what upstream llama.cpp uses to
        // build Vulkan for Android (ANDROID_PLATFORM=android-28), and AGP derives ANDROID_PLATFORM
        // from minSdk. So the default (GPU) build needs minSdk 28; a -PcpuOnly build has no Vulkan and
        // keeps the wider Android 8.0 (API 26) reach.
        minSdk = if (project.hasProperty("cpuOnly")) 26 else 28
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName

        ndk {
            // Vulkan GPU + on-device LLMs need a modern 64-bit device, and llama.cpp's optimized CPU
            // kernels (llamafile sgemm) use ARMv8.2 FP16 NEON intrinsics that 32-bit armeabi-v7a
            // simply doesn't have — so the default (GPU) build ships arm64-v8a only. That also roughly
            // halves CI time, since llama.cpp + the Vulkan shaders are compiled per ABI. A -PcpuOnly
            // build additionally includes armeabi-v7a for older 32-bit phones (with the llamafile path
            // disabled for it in CMakeLists.txt, since those FP16 kernels can't compile there).
            // x86_64 stays dropped; to target the emulator, re-add it here AND in the cargo-ndk list.
            abiFilters += if (project.hasProperty("cpuOnly")) listOf("arm64-v8a", "armeabi-v7a")
                          else listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // The GPU (Vulkan) backend for the on-device model is compiled IN by default, so the
                // in-app CPU/GPU switch can really offload to the GPU. The switch itself still
                // defaults to CPU and warns before enabling GPU; and if a device's Vulkan driver
                // can't handle it, LocalLlm falls back to CPU instead of crashing (see
                // cpp/CMakeLists.txt and LocalLlm.kt). The shader compiler (glslc) is taken from the
                // NDK, so no extra tooling install is required.
                //
                // Build a smaller, CPU-only APK with -PcpuOnly (e.g. if the Vulkan build ever fails).
                arguments += "-DLUCENT_ENABLE_VULKAN=${if (project.hasProperty("cpuOnly")) "OFF" else "ON"}"

                // llama.cpp's Vulkan backend does find_package(SPIRV-Headers CONFIG REQUIRED). That
                // package isn't in the NDK, and cross-compiling normally restricts find_package to the
                // NDK sysroot — so CI installs SPIRV-Headers on the host and points this env var at its
                // config dir. Pass it through as SPIRV-Headers_DIR, and allow find_package to look
                // outside the sysroot (BOTH) so a host, header-only package resolves. Only Vulkan
                // builds set the env var; a -PcpuOnly build ignores all of this.
                System.getenv("LUCENT_SPIRV_HEADERS_DIR")?.takeIf { it.isNotBlank() }?.let { dir ->
                    arguments += "-DSPIRV-Headers_DIR=$dir"
                    arguments += "-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH"
                }

                // ggml-vulkan.cpp includes the C++ Vulkan header <vulkan/vulkan.hpp>, which the NDK
                // does NOT ship (it carries only the C header vulkan.h). CI provides Vulkan-Headers
                // (which has vulkan.hpp) and points this env var at its include dir; pass it as
                // Vulkan_INCLUDE_DIR so find_package(Vulkan) uses those headers. The Android
                // libvulkan.so from the NDK is still used for linking — only the headers change.
                System.getenv("LUCENT_VULKAN_INCLUDE_DIR")?.takeIf { it.isNotBlank() }?.let { inc ->
                    arguments += "-DVulkan_INCLUDE_DIR=$inc"
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // Where the cargo-ndk hook below drops liblucent_native.so per ABI. When the Rust
            // toolchain isn't present this directory simply stays empty and the app runs on its
            // Kotlin fallbacks — building without Rust must always keep working.
            jniLibs.srcDir(layout.buildDirectory.dir("rustJniLibs"))
            // ---- The single shared source tree ----
            // The business logic and most UI live ONCE in shared/src/main/kotlin and are compiled
            // by BOTH this module and :desktop (which adds the same directory to its own source
            // set). Editing a file there changes Android and Windows in the same commit — the old
            // twin-file mirror (app/ + desktop/ byte-identical copies enforced by check_twins.py)
            // is gone. Only genuinely platform-bound files remain in this module's own src dir.
            // AGP 9's built-in Kotlin reads Kotlin directories from the kotlin source set, so the
            // shared tree is added there (previously it rode along via java.srcDir under KGP).
            kotlin.srcDir(rootProject.file("shared/src/main/kotlin"))
        }
    }

    // ---- Release signing, fed entirely by environment variables ----
    //
    // No keystore and no password live in this repository. The GitHub Actions workflow decodes the
    // keystore out of a repository *secret* into a temp file and exports these variables for the
    // one Gradle invocation; on a machine without them the release build still assembles — just
    // unsigned — so a plain checkout of this repo can always be built by anyone.
    //
    //   LUCENT_KEYSTORE_FILE      absolute path to the decoded keystore (PKCS12)
    //   LUCENT_KEYSTORE_PASSWORD  store password (the same password protects the key)
    //   LUCENT_KEY_ALIAS          key alias; defaults to "lucent" when unset
    val releaseStorePath = System.getenv("LUCENT_KEYSTORE_FILE")
    signingConfigs {
        if (releaseStorePath != null) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("LUCENT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUCENT_KEY_ALIAS") ?: "lucent"
                keyPassword = System.getenv("LUCENT_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 full-mode: strip unused code and resources, then obfuscate, to shrink the APK.
            // Keep rules live in proguard-rules.pro; the -optimize default file already preserves
            // native-method names, and Compose/Room/OkHttp/SQLCipher ship their own consumer rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // findByName returns null when the environment isn't set, which simply leaves the APK
            // unsigned rather than failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
        // debug uses the SDK's own auto-generated debug key. The previously checked-in
        // lucent-debug.keystore (with its password in this file) is gone: a signing key committed
        // to a repository authenticates nobody.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // ---- P0-6: lint as a gate, with a committed baseline ----
    // New findings fail the build; findings already in lint-baseline.xml (generated once, then
    // committed) do not, so existing debt does not block work while new debt does. The baseline
    // is regenerated by check.yml's android-jvm-check job when the file is absent and uploaded
    // for the maintainer to commit.
    lint {
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// ---- P0-3: Room exported schema ----
// Room writes the machine-checkable schema JSON for every version into app/schemas (committed),
// so migrations can be validated against what each version ACTUALLY was — by MigrationTestHelper
// on a device and by reviewers in a PR. Previously the Android schema history existed only as
// hand-written MIGRATION_* objects with no record of the real shape.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ---- Rust acceleration library (rust/ at the repo root) ----
//
// Built with cargo-ndk when — and only when — the toolchain is on the PATH. The library is a pure
// accelerator with Kotlin fallbacks everywhere it's consulted (see nativebridge/LucentNative), so
// a machine without Rust still produces a fully working APK; it just runs the JVM crypto and the
// Kotlin animation math instead. CI installs the toolchain (see .github/workflows/build.yml), so
// release builds always carry the fast path.
val rustProjectDir = rootProject.file("rust")
val rustOutDir = layout.buildDirectory.dir("rustJniLibs")

fun toolWorks(vararg cmd: String): Boolean = try {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    p.inputStream.readBytes()
    p.waitFor() == 0
} catch (t: Throwable) {
    false
}

val cargoNdkReady = rustProjectDir.exists() && toolWorks("cargo", "ndk", "--version")

val cargoNdkBuild = tasks.register<Exec>("cargoNdkBuild") {
    group = "build"
    description = "Compile rust/ into liblucent_native.so for every packaged ABI"
    workingDir = rustProjectDir
    // PHASE 3 (Android backlog A-4): derive the target list from the SAME predicate as abiFilters,
    // so the two can no longer drift. The default (GPU) APK packages arm64-v8a only, and building
    // the Rust crate graph for an ABI the APK then filters out was a full extra compile per run —
    // harmless for correctness, pure cost in CI time and failure surface.
    val rustTargets = if (project.hasProperty("cpuOnly"))
        listOf("arm64-v8a", "armeabi-v7a") else listOf("arm64-v8a")
    commandLine(
        buildList {
            add("cargo"); add("ndk")
            rustTargets.forEach { add("-t"); add(it) }
            add("-o"); add(rustOutDir.get().asFile.absolutePath)
            add("build"); add("--release")
        }
    )
}

if (cargoNdkReady) {
    tasks.named("preBuild") { dependsOn(cargoNdkBuild) }
} else {
    logger.lifecycle(
        // PHASE 3 (W-8 / C-11): ASCII only (the em-dash rendered as mojibake in Windows CI logs)
        // and self-locating — this line prints during CONFIGURATION of every Gradle invocation,
        // including desktop-only ones, where it used to read as if the Windows build had just lost
        // its Rust library.
        "lucent(:app, config): cargo-ndk not found - the ANDROID build would fall back to its " +
            "Kotlin implementations (irrelevant to :desktop tasks). To enable the Rust fast paths: " +
            "install rustup, run `cargo install cargo-ndk`, and add the Android targets."
    )
}

dependencies {
    // Versions live in gradle/libs.versions.toml (P0-5). The shared group (OkHttp, coroutines,
    // Haze, org.json) is identical on :desktop by construction.
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    // Biometric unlock for the App Lock. BiometricPrompt requires the host Activity to be a
    // FragmentActivity, so fragment-ktx is pinned to a version that pairs with activity 1.9.x.
    implementation(libs.biometric)
    implementation(libs.fragment.ktx)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)

    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)

    implementation(libs.haze)
    implementation(libs.haze.materials)

    // P0-3: Android JVM unit tests for the pure-logic pieces of the most security-critical code
    // (RecoverableSecret envelope, LocalSecrets prefix handling) — no device, no emulator.
    // Explicit -junit variant: AGP 9's built-in Kotlin does not expose the kotlin("test") helper,
    // and the bare kotlin-test artifact has no content without a test-framework variant selected
    // (AGP's unit-test runner is JUnit4 and does not add junit itself).
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
}
