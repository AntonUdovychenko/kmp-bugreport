// An app can develop against a local checkout of this build with `includeBuild("path/to/kmp-bugreport")`
// (a Gradle composite build), which substitutes it for the published artifacts.
//
// The root project is deliberately NOT called "bugreport": composite-build substitution matches on
// group:name, and a root sharing the library module's coordinates would make the match ambiguous.
rootProject.name = "kmp-bugreport"

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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
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
    }
}

include(":bugreport")
include(":bugreport-ktor")

// The sample apps are only part of the standalone build. When an app pulls this build in with
// `includeBuild`, it should not configure an extra Android application and a desktop distribution it
// never uses.
if (gradle.parent == null) {
    include(":sample")
    include(":sample-android")
}
