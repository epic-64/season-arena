package playground.typestate

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface AuthState
object Unauthenticated : AuthState
object Authenticated : AuthState

data class User<S: AuthState>(val sub: String, val name: String)
data class AuthenticationException(override val message: String) : Exception(message)
data class WebRequest (val headers: Map<String, String>, val body: String, val route: String)

@Serializable
data class DashboardBody(val displayTime: Boolean? = null)

sealed interface DocumentState
object Draft : DocumentState
object Reviewed : DocumentState
object Published : DocumentState
object Archived : DocumentState

data class Document<S: DocumentState>(val id: String, val content: String)

val jsonHandler = Json { ignoreUnknownKeys = true }

const val JWT_SECRET = "a-string-secret-at-least-256-bits-long"
val jwtAlgorithm: Algorithm = Algorithm.HMAC256(JWT_SECRET) // Specified as HS256 in the token.
val jwtVerifier: JWTVerifier = JWT.require(jwtAlgorithm).build()

fun parseUserFromRequest(request: WebRequest): Result<User<Unauthenticated>> {
    val header = request.headers["Authorization"]
    val prefix = "Basic "

    val rawJwt = header?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
        ?: return Result.failure(AuthenticationException("Missing or invalid Authorization header"))

    val jwt = try {
        jwtVerifier.verify(rawJwt)
    } catch (e: JWTVerificationException) {
        return Result.failure(AuthenticationException("Invalid JWT signature: ${e.message}"))
    } catch (e: Exception) {
        return Result.failure(AuthenticationException("JWT verification failed: ${e.message}"))
    }

    val sub = jwt.subject ?: jwt.getClaim("user").asString()
        ?: return Result.failure(AuthenticationException("JWT does not contain a user identity (sub or user claim)"))
    val name = jwt.getClaim("name").asString() ?: sub

    if (sub.isBlank()) {
        return Result.failure(AuthenticationException("User identity (sub) is blank"))
    }

    return Result.success(User<Unauthenticated>(sub, name))
}

fun authenticate(user: User<Unauthenticated>): Result<User<Authenticated>> =
    when {
        user.sub.startsWith("test") -> Result.failure(AuthenticationException("Test users are not allowed"))
        else -> Result.success(User(user.sub, user.name))
    }

fun handleDashboard(request: WebRequest, user: User<Authenticated>): String {
    val body = try {
        jsonHandler.decodeFromString<DashboardBody>(request.body)
    } catch (e: Exception) {
        return "Failed to parse request body: ${e.message}"
    }

    val currentTime: String = when (body.displayTime) {
        true -> "Current server time is: ${java.time.Instant.now()}"
        false -> "User chose not to display time."
        null -> "User did not specify whether to display time."
    }

    return """
        Welcome to your dashboard, ${user.name}!
        $currentTime
    """.trimIndent()
}

fun withAuthenticatedUser(request: WebRequest, handler: (User<Authenticated>) -> String): String {
    val unauthenticatedUser = parseUserFromRequest(request).getOrElse {
        return "Failed to parse user from request: ${it.message}"
    }
    val authedUser = authenticate(unauthenticatedUser).getOrElse {
        return "Authentication failed: ${it.message}"
    }
    return handler(authedUser)
}

fun handleRoute(request: WebRequest): String {
    return when (request.route) {
        "/health/"   -> "OK"
        "/dashboard" -> withAuthenticatedUser(request) { user -> handleDashboard(request, user) }
        "/profile"   -> withAuthenticatedUser(request) { user -> "Welcome to your profile, ${user.name}!" }
        else         -> "Unknown route: ${request.route}"
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
    
    {"displayTime": true}
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

fun reviewDocument(doc: Document<Draft>): Document<Reviewed> {
    println("Reviewing document: ${doc.id}")
    return Document(doc.id, doc.content)
}

fun publishDocument(doc: Document<Reviewed>): Document<Published> {
    println("Publishing document: ${doc.id}")
    return Document(doc.id, doc.content)
}

fun archiveDocument(doc: Document<Published>): Document<Archived> {
    println("Archiving document: ${doc.id}")
    return Document(doc.id, doc.content)
}

fun <S: DocumentState> deleteDocument(doc: Document<S>): String {
    println("Deleting document: ${doc.id}")
    return "Document ${doc.id} deleted."
}

// Demo function to show type safety
fun documentWorkflowDemo() {
    val draft = Document<Draft>("doc-123", "This is a draft.")
    val reviewed = reviewDocument(draft)
    val published = publishDocument(reviewed)
    val archived = archiveDocument(published)

    val deleteMsg = deleteDocument(archived)
}

fun main() {
    val webRequest = rawHttpToRequest(dashboardRequest)

    val response = handleRoute(webRequest)

    println(response)

    // documentWorkflowDemo()
}
