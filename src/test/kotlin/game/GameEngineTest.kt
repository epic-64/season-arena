package game

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class GameEngineTest : StringSpec({
    "snapshotActors correctly snapshots a single actor" {
        val actor = Actor(
            actorClass = ActorClass.Mage,
            name = "TestHero",
            statsBag = StatsBag(hp = 50, maxHp = 100, mana = 100, maxMana = 100, hpRegenPerTurn = 5, manaRegenPerTurn = 5),
            tactics = emptyList(), // No skills
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
            statsBag = StatsBag(hp = 100, maxHp = 100, mana = 100, maxMana = 100, hpRegenPerTurn = 5, manaRegenPerTurn = 5),
            tactics = emptyList(),
            team = 1,
            stats = mutableMapOf("strength" to 10),
            temporalEffects = mutableListOf(TemporalEffect(BuffId.Empower, 5, stacks = 1)),
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
        actorCopy.temporalEffects.first() shouldBe TemporalEffect(BuffId.Empower, 5, stacks = 1)

        teamCopy.actors.size shouldBe 1
        teamCopy.actors.first().name shouldBe "DeepHero"
        teamCopy.actors.first().stats["strength"] shouldBe 10
    }

    "basic 1v1 combat is deterministic" {
        val actorA = Actor(
            actorClass = ActorClass.Hunter,
            name = "Hero",
            statsBag = StatsBag(hp = 50, maxHp = 50, mana = 100, maxMana = 100, hpRegenPerTurn = 5, manaRegenPerTurn = 5),
            tactics = listOf(basicAttack).map { Tactic(emptyList(), it, TargetGroup.enemies) },
            team = 0
        )
        val actorB = Actor(
            actorClass = ActorClass.AbyssalDragon,
            name = "Villain",
            statsBag = StatsBag(hp = 40, maxHp = 40, mana = 100, maxMana = 100, hpRegenPerTurn = 5, manaRegenPerTurn = 5),
            tactics = listOf(basicAttack).map { Tactic(emptyList(), it, TargetGroup.enemies) },
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

        events.count { it is CombatEvent.TurnStart } shouldBe 3
    }

    "battleTick applies basic attack and reduces enemy HP" {
        val attacker = Actor(
            actorClass = ActorClass.Hunter,
            name = "Attacker",
            statsBag = StatsBag(hp = 30, maxHp = 30, mana = 100, maxMana = 100, hpRegenPerTurn = 5, manaRegenPerTurn = 5),
            tactics = listOf(basicAttack).map { Tactic(emptyList(), it, TargetGroup.enemies) },
            team = 0
        )
        val defender = Actor(
            actorClass = ActorClass.Mage,
            name = "Defender",
            statsBag = StatsBag(hp = 25, maxHp = 25, mana = 100, maxMana = 100, hpRegenPerTurn = 5, manaRegenPerTurn = 5),
            tactics = listOf(basicAttack).map { Tactic(emptyList(), it, TargetGroup.enemies) },
            team = 1
        )
        val teamA = Team(mutableListOf(attacker))
        val teamB = Team(mutableListOf(defender))
        val log = mutableListOf<CombatEvent>()
        val state = BattleState(teamA, teamB, turn = 1, log)
        val newState = battleTick(state, attacker)

        val compactLog = newState.log.toCompactCombatEvents()
        // [
        // CCharacterActivated(actor=Attacker, delta=BattleDelta(actors=[ActorDelta(name=Attacker, hp=30, maxHp=30, mana=100, maxMana=100, stats={}, statBuffs=[], resourceTicks=[], statOverrides=[], cooldowns={Strike=0}), ActorDelta(name=Defender, hp=25, maxHp=25, mana=100, maxMana=100, stats={}, statBuffs=[], resourceTicks=[], statOverrides=[], cooldowns={Strike=0})])),
        // CSkillUsed(actor=Attacker, skill=Strike, targets=[Defender], delta=BattleDelta(actors=[])),
        // CDamageDealt(source=Attacker, target=Defender, amount=20, targetHp=5, delta=BattleDelta(actors=[ActorDelta(name=Defender, hp=5, maxHp=null, mana=null, maxMana=null, stats=null, statBuffs=null, resourceTicks=null, statOverrides=null, cooldowns=null)]))
        // ]

        val log0 = compactLog[0] as CompactCombatEvent.CCharacterActivated
        log0.actor shouldBe attacker.name
        log0.delta.actors[0] shouldBe ActorDelta(
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
        log0.delta.actors[1] shouldBe ActorDelta(
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

        val log1 = compactLog[1] as CompactCombatEvent.CSkillUsed

        log1.actor shouldBe attacker.name
        log1.skill shouldBe "Strike"
        log1.targets shouldBe listOf(defender.name)

        val log2 = compactLog[2] as CompactCombatEvent.CDamageDealt
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
})
