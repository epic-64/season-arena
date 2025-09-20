package playground.engine_v1

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min


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

fun combatEventsToJson(events: List<CombatEvent>): String {
    return Json.encodeToString(events)
}

// --- BattleSimulation ---
fun simulate_battle(teamA: Team, teamB: Team): List<CombatEvent> {
    val teamA = teamA.deepCopy()
    val teamB = teamB.deepCopy()

    var turn = 0
    val maxTurns = 100

    fun pickSkill(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Skill?
    {
        // Only pick skills that are not on cooldown
        val availableSkills = actor.skills.filter { (actor.cooldowns[it] ?: 0) <= 0 }
        return availableSkills.firstOrNull { it.activationRule(actor, allies, enemies) } ?: availableSkills.firstOrNull()
    }

    fun applySkill(
        actor: Actor,
        skill: Skill,
        allies: List<Actor>,
        enemies: List<Actor>,
        initialTargets: List<Actor>,
        log: MutableList<CombatEvent>
    )
    {
            var previousTargets: List<Actor> = initialTargets

            for (effect in skill.effects) {
                val targets = effect.targetRule(actor, allies, enemies, previousTargets)

                previousTargets = targets
                if (targets.isEmpty()) {
                    continue
                }

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

    fun processBuffs(actor: Actor, log: MutableList<CombatEvent>)
    {
        val expiredStatBuffs = mutableListOf<Buff.StatBuff>()
        val expiredResourceTicks = mutableListOf<Buff.ResourceTick>()
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
                            val newHp = when (amount > 0) {
                                true -> min(actor.maxHp, actor.getHp() + amount)
                                false -> max(0, actor.getHp() + amount)
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

    val log = mutableListOf<CombatEvent>()
    log.add(CombatEvent.TurnStart(turn, snapshotActors(listOf(teamA, teamB))))

    while (teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isNotEmpty() && turn < maxTurns) {
        turn++
        log.add(CombatEvent.TurnStart(turn, snapshotActors(listOf(teamA, teamB))))
        val allActors = teamA.aliveActors() + teamB.aliveActors()

        for (actor in allActors) {
            processBuffs(actor, log)

            if (!actor.isAlive) {
                continue
            }

            val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
            val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
            val skill = pickSkill(actor, allies, enemies)
            if (skill != null) {
                val initialTargets = skill.initialTargets(actor, allies, enemies)
                val targetNames = initialTargets.map { it.name }
                log.add(CombatEvent.SkillUsed(actor.name, skill.name, targetNames, snapshotActors(listOf(teamA, teamB))))
                applySkill(actor, skill, allies, enemies, initialTargets, log)
            }
            // else: actor skips turn
        }
    }
    val winner = when {
        teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isEmpty() -> "Team A"
        teamB.aliveActors().isNotEmpty() && teamA.aliveActors().isEmpty() -> "Team B"
        else -> "Draw (turn limit reached)"
    }
    log.add(CombatEvent.BattleEnd(winner, snapshotActors(listOf(teamA, teamB))))
    return log
}