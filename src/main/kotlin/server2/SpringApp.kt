package server2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import game.exampleTeam1
import game.exampleTeam2
import game.simulateBattle
import game.compact
import game.compactJson
import game.toJson
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.web.bind.annotation.CrossOrigin

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

    @CrossOrigin(origins = ["*"])
    @GetMapping("/combat/example", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun exampleCombat(): ResponseEntity<String> =
        simulateBattle(exampleTeam1(), exampleTeam2())
            .compactJson()
            .let { ResponseEntity.ok(it) }

}
