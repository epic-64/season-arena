package game

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object TargetGroup {
    val actor = { actor: Actor, _: List<Actor>, _: List<Actor> -> listOf(actor) }
    val allies = { _: Actor, allies: List<Actor>, _: List<Actor> -> allies }
    val enemies = { _: Actor, _: List<Actor>, enemies: List<Actor> -> enemies }
}

fun snapshotActors(teams: List<Team>): BattleSnapshot {
    return BattleSnapshot(
        actors = teams.flatMap { team ->
            team.actors.map { actor ->
                // aggregate temporal effects using BuffRegistry definitions
                val grouped = actor.temporalEffects.groupBy { it.id }

                val statBuffSnapshots = grouped.mapNotNull { (id, effects) ->
                    val def = BuffRegistry.definitions[id] ?: return@mapNotNull null
                    if (def.statBuff.isEmpty()) return@mapNotNull null
                    val totalStacks = effects.sumOf { it.stacks }
                    val mergedStatChanges = def.statBuff.mapValues { (_, v) -> v * totalStacks }
                    StatBuffSnapshot(
                        id = id.label,
                        duration = effects.maxOf { it.duration },
                        statChanges = mergedStatChanges
                    )
                }
                val resourceTickSnapshots = grouped.mapNotNull { (id, effects) ->
                    val def = BuffRegistry.definitions[id] ?: return@mapNotNull null
                    if (def.resourceTick.isEmpty()) return@mapNotNull null
                    val totalStacks = effects.sumOf { it.stacks }
                    val mergedResourceChanges = def.resourceTick.mapValues { (_, v) -> v * totalStacks }
                    ResourceTickSnapshot(
                        id = id.label,
                        duration = effects.maxOf { it.duration },
                        resourceChanges = mergedResourceChanges
                    )
                }
                val statOverrideSnapshots = grouped.mapNotNull { (id, effects) ->
                    val def = BuffRegistry.definitions[id] ?: return@mapNotNull null
                    if (def.statOverride.isEmpty()) return@mapNotNull null
                    // overrides ignore stacks (latest duration kept)
                    StatOverrideSnapshot(
                        id = id.label,
                        duration = effects.maxOf { it.duration },
                        statOverrides = def.statOverride
                    )
                }

                ActorSnapshot(
                    actorClass = actor.actorClass,
                    name = actor.name,
                    hp = actor.getHp(),
                    maxHp = actor.statsBag.maxHp,
                    mana = actor.getMana(),
                    maxMana = actor.statsBag.maxMana,
                    team = actor.team,
                    stats = actor.stats.toMap(),
                    statBuffs = statBuffSnapshots,
                    resourceTicks = resourceTickSnapshots,
                    statOverrides = statOverrideSnapshots,
                    cooldowns = actor.tactics.associate { it.skill.name to (actor.cooldowns[it.skill] ?: 0) }
                )
            }
        }
    )
}

fun combatEventsToJson(events: List<CombatEvent>): String {
    return Json.encodeToString(events)
}

fun simulateBattle(teamA: Team, teamB: Team): BattleState {
    val teamA = teamA.deepCopy()
    val teamB = teamB.deepCopy()
    val maxTurns = 100

    val log = mutableListOf<CombatEvent>()
    log.add(CombatEvent.BattleStart(snapshotActors(listOf(teamA, teamB))))

    var state = BattleState(teamA, teamB, turn = 0, log)

    while (teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isNotEmpty() && state.turn < maxTurns) {
        state.turn++
        state = battleRound(state)
    }

    val winner = when {
        teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isEmpty() -> "Team A"
        teamB.aliveActors().isNotEmpty() && teamA.aliveActors().isEmpty() -> "Team B"
        else -> "Draw (turn limit reached)"
    }
    log.add(CombatEvent.BattleEnd(winner, snapshotActors(listOf(teamA, teamB))))

    return state
}

data class BattleState(
    val teamA: Team,
    val teamB: Team,
    var turn: Int,
    val log: MutableList<CombatEvent>
)

