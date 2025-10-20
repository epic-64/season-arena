package server2

import game.*

fun exampleTeam1(): Team {
    val actorA1 = Actor(
        actorClass = ActorClass.Hunter,
        name = "Alice",
        statsBag = StatsBag.default().copy(hpRegenPerTurn = 5),
        tactics = listOf(
            Tactic(
                conditions = listOf(selfHasNotBuff(BuffId.Amplify)),
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
                conditions = listOf(selfHasBuff(BuffId.Amplify)),
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
        statsBag = StatsBag.default(),
        tactics = listOf(
            Tactic(
                conditions = listOf(minimumEnemiesAlive(3), enemyWeakTo(DamageType.Fire)),
                skill = fireball,
                targetGroup = TargetGroup.enemies,
            ),
            Tactic(
                conditions = emptyList(),
                skill = iceLance,
                targetGroup = TargetGroup.enemies,
                ordering = listOf(::leastHp),
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
        statsBag = StatsBag.default().copy(manaRegenPerTurn = 5),
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
                conditions = listOf(minimumAlliesHaveBuff(BuffId.Burn, 1)),
                skill = extinguish,
                targetGroup = TargetGroup.allies,
                ordering = listOf(::leastHp)
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
        statsBag = StatsBag.default().copy(maxHp = 400, hp = 400),
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

    return Team(mutableListOf(actorB1))
}