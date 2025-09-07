package playground.typestate

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface AuthState
object Unauthenticated : AuthState
object Authenticated : AuthState

data class User<S: AuthState>(val name: String)
data class AuthenticationException(override val message: String) : Exception(message)
data class Session(val user: User<Authenticated>)

fun parseUserFromAuthorizationHeader(header: String): Result<User<Unauthenticated>> {
    if (!header.startsWith("Authorization: Basic ")) {
        return Result.failure(AuthenticationException("Missing or invalid Authorization header"))
    }
    val jwtToken = header.removePrefix("Authorization: Basic ").trim()

    val jwt = try {
        JWT.decode(jwtToken)
    } catch (e: JWTDecodeException) {
        return Result.failure(AuthenticationException("Invalid JWT: ${e.message}"))
    }

    val username = jwt.subject ?: jwt.getClaim("user").asString()

    if (username.isNullOrBlank()) {
        return Result.failure(AuthenticationException("JWT does not contain a user identity (sub or user claim)"))
    }

    return Result.success(User(username))
}

fun authenticate(user: User<Unauthenticated>): Result<User<Authenticated>> =
    Result.success(User(user.name))

fun dashboard(user: User<Authenticated>): String =
    "Welcome to your dashboard, ${user.name}!"

@Serializable
data class RequestBody(val action: String, val resource: String)

fun parseRequestBody(jsonBody: String): Result<RequestBody> {
    return try {
        Result.success(Json.decodeFromString<RequestBody>(jsonBody))
    } catch (e: Exception) {
        Result.failure(AuthenticationException("Your JSON skills are as questionable as your life choices: ${e.message}"))
    }
}

fun main() {
    // Simulate receiving an Authorization header with a JWT
    val header = "Authorization: Basic eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJalice" // Replace with a real JWT for actual testing
    val requestBody = """{"action": "view", "resource": "profile"}"""

    val rawUser = parseUserFromAuthorizationHeader(header).getOrElse {
        println("Failed to parse user from Authorization header: ${it.message}")
        return
    }

    val authedUser = authenticate(rawUser).getOrElse {
        println("Authentication failed: ${it.message}")
        return
    }

    val body = parseRequestBody(requestBody).getOrElse {
        println("Failed to decode request body: ${it.message}")
        return
    }

    val dashboardMessage = dashboard(authedUser)
    println(dashboardMessage)
    println("Action: ${body.action}, Resource: ${body.resource}")
}
