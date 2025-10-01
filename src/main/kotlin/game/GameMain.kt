package game

import kotlin.time.measureTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    val teamA = exampleTeam1()
    val teamB = exampleTeam2()

    val events = simulateBattle(teamA, teamB).log
    println("Battle log has ${events.size} events")
    println("Battle finished in ${events.count { it is CombatEvent.TurnStart } - 1} turns")

    val compactEvents = toCompactCombatEvents(events)
    println("Original events: ${events.size}, compact events: ${compactEvents.size}")
    if (events.size != compactEvents.size) {
        throw IllegalStateException("Event size mismatch after compaction")
    }

    val json = Json.encodeToString(compactEvents)

    File("output/battle_log.json").apply {
        parentFile.mkdirs()
        writeText(json)
        println("Battle log written to $path, size: ${length()} bytes")
    }
}

fun exampleTeam1(): Team {
    val actorA1 = Actor(
        actorClass = ActorClass.Hunter,
        name = "Alice",
        hp = 100,
        maxHp = 100,
        mana = 100,
        maxMana = 100,
        tactics = listOf(
            Tactic(
                conditions = listOf(selfHasNotBuff("Amplify")),
                skill = takeAim,
                targetGroup = TargetGroup.actor,
                ordering = emptyList(),
            ),
            Tactic(
                conditions = listOf(enemyWeakTo(DamageType.Ice)),
                skill = iceShot,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
            ),
            Tactic(
                conditions = listOf(selfHasBuff("Amplify")),
                skill = snipe,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::mostHp),
            ),
            Tactic(
                conditions = emptyList(),
                skill = basicAttack,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
            )
        ),
        team = 0
    )
    val actorA2 = Actor(
        actorClass = ActorClass.Mage,
        name = "Jane",
        hp = 100,
        maxHp = 100,
        mana = 100,
        maxMana = 100,
        tactics = listOf(
            Tactic(
                conditions = listOf(minimumEnemiesAlive(3), enemyWeakTo(DamageType.Fire)),
                skill = fireball,
                targetGroup = TargetGroup.enemies,
            ),
            Tactic(
                conditions = emptyList(),
                skill = spark,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
            ),
            Tactic(
                conditions = emptyList(),
                skill = basicAttack,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
            )
        ),
        team = 0
    )
    val actorA3 = Actor(
        actorClass = ActorClass.Cleric,
        name = "Aidan",
        hp = 100,
        maxHp = 100,
        mana = 100,
        maxMana = 100,
        tactics = listOf(
            Tactic(
                conditions = listOf(minimumAlliesBelowHp(2, 0.5)),
                skill = groupHeal,
                targetGroup = TargetGroup.allies,
                ordering = listOf(::leastHp),
            ),
            Tactic(
                conditions = listOf(minimumAlliesBelowHp(1, 0.8)),
                skill = flashHeal,
                targetGroup = TargetGroup.allies,
                ordering = listOf(::leastHp),
            ),
            Tactic(
                conditions = listOf(minimumAlliesBelowHp(1, 1.0)),
                skill = hotBuff,
                targetGroup = TargetGroup.allies,
                ordering = listOf(::leastHp),
            ),
            Tactic(
                conditions = emptyList(),
                skill = basicAttack,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
            )
        ),
        team = 0
    )
    val actorA4 = Actor(
        actorClass = ActorClass.Paladin,
        name = "Bob",
        hp = 100,
        maxHp = 100,
        mana = 100,
        maxMana = 100,
        tactics = listOf(
            Tactic(
                conditions = listOf(selfHasNotBuff("Shield of Faith")),
                skill = shieldOfFaith,
                targetGroup = TargetGroup.actor,
            ),
            Tactic(
                conditions = emptyList(),
                skill = holyStrike,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
            ),
            Tactic(
                conditions = emptyList(),
                skill = basicAttack,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
            )
        )
        team = 0
    )
    val actorA5 = Actor(
        actorClass = ActorClass.Bard,
        name = "Charlie",
        hp = 100,
        maxHp = 100,
        mana = 100,
        maxMana = 100,
        tactics = listOf(cheer, spark, basicAttack).map { Tactic(emptyList(), it) },
        team = 0
    )
    return Team(mutableListOf(actorA1, actorA2, actorA3, actorA4, actorA5))
}

