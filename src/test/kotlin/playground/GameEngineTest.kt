package playground

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import playground.engine_v1.*

class GameEngineTest : StringSpec({
    "snapshotActors correctly snapshots a single actor" {
        val actor = Actor(
            actorClass = ActorClass.Mage,
            name = "TestHero",
            hp = 50,
            maxHp = 100,
            skills = emptyList(),
            team = 0
        )
        val team = Team(mutableListOf(actor))
        val snapshot = snapshotActors(listOf(team))
        snapshot.actors.size shouldBe 1
        val snap = snapshot.actors.first()
        snap.name shouldBe "TestHero"
        snap.hp shouldBe 50
        snap.maxHp shouldBe 100
        snap.team shouldBe 0
        snap.stats shouldBe emptyMap()
        snap.statBuffs shouldBe emptyList()
        snap.resourceTicks shouldBe emptyList()
        snap.cooldowns shouldBe emptyMap()
    }

    "deepCopy creates a true deep copy of Actor and Team" {
        val actor = Actor(
            actorClass = ActorClass.Hunter,
            name = "DeepHero",
            hp = 100,
            maxHp = 100,
            skills = emptyList(),
            team = 1,
            stats = mutableMapOf("strength" to 10),
            buffs = mutableListOf(Buff.StatBuff("strength", 5)),
            cooldowns = mutableMapOf()
        )
        val team = Team(mutableListOf(actor))

        val actorCopy = actor.deepCopy()
        val teamCopy = team.deepCopy()

        // Mutate original
        actor.stats["strength"] = 20
        actor.buffs.clear()
        team.actors.clear()

        // Assert deep copy is unaffected
        actorCopy.stats["strength"] shouldBe 10
        actorCopy.buffs.size shouldBe 1
        teamCopy.actors.size shouldBe 1
        teamCopy.actors.first().name shouldBe "DeepHero"
        teamCopy.actors.first().stats["strength"] shouldBe 10
    }

    "basic 1v1 combat is deterministic" {
        val actorA = Actor(
            actorClass = ActorClass.Hunter,
            name = "Hero",
            hp = 50,
            maxHp = 50,
            skills = listOf(basicAttack),
            team = 0
        )
        val actorB = Actor(
            actorClass = ActorClass.AbyssalDragon,
            name = "Villain",
            hp = 40,
            maxHp = 40,
            skills = listOf(basicAttack),
            team = 1
        )
        val teamA = Team(mutableListOf(actorA))
        val teamB = Team(mutableListOf(actorB))
        val events = simulate_battle(teamA, teamB)

        printBattleEvents(events)

        val endEvent = events.last() as CombatEvent.BattleEnd
        val winner = endEvent.winner
        val snapshot = endEvent.snapshot
        val hero = snapshot.actors.find { it.name == "Hero" }!!
        val villain = snapshot.actors.find { it.name == "Villain" }!!

        winner shouldBe "Team A"
        villain.hp shouldBe 0
        hero.hp shouldBeGreaterThan 0

        val turnCount = events.count { it is CombatEvent.TurnStart } - 1 // Subtract initial state
        turnCount shouldBe 2
    }
})
