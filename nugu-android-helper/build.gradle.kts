plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.skt.nugu.sdk.platform.android"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {

        minSdk = rootProject.extra["minSdkVersion"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("int", "VERSION_CODE", "${rootProject.extra["nuguVersionCode"]}")
        buildConfigField("String", "VERSION_NAME", "\"${rootProject.extra["nuguVersionName"]}\"")
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
}

dependencies {
    if (rootProject.extra["IS_RELEASE_MODE"] as Boolean) {
        api("com.skt.nugu.sdk:nugu-client-kit:$version")
    } else {
        api(project(":nugu-client-kit"))
    }

    api("com.skt.nugu:silvertray:${libs.versions.silvertray.get()}-RELEASE@aar") {
        exclude(group = "com.skt.nugu.opus", module = "wrapper")
    }

    implementation(libs.jademarble)
    implementation(libs.keensense)
    implementation(libs.opus.wrapper)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

apply (from ="../publish-android.gradle")