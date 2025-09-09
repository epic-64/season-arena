package playground.delegation

data class User(val id: String, val name: String)

interface UserRepository {
    fun findUser(id: String): User?
    fun saveUser(user: User)
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
    private val inner: UserRepository,
    private val cache: Cache<String, User?>
) : UserRepository by inner, Cache<String, User?> by cache {

    override fun findUser(id: String): User? = when (val cached = cache.get(id)) {
        null -> {
            println("Cache MISS for $id → loading from DB...")
            inner.findUser(id).also { cache.put(id, it) }
        }
        else -> println("Cache HIT for $id").let { cached }
    }
}

fun main() {
    val dbRepo = MemoryUserRepository()
    val memoryCache = MemoryCache<String, User?>()
    val repo = CachingUserRepository(dbRepo, memoryCache)

    dbRepo.saveUser(User("1", "Alice"))

    println(repo.findUser("1")) // MISS
    println(repo.findUser("1")) // HIT (cached)
}