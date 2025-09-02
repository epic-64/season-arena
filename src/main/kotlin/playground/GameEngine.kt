package playground

import kotlin.math.max
import kotlin.math.min
import kotlin.time.measureTime

// --- Buffs & Debuffs ---
data class Buff(
    val id: String,
    val duration: Int,
    val statChanges: Map<String, Int> = emptyMap(), // e.g. {"atk": +10}
    val dot: Int = 0 // Damage over time per turn
)

// --- Skill Effects ---
sealed class SkillEffectType {
    object Damage : SkillEffectType()
    object Heal : SkillEffectType()
    object Buff : SkillEffectType()
    object Debuff : SkillEffectType()
    // Add more types as needed
}

data class SkillEffect(
    val type: SkillEffectType,
    val power: Int = 0,
    val targetRule: (Actor, List<Actor>, List<Actor>) -> List<Actor>,
    val buff: Buff? = null // For buff/debuff effects
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
}

// --- Team ---
data class Team(val actors: MutableList<Actor>) {
    fun aliveActors() = actors.filter { it.isAlive }
}

// --- Actor Snapshot Data Structure ---
data class ActorSnapshot(
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val team: Int,
    val stats: Map<String, Int>,
    val buffs: List<BuffSnapshot>,
    val cooldowns: Map<String, Int> // skill name -> cooldown
)

data class BuffSnapshot(
    val id: String,
    val duration: Int,
    val statChanges: Map<String, Int>,
    val dot: Int
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
                    buffs = actor.buffs.map { buff ->
                        BuffSnapshot(
                            id = buff.id,
                            duration = buff.duration,
                            statChanges = buff.statChanges,
                            dot = buff.dot
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
    data class DotApplied(val target: String, val buffId: String, val amount: Int, val targetHp: Int, val snapshot: BattleSnapshot) : CombatEvent()
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
                        val dmg = max(1, effect.power + (actor.stats["atk"] ?: 0))
                        target.hp = max(0, target.hp - dmg)
                        log.add(CombatEvent.DamageDealt(actor.name, target.name, dmg, target.hp, snapshotActors(listOf(teamA, teamB))))
                    }
                }
                SkillEffectType.Heal -> {
                    for (target in targets) {
                        val heal = max(1, effect.power + (actor.stats["matk"] ?: 0))
                        target.hp = min(target.maxHp, target.hp + heal)
                        log.add(CombatEvent.Healed(actor.name, target.name, heal, target.hp, snapshotActors(listOf(teamA, teamB))))
                    }
                }
                SkillEffectType.Buff, SkillEffectType.Debuff -> {
                    for (target in targets) {
                        effect.buff?.let { target.buffs.add(it.copy()) }
                        effect.buff?.let { log.add(CombatEvent.BuffApplied(actor.name, target.name, it.id, snapshotActors(listOf(teamA, teamB)))) }
                    }
                }
            }
        }
        // Apply cooldown
        actor.cooldowns[skill] = skill.cooldown
    }

    private fun processBuffs(team: Team, log: MutableList<CombatEvent>) {
        for (actor in team.actors) {
            val expired = mutableListOf<Buff>()
            for (buff in actor.buffs) {
                if (buff.dot != 0) {
                    actor.hp = if (buff.dot < 0) {
                        // Healing over time: clamp to maxHp
                        min(actor.maxHp, actor.hp - buff.dot)
                    } else {
                        // Damage over time
                        max(0, actor.hp - buff.dot)
                    }
                    log.add(CombatEvent.DotApplied(actor.name, buff.id, buff.dot, actor.hp, snapshotActors(listOf(teamA, teamB))))
                }
            }
            actor.buffs.replaceAll { it.copy(duration = it.duration - 1) }
            expired.addAll(actor.buffs.filter { it.duration <= 0 })
            actor.buffs.removeAll { it.duration <= 0 }
            for (buff in expired) {
                log.add(CombatEvent.BuffExpired(actor.name, buff.id, snapshotActors(listOf(teamA, teamB))))
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
                is CombatEvent.BuffApplied -> println("${event.target} receives buff/debuff ${event.buffId}")
                is CombatEvent.DotApplied -> println("${event.target} takes ${event.amount} DoT from ${event.buffId}! (HP: ${event.targetHp})")
                is CombatEvent.BuffExpired -> println("${event.target}'s buff/debuff ${event.buffId} expired.")
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
                println("  ${actor.name} (Team ${actor.team}): HP=${actor.hp}/${actor.maxHp}, Stats=${actor.stats}, Buffs=[${actor.buffs.joinToString { b -> "${b.id}(dur=${b.duration},dot=${b.dot},stats=${b.statChanges})" }}]")
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
            type = SkillEffectType.Debuff,
            targetRule = { _, _, enemies -> enemies },
            buff = Buff(id = "Burn", duration = 2, dot = 10)
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
            type = SkillEffectType.Buff,
            power = 0,
            targetRule = { actor, _, _ -> listOf(actor) },
            buff = Buff(id = "Regen", duration = 3, dot = -10)
        ),
        SkillEffect(
            type = SkillEffectType.Buff,
            power = 0,
            targetRule = { actor, _, _ -> listOf(actor) },
            buff = Buff(id = "Resist", duration = 3, statChanges = mapOf("def" to 10))
        )
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
            type = SkillEffectType.Buff,
            targetRule = { _, allies, _ -> allies },
            buff = Buff(id = "Regen", duration = 2, dot = -5)
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
            type = SkillEffectType.Debuff,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            },
            buff = Buff(id = "Poison", duration = 4, dot = 5)
        )
    ),
    cooldown = 2
)

// --- Example Usage ---
fun main() {
    val actorA1 = Actor(
        name = "Hero Fighter Jason",
        hp = 100,
        maxHp = 100,
        skills = listOf(whirlwind, doubleStrike, basicAttack),
        team = 0
    )

    val actorA2 = Actor(
        name = "Hero Mage Alice",
        hp = 100,
        maxHp = 100,
        skills = listOf(explode, fireball, spark, basicAttack),
        team = 0
    )

    val actorA3 = Actor(
        name = "Hero Cleric Mary",
        hp = 100,
        maxHp = 100,
        skills = listOf(groupHeal, flashHeal, basicAttack),
        team = 0
    )


    val actorB = Actor(
        name = "Villain",
        hp = 400,
        maxHp = 400,
        skills = listOf(fireball, hotBuff, poisonStrike),
        team = 1
    )

    val teamA = Team(mutableListOf(actorA1, actorA2, actorA3))
    val teamB = Team(mutableListOf(actorB))

    val log: List<CombatEvent>

    val milliSecondsSimulation = measureTime {
        log = BattleSimulation(teamA, teamB).run()
    }

    val milliSecondsPrinting = measureTime {
        BattleLogPrinter.run(log)
    }
    println("Simulation took $milliSecondsSimulation ms, printing took $milliSecondsPrinting ms")
}
