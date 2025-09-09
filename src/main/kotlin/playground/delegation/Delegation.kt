package playground.delegation

data class User(val id: String, val name: String)

interface UserRepository {
    fun findUser(id: String): User?
    fun saveUser(user: User)
}

interface Cache<K, V> {
    fun get(key: K): V?
    fun put(key: K, value: V)
}

open class MemoryUserRepository : UserRepository {
    private val store = mutableMapOf<String, User>()

    override fun findUser(id: String): User? = store[id]

    override fun saveUser(user: User) {
        store[user.id] = user
    }
}

class MemoryCache<K, V> : Cache<K, V> {
    private val store = mutableMapOf<K, V>()
    override fun get(key: K): V? = store[key]
    override fun put(key: K, value: V) { store[key] = value }
}

class CachingUserRepository(): MemoryUserRepository() {
    val cache = MemoryCache<String, User?>()

    override fun findUser(id: String): User? {
        val cached = cache.get(id)

        if (cached == null) {
            println("Cache MISS for $id → loading from DB...")
            val user = super.findUser(id)
            cache.put(id, user)
            return user
        } else {
            println("Cache HIT for $id")
            return cached
        }
    }
}

fun main() {
    val repo = CachingUserRepository()

    repo.saveUser(User("1", "Alice"))

    println(repo.findUser("1")) // MISS
    println(repo.findUser("1")) // HIT (cached)
}