package jez.lastfleetprotocol.prototype.components.game.managers

import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import jez.lastfleetprotocol.prototype.components.gamecore.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.convertShipDesign
import jez.lastfleetprotocol.prototype.components.gamecore.stats.calculateShipStats

/**
 * Outcome of running a [ShipDesign] through the combat-load spawn gate.
 *
 * The gate runs two independent checks in sequence:
 * 1. Structural: the converter must produce a [ShipConfig]. Fails when the
 *    design has no Keel, an unknown `systemType`, a missing `turretConfigId`,
 *    etc. Returned as [ConversionFailed] with the converter's error message.
 * 2. Flightworthy: even a structurally-valid design must have totalMass ≤ lift.
 *    Fails otherwise as [Unflightworthy] with the offending numbers for logging.
 *
 * Slice B Unit 4: replaces the Slice A `.getOrThrow()` calls in
 * [GameStateManager.startDemoScene] with a per-design Result-loop. Callers
 * branch on the result and log, skip, or spawn.
 */
internal sealed interface SpawnGateResult {
    /** Design passed both gates; [config] is ready to hand to `createShip`. */
    data class Ready(val config: ShipConfig) : SpawnGateResult

    /** Converter returned `Result.failure` — design is structurally invalid. */
    data class ConversionFailed(val reason: String) : SpawnGateResult

    /** Design converted cleanly but is over-mass for its Keel's lift budget. */
    data class Unflightworthy(val totalMass: Float, val totalLift: Float) : SpawnGateResult
}

/**
 * Run the two-stage spawn gate against a single [design].
 *
 * Pure function; no I/O, no logging. The caller is responsible for surfacing
 * the result (typically via `println` in [GameStateManager]).
 */
internal fun evaluateSpawnGate(
    design: ShipDesign,
    turretGuns: Map<String, GunData>,
): SpawnGateResult = try {
    runSpawnGate(design, turretGuns)
} catch (t: Throwable) {
    // Defensive: convertShipDesign and calculateShipStats return Result/values
    // and are not supposed to throw, but an internal bug in either would otherwise
    // propagate out of startDemoScene and crash the whole scene — exactly the
    // failure the Result-loop was designed to prevent. See Slice B Unit 4.
    SpawnGateResult.ConversionFailed(
        "unexpected error: ${t.message ?: t::class.simpleName ?: "unknown"}",
    )
}

private fun runSpawnGate(
    design: ShipDesign,
    turretGuns: Map<String, GunData>,
): SpawnGateResult {
    val conversion = convertShipDesign(design, turretGuns)
    val config = conversion.getOrElse {
        return SpawnGateResult.ConversionFailed(
            it.message ?: it::class.simpleName ?: "unknown",
        )
    }

    // Recompute flightworthiness from the design itself — this is the combat-load
    // gate specified in R24. Don't trust any builder-side flag; the design is the
    // authoritative input and the formula is cheap.
    val itemDefs = design.itemDefinitions.associateBy { it.id }
    val stats = calculateShipStats(
        placedHulls = design.placedHulls,
        placedModules = design.placedModules,
        placedTurrets = design.placedTurrets,
        placedKeel = design.placedKeel,
        resolveItem = { itemDefs[it] },
    )
    if (!stats.isFlightworthy) {
        return SpawnGateResult.Unflightworthy(
            totalMass = stats.totalMass,
            totalLift = stats.totalLift,
        )
    }

    return SpawnGateResult.Ready(config)
}
