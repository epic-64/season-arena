package playground

import kotlin.math.max
import kotlin.math.min

// --- Buffs & Debuffs ---
data class Buff(
    val id: String,
    val duration: Int,
    val statChanges: Map<String, Int> = emptyMap(), // e.g. {"atk": +10}
    val dot: Int = 0 // Damage over time per turn
)

// --- Skills ---
sealed class SkillType {
    object Damage : SkillType()
    object Heal : SkillType()
    object Buff : SkillType()
    object Debuff : SkillType()
}

data class Skill(
    val name: String,
    val type: SkillType,
    val power: Int,
    val targetRule: (Actor, List<Actor>, List<Actor>) -> List<Actor>, // (self, allies, enemies) -> targets
    val activationRule: (Actor, List<Actor>, List<Actor>) -> Boolean = { _, _, _ -> true }, // Should use this skill?
    val buff: Buff? = null // For buff/debuff skills
)

// --- Actor ---
data class Actor(
    val name: String,
    var hp: Int,
    val maxHp: Int,
    val skills: List<Skill>,
    val team: Int, // 0 or 1
    val stats: MutableMap<String, Int> = mutableMapOf(),
    val buffs: MutableList<Buff> = mutableListOf()
) {
    val isAlive: Boolean get() = hp > 0
}

// --- Team ---
data class Team(val actors: MutableList<Actor>) {
    fun aliveActors() = actors.filter { it.isAlive }
}

// --- Combat Event Data Structure ---
sealed class CombatEvent {
    data class TurnStart(val turn: Int) : CombatEvent()
    data class SkillUsed(val actor: String, val skill: String, val targets: List<String>) : CombatEvent()
    data class DamageDealt(val source: String, val target: String, val amount: Int, val targetHp: Int) : CombatEvent()
    data class Healed(val source: String, val target: String, val amount: Int, val targetHp: Int) : CombatEvent()
    data class BuffApplied(val source: String, val target: String, val buffId: String) : CombatEvent()
    data class BuffExpired(val target: String, val buffId: String) : CombatEvent()
    data class DotApplied(val target: String, val buffId: String, val amount: Int, val targetHp: Int) : CombatEvent()
    data class BattleEnd(val winner: String) : CombatEvent()
}

// --- Game Engine ---
class GameEngine(
    val teamA: Team,
    val teamB: Team
) {
    private var turn: Int = 1

    fun simulateBattle(): List<CombatEvent> {
        val log = mutableListOf<CombatEvent>()
        log.add(CombatEvent.TurnStart(turn = 0))
        while (teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isNotEmpty()) {
            log.add(CombatEvent.TurnStart(turn))
            val allActors = (teamA.aliveActors() + teamB.aliveActors()).shuffled()
            for (actor in allActors) {
                if (!actor.isAlive) continue
                val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
                val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
                val skill = pickSkill(actor, allies, enemies)
                val targets = skill.targetRule(actor, allies, enemies)
                log.add(CombatEvent.SkillUsed(actor.name, skill.name, targets.map { it.name }))
                applySkill(actor, skill, targets, log)
            }
            processBuffs(teamA, log)
            processBuffs(teamB, log)
            turn++
        }
        val winner = if (teamA.aliveActors().isNotEmpty()) "Team A" else "Team B"
        log.add(CombatEvent.BattleEnd(winner))
        return log
    }

    private fun pickSkill(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Skill {
        return actor.skills.firstOrNull { it.activationRule(actor, allies, enemies) } ?: actor.skills.first()
    }

    private fun applySkill(actor: Actor, skill: Skill, targets: List<Actor>, log: MutableList<CombatEvent>) {
        when (skill.type) {
            SkillType.Damage -> {
                for (target in targets) {
                    val dmg = max(1, skill.power + (actor.stats["atk"] ?: 0))
                    target.hp = max(0, target.hp - dmg)
                    log.add(CombatEvent.DamageDealt(actor.name, target.name, dmg, target.hp))
                }
            }
            SkillType.Heal -> {
                for (target in targets) {
                    val heal = max(1, skill.power + (actor.stats["matk"] ?: 0))
                    target.hp = min(target.maxHp, target.hp + heal)
                    log.add(CombatEvent.Healed(actor.name, target.name, heal, target.hp))
                }
            }
            SkillType.Buff, SkillType.Debuff -> {
                for (target in targets) {
                    skill.buff?.let { target.buffs.add(it.copy()) }
                    skill.buff?.let { log.add(CombatEvent.BuffApplied(actor.name, target.name, it.id)) }
                }
            }
        }
    }

    private fun processBuffs(team: Team, log: MutableList<CombatEvent>) {
        for (actor in team.actors) {
            val expired = mutableListOf<Buff>()
            for (buff in actor.buffs) {
                if (buff.dot != 0) {
                    actor.hp = max(0, actor.hp - buff.dot)
                    log.add(CombatEvent.DotApplied(actor.name, buff.id, buff.dot, actor.hp))
                }
            }
            actor.buffs.replaceAll { it.copy(duration = it.duration - 1) }
            expired.addAll(actor.buffs.filter { it.duration <= 0 })
            actor.buffs.removeAll { it.duration <= 0 }
            for (buff in expired) {
                log.add(CombatEvent.BuffExpired(actor.name, buff.id))
            }
        }
    }
}

// --- Example Skills ---
val singleAttack = Skill(
    name = "Strike",
    type = SkillType.Damage,
    power = 20,
    targetRule = { _, _, enemies -> listOf(enemies.firstOrNull() ?: error("No enemy")) }
)

val aoeAttack = Skill(
    name = "Fireball",
    type = SkillType.Damage,
    power = 10,
    targetRule = { _, _, enemies -> if (enemies.size >= 3) enemies else listOf(enemies.firstOrNull() ?: error("No enemy")) },
    activationRule = { _, _, enemies -> enemies.size >= 3 }
)

val healSkill = Skill(
    name = "Heal",
    type = SkillType.Heal,
    power = 15,
    targetRule = { _, allies, _ -> listOf(allies.minByOrNull { it.hp } ?: error("No ally")) },
    activationRule = { _, allies, _ -> allies.any { it.hp < it.maxHp / 2 } }
)

val dotDebuff = Skill(
    name = "Poison",
    type = SkillType.Debuff,
    power = 0,
    targetRule = { _, _, enemies -> listOf(enemies.firstOrNull() ?: error("No enemy")) },
    buff = Buff(id = "Poison", duration = 3, dot = 5)
)

// --- Example Usage ---
fun main() {
    val actorA = Actor(
        name = "Hero",
        hp = 100,
        maxHp = 100,
        skills = listOf(singleAttack, healSkill),
        team = 0
    )
    val actorB = Actor(
        name = "Villain",
        hp = 100,
        maxHp = 100,
        skills = listOf(singleAttack, dotDebuff),
        team = 1
    )
    val teamA = Team(mutableListOf(actorA))
    val teamB = Team(mutableListOf(actorB))
    val engine = GameEngine(teamA, teamB)
    val log = engine.simulateBattle()
    // Print readable event log for dev sanity
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
    }
}
