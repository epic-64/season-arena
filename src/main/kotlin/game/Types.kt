package game

import game.CombatEvent.*
import game.CompactCombatEvent.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.iterator
import kotlin.reflect.full.memberProperties

sealed class TemporalEffect {
    abstract val id: String
    abstract val duration: Int

    fun decrement(): TemporalEffect = when (this) {
        is StatBuff -> copy(duration = duration - 1)
        is ResourceTick -> copy(duration = duration - 1)
        is StatOverride -> copy(duration = duration - 1)
        is DamageOverTime -> copy(duration = duration - 1)
    }

    data class StatBuff(
        override val id: String,
        override val duration: Int,
        val statChanges: Map<String, Int>
    ) : TemporalEffect()

    data class StatOverride(
        override val id: String,
        override val duration: Int,
        val statOverrides: Map<String, Int>
    ) : TemporalEffect()

    data class ResourceTick(
        override val id: String,
        override val duration: Int,
        val resourceChanges: Map<String, Int>
    ) : TemporalEffect()

    data class DamageOverTime(
        override val id: String,
        override val duration: Int,
        val damageType: DamageType,
        val amount: Int
    ) : TemporalEffect()
}

enum class DamageType {
    Physical,
    Magical,
    Ice,
    Fire,
    Lightning,
    Poison,
    Absolute,
}

sealed class SkillEffectType {
    data class Damage(val damageType: DamageType, val amount: Int) : SkillEffectType()
    data class Heal(val power: Int) : SkillEffectType()
    data class StatBuff(val buff: TemporalEffect.StatBuff) : SkillEffectType()
    data class ResourceTick(val resourceTick: TemporalEffect.ResourceTick) : SkillEffectType()
    data class StatOverride(val statOverride: TemporalEffect.StatOverride) : SkillEffectType()
    data class DamageOverTime(val dot: TemporalEffect.DamageOverTime) : SkillEffectType()
}

data class SkillEffect(
    val type: SkillEffectType,
    val targetRule: (Actor, List<Actor>, List<Actor>, List<Actor>) -> List<Actor> = { actor, allies, enemies, previous ->
        previous
    }
)

data class Skill(
    val name: String,
    val effects: List<SkillEffect>,
    val initialTargets: (Actor, List<Actor>, List<Actor>) -> List<Actor>,
    val condition: (Actor, List<Actor>, List<Actor>) -> Boolean,
    val cooldown: Int,
    val manaCost: Int,
) {
    fun withConditions(vararg conditions: ConditionFn): ConditionalSkill {
        return ConditionalSkill(conditions.toList(), this)
    }
}

enum class ActorClass {
    Fighter,
    Mage,
    Cleric,
    Rogue,
    Hunter,
    Paladin,
    AbyssalDragon,
    Bard,
    Fishman,
}

data class Amplifiers(
    val physicalDamageAdded: Double = 0.0,
    val physicalDamageMultiplier: Double = 1.0,

    val magicalDamageAdded: Double = 0.0,
    val magicalDamageMultiplier: Double = 1.0,

    val absoluteDamageAdded: Double = 0.0,
    val absoluteDamageMultiplier: Double = 1.0,
) {
    fun getAmplifiedDamage(damageType: DamageType, baseDamage: Int): Int {
        return when (damageType) {
            DamageType.Physical -> ((baseDamage + physicalDamageAdded) * physicalDamageMultiplier).toInt()
            DamageType.Magical -> ((baseDamage + magicalDamageAdded) * magicalDamageMultiplier).toInt()
            DamageType.Absolute -> ((baseDamage + absoluteDamageAdded) * absoluteDamageMultiplier).toInt()
        }
    }
}

// --- Condition Function and Conditional Skill ---
typealias ConditionFn = (Actor, List<Actor>, List<Actor>) -> Boolean

data class ConditionalSkill(
    val conditions: List<ConditionFn>,
    val skill: Skill
)

// --- Actor ---
data class Actor(
    val actorClass: ActorClass,
    val name: String,
    private var hp: Int,
    val maxHp: Int,
    private var mana: Int, // current mana
    val maxMana: Int, // maximum mana
    val skills: List<ConditionalSkill>,
    val team: Int, // 0 or 1
    val amplifiers: Amplifiers = Amplifiers(),
    val resistances: Map<DamageType, Int> = mapOf(),
    val stats: MutableMap<String, Int> = mutableMapOf(),
    val temporalEffects: MutableList<TemporalEffect> = mutableListOf(),
    val cooldowns: MutableMap<Skill, Int> = mutableMapOf(), // skill -> turns left
    val hpRegenPerTurn: Int = 0, // new: passive hp regeneration per turn (applied at start of turn)
    val manaRegenPerTurn: Int = 0, // new: passive mana regeneration per turn (applied at start of turn)
) {
    val isAlive: Boolean get() = hp > 0

    fun getHp(): Int = hp

    fun setHp(value: Int) {
        hp = value.coerceIn(0, maxHp)

        if (!isAlive) {
            temporalEffects.clear()
            cooldowns.clear()
        }
    }

    fun getMana(): Int = mana

    fun setMana(value: Int) {
        mana = value.coerceIn(0, maxMana)
    }

    fun deepCopy(): Actor {
        return Actor(
            actorClass = actorClass,
            name = name,
            hp = hp,
            maxHp = maxHp,
            mana = mana,
            maxMana = maxMana,
            skills = skills, // Skills are immutable
            team = team,
            stats = stats.toMutableMap(),
            temporalEffects = temporalEffects.toMutableList(),
            cooldowns = cooldowns.toMutableMap(),
            amplifiers = amplifiers,
            hpRegenPerTurn = hpRegenPerTurn,
            manaRegenPerTurn = manaRegenPerTurn,
        )
    }

    fun getResistance(damageType: DamageType): Int {
        return resistances.getOrDefault(damageType, 0)
    }
}

