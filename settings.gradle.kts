rootProject.name = "lastfleetprotocol"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":components:design")
include(":components:shared:api")
include(":components:shared:impl")
include(":features:splash:api")
include(":features:splash:impl")
include(":features:landing:api")
include(":features:landing:impl")
include(":features:game:api")
include(":features:game:impl")
include(":components:game-core:api")
include(":components:game-core:impl")
include(":features:ship-builder:api")
include(":features:ship-builder:impl")
include(":components:preferences:api")
include(":components:preferences:impl")