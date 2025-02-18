plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.protobuf)
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

sourceSets {
    create("generated") {
        java {
            srcDirs.add(file("${layout.buildDirectory.get()}/generated/source/proto/main/grpc"))
        }
    }
}

protobuf {
    val useAppleSilicon = false

    if(useAppleSilicon) {
        protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}:osx-x86_64" }
        plugins {
            create("grpc") {
                artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}:osx-x86_64"
            }
        }
    } else {
        protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}" }
        plugins {
            create("grpc") {
                artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
            }
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
            task.plugins {
                create("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    if (rootProject.ext["IS_RELEASE_MODE"] as Boolean) {
        api("com.skt.nugu.sdk:nugu-interface:$version")
        api("com.skt.nugu.sdk:nugu-agent:$version")
        implementation("com.skt.nugu.sdk:nugu-core:$version")
    } else {
        api(project(":nugu-interface"))
        api(project(":nugu-agent"))
        implementation(project(":nugu-core"))
    }

    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.okhttp)
    implementation(libs.okhttp)
    implementation(libs.gson)
    compileOnly(libs.tomcat.annotations.api)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}

apply (from ="../publish.gradle")
