package io.holonaut.arena.engine

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable data class Stats(val hp: Int, val atk: Int, val spd: Int)

@Serializable
data class UnitTemplate(
    val id: String,
    val name: String,
    val stats: Stats,
    val ability: AbilitySpec
)

@Serializable
sealed interface AbilitySpec {
    val name: String

    @Serializable data class Strike(override val name: String = "Strike", val multiplier: Double = 1.0): AbilitySpec
    @Serializable data class Bleed(override val name: String = "Bleed", val damagePerTurn: Int = 4, val turns: Int = 3): AbilitySpec
    @Serializable data class Guard(override val name: String = "Guard", val shield: Int = 8): AbilitySpec
    @Serializable data class Heal(override val name: String = "Heal", val amount: Int = 10): AbilitySpec
    @Serializable data class Snipe(override val name: String = "Snipe", val bonus: Int = 6): AbilitySpec
    @Serializable data class Zap(override val name: String = "Zap", val multiplier: Double = 0.8): AbilitySpec
}

/** Runtime combatant. */
data class Fighter(
    val template: UnitTemplate,
    val team: Int,         // 1 or 2
    val slot: Int,         // 0..2
    var hp: Int,
    var shield: Int = 0,
    var bleedLeft: Int = 0,
    var bleedDamage: Int = 0
) {
    val alive get() = hp > 0
}

@Serializable data class ReplayEvent(
    val turn: Int,
    val actor: ActorRef,
    val ability: String,
    val targets: List<ActorRef>,
    val damage: Int = 0,
    val heal: Int = 0,
    val applied: List<String> = emptyList(),
    val board: BoardSnapshot
)

@Serializable data class ActorRef(val team: Int, val slot: Int, val name: String)

@Serializable data class BoardSnapshot(
    val team1: List<FighterSnapshot>,
    val team2: List<FighterSnapshot>
)
@Serializable data class FighterSnapshot(val name: String, val hp: Int, val shield: Int, val bleed: Int)

@Serializable data class SimResult(
    val winner: Int,       // 0 draw, 1 or 2
    val turns: Int,
    val events: List<ReplayEvent>
)

// Helpers to snapshot
fun snapshotBoard(fighters: List<Fighter>): BoardSnapshot {
    val t1 = fighters.filter { it.team == 1 }.sortedBy { it.slot }.map { FighterSnapshot(it.template.name, max(0, it.hp), it.shield, it.bleedLeft) }
    val t2 = fighters.filter { it.team == 2 }.sortedBy { it.slot }.map { FighterSnapshot(it.template.name, max(0, it.hp), it.shield, it.bleedLeft) }
    return BoardSnapshot(t1, t2)
}

fun refOf(f: Fighter) = ActorRef(f.team, f.slot, f.template.name)
