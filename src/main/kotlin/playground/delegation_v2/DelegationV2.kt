package playground.delegation_v2

data class User(val id: String, val name: String)

interface UserRepository {
    fun findUser(id: String): User?
    fun saveUser(user: User): Unit
}

class MemoryUserRepository : UserRepository {
    private val store = mutableMapOf<String, User>()

    override fun findUser(id: String): User? = store[id]
    override fun saveUser(user: User) { store[user.id] = user }
}

interface Cache<K, V> {
    fun get(key: K): V?
    fun put(key: K, value: V)
}

class MemoryCache<K, V> : Cache<K, V> {
    private val store = mutableMapOf<K, V>()

    override fun get(key: K): V? = store[key]
    override fun put(key: K, value: V) { store[key] = value }
}

class CachingUserRepository(
    val inner: UserRepository,
    val cache: Cache<String, User>,
): UserRepository by inner {
    override fun findUser(id: String): User? = when (val cached = cache.get(id)) {
        is User -> {
            println("Cache HIT for $id")
            cached
        }

        null -> {
            println("Cache MISS for $id → loading from DB...")
            inner.findUser(id)?.also { cache.put(id, it) }
        }
    }
}

fun main() {
    val repo: UserRepository = CachingUserRepository(
        inner = MemoryUserRepository(),
        cache = MemoryCache()
    )

    repo.saveUser(User("1", "Alice"))
    println(repo.findUser("1"))
    println(repo.findUser("1"))
    println(repo.findUser("1"))
}