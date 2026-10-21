plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.nerupudinho.contactringtone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nerupudinho.contactringtone"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Fixed key so every new build installs as an update over the previous one.
    signingConfigs {
        create("release") {
            storeFile = file("../release.jks")
            storePassword = "ringtone123"
            keyAlias = "ringtone"
            keyPassword = "ringtone123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
