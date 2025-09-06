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
})

