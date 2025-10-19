import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class Greeting(val message: String)

fun main() {
    // Emit a serialized greeting to the browser console
    console.log(Json.encodeToString(Greeting("Hello from KotlinJS (jsMain)!")))
}