// --- Team ---
data class Team(val actors: MutableList<Actor>) {
    fun aliveActors() = actors.filter { it.isAlive }
    fun deepCopy(): Team {
        return Team(actors.map { it.deepCopy() }.toMutableList())
    }
}

// --- Actor Snapshot Data Structure ---
@Serializable
data class ActorSnapshot(
    val actorClass: ActorClass,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val mana: Int,
    val maxMana: Int,
    val team: Int,
    val stats: Map<String, Int>,
    val statBuffs: List<StatBuffSnapshot>,
    val resourceTicks: List<ResourceTickSnapshot>,
    val statOverrides: List<StatOverrideSnapshot>,
    val cooldowns: Map<String, Int> // skill name -> cooldown
)

@Serializable
data class StatBuffSnapshot(
    val id: String,
    val duration: Int,
    val statChanges: Map<String, Int>
)

@Serializable
data class ResourceTickSnapshot(
    val id: String,
    val duration: Int,
    val resourceChanges: Map<String, Int>
)

@Serializable
data class StatOverrideSnapshot(
    val id: String,
    val duration: Int,
    val statOverrides: Map<String, Int>
)

@Serializable
data class BattleSnapshot(
    val actors: List<ActorSnapshot>
)

enum class DamageModifier {
    Critical,
    Blocked,
    Resisted
}

@Serializable
sealed class CombatEvent {
    abstract val snapshot: BattleSnapshot

    @Serializable
    @SerialName("BattleStart")
    data class BattleStart(override val snapshot: BattleSnapshot) : CombatEvent()

    @Serializable
    @SerialName("TurnStart")
    data class TurnStart(val turn: Int, override val snapshot: BattleSnapshot) : CombatEvent()

