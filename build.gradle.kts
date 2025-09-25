plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "io.holonaut"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-netty:3.0.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
    implementation("io.ktor:ktor-server-html-builder:3.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Logging (quiet and simple)
    implementation("ch.qos.logback:logback-classic:1.5.13")

    // JWT parsing and validation
    implementation("com.auth0:java-jwt:4.4.0")

    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
}

application {
    // Default package and file names below
    mainClass.set("io.holonaut.arena.AppKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

tasks.register<JavaExec>("simulateBattle") {
    group = "application"
    description = "Runs the GameMain main function"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("game.GameMainKt")
}

tasks.register<JavaExec>("generateJsDoc") {
    group = "documentation"
    description = "Generates JSDoc typedef file from Kotlin types"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("tools.GenerateJsDocKt")

    args("--file", "src/main/kotlin/game/Types.kt")
    args("--out", "frontend/generated/types.js")
}

kotlin {
    jvmToolchain(21)
}