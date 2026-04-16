package jez.lastfleetprotocol.prototype.components.gamecore.stats

import jez.lastfleetprotocol.prototype.components.gamecore.geometry.calculatePolygonArea
import jez.lastfleetprotocol.prototype.components.gamecore.geometry.computeBoundingBoxExtents
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import kotlin.math.sqrt

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
    // Atmospheric drag model fields
    val forwardDragCoeff: Float = 0f,
    val lateralDragCoeff: Float = 0f,
    val reverseDragCoeff: Float = 0f,
    val terminalVelForward: Float = Float.MAX_VALUE,
    val terminalVelLateral: Float = Float.MAX_VALUE,
    val terminalVelReverse: Float = Float.MAX_VALUE,
    val turnRate: Float = 0f, // degrees per second
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

    // --- Drag coefficient computation (atmospheric model) ---
    // Area-weighted average of per-hull-piece drag modifiers × bounding box extent × RHO
    var totalHullArea = 0f
    var weightedForwardMod = 0f
    var weightedLateralMod = 0f
    var weightedReverseMod = 0f
    val allHullVertices = mutableListOf<com.pandulapeter.kubriko.types.SceneOffset>()

    for (placedHull in placedHulls) {
        val def = resolveItem(placedHull.itemDefinitionId) ?: continue
        val attrs = def.attributes as? ItemAttributes.HullAttributes ?: continue
        val area = calculatePolygonArea(def.vertices)
        if (area > 0f) {
            totalHullArea += area
            weightedForwardMod += attrs.forwardDragModifier * area
            weightedLateralMod += attrs.lateralDragModifier * area
            weightedReverseMod += attrs.reverseDragModifier * area
        }
        allHullVertices.addAll(def.vertices)
    }

    val (bbWidth, bbHeight) = computeBoundingBoxExtents(allHullVertices)
    // Width (X extent) = forward/reverse cross-section; Height (Y extent) = lateral cross-section
    val forwardExtent = bbHeight // ships moving forward present their Y-axis cross-section
    val lateralExtent = bbWidth  // ships moving laterally present their X-axis cross-section
    val reverseExtent = bbHeight // same as forward

    val forwardDragCoeff: Float
    val lateralDragCoeff: Float
    val reverseDragCoeff: Float

    if (totalHullArea > 0f) {
        val avgForwardMod = weightedForwardMod / totalHullArea
        val avgLateralMod = weightedLateralMod / totalHullArea
        val avgReverseMod = weightedReverseMod / totalHullArea
        forwardDragCoeff = forwardExtent * avgForwardMod * RHO
        lateralDragCoeff = lateralExtent * avgLateralMod * RHO
        reverseDragCoeff = reverseExtent * avgReverseMod * RHO
    } else {
        forwardDragCoeff = 0f
        lateralDragCoeff = 0f
        reverseDragCoeff = 0f
    }

    // Terminal velocity: v_t = sqrt(thrust / dragCoeff) per axis
    val terminalVelForward = terminalVelocity(forwardThrust, forwardDragCoeff)
    val terminalVelLateral = terminalVelocity(lateralThrust, lateralDragCoeff)
    val terminalVelReverse = terminalVelocity(reverseThrust, reverseDragCoeff)

    // Turn rate: degrees per second, derived from angular thrust and mass
    val turnRate = if (totalMass > 0f) angularThrust / totalMass * TURN_RATE_SCALE else 0f

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
        forwardDragCoeff = forwardDragCoeff,
        lateralDragCoeff = lateralDragCoeff,
        reverseDragCoeff = reverseDragCoeff,
        terminalVelForward = terminalVelForward,
        terminalVelLateral = terminalVelLateral,
        terminalVelReverse = terminalVelReverse,
        turnRate = turnRate,
    )
}

private fun terminalVelocity(thrust: Float, dragCoeff: Float): Float {
    if (dragCoeff <= 0f) return Float.MAX_VALUE
    if (thrust <= 0f) return 0f
    return sqrt(thrust / dragCoeff)
}

/**
 * Air density constant — dimensionless tuning parameter that scales all drag coefficients
 * uniformly. Increase for stronger drag (slower ships), decrease for weaker drag (faster ships).
 * Tuned alongside per-ship drag modifiers in the content-retuning pass.
 */
const val RHO = 0.005f

/**
 * Scaling factor from (angularThrust / mass) to degrees-per-second turn rate.
 * Tuned for gameplay feel.
 */
private const val TURN_RATE_SCALE = 50f

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
