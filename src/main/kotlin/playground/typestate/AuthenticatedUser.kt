package playground.typestate

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

sealed interface AuthState
object Unauthenticated : AuthState
object Authenticated : AuthState

data class User<S: AuthState>(val name: String, val passwordHash: String)
data class AuthenticationException(override val message: String) : Exception(message)
data class Session(val user: User<Authenticated>)

fun authenticate(user: User<Unauthenticated>): Result<User<Authenticated>> =
    if (user.passwordHash == "hash")
        Result.success(User(user.name, user.passwordHash))
    else
        Result.failure(AuthenticationException("Invalid password"))

fun login(user: User<Authenticated>): Session =
    Session(user).also { println("Creating session for user: ${user.name}") }

@Serializable
data class UserInput(val username: String, val password: String)

fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

fun parseUserInputFromRequest(request: String): Result<User<Unauthenticated>> {
    return try {
        val userInput = Json.decodeFromString<UserInput>(request)
        val passwordHash = sha256(userInput.password)
        Result.success(User(userInput.username, passwordHash))
    } catch (e: Exception) {
        Result.failure(AuthenticationException("Invalid request: ${e.message}"))
    }
}

fun main() {
    val request = "{\"username\": \"alice\", \"password\": \"alice_raw_password\"}"
    val rawUserResult = parseUserInputFromRequest(request)
    val rawUser = rawUserResult.getOrElse {
        println("Failed to parse user input: ${it.message}")
        return
    }
    val authedResult = authenticate(rawUser)
    val authedUser = authedResult.getOrElse {
        println("Authentication failed: ${it.message}")
        return
    }
    val session = login(authedUser)
    // continue with all the exciting things a logged-in user can do
}
