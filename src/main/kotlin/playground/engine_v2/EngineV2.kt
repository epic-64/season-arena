package playground.engine_v2

import kotlin.random.Random

// --- Core Interfaces ---
enum class StatType {
    Hp, MaxHp, Attack, Defense, CritChance, Speed
    // Add more as needed
}

sealed class StatOp {
    object Add : StatOp()
    object Multiply : StatOp()
    object Override : StatOp()
}

sealed class ModifierSource {
    object Base : ModifierSource()
    data class Gear(val gear: IGear) : ModifierSource()
    data class Buff(val buff: IBuff) : ModifierSource()
    data class Passive(val passive: IPassive) : ModifierSource()
    data class Proc(val proc: IProc) : ModifierSource()
    data class Other(val description: String) : ModifierSource()
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
        StatType.Hp to hp,
        StatType.MaxHp to maxHp,
        StatType.Attack to attack,
        StatType.Defense to defense,
        StatType.CritChance to critChance,
        StatType.Speed to speed
    ))

    fun get(stat: StatType): Number = values[stat] ?: 0
    fun set(stat: StatType, value: Number) { values[stat] = value }
    fun copy(): Stats = Stats(values.toMutableMap())

    // Utility accessors
    val hp: Int get() = get(StatType.Hp).toInt()
    val maxHp: Int get() = get(StatType.MaxHp).toInt()
    val attack: Int get() = get(StatType.Attack).toInt()
    val defense: Int get() = get(StatType.Defense).toInt()
    val critChance: Float get() = get(StatType.CritChance).toFloat()
    val speed: Int get() = get(StatType.Speed).toInt()

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

class Actor(
    override val name: String,
    override val team: Int,
    override val baseStats: Stats,
    override val gear: List<IGear> = emptyList(),
    override val buffs: List<IBuff> = emptyList(),
    override val passives: List<IPassive> = emptyList(),
    override val procs: List<IProc> = emptyList(),
    override val modifierStack: ModifierStack = ModifierStack(),
    override var isAlive: Boolean = true
) : IActor {
    override fun getCurrentStats(): Stats {
        val stats = baseStats.copy()
        val allModifiers = mutableListOf<StatModifier>()
        // Collect modifiers from gear, buffs, passives, and stack
        gear.forEach { allModifiers.addAll(it.statModifiers) }
        buffs.forEach { allModifiers.addAll(it.statModifiers) }
        passives.forEach { allModifiers.addAll(it.statModifiers) }
        allModifiers.addAll(modifierStack.getAll())
        // Apply modifiers by op type
        StatType.entries.forEach { statType ->
            var value = stats.get(statType).toDouble()
            // Additive
            allModifiers.filter { it.stat == statType && it.op is StatOp.Add && (it.condition?.invoke(this) ?: true) }
                .forEach { value += it.value.toDouble() }
            // Multiplicative
            allModifiers.filter { it.stat == statType && it.op is StatOp.Multiply && (it.condition?.invoke(this) ?: true) }
                .forEach { value *= it.value.toDouble() }
            // Override (last one wins)
            allModifiers.filter { it.stat == statType && it.op is StatOp.Override && (it.condition?.invoke(this) ?: true) }
                .lastOrNull()?.let { value = it.value.toDouble() }
            stats.set(statType, value)
        }
        return stats
    }
}

interface ISkill {
    val name: String
    val baseLevel: Int
    val minDamage: Int
    val maxDamage: Int
    val procs: List<IProc>
    fun getCurrentLevel(actor: IActor): Int
    fun use(actor: IActor, target: IActor): List<CombatEvent>
}

