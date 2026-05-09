package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.SceneOffset

interface Targetable : Mobile {
    fun isValidTarget(): Boolean

    /**
     * Smoothed acceleration in scene-units per second². Read by lead-aim solvers
     * to extend the prediction model from constant-velocity (`pos(t) = pos +
     * vel*t`) to constant-acceleration (`pos(t) = pos + vel*t + 0.5*acc*t²`).
     * For thrust-active ships under AI orbit-chase steering — which spend
     * most of their engagement time accelerating *somewhere* — the
     * `0.5·a·t²` correction is dominant at multi-second flight times: at
     * cruiser scale (1 m/s² thrust, 7.6s flight) it adds ~29m of lead.
     *
     * Default returns zero so non-overriding implementers fall back to the
     * constant-velocity model. Rigs that track a velocity history (e.g.
     * [Ship]) override this with a smoothed estimate; raw single-frame
     * derivatives are too noisy under variable AI thrust.
     */
    val smoothedAcceleration: SceneOffset get() = SceneOffset.Zero
}