package playground

import kotlin.math.max
import kotlin.math.min
import kotlin.time.measureTime

// --- Buff (Unified) ---
sealed class Buff {
    abstract val id: String
    abstract val duration: Int

    data class StatBuff(
        override val id: String,
        override val duration: Int,
        val statChanges: Map<String, Int> = emptyMap()
    ) : Buff()
    data class ResourceTick(
        override val id: String,
        override val duration: Int,
        val resourceChanges: Map<String, Int> = emptyMap()
    ) : Buff()
}

// --- Skill Effects ---
sealed class SkillEffectType {
    object Damage : SkillEffectType()
    object Heal : SkillEffectType()
    object StatBuff : SkillEffectType()
    object ResourceTick : SkillEffectType()
    // Add more types as needed
}

data class SkillEffect(
    val type: SkillEffectType,
    val power: Int = 0,
    val targetRule: (Actor, List<Actor>, List<Actor>) -> List<Actor>,
    val statBuff: Buff.StatBuff? = null, // For stat buff effects
    val resourceTick: Buff.ResourceTick? = null // For resource tick effects
)

// --- Skills ---
data class Skill(
    val name: String,
    val effects: List<SkillEffect>,
    val activationRule: (Actor, List<Actor>, List<Actor>) -> Boolean = { _, _, _ -> true }, // Should use this skill?
    val cooldown: Int // cooldown in turns
)

