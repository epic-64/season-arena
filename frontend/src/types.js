/**
 * @typedef {Object} StatBuff
 * @property {string} id
 * @property {number} duration
 * @property {Object.<string, number>} statChanges
 */

/**
 * @typedef {Object} ResourceTick
 * @property {string} id
 * @property {number} duration
 * @property {Object.<string, number>} resourceChanges
 */

/**
 * @typedef {Object} StatOverrideSnapshot
 * @property {string} id
 * @property {number} duration
 * @property {Object.<string, number>} statOverrides
 */

/**
 * @typedef {Object} DamageOverTimeSnapshot
 * @property {string} id
 * @property {number} duration
 * @property {DamageType} damageType
 * @property {number} amount
 */

/**
 * @readonly
 * @enum {string}
 */
export const DamageType = {
    Physical: 'Physical',
    Magical: 'Magical',
    Absolute: 'Absolute',
};

/**
 * @readonly
 * @enum {string}
 */
export const SkillEffectType = {
    Damage: 'Damage',
    Heal: 'Heal',
    StatBuff: 'StatBuff',
    ResourceTick: 'ResourceTick',
    StatOverride: 'StatOverride',
    DamageOverTime: 'DamageOverTime',
};

/**
 * @typedef {Object} SkillEffect
 * @property {SkillEffectType} type
 * @property {DamageType} [damageType]
 * @property {number} [amount] // for Damage / DamageOverTime base amount
 * @property {number} [power] // for Heal
 * @property {function(Actor, Actor[], Actor[], Actor[]): Actor[]} [targetRule]
 * @property {StatBuff} [statBuff]
 * @property {ResourceTick} [resourceTick]
 * @property {StatOverrideSnapshot} [statOverride]
 * @property {DamageOverTimeSnapshot} [dot]
 */

/**
 * @typedef {Object} Skill
 * @property {string} name
 * @property {SkillEffect[]} effects
 * @property {function(Actor, Actor[], Actor[]): boolean} [activationRule]
 * @property {number} cooldown
 */

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
    Fishman: 'Fishman',
};

/**
 * @readonly
 * @enum {string}
 */
export const CombatEventType = {
    TurnStart: 'TurnStart',
    SkillUsed: 'SkillUsed',
    DamageDealt: 'DamageDealt',
    Healed: 'Healed',
    BuffApplied: 'BuffApplied',
    BuffExpired: 'BuffExpired',
    ResourceDrained: 'ResourceDrained',
    BattleEnd: 'BattleEnd',
};

/**
 * @typedef {Object} Actor
 * @property {ActorClass} actorClass
 * @property {string} name
 * @property {number} hp
 * @property {number} maxHp
 * @property {Skill[]} skills
 * @property {number} team
 * @property {Object.<string, number>} stats
 * @property {(StatBuff|ResourceTick)[]} buffs
 * @property {Object.<string, number>} cooldowns
 * @property {boolean} isAlive
 */

/**
 * @typedef {Object} Team
 * @property {Actor[]} actors
 */

/**
 * @typedef {Object} StatBuffSnapshot
 * @property {string} id
 * @property {number} duration
 * @property {Object.<string, number>} statChanges
 */

/**
 * @typedef {Object} ResourceTickSnapshot
 * @property {string} id
 * @property {number} duration
 * @property {Object.<string, number>} resourceChanges
 */

/**
 * @typedef {Object} ActorSnapshot
 * @property {ActorClass} actorClass
 * @property {string} name
 * @property {number} hp
 * @property {number} maxHp
 * @property {number} mana
 * @property {number} maxMana
 * @property {number} team
 * @property {Object.<string, number>} stats
 * @property {StatBuffSnapshot[]} statBuffs
 * @property {ResourceTickSnapshot[]} resourceTicks
 * @property {StatOverrideSnapshot[]} statOverrides
 * @property {Object.<string, number>} cooldowns
 */

/**
 * @typedef {Object} BattleSnapshot
 * @property {ActorSnapshot[]} actors
 */




/**
 * @typedef {Object} CompactCombatEvent_TurnStart
 * @property {string} type
 * @property {number} turn
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CompactCombatEvent_SkillUsed
 * @property {string} type
 * @property {string} actor
 * @property {string} skill
 * @property {string[]} targets
 * @property {BattleDelta} delta
 */

/**
 * @typedef {Object} CompactCombatEvent_DamageDealt
 * @property {string} type
 * @property {string} source
 * @property {string} target
 * @property {number} amount
 * @property {number} targetHp
 * @property {BattleDelta} delta
 * @property {DamageModifier[]} [modifiers]
 */

/**
 * @typedef {Object} CompactCombatEvent_Healed
 * @property {string} type
 * @property {string} source
 * @property {string} target
 * @property {number} amount
 * @property {number} targetHp
 * @property {BattleDelta} delta
 */

/**
 * @typedef {Object} CompactCombatEvent_BuffApplied
 * @property {string} type
 * @property {string} source
 * @property {string} target
 * @property {string} buffId
 * @property {BattleDelta} delta
 */

/**
 * @typedef {Object} CompactCombatEvent_BuffExpired
 * @property {string} type
 * @property {string} target
 * @property {string} buffId
 * @property {BattleDelta} delta
 */

/**
 * @typedef {Object} CompactCombatEvent_ResourceDrained
 * @property {string} type
 * @property {string} target
 * @property {string} buffId
 * @property {string} resource
 * @property {number} amount
 * @property {number} targetResourceValue
 * @property {BattleDelta} delta
 */

/**
 * @typedef {Object} CompactCombatEvent_BattleEnd
 * @property {string} type
 * @property {string} winner
 * @property {BattleDelta} delta
 */
