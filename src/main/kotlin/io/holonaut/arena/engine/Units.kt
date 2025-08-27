package io.holonaut.arena.engine

val UNITS: Map<String, UnitTemplate> = listOf(
    UnitTemplate("berserker", "Berserker", Stats(hp = 42, atk = 12, spd = 7),  AbilitySpec.Strike(multiplier = 1.2)),
    UnitTemplate("guardian",  "Guardian",  Stats(hp = 56, atk = 8,  spd = 5),  AbilitySpec.Guard(shield = 12)),
    UnitTemplate("assassin",  "Assassin",  Stats(hp = 36, atk = 11, spd = 10), AbilitySpec.Bleed(damagePerTurn = 5, turns = 3)),
    UnitTemplate("healer",    "Healer",    Stats(hp = 38, atk = 7,  spd = 8),  AbilitySpec.Heal(amount = 12)),
    UnitTemplate("ranger",    "Ranger",    Stats(hp = 40, atk = 10, spd = 9),  AbilitySpec.Snipe(bonus = 6)),
    UnitTemplate("mage",      "Mage",      Stats(hp = 34, atk = 13, spd = 6),  AbilitySpec.Zap(multiplier = 0.9)),
    UnitTemplate("brute",     "Brute",     Stats(hp = 62, atk = 9,  spd = 4),  AbilitySpec.Strike(multiplier = 1.1)),
    UnitTemplate("monk",      "Monk",      Stats(hp = 44, atk = 9,  spd = 8),  AbilitySpec.Heal(amount = 9))
).associateBy { it.id }

@kotlinx.serialization.Serializable
data class UnitDto(val id: String, val name: String, val hp: Int, val atk: Int, val spd: Int, val ability: String)

fun UnitTemplate.toDto() = UnitDto(
    id = id,
    name = name,
    hp = stats.hp,
    atk = stats.atk,
    spd = stats.spd,
    ability = ability.name
)