// --- Actor ---
data class Actor(
    val name: String,
    var hp: Int,
    val maxHp: Int,
    val skills: List<Skill>,
    val team: Int, // 0 or 1
    val stats: MutableMap<String, Int> = mutableMapOf(),
    val buffs: MutableList<Buff> = mutableListOf(),
    val cooldowns: MutableMap<Skill, Int> = mutableMapOf() // skill -> turns left
) {
    val isAlive: Boolean get() = hp > 0

    fun deepCopy(): Actor {
        return Actor(
            name = name,
            hp = hp,
            maxHp = maxHp,
            skills = skills, // Skills are immutable
            team = team,
            stats = stats.toMutableMap(),
            buffs = buffs.map {
                when (it) {
                    is Buff.StatBuff -> it.copy()
                    is Buff.ResourceTick -> it.copy()
                }
            }.toMutableList(),
            cooldowns = cooldowns.toMutableMap()
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
data class ActorSnapshot(
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val team: Int,
    val stats: Map<String, Int>,
    val statBuffs: List<StatBuffSnapshot>,
    val resourceTicks: List<ResourceTickSnapshot>,
    val cooldowns: Map<String, Int> // skill name -> cooldown
)

data class StatBuffSnapshot(
    val id: String,
    val duration: Int,
    val statChanges: Map<String, Int>
)

data class ResourceTickSnapshot(
    val id: String,
    val duration: Int,
    val resourceChanges: Map<String, Int>
)

data class BattleSnapshot(
    val actors: List<ActorSnapshot>
)

fun snapshotActors(teams: List<Team>): BattleSnapshot {
    return BattleSnapshot(
        actors = teams.flatMap { team ->
            team.actors.map { actor ->
                ActorSnapshot(
                    name = actor.name,
                    hp = actor.hp,
                    maxHp = actor.maxHp,
                    team = actor.team,
                    stats = actor.stats.toMap(),
                    statBuffs = actor.buffs.filterIsInstance<Buff.StatBuff>().map { buff ->
                        StatBuffSnapshot(
                            id = buff.id,
                            duration = buff.duration,
                            statChanges = buff.statChanges
                        )
                    },
                    resourceTicks = actor.buffs.filterIsInstance<Buff.ResourceTick>().map { tick ->
                        ResourceTickSnapshot(
                            id = tick.id,
                            duration = tick.duration,
                            resourceChanges = tick.resourceChanges
                        )
                    },
                    cooldowns = actor.skills.associate { it.name to (actor.cooldowns[it] ?: 0) }
                )
            }
        }
    )
}

// --- Combat Event Data Structure ---
sealed class CombatEvent {
    data class TurnStart(val turn: Int, val snapshot: BattleSnapshot) : CombatEvent()
    data class SkillUsed(val actor: String, val skill: String, val targets: List<String>, val snapshot: BattleSnapshot) : CombatEvent()
    data class DamageDealt(val source: String, val target: String, val amount: Int, val targetHp: Int, val snapshot: BattleSnapshot) : CombatEvent()
    data class Healed(val source: String, val target: String, val amount: Int, val targetHp: Int, val snapshot: BattleSnapshot) : CombatEvent()
    data class BuffApplied(val source: String, val target: String, val buffId: String, val snapshot: BattleSnapshot) : CombatEvent()
    data class BuffExpired(val target: String, val buffId: String, val snapshot: BattleSnapshot) : CombatEvent()
    data class ResourceDrained(val target: String, val buffId: String, val resource: String, val amount: Int, val targetResourceValue: Int, val snapshot: BattleSnapshot) : CombatEvent()
    data class BattleEnd(val winner: String, val snapshot: BattleSnapshot) : CombatEvent()
}

// --- BattleSimulation ---
class BattleSimulation(
    val teamA: Team,
    val teamB: Team
) {
    private var turn: Int = 0

    fun run(): List<CombatEvent> {
        val maxTurns = 100
        val log = mutableListOf<CombatEvent>()
        log.add(CombatEvent.TurnStart(turn, snapshotActors(listOf(teamA, teamB))))

        while (teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isNotEmpty() && turn < maxTurns) {
            turn++
            log.add(CombatEvent.TurnStart(turn, snapshotActors(listOf(teamA, teamB))))
            val allActors = (teamA.aliveActors() + teamB.aliveActors()).shuffled()
            for (actor in allActors) {
                if (!actor.isAlive) continue
                val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
                val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
                val skill = pickSkill(actor, allies, enemies)
                if (skill != null) {
                    // Collect all unique target names for this skill
                    val targetNames = skill.effects
                        .flatMap { it.targetRule(actor, allies, enemies) }
                        .map { it.name }
                        .distinct()
                    log.add(CombatEvent.SkillUsed(actor.name, skill.name, targetNames, snapshotActors(listOf(teamA, teamB))))
                    applySkill(actor, skill, allies, enemies, log)
                }
                // else: actor skips turn
            }
            processBuffs(teamA, log)
            processBuffs(teamB, log)
        }
        val winner = when {
            teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isEmpty() -> "Team A"
            teamB.aliveActors().isNotEmpty() && teamA.aliveActors().isEmpty() -> "Team B"
            else -> "Draw (turn limit reached)"
        }
        log.add(CombatEvent.BattleEnd(winner, snapshotActors(listOf(teamA, teamB))))
        return log
    }

    private fun pickSkill(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Skill? {
        // Only pick skills that are not on cooldown
        val availableSkills = actor.skills.filter { (actor.cooldowns[it] ?: 0) <= 0 }
        return availableSkills.firstOrNull { it.activationRule(actor, allies, enemies) } ?: availableSkills.firstOrNull()
    }

    private fun applySkill(actor: Actor, skill: Skill, allies: List<Actor>, enemies: List<Actor>, log: MutableList<CombatEvent>) {
        for (effect in skill.effects) {
            val targets = effect.targetRule(actor, allies, enemies)
            if (targets.isEmpty()) continue // Skip if no valid targets
            when (effect.type) {
                SkillEffectType.Damage -> {
                    for (target in targets) {
                        val rawDmg = max(1, effect.power + (actor.stats["atk"] ?: 0))
                        val protection = target.stats["protection"]?.coerceIn(0, 100) ?: 0
                        // Protection is a percentage: 10 = 10% reduced damage
                        val finalDmg = max(1, (rawDmg * (1 - protection / 100.0)).toInt())
                        target.hp = max(0, target.hp - finalDmg)
                        log.add(CombatEvent.DamageDealt(actor.name, target.name, finalDmg, target.hp, snapshotActors(listOf(teamA, teamB))))
                    }
                }
                SkillEffectType.Heal -> {
                    for (target in targets) {
                        val heal = max(1, effect.power + (actor.stats["matk"] ?: 0))
                        target.hp = min(target.maxHp, target.hp + heal)
                        log.add(CombatEvent.Healed(actor.name, target.name, heal, target.hp, snapshotActors(listOf(teamA, teamB))))
                    }
                }
                SkillEffectType.StatBuff -> {
                    for (target in targets) {
                        effect.statBuff?.let { target.buffs.add(it.copy()) }
                        effect.statBuff?.let { log.add(CombatEvent.BuffApplied(actor.name, target.name, it.id, snapshotActors(listOf(teamA, teamB)))) }
                    }
                }
                SkillEffectType.ResourceTick -> {
                    for (target in targets) {
                        effect.resourceTick?.let { target.buffs.add(it.copy()) }
                        effect.resourceTick?.let { log.add(CombatEvent.BuffApplied(actor.name, target.name, it.id, snapshotActors(listOf(teamA, teamB)))) }
                    }
                }
            }
        }
        // Apply cooldown
        actor.cooldowns[skill] = skill.cooldown
    }

    private fun processBuffs(team: Team, log: MutableList<CombatEvent>) {
        for (actor in team.actors) {
            val expiredStatBuffs = mutableListOf<Buff.StatBuff>()
            val expiredResourceTicks = mutableListOf<Buff.ResourceTick>()
            // --- StatBuffs: Only apply statChanges on buff application, remove on expiration ---
            // Track buffs that are newly applied this turn
            val previousStatBuffs = actor.stats.toMap()
            val activeStatBuffs = actor.buffs.filterIsInstance<Buff.StatBuff>()
            val statBuffTotals = mutableMapOf<String, Int>()
            for (buff in activeStatBuffs) {
                for ((stat, change) in buff.statChanges) {
                    statBuffTotals[stat] = (statBuffTotals[stat] ?: 0) + change
                }
            }
            // Set stats to base + total from active buffs
            for ((stat, value) in statBuffTotals) {
                actor.stats[stat] = value
            }
            // Remove stats for buffs that have expired
            val expiredBuffs = actor.buffs.filterIsInstance<Buff.StatBuff>().filter { it.duration <= 0 }
            for (buff in expiredBuffs) {
                for ((stat, _) in buff.statChanges) {
                    // Remove stat if no other active buff provides it
                    if (activeStatBuffs.none { it.statChanges.containsKey(stat) }) {
                        actor.stats.remove(stat)
                    }
                }
            }
            // --- ResourceTicks: process as before ---
            for (buff in actor.buffs.filterIsInstance<Buff.ResourceTick>()) {
                if (buff.resourceChanges.isNotEmpty()) {
                    for ((resource, amount) in buff.resourceChanges) {
                        when (resource) {
                            "hp" -> {
                                actor.hp = if (amount > 0) {
                                    min(actor.maxHp, actor.hp + amount)
                                } else {
                                    max(0, actor.hp + amount)
                                }
                                log.add(CombatEvent.ResourceDrained(actor.name, buff.id, resource, amount, actor.hp, snapshotActors(listOf(teamA, teamB))))
                            }
                        }
                    }
                }
            }
            actor.buffs.replaceAll { when (it) {
                is Buff.StatBuff -> it.copy(duration = it.duration - 1)
                is Buff.ResourceTick -> it.copy(duration = it.duration - 1)
            } }
            expiredStatBuffs.addAll(actor.buffs.filterIsInstance<Buff.StatBuff>().filter { it.duration <= 0 })
            expiredResourceTicks.addAll(actor.buffs.filterIsInstance<Buff.ResourceTick>().filter { it.duration <= 0 })
            actor.buffs.removeAll { it is Buff.StatBuff && it.duration <= 0 }
            actor.buffs.removeAll { it is Buff.ResourceTick && it.duration <= 0 }
            for (buff in expiredStatBuffs) {
                log.add(CombatEvent.BuffExpired(actor.name, buff.id, snapshotActors(listOf(teamA, teamB))))
            }
            for (tick in expiredResourceTicks) {
                log.add(CombatEvent.BuffExpired(actor.name, tick.id, snapshotActors(listOf(teamA, teamB))))
            }
            // Decrease cooldowns and clamp to zero
            actor.cooldowns.replaceAll { _, v -> max(0, v - 1) }
        }
    }
}

// --- BattleLogPrinter ---
object BattleLogPrinter {
    fun run(log: List<CombatEvent>) {
        for (event in log) {
            when (event) {
                is CombatEvent.TurnStart -> println("--- Turn ${event.turn} ---")
                is CombatEvent.SkillUsed -> println("${event.actor} uses ${event.skill} on ${event.targets.joinToString()}")
                is CombatEvent.DamageDealt -> println("${event.target} takes ${event.amount} damage! (HP: ${event.targetHp})")
                is CombatEvent.Healed -> println("${event.target} heals ${event.amount} HP! (HP: ${event.targetHp})")
                is CombatEvent.BuffApplied -> println("${event.target} receives buff ${event.buffId}")
                is CombatEvent.ResourceDrained -> println("${event.target} gains ${event.amount} ${event.resource} from ${event.buffId}! (${event.resource}: ${event.targetResourceValue})")
                is CombatEvent.BuffExpired -> println("${event.target}'s buff ${event.buffId} expired.")
                is CombatEvent.BattleEnd -> println("Battle Over! Winner: ${event.winner}")
            }
            // Print actor snapshot after each event
            val snapshot = when (event) {
                is CombatEvent.TurnStart -> event.snapshot
                is CombatEvent.BattleEnd -> event.snapshot
                else -> null
            }

            if (snapshot == null)
                continue

            println("Actors:")
            for (actor in snapshot.actors) {
                println("  ${actor.name} (Team ${actor.team}): HP=${actor.hp}/${actor.maxHp}, Stats=${actor.stats}, Buffs=[${actor.statBuffs.joinToString { b -> "${b.id}(dur=${b.duration})" }}], Ticks=[${actor.resourceTicks.joinToString { t -> "${t.id}(dur=${t.duration})" }}]")
            }
        }
    }
}

val basicAttack = Skill(
    name = "Strike",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 20,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 1
)

val doubleStrike = Skill(
    name = "Double Strike",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        )
    ),
    activationRule = { actor, _, enemies -> enemies.isNotEmpty() },
    cooldown = 2
)

val whirlwind = Skill(
    name = "Whirlwind",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies -> enemies } // All enemies
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 2
)

val fireball = Skill(
    name = "Fireball",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 25,
            targetRule = { _, _, enemies -> enemies }
        ),
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { _, _, enemies -> enemies },
            resourceTick = Buff.ResourceTick(id = "Burn", duration = 2, resourceChanges = mapOf("hp" to -10))
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 4
)

