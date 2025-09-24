package game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.iterator
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

enum class DamageModifier {
    Critical,
    Blocked,
    Resisted
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
    val cooldown: Int,
    val manaCost: Int,
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
    private var mana: Int, // current mana
    val maxMana: Int, // maximum mana
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

// --- Actor Delta Data Structure ---
@Serializable
data class StatBuffDelta(val id: String, val duration: Int, val statChanges: Map<String, Int>)
@Serializable
data class ResourceTickDelta(val id: String, val duration: Int, val resourceChanges: Map<String, Int>)
@Serializable
data class StatOverrideDelta(val id: String, val duration: Int, val statOverrides: Map<String, Int>)

@Serializable
data class ActorDelta(
    val name: String,
    val hp: Int? = null,
    val maxHp: Int? = null,
    val mana: Int? = null,
    val maxMana: Int? = null,
    val stats: Map<String, Int>? = null,
    val statBuffs: List<StatBuffDelta>? = null,
    val resourceTicks: List<ResourceTickDelta>? = null,
    val statOverrides: List<StatOverrideDelta>? = null,
    val cooldowns: Map<String, Int>? = null,
)

@Serializable
data class BattleDelta(
    val actors: List<ActorDelta>
)

// --- Combat Events ---
@Serializable
sealed class CombatEvent {
    @Serializable
    @SerialName("TurnStart")
    data class TurnStart(val turn: Int, val delta: BattleDelta) : CombatEvent()

    @Serializable
    @SerialName("SkillUsed")
    data class SkillUsed(
        val actor: String,
        val skill: String,
        val targets: List<String>,
        val delta: BattleDelta
    ) : CombatEvent()

    @Serializable
    @SerialName("DamageDealt")
    data class DamageDealt(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val delta: BattleDelta,
        val modifiers: List<DamageModifier>
    ) : CombatEvent()

    @Serializable
    @SerialName("Healed")
    data class Healed(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val delta: BattleDelta
    ) : CombatEvent()

    @Serializable
    @SerialName("BuffApplied")
    data class BuffApplied(
        val source: String,
        val target: String,
        val buffId: String,
        val delta: BattleDelta
    ) : CombatEvent()

    @Serializable
    @SerialName("ResourceDrained")
    data class ResourceDrained(
        val target: String,
        val buffId: String,
        val resource: String,
        val amount: Int,
        val targetResourceValue: Int,
        val delta: BattleDelta
    ) : CombatEvent()

    @Serializable
    @SerialName("BattleEnd")
    data class BattleEnd(val winner: String, val delta: BattleDelta) : CombatEvent()
}

// --- Delta and Compact Event Types ---
@Serializable
sealed class CompactCombatEvent {
    @Serializable
    @SerialName("TurnStart")
    data class TurnStart(val turn: Int, val delta: BattleDelta) : CompactCombatEvent()

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
    for (event in events) {
        when (event) {
            is CombatEvent.TurnStart -> {
                compactEvents.add(CompactCombatEvent.TurnStart(event.turn, event.delta))
            }
            is CombatEvent.SkillUsed -> {
                compactEvents.add(CompactCombatEvent.SkillUsed(event.actor, event.skill, event.targets, event.delta))
            }
            is CombatEvent.DamageDealt -> {
                compactEvents.add(CompactCombatEvent.DamageDealt(event.source, event.target, event.amount, event.targetHp, event.delta))
            }
            is CombatEvent.Healed -> {
                compactEvents.add(CompactCombatEvent.Healed(event.source, event.target, event.amount, event.targetHp, event.delta))
            }
            is CombatEvent.BuffApplied -> {
                compactEvents.add(CompactCombatEvent.BuffApplied(event.source, event.target, event.buffId, event.delta))
            }
            is CombatEvent.ResourceDrained -> {
                compactEvents.add(CompactCombatEvent.ResourceDrained(event.target, event.buffId, event.resource, event.amount, event.targetResourceValue, event.delta))
            }
            is CombatEvent.BattleEnd -> {
                compactEvents.add(CompactCombatEvent.BattleEnd(event.winner, event.delta))
            }
        }
    }
    return compactEvents
}
