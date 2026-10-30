plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.maxworld.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.maxworld.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "1.3.3"

        // The Xray core and the tunnel library are built for these two ABIs only.
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    // One fixed key for every build, so a new APK installs over the old one (no "package conflicts").
    signingConfigs {
        getByName("debug") {
            storeFile = file("maxworld.keystore")
            storePassword = "android"
            keyAlias = "maxworld"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK is installable directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Built from source by the GitHub workflow (see BUILD_WORKFLOW.yml) and copied here.
    implementation(files("libs/libv2ray.aar"))
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
