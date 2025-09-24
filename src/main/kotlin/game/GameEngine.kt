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

fun simulateBattle(teamA: Team, teamB: Team, config: EngineConfig = EngineConfig()): BattleState {
    val teamA = teamA.deepCopy()
    val teamB = teamB.deepCopy()
    var turn = 0
    val maxTurns = 100
    val log = mutableListOf<CombatEvent>()
    // Initial full snapshot event
    log.add(CombatEvent.TurnStart(turn, fullBattleDelta(listOf(teamA, teamB))))
    val state = BattleState(teamA, teamB, turn, log, DeltaContext())
    // Seed snapshots after initial full snapshot
    state.deltaContext.ensureInitialized(listOf(teamA, teamB))
    while (teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isNotEmpty() && turn < maxTurns) {
        turn++
        val newState = state.copy(turn = turn)
        battleRound(newState, config)
        // state mutated in place via logs / actors
    }
    val winner = when {
        teamA.aliveActors().isNotEmpty() && teamB.aliveActors().isEmpty() -> "Team A"
        teamB.aliveActors().isNotEmpty() && teamA.aliveActors().isEmpty() -> "Team B"
        else -> "Draw (turn limit reached)"
    }
    val finalDelta = buildBattleDelta(config, listOf(teamA, teamB), state, isTerminal = true)
    log.add(CombatEvent.BattleEnd(winner, finalDelta))
    return state
}

data class BattleState(
    val teamA: Team,
    val teamB: Team,
    val turn: Int,
    val log: MutableList<CombatEvent>,
    val deltaContext: DeltaContext = DeltaContext(),
)

data class ActorSnapshot(
    val hp: Int,
    val maxHp: Int,
    val mana: Int,
    val maxMana: Int,
    val stats: Map<String, Int>,
    val statBuffs: List<StatBuffDelta>,
    val resourceTicks: List<ResourceTickDelta>,
    val statOverrides: List<StatOverrideDelta>,
    val cooldowns: Map<String, Int>,
)

class DeltaContext(
    val snapshots: MutableMap<String, ActorSnapshot> = mutableMapOf()
) {
    fun ensureInitialized(teams: List<Team>) {
        if (snapshots.isNotEmpty()) return
        teams.flatMap { it.actors }.forEach { actor ->
            snapshots[actor.name] = captureSnapshot(actor)
        }
    }
}

fun battleTick(state: BattleState, actor: Actor, config: EngineConfig = EngineConfig()): BattleState {
    val teamA = state.teamA
    val teamB = state.teamB
    val log = state.log
    state.deltaContext.ensureInitialized(listOf(teamA, teamB))
    processBuffs(state, actor, config)
    if (!actor.isAlive) return state
    val allies = if (actor.team == 0) teamA.aliveActors() else teamB.aliveActors()
    val enemies = if (actor.team == 0) teamB.aliveActors() else teamA.aliveActors()
    val skill = pickSkill(actor, allies, enemies)
    if (skill != null) {
        actor.setMana(actor.getMana() - skill.manaCost)
        actor.cooldowns[skill] = skill.cooldown
        val initialTargets = skill.initialTargets(actor, allies, enemies)
        val targetNames = initialTargets.map { it.name }
        // Build delta now (mana/cooldown changes only) BEFORE applying effects so subsequent effect events only show target changes
        val preEffectsDelta = buildBattleDelta(config, listOf(teamA, teamB), state)
        log.add(CombatEvent.SkillUsed(actor.name, skill.name, targetNames, preEffectsDelta))
        applySkill(state, actor, skill, allies, enemies, initialTargets, config)
    }
    actor.temporalEffects.removeAll { it.duration <= 0 }
    return state
}