val explode = Skill(
    name = "Explode",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 50,
            targetRule = { _, _, enemies -> enemies }
        ),
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 50,
            targetRule = { actor, _, _ -> listOf(actor) } // Self-target
        )
    ),
    activationRule = { actor, _, _ -> actor.hp < actor.maxHp / 4 },
    cooldown = 6
)

val spark = Skill(
    name = "Spark",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 10,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) enemies.shuffled().take(2) else emptyList()
            }
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 1
)

val hotBuff = Skill(
    name = "Regeneration",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { actor, _, _ -> listOf(actor) },
            resourceTick = Buff.ResourceTick(id = "Regen", duration = 3, resourceChanges = mapOf("hp" to 10)) // Regeneration should heal
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            power = 0,
            targetRule = { actor, _, _ -> listOf(actor) },
            statBuff = Buff.StatBuff(id = "Protection", duration = 3, statChanges = mapOf("protection" to 10))
        ),
    ),
    activationRule = { actor, _, _ -> actor.buffs.none { it.id == "Regen" } },
    cooldown = 3
)

val flashHeal = Skill(
    name = "Flash Heal",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Heal,
            power = 25,
            targetRule = { _, allies, _ ->
                val target = allies.minByOrNull { it.hp }
                if (target != null) listOf(target) else emptyList()
            }
        )
    ),
    activationRule = { _, allies, _ ->
        val target = allies.minByOrNull { it.hp }
        target != null && target.hp < target.maxHp / 2
    },
    cooldown = 2
)

