plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "io.holonaut"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
    }
    js(IR) {
        browser()
    }
    jvmToolchain(21)
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {}
        val jvmTest by getting {}
        val jsMain by getting {}
        val jsTest by getting {}
    }
}
