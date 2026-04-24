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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    private fun makeKeelDef(
        id: String = "keel_def_1",
        lift: Float = 500f,
        maxHp: Float = 150f,
        shipClass: String = "fighter",
    ) = ItemDefinition(
        id = id,
        name = "Test Keel",
        vertices = triangleVertices,
        attributes = ItemAttributes.KeelAttributes(
            armour = SerializableArmourStats(hardness = 3f, density = 1.5f),
            sizeCategory = "medium",
            mass = 40f,
            maxHp = maxHp,
            lift = lift,
            shipClass = shipClass,
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

    private fun defaultPlacedKeel() = PlacedKeel(
        id = "pk1",
        itemDefinitionId = "keel_def_1",
        position = SceneOffset.Zero,
        rotation = 0f.rad,
    )

    private fun minimalDesign(
        keel: PlacedKeel? = defaultPlacedKeel(),
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
        itemDefs: List<ItemDefinition> = listOf(
            makeHullDef(), makeKeelDef(), makeReactorDef(), makeEngineDef(), makeTurretDef(),
        ),
    ) = ShipDesign(
        name = "Test Ship",
        itemDefinitions = itemDefs,
        placedKeel = keel,
        placedHulls = hulls,
        placedModules = modules,
        placedTurrets = turrets,
    )

    @Test
    fun happyPath_minimalDesign_convertsSuccessfully() {
        val result = convertShipDesign(minimalDesign(), turretGuns)
        assertTrue(result.isSuccess, "Conversion should succeed: ${result.exceptionOrNull()}")
        val config = result.getOrThrow()

        // Hulls list now includes the Keel + 1 regular hull piece
        assertEquals(2, config.hulls.size)
        // Internal systems now include KEEL + the 2 modules
        assertEquals(3, config.internalSystems.size)
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
        // Keel + 2 hull pieces = 3 hulls
        assertEquals(3, config.hulls.size)
    }

    @Test
    fun happyPath_hullTransformApplied() {
        val design = minimalDesign(
            hulls = listOf(
                PlacedHullPiece(
                    id = "ph1",
                    itemDefinitionId = "hull_def_1",
                    position = SceneOffset(10f.sceneUnit, 20f.sceneUnit),
                    rotation = 0f.rad,
                ),
            ),
        )
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        // The hull piece is hulls[1] — hulls[0] is the Keel.
        val hull = config.hulls[1]

        // First vertex (10, 0) + position offset (10, 20) = (20, 20)
        assertEquals(20f, hull.vertices.first().x.raw, 0.01f)
        assertEquals(20f, hull.vertices.first().y.raw, 0.01f)
    }

    @Test
    fun happyPath_hullRotationTransform() {
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
        val noseVertex = config.hulls[1].vertices.first() // hulls[0] is the Keel

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
    fun errorPath_noKeel_fails() {
        // Slice B: placedKeel is required. A design with no Keel produces Result.failure.
        val result = convertShipDesign(minimalDesign(keel = null), turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no Keel") == true)
    }

    @Test
    fun errorPath_noKeelDefinition_fails() {
        // PlacedKeel references a missing itemDefinitionId.
        val design = minimalDesign(
            keel = PlacedKeel("pk1", "MISSING_KEEL_ID", SceneOffset.Zero, 0f.rad),
        )
        val result = convertShipDesign(design, turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MISSING_KEEL_ID") == true)
    }

    @Test
    fun errorPath_keelPointsAtNonKeelDefinition_fails() {
        // The PlacedKeel's itemDefinitionId resolves to a HullAttributes, not KeelAttributes.
        val design = minimalDesign(
            keel = PlacedKeel("pk1", "hull_def_1", SceneOffset.Zero, 0f.rad),
        )
        val result = convertShipDesign(design, turretGuns)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not a keel type") == true)
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
            itemDefs = listOf(makeHullDef(), makeKeelDef(), unknownDef, makeTurretDef()),
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
    fun errorPath_unknownHullItemDefinitionId_fails() {
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
        // A Keel-only ship with no modules and no extra hulls is valid.
        val design = minimalDesign(modules = emptyList())
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        // internalSystems still contains the KEEL entry even without modules
        assertEquals(1, config.internalSystems.size)
        assertEquals(InternalSystemType.KEEL, config.internalSystems.first().type)
    }

    @Test
    fun edgeCase_zeroRegularHulls_succeeds() {
        // A Keel alone is enough hull geometry to fly.
        val design = minimalDesign(hulls = emptyList())
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        assertEquals(1, config.hulls.size) // just the Keel
    }

    @Test
    fun edgeCase_defaultEvasionModifier() {
        val config = convertShipDesign(minimalDesign(), turretGuns, defaultEvasionModifier = 0.5f).getOrThrow()
        assertEquals(0.5f, config.combatStats.evasionModifier)
    }

    @Test
    fun happyPath_keelEmitsInternalSystemSpec() {
        val config = convertShipDesign(minimalDesign(), turretGuns).getOrThrow()
        val keelSpec = config.internalSystems.firstOrNull { it.type == InternalSystemType.KEEL }
        assertNotNull(keelSpec, "Converter must emit a KEEL InternalSystemSpec")
        // maxHp reads from the authored KeelAttributes.maxHp
        assertEquals(150f, keelSpec.maxHp)
        assertEquals(40f, keelSpec.mass)
    }

    @Test
    fun happyPath_keelAppearsAsFirstHull() {
        // The converter emits the Keel as hulls[0] so runtime code (HullCollider,
        // rendering) treats it as a hull piece.
        val config = convertShipDesign(minimalDesign(), turretGuns).getOrThrow()
        // The Keel triangle vertices should be present at hulls[0].
        assertEquals(3, config.hulls[0].vertices.size)
        assertEquals(10f, config.hulls[0].vertices[0].x.raw, 0.01f)
    }

    @Test
    fun happyPath_keelContributesToDrag() {
        // A Keel's drag modifier affects the final drag coefficient — swapping a Keel
        // with high modifiers for one with low modifiers lowers terminal velocity.
        val lowDragKeel = ItemDefinition(
            id = "keel_low_drag",
            name = "Low drag Keel",
            vertices = triangleVertices,
            attributes = ItemAttributes.KeelAttributes(
                armour = SerializableArmourStats(hardness = 3f, density = 1.5f),
                sizeCategory = "medium",
                mass = 40f,
                forwardDragModifier = 0.5f,
                lift = 500f,
                maxHp = 150f,
                shipClass = "fighter",
            ),
        )
        val highDragKeel = ItemDefinition(
            id = "keel_high_drag",
            name = "High drag Keel",
            vertices = triangleVertices,
            attributes = ItemAttributes.KeelAttributes(
                armour = SerializableArmourStats(hardness = 3f, density = 1.5f),
                sizeCategory = "medium",
                mass = 40f,
                forwardDragModifier = 2.0f,
                lift = 500f,
                maxHp = 150f,
                shipClass = "fighter",
            ),
        )

        val lowConfig = convertShipDesign(
            minimalDesign(
                keel = PlacedKeel("pk", "keel_low_drag", SceneOffset.Zero, 0f.rad),
                itemDefs = listOf(makeHullDef(), lowDragKeel, makeReactorDef(), makeEngineDef(), makeTurretDef()),
            ),
            turretGuns,
        ).getOrThrow()
        val highConfig = convertShipDesign(
            minimalDesign(
                keel = PlacedKeel("pk", "keel_high_drag", SceneOffset.Zero, 0f.rad),
                itemDefs = listOf(makeHullDef(), highDragKeel, makeReactorDef(), makeEngineDef(), makeTurretDef()),
            ),
            turretGuns,
        ).getOrThrow()

        assertTrue(
            lowConfig.movementConfig.forwardDragCoeff < highConfig.movementConfig.forwardDragCoeff,
            "Keel with lower forward drag modifier should produce lower drag coefficient: " +
                "low=${lowConfig.movementConfig.forwardDragCoeff} vs high=${highConfig.movementConfig.forwardDragCoeff}"
        )
    }

    @Test
    fun edgeCase_minimalKeelOnlyShip_converts() {
        // The smallest representable flightworthy ship: tiny Keel, lift = 1, no modules, no turrets.
        val tinyKeel = ItemDefinition(
            id = "tiny_keel",
            name = "Tiny Keel",
            vertices = triangleVertices,
            attributes = ItemAttributes.KeelAttributes(
                armour = SerializableArmourStats(hardness = 1f, density = 1f),
                sizeCategory = "small",
                mass = 0.5f,
                lift = 1f,
                maxHp = 10f,
            ),
        )
        val design = ShipDesign(
            name = "Minimal",
            itemDefinitions = listOf(tinyKeel),
            placedKeel = PlacedKeel("pk", "tiny_keel", SceneOffset.Zero, 0f.rad),
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
        )
        val config = convertShipDesign(design, turretGuns).getOrThrow()
        assertEquals(1, config.hulls.size)
        assertEquals(1, config.internalSystems.size)
        assertEquals(InternalSystemType.KEEL, config.internalSystems.first().type)
    }

    @Test
    fun roundTripConversionAfterSerialization() {
        // Cover Unit 2's integration: a ShipDesign constructed from a serialization
        // round-trip still converts cleanly.
        val design = minimalDesign()
        val encoded = kotlinx.serialization.json.Json.encodeToString(ShipDesign.serializer(), design)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(ShipDesign.serializer(), encoded)
        val config = convertShipDesign(decoded, turretGuns).getOrThrow()

        // KEEL is slotted into internalSystems from the round-tripped design
        assertNotNull(config.internalSystems.firstOrNull { it.type == InternalSystemType.KEEL })
    }
}
