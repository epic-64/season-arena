package playground.typestate

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface AuthState
object Unauthenticated : AuthState
object Authenticated : AuthState

data class User<S: AuthState>(val sub: String, val name: String)

data class AuthenticationException(override val message: String) : Exception(message)

@Serializable
data class DashboardBody(val displayTime: Boolean? = null)

data class WebRequest (val headers: Map<String, String>, val body: String, val route: String)

val jsonHandler = Json { ignoreUnknownKeys = true }

const val JWT_SECRET = "a-string-secret-at-least-256-bits-long"
val jwtAlgorithm = Algorithm.HMAC256(JWT_SECRET)
val jwtVerifier: JWTVerifier = JWT.require(jwtAlgorithm).build()

fun parseUserFromRequest(request: WebRequest): Result<User<Unauthenticated>> {
    val header = request.headers["Authorization"]
    val prefix = "Basic "

    val jwtToken = header?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
        ?: return Result.failure(AuthenticationException("Missing or invalid Authorization header"))

    val jwt = try {
        jwtVerifier.verify(jwtToken)
    } catch (e: JWTVerificationException) {
        return Result.failure(AuthenticationException("Invalid JWT signature: ${e.message}"))
    } catch (e: Exception) {
        return Result.failure(AuthenticationException("JWT verification failed: ${e.message}"))
    }

    val sub = jwt.subject ?: jwt.getClaim("user").asString()
    val name = jwt.getClaim("name").asString() ?: sub

    if (sub.isNullOrBlank()) {
        return Result.failure(AuthenticationException("JWT does not contain a user identity (sub or user claim)"))
    }

    return Result.success(User(sub, name))
}

fun authenticate(user: User<Unauthenticated>): Result<User<Authenticated>> =
    Result.success(User(user.sub, user.name))

fun handleDashboard(request: WebRequest, user: User<Authenticated>): String {
    val body = try {
        jsonHandler.decodeFromString<DashboardBody>(request.body)
    } catch (e: Exception) {
        return "Failed to parse request body: ${e.message}"
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

fun handleProfile(request: WebRequest, user: User<Authenticated>): String {
    return "Welcome to your profile, ${user.name}!"
}

fun withAuthenticatedUser(request: WebRequest, handler: (User<Authenticated>) -> String): String {
    val rawUser = parseUserFromRequest(request).getOrElse {
        return "Failed to parse user from request: ${it.message}"
    }
    val authedUser = authenticate(rawUser).getOrElse {
        return "Authentication failed: ${it.message}"
    }
    return handler(authedUser)
}

fun handleRoute(request: WebRequest): String {
    return when (request.route) {
        "/health"    -> "OK"
        "/dashboard" -> withAuthenticatedUser(request) { user -> handleDashboard(request, user) }
        "/profile"   -> withAuthenticatedUser(request) { user -> handleProfile(request, user) }
        else          -> "Unknown route: ${request.route}"
    }
}

fun rawHttpToRequest(raw: String): WebRequest {
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

    return WebRequest(headers, body, route)
}

val dashboardRequest = """
    POST /dashboard HTTP/1.1
    Host: example.com
    Authorization: Basic eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6ImFsaWNlIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.LJpzFMhwyqCTDxXBpnWVsHWXiX5OGambIa0HWTfFYtE
    Content-Type: application/json
    Content-Length: 27
    
    {}
""".trimIndent()

val profileRequest = """
    GET /profile HTTP/1.1
    Host: example.com
    Authorization: Basic eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6ImFsaWNlIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.LJpzFMhwyqCTDxXBpnWVsHWXiX5OGambIa0HWTfFYtE
""".trimIndent()

val healthRequest = """
    GET /health HTTP/1.1
    Host: example.com
""".trimIndent()

const val badRequest = "asdf"

fun main() {
    val webRequest = rawHttpToRequest(dashboardRequest)

    val response = handleRoute(webRequest)

    println(response)
}
