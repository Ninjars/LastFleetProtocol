package jez.lastfleetprotocol.prototype.components.game.systems

import jez.lastfleetprotocol.prototype.components.game.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.game.data.InternalSystemType

/**
 * Holds the flat list of a ship's internal systems and orchestrates
 * damage application and capability queries.
 *
 * The system itself does not know about the ship — it only manages
 * internal system state and exposes query functions.
 */
class ShipSystems(
    specs: List<InternalSystemSpec>,
) {
    private val systems: Map<InternalSystemType, InternalSystem> =
        specs.associate { it.type to InternalSystem(it) }

    fun getSystem(type: InternalSystemType): InternalSystem =
        systems.getValue(type)

    /**
     * Apply damage to a specific system type.
     *
     * Damage absorption: if the system's density >= [armourPiercing], the system
     * absorbs all the damage. Otherwise the system absorbs
     * `damage * (density / armourPiercing)` and the remainder passes through.
     *
     * @return the amount of damage absorbed by the system.
     */
    fun applyDamage(
        systemType: InternalSystemType,
        damage: Float,
        armourPiercing: Float,
    ): Float {
        val system = systems[systemType] ?: return 0f
        if (damage <= 0f || armourPiercing <= 0f) return 0f

        val effectiveDamage = if (system.spec.density >= armourPiercing) {
            damage
        } else {
            damage * (system.spec.density / armourPiercing)
        }

        return system.takeDamage(effectiveDamage)
    }

    fun isReactorDestroyed(): Boolean =
        getSystem(InternalSystemType.REACTOR).isDestroyed

    /**
     * Power is available only if the reactor is neither disabled nor destroyed.
     */
    fun isPowered(): Boolean {
        val reactor = getSystem(InternalSystemType.REACTOR)
        return !reactor.isDisabled && !reactor.isDestroyed
    }

    /**
     * Movement requires power and the main engine to be functional.
     */
    fun canMove(): Boolean {
        if (!isPowered()) return false
        val engine = getSystem(InternalSystemType.MAIN_ENGINE)
        return !engine.isDisabled && !engine.isDestroyed
    }

    /**
     * Fire control requires power and the bridge to be functional.
     */
    fun hasFireControl(): Boolean {
        if (!isPowered()) return false
        val bridge = getSystem(InternalSystemType.BRIDGE)
        return !bridge.isDisabled && !bridge.isDestroyed
    }

    /**
     * Receiving orders requires power and the bridge to be functional.
     */
    fun canReceiveOrders(): Boolean {
        if (!isPowered()) return false
        val bridge = getSystem(InternalSystemType.BRIDGE)
        return !bridge.isDisabled && !bridge.isDestroyed
    }
}
