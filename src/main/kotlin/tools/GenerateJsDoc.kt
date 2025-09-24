package tools

import game.*
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Generates a JSDoc typedef file for selected Kotlin domain classes / enums / sealed hierarchies.
 * This is a pragmatic reflection-based exporter (not a full Kotlin->TypeScript mapper).
 *
 * Supported mappings:
 *  - Kotlin Int/Long/Short/Byte/Float/Double -> number
 *  - Kotlin String -> string
 *  - Kotlin Boolean -> boolean
 *  - List<T> -> T[]
 *  - Map<String, V> -> Object.<string, V>
 *  - Enums -> @readonly @enum {string}
 *  - Data classes -> @typedef {Object} Name (listing properties)
 *  - Sealed classes (with data class subclasses) ->
 *        * Each subclass becomes Typedef Base_Sub
 *        * A union typedef Base = (Base_Sub1|Base_Sub2|...)
 *
 * Function-typed properties are skipped.
 */
object JsDocGenerator {
    private data class Context(
        val processed: MutableSet<String> = mutableSetOf(),
        val enumBuffer: MutableList<String> = mutableListOf(),
        val typedefBuffer: MutableList<String> = mutableListOf(),
        val unionBuffer: MutableList<String> = mutableListOf(),
        val queue: ArrayDeque<KClass<*>> = ArrayDeque()
    )

    private val primitiveNumber = setOf(
        Int::class, Long::class, Short::class, Byte::class, Double::class, Float::class
    )

    fun generate(root: List<KClass<*>>): String {
        val ctx = Context()
        root.forEach { enqueue(ctx, it) }
        while (ctx.queue.isNotEmpty()) {
            val k = ctx.queue.removeFirst()
            if (!ctx.processed.add(k.qualifiedName ?: k.simpleName ?: continue)) continue
            when {
                k.java.isEnum -> emitEnum(ctx, k)
                k.isSealed() -> emitSealed(ctx, k)
                k.isData -> emitDataClass(ctx, k)
                else -> { /* skip non-data regular classes to avoid exposing internals */ }
            }
        }
        return buildString {
            append("/**\n * AUTO-GENERATED FILE. DO NOT EDIT DIRECTLY.\n * Regenerate with: ./gradlew generateJsDoc\n */\n\n")
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
        val subclasses = k.sealedSubclasses
        val unionParts = mutableListOf<String>()
        subclasses.forEach { sub ->
            when {
                sub.java.isEnum -> emitEnum(ctx, sub)
                sub.isData -> {
                    val typedefName = "${base}_${sub.simpleName}".replace(".", "_")
                    unionParts += typedefName
                    emitDataClass(ctx, sub, typedefNameOverride = typedefName)
                }
                sub.isSealed() -> {
                    emitSealed(ctx, sub) // nested sealed
                    val nestedUnion = sub.simpleName
                    if (nestedUnion != null) unionParts += nestedUnion
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
                // Skip function types
                if (t.classifier is KClass<*> && (t.classifier as KClass<*>).qualifiedName?.startsWith("kotlin.Function") == true) return@mapNotNull null
                val jsType = mapType(ctx, t)
                " * @property {$jsType} ${p.name}" + if (t.isMarkedNullable) " (nullable)" else ""
            }
        ctx.typedefBuffer += "/**\n * @typedef {Object} $name\n${props.joinToString("\n")}\n */\n"
    }

    private fun mapType(ctx: Context, t: KType, depth: Int = 0): String {
        val cls = t.classifier as? KClass<*> ?: return "any"
        return when {
            primitiveNumber.contains(cls) -> "number"
            cls == String::class -> "string"
            cls == Boolean::class -> "boolean"
            cls == List::class || cls == MutableList::class -> {
                val arg = t.arguments.firstOrNull()?.type
                val inner = if (arg != null) mapType(ctx, arg, depth + 1) else "any"
                "$inner[]"
            }
            cls == Map::class || cls == MutableMap::class -> {
                val kType = t.arguments.getOrNull(0)?.type
                val vType = t.arguments.getOrNull(1)?.type
                val keyStr = if (kType?.classifier == String::class) "string" else mapType(ctx, kType ?: String::class.createType())
                val valStr = if (vType != null) mapType(ctx, vType, depth + 1) else "any"
                "Object.<$keyStr, $valStr>"
            }
            cls.java.isEnum -> {
                enqueue(ctx, cls)
                cls.simpleName ?: "string"
            }
            cls.isSealed() -> {
                enqueue(ctx, cls)
                cls.simpleName ?: "Object"
            }
            cls.isData -> {
                enqueue(ctx, cls)
                cls.simpleName ?: "Object"
            }
            else -> {
                // Fallback for unknown classes: attempt simpleName or any
                cls.simpleName ?: "any"
            }
        }.let { base -> if (t.isMarkedNullable) "($base|null)" else base }
    }

    private fun KClass<*>.isSealed(): Boolean = this.isSealed
}

fun main(args: Array<String>) {
    val outPath = parseArgs(args)["out"] ?: parseArgs(args)["o"] ?: "generated-jsdoc.js"

    // Root types to export; extend as necessary.
    val roots: List<KClass<*>> = listOf(
        ActorSnapshot::class,
        StatBuffSnapshot::class,
        ResourceTickSnapshot::class,
        StatOverrideSnapshot::class,
        BattleSnapshot::class,
        DamageType::class,
        DamageModifier::class,
        CombatEvent::class,
        ActorDelta::class,
        BattleDelta::class,
        DurationEffect::class,
        SkillEffectType::class,
        Amplifiers::class,
        SkillEffect::class,
    )

    val output = JsDocGenerator.generate(roots)
    val file = File(outPath)
    file.parentFile?.mkdirs()
    file.writeText(output)
    println("[generateJsDoc] Wrote ${file.absolutePath} (${output.length} chars)")
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            val key = a.removePrefix("--")
            val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("-") }
            if (value != null) {
                map[key] = value
                i += 2
                continue
            } else {
                map[key] = "true"
            }
        } else if (a.startsWith("-")) {
            val key = a.removePrefix("-")
            val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("-") }
            if (value != null) {
                map[key] = value
                i += 2
                continue
            } else {
                map[key] = "true"
            }
        }
        i++
    }
    return map
}