fun battleTick(state: BattleState, actor: Actor): BattleState {
    val teamA = state.teamA
    val teamB = state.teamB
    val log = state.log

    // Emit CharacterActivated event after buffs processed and actor confirmed alive
    log.add(CombatEvent.CharacterActivated(actor.name, snapshotActors(listOf(teamA, teamB))))

    processBuffs(state, actor)

    if (!actor.isAlive) {
        return state // dead actors skip their turn
    }

    // tactic application logic
    val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
    val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
    val tactic = pickTactic(actor, allies, enemies)

    if (tactic != null) {
        val skill = tactic.skill

        // Deduct mana cost before applying skill
        actor.setMana(actor.getMana() - skill.manaCost)
        val initialTargets = tactic.getTargets(actor, allies, enemies)
        val targetNames = initialTargets.map { it.name }
        log.add(CombatEvent.SkillUsed(actor.name, skill.name, targetNames, snapshotActors(listOf(teamA, teamB))))
        applySkill(state, actor, skill, allies, enemies, initialTargets)
    }

    // remove buffs after skill application to ensure they last the full turn
    actor.temporalEffects.removeAll { it.duration <= 0 }

    return state
}

fun battleRound(state: BattleState): BattleState {
    val turn = state.turn
    val log = state.log
    val teamA = state.teamA
    val teamB = state.teamB

    log.add(CombatEvent.TurnStart(turn, snapshotActors(listOf(teamA, teamB))))
    val allActors = teamA.aliveActors() + teamB.aliveActors()

    var newState = state
    for (actor in allActors) {
        newState = battleTick(state, actor)
    }

    return newState
}

fun pickTactic(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Tactic? {
    fun isOffCooldown(skill: Skill): Boolean = (actor.cooldowns[skill] ?: 0) <= 0
    fun hasEnoughMana(skill: Skill): Boolean = actor.getMana() >= skill.manaCost
    fun matchesConditions(tactic: Tactic): Boolean =
        tactic.skill.condition(actor, allies, enemies) &&
        tactic.conditions.all { it(actor, allies, enemies) }

    return actor.tactics.firstOrNull {
        isOffCooldown(it.skill) && hasEnoughMana(it.skill) && matchesConditions(it)
    }
}

fun applySkill(
    state: BattleState,
    actor: Actor,
    skill: Skill,
    allies: List<Actor>,
    enemies: List<Actor>,
    initialTargets: List<Actor>,
): BattleState
{
    val teamA = state.teamA
    val teamB = state.teamB
    val log = state.log

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
                    var isCriticalHit = false

                    val finalDamage: Int = effect.type.amount
                        .let { // get amplifiers from actor
                            actor.amplifiers.getAmplifiedDamage(effect.type.damageType, it)
                        }
                        .let { // apply critical damage if applicable
                            isCriticalHit = (actor.stats["critChance"] ?: 0) > Random.nextInt(100)
                            if (isCriticalHit) it * 2 else it
                        }
                        .let { // apply amplify buffs from actor
                            (it * (1 + (actor.stats["amplify"] ?: 0) / 100.0)).toInt()
                        }
                        .let { // apply protection from target (0-100%)
                            val protection = target.stats["protection"]?.coerceIn(0, 100) ?: 0
                            max(1, (it * (1 - protection / 100.0)).toInt())
                        }
                        .let { // clamp to at least 1 damage
                            max(1, it)
                        }

                    target.setHp(max(0, target.getHp() - finalDamage))

                    val modifiers = mutableListOf<DamageModifier>()
                    if (isCriticalHit) {
                        modifiers.add(DamageModifier.Critical)
                    }

                    log.add(CombatEvent.DamageDealt(
                        actor.name,
                        target.name,
                        finalDamage,
                        target.getHp(),
                        snapshotActors(listOf(teamA, teamB)),
                        modifiers,
                    ))
                }
            }
            is SkillEffectType.Heal -> {
                for (target in targets) {
                    val heal = max(1, effect.type.power + (actor.stats["matk"] ?: 0))
                    target.setHp(min(target.statsBag.maxHp, target.getHp() + heal))
                    log.add(CombatEvent.Healed(actor.name, target.name, heal, target.getHp(), snapshotActors(listOf(teamA, teamB))))
                }
            }
            is SkillEffectType.ApplyBuff -> {
                for (target in targets) {
                    target.temporalEffects.add(TemporalEffect(effect.type.id, effect.type.duration, effect.type.stacks))
                    log.add(CombatEvent.BuffApplied(actor.name, target.name, effect.type.id.label, snapshotActors(listOf(teamA, teamB))))
                }
            }
            is SkillEffectType.RemoveTemporalEffect -> {
                for (target in targets) {
                    val before = target.temporalEffects.size
                    target.temporalEffects.removeIf { it.id == effect.type.effectId }
                    val after = target.temporalEffects.size
                    if (before != after) {
                        log.add(CombatEvent.BuffRemoved(target.name, effect.type.effectId.label, snapshotActors(listOf(teamA, teamB))))
                    }
                }
            }
        }
    }
    // Apply cooldown
    actor.cooldowns[skill] = skill.cooldown

    return state
}