fun battleRound(state: BattleState, config: EngineConfig = EngineConfig()): BattleState {
    val log = state.log
    val teamA = state.teamA
    val teamB = state.teamB
    // Turn boundary event: may be patch or full depending on mode/keyframe
    val turnDelta = buildBattleDelta(config, listOf(teamA, teamB), state)
    log.add(CombatEvent.TurnStart(state.turn, turnDelta))
    val allActors = teamA.aliveActors() + teamB.aliveActors()
    for (actor in allActors) {
        battleTick(state, actor, config)
    }
    return state
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
    config: EngineConfig = EngineConfig(),
): BattleState {
    val teamA = state.teamA
    val teamB = state.teamB
    val log = state.log
    var previousTargets: List<Actor> = initialTargets
    for (effect in skill.effects) {
        val targets = effect.targetRule(actor, allies, enemies, previousTargets)
        previousTargets = targets
        if (targets.isEmpty()) continue
        when (effect.type) {
            is SkillEffectType.Damage -> {
                for (target in targets) {
                    var isCriticalHit = false
                    val finalDamage: Int = effect.type.amount
                        .let { actor.amplifiers.getAmplifiedDamage(effect.type.damageType, it) }
                        .let {
                            isCriticalHit = (actor.stats["critChance"] ?: 0) > Random.nextInt(100)
                            if (isCriticalHit) it * 2 else it
                        }
                        .let { (it * (1 + (actor.stats["amplify"] ?: 0) / 100.0)).toInt() }
                        .let {
                            val protection = target.stats["protection"]?.coerceIn(0, 100) ?: 0
                            max(1, (it * (1 - protection / 100.0)).toInt())
                        }
                        .let { max(1, it) }
                    target.setHp(max(0, target.getHp() - finalDamage))
                    val modifiers = mutableListOf<DamageModifier>()
                    if (isCriticalHit) modifiers.add(DamageModifier.Critical)
                    val delta = buildBattleDelta(config, listOf(teamA, teamB), state)
                    log.add(CombatEvent.DamageDealt(
                        actor.name,
                        target.name,
                        finalDamage,
                        target.getHp(),
                        delta,
                        modifiers,
                    ))
                }
            }
            is SkillEffectType.Heal -> {
                for (target in targets) {
                    val heal = max(1, effect.type.power + (actor.stats["matk"] ?: 0))
                    target.setHp(min(target.maxHp, target.getHp() + heal))
                    val delta = buildBattleDelta(config, listOf(teamA, teamB), state)
                    log.add(CombatEvent.Healed(actor.name, target.name, heal, target.getHp(), delta))
                }
            }
            is SkillEffectType.StatBuff -> {
                for (target in targets) {
                    effect.type.buff.let { target.temporalEffects.add(it.copy()) }
                    val delta = buildBattleDelta(config, listOf(teamA, teamB), state)
                    log.add(CombatEvent.BuffApplied(actor.name, target.name, effect.type.buff.id, delta))
                }
            }
            is SkillEffectType.ResourceTick -> {
                for (target in targets) {
                    effect.type.resourceTick.let { target.temporalEffects.add(it.copy()) }
                    val delta = buildBattleDelta(config, listOf(teamA, teamB), state)
                    log.add(CombatEvent.BuffApplied(actor.name, target.name, effect.type.resourceTick.id, delta))
                }
            }
            is SkillEffectType.StatOverride -> {
                for (target in targets) {
                    effect.type.statOverride.let { target.temporalEffects.add(it.copy()) }
                    val delta = buildBattleDelta(config, listOf(teamA, teamB), state)
                    log.add(CombatEvent.BuffApplied(actor.name, target.name, effect.type.statOverride.id, delta))
                }
            }
            is SkillEffectType.DamageOverTime -> {
                for (target in targets) {
                    effect.type.dot.let { target.temporalEffects.add(it.copy()) }
                    val delta = buildBattleDelta(config, listOf(teamA, teamB), state)
                    log.add(CombatEvent.BuffApplied(actor.name, target.name, effect.type.dot.id, delta))
                }
            }
        }
    }
    return state
}

