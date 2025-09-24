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
 * @typedef {('Damage'|'Heal'|'StatBuff'|'ResourceTick')} SkillEffectType
 */

/**
 * @typedef {Object} SkillEffect
 * @property {SkillEffectType} type
 * @property {number} [power]
 * @property {function(Actor, Actor[], Actor[]): Actor[]} targetRule
 * @property {StatBuff} [statBuff]
 * @property {ResourceTick} [resourceTick]
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
 * @property {number} team
 * @property {Object.<string, number>} stats
 * @property {StatBuffSnapshot[]} statBuffs
 * @property {ResourceTickSnapshot[]} resourceTicks
 * @property {Object.<string, number>} cooldowns
 */

/**
 * @typedef {Object} BattleSnapshot
 * @property {ActorSnapshot[]} actors
 */

/**
 * @typedef {Object} CombatEvent_TurnStart
 * @property {string} type
 * @property {number} turn
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CombatEvent_SkillUsed
 * @property {string} type
 * @property {string} actor
 * @property {string} skill
 * @property {string[]} targets
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CombatEvent_DamageDealt
 * @property {string} type
 * @property {string} source
 * @property {string} target
 * @property {number} amount
 * @property {number} targetHp
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CombatEvent_Healed
 * @property {string} type
 * @property {string} source
 * @property {string} target
 * @property {number} amount
 * @property {number} targetHp
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CombatEvent_BuffApplied
 * @property {string} type
 * @property {string} source
 * @property {string} target
 * @property {string} buffId
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CombatEvent_BuffExpired
 * @property {string} type
 * @property {string} target
 * @property {string} buffId
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CombatEvent_ResourceDrained
 * @property {string} type
 * @property {string} target
 * @property {string} buffId
 * @property {string} resource
 * @property {number} amount
 * @property {number} targetResourceValue
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CombatEvent_BattleEnd
 * @property {string} type
 * @property {string} winner
 * @property {BattleSnapshot} snapshot
 */