class LightningStrikeSkill(
    override val baseLevel: Int = 1,
    override val procs: List<IProc> = emptyList()
) : ISkill {
    override val name: String = "Lightning Strike"
    override val minDamage: Int get() = 20 + 5 * getCurrentLevel(actorForLevel)
    override val maxDamage: Int get() = 40 + 10 * getCurrentLevel(actorForLevel)
    private var actorForLevel: IActor = DummyActor // placeholder, set before use

    override fun getCurrentLevel(actor: IActor): Int {
        // Calculate skill level from gear, buffs, passives, etc.
        var level = baseLevel
        actor.gear.forEach { gear ->
            gear.statModifiers.filter { it.stat == StatType.Speed && it.op is StatOp.Add }.forEach {
                // Let's say SPEED stat boosts Lightning Strike level for demo purposes
                level += it.value.toInt()
            }
        }
        // You can add more sources here (buffs, passives, etc.)
        return level
    }

    override fun use(actor: IActor, target: IActor): List<CombatEvent> {
        actorForLevel = actor
        val level = getCurrentLevel(actor)
        val damage = Random.nextInt(minDamage, maxDamage + 1)
        val events = mutableListOf<CombatEvent>()
        events.add(CombatEvent.Damage(
            source = actor.name,
            target = target.name,
            amount = damage,
            type = "lightning"
        ))
        // Check for procs attached to the skill
        procs.forEach { proc ->
            events.addAll(proc.activate(ProcContext(actor, target, events.last())))
        }
        return events
    }

    private object DummyActor : IActor {
        override val name = "Dummy"
        override val team = 0
        override val baseStats = Stats()
        override val gear = emptyList<IGear>()
        override val buffs = emptyList<IBuff>()
        override val passives = emptyList<IPassive>()
        override val procs = emptyList<IProc>()
        override val modifierStack = ModifierStack()
        override val isAlive = true
        override fun getCurrentStats() = baseStats
    }
}

class UniqueBodyArmor : IGear {
    override val id: String = "unique_body_armor"
    override val statModifiers: List<StatModifier> = emptyList()
    override val procs: List<IProc> = listOf(LightningStrikeRepeatProc)
}

object LightningStrikeRepeatProc : IProc {
    override val id: String = "lightning_strike_repeat_proc"
    override val trigger: ProcTrigger = ProcTrigger.ON_SKILL_USE
    override fun activate(context: ProcContext): List<CombatEvent> {
        // Only proc if the skill used is Lightning Strike
        if (context.event is CombatEvent.Damage && context.event.type == "lightning") {
            if (Random.nextFloat() < 0.1f) { // 10% chance
                val actor = context.source
                val target = context.target ?: return emptyList()
                val skill = LightningStrikeSkill()
                val events = skill.use(actor, target)
                return events + CombatEvent.ProcActivated(actor.name, id, trigger)
            }
        }
        return emptyList()
    }
}

class SwordOfLightning : IGear {
    override val id: String = "sword_of_lightning"
    override val statModifiers: List<StatModifier> = listOf(
        StatModifier(StatType.Attack, StatOp.Add, 10, ModifierSource.Gear(this)),
        StatModifier(StatType.Speed, StatOp.Add, 1, ModifierSource.Gear(this)) // SPEED boosts Lightning Strike level
    )
    override val procs: List<IProc> = listOf(LightningStrikeProc)
}

object LightningStrikeProc : IProc {
    override val id: String = "lightning_strike_proc"
    override val trigger: ProcTrigger = ProcTrigger.ON_HIT
    override fun activate(context: ProcContext): List<CombatEvent> {
        // 50% chance to proc Lightning Strike skill
        if (Random.nextFloat() < 0.5f) {
            val actor = context.source
            val target = context.target ?: return emptyList()
            // Lightning Strike skill may have procs from gear
            val skillProcs = actor.gear.flatMap { it.procs }.filter { it.trigger == ProcTrigger.ON_SKILL_USE }
            val skill = LightningStrikeSkill(procs = skillProcs)
            val events = skill.use(actor, target)
            return events + CombatEvent.ProcActivated(actor.name, id, trigger)
        }
        return emptyList()
    }
}

// Demo in main()
fun main() {
    val sword = SwordOfLightning()
    val armor = UniqueBodyArmor()
    val actor = Actor(
        name = "Hero",
        team = 1,
        baseStats = Stats(hp = 100, maxHp = 100, attack = 20, defense = 5, critChance = 0.1f, speed = 10),
        gear = listOf(sword, armor),
        procs = sword.procs + armor.procs
    )
    val target = Actor(
        name = "Goblin",
        team = 2,
        baseStats = Stats(hp = 50, maxHp = 50, attack = 10, defense = 2, critChance = 0.05f, speed = 8)
    )
    println("${actor.name} attacks ${target.name}!")
    // Simulate ON_HIT procs
    actor.procs.filter { it.trigger == ProcTrigger.ON_HIT }.forEach { proc ->
        val events = proc.activate(ProcContext(source = actor, target = target, event = CombatEvent.Damage(actor.name, target.name, actor.getCurrentStats().attack, "physical")))
        events.forEach { println(it) }
    }
}