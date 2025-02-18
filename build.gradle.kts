// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply false                     // https://github.com/Kotlin/dokka/releases
}

allprojects {
    apply(from = "${rootDir}/versions.gradle.kts")
    rootProject.extra["IS_RELEASE_MODE"] = (System.getenv("IS_RELEASE_MODE") ?: "FALSE").toBoolean()
    rootProject.extra["PUBLISH_SNAPSHOT"] = (System.getenv("PUBLISH_SNAPSHOT") ?: "FALSE").toBoolean()
}

subprojects {
    val sdkVersionName = rootProject.extra["nuguVersionName"]
    version = sdkVersionName.toString()
}