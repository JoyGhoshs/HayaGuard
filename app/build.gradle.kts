plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hayaguard.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hayaguard.app"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.onnxruntime)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.webkit)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.ktx)
    implementation(libs.splashscreen)
    implementation(libs.tflite)
    implementation(libs.tflite.support)
    implementation(libs.tflite.gpu) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation(libs.tflite.gpu.api) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation(libs.cronet)
    implementation(libs.customtabs)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.entity.extraction)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.devanagari)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}