// ---- P2-4: Baseline Profile generator module ----
//
// This is a `com.android.test` module: an instrumentation-test APK in its own right, not a
// library. Its "main" source set (not androidTest — a com.android.test module has no separate
// test/production split) is the whole content of this module. Running it produces
// :app's src/release/generated/baselineProfiles/baseline-prof.txt.
//
// UNVERIFIED: nothing here has been built. There is no Android SDK, NDK, device, or emulator in
// the environment this was written in, so this is "config and a test class written to match the
// current androidx.baselineprofile 1.5.0 API surface", not "config confirmed to compile and run".
// See P2-4-BASELINE-PROFILE-NOTES.md for exactly what to check when you run this for real.
plugins {
    id("com.android.test")
    // No org.jetbrains.kotlin.android here either — same reasoning as :app's plugins block.
    // AGP 9's built-in Kotlin covers com.android.test modules the same way it covers
    // com.android.application ones; applying the old KGP plugin alongside it is what throws
    // "Cannot add extension with name 'kotlin', as there is an extension already registered".
    id("androidx.baselineprofile")
}

android {
    namespace = "com.lucent.app.baselineprofile"
    compileSdk = 36

    // Which app (and which of its build outputs) this module measures. Not a defaultConfig
    // property — it's fixed for the whole module, not something a flavor could override.
    targetProjectPath = ":app"

    defaultConfig {
        // Matches :app's DEFAULT (GPU/Vulkan) variant, which is what most users actually get and
        // so the variant most worth generating a profile against. A -PcpuOnly build of :app
        // (minSdk 26) can still be profiled — this value only constrains what the *test APK*
        // requires the device to be; lower it if you specifically want to generate against a
        // -PcpuOnly build on an API 26/27 device.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// No baselineProfile {} block needed in THIS module: that DSL is for choosing a Gradle Managed
// Device (managedDevices / useConnectedDevices) to run generation on. useConnectedDevices already
// defaults to true, which is the wanted behavior — run against whatever device or emulator is
// connected via adb when you invoke the task. Add one only if you want a GMD instead; see
// https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles.
// See the version note next to `benchmarkMacroJunit4` in gradle/libs.versions.toml for why 1.5.0
// specifically (it's the first androidx.baselineprofile release that doesn't need
// android.newDsl=false against AGP 9).

dependencies {
    // A com.android.test module compiles its one source set with `implementation`, not
    // `androidTestImplementation` — there's no separate app/test split to distinguish here.
    implementation(libs.benchmark.macro.junit4)
}
