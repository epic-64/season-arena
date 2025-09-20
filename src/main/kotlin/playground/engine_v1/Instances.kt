package playground.engine_v1

fun firstEnemy(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    enemies.firstOrNull()?.let { listOf(it) } ?: emptyList()

fun actorSelf(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    listOf(actor)

fun allEnemies(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    enemies

fun allAllies(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    allies

fun leastHpAlly(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    allies.minByOrNull { it.getHp() }?.let { listOf(it) } ?: emptyList()

fun leastHpEnemy(actor: Actor, allies: List<Actor>, enemies: List<Actor>): List<Actor> =
    enemies.minByOrNull { it.getHp() }?.let { listOf(it) } ?: emptyList()


val basicAttack = Skill(
    name = "Strike",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage(20)
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 1
)

val takeAim = Skill(
    name = "Take Aim",
    initialTargets = ::actorSelf,
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.StatBuff(
                Buff.StatBuff(id = "Amplify", duration = 1, statChanges = mapOf("amplify" to 200))
            )
        )
    ),
    cooldown = 3
)

val doubleStrike = Skill(
    name = "Double Strike",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(15)),
        SkillEffect(type = SkillEffectType.Damage(15))
    ),
    cooldown = 2
)

val whirlwind = Skill(
    name = "Whirlwind",
    initialTargets = ::allEnemies,
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(15))),
    cooldown = 2
)

val fireball = Skill(
    name = "Fireball",
    initialTargets = ::allEnemies,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(25)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            Buff.ResourceTick(id = "Burn", duration = 2, resourceChanges = mapOf("hp" to -10))
        ))
    ),
    cooldown = 4
)

val spark = Skill(
    name = "Spark",
    initialTargets = { _, _, enemies -> if (enemies.isNotEmpty()) enemies.shuffled().take(2) else emptyList() },
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(10)),
        SkillEffect(type = SkillEffectType.StatBuff(
            Buff.StatBuff(id = "Shock", duration = 2, statChanges = mapOf("def" to -5))
        ))
    ),
    cooldown = 1
)

val hotBuff = Skill(
    name = "Regeneration",
    initialTargets = ::actorSelf,
    effects = listOf(
        SkillEffect(type = SkillEffectType.ResourceTick(
            Buff.ResourceTick(id = "Regen", duration = 3, resourceChanges = mapOf("hp" to 10))
        )),
        SkillEffect(type = SkillEffectType.StatBuff(
            Buff.StatBuff(id = "Protection", duration = 3, statChanges = mapOf("protection" to 10))
        )),
    ),
    activationRule = { actor, _, _ -> actor.buffs.none { it.id == "Regen" } },
    cooldown = 3
)

val flashHeal = Skill(
    name = "Flash Heal",
    initialTargets = ::leastHpAlly,
    effects = listOf(SkillEffect(type = SkillEffectType.Heal(25))),
    activationRule = { _, allies, _ ->
        val target = allies.minByOrNull { it.getHp() }
        target != null && target.getHp() < target.maxHp / 2
    },
    cooldown = 2
)

val iceShot = Skill(
    name = "Ice Shot",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(25),),
        SkillEffect(type = SkillEffectType.StatBuff(
            Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -10))
        ))
    ),
    cooldown = 2
)

val groupHeal = Skill(
    name = "Group Heal",
    initialTargets = ::allAllies,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Heal(20)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            Buff.ResourceTick(id = "Regen", duration = 2, resourceChanges = mapOf("hp" to 5))
        ))
    ),
    activationRule = { _, allies, _ ->
        allies.count { it.getHp() < it.maxHp * 0.7 } >= 2
    },
    cooldown = 6
)

val poisonStrike = Skill(
    name = "Poison Strike",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(15)),
        SkillEffect(type = SkillEffectType.ResourceTick(
            Buff.ResourceTick(id = "Poison", duration = 4, resourceChanges = mapOf("hp" to -5))
        ))
    ),
    cooldown = 2
)

val blackHole = Skill(
    name = "Black Hole",
    initialTargets = ::allEnemies,
    effects = listOf(SkillEffect(type = SkillEffectType.Damage(40))),
    cooldown = 5
)

val iceLance = Skill(
    name = "Ice Lance",
    initialTargets = ::firstEnemy,
    effects = listOf(
        SkillEffect(type = SkillEffectType.Damage(30)),
        SkillEffect(type = SkillEffectType.StatBuff(
            Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -5))
        )),
    ),
    cooldown = 3
)