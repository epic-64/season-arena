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

// ---- Ally Conditions ----
fun minimumAlliesBelowHp(n: Int, threshold: Double): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, allies, _ -> allies.count { it.getHp() <= it.statsBag.maxHp * threshold } >= n }

fun minimumAlliesHaveBuff(name: String, n: Int): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, allies, _ -> allies.count { ally -> ally.temporalEffects.any { it.id == name } } >= n }

// ---- Actor Conditions ----
fun selfHasBuff(name: String): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { actor, _, _ -> actor.temporalEffects.any { it.id == name } }

// ---- Enemy Conditions ----
fun minimumEnemiesAlive(n: Int): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, _, enemies -> enemies.count { it.isAlive } >= n }

fun enemyWeakTo(damageType: DamageType): (Actor, List<Actor>, List<Actor>) -> Boolean =
    { _, _, enemies -> enemies.any { it.getResistance(damageType) < 0 } }

// ---- Ordering Functions ----
fun leastHp(actors: List<Actor>): List<Actor> = actors.sortedBy { it.getHp() }
fun mostHp(actors: List<Actor>): List<Actor> = actors.sortedByDescending { it.getHp() }
fun leastMana(actors: List<Actor>): List<Actor> = actors.sortedBy { it.getMana() }
fun mostMana(actors: List<Actor>): List<Actor> = actors.sortedByDescending { it.getMana() }