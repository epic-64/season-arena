package game

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class GameEngineTest : StringSpec({
    "fullBattleDelta correctly deltas a single actor" {
        val actor = Actor(
            actorClass = ActorClass.Mage,
            name = "TestHero",
            hp = 50,
            maxHp = 100,
            mana = 100,
            maxMana = 100,
            skills = emptyList(),
            team = 0
        )
        val team = Team(mutableListOf(actor))
        val delta = fullBattleDelta(listOf(team))
        delta.actors.size shouldBe 1
        val act = delta.actors.first()
        act.name shouldBe "TestHero"
        act.hp shouldBe 50
        act.maxHp shouldBe 100
        act.mana shouldBe 100
        act.maxMana shouldBe 100
        act.stats shouldBe emptyMap()
        act.statBuffs shouldBe emptyList()
        act.resourceTicks shouldBe emptyList()
        act.cooldowns shouldBe emptyMap()
        act.statOverrides shouldBe emptyList()
    }

    "deepCopy creates a true deep copy of Actor and Team" {
        val actor = Actor(
            actorClass = ActorClass.Hunter,
            name = "DeepHero",
            hp = 100,
            maxHp = 100,
            mana = 100,
            maxMana = 100,
            skills = emptyList(),
            team = 1,
            stats = mutableMapOf("strength" to 10),
            temporalEffects = mutableListOf(DurationEffect.StatBuff("empower", 5, mapOf("strength" to 5))),
            cooldowns = mutableMapOf()
        )
        val team = Team(mutableListOf(actor))

        val actorCopy = actor.deepCopy()
        val teamCopy = team.deepCopy()

        // Mutate original
        actor.stats["strength"] = 20
        actor.temporalEffects.clear()
        team.actors.clear()

        // Assert deep copy is unaffected
        actorCopy.stats["strength"] shouldBe 10
        actorCopy.temporalEffects.size shouldBe 1
        actorCopy.temporalEffects.first() shouldBe DurationEffect.StatBuff("empower", 5, mapOf("strength" to 5))

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
            mana = 100,
            maxMana = 100,
            skills = listOf(basicAttack),
            team = 0
        )
        val actorB = Actor(
            actorClass = ActorClass.AbyssalDragon,
            name = "Villain",
            hp = 40,
            maxHp = 40,
            mana = 100,
            maxMana = 100,
            skills = listOf(basicAttack),
            team = 1
        )
        val teamA = Team(mutableListOf(actorA))
        val teamB = Team(mutableListOf(actorB))
        val events = simulateBattle(teamA, teamB).log

        val endEvent = events.last() as CombatEvent.BattleEnd
        val winner = endEvent.winner

        winner shouldBe "Team A"
        // End event delta is empty by design now (only first snapshot is full)
        endEvent.delta.actors.size shouldBe 0

        // Validate via last DamageDealt event that villain reached 0 hp
        val lastDamageToVillain = events.filterIsInstance<CombatEvent.DamageDealt>().last { it.target == "Villain" }
        lastDamageToVillain.targetHp shouldBe 0
        val heroStillAlive = events.filterIsInstance<CombatEvent.DamageDealt>().last { it.target == "Villain" }.delta.actors.firstOrNull { it.name == "Hero" } == null
        heroStillAlive shouldBe true

        val turnCount = events.count { it is CombatEvent.TurnStart } - 1 // Subtract initial state
        turnCount shouldBe 2
    }

    "battleTick applies basic attack and reduces enemy HP" {
        val attacker = Actor(
            actorClass = ActorClass.Hunter,
            name = "Attacker",
            hp = 30,
            maxHp = 30,
            mana = 100,
            maxMana = 100,
            skills = listOf(basicAttack),
            team = 0
        )
        val defender = Actor(
            actorClass = ActorClass.Mage,
            name = "Defender",
            hp = 25,
            maxHp = 25,
            mana = 100,
            maxMana = 100,
            skills = listOf(basicAttack),
            team = 1
        )
        val teamA = Team(mutableListOf(attacker))
        val teamB = Team(mutableListOf(defender))
        val log = mutableListOf<CombatEvent>()
        val state = BattleState(teamA, teamB, turn = 1, log)
        val newState = battleTick(state, attacker)

        val compactLog = toCompactCombatEvents(newState.log)

        val log1 = compactLog[0] as CompactCombatEvent.SkillUsed

        log1.actor shouldBe attacker.name
        log1.skill shouldBe "Strike"
        log1.targets shouldBe listOf(defender.name)
        // Mana cost is 0, so no changes => empty delta
        log1.delta.actors.size shouldBe 0

        val log2 = compactLog[1] as CompactCombatEvent.DamageDealt

        log2.source shouldBe attacker.name
        log2.target shouldBe defender.name
        log2.amount shouldBe 20
        log2.targetHp shouldBe 5
        log2.delta.actors.size shouldBe 1 // only defender changed
        log2.delta.actors[0] shouldBe ActorDelta(
            name = defender.name,
            hp = 5,
            // rest is missing (implicitly null). Using null to indicate no change.
        )
    }
})
