pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.mineking.dev/snapshots")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "DiscordToolKit"
include("examples")