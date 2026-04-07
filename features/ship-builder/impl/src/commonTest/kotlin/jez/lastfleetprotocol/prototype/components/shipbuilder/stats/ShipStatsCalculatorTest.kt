package jez.lastfleetprotocol.prototype.components.shipbuilder.stats

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState
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
        val hullDef = ItemDefinition(
            id = "hull1",
            name = "Test Hull",
            vertices = listOf(
                SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
                SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
            ),
            itemType = ItemType.HULL,
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 2f),
                sizeCategory = "medium",
                mass = 50f,
            ),
        )
        val placedHull = PlacedHullPiece(
            id = "placed_hull1",
            itemDefinitionId = "hull1",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
        )

        val state = ShipBuilderState(
            itemDefinitions = listOf(hullDef),
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
        val hullDef = ItemDefinition(
            id = "hull1",
            name = "Test Hull",
            vertices = listOf(
                SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
                SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
            ),
            itemType = ItemType.HULL,
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 2f),
                sizeCategory = "medium",
                mass = 50f,
            ),
        )
        val reactorDef = ItemDefinition(
            id = "reactor_def",
            name = "Reactor",
            vertices = emptyList(),
            itemType = ItemType.MODULE,
            attributes = ItemAttributes.ModuleAttributes(
                systemType = "REACTOR",
                maxHp = 100f,
                density = 8f,
                mass = 20f,
            ),
        )
        val placedHull = PlacedHullPiece(
            id = "placed_hull1",
            itemDefinitionId = "hull1",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
        )
        val reactor = PlacedModule(
            id = "reactor1",
            itemDefinitionId = "reactor_def",
            systemType = "REACTOR",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
            parentHullId = "placed_hull1",
        )

        val state = ShipBuilderState(
            itemDefinitions = listOf(hullDef, reactorDef),
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
        val hullDef = ItemDefinition(
            id = "hull1",
            name = "Test Hull",
            vertices = listOf(
                SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
                SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
            ),
            itemType = ItemType.HULL,
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 0f),
                sizeCategory = "medium",
                mass = 100f,
            ),
        )
        val engineDef = ItemDefinition(
            id = "engine_def",
            name = "Main Engine",
            vertices = emptyList(),
            itemType = ItemType.MODULE,
            attributes = ItemAttributes.ModuleAttributes(
                systemType = "MAIN_ENGINE",
                maxHp = 80f,
                density = 4f,
                mass = 15f,
                forwardThrust = 1200f,
                lateralThrust = 500f,
                reverseThrust = 500f,
                angularThrust = 300f,
            ),
        )
        val placedHull = PlacedHullPiece(
            id = "placed_hull1",
            itemDefinitionId = "hull1",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
        )
        val engine = PlacedModule(
            id = "engine1",
            itemDefinitionId = "engine_def",
            systemType = "MAIN_ENGINE",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 0f.rad,
            parentHullId = "placed_hull1",
        )

        val state = ShipBuilderState(
            itemDefinitions = listOf(hullDef, engineDef),
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
