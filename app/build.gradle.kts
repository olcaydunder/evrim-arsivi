plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val vCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.evrimarsivi.planlayici"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.evrimarsivi.planlayici"
        minSdk = 29
        targetSdk = 34
        versionCode = vCode
        versionName = "1.0.$vCode"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
