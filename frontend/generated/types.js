/**
 * AUTO-GENERATED FILE. DO NOT EDIT DIRECTLY.
 * Regenerate with: ./gradlew generateJsDoc
 */

/**
 * @readonly
 * @enum {string}
 */
export const BuffId = {
    Amplify: 'Amplify',
    Cheer: 'Cheer',
    MoraleBoost: 'MoraleBoost',
    Burn: 'Burn',
    Shock: 'Shock',
    Regen: 'Regen',
    Protection: 'Protection',
    Chill: 'Chill',
    Poison: 'Poison',
    Empower: 'Empower'
};

/**
 * @readonly
 * @enum {string}
 */
export const DamageType = {
    Physical: 'Physical',
    Magical: 'Magical',
    Ice: 'Ice',
    Fire: 'Fire',
    Lightning: 'Lightning',
    Poison: 'Poison',
    Absolute: 'Absolute'
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
 * @readonly
 * @enum {string}
 */
export const DamageModifier = {
    Critical: 'Critical',
    Blocked: 'Blocked',
    Resisted: 'Resisted'
};

/**
 * @typedef {Object} TemporalEffect
 * @property {number} duration
 * @property {BuffId} id
 * @property {number} stacks
 */

/**
 * @typedef {Object} SkillEffectType_ApplyBuff
 * @property {number} duration
 * @property {BuffId} id
 * @property {number} stacks
 */

/**
 * @typedef {Object} SkillEffectType_Damage
 * @property {number} amount
 * @property {DamageType} damageType
 */

/**
 * @typedef {Object} SkillEffectType_Heal
 * @property {number} power
 */

/**
 * @typedef {Object} SkillEffectType_RemoveTemporalEffect
 * @property {BuffId} effectId
 */

/**
 * @typedef {Object} SkillEffect
 * @property {SkillEffectType} type
 */

/**
 * @typedef {Object} Skill
 * @property {number} cooldown
 * @property {string} description
 * @property {SkillEffect[]} effects
 * @property {number} manaCost
 * @property {number} maximumTargets
 * @property {string} name
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
 * @typedef {Object} Tactic
 * @property {Function3[]} conditions
 * @property {Function1[]} ordering
 * @property {Skill} skill
 */

/**
 * @typedef {Object} ResistanceBag
 * @property {number} chaos
 * @property {number} fire
 * @property {number} ice
 * @property {number} lightning
 * @property {number} physical
 */

/**
 * @typedef {Object} StatsBag
 * @property {number} hp
 * @property {number} hpRegenPerTurn
 * @property {boolean} isAlive
 * @property {number} mana
 * @property {number} manaRegenPerTurn
 * @property {number} maxHp
 * @property {number} maxMana
 */

/**
 * @typedef {Object} Actor
 * @property {ActorClass} actorClass
 * @property {Amplifiers} amplifiers
 * @property {Object.<Skill, number>} cooldowns
 * @property {number} hpRegenPerTurn
 * @property {boolean} isAlive
 * @property {number} manaRegenPerTurn
 * @property {number} maxHp
 * @property {number} maxMana
 * @property {string} name
 * @property {Object.<DamageType, number>} resistances
 * @property {Object.<string, number>} stats
 * @property {StatsBag} statsBag
 * @property {Tactic[]} tactics
 * @property {number} team
 * @property {TemporalEffect[]} temporalEffects
 */

/**
 * @typedef {Object} Team
 * @property {Actor[]} actors
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
 * @typedef {Object} CompactCombatEvent_CBattleEnd
 * @property {BattleDelta} delta
 * @property {string} winner
 */

/**
 * @typedef {Object} CompactCombatEvent_CBattleStart
 * @property {BattleSnapshot} snapshot
 */

/**
 * @typedef {Object} CompactCombatEvent_CBuffApplied
 * @property {string} buffId
 * @property {BattleDelta} delta
 * @property {string} source
 * @property {string} target
 */

/**
 * @typedef {Object} CompactCombatEvent_CBuffExpired
 * @property {string} buffId
 * @property {BattleDelta} delta
 * @property {string} target
 */

/**
 * @typedef {Object} CompactCombatEvent_CBuffRemoved
 * @property {string} buffId
 * @property {BattleDelta} delta
 * @property {string} target
 */

/**
 * @typedef {Object} CompactCombatEvent_CCharacterActivated
 * @property {string} actor
 * @property {BattleDelta} delta
 */

/**
 * @typedef {Object} CompactCombatEvent_CDamageDealt
 * @property {number} amount
 * @property {BattleDelta} delta
 * @property {string} source
 * @property {string} target
 * @property {number} targetHp
 */

/**
 * @typedef {Object} CompactCombatEvent_CHealed
 * @property {number} amount
 * @property {BattleDelta} delta
 * @property {string} source
 * @property {string} target
 * @property {number} targetHp
 */

/**
 * @typedef {Object} CompactCombatEvent_CResourceDrained
 * @property {number} amount
 * @property {string} buffId
 * @property {BattleDelta} delta
 * @property {string} resource
 * @property {string} target
 * @property {number} targetResourceValue
 */

/**
 * @typedef {Object} CompactCombatEvent_CResourceRegenerated
 * @property {number} amount
 * @property {BattleDelta} delta
 * @property {string} resource
 * @property {string} target
 * @property {number} targetResourceValue
 */

/**
 * @typedef {Object} CompactCombatEvent_CSkillUsed
 * @property {string} actor
 * @property {BattleDelta} delta
 * @property {string} skill
 * @property {string[]} targets
 */

/**
 * @typedef {Object} CompactCombatEvent_CTurnStart
 * @property {BattleDelta} delta
 * @property {number} turn
 */

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
 * @typedef {(SkillEffectType_ApplyBuff|SkillEffectType_Damage|SkillEffectType_Heal|SkillEffectType_RemoveTemporalEffect)} SkillEffectType
 */

/**
 * @typedef {(CompactCombatEvent_CBattleEnd|CompactCombatEvent_CBattleStart|CompactCombatEvent_CBuffApplied|CompactCombatEvent_CBuffExpired|CompactCombatEvent_CBuffRemoved|CompactCombatEvent_CCharacterActivated|CompactCombatEvent_CDamageDealt|CompactCombatEvent_CHealed|CompactCombatEvent_CResourceDrained|CompactCombatEvent_CResourceRegenerated|CompactCombatEvent_CSkillUsed|CompactCombatEvent_CTurnStart)} CompactCombatEvent
 */

