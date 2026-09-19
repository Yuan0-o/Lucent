plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.lucent.app.baselineprofile"
    compileSdk = 36

    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}


dependencies {
    implementation(libs.benchmark.macro.junit4)
}