val groupHeal = Skill(
    name = "Group Heal",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Heal,
            power = 20,
            targetRule = { _, allies, _ -> allies }
        ),
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { _, allies, _ -> allies },
            resourceTick = Buff.ResourceTick(id = "Regen", duration = 2, resourceChanges = mapOf("hp" to 5)) // Should heal, not damage
        )
    ),
    activationRule = { _, allies, _ ->
        allies.count { it.hp < it.maxHp * 0.7 } >= 2
    },
    cooldown = 6
)

val poisonStrike = Skill(
    name = "Poison Strike",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            },
            resourceTick = Buff.ResourceTick(id = "Poison", duration = 4, resourceChanges = mapOf("hp" to -5)) // Poison should damage
        )
    ),
    cooldown = 2
)

// --- Example Usage ---
fun main() {
    val originalActorA1 = Actor(
        name = "Hero Fighter Jason",
        hp = 100,
        maxHp = 100,
        skills = listOf(whirlwind, doubleStrike, basicAttack),
        team = 0
    )
    val originalActorA2 = Actor(
        name = "Hero Mage Alice",
        hp = 100,
        maxHp = 100,
        skills = listOf(explode, fireball, spark, basicAttack),
        team = 0
    )
    val originalActorA3 = Actor(
        name = "Hero Cleric Mary",
        hp = 100,
        maxHp = 100,
        skills = listOf(groupHeal, flashHeal, basicAttack),
        team = 0
    )
    val originalActorB = Actor(
        name = "Villain",
        hp = 400,
        maxHp = 400,
        skills = listOf(fireball, hotBuff, poisonStrike),
        team = 1
    )
    val originalTeamA = Team(mutableListOf(originalActorA1, originalActorA2, originalActorA3))
    val originalTeamB = Team(mutableListOf(originalActorB))

    repeat(100) { i ->
        val teamA = originalTeamA.deepCopy()
        val teamB = originalTeamB.deepCopy()
        val log: List<CombatEvent>
        val milliSecondsSimulation = measureTime {
            log = BattleSimulation(teamA, teamB).run()
        }
        val turns = log.count { it is CombatEvent.TurnStart } - 1 // Subtract initial state
        val milliSecondsPrinting = measureTime {
            // BattleLogPrinter.run(log)
        }
        println("Run #$i: Simulation took $milliSecondsSimulation, printing took $milliSecondsPrinting, turns: $turns")
    }
}
