package game

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.collections.iterator
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


fun combatEventsToJson(events: List<CombatEvent>): String {
    return Json.encodeToString(events)
}

fun simulateBattle(teamA: Team, teamB: Team): BattleState {
    val teamA = teamA.deepCopy()
    val teamB = teamB.deepCopy()

    var turn = 0
    val maxTurns = 100

    val log = mutableListOf<CombatEvent>()
    log.add(CombatEvent.TurnStart(turn, fullBattleDelta(listOf(teamA, teamB))))

    var state = BattleState(teamA, teamB, turn, log)

    while (teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isNotEmpty() && turn < maxTurns) {
        turn++
        state = battleRound(state)
    }

    val winner = when {
        teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isEmpty() -> "Team A"
        teamB.aliveActors().isNotEmpty() && teamA.aliveActors().isEmpty() -> "Team B"
        else -> "Draw (turn limit reached)"
    }
    log.add(CombatEvent.BattleEnd(winner, BattleDelta(emptyList())))

    return state
}

data class BattleState(
    val teamA: Team,
    val teamB: Team,
    val turn: Int,
    val log: MutableList<CombatEvent>
)

fun battleTick(state: BattleState, actor: Actor): BattleState {
    val teamA = state.teamA
    val teamB = state.teamB
    val log = state.log

    processBuffs(state, actor)

    if (!actor.isAlive) {
        return state // dead actors skip their turn
    }

    val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
    val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
    val skill = pickSkill(actor, allies, enemies)

    if (skill != null) {
        val previousMana = actor.getMana()
        actor.setMana(actor.getMana() - skill.manaCost)
        val manaChanged = skill.manaCost > 0 && actor.getMana() != previousMana
        val initialTargets = skill.initialTargets(actor, allies, enemies)
        val targetNames = initialTargets.map { it.name }
        val deltaActors = if (manaChanged) listOf(ActorDelta(name = actor.name, mana = actor.getMana())) else emptyList()
        log.add(CombatEvent.SkillUsed(actor.name, skill.name, targetNames, BattleDelta(deltaActors)))
        applySkill(state, actor, skill, allies, enemies, initialTargets)
    }

    actor.temporalEffects.removeAll { it.duration <= 0 }

    return state
}

fun battleRound(state: BattleState): BattleState {
    val turn = state.turn
    val log = state.log
    val teamA = state.teamA
    val teamB = state.teamB

    // After initial snapshot, subsequent TurnStart events just mark a boundary; no full snapshot
    log.add(CombatEvent.TurnStart(turn, BattleDelta(emptyList())))
    val allActors = teamA.aliveActors() + teamB.aliveActors()

    var newState = state
    for (actor in allActors) {
        newState = battleTick(state, actor)
    }

    return newState
}

fun pickSkill(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Skill?
{
    // Only pick skills that are not on cooldown and actor has enough mana for
    val availableSkills = actor.skills.filter { (actor.cooldowns[it] ?: 0) <= 0 && actor.getMana() >= it.manaCost }
    return availableSkills.firstOrNull { it.activationRule(actor, allies, enemies) } ?: availableSkills.firstOrNull()
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
                        // Use a partial delta: only include the damaged target's updated HP to satisfy test expectations
                        BattleDelta(listOf(ActorDelta(name = target.name, hp = target.getHp()))),
                        modifiers,
                    ))
                }
            }
            is SkillEffectType.Heal -> {
                for (target in targets) {
                    val heal = max(1, effect.type.power + (actor.stats["matk"] ?: 0))
                    target.setHp(min(target.maxHp, target.getHp() + heal))
                    // Partial delta: only healed target HP changed
                    log.add(CombatEvent.Healed(
                        actor.name,
                        target.name,
                        heal,
                        target.getHp(),
                        BattleDelta(listOf(ActorDelta(name = target.name, hp = target.getHp())))
                    ))
                }
            }
            is SkillEffectType.StatBuff -> {
                for (target in targets) {
                    effect.type.buff.let { target.temporalEffects.add(it.copy()) }
                    // Partial delta: stats + statBuffs for target (stats may change due to buff)
                    val statBuffs = target.temporalEffects.filterIsInstance<DurationEffect.StatBuff>().map { StatBuffDelta(it.id, it.duration, it.statChanges) }
                    log.add(CombatEvent.BuffApplied(
                        actor.name,
                        target.name,
                        effect.type.buff.id,
                        BattleDelta(listOf(ActorDelta(name = target.name, stats = target.stats.toMap(), statBuffs = statBuffs)))
                    ))
                }
            }
            is SkillEffectType.ResourceTick -> {
                for (target in targets) {
                    effect.type.resourceTick.let { target.temporalEffects.add(it.copy()) }
                    // Partial delta: resourceTicks list only
                    val resourceTicks = target.temporalEffects.filterIsInstance<DurationEffect.ResourceTick>().map { ResourceTickDelta(it.id, it.duration, it.resourceChanges) }
                    log.add(CombatEvent.BuffApplied(
                        actor.name,
                        target.name,
                        effect.type.resourceTick.id,
                        BattleDelta(listOf(ActorDelta(name = target.name, resourceTicks = resourceTicks)))
                    ))
                }
            }
            is SkillEffectType.StatOverride -> {
                for (target in targets) {
                    effect.type.statOverride.let { target.temporalEffects.add(it.copy()) }
                    // Partial delta: stats + statOverrides list only
                    val statOverrides = target.temporalEffects.filterIsInstance<DurationEffect.StatOverride>().map { StatOverrideDelta(it.id, it.duration, it.statOverrides) }
                    log.add(CombatEvent.BuffApplied(
                        actor.name,
                        target.name,
                        effect.type.statOverride.id,
                        BattleDelta(listOf(ActorDelta(name = target.name, stats = target.stats.toMap(), statOverrides = statOverrides)))
                    ))
                }
            }
            is SkillEffectType.DamageOverTime -> {
                for (target in targets) {
                    effect.type.dot.let { target.temporalEffects.add(it.copy()) }
                    // No dedicated DOT field in ActorDelta yet; keeping full snapshot would be inconsistent, so send minimal placeholder delta (no direct state change now)
                    log.add(CombatEvent.BuffApplied(
                        actor.name,
                        target.name,
                        effect.type.dot.id,
                        BattleDelta(listOf(ActorDelta(name = target.name)))
                    ))
                }
            }
        }
    }
    // Apply cooldown
    actor.cooldowns[skill] = skill.cooldown

    return state
}

