package jez.lastfleetprotocol.prototype.components.shipbuilder.stats

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.HullPieceDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.ShipBuilderState
import kotlin.test.Test
import kotlin.test.assertEquals

class ShipStatsCalculatorTest {

    @Test
    fun emptyDesign_returnsZeroStats() {
        val state = ShipBuilderState()
        val stats = calculateStats(state)

        assertEquals(0f, stats.totalMass)
        assertEquals(0f, stats.forwardAccel)
        assertEquals(0f, stats.lateralAccel)
        assertEquals(0f, stats.reverseAccel)
        assertEquals(0f, stats.angularAccel)
    }

    @Test
    fun singleHullPiece_includesHullMassAndArmourContribution() {
        val hullDef = HullPieceDefinition(
            id = "hull1",
            vertices = listOf(
                SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
                SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
            ),
            armour = SerializableArmourStats(hardness = 5f, density = 2f),
            sizeCategory = "medium",
            mass = 50f,
        )
        val placedHull = PlacedHullPiece(
            id = "placed_hull1",
            hullPieceId = "hull1",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
        )

        val state = ShipBuilderState(
            hullPieces = listOf(hullDef),
            placedHulls = listOf(placedHull),
        )
        val stats = calculateStats(state)

        // hull mass = 50, armour contribution = 2.0 * 50 * 0.1 = 10
        val expectedMass = 50f + 10f
        assertEquals(expectedMass, stats.totalMass)
        // No engine, so zero thrust and zero accel
        assertEquals(0f, stats.forwardThrust)
        assertEquals(0f, stats.forwardAccel)
    }

    @Test
    fun hullWithReactorModule_massIncludesReactorMass() {
        val hullDef = HullPieceDefinition(
            id = "hull1",
            vertices = listOf(
                SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
                SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
            ),
            armour = SerializableArmourStats(hardness = 5f, density = 2f),
            sizeCategory = "medium",
            mass = 50f,
        )
        val placedHull = PlacedHullPiece(
            id = "placed_hull1",
            hullPieceId = "hull1",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
        )
        val reactor = PlacedModule(
            id = "reactor1",
            systemType = "REACTOR",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
            parentHullId = "placed_hull1",
        )

        val state = ShipBuilderState(
            hullPieces = listOf(hullDef),
            placedHulls = listOf(placedHull),
            placedModules = listOf(reactor),
        )
        val stats = calculateStats(state)

        // hull mass = 50, armour = 10, reactor = 20
        val expectedMass = 50f + 10f + 20f
        assertEquals(expectedMass, stats.totalMass)
    }

    @Test
    fun hullWithEngineModule_hasThrustAndAcceleration() {
        val hullDef = HullPieceDefinition(
            id = "hull1",
            vertices = listOf(
                SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
                SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
            ),
            armour = SerializableArmourStats(hardness = 5f, density = 0f),
            sizeCategory = "medium",
            mass = 100f,
        )
        val placedHull = PlacedHullPiece(
            id = "placed_hull1",
            hullPieceId = "hull1",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
        )
        val engine = PlacedModule(
            id = "engine1",
            systemType = "MAIN_ENGINE",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
            parentHullId = "placed_hull1",
        )

        val state = ShipBuilderState(
            hullPieces = listOf(hullDef),
            placedHulls = listOf(placedHull),
            placedModules = listOf(engine),
        )
        val stats = calculateStats(state)

        // hull mass = 100, armour = 0, engine = 15
        val totalMass = 100f + 0f + 15f
        assertEquals(totalMass, stats.totalMass)

        assertEquals(1200f, stats.forwardThrust)
        assertEquals(500f, stats.lateralThrust)

        // forward accel = 1200 / 115
        val expectedForwardAccel = 1200f / totalMass
        assertEquals(expectedForwardAccel, stats.forwardAccel, 0.01f)
    }
}
