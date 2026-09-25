plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
}

val MARKETING_VERSION = "3.0.1"

val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
    ?: MARKETING_VERSION.replace(".", "").toIntOrNull()
    ?: 1

val ciVersionName = (project.findProperty("versionName") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: MARKETING_VERSION

android {
    namespace = "com.lucent.app"
    compileSdk = 36

    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.jiaying.yuan.lucentapp"
        minSdk = 28
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DLUCENT_ENABLE_VULKAN=ON"

                System.getenv("LUCENT_SPIRV_HEADERS_DIR")?.takeIf { it.isNotBlank() }?.let { dir ->
                    arguments += "-DSPIRV-Headers_DIR=$dir"
                    arguments += "-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH"
                }

                System.getenv("LUCENT_VULKAN_INCLUDE_DIR")?.takeIf { it.isNotBlank() }?.let { inc ->
                    arguments += "-DVulkan_INCLUDE_DIR=$inc"
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories += layout.buildDirectory.dir("rustJniLibs").get().asFile.path
            kotlin.directories += rootProject.file("shared/src/main/kotlin").path
        }
        getByName("test") {
            kotlin.directories += rootProject.file("shared/src/test/kotlin").path
        }
        getByName("androidTest") {
            assets.directories += "$projectDir/schemas"
        }
    }

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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val rustProjectDir = rootProject.file("rust")
val rustOutDir = layout.buildDirectory.dir("rustJniLibs")

fun toolWorks(vararg cmd: String): Boolean = try {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    p.inputStream.readBytes()
    p.waitFor() == 0
} catch (t: Throwable) {
    false
}

val cargoNdkReady = rustProjectDir.exists() && toolWorks("cargo", "ndk", "--version")

val cargoNdkBuild = tasks.register<Exec>("cargoNdkBuild") {
    group = "build"
    description = "Compile rust/ into liblucent_native.so for every packaged ABI"
    workingDir = rustProjectDir
    val rustTargets = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    commandLine(
        buildList {
            add("cargo"); add("ndk")
            rustTargets.forEach { add("-t"); add(it) }
            add("-o"); add(rustOutDir.get().asFile.absolutePath)
            add("build"); add("--release")
        }
    )
}

if (cargoNdkReady) {
    tasks.named("preBuild") { dependsOn(cargoNdkBuild) }
} else {
    logger.lifecycle(
        "lucent(:app, config): cargo-ndk not found - the ANDROID build would fall back to its " +
            "Kotlin implementations (irrelevant to :desktop tasks). To enable the Rust fast paths: " +
            "install rustup, run `cargo install cargo-ndk`, and add the Android targets."
    )
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    implementation(libs.biometric)
    implementation(libs.fragment.ktx)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)

    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("dev.rikka.shizuku:aidl:13.1.5")

    implementation(libs.haze)
    implementation(libs.haze.materials)

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
    testImplementation(libs.org.json)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)

    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}
