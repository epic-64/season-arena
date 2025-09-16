package playground.engine_v1

import kotlin.collections.iterator
import kotlin.math.max
import kotlin.math.min
import kotlin.time.measureTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

// --- Actor ---
data class Actor(
    val actorClass: ActorClass,
    val name: String,
    private var hp: Int,
    val maxHp: Int,
    val skills: List<Skill>,
    val team: Int, // 0 or 1
    val stats: MutableMap<String, Int> = mutableMapOf(),
    val buffs: MutableList<Buff> = mutableListOf(),
    val cooldowns: MutableMap<Skill, Int> = mutableMapOf() // skill -> turns left
) {
    val isAlive: Boolean get() = hp > 0

    fun getHp(): Int = hp

    fun setHp(value: Int) {
        hp = value.coerceIn(0, maxHp)

        if (!isAlive) {
            buffs.clear()
            cooldowns.clear()
        }
    }

    fun deepCopy(): Actor {
        return Actor(
            actorClass = actorClass,
            name = name,
            hp = hp,
            maxHp = maxHp,
            skills = skills, // Skills are immutable
            team = team,
            stats = stats.toMutableMap(),
            buffs = buffs.toMutableList(),
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
@Serializable
data class ActorSnapshot(
    val actorClass: ActorClass,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val team: Int,
    val stats: Map<String, Int>,
    val statBuffs: List<StatBuffSnapshot>,
    val resourceTicks: List<ResourceTickSnapshot>,
    val cooldowns: Map<String, Int>
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
data class BattleSnapshot(
    val actors: List<ActorSnapshot>
)

fun snapshotActors(teams: List<Team>): BattleSnapshot {
    return BattleSnapshot(
        actors = teams.flatMap { team ->
            team.actors.map { actor ->
                ActorSnapshot(
                    actorClass = actor.actorClass,
                    name = actor.name,
                    hp = actor.getHp(),
                    maxHp = actor.maxHp,
                    team = actor.team,
                    stats = actor.stats.toMap(),
                    statBuffs = actor.buffs.filterIsInstance<Buff.StatBuff>()
                        .groupBy { it.id }
                        .map { (id, buffs) ->
                            // Summarize statChanges by summing values for each stat
                            val mergedStatChanges = mutableMapOf<String, Int>()
                            buffs.forEach { buff ->
                                buff.statChanges.forEach { (stat, value) ->
                                    mergedStatChanges[stat] = (mergedStatChanges[stat] ?: 0) + value
                                }
                            }
                            StatBuffSnapshot(
                                id = id,
                                duration = buffs.maxOf { it.duration }, // longest duration
                                statChanges = mergedStatChanges
                            )
                        },
                    resourceTicks = actor.buffs.filterIsInstance<Buff.ResourceTick>()
                        .groupBy { it.id }
                        .map { (id, ticks) ->
                            // Summarize resourceChanges by summing values for each resource
                            val mergedResourceChanges = mutableMapOf<String, Int>()
                            ticks.forEach { tick ->
                                tick.resourceChanges.forEach { (resource, value) ->
                                    mergedResourceChanges[resource] = (mergedResourceChanges[resource] ?: 0) + value
                                }
                            }
                            ResourceTickSnapshot(
                                id = id,
                                duration = ticks.maxOf { it.duration }, // longest duration
                                resourceChanges = mergedResourceChanges
                            )
                        },
                    cooldowns = actor.skills.associate { it.name to (actor.cooldowns[it] ?: 0) }
                )
            }
        }
    )
}

// --- Combat Event Data Structure ---
@Serializable
sealed class CombatEvent {
    @Serializable
    data class TurnStart(val turn: Int, val snapshot: BattleSnapshot) : CombatEvent()

    @Serializable
    data class SkillUsed(
        val actor: String,
        val skill: String,
        val targets: List<String>,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    data class DamageDealt(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    data class Healed(
        val source: String,
        val target: String,
        val amount: Int,
        val targetHp: Int,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    data class BuffApplied(
        val source: String,
        val target: String,
        val buffId: String,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    data class BuffExpired(val target: String, val buffId: String, val snapshot: BattleSnapshot) : CombatEvent()

    @Serializable
    data class ResourceDrained(
        val target: String,
        val buffId: String,
        val resource: String,
        val amount: Int,
        val targetResourceValue: Int,
        val snapshot: BattleSnapshot
    ) : CombatEvent()

    @Serializable
    data class BattleEnd(val winner: String, val snapshot: BattleSnapshot) : CombatEvent()
}

fun combatEventsToJson(events: List<CombatEvent>): String {
    return Json.encodeToString(events)
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
            val allActors = teamA.aliveActors() + teamB.aliveActors()

            for (actor in allActors) {
                val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
                val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
                val skill = pickSkill(actor, allies, enemies)
                if (skill != null) {
                    // Collect all target names for this skill
                    val targetNames = skill.effects
                        .flatMap { it.targetRule(actor, allies, enemies) }
                        .map { it.name }
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
                        val amplifiedDmg = (rawDmg * (1 + (actor.stats["amplify"] ?: 0) / 100.0)).toInt()
                        val protection = target.stats["protection"]?.coerceIn(0, 100) ?: 0
                        // Protection is a percentage: 10 = 10% reduced damage
                        val finalDmg = max(1, (amplifiedDmg * (1 - protection / 100.0)).toInt())
                        target.setHp(max(0, target.getHp() - finalDmg))
                        log.add(CombatEvent.DamageDealt(actor.name, target.name, finalDmg, target.getHp(), snapshotActors(listOf(teamA, teamB))))
                    }
                }
                SkillEffectType.Heal -> {
                    for (target in targets) {
                        val heal = max(1, effect.power + (actor.stats["matk"] ?: 0))
                        target.setHp(min(target.maxHp, target.getHp() + heal))
                        log.add(CombatEvent.Healed(actor.name, target.name, heal, target.getHp(), snapshotActors(listOf(teamA, teamB))))
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
            // --- ResourceTicks: aggregate by id ---
            val resourceTickGroups = actor.buffs.filterIsInstance<Buff.ResourceTick>().groupBy { it.id }
            for ((id, ticks) in resourceTickGroups) {
                if (ticks.isNotEmpty()) {
                    // Aggregate resource changes
                    val totalResourceChanges = ticks.flatMap { it.resourceChanges.entries }
                        .groupBy { it.key }
                        .mapValues { (_, v) -> v.sumOf { it.value } }
                    for ((resource, amount) in totalResourceChanges) {
                        when (resource) {
                            "hp" -> {
                                val newHp = if (amount > 0) {
                                    min(actor.maxHp, actor.getHp() + amount)
                                } else {
                                    max(0, actor.getHp() + amount)
                                }
                                actor.setHp(newHp)
                                log.add(CombatEvent.ResourceDrained(actor.name, id, resource, amount, actor.getHp(), snapshotActors(listOf(teamA, teamB))))
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

fun printBattleEvents(events: List<CombatEvent>) {
    for (event in events) {
        printBattleEvent(event)
    }
}

fun printBattleEvent(event: CombatEvent) {
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
        return

    println("Actors:")
    for (actor in snapshot.actors) {
        println("  ${actor.name} (Team ${actor.team}): HP=${actor.hp}/${actor.maxHp}, Stats=${actor.stats}, Buffs=[${actor.statBuffs.joinToString { b -> "${b.id}(dur=${b.duration})" }}], Ticks=[${actor.resourceTicks.joinToString { t -> "${t.id}(dur=${t.duration})" }}]")
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

val takeAim = Skill(
    name = "Take Aim",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.StatBuff,
            power = 0,
            targetRule = { actor, _, _ -> listOf(actor) },
            statBuff = Buff.StatBuff(id = "Amplify", duration = 1, statChanges = mapOf("amplify" to 200))
        )
    ),
    cooldown = 3
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

val spark = Skill(
    name = "Spark",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 10,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) enemies.shuffled().take(2) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) enemies.shuffled().take(2) else emptyList()
            },
            statBuff = Buff.StatBuff(id = "Shock", duration = 2, statChanges = mapOf("def" to -5)),
        )
    ),
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
                val target = allies.minByOrNull { it.getHp() }
                if (target != null) listOf(target) else emptyList()
            }
        )
    ),
    activationRule = { _, allies, _ ->
        val target = allies.minByOrNull { it.getHp() }
        target != null && target.getHp() < target.maxHp / 2
    },
    cooldown = 2
)

val iceShot = Skill(
    name = "Ice Shot",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 25,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            },
            statBuff = Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -10))
        )
    ),
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
        allies.count { it.getHp() < it.maxHp * 0.7 } >= 2
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

val blackHole = Skill(
    name = "Black Hole",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 40,
            targetRule = { _, _, enemies -> enemies }
        ),
    ),
    cooldown = 5
)

val iceLance = Skill(
    name = "Ice Lance",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 30,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            },
            statBuff = Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("atk" to -5))
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            },
            statBuff = Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("atk" to -5))
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, enemies ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            },
            statBuff = Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("atk" to -5))
        ),
    ),
    cooldown = 3
)

