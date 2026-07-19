plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.lucent.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jiaying.yuan.lucentapp"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"
    }

    // ---- Release signing, fed entirely by environment variables ----
    //
    // No keystore and no password live in this repository. The GitHub Actions workflow decodes the
    // keystore out of a repository *secret* into a temp file and exports these variables for the
    // one Gradle invocation; on a machine without them, the release build still assembles — just
    // unsigned — so nothing here can ever block a checkout from building.
    //
    //   LUCENT_KEYSTORE_FILE      absolute path to the decoded .keystore (PKCS12)
    //   LUCENT_KEYSTORE_PASSWORD  store password (same password protects the key)
    //   LUCENT_KEY_ALIAS          key alias; defaults to "lucent" when unset
    val releaseStorePath = System.getenv("LUCENT_KEYSTORE_FILE")
    signingConfigs {
        if (releaseStorePath != null) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("LUCENT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUCENT_KEY_ALIAS") ?: "lucent"
                keyPassword = System.getenv("LUCENT_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // findByName: null when the env isn't set, which simply leaves the APK unsigned.
            signingConfig = signingConfigs.findByName("release")
        }
        // The debug variant now uses the SDK's auto-generated debug keystore (the checked-in
        // lucent-debug.keystore is gone — a key in a public repository signs nothing worth trusting).
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.7.2")
}
