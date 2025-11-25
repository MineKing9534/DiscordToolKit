plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)

    id("maven-publish")
}

val jvmVersion = 21
val release = System.getenv("RELEASE") == "true"

allprojects {
    group = "de.mineking"
    version = "1.5.1"

    apply(plugin = "kotlin")

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

dependencies {
    implementation(libs.jda)
    compileOnly(libs.emoji)

    compileOnly(kotlin("reflect"))
    implementation(libs.serialization)
    implementation(libs.coroutines)

    implementation(libs.logging)
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
