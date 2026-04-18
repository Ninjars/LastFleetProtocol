package jez.lastfleetprotocol.prototype.components.game.actors

/**
 * Three-state lifecycle for a [Ship]. Replaces the Slice A `isDestroyed: Boolean` flag.
 *
 * Transitions (see [Ship.updateLifecycle]):
 * - `Active` → `Destroyed(HULL)` — reactor HP reaches zero. No drift window.
 * - `Active` → `LiftFailed(remainingMs)` — Keel HP reaches zero. Drift begins.
 * - `LiftFailed` → `Destroyed(LIFT_FAILURE)` — drift countdown reaches zero.
 *
 * Reactor destruction takes precedence on same-frame double-kills — a single hit
 * that zeros both KEEL and REACTOR resolves as `Destroyed(HULL)` directly, skipping
 * `LiftFailed`. Rationale: reactor destruction is final combat death; drift is only
 * meaningful when the reactor survives.
 *
 * See Slice B plan Key Decisions 3 and 5.
 */
sealed interface ShipLifecycle {
    /** Ship is flying, controllable, and a valid combat target. */
    data object Active : ShipLifecycle

    /**
     * Keel destroyed; ship is drifting under drag with controls disengaged. Still
     * present in the scene but not a valid combat target. Transitions to
     * `Destroyed(LIFT_FAILURE)` when [remainingMs] decrements below zero.
     */
    data class LiftFailed(val remainingMs: Int) : ShipLifecycle

    /** Terminal state. Actor is being or has been removed. Cause distinguishes R19 log events. */
    data class Destroyed(val cause: DestructionCause) : ShipLifecycle
}

/** Why a ship reached `ShipLifecycle.Destroyed`. Surfaces in the combat `println` log. */
enum class DestructionCause {
    /** Reactor HP reached zero. Direct combat kill. */
    HULL,

    /** Keel HP reached zero, drift window elapsed. Lift-failure kill. */
    LIFT_FAILURE,
}
