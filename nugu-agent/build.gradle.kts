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
        implementation("com.skt.nugu.sdk:nugu-interface:$version")
    } else {
        implementation(project(":nugu-interface"))
    }

    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}

apply (from ="../publish.gradle")