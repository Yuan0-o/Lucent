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
}
