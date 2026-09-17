package com.lucent.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates :app's Baseline Profile by exercising its cold-start path.
 *
 * UNVERIFIED — see P2-4-BASELINE-PROFILE-NOTES.md. This has not been compiled or run; there is no
 * Android SDK, device, or emulator available wherever this file was written. It is written to
 * match the androidx.benchmark 1.5.0 API surface as documented at
 * https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile , not
 * confirmed against a real build.
 *
 * Run with: `./gradlew :app:generateBaselineProfile` (or the variant-specific
 * `:app:generateReleaseBaselineProfile`, if the generic task doesn't resolve against this plugin
 * version — check whichever `./gradlew :app:tasks --group=verification` actually lists). Requires
 * a connected physical device or emulator; this cannot run headless in CI as configured today
 * (see the note about check.yml in P2-4-BASELINE-PROFILE-NOTES.md). The generated
 * baseline-prof.txt lands in app/src/release/generated/baselineProfiles/ — commit it.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    // The applicationId ("com.jiaying.yuan.lucentapp"), NOT the namespace ("com.lucent.app") —
    // BaselineProfileRule.collect needs the package name ART actually installs the app under,
    // which is applicationId. The two differ in this project (see app/build.gradle.kts), and
    // using the namespace here by mistake is a plausible way for this to silently profile nothing.
    private val targetPackageName = "com.jiaying.yuan.lucentapp"

    /**
     * Cold start: process launch through to the first drawn frame, which is also where
     * SplashScreen.kt's animation runs — the coldest possible point for its JIT, and so the
     * clearest place for a Baseline Profile to matter. This is the one journey task P2-4 called
     * "the most basic" and asked for unconditionally.
     */
    @Test
    fun coldStart() = baselineProfileRule.collect(
        packageName = targetPackageName,
        // Also folds these classes/methods into a Startup Profile subset (dex layout
        // optimization), which is free once a Baseline Profile is already being collected — see
        // https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    // Deliberately no second journey (e.g. "open a note"). P2-4 left that judgment call open, and
    // the reasons to skip it are specific to this app rather than general laziness:
    //  - A freshly installed instrumentation target has an empty notes database, so there is no
    //    note to open — a journey like this would need to first create seed data, which is its
    //    own untested addition.
    //  - There's no existing `Modifier.testTag` / `testTagsAsResourceId` anywhere in
    //    shared/src/main/kotlin/com/lucent/app/ui (checked, not touched — it's on the do-not-edit
    //    list for this task), so UiAutomator has nothing stable to target a note card by; a
    //    selector guessed from outside the running app (by text, by index) can't be verified here
    //    and is exactly the kind of thing that looks fine and silently matches nothing on-device.
    // If you want this journey, seeding one note in setupBlock and giving its card a testTag is
    // the straightforward way in; happy to add it once there's a device to check the selector on.
}
