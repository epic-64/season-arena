plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "io.holonaut"
version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

kotlin {
    js(IR) {
        browser { binaries.executable() }
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":game-model"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val jsTest by getting {}
    }
}

// Copies the compiled JS bundle (and related files) into frontend-kotlin directory
tasks.register<Copy>("copyKotlinFrontend") {
    dependsOn("jsBrowserProductionWebpack")
    from(layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) { include("**/*") }
    into(rootProject.layout.projectDirectory.dir("frontend-kotlin"))
}

tasks.named("build") {
    dependsOn(":kjs:copyKotlinFrontend")
}
