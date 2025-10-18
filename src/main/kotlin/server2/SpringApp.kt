package server2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import game.exampleTeam1
import game.exampleTeam2
import game.simulateBattle
import game.compact
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@SpringBootApplication
class SpringApp

fun main(args: Array<String>) {
    runApplication<SpringApp>(*args)
}

@RestController
class HelloController {
    @GetMapping("/hello")
    fun hello(): String = "Hello World"
}

@RestController
class CombatController {
    @GetMapping("/combat/example", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun exampleCombat(): ResponseEntity<String> {
        val teamA = exampleTeam1()
        val teamB = exampleTeam2()
        val compact = simulateBattle(teamA, teamB).events.compact()
        val json = Json.encodeToString(compact)
        return ResponseEntity.ok(json)
    }
}
