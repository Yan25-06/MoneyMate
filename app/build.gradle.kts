import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.group10.moneymate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.group10.moneymate"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GEMINI_API_KEY: Add your key to local.properties as: GEMINI_API_KEY=your_key_here
        // NEVER hard-code the API key directly into source code.
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.fragment)
    annotationProcessor(libs.room.compiler)

    // Firebase (BOM quản lý version tự động)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // MPAndroidChart
    implementation(libs.mpandroidchart)

    // Firebase Firestore
    implementation(libs.firebase.firestore)

    // Biometric
    implementation(libs.biometric)

    // WorkManager
    implementation(libs.workmanager)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit Text Recognition
    implementation(libs.mlkit.text.recognition)

    // Gemini AI
    implementation(libs.generativeai)
    implementation(libs.json)
    implementation(libs.exifinterface)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.fragment.testing)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.workmanager.testing)
    debugImplementation(libs.fragment.testing.manifest)

    implementation(libs.fragment.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
}
