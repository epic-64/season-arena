package game

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class GameEngineTest : StringSpec({
    "snapshotActors correctly snapshots a single actor" {
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
        val snapshot = endEvent.snapshot
        val hero = snapshot.actors.find { it.name == "Hero" }!!
        val villain = snapshot.actors.find { it.name == "Villain" }!!

        winner shouldBe "Team A"
        villain.hp shouldBe 0
        hero.hp shouldBeGreaterThan 0

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
        log1.delta.actors[0] shouldBe ActorDelta(
            name = attacker.name,
            hp = 30,
            maxHp = 30,
            mana = 100,
            maxMana = 100,
            stats = emptyMap(),
            statBuffs = emptyList(),
            resourceTicks = emptyList(),
            cooldowns = mapOf("Strike" to 0),
            statOverrides = emptyList(),
        )
        log1.delta.actors[1] shouldBe ActorDelta(
            name = defender.name,
            hp = 25,
            maxHp = 25,
            mana = 100,
            maxMana = 100,
            stats = emptyMap(),
            statBuffs = emptyList(),
            resourceTicks = emptyList(),
            cooldowns = mapOf("Strike" to 0),
            statOverrides = emptyList(),
        )

        val log2 = compactLog[1] as CompactCombatEvent.DamageDealt

        log2.source shouldBe attacker.name
        log2.target shouldBe defender.name
        log2.amount shouldBe 20
        log2.targetHp shouldBe 5
        log2.delta.actors.size shouldBe 1 // nothing changed about attacker, only defender
        log2.delta.actors[0] shouldBe ActorDelta(
            name = defender.name,
            hp = 5,
            // rest is missing (implicitly null). Using null to indicate no change.
        )
    }

    "hp and mana regeneration are applied at start of turn" {
        val regenerator = Actor(
            actorClass = ActorClass.Cleric,
            name = "Regen",
            hp = 40,
            maxHp = 50,
            mana = 80,
            maxMana = 100,
            skills = emptyList(),
            team = 0,
            hpRegenPerTurn = 5,
            manaRegenPerTurn = 7,
        )
        val dummyOpponent = Actor(
            actorClass = ActorClass.Fighter,
            name = "Dummy",
            hp = 30,
            maxHp = 30,
            mana = 0,
            maxMana = 0,
            skills = emptyList(),
            team = 1,
        )
        val teamA = Team(mutableListOf(regenerator))
        val teamB = Team(mutableListOf(dummyOpponent))
        val state = BattleState(teamA, teamB, turn = 1, log = mutableListOf())

        battleTick(state, regenerator)

        regenerator.getHp() shouldBe 45 // 40 + 5
        regenerator.getMana() shouldBe 87 // 80 + 7

        val hpRegenEvent = state.log.filterIsInstance<CombatEvent.ResourceRegenerated>().firstOrNull { it.target == "Regen" && it.resource == "hp" }
        hpRegenEvent?.amount shouldBe 5
        hpRegenEvent?.targetResourceValue shouldBe 45

        val manaRegenEvent = state.log.filterIsInstance<CombatEvent.ResourceRegenerated>().firstOrNull { it.target == "Regen" && it.resource == "mana" }
        manaRegenEvent?.amount shouldBe 7
        manaRegenEvent?.targetResourceValue shouldBe 87
    }

    "regeneration does not exceed max values" {
        val regenerator = Actor(
            actorClass = ActorClass.Cleric,
            name = "RegenCap",
            hp = 49,
            maxHp = 50,
            mana = 99,
            maxMana = 100,
            skills = emptyList(),
            team = 0,
            hpRegenPerTurn = 5,
            manaRegenPerTurn = 5,
        )
        val dummyOpponent = Actor(
            actorClass = ActorClass.Fighter,
            name = "Dummy2",
            hp = 30,
            maxHp = 30,
            mana = 0,
            maxMana = 0,
            skills = emptyList(),
            team = 1,
        )
        val teamA = Team(mutableListOf(regenerator))
        val teamB = Team(mutableListOf(dummyOpponent))
        val state = BattleState(teamA, teamB, turn = 1, log = mutableListOf())

        battleTick(state, regenerator)

        regenerator.getHp() shouldBe 50 // capped
        regenerator.getMana() shouldBe 100 // capped

        val hpRegenEvent = state.log.filterIsInstance<CombatEvent.ResourceRegenerated>().firstOrNull { it.target == "RegenCap" && it.resource == "hp" }
        hpRegenEvent?.amount shouldBe 1 // only 1 needed to reach cap
        hpRegenEvent?.targetResourceValue shouldBe 50

        val manaRegenEvent = state.log.filterIsInstance<CombatEvent.ResourceRegenerated>().firstOrNull { it.target == "RegenCap" && it.resource == "mana" }
        manaRegenEvent?.amount shouldBe 1 // only 1 needed to reach cap
        manaRegenEvent?.targetResourceValue shouldBe 100
    }
})
