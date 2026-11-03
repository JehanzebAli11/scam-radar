// Pure Kotlin/JVM: schema parser, rule layer, verdicts and pre-written text.
// No Android dependency, so `./gradlew :core:test` runs without the Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Shared fixtures from the Python side of the repo.
    systemProperty("scamradar.repoRoot", rootDir.parentFile.absolutePath)
}
