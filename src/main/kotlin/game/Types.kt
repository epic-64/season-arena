package game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.full.memberProperties

typealias Turns = Int

fun Int.turns() = this

@Serializable
enum class BuffId(val label: String) {
    Amplify("Amplify"),
    Cheer("Cheer"),
    MoraleBoost("Morale Boost"),
    Burn("Burn"),
    Shock("Shock"),
    Regen("Regen"),
    Protection("Protection"),
    Chill("Chill"),
    Poison("Poison"),
}

sealed class TemporalEffect {
    abstract val id: BuffId
    abstract val duration: Int

    fun decrement(): TemporalEffect = when (this) {
        is StatBuff -> copy(duration = duration - 1)
        is ResourceTick -> copy(duration = duration - 1)
        is StatOverride -> copy(duration = duration - 1)
        is DamageOverTime -> copy(duration = duration - 1)
    }

    data class StatBuff(
        override val id: BuffId,
        override val duration: Int,
        val statChanges: Map<String, Int>
    ) : TemporalEffect()

    data class StatOverride(
        override val id: BuffId,
        override val duration: Int,
        val statOverrides: Map<String, Int>
    ) : TemporalEffect()

    data class ResourceTick(
        override val id: BuffId,
        override val duration: Int,
        val resourceChanges: Map<String, Int>
    ) : TemporalEffect()

    data class DamageOverTime(
        override val id: BuffId,
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
    data class RemoveTemporalEffect(val effectId: BuffId) : SkillEffectType()
}

data class SkillEffect(
    val type: SkillEffectType,
    val targetRule: (Actor, List<Actor>, List<Actor>, List<Actor>) -> List<Actor> =
        { _, _, _, previous -> previous }
)

typealias targetSelectionFn = (Actor, List<Actor>, List<Actor>) -> List<Actor>

data class Skill(
    val description: String,
    val name: String,
    val effects: List<SkillEffect>,
    val maximumTargets: Int,
    val targetsOverride: targetSelectionFn? = null,
    val condition: (Actor, List<Actor>, List<Actor>) -> Boolean = { _, _, _ -> true },
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
            else -> baseDamage // todo: handle elemental damage amplification
        }
    }
}

// --- Condition Function and Conditional Skill ---
typealias ConditionFn = (Actor, List<Actor>, List<Actor>) -> Boolean
typealias TargetFn = (Actor, List<Actor>, List<Actor>) -> List<Actor>
typealias PriorityFn = (List<Actor>) -> List<Actor>

data class Tactic(
    val conditions: List<ConditionFn>,
    val skill: Skill,
    val targetGroup: TargetFn,
    val ordering: List<PriorityFn> = emptyList()
) {
    fun getTargets(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> {
        val initialTargets = targetGroup(actor, allies, enemies)
        val ordered = ordering.fold(initialTargets) { currentTargets, ordering -> ordering(currentTargets) }
        return ordered.take(skill.maximumTargets)
    }
}

data class ResistanceBag(
    val physical: Int,
    val ice: Int,
    val fire: Int,
    val lightning: Int,
    val chaos: Int,
) {
    companion object {
        fun default() = ResistanceBag(
            physical = 0,
            ice = 0,
            fire = 0,
            lightning = 0,
            chaos = 0
        )
    }
}

// Container for core combat resources
interface ResourceStats {
    fun getHp(): Int
    fun setHp(value: Int)
    fun getMana(): Int
    fun setMana(value: Int)
    val maxHp: Int
    val maxMana: Int
    val hpRegenPerTurn: Int
    val manaRegenPerTurn: Int
    val isAlive: Boolean
}

data class StatsBag(
    private var mana: Int,
    private var hp: Int,
    override val maxHp: Int,
    override val maxMana: Int,
    override val hpRegenPerTurn: Int,
    override val manaRegenPerTurn: Int,
) : ResourceStats {
    override val isAlive: Boolean get() = hp > 0
    override fun getHp(): Int = hp
    override fun getMana(): Int = mana
    override fun setHp(value: Int) { hp = value.coerceIn(0, maxHp) }
    override fun setMana(value: Int) { mana = value.coerceIn(0, maxMana) }

    companion object {
        fun default() = StatsBag(
            hp = 100,
            maxHp = 100,
            mana = 100,
            maxMana = 100,
            hpRegenPerTurn = 0,
            manaRegenPerTurn = 0
        )
    }
}

// --- Actor ---
data class Actor(
    val team: Int, // 0 or 1
    val actorClass: ActorClass,
    val name: String,
    val statsBag: StatsBag,
    val tactics: List<Tactic>,
    val amplifiers: Amplifiers = Amplifiers(),
    val resistances: Map<DamageType, Int> = mapOf(),
    val stats: MutableMap<String, Int> = mutableMapOf(),
    val temporalEffects: MutableList<TemporalEffect> = mutableListOf(),
    val cooldowns: MutableMap<Skill, Int> = mutableMapOf(),
) : ResourceStats by statsBag {
    override fun setHp(value: Int) {
        statsBag.setHp(value)

        if (!isAlive) {
            statsBag.setMana(0)
            temporalEffects.clear()
            cooldowns.clear()
        }
    }

    fun deepCopy(): Actor {
        return Actor(
            actorClass = actorClass,
            name = name,
            statsBag = statsBag.copy(),
            tactics = tactics, // immutable assumed
            team = team,
            stats = stats.toMutableMap(),
            temporalEffects = temporalEffects.toMutableList(),
            cooldowns = cooldowns.toMutableMap(),
            amplifiers = amplifiers,
            resistances = resistances.toMap(),
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
    @SerialName("BuffRemoved")
    data class BuffRemoved(
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
    @SerialName("BuffRemoved")
    data class CBuffRemoved(
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
