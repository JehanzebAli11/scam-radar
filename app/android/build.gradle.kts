// AGP 9 has built-in Kotlin support, so there is no separate kotlin-android plugin.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.jehanzebali11.scamradar"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jehanzebali11.scamradar"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // LiteRT-LM ships arm64-v8a (phones) and x86_64 (emulator) native libraries.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        // KV-cache cap passed to LiteRT-LM (message + answer is under ~300 tokens).
        // Override for the memory experiment: ./gradlew assembleDebug -PscamradarMaxTokens=0
        // (0 = use the model/engine default). See export/README.md.
        val maxTokens = (project.findProperty("scamradarMaxTokens") as String?)?.toInt() ?: 512
        buildConfigField("int", "MODEL_MAX_TOKENS", maxTokens.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // The model is never bundled in the APK; it is pushed to app storage (see README).
        jniLibs { useLegacyPackaging = false }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true // Robolectric UI tests
        unitTests.all { test ->
            // Demo-video frames are only written when asked for (see app/tools/make_demo_video.sh).
            for (key in listOf("scamradar.demoFrames", "robolectric.dependency.repo.url")) {
                System.getProperty(key)?.let { test.systemProperty(key, it) }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
