package game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Minimal subset of original game types needed for CompactCombatEvent demo on JS.
// Duplicated intentionally for cross-platform usage; original server JVM module still uses its own sources.

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

@Serializable
enum class DamageType { Physical, Magical, Ice, Fire, Lightning, Poison, Absolute }

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
data class BattleDelta(val actors: List<ActorDelta>)

@Serializable
data class StatBuffSnapshot(val id: String, val duration: Int, val statChanges: Map<String, Int>)
@Serializable
data class ResourceTickSnapshot(val id: String, val duration: Int, val resourceChanges: Map<String, Int>)
@Serializable
data class StatOverrideSnapshot(val id: String, val duration: Int, val statOverrides: Map<String, Int>)

@Serializable
data class ActorSnapshot(
    val actorClass: String,
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
data class BattleSnapshot(val actors: List<ActorSnapshot>)

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
    data class CSkillUsed(val actor: String, val skill: String, val targets: List<String>, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("DamageDealt")
    data class CDamageDealt(val source: String, val target: String, val amount: Int, val targetHp: Int, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("Healed")
    data class CHealed(val source: String, val target: String, val amount: Int, val targetHp: Int, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("BuffApplied")
    data class CBuffApplied(val source: String, val target: String, val buffId: String, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("BuffRemoved")
    data class CBuffRemoved(val target: String, val buffId: String, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("BuffExpired")
    data class CBuffExpired(val target: String, val buffId: String, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("ResourceDrained")
    data class CResourceDrained(val target: String, val buffId: String, val resource: String, val amount: Int, val targetResourceValue: Int, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("ResourceRegenerated")
    data class CResourceRegenerated(val target: String, val resource: String, val amount: Int, val targetResourceValue: Int, val delta: BattleDelta) : CompactCombatEvent()

    @Serializable
    @SerialName("BattleEnd")
    data class CBattleEnd(val winner: String, val delta: BattleDelta) : CompactCombatEvent()
}

