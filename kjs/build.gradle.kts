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
            }
        }
        val jsTest by getting {}
    }
}

// Copies the compiled JS bundle into frontend/generated/kotlin for integration
tasks.register<Copy>("copyJsToFrontend") {
    dependsOn("jsBrowserProductionWebpack")
    from(layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) { include("kjs.js") }
    into(rootProject.layout.projectDirectory.dir("frontend/generated/kotlin"))
}

// Copies the compiled JS bundle (and related files) into frontend-kotlin directory
tasks.register<Copy>("copyKotlinFrontend") {
    dependsOn("jsBrowserProductionWebpack")
    from(layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) { include("**/*") }
    into(rootProject.layout.projectDirectory.dir("frontend-kotlin"))
}
