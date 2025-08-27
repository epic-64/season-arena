package io.holonaut.arena.api

import io.holonaut.arena.engine.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.installRoutes() {
    routing {
        get("/health") { call.respond(mapOf("ok" to true)) }

        get("/units") { call.respond(UNITS.values.map { it.toDto() }) }

        post("/simulate") {
            val req = try { call.receive<SimRequest>() } catch (t: Throwable) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid request body"))
                return@post
            }

            // Validate team size and ids
            if (req.team1.size != 3 || req.team2.size != 3) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "each team must have exactly 3 units"))
                return@post
            }
            val unknown = (req.team1 + req.team2).filter { it !in UNITS.keys }
            if (unknown.isNotEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown unit ids", "ids" to unknown))
                return@post
            }

            val teamA = req.team1.map { UNITS[it]!! }
            val teamB = req.team2.map { UNITS[it]!! }

            val result = simulateMatch(teamA, teamB)
            call.respond(result)
        }
    }
}

@Serializable
data class SimRequest(
    val team1: List<String>,
    val team2: List<String>
)
