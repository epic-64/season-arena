package playground.html_dsl

sealed interface AuthState
object Unauthenticated : AuthState
object Authenticated : AuthState

class User<S: AuthState>(val name: String, val passwordHash: String)

fun authenticate(user: User<Unauthenticated>): User<Authenticated> =
    if (user.passwordHash == "hash")
        User(user.name, user.passwordHash)
    else
        throw IllegalArgumentException("Invalid credentials")

fun login(user: User<Authenticated>) {
    println("Setting up session for ${user.name}")
}

fun main() {
    val rawUser = User<Unauthenticated>("alice", "hash")
    val authedUser = authenticate(rawUser)
    login(authedUser)   // ✅ Compiles
    // login(rawUser)
}