package io.holonaut.arena.api

import io.holonaut.arena.engine.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.html.*
import kotlinx.serialization.Serializable
import kotlinx.html.*

fun Application.installRoutes() {
    ApiRegistry.registerKnownRoutes()

    routing {
        staticResources("/", "static")

        // Config for the page
        get("/_config") {
            call.respond(mapOf(
                "env" to (System.getenv("ENV") ?: "dev"),
                "version" to "0.1.0"
            ))
        }

        // Routes list for the dropdown
        get("/_routes") { call.respond(ApiRegistry.routes) }

        get("/health") { call.respond(mapOf("ok" to true)) }

        post("/simulate") {
            val req = try { call.receive<SimRequest>() } catch (t: Throwable) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid request body"))
                return@post
            }

            call.respond("not implemented yet")
        }

        get("/greet") {
            val name = call.request.queryParameters["name"] ?: "meat sack"
            call.respondHtml {
                head {
                    title { +"Greeting" }
                    script(src = "https://unpkg.com/htmx.org@1.9.10") {}
                }
                body {
                    h1 { +"Hello, $name!" }
                    p { +"Welcome to the snarkiest server-side rendering this side of the JVM." }
                    button {
                        attributes["hx-get"] = "/greet?name=meatbag"
                        attributes["hx-target"] = "#greeting"
                        +"Greet a meatbag"
                    }
                    div { id = "greeting" }
                }
            }
        }
    }
}

@Serializable
data class SimRequest(
    val team1: List<String>,
    val team2: List<String>
)
