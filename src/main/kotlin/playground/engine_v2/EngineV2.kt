package playground.engine_v2

// Welcome to engine_v2: The simulation engine that doesn't make you cry.
// This is a clean slate for extensible combat simulation.
// We'll add proper abstractions for gear, buffs, DoTs, procs, and more.

// --- Core Interfaces ---
data class Stats(
    val hp: Int = 0,
    val maxHp: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val critChance: Float = 0f,
    val speed: Int = 0
    // Add more stats as needed
) {
    fun withHp(newHp: Int) = copy(hp = newHp)
    fun withMaxHp(newMaxHp: Int) = copy(maxHp = newMaxHp)
    // Add more utility methods as needed
}

interface IActor {
    val name: String
    val team: Int
    val stats: Stats
    val gear: List<IGear>
    val buffs: List<IBuff>
    val procs: List<IProc>
    val isAlive: Boolean

    // Convenience accessors
    val hp: Int get() = stats.hp
    val maxHp: Int get() = stats.maxHp
}

interface IGear {
    val id: String
    val statModifiers: Stats
    val procs: List<IProc>
}

interface IBuff {
    val id: String
    val duration: Int
    val statModifiers: Stats
    val dotEffects: List<IDamageOverTime>
}

interface IDamageOverTime {
    val id: String
    val amount: Int
    val type: String // e.g., "poison", "burn"
    val duration: Int
}

interface IProc {
    val id: String
    val trigger: ProcTrigger
    fun activate(context: ProcContext): List<CombatEvent>
}

enum class ProcTrigger {
    ON_HIT, ON_CRIT, ON_LOW_LIFE, ON_SKILL_USE, ON_BUFF_APPLY, ON_TURN_START, ON_TURN_END
}

class ProcContext(
    val source: IActor,
    val target: IActor?,
    val event: CombatEvent
)

// --- Combat Event ---
sealed class CombatEvent {
    data class Damage(val source: String, val target: String, val amount: Int, val type: String) : CombatEvent()
    data class Heal(val source: String, val target: String, val amount: Int) : CombatEvent()
    data class BuffApplied(val source: String, val target: String, val buffId: String) : CombatEvent()
    data class BuffExpired(val target: String, val buffId: String) : CombatEvent()
    data class ProcActivated(val source: String, val procId: String, val trigger: ProcTrigger) : CombatEvent()
    data class TurnStart(val turn: Int) : CombatEvent()
    data class TurnEnd(val turn: Int) : CombatEvent()
    data class BattleEnd(val winner: String) : CombatEvent()
}

// --- Simulation Engine Skeleton ---
class SimulationEngine(
    val teams: List<List<IActor>>
) {
    private var turn: Int = 0
    private val log = mutableListOf<CombatEvent>()

    fun run(): List<CombatEvent> {
        // TODO: Implement main simulation loop
        return log
    }
}

// Next steps: Implement concrete classes for Actor, Gear, Buff, DoT, Proc, and the simulation loop.
// This is just the skeleton. We'll flesh it out as we go.

fun main() {
    println("Engine v2: The simulation engine that doesn't make you cry.")
}