fun processBuffs(state: BattleState, actor: Actor): BattleState
{
    val activeStatBuffs = actor.temporalEffects.filterIsInstance<DurationEffect.StatBuff>()
    val statBuffTotals = mutableMapOf<String, Int>()

    val statOverrides = actor.temporalEffects.filterIsInstance<DurationEffect.StatOverride>()

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
    val expiredBuffs = actor.temporalEffects.filterIsInstance<DurationEffect.StatBuff>().filter { it.duration <= 0 }
    for (buff in expiredBuffs) {
        for ((stat, _) in buff.statChanges) {
            // Remove stat if no other active buff provides it
            if (activeStatBuffs.none { it.statChanges.containsKey(stat) }) {
                actor.stats.remove(stat)
            }
        }
    }

    // --- ResourceTicks: aggregate by id ---
    val resourceTickGroups = actor.temporalEffects.filterIsInstance<DurationEffect.ResourceTick>().groupBy { it.id }
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
                        state.log.add(CombatEvent.ResourceDrained(actor.name, id, resource, amount, actor.getHp(), BattleDelta(listOf(ActorDelta(name = actor.name, hp = actor.getHp())))))
                    }
                }
            }
        }
    }

    actor.temporalEffects.replaceAll { when (it) {
        is DurationEffect.StatBuff -> it.copy(duration = it.duration - 1)
        is DurationEffect.ResourceTick -> it.copy(duration = it.duration - 1)
        is DurationEffect.StatOverride -> it.copy(duration = it.duration - 1)
        is DurationEffect.DamageOverTime -> it.copy(duration = it.duration - 1)
    } }

    actor.cooldowns.replaceAll { _, v -> max(0, v - 1) }

    return state
}

fun fullBattleDelta(teams: List<Team>): BattleDelta {
    return BattleDelta(teams.flatMap { team ->
        team.actors.map { actor ->
            ActorDelta(
                name = actor.name,
                hp = actor.getHp(),
                maxHp = actor.maxHp,
                mana = actor.getMana(),
                maxMana = actor.maxMana,
                stats = actor.stats.toMap(),
                statBuffs = actor.temporalEffects.filterIsInstance<DurationEffect.StatBuff>().map {
                    StatBuffDelta(it.id, it.duration, it.statChanges)
                },
                resourceTicks = actor.temporalEffects.filterIsInstance<DurationEffect.ResourceTick>().map {
                    ResourceTickDelta(it.id, it.duration, it.resourceChanges)
                },
                statOverrides = actor.temporalEffects.filterIsInstance<DurationEffect.StatOverride>().map {
                    StatOverrideDelta(it.id, it.duration, it.statOverrides)
                },
                cooldowns = actor.skills.associate { it.name to (actor.cooldowns[it] ?: 0) }
            )
        }
    })
}
