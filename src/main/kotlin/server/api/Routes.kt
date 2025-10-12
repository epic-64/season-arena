package server.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.html.*
import kotlinx.serialization.Serializable
import kotlinx.html.*
import game.exampleTeam2
import game.exampleTeam1
import game.simulateBattle
import game.toCompactCombatEvents
import game.CombatEvent

fun Application.installRoutes() {

    routing {
        // staticResources("/", "static")

        // Config for the page
        get("/_config") {
            call.respond(mapOf(
                "env" to (System.getenv("ENV") ?: "dev"),
                "version" to "0.1.0"
            ))
        }

        get("/health") { call.respond(mapOf("ok" to true)) }

        post("next-encounter") {
            // Build an enemy team (player team is not stored here)
            val enemyTeam = exampleTeam2()
            val encounterId = EncounterStore.create(enemyTeam)
            call.respond(mapOf("encounterId" to encounterId))
        }

        post("battle") {
            val req = try { call.receive<BattleRequest>() } catch (t: ContentTransformationException) {
                val errorMsg = "invalid request body" + t.cause?.let { ": ${it.message}" }.orEmpty()
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to errorMsg))
                return@post
            }

            val encounterId = req.encounterId
            val enemyTeam = EncounterStore.get(encounterId)
            if (enemyTeam == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "encounter not found"))
                return@post
            }

            val playerTeam = exampleTeam1()

            try {
                val battleState = simulateBattle(playerTeam, enemyTeam)
                val events = battleState.log
                val compactEvents = toCompactCombatEvents(events)
                val turns = events.count { it is CombatEvent.TurnStart } - 1 // exclude initial state
                val winner = (events.lastOrNull { it is CombatEvent.BattleEnd } as? CombatEvent.BattleEnd)?.winner ?: "Unknown"

                val response = BattleResponse(
                    encounterId = encounterId,
                    winner = winner,
                    turns = turns,
                    events = compactEvents
                )
                call.respond(response)
            } catch (t: Throwable) {
                application.log.error("battle simulation failed", t)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (t.message ?: "internal error")))
            }
        }

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
data class BattleRequest(
    val encounterId: String,
)

@Serializable
data class BattleResponse(
    val encounterId: String,
    val winner: String,
    val turns: Int,
    val events: List<game.CompactCombatEvent>,
)

@Serializable
data class SimRequest(
    val team1: List<String>,
    val team2: List<String>
)
