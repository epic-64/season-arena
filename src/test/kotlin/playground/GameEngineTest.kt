package playground

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GameEngineTest : StringSpec({
    "snapshotActors should correctly snapshot a single actor" {
        val actor = Actor(
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

    "deepCopy should create a true deep copy of Actor and Team" {
        val actor = Actor(
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
})