fun processBuffs(state: BattleState, actor: Actor): BattleState {
    val log = state.log

    // Passive regeneration (applied at the start of the actor's turn before other duration effects tick)
    if (actor.isAlive) {
        // HP regen
        if (actor.statsBag.hpRegenPerTurn > 0 && actor.getHp() < actor.statsBag.maxHp) {
            val before = actor.getHp()
            actor.setHp(before + actor.statsBag.hpRegenPerTurn)
            val gained = actor.getHp() - before
            if (gained > 0) {
                log.add(
                    CombatEvent.ResourceRegenerated(
                        target = actor.name,
                        resource = "hp",
                        amount = gained,
                        newValue = actor.getHp(),
                        snapshot = snapshotActors(listOf(state.teamA, state.teamB))
                    )
                )
            }
        }
        // Mana regen
        if (actor.statsBag.manaRegenPerTurn > 0 && actor.getMana() < actor.statsBag.maxMana) {
            val before = actor.getMana()
            actor.setMana(actor.getMana() + actor.statsBag.manaRegenPerTurn)
            val gained = actor.getMana() - before
            if (gained > 0) {
                log.add(
                    CombatEvent.ResourceRegenerated(
                        target = actor.name,
                        resource = "mana",
                        amount = gained,
                        newValue = actor.getMana(),
                        snapshot = snapshotActors(listOf(state.teamA, state.teamB))
                    )
                )
            }
        }
    }

    val statBuffTotals = mutableMapOf<String, Int>()

    // Process all temporal effects using registry definitions
    for (effect in actor.temporalEffects) {
        val def = BuffRegistry.definitions[effect.id] ?: continue
        // stat buffs (additive * stacks)
        def.statBuff.forEach { (stat, value) ->
            statBuffTotals[stat] = (statBuffTotals[stat] ?: 0) + value * effect.stacks
        }
    }

    // Set stats to buff totals first
    for ((stat, value) in statBuffTotals) {
        actor.stats[stat] = value
    }

    // Apply overrides (ignore stacking, last wins but they should be same per id)
    for (effect in actor.temporalEffects) {
        val def = BuffRegistry.definitions[effect.id] ?: continue
        def.statOverride.forEach { (stat, value) ->
            actor.stats[stat] = value
        }
    }

    // Resource ticks aggregated by resource
    val resourceAggregates = mutableMapOf<String, Int>()
    for (effect in actor.temporalEffects) {
        val def = BuffRegistry.definitions[effect.id] ?: continue
        def.resourceTick.forEach { (resource, amount) ->
            resourceAggregates[resource] = (resourceAggregates[resource] ?: 0) + amount * effect.stacks
        }
    }
    for ((resource, amount) in resourceAggregates) {
        when (resource) {
            "hp" -> {
                val newHp = when (amount > 0) {
                    true -> min(actor.statsBag.maxHp, actor.getHp() + amount)
                    false -> max(0, actor.getHp() + amount)
                }
                actor.setHp(newHp)
                log.add(CombatEvent.ResourceDrained(actor.name, "Buff", resource, amount, actor.getHp(), snapshotActors(listOf(state.teamA, state.teamB))))
            }
        }
    }

    // decrement and remove expired
    actor.temporalEffects.replaceAll { it.decrement() }
    actor.temporalEffects.removeAll { it.duration <= 0 }

    actor.cooldowns.replaceAll { _, v -> max(0, v - 1) }

    return state
}
