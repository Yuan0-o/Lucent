import org.jetbrains.compose.ComposeBuildConfig

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "com.lucent.shared"
        compileSdk = 36
        minSdk = 28
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val jvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okhttp)
                implementation(libs.org.json)
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.org.json)
                implementation(libs.kotlinx.datetime)
            }
        }
        val androidMain by getting {
            dependsOn(jvmMain.get())
            dependencies {
                implementation(libs.core.ktx)
                implementation(libs.activity.compose)
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
                implementation("androidx.compose.ui:ui-text-android:1.11.0")
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.room.runtime)
                implementation(libs.sqlcipher.android)
                implementation(libs.sqlite.ktx)
                implementation(libs.datastore.preferences)
                implementation(libs.okhttp)
                implementation(libs.haze)
                implementation(libs.haze.materials)
                implementation(libs.org.json)
                implementation("dev.rikka.shizuku:api:13.1.5")
                implementation("dev.rikka.shizuku:provider:13.1.5")
                implementation("dev.rikka.shizuku:aidl:13.1.5")
                implementation(libs.biometric)
                implementation(libs.fragment.ktx)
                implementation("androidx.compose.material3:material3:1.4.0")
                implementation("androidx.compose.material:material-icons-extended:1.7.8")
            }
        }
        val desktopMain by getting {
            dependsOn(jvmMain.get())
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation("org.jetbrains.compose.material3:material3:${ComposeBuildConfig.composeMaterial3Version}")
                implementation(libs.compose.material.icons.extended)
                implementation(libs.haze)
                implementation(libs.haze.materials)
                implementation(libs.okhttp)
                implementation(libs.org.json)
                implementation(libs.sqlite.jdbc)
                implementation(libs.pdfbox)
                implementation(libs.jna.platform)
            }
        }
    }
}
