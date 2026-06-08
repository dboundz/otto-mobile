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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val mapboxDownloadsToken =
    providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN")
        .orElse(providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN"))
        .orElse("")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = mapboxDownloadsToken.get()
            }
        }
        maven(url = uri("https://jitpack.io"))
    }
}

rootProject.name = "OttoMobile"
include(":app")