// --- Example Usage ---
fun main() {
    val actorA1 = Actor(
        actorClass = ActorClass.Hunter,
        name = "Alice",
        hp = 100,
        maxHp = 100,
        skills = listOf(takeAim, iceShot, basicAttack),
        team = 0
    )
    val actorA2 = Actor(
        actorClass = ActorClass.Mage,
        name = "Jane",
        hp = 100,
        maxHp = 100,
        skills = listOf(fireball, spark, basicAttack),
        team = 0
    )
    val actorA3 = Actor(
        actorClass = ActorClass.Cleric,
        name = "Aidan",
        hp = 100,
        maxHp = 100,
        skills = listOf(groupHeal, flashHeal, iceLance, basicAttack),
        team = 0
    )
    val actorA4 = Actor(
        actorClass = ActorClass.Paladin,
        name = "Bob",
        hp = 100,
        maxHp = 100,
        skills = listOf(blackHole, hotBuff, basicAttack),
        team = 0
    )
    val actorA5 = Actor(
        actorClass = ActorClass.Bard,
        name = "Charlie",
        hp = 100,
        maxHp = 100,
        skills = listOf(spark, spark, basicAttack),
        team = 0
    )
    val actorB1 = Actor(
        actorClass = ActorClass.AbyssalDragon,
        name = "Abyssal Dragon",
        hp = 400,
        maxHp = 400,
        skills = listOf(fireball, spark, iceLance, poisonStrike, basicAttack),
        team = 1
    )
    val actorB2 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Shaman",
        hp = 120,
        maxHp = 120,
        skills = listOf(groupHeal, flashHeal, hotBuff, spark, basicAttack),
        team = 1
    )
    val actorB3 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Archer",
        hp = 120,
        maxHp = 120,
        skills = listOf(takeAim, iceShot, basicAttack),
        team = 1
    )
    val actorB4 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Warrior",
        hp = 120,
        maxHp = 120,
        skills = listOf(whirlwind, doubleStrike, basicAttack),
        team = 1
    )

    val teamA = Team(mutableListOf(actorA1, actorA2, actorA3, actorA4, actorA5))
    val teamB = Team(mutableListOf(
        actorB1,
        actorB2,
        actorB3,
        actorB4,
    ))

    val events = BattleSimulation(teamA.deepCopy(), teamB.deepCopy()).run().filterNot {
        it is CombatEvent.BuffExpired // currently not used in UI
    }
    val json = combatEventsToJson(events)

    java.io.File("output/battle_log.json").apply {
        writeText(json)
        println("Battle log written to $path, size: ${length()} bytes")
    }
}

fun benchmark(inputTeamA: Team, inputTeamB: Team) {
    repeat(100) { i ->
        val teamA = inputTeamA.deepCopy()
        val teamB = inputTeamB.deepCopy()
        val log: List<CombatEvent>
        val milliSecondsSimulation = measureTime {
            log = BattleSimulation(teamA, teamB).run().filterNot {
                it is CombatEvent.BuffExpired // currently not used in UI
            }
        }

        val json: String
        val milliSecondsJsonEncode = measureTime {
            json = combatEventsToJson(log)
        }

        val turns = log.count { it is CombatEvent.TurnStart } - 1 // Subtract initial state
        println("Run #$i: Simulation took $milliSecondsSimulation," +
                "JSON encoding took $milliSecondsJsonEncode," +
                "Turns: $turns," +
                "Events: ${log.size}," +
                "JSON size: ${json.length} chars")
    }
}