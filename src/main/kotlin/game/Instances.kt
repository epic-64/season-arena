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
fun selfHasNotBuff(name: String): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { actor, _, _ -> actor.temporalEffects.none { it.id == name } }

@Suppress("UNUSED_PARAMETER")
fun atLeastOneEnemyAlive(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Boolean =
    enemies.any{ it.isAlive }

@Suppress("UNUSED_PARAMETER")
fun atLeastTwoEnemiesAlive(actor: Actor, allies: List<Actor>, enemies: List<Actor>): Boolean =
    enemies.count { it.isAlive } >= 2

@Suppress("UNUSED_PARAMETER")
fun minimumEnemiesAlive(n: Int): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, _, enemies -> enemies.count { it.isAlive } >= n }

@Suppress("UNUSED_PARAMETER")
fun minimumAlliesBelowHp(n: Int, threshold: Double): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, allies, _ -> allies.count { it.getHp() <= it.statsBag.maxHp * threshold } >= n }

@Suppress("UNUSED_PARAMETER")
fun selfHasBuff(name: String): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { actor, _, _ -> actor.temporalEffects.any { it.id == name } }

@Suppress("UNUSED_PARAMETER")
fun enemyWeakTo(damageType: DamageType): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, _, enemies -> enemies.any { it.getResistance(damageType) < 0 } }

// ---- Ordering Functions ----
fun leastHp(actors: List<Actor>): List<Actor> = actors.sortedBy { it.getHp() }
fun mostHp(actors: List<Actor>): List<Actor> = actors.sortedByDescending { it.getHp() }
fun leastMana(actors: List<Actor>): List<Actor> = actors.sortedBy { it.getMana() }
fun mostMana(actors: List<Actor>): List<Actor> = actors.sortedByDescending { it.getMana() }


val basicAttack = Skill(
    description = "A basic physical attack dealing moderate damage to a single enemy.",
    name = "Strike",
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 20))),
    condition = ::atLeastOneEnemyAlive,
    maximumTargets = 1,
    cooldown = 1,
    manaCost = 0
)

val takeAim = Skill(
    description = "Increase your next attack's damage by 200% for one turn.",
    name = "Take Aim",
    effects = listOf(
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Amplify", duration = 1, statChanges = mapOf("amplify" to 200))
        ))
    ),
    targetsOverride = ::actorSelf,
    condition = ::atLeastOneEnemyAlive,
    maximumTargets = 1,
    cooldown = 3,
    manaCost = 5
)

val snipe = Skill(
    description = "A powerful ranged attack that deals high physical damage to a single enemy with the lowest health.",
    name = "Snipe",
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 50))),
    condition = ::atLeastOneEnemyAlive,
    maximumTargets = 1,
    cooldown = 4,
    manaCost = 10
)

val cheer = Skill(
    description = "Increase one ally's critical hit chance to 100% and boost their attack by 10 for 3 turns.",
    name = "Cheer",
    effects = listOf(
        SkillEffect(type = SkillEffectType.StatOverride(
            TemporalEffect.StatOverride(id = "Cheer", duration = 1, statOverrides = mapOf("critChance" to 100))
        )),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Morale Boost", duration = 1, statChanges = mapOf("attack" to 10))
        ))
    ),
    maximumTargets = 1,
    cooldown = 2,
    manaCost = 10
)

val doubleStrike = Skill(
    description = "Strike an enemy twice in quick succession, dealing moderate physical damage with each hit.",
    name = "Double Strike",
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15)),
        SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15))
    ),
    maximumTargets = 1,
    cooldown = 2,
    manaCost = 0
)

val whirlwind = Skill(
    description = "A sweeping attack that hits all enemies for moderate physical damage.",
    name = "Whirlwind",
    targetsOverride = ::allEnemies,
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15))),
    maximumTargets = 100,
    cooldown = 2,
    manaCost = 20
)

val fireball = Skill(
    description = "Deal significant magical damage to all enemies and apply a burning effect that deals damage over time.",
    name = "Fireball",
    targetsOverride = ::allEnemies,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 25)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Burn", duration = 2, resourceChanges = mapOf("hp" to -10))
        ))
    ),
    maximumTargets = 100,
    cooldown = 4,
    manaCost = 30
)

val spark = Skill(
    description = "Deal minor damage to two enemies and reduce their defense",
    name = "Spark",
    targetsOverride = { _, _, enemies -> if (enemies.isNotEmpty()) enemies.shuffled().take(2) else emptyList() },
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 10)),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Shock", duration = 2, statChanges = mapOf("def" to -5))
        ))
    ),
    condition = ::atLeastOneEnemyAlive,
    maximumTargets = 2,
    cooldown = 1,
    manaCost = 5
)

val hotBuff = Skill(
    description = "Heal over time and increase protection",
    name = "Regeneration",
    targetsOverride = ::actorSelf,
    effects = listOf(
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Regen", duration = 3, resourceChanges = mapOf("hp" to 10))
        )),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Protection", duration = 3, statChanges = mapOf("protection" to 10))
        )),
    ),
    condition = { actor, _, _ -> actor.temporalEffects.none { it.id == "Regen" } },
    maximumTargets = 1,
    cooldown = 3,
    manaCost = 10
)

val flashHeal = Skill(
    description = "Instantly heal one target for a moderate amount.",
    name = "Flash Heal",
    effects = listOf(SkillEffect(type = SkillEffectType.Heal(25))),
    maximumTargets = 1,
    cooldown = 2,
    manaCost = 10
)

val iceShot = Skill(
    description = "Deal moderate magical damage to a single target and reduce their damage output.",
    name = "Ice Shot",
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 25)),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -10))
        ))
    ),
    maximumTargets = 1,
    cooldown = 2,
    manaCost = 10
)

val groupHeal = Skill(
    description = "Heal all allies for a moderate amount and apply a heal over time effect.",
    name = "Group Heal",
    targetsOverride = ::allAllies,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Heal(20)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Regen", duration = 2, resourceChanges = mapOf("hp" to 5))
        ))
    ),
    maximumTargets = 100,
    cooldown = 6,
    manaCost = 30
)

val poisonStrike = Skill(
    description = "A physical attack that deals moderate damage and applies a poison effect that deals damage over time.",
    name = "Poison Strike",
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Physical, 15)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            TemporalEffect.ResourceTick(id = "Poison", duration = 4, resourceChanges = mapOf("hp" to -5))
        ))
    ),
    maximumTargets = 1,
    cooldown = 2,
    manaCost = 5
)

val blackHole = Skill(
    description = "Create a black hole that deals significant magical damage to a group.",
    name = "Black Hole",
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 40))),
    maximumTargets = 100,
    cooldown = 5,
    manaCost = 40
)

val iceLance = Skill(
    description = "Deal moderate magical damage to a single target and reduce their damage output.",
    name = "Ice Lance",
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 30)),
        SkillEffect(type = SkillEffectType.StatBuff(
            TemporalEffect.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -5))
        )),
    ),
    maximumTargets = 1,
    cooldown = 3,
    manaCost = 15
)

// BARD skills
val solo = Skill(
    description = "Deal increasing magical damage to a single target.",
    name = "Solo",
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 5)),
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 10)),
        SkillEffect(type = SkillEffectType.Damage(DamageType.Magical, 15)),
    ),
    maximumTargets = 1,
    cooldown = 1,
    manaCost = 35
)