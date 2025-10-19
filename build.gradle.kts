// Add shared version variables
val kotlinVersion = "2.2.20"
val kotestVersion = "5.8.0"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

    // Spring
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
}

group = "io.holonaut"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":game-model"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    // JWT parsing and validation
    implementation("com.auth0:java-jwt:4.4.0")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.6")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation(project(":game-model"))

    runtimeOnly("com.h2database:h2")
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

    args("--file", "game-model/src/commonMain/kotlin/game/CompactTypes.kt")
    args("--out", "frontend/generated/types.js")
}

tasks.register<JavaExec>("runSpringServer2") {
    group = "application"
    description = "Runs the Spring Boot server2 application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("server2.SpringAppKt")
}

springBoot {
    mainClass.set("server2.SpringAppKt")
}

kotlin {
    jvmToolchain(21)
}