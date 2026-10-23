plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "se.ditt.relay"
    compileSdk = 34

    defaultConfig {
        applicationId = "se.ditt.relay"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "WORKER_URL",
            "\"" + (System.getenv("WORKER_URL") ?: "") + "\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        create("rel") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KS_PASS").orEmpty().ifBlank { "android" }
            keyAlias = System.getenv("KEY_ALIAS").orEmpty().ifBlank { "relay" }
            keyPassword = System.getenv("KEY_PASS").orEmpty().ifBlank { "android" }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rel")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}
