package jez.lastfleetprotocol.prototype.components.shipbuilder.stats

import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState

/**
 * Calculated stats for the current ship design.
 */
data class ShipStats(
    val totalMass: Float = 0f,
    val forwardThrust: Float = 0f,
    val lateralThrust: Float = 0f,
    val reverseThrust: Float = 0f,
    val angularThrust: Float = 0f,
    val forwardAccel: Float = 0f,
    val lateralAccel: Float = 0f,
    val reverseAccel: Float = 0f,
    val angularAccel: Float = 0f,
)

/**
 * Standard mass values for internal system module types.
 * Matches DemoScenarioConfig's playerSystems values.
 */
private val SYSTEM_MASS = mapOf(
    "REACTOR" to 20f,
    "MAIN_ENGINE" to 15f,
    "BRIDGE" to 10f,
)

/**
 * Default thrust values when an Engine module is present.
 * Matches DemoScenarioConfig's playerShipConfig movement values.
 */
private const val DEFAULT_FORWARD_THRUST = 1200f
private const val DEFAULT_LATERAL_THRUST = 500f
private const val DEFAULT_REVERSE_THRUST = 500f
private const val DEFAULT_ANGULAR_THRUST = 300f

/**
 * Calculates ship stats from the current builder state.
 *
 * - Total mass: hull piece masses + armour contribution (density * mass * 0.1) + system module masses
 * - Thrust: if any Engine module is placed, use default thrust values
 * - Acceleration: thrust / mass per axis (0 if mass is 0)
 */
fun calculateStats(state: ShipBuilderState): ShipStats {
    // Hull mass + armour contribution
    var hullMass = 0f
    for (placedHull in state.placedHulls) {
        val def = state.hullPieces.find { it.id == placedHull.hullPieceId } ?: continue
        hullMass += def.mass
        hullMass += def.armour.density * def.mass * 0.1f
    }

    // System module mass
    var systemMass = 0f
    for (module in state.placedModules) {
        systemMass += SYSTEM_MASS[module.systemType] ?: 0f
    }

    val totalMass = hullMass + systemMass

    // Thrust: present if any engine module is placed
    val hasEngine = state.placedModules.any { it.systemType == "MAIN_ENGINE" }
    val forwardThrust = if (hasEngine) DEFAULT_FORWARD_THRUST else 0f
    val lateralThrust = if (hasEngine) DEFAULT_LATERAL_THRUST else 0f
    val reverseThrust = if (hasEngine) DEFAULT_REVERSE_THRUST else 0f
    val angularThrust = if (hasEngine) DEFAULT_ANGULAR_THRUST else 0f

    // Acceleration = thrust / mass
    val forwardAccel = if (totalMass > 0f) forwardThrust / totalMass else 0f
    val lateralAccel = if (totalMass > 0f) lateralThrust / totalMass else 0f
    val reverseAccel = if (totalMass > 0f) reverseThrust / totalMass else 0f
    val angularAccel = if (totalMass > 0f) angularThrust / totalMass else 0f

    return ShipStats(
        totalMass = totalMass,
        forwardThrust = forwardThrust,
        lateralThrust = lateralThrust,
        reverseThrust = reverseThrust,
        angularThrust = angularThrust,
        forwardAccel = forwardAccel,
        lateralAccel = lateralAccel,
        reverseAccel = reverseAccel,
        angularAccel = angularAccel,
    )
}
