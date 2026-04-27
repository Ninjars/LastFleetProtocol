package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

/**
 * Launch-path seam between the scenario builder (writer) and the Game screen
 * (reader). Provided as a singleton via the DI graph.
 *
 * Lifecycle is **consume-on-read**: the scenario builder writes [slots]
 * before navigating to Game; `GameVM.init` calls [consume] once, which
 * returns the slots and nulls the field in one step. The production
 * Play-from-landing path always sees null and runs the demo, regardless of
 * whether the scenario builder was used earlier in the session.
 *
 * Persistent "what's currently running" state lives in
 * `GameStateManager.lastLaunched` — this holder is just a one-shot transport.
 */
class PendingScenario {

    var slots: List<SpawnSlotConfig>? = null

    /**
     * Atomically read and clear [slots]. Intended for `GameVM.init` to
     * dispatch to `startScene(slots)` (custom) or `startDemoScene()` (null).
     */
    fun consume(): List<SpawnSlotConfig>? = slots.also { slots = null }
}
