pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Lucent"
include(":app")
// Windows desktop build (Compose for Desktop). Lives beside :app and never touches it.
include(":desktop")
// P2-4: instrumentation-test module that generates :app's Baseline Profile. A `com.android.test`
// module, not a library — it produces no artifact :app depends on; :app depends on ITS output
// (see the `baselineProfile(project(":baselineprofile"))` line in app/build.gradle.kts).
include(":baselineprofile")
