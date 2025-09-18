package playground.engine_v1

val basicAttack = Skill(
    name = "Strike",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 20,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 1
)

val takeAim = Skill(
    name = "Take Aim",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.StatBuff,
            power = 0,
            targetRule = { actor, _, _, _ -> listOf(actor) },
            statBuff = Buff.StatBuff(id = "Amplify", duration = 1, statChanges = mapOf("amplify" to 200))
        )
    ),
    cooldown = 3
)

val doubleStrike = Skill(
    name = "Double Strike",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        )
    ),
    activationRule = { actor, _, enemies -> enemies.isNotEmpty() },
    cooldown = 2
)

val whirlwind = Skill(
    name = "Whirlwind",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies, _ -> enemies } // All enemies
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 2
)

val fireball = Skill(
    name = "Fireball",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 25,
            targetRule = { _, _, enemies, _ -> enemies }
        ),
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { _, _, enemies, _ -> enemies },
            resourceTick = Buff.ResourceTick(id = "Burn", duration = 2, resourceChanges = mapOf("hp" to -10))
        )
    ),
    activationRule = { _, _, enemies -> enemies.isNotEmpty() },
    cooldown = 4
)

val spark = Skill(
    name = "Spark",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 10,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) enemies.shuffled().take(2) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, _, previousTargets -> previousTargets }, // Chain to previous effect's targets
            statBuff = Buff.StatBuff(id = "Shock", duration = 2, statChanges = mapOf("def" to -5)),
        )
    ),
    cooldown = 1
)

val hotBuff = Skill(
    name = "Regeneration",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { actor, _, _, _ -> listOf(actor) },
            resourceTick = Buff.ResourceTick(id = "Regen", duration = 3, resourceChanges = mapOf("hp" to 10)) // Regeneration should heal
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            power = 0,
            targetRule = { actor, _, _, _ -> listOf(actor) },
            statBuff = Buff.StatBuff(id = "Protection", duration = 3, statChanges = mapOf("protection" to 10))
        ),
    ),
    activationRule = { actor, _, _ -> actor.buffs.none { it.id == "Regen" } },
    cooldown = 3
)

val flashHeal = Skill(
    name = "Flash Heal",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Heal,
            power = 25,
            targetRule = { _, allies, _, _ ->
                val target = allies.minByOrNull { it.getHp() }
                if (target != null) listOf(target) else emptyList()
            }
        )
    ),
    activationRule = { _, allies, _ ->
        val target = allies.minByOrNull { it.getHp() }
        target != null && target.getHp() < target.maxHp / 2
    },
    cooldown = 2
)

val iceShot = Skill(
    name = "Ice Shot",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 25,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, _, previous -> previous },
            statBuff = Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("amplify" to -10))
        )
    ),
    cooldown = 2
)

val groupHeal = Skill(
    name = "Group Heal",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Heal,
            power = 20,
            targetRule = { _, allies, _, _ -> allies }
        ),
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { _, allies, _, _ -> allies },
            resourceTick = Buff.ResourceTick(id = "Regen", duration = 2, resourceChanges = mapOf("hp" to 5)) // Should heal, not damage
        )
    ),
    activationRule = { _, allies, _ ->
        allies.count { it.getHp() < it.maxHp * 0.7 } >= 2
    },
    cooldown = 6
)

val poisonStrike = Skill(
    name = "Poison Strike",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 15,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.ResourceTick,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            },
            resourceTick = Buff.ResourceTick(id = "Poison", duration = 4, resourceChanges = mapOf("hp" to -5)) // Poison should damage
        )
    ),
    cooldown = 2
)

val blackHole = Skill(
    name = "Black Hole",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 40,
            targetRule = { _, _, enemies, _ -> enemies }
        ),
    ),
    cooldown = 5
)

val iceLance = Skill(
    name = "Ice Lance",
    effects = listOf(
        SkillEffect(
            type = SkillEffectType.Damage,
            power = 30,
            targetRule = { _, _, enemies, _ ->
                if (enemies.isNotEmpty()) listOf(enemies.first()) else emptyList()
            }
        ),
        SkillEffect(
            type = SkillEffectType.StatBuff,
            targetRule = { _, _, _, previous -> previous },
            statBuff = Buff.StatBuff(id = "Chill", duration = 2, statChanges = mapOf("atk" to -5))
        ),
    ),
    cooldown = 3
)