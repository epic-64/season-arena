package game

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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Amplify, duration = 1))
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Cheer, duration = 1)),
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.MoraleBoost, duration = 1)),
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Burn, duration = 2))
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Shock, duration = 2))
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Regen, duration = 3, stacks = 2)), // 2 * 5 = 10 per turn
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Protection, duration = 3)),
    ),
    condition = { actor, _, _ -> actor.temporalEffects.none { it.id == BuffId.Regen } },
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Chill, duration = 2, stacks = 2)), // -5 *2 = -10 amplify
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Regen, duration = 2)), // 5 per turn
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Poison, duration = 4))
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
        SkillEffect(type = SkillEffectType.ApplyBuff(BuffId.Chill, duration = 2)), // -5 amplify
    ),
    maximumTargets = 1,
    cooldown = 3,
    manaCost = 15
)

val extinguish = Skill(
    description = "Remove burn effects from an ally and heal them for a small amount.",
    name = "Extinguish",
    effects = listOf(
        SkillEffect(type = SkillEffectType.RemoveTemporalEffect(BuffId.Burn)),
        SkillEffect(type = SkillEffectType.Heal(10))
    ),
    maximumTargets = 1,
    cooldown = 0,
    manaCost = 5
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