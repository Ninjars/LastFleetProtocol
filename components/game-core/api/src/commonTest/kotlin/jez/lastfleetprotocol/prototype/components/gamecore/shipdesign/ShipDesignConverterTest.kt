package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.gamecore.data.ProjectileStats
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShipDesignConverterTest {

    private val triangleVertices = listOf(
        SceneOffset(10f.sceneUnit, 0f.sceneUnit),
        SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
        SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
    )

    private val testGunData = GunData(
        projectileStats = ProjectileStats(
            damage = 15f, armourPiercing = 6f, toHitModifier = 0.15f,
            speed = 200f, lifetimeMs = 4000,
        ),
        aimTolerance = (AngleRadians.TwoPi / 1440f),
        magazineCapacity = 100,
        reloadMilliseconds = 2000,
        shotsPerBurst = 3,
        burstCycleMilliseconds = 100,
        cycleMilliseconds = 700,
    )

    private val turretGuns = mapOf("standard_turret" to testGunData)

    private fun makeHullDef(id: String = "hull_def_1") = ItemDefinition(
        id = id,
        name = "Test Hull",
        vertices = triangleVertices,
        attributes = ItemAttributes.HullAttributes(
            armour = SerializableArmourStats(hardness = 5f, density = 2f),
            sizeCategory = "medium",
            mass = 50f,
        ),
    )

    private fun makeEngineDef(id: String = "engine_def_1") = ItemDefinition(
        id = id,
        name = "Main Engine",
        vertices = emptyList(),
        attributes = ItemAttributes.ModuleAttributes(
            systemType = "MAIN_ENGINE",
            maxHp = 80f, density = 4f, mass = 15f,
            forwardThrust = 1200f, lateralThrust = 500f,
            reverseThrust = 500f, angularThrust = 300f,
        ),
    )

    private fun makeReactorDef(id: String = "reactor_def_1") = ItemDefinition(
        id = id,
        name = "Reactor",
        vertices = emptyList(),
        attributes = ItemAttributes.ModuleAttributes(
            systemType = "REACTOR",
            maxHp = 100f, density = 8f, mass = 20f,
        ),
    )

    private fun makeTurretDef(id: String = "turret_def_1") = ItemDefinition(
        id = id,
        name = "Standard Turret",
        vertices = triangleVertices,
        attributes = ItemAttributes.TurretAttributes(
            sizeCategory = "Medium", mass = 5f,
        ),
    )

    private fun minimalDesign(
        hulls: List<PlacedHullPiece> = listOf(
            PlacedHullPiece("ph1", "hull_def_1", SceneOffset.Zero, 0f.rad)
        ),
        modules: List<PlacedModule> = listOf(
            PlacedModule("pm1", "reactor_def_1", "REACTOR", SceneOffset.Zero, 0f.rad, parentHullId = "ph1"),
            PlacedModule("pm2", "engine_def_1", "MAIN_ENGINE", SceneOffset.Zero, 0f.rad, parentHullId = "ph1"),
        ),
        turrets: List<PlacedTurret> = listOf(
            PlacedTurret("pt1", "turret_def_1", "standard_turret", SceneOffset.Zero, 0f.rad, parentHullId = "ph1"),
        ),
        itemDefs: List<ItemDefinition> = listOf(makeHullDef(), makeReactorDef(), makeEngineDef(), makeTurretDef()),
    ) = ShipDesign(
        name = "Test Ship",
        itemDefinitions = itemDefs,
        placedHulls = hulls,
        placedModules = modules,
        placedTurrets = turrets,
    )

    @Test
    fun happyPath_minimalDesign_convertsSuccessfully() {
        val result = convertShipDesign(minimalDesign(), turretGuns)
        assertTrue(result.isSuccess, "Conversion should succeed: ${result.exceptionOrNull()}")
        val config = result.getOrThrow()

        assertEquals(1, config.hulls.size)
        assertEquals(2, config.internalSystems.size)
        assertEquals(1, config.turretConfigs.size)
        assertTrue(config.movementConfig.forwardThrust > 0f)
        assertTrue(config.totalMass > 0f)
    }

    @Test
    fun happyPath_multiHull_preservesBothHulls() {
        val design = minimalDesign(
            hulls = listOf(
                PlacedHullPiece("ph1", "hull_def_1", SceneOffset.Zero, 0f.rad),
                PlacedHullPiece("ph2", "hull_def_1", SceneOffset(20f.sceneUnit, 0f.sceneUnit), 0f.rad),
            ),
        )
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        assertEquals(2, config.hulls.size)
    }

    @Test
    fun happyPath_hullTransformApplied() {
        val design = minimalDesign(
            hulls = listOf(
                PlacedHullPiece(
                    id = "ph1",
                    itemDefinitionId = "hull_def_1",
                    position = SceneOffset(10f.sceneUnit, 20f.sceneUnit),
                    rotation = 0f.rad, // no rotation for easy verification
                ),
            ),
        )
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        val hull = config.hulls.first()

        // The first vertex (10, 0) + position offset (10, 20) = (20, 20)
        assertEquals(20f, hull.vertices.first().x.raw, 0.01f)
        assertEquals(20f, hull.vertices.first().y.raw, 0.01f)
    }

    @Test
    fun happyPath_hullRotationTransform() {
        // Rotate 90° (PI/2) — vertex at (10, 0) becomes (0, 10)
        val design = minimalDesign(
            hulls = listOf(
                PlacedHullPiece(
                    id = "ph1",
                    itemDefinitionId = "hull_def_1",
                    position = SceneOffset.Zero,
                    rotation = (PI.toFloat() / 2f).rad,
                ),
            ),
        )
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        val noseVertex = config.hulls.first().vertices.first() // was (10, 0), now rotated

        assertEquals(0f, noseVertex.x.raw, 0.1f)
        assertEquals(10f, noseVertex.y.raw, 0.1f)
    }

    @Test
    fun happyPath_movementConfigMatchesStatsCore() {
        val config = convertShipDesign(minimalDesign(), turretGuns).getOrThrow()

        assertEquals(1200f, config.movementConfig.forwardThrust)
        assertEquals(500f, config.movementConfig.lateralThrust)
        assertEquals(500f, config.movementConfig.reverseThrust)
        assertEquals(300f, config.movementConfig.angularThrust)
    }

    @Test
    fun happyPath_turretOffsetFromPlacedPosition() {
        val design = minimalDesign(
            turrets = listOf(
                PlacedTurret(
                    "pt1", "turret_def_1", "standard_turret",
                    position = SceneOffset(0f.sceneUnit, (-45f).sceneUnit),
                    rotation = 0f.rad, parentHullId = "ph1",
                ),
            ),
        )
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        assertEquals(0f, config.turretConfigs.first().offsetX)
        assertEquals(-45f, config.turretConfigs.first().offsetY)
    }

    @Test
    fun errorPath_zeroHulls_fails() {
        val design = minimalDesign(hulls = emptyList())
        val result = convertShipDesign(design, turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no hull pieces") == true)
    }

    @Test
    fun errorPath_unknownSystemType_fails() {
        val unknownDef = ItemDefinition(
            id = "plasma_def", name = "Plasma Core", vertices = emptyList(),
            attributes = ItemAttributes.ModuleAttributes(
                systemType = "PLASMA_CORE", maxHp = 100f, density = 8f, mass = 20f,
            ),
        )
        val design = minimalDesign(
            modules = listOf(
                PlacedModule("pm1", "plasma_def", "PLASMA_CORE", SceneOffset.Zero, 0f.rad, parentHullId = "ph1"),
            ),
            itemDefs = listOf(makeHullDef(), unknownDef, makeTurretDef()),
        )
        val result = convertShipDesign(design, turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PLASMA_CORE") == true)
    }

    @Test
    fun errorPath_duplicateModuleType_fails() {
        val design = minimalDesign(
            modules = listOf(
                PlacedModule("pm1", "reactor_def_1", "REACTOR", SceneOffset.Zero, 0f.rad, parentHullId = "ph1"),
                PlacedModule("pm2", "reactor_def_1", "REACTOR", SceneOffset.Zero, 0f.rad, parentHullId = "ph1"),
            ),
        )
        val result = convertShipDesign(design, turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("REACTOR") == true)
        assertTrue(result.exceptionOrNull()?.message?.contains("2 modules") == true)
    }

    @Test
    fun errorPath_unknownTurretGunId_fails() {
        val design = minimalDesign(
            turrets = listOf(
                PlacedTurret("pt1", "turret_def_1", "nonexistent_gun", SceneOffset.Zero, 0f.rad, parentHullId = "ph1"),
            ),
        )
        val result = convertShipDesign(design, turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("nonexistent_gun") == true)
    }

    @Test
    fun errorPath_unknownItemDefinitionId_fails() {
        val design = minimalDesign(
            hulls = listOf(
                PlacedHullPiece("ph1", "MISSING_ID", SceneOffset.Zero, 0f.rad),
            ),
        )
        val result = convertShipDesign(design, turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MISSING_ID") == true)
    }

    @Test
    fun edgeCase_zeroModules_succeeds() {
        val design = minimalDesign(modules = emptyList())
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        assertTrue(config.internalSystems.isEmpty())
    }

    @Test
    fun edgeCase_defaultEvasionModifier() {
        val config = convertShipDesign(minimalDesign(), turretGuns, defaultEvasionModifier = 0.5f).getOrThrow()
        assertEquals(0.5f, config.combatStats.evasionModifier)
    }
}
