package server2

import game.simulateBattle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class SpringApp

fun main(args: Array<String>) {
    runApplication<SpringApp>(*args)
}

val counter = java.util.concurrent.atomic.AtomicLong()

@RestController
class HelloController {
    @GetMapping("/hello")
    fun hello(): String = "hello world"
}

@RestController
class CombatController {
    @GetMapping("/combat/example", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun exampleCombat(): ResponseEntity<String> =
        simulateBattle(exampleTeam1(), exampleTeam2())
            .compact()
            .let(Json::encodeToString)
            .let(ResponseEntity<String>::ok)
}
