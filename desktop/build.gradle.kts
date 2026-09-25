import org.jetbrains.compose.ComposeBuildConfig
import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    toolVersion = "1.23.8"
    buildUponDefaultConfig = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir(rootProject.file("shared/src/main/kotlin"))
        }
        test {
            kotlin.srcDir(rootProject.file("shared/src/test/kotlin"))
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:${ComposeBuildConfig.composeMaterial3Version}")
    implementation(libs.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(libs.haze)
    implementation(libs.haze.materials)

    implementation(libs.okhttp)

    implementation(libs.org.json)

    testImplementation(kotlin("test"))
    tasks.withType<Test> { useJUnitPlatform() }

    implementation(libs.sqlite.jdbc)

    implementation(libs.pdfbox)

    implementation(libs.jna.platform)

}

tasks.register<org.gradle.api.tasks.JavaExec>("cipherSelfCheck") {
    group = "verification"
    description = "Prove at-rest encryption end to end with the resolved sqlite-jdbc driver."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.lucent.desktop.CipherSelfCheckKt")
}

compose.desktop {
    application {
        mainClass = "com.lucent.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Lucent"
            packageVersion = "3.0.0"

            includeAllModules = true

            windows {
                menu = true
                menuGroup = "Lucent"
                shortcut = true
                upgradeUuid = "8f4e2a10-1c3b-4d5e-9a7f-2b6c8d0e1f23"
                dirChooser = true
                perUserInstall = true
                val icoFile = project.file("src/main/resources/icons/lucent.ico")
                if (icoFile.exists()) iconFile.set(icoFile)
            }
        }
    }
}
