import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    // XÓA: alias(libs.plugins.google.services)  ← bỏ plugin google-services
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

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )

        // ─── Supabase credentials (thêm vào local.properties) ─────────────────
        // SUPABASE_URL=https://your-project-id.supabase.co
        // SUPABASE_ANON_KEY=your-anon-key
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperties.getProperty("SUPABASE_URL", "")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\""
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

    // ─── Firebase (GIỮ LẠI analytics nếu cần, XÓA auth + firestore) ──────────
    // XÓA: implementation(platform(libs.firebase.bom))
    // XÓA: implementation(libs.firebase.auth)
    // XÓA: implementation(libs.firebase.analytics)
    // XÓA: implementation(libs.firebase.firestore)
    // Nếu vẫn muốn giữ Analytics (không bắt buộc):
    // implementation(platform(libs.firebase.bom))
    // implementation(libs.firebase.analytics)

    // ─── Supabase: dùng OkHttp để gọi REST API (không cần SDK riêng) ─────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // MPAndroidChart
    implementation(libs.mpandroidchart)

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

    // Google Sign-In (GIỮ LẠI – vẫn dùng để lấy idToken cho Supabase)
    implementation(libs.playservices.auth)

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