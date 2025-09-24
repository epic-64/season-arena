package game

/**
 * Controls how battle deltas are emitted.
 * - FULL_EVERY_EVENT: every event carries a fullBattleDelta snapshot (original simple / fool proof mode)
 * - INITIAL_FULL_ONLY: only the very first TurnStart is full, subsequent events are minimal patches (current optimized mode)
 * - KEYFRAMES: (reserved) first snapshot + periodic full snapshots every [keyframeInterval] events, others are patches.
 */
enum class DeltaMode {
    FULL_EVERY_EVENT,
    INITIAL_FULL_ONLY,
    KEYFRAMES,
}

data class EngineConfig(
    val deltaMode: DeltaMode = DeltaMode.INITIAL_FULL_ONLY,
    val keyframeInterval: Int = 25, // only used when deltaMode == KEYFRAMES
)

