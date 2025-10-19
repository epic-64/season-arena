import kotlinx.serialization.Serializable

@Serializable
data class Greeting(val message: String)

fun main() {
    // Simple example producing a JS console log.
    val g = Greeting("Hello from KotlinJS module kjs!")
    println(JSON.stringify(g))
}
plugins {
    kotlin("js")
    kotlin("plugin.serialization")
}

group = "io.holonaut"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        browser {
            binaries.executable()
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

// Copies the compiled JS bundle into frontend/generated/kotlin for integration
tasks.register<Copy>("copyJsToFrontend") {
    val outputDir = file("${rootProject.projectDir}/frontend/generated/kotlin")
    from("$buildDir/distributions") {
        include("*.js")
    }
    into(outputDir)
    doFirst { outputDir.mkdirs() }
}

