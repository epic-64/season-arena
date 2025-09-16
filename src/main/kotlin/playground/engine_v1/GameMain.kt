package playground.engine_v1

import kotlin.time.measureTime

fun main() {
    val teamA = exampleTeam1()
    val teamB = exampleTeam2()

    val events = BattleSimulation(teamA.deepCopy(), teamB.deepCopy()).run().filterNot {
        it is CombatEvent.BuffExpired // currently not used in UI
    }
    val json = combatEventsToJson(events)

    java.io.File("output/battle_log.json").apply {
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
        skills = listOf(takeAim, iceShot, basicAttack),
        team = 0
    )
    val actorA2 = Actor(
        actorClass = ActorClass.Mage,
        name = "Jane",
        hp = 100,
        maxHp = 100,
        skills = listOf(fireball, spark, basicAttack),
        team = 0
    )
    val actorA3 = Actor(
        actorClass = ActorClass.Cleric,
        name = "Aidan",
        hp = 100,
        maxHp = 100,
        skills = listOf(groupHeal, flashHeal, iceLance, basicAttack),
        team = 0
    )
    val actorA4 = Actor(
        actorClass = ActorClass.Paladin,
        name = "Bob",
        hp = 100,
        maxHp = 100,
        skills = listOf(blackHole, hotBuff, basicAttack),
        team = 0
    )
    val actorA5 = Actor(
        actorClass = ActorClass.Bard,
        name = "Charlie",
        hp = 100,
        maxHp = 100,
        skills = listOf(spark, spark, basicAttack),
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
        skills = listOf(fireball, spark, iceLance, poisonStrike, basicAttack),
        team = 1
    )
    val actorB2 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Shaman",
        hp = 120,
        maxHp = 120,
        skills = listOf(groupHeal, flashHeal, hotBuff, spark, basicAttack),
        team = 1
    )
    val actorB3 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Archer",
        hp = 120,
        maxHp = 120,
        skills = listOf(takeAim, iceShot, basicAttack),
        team = 1
    )
    val actorB4 = Actor(
        actorClass = ActorClass.Fishman,
        name = "Fishman Warrior",
        hp = 120,
        maxHp = 120,
        skills = listOf(whirlwind, doubleStrike, basicAttack),
        team = 1
    )
    return Team(mutableListOf(
        actorB1,
        actorB2,
        actorB3,
        actorB4,
    ))
}

fun benchmark(inputTeamA: Team, inputTeamB: Team) {
    repeat(100) { i ->
        val teamA = inputTeamA.deepCopy()
        val teamB = inputTeamB.deepCopy()
        val log: List<CombatEvent>
        val milliSecondsSimulation = measureTime {
            log = BattleSimulation(teamA, teamB).run().filterNot {
                it is CombatEvent.BuffExpired // currently not used in UI
            }
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