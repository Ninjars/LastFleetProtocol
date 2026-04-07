package jez.lastfleetprotocol.prototype.components.shipbuilder.stats

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
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
 * Calculates ship stats from the current builder state.
 *
 * - Total mass: hull piece masses + armour contribution (density * mass * 0.1) + module masses
 * - Thrust: summed from all engine modules via [ItemAttributes.ModuleAttributes]
 * - Acceleration: thrust / mass per axis (0 if mass is 0)
 */
fun calculateStats(state: ShipBuilderState): ShipStats {
    val itemDefs = state.itemDefinitions

    // Hull mass + armour contribution
    var hullMass = 0f
    for (placedHull in state.placedHulls) {
        val def = itemDefs.find { it.id == placedHull.itemDefinitionId } ?: continue
        val attrs = def.attributes as? ItemAttributes.HullAttributes ?: continue
        hullMass += attrs.mass
        hullMass += attrs.armour.density * attrs.mass * 0.1f
    }

    // Module mass and thrust (from ItemDefinition attributes)
    var moduleMass = 0f
    var forwardThrust = 0f
    var lateralThrust = 0f
    var reverseThrust = 0f
    var angularThrust = 0f

    for (module in state.placedModules) {
        val def = itemDefs.find { it.id == module.itemDefinitionId }
        val attrs = def?.attributes as? ItemAttributes.ModuleAttributes
        if (attrs != null) {
            moduleMass += attrs.mass
            forwardThrust += attrs.forwardThrust
            lateralThrust += attrs.lateralThrust
            reverseThrust += attrs.reverseThrust
            angularThrust += attrs.angularThrust
        } else {
            // Fallback for modules without an ItemDefinition (legacy data)
            moduleMass += LEGACY_SYSTEM_MASS[module.systemType] ?: 0f
            if (module.systemType == "MAIN_ENGINE") {
                forwardThrust += LEGACY_FORWARD_THRUST
                lateralThrust += LEGACY_LATERAL_THRUST
                reverseThrust += LEGACY_REVERSE_THRUST
                angularThrust += LEGACY_ANGULAR_THRUST
            }
        }
    }

    val totalMass = hullMass + moduleMass

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

// Legacy fallback values for modules without ItemDefinition
private val LEGACY_SYSTEM_MASS = mapOf(
    "REACTOR" to 20f,
    "MAIN_ENGINE" to 15f,
    "BRIDGE" to 10f,
)
private const val LEGACY_FORWARD_THRUST = 1200f
private const val LEGACY_LATERAL_THRUST = 500f
private const val LEGACY_REVERSE_THRUST = 500f
private const val LEGACY_ANGULAR_THRUST = 300f
