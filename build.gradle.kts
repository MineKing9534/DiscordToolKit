import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.1.0"

    id("com.adarshr.test-logger") version "4.0.0"

    id("maven-publish")
}

group = "de.mineking"
version = "1.0.0"

val jvmVersion = 21
val release = System.getenv("RELEASE") == "true"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.freya022:JDA:1be8478")

    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.7.3")

    compileOnly("me.carleslc.Simple-YAML:Simple-Yaml:1.8.4")
    compileOnly("org.kodein.emoji:emoji-kt:2.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.2.0")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.2.0")

    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    testImplementation("ch.qos.logback:logback-classic:1.5.15")

    testImplementation(kotlin("test"))
    testImplementation("org.kodein.emoji:emoji-kt:2.0.1")
    testImplementation("me.carleslc.Simple-YAML:Simple-Yaml:1.8.4")
}

testlogger {
    theme = ThemeType.MOCHA
    showStackTraces = false
}

publishing {
    repositories {
        maven {
            url = uri("https://maven.mineking.dev/" + (if (release) "releases" else "snapshots"))
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_SECRET")
            }
        }
    }

    publications {
        register<MavenPublication>("maven") {
            from(components["java"])

            groupId = "de.mineking"
            artifactId = "DiscordToolKit"
            version = if (release) "${ project.version }" else System.getenv("BRANCH")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(jvmVersion)
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}

java {
    sourceCompatibility = JavaVersion.toVersion(jvmVersion)
    targetCompatibility = JavaVersion.toVersion(jvmVersion)

    withJavadocJar()
    withSourcesJar()
}