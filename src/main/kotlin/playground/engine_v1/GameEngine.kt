package playground.engine_v1

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


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
                    statBuffs = actor.buffs.filterIsInstance<DurationEffect.StatBuff>()
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
                    resourceTicks = actor.buffs.filterIsInstance<DurationEffect.ResourceTick>()
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
                    statOverrides = actor.buffs.filterIsInstance<DurationEffect.StatOverride>()
                        .groupBy { it.id }
                        .map { (id, overrides) ->
                            // Summarize statOverrides by taking the latest value for each stat
                            val mergedStatOverrides = mutableMapOf<String, Int>()
                            overrides.forEach { override ->
                                override.statOverrides.forEach { (stat, value) ->
                                    mergedStatOverrides[stat] = value // latest value
                                }
                            }
                            StatOverrideSnapshot(
                                id = id,
                                duration = overrides.maxOf { it.duration }, // longest duration
                                statOverrides = mergedStatOverrides
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
                    is SkillEffectType.Damage -> {
                        for (target in targets) {
                            val rawDmg = max(1, effect.type.power + (actor.stats["atk"] ?: 0))
                            val isCriticalHit = (actor.stats["critChance"] ?: 0) > Random.nextInt(100)
                            val criticalDamage = if (isCriticalHit) rawDmg * 2 else rawDmg
                            val amplifiedDmg = (criticalDamage * (1 + (actor.stats["amplify"] ?: 0) / 100.0)).toInt()
                            val protection = target.stats["protection"]?.coerceIn(0, 100) ?: 0
                            // Protection is a percentage: 10 = 10% reduced damage
                            val finalDmg = max(1, (amplifiedDmg * (1 - protection / 100.0)).toInt())
                            target.setHp(max(0, target.getHp() - finalDmg))

                            val modifiers = mutableListOf<DamageModifier>()
                            if (isCriticalHit) {
                                modifiers.add(DamageModifier.Critical)
                            }

                            log.add(CombatEvent.DamageDealt(
                                actor.name,
                                target.name,
                                finalDmg,
                                target.getHp(),
                                snapshotActors(listOf(teamA, teamB)),
                                modifiers,
                            ))
                        }
                    }
                    is SkillEffectType.Heal -> {
                        for (target in targets) {
                            val heal = max(1, effect.type.power + (actor.stats["matk"] ?: 0))
                            target.setHp(min(target.maxHp, target.getHp() + heal))
                            log.add(CombatEvent.Healed(actor.name, target.name, heal, target.getHp(), snapshotActors(listOf(teamA, teamB))))
                        }
                    }
                    is SkillEffectType.StatBuff -> {
                        for (target in targets) {
                            effect.type.buff.let { target.buffs.add(it.copy()) }
                            effect.type.buff.let { log.add(CombatEvent.BuffApplied(actor.name, target.name, it.id, snapshotActors(listOf(teamA, teamB)))) }
                        }
                    }
                    is SkillEffectType.ResourceTick -> {
                        for (target in targets) {
                            effect.type.resourceTick.let { target.buffs.add(it.copy()) }
                            effect.type.resourceTick.let { log.add(CombatEvent.BuffApplied(actor.name, target.name, it.id, snapshotActors(listOf(teamA, teamB)))) }
                        }
                    }
                    is SkillEffectType.StatOverride -> {
                        for (target in targets) {
                            effect.type.statOverride.let { target.buffs.add(it.copy()) }
                            effect.type.statOverride.let { log.add(CombatEvent.BuffApplied(actor.name, target.name, it.id, snapshotActors(listOf(teamA, teamB)))) }
                        }
                    }
                }
            }
            // Apply cooldown
            actor.cooldowns[skill] = skill.cooldown
        }

    fun processBuffs(actor: Actor, log: MutableList<CombatEvent>)
    {
        val activeStatBuffs = actor.buffs.filterIsInstance<DurationEffect.StatBuff>()
        val statBuffTotals = mutableMapOf<String, Int>()

        val statOverrides = actor.buffs.filterIsInstance<DurationEffect.StatOverride>()

        for (buff in activeStatBuffs) {
            for ((stat, change) in buff.statChanges) {
                statBuffTotals[stat] = (statBuffTotals[stat] ?: 0) + change
            }
        }

        // Set stats to base + total from active buffs
        for ((stat, value) in statBuffTotals) {
            actor.stats[stat] = value
        }

        // Apply stat overrides (these take precedence)
        for (override in statOverrides) {
            for ((stat, value) in override.statOverrides) {
                actor.stats[stat] = value
            }
        }

        // Remove stats for buffs that have expired
        val expiredBuffs = actor.buffs.filterIsInstance<DurationEffect.StatBuff>().filter { it.duration <= 0 }
        for (buff in expiredBuffs) {
            for ((stat, _) in buff.statChanges) {
                // Remove stat if no other active buff provides it
                if (activeStatBuffs.none { it.statChanges.containsKey(stat) }) {
                    actor.stats.remove(stat)
                }
            }
        }

        // --- ResourceTicks: aggregate by id ---
        val resourceTickGroups = actor.buffs.filterIsInstance<DurationEffect.ResourceTick>().groupBy { it.id }
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
            is DurationEffect.StatBuff -> it.copy(duration = it.duration - 1)
            is DurationEffect.ResourceTick -> it.copy(duration = it.duration - 1)
            is DurationEffect.StatOverride -> it.copy(duration = it.duration - 1)
        } }

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

            // skill application logic
            val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
            val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
            val skill = pickSkill(actor, allies, enemies)

            if (skill != null) {
                val initialTargets = skill.initialTargets(actor, allies, enemies)
                val targetNames = initialTargets.map { it.name }
                log.add(CombatEvent.SkillUsed(actor.name, skill.name, targetNames, snapshotActors(listOf(teamA, teamB))))
                applySkill(actor, skill, allies, enemies, initialTargets, log)
            }

            // remove buffs after skill application to ensure they last the full turn
            actor.buffs.removeAll { it.duration <= 0 }
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