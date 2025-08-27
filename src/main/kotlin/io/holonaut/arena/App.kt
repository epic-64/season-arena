package io.holonaut.arena

import io.holonaut.arena.api.ApiRegistry
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.holonaut.arena.api.installRoutes
import io.ktor.http.*
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    registerKnownRoutes()

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        installRoutes()
    }.start(wait = true)
}

// Register known routes so the dropdown has entries immediately
private fun registerKnownRoutes() {
    ApiRegistry.register(HttpMethod.Get, "/health", "Server health check")
    ApiRegistry.register(HttpMethod.Get, "/units", "List available unit templates")
    ApiRegistry.register(
        HttpMethod.Post, "/simulate", "Simulate a 3v3 match",
        """{"team1":["guardian","ranger","healer"],"team2":["berserker","assassin","mage"]}"""
    )
}
