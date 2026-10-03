plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
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
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
            }
        }
        val androidMain by getting {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(libs.core.ktx)
                implementation(libs.activity.compose)
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
            }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
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

android {
    namespace = "com.lucent.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
