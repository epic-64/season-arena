package game

import game.CombatEvent.*
import game.CompactCombatEvent.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// --- Basic Aliases / Typealiases ---
typealias Turns = Int

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
    Empower("Empower"),
}

// TemporalEffect used by actors for active buffs
data class TemporalEffect(val id: BuffId, val duration: Turns, val stacks: Int = 1) {
    fun decrement(): TemporalEffect = copy(duration = duration - 1)
}

object BuffRegistry {
    data class BuffDefinition(
        val statBuff: Map<String, Int> = emptyMap(),
        val statOverride: Map<String, Int> = emptyMap(),
        val resourceTick: Map<String, Int> = emptyMap(),
        val damageOverTime: Pair<DamageType, Int>? = null
    )

    val definitions: Map<BuffId, BuffDefinition> = mapOf(
        BuffId.Amplify to BuffDefinition(statBuff = mapOf("amplify" to 200)),
        BuffId.Cheer to BuffDefinition(statOverride = mapOf("critChance" to 100)),
        BuffId.MoraleBoost to BuffDefinition(statBuff = mapOf("attack" to 10)),
        BuffId.Burn to BuffDefinition(resourceTick = mapOf("hp" to -10)),
        BuffId.Shock to BuffDefinition(statBuff = mapOf("def" to -5)),
        BuffId.Regen to BuffDefinition(resourceTick = mapOf("hp" to 5)),
        BuffId.Protection to BuffDefinition(statBuff = mapOf("protection" to 10)),
        BuffId.Chill to BuffDefinition(statBuff = mapOf("amplify" to -5)),
        BuffId.Poison to BuffDefinition(resourceTick = mapOf("hp" to -5)),
        BuffId.Empower to BuffDefinition(statBuff = mapOf("strength" to 5)),
    )
}

enum class DamageType { Physical, Magical, Ice, Fire, Lightning, Poison, Absolute }

// --- Skill / Effects ---
sealed class SkillEffectType {
    data class Damage(val damageType: DamageType, val amount: Int) : SkillEffectType()
    data class Heal(val power: Int) : SkillEffectType()
    data class ApplyBuff(val id: BuffId, val duration: Int, val stacks: Int = 1) : SkillEffectType()
    data class RemoveTemporalEffect(val effectId: BuffId) : SkillEffectType()
}

data class SkillEffect(
    val type: SkillEffectType,
    val targetRule: (Actor, List<Actor>, List<Actor>, List<Actor>) -> List<Actor> = { _, _, _, previous -> previous }
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

enum class ActorClass { Fighter, Mage, Cleric, Rogue, Hunter, Paladin, AbyssalDragon, Bard, Fishman }

data class Amplifiers(
    val physicalDamageAdded: Double = 0.0,
    val physicalDamageMultiplier: Double = 1.0,
    val magicalDamageAdded: Double = 0.0,
    val magicalDamageMultiplier: Double = 1.0,
    val absoluteDamageAdded: Double = 0.0,
    val absoluteDamageMultiplier: Double = 1.0,
) {
    fun getAmplifiedDamage(damageType: DamageType, baseDamage: Int): Int = when (damageType) {
        DamageType.Physical -> ((baseDamage + physicalDamageAdded) * physicalDamageMultiplier).toInt()
        DamageType.Magical -> ((baseDamage + magicalDamageAdded) * magicalDamageMultiplier).toInt()
        DamageType.Absolute -> ((baseDamage + absoluteDamageAdded) * absoluteDamageMultiplier).toInt()
        else -> baseDamage
    }
}

typealias ConditionFn = (Actor, List<Actor>, List<Actor>) -> Boolean
// TargetFn already declared as targetSelectionFn
// Priority function ordering actors

typealias PriorityFn = (List<Actor>) -> List<Actor>

data class Tactic(
    val conditions: List<ConditionFn>,
    val skill: Skill,
    val targetGroup: targetSelectionFn,
    val ordering: List<PriorityFn> = emptyList()
) {
    fun getTargets(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> {
        val initialTargets = targetGroup(actor, allies, enemies)
        val ordered = ordering.fold(initialTargets) { currentTargets, ordering -> ordering(currentTargets) }
        return ordered.take(skill.maximumTargets)
    }
}

data class ResistanceBag(val physical: Int, val ice: Int, val fire: Int, val lightning: Int, val chaos: Int) {
    companion object {
        fun default() = ResistanceBag(0, 0, 0, 0, 0)
    }
}

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
    override fun setHp(value: Int) {
        hp = value.coerceIn(0, maxHp)
    }

    override fun setMana(value: Int) {
        mana = value.coerceIn(0, maxMana)
    }

    companion object {
        fun default() = StatsBag(100, 100, 100, 100, 0, 0)
    }
}

data class Actor(
    val team: Int,
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

    fun deepCopy(): Actor = Actor(
        actorClass = actorClass,
        name = name,
        statsBag = statsBag.copy(),
        tactics = tactics,
        team = team,
        stats = stats.toMutableMap(),
        temporalEffects = temporalEffects.toMutableList(),
        cooldowns = cooldowns.toMutableMap(),
        amplifiers = amplifiers,
        resistances = resistances.toMap(),
    )

    fun getResistance(damageType: DamageType): Int = resistances[damageType] ?: 0
}

data class Team(val actors: MutableList<Actor>) {
    fun aliveActors() = actors.filter { it.isAlive };
    fun deepCopy(): Team = Team(actors.map { it.deepCopy() }.toMutableList())
}

// --- Snapshots ---
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
    val cooldowns: Map<String, Int>
)

