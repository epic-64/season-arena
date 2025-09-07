package playground.typestate

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException

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
    Result.success(User<Authenticated>(user.name))

fun dashboard(user: User<Authenticated>): String =
    "Welcome to your dashboard, ${user.name}!"

fun main() {
    // Simulate receiving an Authorization header with a JWT
    val header = "Authorization: Basic eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJalice" // Replace with a real JWT for actual testing

    val rawUser = parseUserFromAuthorizationHeader(header).getOrElse {
        println("Failed to parse user from Authorization header: ${it.message}")
        return
    }

    val authedUser = authenticate(rawUser).getOrElse {
        println("Authentication failed: ${it.message}")
        return
    }

    val dashboardMessage = dashboard(authedUser)
    println(dashboardMessage)
}