fun processBuffs(state: BattleState, actor: Actor, config: EngineConfig = EngineConfig()): BattleState {
    val activeStatBuffs = actor.temporalEffects.filterIsInstance<DurationEffect.StatBuff>()
    val statBuffTotals = mutableMapOf<String, Int>()
    val statOverrides = actor.temporalEffects.filterIsInstance<DurationEffect.StatOverride>()
    for (buff in activeStatBuffs) {
        for ((stat, change) in buff.statChanges) {
            statBuffTotals[stat] = (statBuffTotals[stat] ?: 0) + change
        }
    }
    for ((stat, value) in statBuffTotals) { actor.stats[stat] = value }
    for (override in statOverrides) {
        for ((stat, value) in override.statOverrides) { actor.stats[stat] = value }
    }
    val expiredBuffs = actor.temporalEffects.filterIsInstance<DurationEffect.StatBuff>().filter { it.duration <= 0 }
    for (buff in expiredBuffs) {
        for ((stat, _) in buff.statChanges) {
            if (activeStatBuffs.none { it.statChanges.containsKey(stat) }) actor.stats.remove(stat)
        }
    }
    val resourceTickGroups = actor.temporalEffects.filterIsInstance<DurationEffect.ResourceTick>().groupBy { it.id }
    for ((id, ticks) in resourceTickGroups) {
        if (ticks.isNotEmpty()) {
            val totalResourceChanges = ticks.flatMap { it.resourceChanges.entries }
                .groupBy { it.key }
                .mapValues { (_, v) -> v.sumOf { it.value } }
            for ((resource, amount) in totalResourceChanges) {
                when (resource) {
                    "hp" -> {
                        val newHp = if (amount > 0) min(actor.maxHp, actor.getHp() + amount) else max(0, actor.getHp() + amount)
                        actor.setHp(newHp)
                        val delta = buildBattleDelta(config, listOf(state.teamA, state.teamB), state)
                        state.log.add(CombatEvent.ResourceDrained(actor.name, id, resource, amount, actor.getHp(), delta))
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

// Helper deciding between full snapshot or patch based on configuration and event position
private fun buildBattleDelta(
    config: EngineConfig,
    teams: List<Team>,
    state: BattleState,
    isTerminal: Boolean = false,
): BattleDelta {
    val ctx = state.deltaContext
    // For FULL_EVERY_EVENT always emit full snapshot and refresh all snapshots
    return when (config.deltaMode) {
        DeltaMode.FULL_EVERY_EVENT -> {
            val full = fullBattleDelta(teams)
            teams.flatMap { it.actors }.forEach { ctx.snapshots[it.name] = captureSnapshot(it) }
            full
        }
        DeltaMode.INITIAL_FULL_ONLY -> {
            if (isTerminal) return BattleDelta(emptyList())
            val patches = mutableListOf<ActorDelta>()
            teams.flatMap { it.actors }.forEach { actor ->
                val snap = ctx.snapshots[actor.name]
                if (snap == null) {
                    // First time we see actor (edge case) seed snapshot without emitting patch
                    ctx.snapshots[actor.name] = captureSnapshot(actor)
                } else {
                    diffActor(actor, snap)?.let { patches.add(it); ctx.snapshots[actor.name] = captureSnapshot(actor) }
                }
            }
            BattleDelta(patches)
        }
        DeltaMode.KEYFRAMES -> {
            val eventIndex = state.log.size // before adding current event
            val isKeyframe = (eventIndex % config.keyframeInterval == 0) || isTerminal
            if (isKeyframe) {
                val full = fullBattleDelta(teams)
                teams.flatMap { it.actors }.forEach { ctx.snapshots[it.name] = captureSnapshot(it) }
                full
            } else {
                val patches = mutableListOf<ActorDelta>()
                teams.flatMap { it.actors }.forEach { actor ->
                    val snap = ctx.snapshots[actor.name]
                    if (snap == null) {
                        ctx.snapshots[actor.name] = captureSnapshot(actor)
                    } else {
                        diffActor(actor, snap)?.let { patches.add(it); ctx.snapshots[actor.name] = captureSnapshot(actor) }
                    }
                }
                BattleDelta(patches)
            }
        }
    }
}

private fun captureSnapshot(actor: Actor): ActorSnapshot = ActorSnapshot(
    hp = actor.getHp(),
    maxHp = actor.maxHp,
    mana = actor.getMana(),
    maxMana = actor.maxMana,
    stats = actor.stats.toMap(),
    statBuffs = actor.temporalEffects.filterIsInstance<DurationEffect.StatBuff>().map { StatBuffDelta(it.id, it.duration, it.statChanges) },
    resourceTicks = actor.temporalEffects.filterIsInstance<DurationEffect.ResourceTick>().map { ResourceTickDelta(it.id, it.duration, it.resourceChanges) },
    statOverrides = actor.temporalEffects.filterIsInstance<DurationEffect.StatOverride>().map { StatOverrideDelta(it.id, it.duration, it.statOverrides) },
    cooldowns = actor.skills.associate { it.name to (actor.cooldowns[it] ?: 0) },
)

private fun diffActor(actor: Actor, snap: ActorSnapshot): ActorDelta? {
    var changed = false
    var hp: Int? = null
    var mana: Int? = null
    var stats: Map<String, Int>? = null
    var statBuffs: List<StatBuffDelta>? = null
    var resourceTicks: List<ResourceTickDelta>? = null
    var statOverrides: List<StatOverrideDelta>? = null
    var cooldowns: Map<String, Int>? = null
    val currentHp = actor.getHp()
    if (currentHp != snap.hp) { hp = currentHp; changed = true }
    val currentMana = actor.getMana()
    if (currentMana != snap.mana) { mana = currentMana; changed = true }
    val currentStats = actor.stats.toMap()
    if (currentStats != snap.stats) { stats = currentStats; changed = true }
    val currentStatBuffs = actor.temporalEffects.filterIsInstance<DurationEffect.StatBuff>().map { StatBuffDelta(it.id, it.duration, it.statChanges) }
    if (currentStatBuffs != snap.statBuffs) { statBuffs = currentStatBuffs; changed = true }
    val currentResourceTicks = actor.temporalEffects.filterIsInstance<DurationEffect.ResourceTick>().map { ResourceTickDelta(it.id, it.duration, it.resourceChanges) }
    if (currentResourceTicks != snap.resourceTicks) { resourceTicks = currentResourceTicks; changed = true }
    val currentStatOverrides = actor.temporalEffects.filterIsInstance<DurationEffect.StatOverride>().map { StatOverrideDelta(it.id, it.duration, it.statOverrides) }
    if (currentStatOverrides != snap.statOverrides) { statOverrides = currentStatOverrides; changed = true }
    val currentCooldowns = actor.skills.associate { it.name to (actor.cooldowns[it] ?: 0) }
    val changedCooldownEntries = currentCooldowns.filter { (k, v) -> snap.cooldowns[k] != v }
    if (changedCooldownEntries.isNotEmpty()) { cooldowns = changedCooldownEntries; changed = true }
    if (!changed) return null
    return ActorDelta(
        name = actor.name,
        hp = hp,
        maxHp = null,
        mana = mana,
        maxMana = null,
        stats = stats,
        statBuffs = statBuffs,
        resourceTicks = resourceTicks,
        statOverrides = statOverrides,
        cooldowns = cooldowns,
    )
}
