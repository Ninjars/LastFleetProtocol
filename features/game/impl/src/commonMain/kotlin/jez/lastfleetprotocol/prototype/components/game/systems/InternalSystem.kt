package jez.lastfleetprotocol.prototype.components.game.systems

import jez.lastfleetprotocol.prototype.components.game.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.game.data.InternalSystemType

/**
 * Runtime state for a single internal system.
 *
 * Exposes read-only public state and a single mutation function [takeDamage].
 * Disabled state is triggered when cumulative damage >= disableThreshold (2/3 of maxHp),
 * i.e. when [currentHp] <= maxHp - disableThreshold. No recovery is possible.
 */
class InternalSystem(
    val spec: InternalSystemSpec,
) {
    val type: InternalSystemType get() = spec.type

    var currentHp: Float = spec.maxHp
        private set

    val isDestroyed: Boolean get() = currentHp <= 0f

    val isDisabled: Boolean get() = currentHp <= spec.maxHp - spec.disableThreshold

    /**
     * Apply [amount] points of damage to this system.
     * Returns the amount of damage actually absorbed (clamped so HP does not go below 0).
     * Damage to an already-destroyed system is ignored (returns 0).
     */
    fun takeDamage(amount: Float): Float {
        if (isDestroyed || amount <= 0f) return 0f

        val absorbed = amount.coerceAtMost(currentHp)
        currentHp -= absorbed
        return absorbed
    }
}
