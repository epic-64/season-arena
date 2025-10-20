package game

import game.CombatEvent.*
import game.CompactCombatEvent.*

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