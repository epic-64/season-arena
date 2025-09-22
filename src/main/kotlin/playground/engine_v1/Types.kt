package playground.engine_v1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.full.memberProperties

sealed class DurationEffect {
    abstract val id: String
    abstract val duration: Int

    data class StatBuff(
        override val id: String,
        override val duration: Int,
        val statChanges: Map<String, Int>
    ) : DurationEffect()

    data class StatOverride(
        override val id: String,
        override val duration: Int,
        val statOverrides: Map<String, Int>
    ) : DurationEffect()

    data class ResourceTick(
        override val id: String,
        override val duration: Int,
        val resourceChanges: Map<String, Int>
    ) : DurationEffect()

    data class DamageOverTime(
        override val id: String,
        override val duration: Int,
        val damageType: DamageType,
        val amount: Int
    ) : DurationEffect()
}

enum class DamageType {
    Physical,
    Magical,
    Absolute,
}

sealed class SkillEffectType {
    data class Damage(val damageType: DamageType, val amount: Int) : SkillEffectType()
    data class Heal(val power: Int) : SkillEffectType()
    data class StatBuff(val buff: DurationEffect.StatBuff) : SkillEffectType()
    data class ResourceTick(val resourceTick: DurationEffect.ResourceTick) : SkillEffectType()
    data class StatOverride(val statOverride: DurationEffect.StatOverride) : SkillEffectType()
    data class DamageOverTime(val dot: DurationEffect.DamageOverTime) : SkillEffectType()
}

data class SkillEffect(
    val type: SkillEffectType,
    val targetRule: (Actor, List<Actor>, List<Actor>, List<Actor>) -> List<Actor> = {
        actor, allies, enemies, previous -> previous
    }
)

data class Skill(
    val name: String,
    val effects: List<SkillEffect>,
    val initialTargets: (Actor, List<Actor>, List<Actor>) -> List<Actor>,
    val activationRule: (Actor, List<Actor>, List<Actor>) -> Boolean,
    val cooldown: Int // cooldown in turns
)

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

