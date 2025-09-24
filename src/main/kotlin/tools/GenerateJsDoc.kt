package tools

import game.*
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

/**
 * Generates a JSDoc typedef file for Kotlin domain classes / enums / sealed hierarchies.
 * Auto-discovers top-level classes in a specified source file (--file) unless empty, then uses a fallback list.
 * Blacklist with --blacklist Name1,Name2.
 */
object JsDocGenerator {
    private data class Context(
        val processed: MutableSet<String> = mutableSetOf(),
        val enumBuffer: MutableList<String> = mutableListOf(),
        val typedefBuffer: MutableList<String> = mutableListOf(),
        val unionBuffer: MutableList<String> = mutableListOf(),
        val queue: ArrayDeque<KClass<*>> = ArrayDeque()
    )

    private val primitiveNumber = setOf(Int::class, Long::class, Short::class, Byte::class, Double::class, Float::class)

    fun generate(root: List<KClass<*>>): String {
        val ctx = Context()
        root.forEach { enqueue(ctx, it) }
        while (ctx.queue.isNotEmpty()) {
            val k = ctx.queue.removeFirst()
            if (!ctx.processed.add(k.qualifiedName ?: k.simpleName ?: continue)) continue
            when {
                k.java.isEnum -> emitEnum(ctx, k)
                k.isSealed -> emitSealed(ctx, k)
                k.isData -> emitDataClass(ctx, k)
            }
        }
        return buildString {
            append("""/**\n * AUTO-GENERATED FILE. DO NOT EDIT DIRECTLY.\n * Regenerate with: ./gradlew generateJsDoc\n */\n\n""")
            ctx.enumBuffer.forEach { append(it).append('\n') }
            ctx.typedefBuffer.forEach { append(it).append('\n') }
            ctx.unionBuffer.forEach { append(it).append('\n') }
        }
    }

    private fun enqueue(ctx: Context, k: KClass<*>) {
        if (k.qualifiedName == null) return
        if (!ctx.processed.contains(k.qualifiedName)) ctx.queue.add(k)
    }

    private fun emitEnum(ctx: Context, k: KClass<*>) {
        val name = k.simpleName ?: return
        val constants = k.java.enumConstants.joinToString(",\n    ") { c -> "$c: '$c'" }
        ctx.enumBuffer += "/**\n * @readonly\n * @enum {string}\n */\nexport const $name = {\n    $constants\n};\n"
    }

    private fun emitSealed(ctx: Context, k: KClass<*>) {
        val base = k.simpleName ?: return
        val unionParts = mutableListOf<String>()
        k.sealedSubclasses.forEach { sub ->
            when {
                sub.java.isEnum -> emitEnum(ctx, sub)
                sub.isData -> {
                    val typedefName = "${base}_${sub.simpleName}".replace('.', '_')
                    unionParts += typedefName
                    emitDataClass(ctx, sub, typedefName)
                }
                sub.isSealed -> {
                    emitSealed(ctx, sub)
                    sub.simpleName?.let { unionParts += it }
                }
            }
        }
        if (unionParts.isNotEmpty()) {
            ctx.unionBuffer += "/**\n * @typedef {(${unionParts.joinToString("|")})} $base\n */\n"
        }
    }

    private fun emitDataClass(ctx: Context, k: KClass<*>, typedefNameOverride: String? = null) {
        val name = typedefNameOverride ?: k.simpleName ?: return
        val props = k.memberProperties
            .sortedBy { it.name }
            .mapNotNull { p ->
                val t = p.returnType
                val classifier = t.classifier as? KClass<*>
                if (classifier != null && classifier.qualifiedName?.startsWith("kotlin.Function") == true) return@mapNotNull null
                val jsType = mapType(ctx, t)
                " * @property {$jsType} ${p.name}" + if (t.isMarkedNullable) " (nullable)" else ""
            }
        ctx.typedefBuffer += "/**\n * @typedef {Object} $name\n${props.joinToString("\n")}\n */\n"
    }

    private fun mapType(ctx: Context, t: KType, depth: Int = 0): String {
        val cls = t.classifier as? KClass<*> ?: return "any"
        val base = when {
            cls in primitiveNumber -> "number"
            cls == String::class -> "string"
            cls == Boolean::class -> "boolean"
            cls == List::class || cls == MutableList::class -> {
                val arg = t.arguments.firstOrNull()?.type
                val inner = arg?.let { mapType(ctx, it, depth + 1) } ?: "any"
                "$inner[]"
            }
            cls == Map::class || cls == MutableMap::class -> {
                val kType = t.arguments.getOrNull(0)?.type
                val vType = t.arguments.getOrNull(1)?.type
                val keyStr = if (kType?.classifier == String::class) "string" else mapType(ctx, kType ?: String::class.createType())
                val valStr = vType?.let { mapType(ctx, it, depth + 1) } ?: "any"
                "Object.<$keyStr, $valStr>"
            }
            cls.java.isEnum -> { enqueue(ctx, cls); cls.simpleName ?: "string" }
            cls.isSealed -> { enqueue(ctx, cls); cls.simpleName ?: "Object" }
            cls.isData -> { enqueue(ctx, cls); cls.simpleName ?: "Object" }
            else -> cls.simpleName ?: "any"
        }
        return if (t.isMarkedNullable) "($base|null)" else base
    }
}

private fun parseTopLevelTypesFromFile(file: File): List<KClass<*>> {
    if (!file.exists()) return emptyList()
    val text = file.readText()
    val pkg = Regex("^\\s*package\\s+([a-zA-Z0-9_.]+)", RegexOption.MULTILINE)
        .find(text)?.groupValues?.get(1) ?: return emptyList()
    val classRegex = Regex("^(?:[a-zA-Z0-9_]+\\s+)*(class|interface|object)\\s+([A-Za-z0-9_]+)", RegexOption.MULTILINE)
    val names = classRegex.findAll(text)
        .map { it.groupValues[2] }
        .filter { it.isNotBlank() }
        .toSet()
    return names.mapNotNull { n ->
        try { Class.forName("$pkg.$n").kotlin } catch (_: Throwable) { null }
    }
}

fun main(args: Array<String>) {
    val argMap = parseArgs(args)
    val outPath = argMap["out"] ?: argMap["o"] ?: "frontend/src/generated-types.js"
    val sourceFile = argMap["file"] ?: argMap["f"]

    val blacklist: Set<String> = setOfNotNull(
        CombatEvent::class.simpleName,
        ActorSnapshot::class.simpleName,
    )

    val discovered = sourceFile?.let { parseTopLevelTypesFromFile(File(it)) } ?: emptyList()

    // Filter out blacklisted simple or qualified names
    val roots = discovered.filter { k ->
        val sn = k.simpleName
        val qn = k.qualifiedName
        (sn == null || sn !in blacklist) && (qn == null || qn !in blacklist)
    }

    val output = JsDocGenerator.generate(roots)
    File(outPath).apply {
        parentFile?.mkdirs()
        writeText(output)
        println("[generateJsDoc] Roots: ${roots.mapNotNull { it.simpleName }}")
        if (blacklist.isNotEmpty()) println("[generateJsDoc] Blacklist(simple): $blacklist")
        println("[generateJsDoc] Wrote $absolutePath (${output.length} chars)")
    }
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            val key = a.removePrefix("--")
            val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith('-') }
            if (value != null) { map[key] = value; i += 2; continue } else map[key] = "true"
        } else if (a.startsWith('-')) {
            val key = a.removePrefix("-")
            val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith('-') }
            if (value != null) { map[key] = value; i += 2; continue } else map[key] = "true"
        }
        i++
    }
    return map
}
