plugins {
    id("com.android.application") version "8.12.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    // ---- Desktop (Windows) build ----
    // The :desktop module is a plain-JVM Compose for Desktop app; it applies these two plugins
    // WITHOUT a version, so the versions must be declared here. Compose Multiplatform 1.11.1 is the
    // release built against Kotlin 2.4.0. (If CI reports "org.jetbrains.compose:1.11.1 not found",
    // change only this one version string to the CMP release that matches Kotlin 2.4.0.)
    id("org.jetbrains.kotlin.jvm") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}
