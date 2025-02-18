import java.net.URI

include(":nugu-ux-kit")


include(":nugu-service-kit")


pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = URI("https://nexus.nugu.co.kr/repository/maven-public/")
        }
    }
}

rootProject.name = "nugu-android"
include(":app")
include(":nugu-interface")
include(":nugu-core")
include(":nugu-agent")
include(":nugu-client-transport-http2")
include(":nugu-client-kit")
include(":nugu-android-helper")
include(":nugu-login-kit")
