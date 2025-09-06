package playground.engine_v2

// Welcome to engine_v2: The simulation engine that doesn't make you cry.
// This is a clean slate for extensible combat simulation.
// We'll add proper abstractions for gear, buffs, DoTs, procs, and more.

// --- Core Interfaces ---
enum class StatType {
    HP, MAX_HP, ATTACK, DEFENSE, CRIT_CHANCE, SPEED
    // Add more as needed
}

sealed class StatOp {
    object Add : StatOp()
    object Multiply : StatOp()
    object Override : StatOp()
}

enum class ModifierSource {
    BASE, GEAR, BUFF, PASSIVE, PROC, OTHER
}

// Represents a single stat modification
data class StatModifier(
    val stat: StatType,
    val op: StatOp,
    val value: Number,
    val source: ModifierSource,
    val condition: ((IActor) -> Boolean)? = null // For conditional procs, etc.
)

// Holds a set of modifiers, e.g. from gear, buffs, passives
class ModifierStack {
    private val modifiers = mutableListOf<StatModifier>()

    fun add(mod: StatModifier) = modifiers.add(mod)
    fun addAll(mods: Collection<StatModifier>) = modifiers.addAll(mods)
    fun remove(mod: StatModifier) = modifiers.remove(mod)
    fun clear() = modifiers.clear()
    fun getAll(): List<StatModifier> = modifiers.toList()
}

// Updated Stats class to use StatType keys internally for flexibility
class Stats(private val values: MutableMap<StatType, Number> = mutableMapOf()) {
    constructor(
        hp: Int = 0,
        maxHp: Int = 0,
        attack: Int = 0,
        defense: Int = 0,
        critChance: Float = 0f,
        speed: Int = 0
    ) : this(mutableMapOf(
        StatType.HP to hp,
        StatType.MAX_HP to maxHp,
        StatType.ATTACK to attack,
        StatType.DEFENSE to defense,
        StatType.CRIT_CHANCE to critChance,
        StatType.SPEED to speed
    ))

    fun get(stat: StatType): Number = values[stat] ?: 0
    fun set(stat: StatType, value: Number) { values[stat] = value }
    fun copy(): Stats = Stats(values.toMutableMap())

    // Utility accessors
    val hp: Int get() = get(StatType.HP).toInt()
    val maxHp: Int get() = get(StatType.MAX_HP).toInt()
    val attack: Int get() = get(StatType.ATTACK).toInt()
    val defense: Int get() = get(StatType.DEFENSE).toInt()
    val critChance: Float get() = get(StatType.CRIT_CHANCE).toFloat()
    val speed: Int get() = get(StatType.SPEED).toInt()

    // Merges another Stats into this one (additive)
    fun merge(other: Stats): Stats {
        val result = copy()
        for ((k, v) in other.values) {
            result.values[k] = (result.values[k]?.toDouble() ?: 0.0) + v.toDouble()
        }
        return result
    }
}

interface IActor {
    val name: String
    val team: Int
    val baseStats: Stats
    val gear: List<IGear>
    val buffs: List<IBuff>
    val passives: List<IPassive>
    val procs: List<IProc>
    val modifierStack: ModifierStack
    val isAlive: Boolean

    // Calculates current stats by applying all modifiers
    fun getCurrentStats(): Stats

    // Convenience accessors
    val hp: Int get() = getCurrentStats().hp
    val maxHp: Int get() = getCurrentStats().maxHp
}

interface IGear {
    val id: String
    val statModifiers: List<StatModifier>
    val procs: List<IProc>
}

interface IBuff {
    val id: String
    val duration: Int
    val statModifiers: List<StatModifier>
    val dotEffects: List<IDamageOverTime>
}

interface IPassive {
    val id: String
    val statModifiers: List<StatModifier>
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