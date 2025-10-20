rootProject.name = "season-arena"

pluginManagement {
    val kotlinVersion = "2.2.20"
    val springBootVersion = "3.3.4"
    val springDepManVersion = "1.1.4"
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("js") version kotlinVersion // enable KotlinJS plugin
        kotlin("multiplatform") version kotlinVersion // add multiplatform plugin
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springDepManVersion
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

include(":kjs")
include(":game-model")
// Point logical project names to their new physical locations under modules/
project(":kjs").projectDir = file("modules/kjs")
project(":game-model").projectDir = file("modules/game-model")
