package game

import game.CombatEvent.*
import game.CompactCombatEvent.*

fun computeBattleDelta(prev: BattleSnapshot, curr: BattleSnapshot): BattleDelta {
    val prevActors = prev.actors.associateBy { it.name }
    val currActors = curr.actors.associateBy { it.name }
    val deltas = mutableListOf<ActorDelta>()
    for ((name, currActor) in currActors) {
        val prevActor = prevActors[name]
        if (prevActor == null) {
            deltas.add(
                ActorDelta(
                    name = currActor.name,
                    hp = currActor.hp,
                    maxHp = currActor.maxHp,
                    mana = currActor.mana,
                    maxMana = currActor.maxMana,
                    stats = currActor.stats,
                    statBuffs = currActor.statBuffs,
                    resourceTicks = currActor.resourceTicks,
                    statOverrides = currActor.statOverrides,
                    cooldowns = currActor.cooldowns
                )
            )
        } else {
            val delta = ActorDelta(
                name = currActor.name,
                hp = currActor.hp.takeIf { it != prevActor.hp },
                maxHp = currActor.maxHp.takeIf { it != prevActor.maxHp },
                mana = currActor.mana.takeIf { it != prevActor.mana },
                maxMana = currActor.maxMana.takeIf { it != prevActor.maxMana },
                stats = currActor.stats.takeIf { it != prevActor.stats },
                statBuffs = currActor.statBuffs.takeIf { it != prevActor.statBuffs },
                resourceTicks = currActor.resourceTicks.takeIf { it != prevActor.resourceTicks },
                statOverrides = currActor.statOverrides.takeIf { it != prevActor.statOverrides },
                cooldowns = currActor.cooldowns.takeIf { it != prevActor.cooldowns }
            )
            if (delta.hasAnyChange()) deltas.add(delta)
        }
    }
    return BattleDelta(deltas)
}

fun BattleDelta.Companion.fullSnapshot(snapshot: BattleSnapshot): BattleDelta =
    BattleDelta(snapshot.actors.map { a ->
        ActorDelta(
            name = a.name,
            hp = a.hp,
            maxHp = a.maxHp,
            mana = a.mana,
            maxMana = a.maxMana,
            stats = a.stats,
            statBuffs = a.statBuffs,
            resourceTicks = a.resourceTicks,
            statOverrides = a.statOverrides,
            cooldowns = a.cooldowns
        )
    })

fun CombatEvent.compact(delta: BattleDelta): CompactCombatEvent = when (this) {
    is BattleStart -> CBattleStart(snapshot)
    is TurnStart -> CTurnStart(turn, delta)
    is CharacterActivated -> CCharacterActivated(actor, delta)
    is SkillUsed -> CSkillUsed(actor, skill, targets, delta)
    is DamageDealt -> CDamageDealt(source, target, amount, targetHp, delta)
    is Healed -> CHealed(source, target, amount, targetHp, delta)
    is BuffApplied -> CBuffApplied(source, target, buffId, delta)
    is BuffRemoved -> CBuffRemoved(target, buffId, delta)
    is ResourceDrained -> CResourceDrained(target, buffId, resource, amount, newValue, delta)
    is ResourceRegenerated -> CResourceRegenerated(target, resource, amount, newValue, delta)
    is BattleEnd -> CBattleEnd(winner, delta)
}

fun List<CombatEvent>.compact(): List<CompactCombatEvent> {
    val firstEvent = firstOrNull() ?: return emptyList()
    val compactEvents = mutableListOf<CompactCombatEvent>()
    var prevSnapshot = firstEvent.snapshot
    val firstDelta = BattleDelta.fullSnapshot(prevSnapshot)
    compactEvents.add(firstEvent.compact(firstDelta))
    for (event in drop(1)) {
        val delta = computeBattleDelta(prevSnapshot, event.snapshot)
        compactEvents.add(event.compact(delta))
        prevSnapshot = event.snapshot
    }
    return compactEvents
}