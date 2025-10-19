package game

import game.CombatEvent.*
import game.CompactCombatEvent.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Conversions moved to game-model module (game-model/src/commonMain/kotlin/game/CompactTypes.kt)
// Functions: computeBattleDelta, BattleDelta.fullSnapshot, CombatEvent.compact, List<CombatEvent>.compact, toJson

fun BattleState.compactJson(): String = this.events.compact().toJson()