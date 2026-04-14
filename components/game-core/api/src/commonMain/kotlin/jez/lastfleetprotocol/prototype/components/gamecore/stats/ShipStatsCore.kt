package jez.lastfleetprotocol.prototype.components.gamecore.stats

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret

/**
 * Calculated stats for a ship design.
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
 * Pure calculation of ship stats from placed items and an item-definition resolver.
 *
 * This is the single source of truth for thrust/mass aggregation, shared by the
 * builder's live stats panel and the runtime [ShipDesignConverter].
 *
 * @param placedHulls hull pieces in the design
 * @param placedModules modules in the design
 * @param placedTurrets turrets in the design
 * @param resolveItem resolves an item-definition id to its [ItemDefinition], or null
 */
fun calculateShipStats(
    placedHulls: List<PlacedHullPiece>,
    placedModules: List<PlacedModule>,
    placedTurrets: List<PlacedTurret>,
    resolveItem: (String) -> ItemDefinition?,
): ShipStats {
    // Hull mass + armour contribution
    var hullMass = 0f
    for (placedHull in placedHulls) {
        val def = resolveItem(placedHull.itemDefinitionId) ?: continue
        val attrs = def.attributes as? ItemAttributes.HullAttributes ?: continue
        hullMass += attrs.mass
        hullMass += attrs.armour.density * attrs.mass * 0.1f
    }

    // Module mass and thrust
    var moduleMass = 0f
    var forwardThrust = 0f
    var lateralThrust = 0f
    var reverseThrust = 0f
    var angularThrust = 0f

    for (module in placedModules) {
        val def = resolveItem(module.itemDefinitionId)
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

    var turretMass = 0f
    for (turret in placedTurrets) {
        resolveItem(turret.itemDefinitionId)?.run {
            turretMass += attributes.mass
        }
    }

    val totalMass = hullMass + moduleMass + turretMass

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
