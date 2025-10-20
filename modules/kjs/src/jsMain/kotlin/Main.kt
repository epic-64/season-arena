import game.CompactCombatEvent
import game.CompactCombatEvent.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.Element

private fun describe(event: CompactCombatEvent): String = when (event) {
    is CBattleStart -> "Battle starts."
    is CTurnStart -> "-- Turn ${event.turn} --"
    is CCharacterActivated -> "${event.actor} prepares an action."
    is CSkillUsed -> "${event.actor} uses ${event.skill} on ${event.targets.joinToString() }"
    is CDamageDealt -> "${event.source} hits ${event.target} for ${event.amount} (HP ${event.targetHp})"
    is CHealed -> "${event.source} heals ${event.target} for ${event.amount} (HP ${event.targetHp})"
    is CBuffApplied -> "${event.source} applies ${event.buffId} to ${event.target}"
    is CBuffRemoved -> "${event.buffId} removed from ${event.target}"
    is CBuffExpired -> "${event.buffId} expired on ${event.target}"
    is CResourceDrained -> "${event.target} ${event.resource} changed by ${event.amount} -> ${event.targetResourceValue}"
    is CResourceRegenerated -> "${event.target} ${event.resource} +${event.amount} -> ${event.targetResourceValue}"
    is CBattleEnd -> "Winner: ${event.winner}"
}

private fun render(events: List<CompactCombatEvent>): Element {
    val doc = document

    val ul = doc.createElement("ul")
    events.forEach { e ->
        val li = doc.createElement("li")
        li.textContent = describe(e)
        ul.appendChild(li)
    }

    return ul
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
            console.error("Failed to fetch remote combat events", e).let { emptyList() }
        }

        val root = document.getElementById("kotlin-root")!!
        root.textContent = ""
        val eventsUl = render(events)
        root.appendChild(eventsUl)
    }
}