fun exampleTeam2(): Team {
    val actorB1 = Actor(
        actorClass = ActorClass.AbyssalDragon,
        name = "Abyssal Dragon",
        hp = 400,
        maxHp = 400,
        mana = 100,
        maxMana = 100,
        tactics = listOf(fireball, spark, iceLance, poisonStrike, basicAttack).map { Tactic(emptyList(), it) },
        team = 1,
        amplifiers = Amplifiers(magicalDamageAdded = 20.0)
    )
    val actorB2 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Shaman",
        hp = 120,
        maxHp = 120,
        mana = 100,
        maxMana = 100,
        tactics = listOf(groupHeal, flashHeal, hotBuff, spark, basicAttack).map { Tactic(emptyList(), it) },
        team = 1
    )
    val actorB3 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Archer",
        hp = 120,
        maxHp = 120,
        mana = 100,
        maxMana = 100,
        tactics = listOf(takeAim, iceShot, basicAttack).map { Tactic(emptyList(), it) },
        team = 1
    )
    val actorB4 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Warrior",
        hp = 120,
        maxHp = 120,
        mana = 100,
        maxMana = 100,
        tactics = listOf(whirlwind, doubleStrike, basicAttack).map { Tactic(emptyList(), it) },
        team = 1
    )
    return Team(mutableListOf(
        actorB1,
        actorB2,
        actorB3,
        actorB4,
    ))
}

fun exampleTeam3(team: Int): Team {
    val actor = Actor(
        actorClass = ActorClass.Bard,
        name = "Charlie the Bard",
        hp = 450,
        maxHp = 450,
        mana = 100,
        maxMana = 100,
        manaRegenPerTurn = 1,
        hpRegenPerTurn = 1,
        tactics = listOf(solo).map { Tactic(emptyList(), it) },
        team = team,
        amplifiers = Amplifiers(magicalDamageAdded = 30.0, physicalDamageAdded = 10.0)
    )
    return Team(mutableListOf(actor))
}

fun exampleTeam4(team: Int): Team {
    val actor = Actor(
        actorClass = ActorClass.AbyssalDragon,
        name = "Abyssal Dragon",
        hp = 500,
        maxHp = 500,
        mana = 100,
        maxMana = 100,
        tactics = listOf(fireball, spark, iceLance, poisonStrike, basicAttack).map { Tactic(emptyList(), it) },
        team = team,
        amplifiers = Amplifiers(magicalDamageAdded = 20.0)
    )
    return Team(mutableListOf(actor))
}

fun simpleActor(name: String, team: Int): Actor {
    return Actor(
        actorClass = ActorClass.Fishman,
        name = name,
        hp = 100,
        maxHp = 100,
        mana = 100,
        maxMana = 100,
        tactics = listOf(basicAttack).map { Tactic(emptyList(), it) },
        team = team
    )
}

fun benchmark(inputTeamA: Team, inputTeamB: Team) {
    repeat(100) { i ->
        val teamA = inputTeamA.deepCopy()
        val teamB = inputTeamB.deepCopy()
        val log: List<CombatEvent>
        val milliSecondsSimulation = measureTime {
            log = simulateBattle(teamA, teamB).log
        }

        val json: String
        val milliSecondsJsonEncode = measureTime {
            json = combatEventsToJson(log)
        }

        val turns = log.count { it is CombatEvent.TurnStart } - 1 // Subtract initial state
        println("Run #$i: Simulation took $milliSecondsSimulation," +
                "JSON encoding took $milliSecondsJsonEncode," +
                "Turns: $turns," +
                "Events: ${log.size}," +
                "JSON size: ${json.length} chars")
    }
}