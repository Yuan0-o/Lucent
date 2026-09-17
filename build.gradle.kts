plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    // ---- Desktop (Windows) build ----
    // The :desktop module is a plain-JVM Compose for Desktop app; it applies these two plugins
    // WITHOUT a version, so the versions must be declared here. Compose Multiplatform 1.12.0 is
    // the release built against Kotlin 2.4.0. (If CI reports "org.jetbrains.compose:1.12.0 not
    // found", change only this one version string to the CMP release that matches Kotlin 2.4.0.)
    id("org.jetbrains.kotlin.jvm") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.12.0" apply false
    // P0-6: static analysis for the JVM module (:desktop). Pinned 1.23.8 — the newest on Maven
    // Central; if Gradle 9.7.1 rejects it, the gate falls back to lint-only (recorded in P0-6).
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    // P2-4: Baseline Profile generation for :app (see :baselineprofile and the `baselineProfile {}`
    // block in app/build.gradle.kts). 1.5.0 is deliberate, not "just the latest": every 1.x release
    // before 1.5.0-alpha01 needs `android.newDsl=false` to work against AGP 9's new DSL at all, and
    // this project is already on AGP 9.4.0 with the new DSL (built-in Kotlin, no
    // org.jetbrains.kotlin.android). 1.5.0-alpha01 is the release that removes that requirement, so
    // it's the floor here, not a preference — confirmed against the current release notes at
    // https://developer.android.com/jetpack/androidx/releases/benchmark (1.5.0 stable, 2026-09-09).
    id("androidx.baselineprofile") version "1.5.0" apply false
}
