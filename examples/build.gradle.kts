import de.mineking.discord.localization.gradle.declareProperty

plugins {
    id("de.mineking.discord.localization") version "1.0.0"
}

group = "de.mineking"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
    implementation("com.github.freya022:JDA:1be8478")

    implementation("ch.qos.logback:logback-classic:1.5.15")
    implementation("org.kodein.emoji:emoji-kt:2.0.1")
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
    jvmToolchain(21)
}

discordLocalization {
    locales = listOf("en-US", "de")
    defaultLocale = "en-US"

    declareProperty<Int>("bot")
}