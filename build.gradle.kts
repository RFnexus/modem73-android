// Top-level build file. With AGP 9 Kotlin support is built in, so the Kotlin
// Gradle plugin is only declared here (apply false) to put it on the classpath;
// modules must NOT apply org.jetbrains.kotlin.android themselves.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
