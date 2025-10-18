package game

import game.CombatEvent.*
import game.CompactCombatEvent.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun computeBattleDelta(prev: BattleSnapshot, curr: BattleSnapshot): BattleDelta {
    val prevActors = prev.actors.associateBy { it.name }
    val currActors = curr.actors.associateBy { it.name }
    val deltas = mutableListOf<ActorDelta>()
    for ((name, currActor) in currActors) {
        val prevActor = prevActors[name]
        if (prevActor == null) {
            // New actor, include full
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
                hp = if (currActor.hp != prevActor.hp) currActor.hp else null,
                maxHp = if (currActor.maxHp != prevActor.maxHp) currActor.maxHp else null,
                mana = if (currActor.mana != prevActor.mana) currActor.mana else null,
                maxMana = if (currActor.maxMana != prevActor.maxMana) currActor.maxMana else null,
                stats = if (currActor.stats != prevActor.stats) currActor.stats else null,
                statBuffs = if (currActor.statBuffs != prevActor.statBuffs) currActor.statBuffs else null,
                resourceTicks = if (currActor.resourceTicks != prevActor.resourceTicks) currActor.resourceTicks else null,
                statOverrides = if (currActor.statOverrides != prevActor.statOverrides) currActor.statOverrides else null,
                cooldowns = if (currActor.cooldowns != prevActor.cooldowns) currActor.cooldowns else null
            )

            if (delta.hasAnyChange()) {
                deltas.add(delta)
            }
        }
    }
    return BattleDelta(deltas)
}

fun BattleDelta.Companion.fullSnapshot(snapshot: BattleSnapshot): BattleDelta {
    return BattleDelta(snapshot.actors.map {
        ActorDelta(
            name = it.name,
            hp = it.hp,
            maxHp = it.maxHp,
            mana = it.mana,
            maxMana = it.maxMana,
            stats = it.stats,
            statBuffs = it.statBuffs,
            resourceTicks = it.resourceTicks,
            statOverrides = it.statOverrides,
            cooldowns = it.cooldowns
        )
    })
}

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
    val compactEvents = mutableListOf<CompactCombatEvent>()

    val firstEvent = firstOrNull() ?: throw IllegalArgumentException("Event list is empty")

    // For the first event, we always include the full snapshot as delta
    var prevSnapshot: BattleSnapshot = firstEvent.snapshot
    val firstDelta = BattleDelta.fullSnapshot(prevSnapshot)
    val firstCompactEvent = firstEvent.compact(firstDelta)
    compactEvents.add(firstCompactEvent)
    prevSnapshot = firstEvent.snapshot

    for (event in drop(1)) {
        val delta = computeBattleDelta(prevSnapshot, event.snapshot)
        val compactEvent = event.compact(delta)
        compactEvents.add(compactEvent)
        prevSnapshot = event.snapshot
    }

    if (compactEvents.size != size) {
        throw IllegalStateException("Mismatch in compact event size and original event size")
    }

    return compactEvents
}

inline fun<reified T> List<T>.toJson(): String = Json.encodeToString(this)

fun BattleState.compactJson(): String = this.events.compact().toJson()