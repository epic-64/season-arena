package server2

import game.compactJson
import game.exampleTeam1
import game.exampleTeam2
import game.simulateBattle
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
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
    @CrossOrigin(origins = ["*"])
    @GetMapping("/hello")
    fun hello(): String = "Hello, World! The app has served ${counter.incrementAndGet()} requests."
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
