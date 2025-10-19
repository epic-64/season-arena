import game.CompactCombatEvent
import game.BattleSnapshot
import game.ActorSnapshot
import game.BattleDelta
import game.ActorDelta
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import kotlinx.serialization.decodeFromString

// Simple helper functions to build demo data
private fun sampleSnapshot(): BattleSnapshot = BattleSnapshot(
    actors = listOf(
        ActorSnapshot(
            actorClass = "Mage",
            name = "Alice",
            hp = 100,
            maxHp = 100,
            mana = 50,
            maxMana = 50,
            team = 0,
            stats = emptyMap(),
            statBuffs = emptyList(),
            resourceTicks = emptyList(),
            statOverrides = emptyList(),
            cooldowns = emptyMap()
        ),
        ActorSnapshot(
            actorClass = "Fighter",
            name = "Bob",
            hp = 120,
            maxHp = 120,
            mana = 30,
            maxMana = 30,
            team = 1,
            stats = emptyMap(),
            statBuffs = emptyList(),
            resourceTicks = emptyList(),
            statOverrides = emptyList(),
            cooldowns = emptyMap()
        )
    )
)

private fun emptyDelta(): BattleDelta = BattleDelta(actors = emptyList())

private fun sampleEvents(): List<CompactCombatEvent> {
    val snap = sampleSnapshot()
    return listOf(
        CompactCombatEvent.CBattleStart(snapshot = snap),
        CompactCombatEvent.CTurnStart(turn = 1, delta = emptyDelta()),
        CompactCombatEvent.CCharacterActivated(actor = "Alice", delta = emptyDelta()),
        CompactCombatEvent.CSkillUsed(actor = "Alice", skill = "Firebolt", targets = listOf("Bob"), delta = emptyDelta()),
        CompactCombatEvent.CDamageDealt(source = "Alice", target = "Bob", amount = 25, targetHp = 95, delta = emptyDelta()),
        CompactCombatEvent.CHealed(source = "Bob", target = "Bob", amount = 10, targetHp = 105, delta = emptyDelta()),
        CompactCombatEvent.CBattleEnd(winner = "Alice", delta = emptyDelta())
    )
}

private fun describe(event: CompactCombatEvent): String = when (event) {
    is CompactCombatEvent.CBattleStart -> "Battle starts."
    is CompactCombatEvent.CTurnStart -> "-- Turn ${event.turn} --"
    is CompactCombatEvent.CCharacterActivated -> "${event.actor} prepares an action."
    is CompactCombatEvent.CSkillUsed -> "${event.actor} uses ${event.skill} on ${event.targets.joinToString() }"
    is CompactCombatEvent.CDamageDealt -> "${event.source} hits ${event.target} for ${event.amount} (HP ${event.targetHp})"
    is CompactCombatEvent.CHealed -> "${event.source} heals ${event.target} for ${event.amount} (HP ${event.targetHp})"
    is CompactCombatEvent.CBuffApplied -> "${event.source} applies ${event.buffId} to ${event.target}"
    is CompactCombatEvent.CBuffRemoved -> "${event.buffId} removed from ${event.target}"
    is CompactCombatEvent.CBuffExpired -> "${event.buffId} expired on ${event.target}"
    is CompactCombatEvent.CResourceDrained -> "${event.target} ${event.resource} changed by ${event.amount} -> ${event.targetResourceValue}"
    is CompactCombatEvent.CResourceRegenerated -> "${event.target} ${event.resource} +${event.amount} -> ${event.targetResourceValue}"
    is CompactCombatEvent.CBattleEnd -> "Winner: ${event.winner}"
}

private fun render(events: List<CompactCombatEvent>) {
    val doc = document
    val root = doc.getElementById("kotlin-root") ?: run {
        val div = doc.createElement("div")
        div.id = "kotlin-root"
        doc.body?.appendChild(div)
        div
    }
    val ul = doc.createElement("ul")
    events.forEach { e ->
        val li = doc.createElement("li")
        li.textContent = describe(e)
        ul.appendChild(li)
    }
    root.appendChild(ul)
    // Also log JSON serialization of list
    console.log(Json.encodeToString(events))
}

private val scope = MainScope()

private fun renderLoading(message: String = "Loading combat events...") {
    val doc = document
    val root = doc.getElementById("kotlin-root") ?: run {
        val div = doc.createElement("div")
        div.id = "kotlin-root"
        doc.body?.appendChild(div)
        div
    }
    root.textContent = message
}

private suspend fun fetchRemoteEvents(): List<CompactCombatEvent> {
    val resp = window.fetch("http://localhost:8080/combat/example").await()
    if (!resp.ok) throw IllegalStateException("HTTP ${resp.status}")
    val text = resp.text().await()
    return Json.decodeFromString(text)
}

fun main() {
    renderLoading()
    scope.launch {
        val events = try {
            fetchRemoteEvents()
        } catch (e: Throwable) {
            console.error("Failed to fetch remote combat events, using sample events", e)
            sampleEvents()
        }
        render(events)
    }
}
