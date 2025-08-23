plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.XRTEST"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.XRTEST"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose)
    implementation(libs.runtime)
    implementation(libs.androidx.scenecore)
    
    // 📷 CameraX API for AR Glass camera processing
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.camera:camera-extensions:1.4.0")
    
    // 🔧 Guava for ListenableFuture support (required by CameraX)
    implementation("com.google.guava:guava:31.1-android")
    
    // 🎤 Speech Recognition and TTS for voice interaction
    implementation("androidx.core:core-ktx:1.13.1")
    
    // 🌐 HTTP client for A2A agent communication  
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // 🔐 JSON handling for API communication
    implementation("org.json:json:20230618")
    
    // 🔒 Security library for encrypted preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // 🔄 Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // 🎯 Permission handling
    implementation("androidx.activity:activity-compose:1.9.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}