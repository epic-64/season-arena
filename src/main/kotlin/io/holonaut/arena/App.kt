package io.holonaut.arena

import io.holonaut.arena.api.installRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.thymeleaf.Thymeleaf
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templatemode.TemplateMode
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
        install(Thymeleaf) {
            setTemplateResolver(ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                characterEncoding = "UTF-8"
                templateMode = TemplateMode.HTML
            })
        }
        installRoutes()
    }.start(wait = true)
}
