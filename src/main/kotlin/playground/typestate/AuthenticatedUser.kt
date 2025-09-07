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

fun parseUserFromRequest(request: Request): Result<User<Unauthenticated>> {
    val header = request.headers["Authorization"]
    val prefix = "Basic "

    val jwtToken = header?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
        ?: return Result.failure(AuthenticationException("Missing or invalid Authorization header"))

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

@Serializable
data class DashboardBody(val displayTime: Boolean?)

fun parseDashboardBody(request: Request): Result<DashboardBody> {
    val jsonBody = request.body

    return try {
        Result.success(Json.decodeFromString<DashboardBody>(jsonBody))
    } catch (e: Exception) {
        Result.failure(AuthenticationException("Your JSON skills are as questionable as your life choices: ${e.message}"))
    }
}

data class Request (val headers: Map<String, String>, val body: String, val route: String)

fun handleDashboard(request: Request, user: User<Authenticated>): String {
    val body = parseDashboardBody(request).getOrElse {
        return "Failed to decode request body: ${it.message}"
    }

    val currentTime =
        if (body.displayTime == true)
            "Current time is ${System.currentTimeMillis()}"
        else
            "Time display not requested"

    return """
        Welcome to your dashboard, ${user.name}!
        $currentTime
    """.trimIndent()
}

fun handleProfile(request: Request, user: User<Authenticated>): String {
    return "Welcome to your profile, ${user.name}!"
}

fun handleRoute(request: Request): String {
    return when (request.route) {
        "/health" -> "OK"
        "/dashboard" -> {
            val rawUser = parseUserFromRequest(request).getOrElse {
                return "Failed to parse user from request: ${it.message}"
            }
            val authedUser = authenticate(rawUser).getOrElse {
                return "Authentication failed: ${it.message}"
            }
            handleDashboard(request, authedUser)
        }
        "/profile" -> {
            val rawUser = parseUserFromRequest(request).getOrElse {
                return "Failed to parse user from request: ${it.message}"
            }
            val authedUser = authenticate(rawUser).getOrElse {
                return "Authentication failed: ${it.message}"
            }
            handleProfile(request, authedUser)
        }
        else -> "Unknown route: ${request.route}"
    }
}

fun rawHttpToRequest(raw: String): Request {
    val lines = raw.lines()
    val headers = mutableMapOf<String, String>()
    var body = ""
    var route = "/"

    for (line in lines) {
        when {
            line.startsWith("GET") || line.startsWith("POST") -> {
                val parts = line.split(" ")
                if (parts.size >= 2) {
                    route = parts[1]
                }
            }
            line.contains(":") -> {
                val (key, value) = line.split(":", limit = 2)
                headers[key.trim()] = value.trim()
            }
            line.isBlank() -> {
                // Body starts after a blank line
                body = lines.dropWhile { it != line }.drop(1).joinToString("\n")
                break
            }
        }
    }

    return Request(headers, body, route)
}

fun main() {
    val rawInput = """
        POST /dashboard HTTP/1.1
        Host: example.com
        Authorization: Basic eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6ImFsaWNlIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.LJpzFMhwyqCTDxXBpnWVsHWXiX5OGambIa0HWTfFYtE
        Content-Type: application/json
        Content-Length: 27
        
        {"displayTime": true}
    """.trimIndent()

    val dashboardRequest = rawHttpToRequest(rawInput)
    val dashboardResponse = handleRoute(dashboardRequest)
    println(dashboardResponse)

    val rawProfileRequest = """
        GET /profile HTTP/1.1
        Host: example.com
        Authorization: Basic eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6ImFsaWNlIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.LJpzFMhwyqCTDxXBpnWVsHWXiX5OGambIa0HWTfFYtE
    """.trimIndent()

    val profileRequest = rawHttpToRequest(rawProfileRequest)
    val profileResponse = handleRoute(profileRequest)
    println(profileResponse)
}
