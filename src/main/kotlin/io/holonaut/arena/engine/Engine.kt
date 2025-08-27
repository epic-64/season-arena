package io.holonaut.arena.engine

import kotlin.math.max
import kotlin.math.min

fun simulateMatch(team1: List<UnitTemplate>, team2: List<UnitTemplate>): SimResult {
    require(team1.size == 3 && team2.size == 3) { "teams must have 3 units" }

    val fighters = mutableListOf<Fighter>().apply {
        team1.forEachIndexed { i, u -> add(Fighter(u, team = 1, slot = i, hp = u.stats.hp)) }
        team2.forEachIndexed { i, u -> add(Fighter(u, team = 2, slot = i, hp = u.stats.hp)) }
    }

    val events = mutableListOf<ReplayEvent>()
    var turn = 0

    // up to 10 turns max to avoid stalemates
    while (turn < 10 && !teamDefeated(fighters, 1) && !teamDefeated(fighters, 2)) {
        turn += 1

        // Initiative: all alive fighters, highest speed first, ties: team 1 first, then lower slot
        val order = fighters
            .filter { it.alive }
            .sortedWith(compareByDescending<Fighter> { it.template.stats.spd }.thenBy { it.team }.thenBy { it.slot })

        for (actor in order) {
            if (!actor.alive) continue
            if (teamDefeated(fighters, 1) || teamDefeated(fighters, 2)) break

            val pre = snapshotBoard(fighters)
            val ev = performAbility(actor, fighters)
            val post = snapshotBoard(fighters)

            events += ev.copy(turn = turn, board = post)

            // End-of-actor effects processed inside performAbility
        }

        // End-of-turn DOTs
        applyBleedTick(fighters, events, turn)
    }

    val winner = when {
        teamDefeated(fighters, 2) && !teamDefeated(fighters, 1) -> 1
        teamDefeated(fighters, 1) && !teamDefeated(fighters, 2) -> 2
        else -> 0
    }
    return SimResult(winner = winner, turns = turn, events = events)
}

private fun teamDefeated(all: List<Fighter>, team: Int) = all.filter { it.team == team }.none { it.alive }

private fun performAbility(actor: Fighter, fighters: MutableList<Fighter>): ReplayEvent {
    val spec = actor.template.ability
    val actorRef = refOf(actor)
    return when (spec) {
        is AbilitySpec.Strike -> {
            val target = pickFrontAliveEnemy(actor.team, fighters) ?: return ReplayEvent(0, actorRef, spec.name, emptyList(), board = snapshotBoard(fighters))
            val raw = (actor.template.stats.atk * spec.multiplier).toInt()
            val dmg = dealDamage(target, raw)
            ReplayEvent(0, actorRef, spec.name, listOf(refOf(target)), damage = dmg, board = snapshotBoard(fighters))
        }
        is AbilitySpec.Bleed -> {
            val target = pickFrontAliveEnemy(actor.team, fighters) ?: return ReplayEvent(0, actorRef, spec.name, emptyList(), board = snapshotBoard(fighters))
            target.bleedLeft = max(target.bleedLeft, spec.turns)
            target.bleedDamage = max(target.bleedDamage, spec.damagePerTurn)
            ReplayEvent(0, actorRef, spec.name, listOf(refOf(target)), applied = listOf("Bleed ${spec.damagePerTurn} x ${spec.turns}"), board = snapshotBoard(fighters))
        }
        is AbilitySpec.Guard -> {
            actor.shield += spec.shield
            ReplayEvent(0, actorRef, spec.name, listOf(actorRef), applied = listOf("Shield +${spec.shield}"), board = snapshotBoard(fighters))
        }
        is AbilitySpec.Heal -> {
            val target = lowestHpAlly(actor.team, fighters) ?: actor
            val healed = heal(target, spec.amount)
            ReplayEvent(0, actorRef, spec.name, listOf(refOf(target)), heal = healed, board = snapshotBoard(fighters))
        }
        is AbilitySpec.Snipe -> {
            val target = lowestHpEnemy(actor.team, fighters) ?: return ReplayEvent(0, actorRef, spec.name, emptyList(), board = snapshotBoard(fighters))
            val raw = actor.template.stats.atk + spec.bonus
            val dmg = dealDamage(target, raw)
            ReplayEvent(0, actorRef, spec.name, listOf(refOf(target)), damage = dmg, board = snapshotBoard(fighters))
        }
        is AbilitySpec.Zap -> {
            // hits two lowest HP enemies for smaller damage
            val enemies = fighters.filter { it.team != actor.team && it.alive }
                .sortedBy { it.hp }
                .take(2)
            var total = 0
            enemies.forEach {
                total += dealDamage(it, (actor.template.stats.atk * spec.multiplier).toInt())
            }
            ReplayEvent(0, actorRef, spec.name, enemies.map { refOf(it) }, damage = total, board = snapshotBoard(fighters))
        }
    }
}

private fun dealDamage(target: Fighter, amount: Int): Int {
    var remaining = amount
    if (target.shield > 0) {
        val absorbed = min(target.shield, remaining)
        target.shield -= absorbed
        remaining -= absorbed
    }
    if (remaining > 0) target.hp -= remaining
    return amount
}

private fun heal(target: Fighter, amount: Int): Int {
    val before = target.hp
    target.hp = min(target.template.stats.hp, target.hp + amount)
    return target.hp - before
}

private fun applyBleedTick(fighters: MutableList<Fighter>, events: MutableList<ReplayEvent>, turn: Int) {
    fighters.filter { it.alive && it.bleedLeft > 0 }.forEach { f ->
        val dmg = f.bleedDamage
        dealDamage(f, dmg)
        f.bleedLeft -= 1
        events += ReplayEvent(
            turn = turn,
            actor = ActorRef(f.team, f.slot, f.template.name),
            ability = "BleedTick",
            targets = listOf(refOf(f)),
            damage = dmg,
            applied = listOf("Bleed -1"),
            board = snapshotBoard(fighters)
        )
    }
}

private fun pickFrontAliveEnemy(team: Int, fighters: List<Fighter>): Fighter? =
    fighters.filter { it.team != team && it.alive }.minByOrNull { it.slot }

private fun lowestHpAlly(team: Int, fighters: List<Fighter>): Fighter? =
    fighters.filter { it.team == team && it.alive }.minByOrNull { it.hp }

private fun lowestHpEnemy(team: Int, fighters: List<Fighter>): Fighter? =
    fighters.filter { it.team != team && it.alive }.minByOrNull { it.hp }
