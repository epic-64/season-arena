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

// --- Game Engine ---
class GameEngine(
    val teamA: Team,
    val teamB: Team
) {
    private var turn: Int = 1

    fun simulateBattle() {
        println("Battle Start!")
        while (teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isNotEmpty()) {
            println("--- Turn $turn ---")
            val allActors = (teamA.aliveActors() + teamB.aliveActors()).shuffled() // Random order
            for (actor in allActors) {
                if (!actor.isAlive) continue
                val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
                val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
                val skill = pickSkill(actor, allies, enemies)
                val targets = skill.targetRule(actor, allies, enemies)
                println("${actor.name} uses ${skill.name} on ${targets.joinToString { it.name }}")
                applySkill(actor, skill, targets)
            }
            processBuffs(teamA)
            processBuffs(teamB)
            turn++
        }
        val winner = if (teamA.aliveActors().isNotEmpty()) "Team A" else "Team B"
        println("Battle Over! Winner: $winner")
    }

    private fun pickSkill(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Skill {
        // Pick highest priority skill whose activationRule returns true
        return actor.skills.firstOrNull { it.activationRule(actor, allies, enemies) } ?: actor.skills.first()
    }

    private fun applySkill(actor: Actor, skill: Skill, targets: List<Actor>) {
        when (skill.type) {
            SkillType.Damage -> {
                for (target in targets) {
                    val dmg = max(1, skill.power + (actor.stats["atk"] ?: 0))
                    target.hp = max(0, target.hp - dmg)
                    println("${target.name} takes $dmg damage! (HP: ${target.hp}/${target.maxHp})")
                }
            }
            SkillType.Heal -> {
                for (target in targets) {
                    val heal = max(1, skill.power + (actor.stats["matk"] ?: 0))
                    target.hp = min(target.maxHp, target.hp + heal)
                    println("${target.name} heals $heal HP! (HP: ${target.hp}/${target.maxHp})")
                }
            }
            SkillType.Buff, SkillType.Debuff -> {
                for (target in targets) {
                    skill.buff?.let { target.buffs.add(it.copy()) }
                    println("${target.name} receives buff/debuff ${skill.buff?.id}")
                }
            }
        }
    }

    private fun processBuffs(team: Team) {
        for (actor in team.actors) {
            val expired = mutableListOf<Buff>()
            for (buff in actor.buffs) {
                if (buff.dot != 0) {
                    actor.hp = max(0, actor.hp - buff.dot)
                    println("${actor.name} takes ${buff.dot} DoT from ${buff.id}! (HP: ${actor.hp}/${actor.maxHp})")
                }
                // Apply stat changes (for simplicity, just add/remove on apply/expire)
            }
            actor.buffs.replaceAll { it.copy(duration = it.duration - 1) }
            expired.addAll(actor.buffs.filter { it.duration <= 0 })
            actor.buffs.removeAll { it.duration <= 0 }
            for (buff in expired) {
                println("${actor.name}'s buff/debuff ${buff.id} expired.")
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
    engine.simulateBattle()
}
