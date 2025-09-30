package game

// ---- Skill Targeting Functions ----
@Suppress("UNUSED_PARAMETER")
fun firstEnemy(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    enemies.take(1)

@Suppress("UNUSED_PARAMETER")
fun actorSelf(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    listOf(actor)

@Suppress("UNUSED_PARAMETER")
fun allEnemies(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    enemies

@Suppress("UNUSED_PARAMETER")
fun allAllies(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    allies

@Suppress("UNUSED_PARAMETER")
fun leastHpAlly(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    listOfNotNull(allies.minByOrNull { it.getHp() })

@Suppress("UNUSED_PARAMETER")
fun leastHpEnemy(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    listOfNotNull(enemies.minByOrNull { it.getHp() })

// ---- Skill Activation Rules ----
@Suppress("UNUSED_PARAMETER")
fun atLeastOneEnemyAlive(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Boolean =
    enemies.any{ it.isAlive }

@Suppress("UNUSED_PARAMETER")
fun atLeastTwoEnemiesAlive(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Boolean =
    enemies.count { it.isAlive } >= 2

@Suppress("UNUSED_PARAMETER")
fun selfHasBuff(name: String): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { actor, _, _ -> actor.temporalEffects.any { it.id == name } }

@Suppress("UNUSED_PARAMETER")
fun enemyWeakTo(damageType: DamageType): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, _, enemies -> enemies.any { it.getResistance(damageType) < 0 } }


val basicAttack = Skill(
    name = "Strike",
    initialTargets = ::firstEnemy,
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 20))),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 1,
    manaCost = 0
)

val takeAim = Skill(
    name = "Take Aim",
    initialTargets = ::actorSelf,
    effects = listOf(
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Amplify", duration = 1, statChanges = mapOf("amplify" to 200))
        ))
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 3,
    manaCost = 5
)

val snipe = Skill(
    name = "Snipe",
    initialTargets = ::leastHpEnemy,
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 50))),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 4,
    manaCost = 10
)

val cheer = Skill(
    name = "Cheer",
    initialTargets = ::allAllies,
    effects = listOf(
        SkillEffect(type = SkillEffectType.StatOverride(
            TemporalEffect.StatOverride(id = "Cheer", duration = 1, statOverrides = mapOf("critChance" to 100))
        ))
    ),
    condition = { _, allies, _ ->
        allies.any { it.temporalEffects.any { buff -> buff.id == "Amplify" } }
    },
    cooldown = 5,
    manaCost = 10
)

val doubleStrike = Skill(
    name = "Double Strike",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15)),
        SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15))
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 2,
    manaCost = 0
)

val whirlwind = Skill(
    name = "Whirlwind",
    initialTargets = ::allEnemies,
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15))),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 2,
    manaCost = 20
)

val fireball = Skill(
    name = "Fireball",
    initialTargets = ::allEnemies,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 25)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Burn", duration = 2, resourceChanges = mapOf("hp" to -10))
        ))
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 4,
    manaCost = 30
)

val spark = Skill(
    name = "Spark",
    initialTargets = { _, _, enemies -> if (enemies.isNotEmpty()) enemies.shuffled().take(2) else emptyList() },
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 10)),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Shock", duration = 2, statChanges = mapOf("def" to -5))
        ))
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 1,
    manaCost = 5
)

val hotBuff = Skill(
    name = "Regeneration",
    initialTargets = ::actorSelf,
    effects = listOf(
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Regen", duration = 3, resourceChanges = mapOf("hp" to 10))
        )),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Protection", duration = 3, statChanges = mapOf("protection" to 10))
        )),
    ),
    condition = { actor, _, _ -> actor.temporalEffects.none { it.id == "Regen" } },
    cooldown = 3,
    manaCost = 10
)

val flashHeal = Skill(
    name = "Flash Heal",
    initialTargets = ::leastHpAlly,
    effects = listOf(SkillEffect(type = SkillEffectType.Heal(25))),
    condition = { _, allies, _ ->
        val target = allies.minByOrNull { it.getHp() }
        target != null && target.getHp() < target.maxHp / 2
    },
    cooldown = 2,
    manaCost = 10
)

val iceShot = Skill(
    name = "Ice Shot",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 25)),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -10))
        ))
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 2,
    manaCost = 10
)

val groupHeal = Skill(
    name = "Group Heal",
    initialTargets = ::allAllies,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Heal(20)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Regen", duration = 2, resourceChanges = mapOf("hp" to 5))
        ))
    ),
    condition = { _, allies, _ ->
        allies.count { it.getHp() < it.maxHp * 0.7 } >= 2
    },
    cooldown = 6,
    manaCost = 30
)

val poisonStrike = Skill(
    name = "Poison Strike",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Poison", duration = 4, resourceChanges = mapOf("hp" to -5))
        ))
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 2,
    manaCost = 5
)

val blackHole = Skill(
    name = "Black Hole",
    initialTargets = ::allEnemies,
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 40))),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 5,
    manaCost = 40
)

val iceLance = Skill(
    name = "Ice Lance",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 30)),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -5))
        )),
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 3,
    manaCost = 15
)

// BARD skills
val solo = Skill(
    name = "Solo",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 5)),
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 10)),
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 15)),
    ),
    condition = ::atLeastOneEnemyAlive,
    cooldown = 1,
    manaCost = 35
)