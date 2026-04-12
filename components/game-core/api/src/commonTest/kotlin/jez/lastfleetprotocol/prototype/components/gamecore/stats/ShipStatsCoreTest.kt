package jez.lastfleetprotocol.prototype.components.gamecore.stats

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import kotlin.test.Test
import kotlin.test.assertEquals

class ShipStatsCoreTest {

    private val triangleVertices = listOf(
        SceneOffset(10f.sceneUnit, 0f.sceneUnit),
        SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
        SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
    )

    private fun makeHullDef(
        id: String = "hull1",
        mass: Float = 50f,
        armourDensity: Float = 2f,
    ) = ItemDefinition(
        id = id,
        name = "Test Hull",
        vertices = triangleVertices,
        attributes = ItemAttributes.HullAttributes(
            armour = SerializableArmourStats(hardness = 5f, density = armourDensity),
            sizeCategory = "medium",
            mass = mass,
        ),
    )

    private fun makeEngineDef(
        id: String = "engine1",
        mass: Float = 15f,
        forwardThrust: Float = 1200f,
    ) = ItemDefinition(
        id = id,
        name = "Engine",
        vertices = emptyList(),
        attributes = ItemAttributes.ModuleAttributes(
            systemType = "MAIN_ENGINE",
            maxHp = 80f,
            density = 4f,
            mass = mass,
            forwardThrust = forwardThrust,
            lateralThrust = 500f,
            reverseThrust = 500f,
            angularThrust = 300f,
        ),
    )

    private fun makeTurretDef(
        id: String = "turret1",
        mass: Float = 5f,
    ) = ItemDefinition(
        id = id,
        name = "Turret",
        vertices = triangleVertices,
        attributes = ItemAttributes.TurretAttributes(
            sizeCategory = "Medium",
            mass = mass,
        ),
    )

    @Test
    fun emptyLists_returnsZeroStats() {
        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            resolveItem = { null },
        )
        assertEquals(0f, stats.totalMass)
        assertEquals(0f, stats.forwardThrust)
        assertEquals(0f, stats.forwardAccel)
    }

    @Test
    fun singleHull_includesMassAndArmourContribution() {
        val hullDef = makeHullDef(mass = 50f, armourDensity = 2f)
        val stats = calculateShipStats(
            placedHulls = listOf(
                PlacedHullPiece(
                    id = "placed1",
                    itemDefinitionId = "hull1",
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                )
            ),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            resolveItem = { if (it == "hull1") hullDef else null },
        )
        // hull mass = 50, armour contribution = 2 * 50 * 0.1 = 10
        assertEquals(60f, stats.totalMass)
        assertEquals(0f, stats.forwardThrust)
    }

    @Test
    fun hullWithEngine_hasThrustAndAcceleration() {
        val hullDef = makeHullDef(id = "hull1", mass = 100f, armourDensity = 0f)
        val engineDef = makeEngineDef(id = "engine1", mass = 15f, forwardThrust = 1200f)
        val defs = mapOf("hull1" to hullDef, "engine1" to engineDef)

        val stats = calculateShipStats(
            placedHulls = listOf(
                PlacedHullPiece("ph1", "hull1", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad)
            ),
            placedModules = listOf(
                PlacedModule("pm1", "engine1", "MAIN_ENGINE", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad, parentHullId = "ph1")
            ),
            placedTurrets = emptyList(),
            resolveItem = { defs[it] },
        )

        val expectedMass = 100f + 15f
        assertEquals(expectedMass, stats.totalMass)
        assertEquals(1200f, stats.forwardThrust)
        assertEquals(1200f / expectedMass, stats.forwardAccel, 0.01f)
    }

    @Test
    fun turretMass_includedInTotal() {
        val hullDef = makeHullDef(id = "hull1", mass = 50f, armourDensity = 0f)
        val turretDef = makeTurretDef(id = "turret1", mass = 8f)
        val defs = mapOf("hull1" to hullDef, "turret1" to turretDef)

        val stats = calculateShipStats(
            placedHulls = listOf(
                PlacedHullPiece("ph1", "hull1", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad)
            ),
            placedModules = emptyList(),
            placedTurrets = listOf(
                PlacedTurret("pt1", "turret1", "turret1", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad, parentHullId = "ph1")
            ),
            resolveItem = { defs[it] },
        )

        assertEquals(50f + 8f, stats.totalMass)
    }

    @Test
    fun legacyModule_usesHardcodedFallbackValues() {
        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = listOf(
                PlacedModule("pm1", "", "MAIN_ENGINE", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad, parentHullId = "")
            ),
            placedTurrets = emptyList(),
            resolveItem = { null }, // no definition found → legacy path
        )

        assertEquals(15f, stats.totalMass) // LEGACY_SYSTEM_MASS["MAIN_ENGINE"]
        assertEquals(1200f, stats.forwardThrust)
    }

    @Test
    fun multipleModules_thrustSumsCorrectly() {
        val engine1 = makeEngineDef(id = "e1", mass = 10f, forwardThrust = 600f)
        val engine2 = makeEngineDef(id = "e2", mass = 10f, forwardThrust = 400f)
        val defs = mapOf("e1" to engine1, "e2" to engine2)

        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = listOf(
                PlacedModule("pm1", "e1", "MAIN_ENGINE", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad, parentHullId = ""),
                PlacedModule("pm2", "e2", "MAIN_ENGINE", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad, parentHullId = ""),
            ),
            placedTurrets = emptyList(),
            resolveItem = { defs[it] },
        )

        assertEquals(20f, stats.totalMass)
        assertEquals(1000f, stats.forwardThrust) // 600 + 400
    }
}
