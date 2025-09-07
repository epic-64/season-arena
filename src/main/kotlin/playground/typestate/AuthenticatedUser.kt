package playground.typestate

sealed interface AuthState
object Unauthenticated : AuthState
object Authenticated : AuthState

class User<S: AuthState>(val name: String, val passwordHash: String)

fun authenticate(user: User<Unauthenticated>): User<Authenticated>? =
    if (user.passwordHash == "hash")
        User<Authenticated>(user.name, user.passwordHash)
    else
        null

fun login(user: User<Authenticated>) {
    println("Setting up session for ${user.name}")
}

fun main() {
    val rawUser = User<Unauthenticated>("alice", "hashed_password")
    val authedUser = authenticate(rawUser)

    if (authedUser == null) {
        println("Authentication failed")
        return
    }

    login(authedUser)

    // login(rawUser)
}