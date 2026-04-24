package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedKeel
import jez.lastfleetprotocol.prototype.components.gamecore.stats.ShipStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Slice B Unit 6 — verifies the three-state flightworthiness display derivation.
 * The composable itself is manually verified (Compose draw output) but the
 * (placedKeel, stats) → state mapping is pure and worth unit-testing so a
 * future refactor can't silently swap the NoKeel / MassExceedsLift logic.
 */
class FlightworthinessDisplayTest {

    private val somePlacedKeel = PlacedKeel(
        id = "pk",
        itemDefinitionId = "keel_def",
        position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
        rotation = 0f.rad,
    )

    @Test
    fun nullKeel_regardlessOfFlightworthyFlag_yieldsNoKeel() {
        // isFlightworthy can only be true when lift > 0 and totalMass <= lift,
        // which requires a Keel — but guard against a future change where the
        // flag could be spuriously true for a keel-less design.
        val stats = ShipStats(totalMass = 0f, totalLift = 0f, isFlightworthy = false)
        val result = flightworthinessDisplay(placedKeel = null, stats = stats)
        assertIs<FlightworthinessDisplay.NoKeel>(result)
    }

    @Test
    fun placedKeelAndFlightworthy_yieldsFlightworthy() {
        val stats = ShipStats(totalMass = 80f, totalLift = 100f, isFlightworthy = true)
        val result = flightworthinessDisplay(placedKeel = somePlacedKeel, stats = stats)
        assertIs<FlightworthinessDisplay.Flightworthy>(result)
    }

    @Test
    fun placedKeelAndNotFlightworthy_yieldsMassExceedsLift() {
        val stats = ShipStats(totalMass = 150f, totalLift = 100f, isFlightworthy = false)
        val result = flightworthinessDisplay(placedKeel = somePlacedKeel, stats = stats)
        val failure = assertIs<FlightworthinessDisplay.MassExceedsLift>(result)
        assertEquals(150f, failure.mass)
        assertEquals(100f, failure.lift)
    }

    @Test
    fun placedKeelAndZeroLift_yieldsMassExceedsLift() {
        // Edge: Keel placed but lift is 0 (poorly authored). isFlightworthy is
        // false. The user sees "Mass exceeds lift (X / 0)" — a clear signal to
        // pick a different Keel. NoKeel is specifically for the pre-commit case.
        val stats = ShipStats(totalMass = 40f, totalLift = 0f, isFlightworthy = false)
        val result = flightworthinessDisplay(placedKeel = somePlacedKeel, stats = stats)
        val failure = assertIs<FlightworthinessDisplay.MassExceedsLift>(result)
        assertEquals(40f, failure.mass)
        assertEquals(0f, failure.lift)
    }

    @Test
    fun placedKeelAndEqualMassAndLift_isFlightworthy() {
        // Boundary: totalMass == totalLift is valid per ShipStats formula.
        val stats = ShipStats(totalMass = 100f, totalLift = 100f, isFlightworthy = true)
        val result = flightworthinessDisplay(placedKeel = somePlacedKeel, stats = stats)
        assertIs<FlightworthinessDisplay.Flightworthy>(result)
    }
}
