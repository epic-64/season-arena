package io.holonaut.arena

import freemarker.cache.ClassTemplateLoader
import io.holonaut.arena.api.installRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

val jsonCodec = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) { json(jsonCodec) }
        install(FreeMarker) {
            templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
        }
        installRoutes()
    }.start(wait = true)
}
