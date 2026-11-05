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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Telefondan eklenen videoları yükleme sınırına sığdırmak için
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
}
