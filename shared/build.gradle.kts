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
        val commonMain by getting {
            dependencies {
                implementation(libs.org.json)
            }
        }
        val androidMain by getting {
            kotlin.srcDir("src/platformMain/kotlin")
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
                implementation(libs.androidx.material3)
            }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/platformMain/kotlin")
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
