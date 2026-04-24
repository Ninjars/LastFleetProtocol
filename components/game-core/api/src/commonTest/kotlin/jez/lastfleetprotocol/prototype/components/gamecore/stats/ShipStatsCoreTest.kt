package jez.lastfleetprotocol.prototype.components.gamecore.stats

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedKeel
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun singleHull_producesDragCoefficients() {
        val hullDef = makeHullDef(id = "hull1", mass = 50f, armourDensity = 0f)
        val stats = calculateShipStats(
            placedHulls = listOf(
                PlacedHullPiece("ph1", "hull1", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad)
            ),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            resolveItem = { if (it == "hull1") hullDef else null },
        )
        // Hull has default drag modifiers (1.0). Bounding box from triangle vertices (10,0),(-5,5),(-5,-5):
        // width = 15, height = 10. Forward drag uses Y extent (10), lateral uses X extent (15).
        assertTrue(stats.forwardDragCoeff > 0f, "Forward drag should be non-zero for hull with vertices")
        assertTrue(stats.lateralDragCoeff > 0f, "Lateral drag should be non-zero")
        assertTrue(stats.reverseDragCoeff > 0f, "Reverse drag should be non-zero")
    }

    @Test
    fun hullWithCustomDragModifiers_weightsCorrectly() {
        val hullDef = ItemDefinition(
            id = "hull1",
            name = "Test Hull",
            vertices = triangleVertices,
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 0f),
                sizeCategory = "medium",
                mass = 50f,
                forwardDragModifier = 0.5f,
                lateralDragModifier = 2.0f,
                reverseDragModifier = 1.5f,
            ),
        )
        val stats = calculateShipStats(
            placedHulls = listOf(
                PlacedHullPiece("ph1", "hull1", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad)
            ),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            resolveItem = { if (it == "hull1") hullDef else null },
        )
        // Forward drag (lower modifier) should be less than lateral drag (higher modifier)
        assertTrue(stats.lateralDragCoeff > stats.forwardDragCoeff, "Higher modifier should produce higher drag")
    }

    @Test
    fun terminalVelocity_computedFromThrustAndDrag() {
        val hullDef = makeHullDef(id = "hull1", mass = 50f, armourDensity = 0f)
        val engineDef = makeEngineDef(id = "engine1", mass = 10f, forwardThrust = 1200f)
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

        assertTrue(stats.terminalVelForward < Float.MAX_VALUE, "Forward terminal velocity should be finite with drag")
        assertTrue(stats.terminalVelForward > 0f, "Forward terminal velocity should be positive with thrust")
        // v_t = sqrt(thrust / dragCoeff)
        val expectedVt = kotlin.math.sqrt(stats.forwardThrust / stats.forwardDragCoeff)
        assertEquals(expectedVt, stats.terminalVelForward, 0.01f)
    }

    @Test
    fun zeroDragCoeff_producesUnlimitedTerminalVelocity() {
        val stats = calculateShipStats(
            placedHulls = emptyList(), // no hulls → no drag
            placedModules = listOf(
                PlacedModule("pm1", "", "MAIN_ENGINE", SceneOffset(0f.sceneUnit, 0f.sceneUnit), 0f.rad, parentHullId = "")
            ),
            placedTurrets = emptyList(),
            resolveItem = { null },
        )
        assertEquals(Float.MAX_VALUE, stats.terminalVelForward)
    }

    @Test
    fun turnRate_computedFromAngularThrustAndMass() {
        val hullDef = makeHullDef(id = "hull1", mass = 50f, armourDensity = 0f)
        val engineDef = makeEngineDef(id = "engine1", mass = 10f, forwardThrust = 1200f)
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

        assertTrue(stats.turnRate > 0f, "Turn rate should be positive with angular thrust")
    }

    // --- Slice B: lift + flightworthiness + Keel drag contribution ---

    private fun makeKeelDef(
        id: String = "keel1",
        mass: Float = 40f,
        lift: Float = 200f,
        forwardDragModifier: Float = 1.0f,
        lateralDragModifier: Float = 1.0f,
        reverseDragModifier: Float = 1.0f,
        armourDensity: Float = 0f,
    ) = ItemDefinition(
        id = id,
        name = "Test Keel",
        vertices = triangleVertices,
        attributes = ItemAttributes.KeelAttributes(
            armour = SerializableArmourStats(hardness = 3f, density = armourDensity),
            sizeCategory = "medium",
            mass = mass,
            forwardDragModifier = forwardDragModifier,
            lateralDragModifier = lateralDragModifier,
            reverseDragModifier = reverseDragModifier,
            maxHp = 150f,
            lift = lift,
            shipClass = "fighter",
        ),
    )

    @Test
    fun keelProvidesLift_shipUnderMassIsFlightworthy() {
        val keelDef = makeKeelDef(mass = 40f, lift = 100f)
        val engineDef = makeEngineDef(id = "eng", mass = 15f, forwardThrust = 1200f)
        val defs = mapOf("keel1" to keelDef, "eng" to engineDef)

        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = listOf(
                PlacedModule("pm1", "eng", "MAIN_ENGINE", SceneOffset.Zero, 0f.rad, parentHullId = "pk1")
            ),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk1", "keel1", SceneOffset.Zero, 0f.rad),
            resolveItem = { defs[it] },
        )
        // totalMass = keel(40) + engine(15) = 55; lift = 100 → flightworthy
        assertEquals(55f, stats.totalMass, 0.01f)
        assertEquals(100f, stats.totalLift)
        assertTrue(stats.isFlightworthy)
    }

    @Test
    fun shipOverMass_isNotFlightworthy() {
        val keelDef = makeKeelDef(mass = 40f, lift = 100f)
        val engineDef = makeEngineDef(id = "eng", mass = 100f, forwardThrust = 1200f)
        val defs = mapOf("keel1" to keelDef, "eng" to engineDef)

        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = listOf(
                PlacedModule("pm1", "eng", "MAIN_ENGINE", SceneOffset.Zero, 0f.rad, parentHullId = "pk1")
            ),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk1", "keel1", SceneOffset.Zero, 0f.rad),
            resolveItem = { defs[it] },
        )
        // totalMass = 40 + 100 = 140; lift = 100 → unflightworthy
        assertEquals(140f, stats.totalMass, 0.01f)
        assertEquals(100f, stats.totalLift)
        assertFalse(stats.isFlightworthy)
    }

    @Test
    fun noKeel_isNotFlightworthy() {
        val hullDef = makeHullDef(id = "hull1", mass = 50f, armourDensity = 0f)
        val stats = calculateShipStats(
            placedHulls = listOf(
                PlacedHullPiece("ph1", "hull1", SceneOffset.Zero, 0f.rad)
            ),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = null,
            resolveItem = { if (it == "hull1") hullDef else null },
        )
        assertEquals(0f, stats.totalLift)
        assertFalse(stats.isFlightworthy)
    }

    @Test
    fun keelContributesToMass_includingArmour() {
        // Keel mass = 40; armour contribution = density(2) * mass(40) * 0.1 = 8 → 48
        val keelDef = makeKeelDef(mass = 40f, lift = 200f, armourDensity = 2f)
        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk1", "keel1", SceneOffset.Zero, 0f.rad),
            resolveItem = { if (it == "keel1") keelDef else null },
        )
        assertEquals(48f, stats.totalMass, 0.01f)
    }

    @Test
    fun keelContributesToDragAggregation() {
        // Two identical setups — only the Keel's forward drag modifier differs.
        // Keel with low forward modifier → lower forward drag coefficient.
        val lowDragKeel = makeKeelDef(id = "k_low", forwardDragModifier = 0.5f)
        val highDragKeel = makeKeelDef(id = "k_high", forwardDragModifier = 2.0f)

        val low = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk", "k_low", SceneOffset.Zero, 0f.rad),
            resolveItem = { if (it == "k_low") lowDragKeel else null },
        )
        val high = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk", "k_high", SceneOffset.Zero, 0f.rad),
            resolveItem = { if (it == "k_high") highDragKeel else null },
        )

        assertTrue(
            low.forwardDragCoeff < high.forwardDragCoeff,
            "Lower drag modifier should produce lower drag coefficient: low=${low.forwardDragCoeff} high=${high.forwardDragCoeff}",
        )
    }

    @Test
    fun keelAndHull_bothContributeToDragAggregation() {
        // With both Keel and a hull piece in the same design, total drag reflects
        // the area-weighted average of both per-piece modifiers.
        val keelDef = makeKeelDef(forwardDragModifier = 0.5f)
        val hullDef = makeHullDef(id = "hull1", mass = 50f, armourDensity = 0f)
        val defs = mapOf("keel1" to keelDef, "hull1" to hullDef)

        val keelOnly = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk", "keel1", SceneOffset.Zero, 0f.rad),
            resolveItem = { defs[it] },
        )
        val keelAndHull = calculateShipStats(
            placedHulls = listOf(
                PlacedHullPiece("ph1", "hull1", SceneOffset.Zero, 0f.rad)
            ),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk", "keel1", SceneOffset.Zero, 0f.rad),
            resolveItem = { defs[it] },
        )

        // Adding a hull with default modifier=1.0 to a Keel with modifier=0.5
        // should raise the forward drag coefficient (average pulled toward 1.0).
        assertTrue(
            keelAndHull.forwardDragCoeff > keelOnly.forwardDragCoeff,
            "Adding a hull with default drag should raise the aggregate drag vs a Keel-only ship",
        )
    }

    @Test
    fun zeroLiftKeel_isNotFlightworthy() {
        // A Keel with lift=0 fails the gate even if mass is trivially small.
        val keelDef = makeKeelDef(mass = 1f, lift = 0f)
        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk", "keel1", SceneOffset.Zero, 0f.rad),
            resolveItem = { if (it == "keel1") keelDef else null },
        )
        assertFalse(stats.isFlightworthy, "lift=0 must not be flightworthy")
    }

    @Test
    fun exactlyEqualMassAndLift_isFlightworthy() {
        // Boundary: totalMass == totalLift. Comparison is <= so it should pass.
        val keelDef = makeKeelDef(mass = 100f, lift = 100f, armourDensity = 0f)
        val stats = calculateShipStats(
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
            placedKeel = PlacedKeel("pk", "keel1", SceneOffset.Zero, 0f.rad),
            resolveItem = { if (it == "keel1") keelDef else null },
        )
        assertEquals(100f, stats.totalMass, 0.01f)
        assertEquals(100f, stats.totalLift)
        assertTrue(stats.isFlightworthy)
    }
}
