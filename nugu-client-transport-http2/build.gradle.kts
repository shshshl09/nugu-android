plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    if (rootProject.extra["IS_RELEASE_MODE"] as Boolean) {
        api("com.skt.nugu.sdk:nugu-interface:$version")
    } else {
        api(project(":nugu-interface"))
    }

    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
}

apply (from ="../publish.gradle")