plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.your.videochat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.your.videochat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // AppCompat для AppCompatActivity
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // RecyclerView для списка комнат
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // CardView для карточек комнат
    implementation("androidx.cardview:cardview:1.0.0")
    
    // WebRTC зависимость (Stream WebRTC - активно поддерживаемая версия)
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    
    // OkHttp для WebSocket соединения с signalling сервером
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Security crypto для шифрования сигнальных сообщений
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}