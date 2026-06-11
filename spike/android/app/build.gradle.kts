// DISPOSABLE Phase-0 spike (OC-5). app module for the INP S0.5 Android probe.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.inpspike"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.inpspike"
        minSdk = 26                 // OC-8 baseline. WebMessageListener reach is documented in InpChannel.kt.
        targetSdk = 35              // API 35; predictive-back observation points target API 34+ (S0.5).
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    buildTypes {
        debug {
            // This probe only ever runs as a debug build against a local dev server.
            isMinifyEnabled = false
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
        viewBinding = false   // The probe builds its UI in code to stay dependency-light.
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")        // OnBackPressedCallback + predictive back
    // androidx.webkit gives us WebViewCompat: addWebMessageListener (channel b),
    // addDocumentStartJavaScript (document-start injection), and feature gating.
    implementation("androidx.webkit:webkit:1.11.0")
}
