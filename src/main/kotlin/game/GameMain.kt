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

    return Team(mutableListOf(actorA1, actorA2, actorA3))
}

fun exampleTeam2(): Team {
    val actorB1 = Actor(
        actorClass = ActorClass.AbyssalDragon,
        name = "Abyssal Dragon",
        hp = 400,
        maxHp = 400,
        mana = 100,
        maxMana = 100,
        tactics = listOf(
            Tactic(
                conditions = listOf(minimumEnemiesAlive(3)),
                skill = fireball,
                targetGroup = TargetGroup.enemies,
            ),
            Tactic(
                conditions = listOf(minimumEnemiesAlive(2)),
                skill = spark,
                targetGroup = TargetGroup.enemies, // will be overridden by spark.targetsOverride
            ),
            Tactic(
                conditions = emptyList(),
                skill = iceLance,
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
        team = 1,
        amplifiers = Amplifiers(magicalDamageAdded = 20.0)
    )

    return Team(mutableListOf(
        actorB1,
    ))
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