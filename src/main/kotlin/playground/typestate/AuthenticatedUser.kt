package playground.typestate

sealed interface AuthState
object Unauthenticated : AuthState
object Authenticated : AuthState

data class User<S: AuthState>(val name: String, val passwordHash: String)
data class AuthenticationException(override val message: String) : Exception(message)
data class Session(val user: User<Authenticated>)

fun authenticate(user: User<Unauthenticated>): Result<User<Authenticated>> =
    if (user.passwordHash == "hash")
        Result.success(User<Authenticated>(user.name, user.passwordHash))
    else
        Result.failure(AuthenticationException("Invalid password"))

fun login(user: User<Authenticated>): Session =
    Session(user).also { println("Creating session for user: ${user.name}") }

fun main() {
    val rawUser = User<Unauthenticated>("alice", "hashed_password")
    val authedResult = authenticate(rawUser)

    val authedUser = authedResult.getOrElse {
        println("Authentication failed: ${it.message}")
        return
    }

    val session = login(authedUser)
    // continue with all the exciting things a logged-in user can do
}
