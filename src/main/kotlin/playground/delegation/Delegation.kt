package playground.delegation

interface Logger {
    fun log(msg: String)
}

class ConsoleLogger : Logger {
    override fun log(msg: String) = println("[LOG] $msg")
}

interface Cache {
    fun put(key: String, value: String)
    fun get(key: String): String?
}

class MemoryCache : Cache {
    private val store = mutableMapOf<String, String>()
    override fun put(key: String, value: String) = value.let { store[key] = value }
    override fun get(key: String): String? = store[key]
}

class MyController(
    logger: Logger,
    cache: Cache
) : Logger by logger, Cache by cache {
    fun handleRequest(id: String): String = when (val cached = get(id)) {
        is String -> log("Cache HIT for id: $id, data: $cached")
            .let { cached }

        null -> log("Cache MISS for id: $id, computing data...")
            .let { (1..5).map { ('A'..'Z').random() }.joinToString("") }
            .also { put(id, it) }
    }
}

fun main() {
    val controller = MyController(
        logger = ConsoleLogger(),
        cache = MemoryCache()
    )

    controller.handleRequest("123")
    controller.handleRequest("123")
}