plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    // Spring Boot plugins for server2 package
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("plugin.spring") version "2.2.20"
}

group = "io.holonaut"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")

    // JWT parsing and validation
    implementation("com.auth0:java-jwt:4.4.0")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web:3.3.4")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
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

tasks.register<JavaExec>("runSpringServer2") {
    group = "application"
    description = "Runs the Spring Boot server2 application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("server2.SpringAppKt")
}

kotlin {
    jvmToolchain(21)
}