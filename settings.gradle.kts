// Standalone build for the bug-report library. CarGuy pulls it in with `includeBuild("bugreport")`
// (a Gradle composite build), so it must never depend on anything from the app — it is destined to
// be split into its own repository with `git subtree split --prefix=bugreport`.
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
