package server.api

import game.Team
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// In-memory store for encounter enemy teams. For now only the enemy team is persisted; the player team
// will be supplied at battle time. This is intentionally simple and non-persistent.
object EncounterStore {
    private val encounters = ConcurrentHashMap<String, Team>()

    fun create(enemyTeam: Team): String =
        UUID.randomUUID().toString()
            .also { encounters[it] = enemyTeam.deepCopy() }

    fun get(encounterId: String): Team? =
        encounters[encounterId]?.deepCopy()

    // Simple helper for future cleanup logic (not used yet)
    fun remove(encounterId: String) =
        encounters.remove(encounterId)
}