    @Serializable
    @SerialName("SkillUsed")
    data class SkillUsed(
        val actor: String,
        val skill: String,
        val targets: List<String>,
        override val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("DamageDealt")
    data class DamageDealt(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        override val snapshot: BattleSnapshot,
        val modifiers: List<DamageModifier>
    ) : CombatEvent()

    @Serializable
    @SerialName("Healed")
    data class Healed(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        override val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("BuffApplied")
    data class BuffApplied(
        val source: String,
        val target: String,
        val buffId: String,
        override val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("ResourceDrained")
    data class ResourceDrained(
        val target: String,
        val buffId: String,
        val resource: String,
        val amount: Int,
        val newValue: Int,
        override val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("ResourceRegenerated")
    data class ResourceRegenerated(
        val target: String,
        val resource: String,
        val amount: Int,
        val newValue: Int,
        override val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("BattleEnd")
    data class BattleEnd(val winner: String, override val snapshot: BattleSnapshot) : CombatEvent()
}

// --- Delta and Compact Event Types ---

@Serializable
data class ActorDelta(
    val name: String,
    val hp: Int? = null,
    val maxHp: Int? = null,
    val mana: Int? = null,
    val maxMana: Int? = null,
    val stats: Map<String, Int>? = null,
    val statBuffs: List<StatBuffSnapshot>? = null,
    val resourceTicks: List<ResourceTickSnapshot>? = null,
    val statOverrides: List<StatOverrideSnapshot>? = null,
    val cooldowns: Map<String, Int>? = null,
)

@Serializable
data class BattleDelta(
    val actors: List<ActorDelta>
)

fun ActorDelta.hasAnyChange(): Boolean {
    return ActorDelta::class.memberProperties
        .filter { it.name != "name" }
        .any { it.get(this) != null }
}

fun computeBattleDelta(prev: BattleSnapshot, curr: BattleSnapshot): BattleDelta {
    val prevActors = prev.actors.associateBy { it.name }
    val currActors = curr.actors.associateBy { it.name }
    val deltas = mutableListOf<ActorDelta>()
    for ((name, currActor) in currActors) {
        val prevActor = prevActors[name]
        if (prevActor == null) {
            // New actor, include full
            deltas.add(
                ActorDelta(
                    name = currActor.name,
                    hp = currActor.hp,
                    maxHp = currActor.maxHp,
                    mana = currActor.mana,
                    maxMana = currActor.maxMana,
                    stats = currActor.stats,
                    statBuffs = currActor.statBuffs,
                    resourceTicks = currActor.resourceTicks,
                    statOverrides = currActor.statOverrides,
                    cooldowns = currActor.cooldowns
                )
            )
        } else {
            val delta = ActorDelta(
                name = currActor.name,
                hp = if (currActor.hp != prevActor.hp) currActor.hp else null,
                maxHp = if (currActor.maxHp != prevActor.maxHp) currActor.maxHp else null,
                mana = if (currActor.mana != prevActor.mana) currActor.mana else null,
                maxMana = if (currActor.maxMana != prevActor.maxMana) currActor.maxMana else null,
                stats = if (currActor.stats != prevActor.stats) currActor.stats else null,
                statBuffs = if (currActor.statBuffs != prevActor.statBuffs) currActor.statBuffs else null,
                resourceTicks = if (currActor.resourceTicks != prevActor.resourceTicks) currActor.resourceTicks else null,
                statOverrides = if (currActor.statOverrides != prevActor.statOverrides) currActor.statOverrides else null,
                cooldowns = if (currActor.cooldowns != prevActor.cooldowns) currActor.cooldowns else null
            )

            if (delta.hasAnyChange()) {
                deltas.add(delta)
            }
        }
    }
    return BattleDelta(deltas)
}

fun BattleDelta.Companion.fullSnapshot(snapshot: BattleSnapshot): BattleDelta {
    return BattleDelta(snapshot.actors.map {
        ActorDelta(
            name = it.name,
            hp = it.hp,
            maxHp = it.maxHp,
            mana = it.mana,
            maxMana = it.maxMana,
            stats = it.stats,
            statBuffs = it.statBuffs,
            resourceTicks = it.resourceTicks,
            statOverrides = it.statOverrides,
            cooldowns = it.cooldowns
        )
    })
}

@Serializable
sealed class CompactCombatEvent {
    @Serializable
    @SerialName("BattleStart")
    data class CBattleStart(val snapshot: BattleSnapshot) : CompactCombatEvent()

    @Serializable
    @SerialName("TurnStart")
    data class CTurnStart(val turn: Int, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("SkillUsed")
    data class CSkillUsed(
        val actor: String,
        val skill: String,
        val targets: List<String>,
        val delta: BattleDelta,
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("DamageDealt")
    data class CDamageDealt(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("Healed")
    data class CHealed(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("BuffApplied")
    data class CBuffApplied(
        val source: String,
        val target: String,
        val buffId: String,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("BuffExpired")
    data class CBuffExpired(val target: String, val buffId: String, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("ResourceDrained")
    data class CResourceDrained(
        val target: String,
        val buffId: String,
        val resource: String,
        val amount: Int,
        val targetResourceValue: Int,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("ResourceRegenerated")
    data class CResourceRegenerated(
        val target: String,
        val resource: String,
        val amount: Int,
        val targetResourceValue: Int,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("BattleEnd")
    data class CBattleEnd(val winner: String, val delta: BattleDelta) : CompactCombatEvent()
}

fun CombatEvent.toCompactCombatEvent(delta: BattleDelta): CompactCombatEvent = when (this) {
    is BattleStart -> CBattleStart(snapshot)
    is TurnStart -> CTurnStart(turn, delta)
    is SkillUsed -> CSkillUsed(actor, skill, targets, delta)
    is DamageDealt -> CDamageDealt(source, target, amount, targetHp, delta)
    is Healed -> CHealed(source, target, amount, targetHp, delta)
    is BuffApplied -> CBuffApplied(source, target, buffId, delta)
    is ResourceDrained -> CResourceDrained(target, buffId, resource, amount, newValue, delta)
    is ResourceRegenerated -> CResourceRegenerated(target, resource, amount, newValue, delta)
    is BattleEnd -> CBattleEnd(winner, delta)
}

fun toCompactCombatEvents(events: List<CombatEvent>): List<CompactCombatEvent> {
    val compactEvents = mutableListOf<CompactCombatEvent>()

    val firstEvent = events.firstOrNull() ?: throw IllegalArgumentException("Event list is empty")

    // For the first event, we always include the full snapshot as delta
    var prevSnapshot: BattleSnapshot = firstEvent.snapshot
    val firstDelta = BattleDelta.fullSnapshot(prevSnapshot)
    val firstCompactEvent = firstEvent.toCompactCombatEvent(firstDelta)
    compactEvents.add(firstCompactEvent)
    prevSnapshot = firstEvent.snapshot

    for (event in events.drop(1)) {
        val delta = computeBattleDelta(prevSnapshot, event.snapshot)
        val compactEvent = event.toCompactCombatEvent(delta)
        compactEvents.add(compactEvent)
        prevSnapshot = event.snapshot
    }
    return compactEvents
}
