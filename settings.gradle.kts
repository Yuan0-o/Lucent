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
        maven("https://maven.rikka.app/releases")
    }
}
rootProject.name = "Lucent"
include(":app")
include(":desktop")
include(":baselineprofile")
