package server2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

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

