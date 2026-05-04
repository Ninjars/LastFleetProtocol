package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.SceneOffset

interface Targetable : Mobile {
    fun isValidTarget(): Boolean

    /**
     * Velocity averaged over the last few frames. Lead-aim solvers read this
     * instead of the instantaneous [velocity] so per-frame steering jitter
     * (AI turning to chase an orbit point, brief thrust spikes during
     * approach) doesn't translate directly into wobbling firing solutions.
     *
     * Default returns instantaneous [velocity]; rigs that maintain a
     * smoothing buffer (e.g. [Ship]) override this with the buffered mean.
     */
    val smoothedVelocity: SceneOffset get() = velocity
}