/**
 * AUTO-GENERATED FILE. DO NOT EDIT DIRECTLY.
 * Regenerate with: ./gradlew generateJsDoc
 */

/**
 * @readonly
 * @enum {string}
 */
export const DamageType = {
    Physical: 'Physical',
    Magical: 'Magical',
    Absolute: 'Absolute'
};

/**
 * @readonly
 * @enum {string}
 */
export const DamageModifier = {
    Critical: 'Critical',
    Blocked: 'Blocked',
    Resisted: 'Resisted'
};

/**
 * @readonly
 * @enum {string}
 */
export const ActorClass = {
    Fighter: 'Fighter',
    Mage: 'Mage',
    Cleric: 'Cleric',
    Rogue: 'Rogue',
    Hunter: 'Hunter',
    Paladin: 'Paladin',
    AbyssalDragon: 'AbyssalDragon',
    Bard: 'Bard',
    Fishman: 'Fishman'
};

/**
 * @typedef {Object} ActorSnapshot
 * @property {ActorClass} actorClass
 * @property {Object.<string, number>} cooldowns
 * @property {number} hp
 * @property {number} mana
 * @property {number} maxHp
 * @property {number} maxMana
 * @property {string} name
 * @property {ResourceTickSnapshot[]} resourceTicks
 * @property {StatBuffSnapshot[]} statBuffs
 * @property {StatOverrideSnapshot[]} statOverrides
 * @property {Object.<string, number>} stats
 * @property {number} team
 */

/**
 * @typedef {Object} StatBuffSnapshot
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} statChanges
 */

/**
 * @typedef {Object} ResourceTickSnapshot
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} resourceChanges
 */

/**
 * @typedef {Object} StatOverrideSnapshot
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} statOverrides
 */

/**
 * @typedef {Object} BattleSnapshot
 * @property {ActorSnapshot[]} actors
 */

/**
 * @typedef {Object} CombatEvent_BattleEnd
 * @property {BattleSnapshot} snapshot
 * @property {string} winner
 */

/**
 * @typedef {Object} CombatEvent_BuffApplied
 * @property {string} buffId
 * @property {BattleSnapshot} snapshot
 * @property {string} source
 * @property {string} target
 */

/**
 * @typedef {Object} CombatEvent_DamageDealt
 * @property {number} amount
 * @property {DamageModifier[]} modifiers
 * @property {BattleSnapshot} snapshot
 * @property {string} source
 * @property {string} target
 * @property {number} targetHp
 */

/**
 * @typedef {Object} CombatEvent_Healed
 * @property {number} amount
 * @property {BattleSnapshot} snapshot
 * @property {string} source
 * @property {string} target
 * @property {number} targetHp
 */

/**
 * @typedef {Object} CombatEvent_ResourceDrained
 * @property {number} amount
 * @property {string} buffId
 * @property {string} resource
 * @property {BattleSnapshot} snapshot
 * @property {string} target
 * @property {number} targetResourceValue
 */

/**
 * @typedef {Object} CombatEvent_SkillUsed
 * @property {string} actor
 * @property {string} skill
 * @property {BattleSnapshot} snapshot
 * @property {string[]} targets
 */

/**
 * @typedef {Object} CombatEvent_TurnStart
 * @property {BattleSnapshot} snapshot
 * @property {number} turn
 */

/**
 * @typedef {Object} ActorDelta
 * @property {(Object.<string, number>|null)} cooldowns (nullable)
 * @property {(number|null)} hp (nullable)
 * @property {(number|null)} mana (nullable)
 * @property {(number|null)} maxHp (nullable)
 * @property {(number|null)} maxMana (nullable)
 * @property {string} name
 * @property {(ResourceTickSnapshot[]|null)} resourceTicks (nullable)
 * @property {(StatBuffSnapshot[]|null)} statBuffs (nullable)
 * @property {(StatOverrideSnapshot[]|null)} statOverrides (nullable)
 * @property {(Object.<string, number>|null)} stats (nullable)
 */

/**
 * @typedef {Object} BattleDelta
 * @property {ActorDelta[]} actors
 */

/**
 * @typedef {Object} DurationEffect_DamageOverTime
 * @property {number} amount
 * @property {DamageType} damageType
 * @property {number} duration
 * @property {string} id
 */

/**
 * @typedef {Object} DurationEffect_ResourceTick
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} resourceChanges
 */

/**
 * @typedef {Object} DurationEffect_StatBuff
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} statChanges
 */

/**
 * @typedef {Object} DurationEffect_StatOverride
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} statOverrides
 */

/**
 * @typedef {Object} SkillEffectType_Damage
 * @property {number} amount
 * @property {DamageType} damageType
 */

/**
 * @typedef {Object} SkillEffectType_DamageOverTime
 * @property {DamageOverTime} dot
 */

/**
 * @typedef {Object} SkillEffectType_Heal
 * @property {number} power
 */

/**
 * @typedef {Object} SkillEffectType_ResourceTick
 * @property {ResourceTick} resourceTick
 */

/**
 * @typedef {Object} SkillEffectType_StatBuff
 * @property {StatBuff} buff
 */

/**
 * @typedef {Object} SkillEffectType_StatOverride
 * @property {StatOverride} statOverride
 */

/**
 * @typedef {Object} Amplifiers
 * @property {number} absoluteDamageAdded
 * @property {number} absoluteDamageMultiplier
 * @property {number} magicalDamageAdded
 * @property {number} magicalDamageMultiplier
 * @property {number} physicalDamageAdded
 * @property {number} physicalDamageMultiplier
 */

/**
 * @typedef {Object} SkillEffect
 * @property {SkillEffectType} type
 */

/**
 * @typedef {Object} DamageOverTime
 * @property {number} amount
 * @property {DamageType} damageType
 * @property {number} duration
 * @property {string} id
 */

/**
 * @typedef {Object} ResourceTick
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} resourceChanges
 */

/**
 * @typedef {Object} StatBuff
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} statChanges
 */

/**
 * @typedef {Object} StatOverride
 * @property {number} duration
 * @property {string} id
 * @property {Object.<string, number>} statOverrides
 */

/**
 * @typedef {(CombatEvent_BattleEnd|CombatEvent_BuffApplied|CombatEvent_DamageDealt|CombatEvent_Healed|CombatEvent_ResourceDrained|CombatEvent_SkillUsed|CombatEvent_TurnStart)} CombatEvent
 */

/**
 * @typedef {(DurationEffect_DamageOverTime|DurationEffect_ResourceTick|DurationEffect_StatBuff|DurationEffect_StatOverride)} DurationEffect
 */

/**
 * @typedef {(SkillEffectType_Damage|SkillEffectType_DamageOverTime|SkillEffectType_Heal|SkillEffectType_ResourceTick|SkillEffectType_StatBuff|SkillEffectType_StatOverride)} SkillEffectType
 */

