plugins {
    alias(libs.plugins.android.application) apply false
    // Also pins the Kotlin Gradle plugin that AGP's built-in Kotlin support uses.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