// --- Actor ---
data class Actor(
    val actorClass: ActorClass,
    val name: String,
    private var hp: Int,
    val maxHp: Int,
    val skills: List<Skill>,
    val team: Int, // 0 or 1
    val amplifiers: Amplifiers = Amplifiers(),
    val stats: MutableMap<String, Int> = mutableMapOf(),
    val temporalEffects: MutableList<DurationEffect> = mutableListOf(),
    val cooldowns: MutableMap<Skill, Int> = mutableMapOf() // skill -> turns left
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

    fun deepCopy(): Actor {
        return Actor(
            actorClass = actorClass,
            name = name,
            hp = hp,
            maxHp = maxHp,
            skills = skills, // Skills are immutable
            team = team,
            stats = stats.toMutableMap(),
            temporalEffects = temporalEffects.toMutableList(),
            cooldowns = cooldowns.toMutableMap(),
            amplifiers = amplifiers,
        )
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
    @Serializable
    @SerialName("TurnStart")
    data class TurnStart(val turn: Int, val snapshot: BattleSnapshot) : CombatEvent()

    @Serializable
    @SerialName("SkillUsed")
    data class SkillUsed(
        val actor: String,
        val skill: String,
        val targets: List<String>,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("DamageDealt")
    data class DamageDealt(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val snapshot: BattleSnapshot,
        val modifiers: List<DamageModifier>
    ) : CombatEvent()

    @Serializable
    @SerialName("Healed")
    data class Healed(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("BuffApplied")
    data class BuffApplied(
        val source: String,
        val target: String,
        val buffId: String,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("ResourceDrained")
    data class ResourceDrained(
        val target: String,
        val buffId: String,
        val resource: String,
        val amount: Int,
        val targetResourceValue: Int,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    @SerialName("BattleEnd")
    data class BattleEnd(val winner: String, val snapshot: BattleSnapshot) : CombatEvent()
}

// --- Delta and Compact Event Types ---

@Serializable
data class ActorDelta(
    val name: String,
    val hp: Int? = null,
    val maxHp: Int? = null,
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

fun BattleDelta.Companion.fromFullSnapshot(snapshot: BattleSnapshot): BattleDelta {
    return BattleDelta(snapshot.actors.map {
        ActorDelta(
            name = it.name,
            hp = it.hp,
            maxHp = it.maxHp,
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
    @SerialName("TurnStart")
    data class TurnStart(val turn: Int, val snapshot: BattleSnapshot) : CompactCombatEvent()

    @Serializable
    @SerialName("SkillUsed")
    data class SkillUsed(
        val actor: String,
        val skill: String,
        val targets: List<String>,
        val delta: BattleDelta,
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("DamageDealt")
    data class DamageDealt(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("Healed")
    data class Healed(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("BuffApplied")
    data class BuffApplied(
        val source: String,
        val target: String,
        val buffId: String,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("BuffExpired")
    data class BuffExpired(val target: String, val buffId: String, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("ResourceDrained")
    data class ResourceDrained(
        val target: String,
        val buffId: String,
        val resource: String,
        val amount: Int,
        val targetResourceValue: Int,
        val delta: BattleDelta
    ) : CompactCombatEvent()

    @Serializable
    @SerialName("BattleEnd")
    data class BattleEnd(val winner: String, val delta: BattleDelta) : CompactCombatEvent()
}

fun toCompactCombatEvents(events: List<CombatEvent>): List<CompactCombatEvent> {
    val compactEvents = mutableListOf<CompactCombatEvent>()
    var prevSnapshot: BattleSnapshot? = null
    for (event in events) {
        when (event) {
            is CombatEvent.TurnStart -> {
                compactEvents.add(CompactCombatEvent.TurnStart(event.turn, event.snapshot))
                prevSnapshot = event.snapshot
            }
            is CombatEvent.SkillUsed -> {
                val delta = prevSnapshot?.let { computeBattleDelta(it, event.snapshot) } ?: BattleDelta.fromFullSnapshot(event.snapshot)
                compactEvents.add(CompactCombatEvent.SkillUsed(event.actor, event.skill, event.targets, delta))
                prevSnapshot = event.snapshot
            }
            is CombatEvent.DamageDealt -> {
                val delta = prevSnapshot?.let { computeBattleDelta(it, event.snapshot) } ?: BattleDelta.fromFullSnapshot(event.snapshot)
                compactEvents.add(CompactCombatEvent.DamageDealt(event.source, event.target, event.amount, event.targetHp, delta))
                prevSnapshot = event.snapshot
            }
            is CombatEvent.Healed -> {
                val delta = prevSnapshot?.let { computeBattleDelta(it, event.snapshot) } ?: BattleDelta.fromFullSnapshot(event.snapshot)
                compactEvents.add(CompactCombatEvent.Healed(event.source, event.target, event.amount, event.targetHp, delta))
                prevSnapshot = event.snapshot
            }
            is CombatEvent.BuffApplied -> {
                val delta = prevSnapshot?.let { computeBattleDelta(it, event.snapshot) } ?: BattleDelta.fromFullSnapshot(event.snapshot)
                compactEvents.add(CompactCombatEvent.BuffApplied(event.source, event.target, event.buffId, delta))
                prevSnapshot = event.snapshot
            }
            is CombatEvent.ResourceDrained -> {
                val delta = prevSnapshot?.let { computeBattleDelta(it, event.snapshot) } ?: BattleDelta.fromFullSnapshot(event.snapshot)
                compactEvents.add(CompactCombatEvent.ResourceDrained(event.target, event.buffId, event.resource, event.amount, event.targetResourceValue, delta))
                prevSnapshot = event.snapshot
            }
            is CombatEvent.BattleEnd -> {
                val delta = prevSnapshot?.let { computeBattleDelta(it, event.snapshot) } ?: BattleDelta.fromFullSnapshot(event.snapshot)
                compactEvents.add(CompactCombatEvent.BattleEnd(event.winner, delta))
                prevSnapshot = event.snapshot
            }
        }
    }
    return compactEvents
}
