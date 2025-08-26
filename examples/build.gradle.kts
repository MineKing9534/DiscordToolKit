plugins {
    id("de.mineking.discord.localization") version "1.0.0"
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
    implementation("net.dv8tion:JDA:6.0.0-rc.3_DEV")

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

    locationFormat = "$projectDir/localization/%locale%/%name%.yaml"
}