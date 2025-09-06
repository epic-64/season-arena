package playground.engine_v2

import kotlin.random.Random

// --- Core Types ---
enum class StatType {
    HP, MAX_HP, ATTACK, DEFENSE, CRIT_CHANCE, SPEED
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
    // If you need Buff/Passive/Proc/Other sources, uncomment and use:
    // data class Buff(val buff: IBuff) : ModifierSource()
    // data class Passive(val passive: IPassive) : ModifierSource()
    // data class Proc(val proc: IProc) : ModifierSource()
    // data class Other(val description: String) : ModifierSource()
}

data class StatModifier(
    val stat: StatType,
    val op: StatOp,
    val value: Number,
    val source: ModifierSource,
    val condition: ((Actor) -> Boolean)? = null
)

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

    val hp: Int get() = get(StatType.HP).toInt()
    val maxHp: Int get() = get(StatType.MAX_HP).toInt()
    val attack: Int get() = get(StatType.ATTACK).toInt()
    val defense: Int get() = get(StatType.DEFENSE).toInt()
    val critChance: Float get() = get(StatType.CRIT_CHANCE).toFloat()
    val speed: Int get() = get(StatType.SPEED).toInt()

    fun merge(other: Stats): Stats {
        val result = copy()
        for ((k, v) in other.values) {
            result.values[k] = (result.values[k]?.toDouble() ?: 0.0) + v.toDouble()
        }
        return result
    }
}

// --- Actor ---
class Actor(
    val name: String,
    val team: Int,
    val baseStats: Stats,
    val gear: List<IGear> = emptyList(),
    val buffs: List<IBuff> = emptyList(),
    val passives: List<IPassive> = emptyList(),
    var isAlive: Boolean = true
) {
    val hp: Int get() = getCurrentStats().hp
    val maxHp: Int get() = getCurrentStats().maxHp
    val allProcs: List<IProc>
        get() = gear.flatMap { it.procs } +
                buffs.flatMap { it.procs } +
                passives.flatMap { it.procs }
    fun getCurrentStats(): Stats {
        val stats = baseStats.copy()
        val allModifiers = mutableListOf<StatModifier>()
        gear.forEach { allModifiers.addAll(it.statModifiers) }
        buffs.forEach { allModifiers.addAll(it.statModifiers) }
        passives.forEach { allModifiers.addAll(it.statModifiers) }
        StatType.entries.forEach { statType ->
            var value = stats.get(statType).toDouble()
            allModifiers
                .filter { it.stat == statType && it.op is StatOp.Add && (it.condition?.invoke(this) ?: true) }
                .forEach { value += it.value.toDouble() }
            allModifiers
                .filter { it.stat == statType && it.op is StatOp.Multiply && (it.condition?.invoke(this) ?: true) }
                .forEach { value *= it.value.toDouble() }
            allModifiers
                .filter { it.stat == statType && it.op is StatOp.Override && (it.condition?.invoke(this) ?: true) }
                .lastOrNull()?.let { value = it.value.toDouble() }
            stats.set(statType, value)
        }
        return stats
    }
}

// --- Interfaces ---
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
    val procs: List<IProc>
}

interface IPassive {
    val id: String
    val statModifiers: List<StatModifier>
    val procs: List<IProc>
}

interface IDamageOverTime {
    val id: String
    val amount: Int
    val type: String
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

class ProcContext(
    val source: Actor,
    val target: Actor?,
    val event: CombatEvent
)

// --- Simulation Engine Skeleton ---
class SimulationEngine(
    val teams: List<List<Actor>>
) {
    private var turn: Int = 0
    private val log = mutableListOf<CombatEvent>()

    fun run(): List<CombatEvent> {
        // TODO: Implement main simulation loop
        return log
    }
}

// --- Lightning Strike Skill ---
class LightningStrikeSkill(
    val baseLevel: Int = 1,
    val procs: List<IProc> = emptyList()
) {
    val name: String = "Lightning Strike"
    val minDamage: Int get() = 20 + 5 * getCurrentLevel(actorForLevel)
    val maxDamage: Int get() = 40 + 10 * getCurrentLevel(actorForLevel)
    private var actorForLevel: Actor = Actor(
        name = "Dummy",
        team = 0,
        baseStats = Stats(),
        gear = emptyList(),
        buffs = emptyList(),
        passives = emptyList(),
        isAlive = true
    ) // placeholder, set before use

    fun getCurrentLevel(actor: Actor): Int {
        var level = baseLevel
        actor.gear.forEach { gear ->
            gear.statModifiers.filter { it.stat == StatType.SPEED && it.op is StatOp.Add }.forEach {
                level += it.value.toInt()
            }
        }
        return level
    }

    fun use(actor: Actor, target: Actor): List<CombatEvent> {
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
        procs.forEach { proc ->
            events.addAll(proc.activate(ProcContext(actor, target, events.last())))
        }
        return events
    }
}

// --- Example Gear/Proc ---
class UniqueBodyArmor : IGear {
    override val id: String = "unique_body_armor"
    override val statModifiers: List<StatModifier> = emptyList()
    override val procs: List<IProc> = listOf(LightningStrikeRepeatProc)
}

object LightningStrikeRepeatProc : IProc {
    override val id: String = "lightning_strike_repeat_proc"
    override val trigger: ProcTrigger = ProcTrigger.ON_SKILL_USE
    override fun activate(context: ProcContext): List<CombatEvent> {
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
        StatModifier(StatType.ATTACK, StatOp.Add, 10, ModifierSource.Gear(this)),
        StatModifier(StatType.SPEED, StatOp.Add, 1, ModifierSource.Gear(this))
    )
    override val procs: List<IProc> = listOf(LightningStrikeProc)
}

object LightningStrikeProc : IProc {
    override val id: String = "lightning_strike_proc"
    override val trigger: ProcTrigger = ProcTrigger.ON_HIT
    override fun activate(context: ProcContext): List<CombatEvent> {
        if (Random.nextFloat() < 0.5f) {
            val actor = context.source
            val target = context.target ?: return emptyList()
            val skillProcs = actor.gear.flatMap { it.procs }.filter { it.trigger == ProcTrigger.ON_SKILL_USE }
            val skill = LightningStrikeSkill(procs = skillProcs)
            val events = skill.use(actor, target)
            return events + CombatEvent.ProcActivated(actor.name, id, trigger)
        }
        return emptyList()
    }
}

// --- Demo ---
fun main() {
    val sword = SwordOfLightning()
    val armor = UniqueBodyArmor()
    val actor = Actor(
        name = "Hero",
        team = 1,
        baseStats = Stats(hp = 100, maxHp = 100, attack = 20, defense = 5, critChance = 0.1f, speed = 10),
        gear = listOf(sword, armor)
    )
    val target = Actor(
        name = "Goblin",
        team = 2,
        baseStats = Stats(hp = 50, maxHp = 50, attack = 10, defense = 2, critChance = 0.05f, speed = 8)
    )
    println("${actor.name} attacks ${target.name}!")
    actor.allProcs.filter { it.trigger == ProcTrigger.ON_HIT }.forEach { proc ->
        val events = proc.activate(ProcContext(source = actor, target = target, event = CombatEvent.Damage(actor.name, target.name, actor.getCurrentStats().attack, "physical")))
        events.forEach { println(it) }
    }
}