@Serializable
data class StatBuffSnapshot(val id: String, val duration: Int, val statChanges: Map<String, Int>)

@Serializable
data class ResourceTickSnapshot(val id: String, val duration: Int, val resourceChanges: Map<String, Int>)

@Serializable
data class StatOverrideSnapshot(val id: String, val duration: Int, val statOverrides: Map<String, Int>)

@Serializable
data class BattleSnapshot(val actors: List<ActorSnapshot>)

enum class DamageModifier { Critical, Blocked, Resisted }

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
    @SerialName("CharacterActivated")
    data class CharacterActivated(val actor: String, override val snapshot: BattleSnapshot) : CombatEvent()

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
    data class BuffRemoved(val target: String, val buffId: String, override val snapshot: BattleSnapshot) :
        CombatEvent()

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
data class BattleDelta(val actors: List<ActorDelta>) {
    companion object
}

fun ActorDelta.hasAnyChange(): Boolean =
    listOf(hp, maxHp, mana, maxMana, stats, statBuffs, resourceTicks, statOverrides, cooldowns).any { it != null }

@Serializable
sealed class CompactCombatEvent {
    @Serializable
    @SerialName("BattleStart")
    data class CBattleStart(val snapshot: BattleSnapshot) : CompactCombatEvent()

    @Serializable
    @SerialName("TurnStart")
    data class CTurnStart(val turn: Int, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("CharacterActivated")
    data class CCharacterActivated(val actor: String, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("SkillUsed")
    data class CSkillUsed(val actor: String, val skill: String, val targets: List<String>, val delta: BattleDelta) :
        CompactCombatEvent()

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
    data class CBuffApplied(val source: String, val target: String, val buffId: String, val delta: BattleDelta) :
        CompactCombatEvent()

    @Serializable
    @SerialName("BuffRemoved")
    data class CBuffRemoved(val target: String, val buffId: String, val delta: BattleDelta) : CompactCombatEvent()

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

// Conversion utilities (subset) for compacting CombatEvents
fun computeBattleDelta(prev: BattleSnapshot, curr: BattleSnapshot): BattleDelta {
    val prevActors = prev.actors.associateBy { it.name }
    val currActors = curr.actors.associateBy { it.name }
    val deltas = mutableListOf<ActorDelta>()
    for ((name, currActor) in currActors) {
        val prevActor = prevActors[name]
        if (prevActor == null) {
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
                hp = currActor.hp.takeIf { it != prevActor.hp },
                maxHp = currActor.maxHp.takeIf { it != prevActor.maxHp },
                mana = currActor.mana.takeIf { it != prevActor.mana },
                maxMana = currActor.maxMana.takeIf { it != prevActor.maxMana },
                stats = currActor.stats.takeIf { it != prevActor.stats },
                statBuffs = currActor.statBuffs.takeIf { it != prevActor.statBuffs },
                resourceTicks = currActor.resourceTicks.takeIf { it != prevActor.resourceTicks },
                statOverrides = currActor.statOverrides.takeIf { it != prevActor.statOverrides },
                cooldowns = currActor.cooldowns.takeIf { it != prevActor.cooldowns }
            )
            if (delta.hasAnyChange()) deltas.add(delta)
        }
    }
    return BattleDelta(deltas)
}

fun BattleDelta.Companion.fullSnapshot(snapshot: BattleSnapshot): BattleDelta = BattleDelta(snapshot.actors.map { a ->
    ActorDelta(
        name = a.name,
        hp = a.hp,
        maxHp = a.maxHp,
        mana = a.mana,
        maxMana = a.maxMana,
        stats = a.stats,
        statBuffs = a.statBuffs,
        resourceTicks = a.resourceTicks,
        statOverrides = a.statOverrides,
        cooldowns = a.cooldowns
    )
})

fun CombatEvent.compact(delta: BattleDelta): CompactCombatEvent = when (this) {
    is BattleStart -> CBattleStart(snapshot)
    is TurnStart -> CTurnStart(turn, delta)
    is CharacterActivated -> CCharacterActivated(actor, delta)
    is SkillUsed -> CSkillUsed(actor, skill, targets, delta)
    is DamageDealt -> CDamageDealt(source, target, amount, targetHp, delta)
    is Healed -> CHealed(source, target, amount, targetHp, delta)
    is BuffApplied -> CBuffApplied(source, target, buffId, delta)
    is BuffRemoved -> CBuffRemoved(target, buffId, delta)
    is ResourceDrained -> CResourceDrained(target, buffId, resource, amount, newValue, delta)
    is ResourceRegenerated -> CResourceRegenerated(target, resource, amount, newValue, delta)
    is BattleEnd -> CBattleEnd(winner, delta)
}

fun List<CombatEvent>.compact(): List<CompactCombatEvent> {
    val firstEvent = firstOrNull() ?: return emptyList()
    val compactEvents = mutableListOf<CompactCombatEvent>()
    var prevSnapshot = firstEvent.snapshot
    val firstDelta = BattleDelta.fullSnapshot(prevSnapshot)
    compactEvents.add(firstEvent.compact(firstDelta))
    for (event in drop(1)) {
        val delta = computeBattleDelta(prevSnapshot, event.snapshot)
        compactEvents.add(event.compact(delta))
        prevSnapshot = event.snapshot
    }
    return compactEvents
}

inline fun <reified T> List<T>.toJson(): String = Json.encodeToString(this)

// (BattleState.compactJson omitted; BattleState lives in server-only code currently)
