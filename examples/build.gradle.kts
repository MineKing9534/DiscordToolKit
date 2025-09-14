plugins {
    alias(libs.plugins.localization)
}

group = "de.mineking"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.mineking.dev/snapshots")
    maven("https://maven.mineking.dev/releases")
}

dependencies {
    implementation(rootProject)
    implementation(libs.jda)

    implementation(kotlin("reflect"))
    implementation(libs.logback)
    implementation(libs.emoji)
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
    jvmToolchain(21)
}

discordLocalization {
    locales = listOf("en-US", "de")
    defaultLocale = "en-US"
}