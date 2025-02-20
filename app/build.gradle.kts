plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.skt.nugu.app"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = "com.skt.nugu.app"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = rootProject.extra["nuguVersionCode"] as Int
        versionName = rootProject.extra["nuguVersionName"] as String

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
        compose = true
    }
    androidResources {
        noCompress += "raw"
    }
}

dependencies {
    if (rootProject.extra["IS_RELEASE_MODE"] as Boolean) {
        implementation("com.skt.nugu.sdk:nugu-android-helper:$version")
        implementation("com.skt.nugu.sdk:nugu-ux-kit:$version")
        implementation("com.skt.nugu.sdk:nugu-service-kit:$version")
    } else {
        implementation(project(":nugu-android-helper"))
        implementation(project(":nugu-ux-kit"))
        implementation(project(":nugu-service-kit"))
    }
    // include default resources
    implementation("com.skt.nugu.jademarble:default-resource:0.2.7" )
    implementation("com.skt.nugu.keensense:default-resource:0.3.1" )

    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.hls)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.fragment.ktx) // 또는 최신 버전 확인 후 적용

}