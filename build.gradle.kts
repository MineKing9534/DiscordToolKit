plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.1.0"

    id("maven-publish")
}

val jvmVersion = 21
val release = System.getenv("RELEASE") == "true"

allprojects {
    group = "de.mineking"
    version = "1.2.0"

    apply(plugin = "kotlin")

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

dependencies {
    implementation("com.github.MineKing9534:JDA:1c0dbd9")
    compileOnly("org.kodein.emoji:emoji-kt:2.0.1")

    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
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
