package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.game.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArcDamageRouterTest {

    private fun makeSpec(
        type: InternalSystemType,
        maxHp: Float = 100f,
        density: Float = 10f,
        mass: Float = 5f,
    ) = InternalSystemSpec(type = type, maxHp = maxHp, density = density, mass = mass)

    private fun defaultSpecs() = listOf(
        makeSpec(InternalSystemType.REACTOR),
        makeSpec(InternalSystemType.MAIN_ENGINE),
        makeSpec(InternalSystemType.BRIDGE),
    )

    private val shipPosition = SceneOffset(0f.sceneUnit, 0f.sceneUnit)
    private val noRotation = AngleRadians.Zero

    // --- Arc determination tests ---
    // Forward = +X in atan2/Kubriko convention (rotation=0 means facing right)

    @Test
    fun forwardHit_targetsBridge() {
        // Impact from directly ahead (+X direction)
        val impactWorld = SceneOffset(10f.sceneUnit, 0f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.BRIDGE, arc)
    }

    @Test
    fun rearHit_targetsMainEngine() {
        // Impact from directly behind (-X direction)
        val impactWorld = SceneOffset((-10f).sceneUnit, 0f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.MAIN_ENGINE, arc)
    }

    @Test
    fun topSideHit_targetsReactor() {
        // Impact from above (-Y direction, perpendicular to forward)
        val impactWorld = SceneOffset(0f.sceneUnit, (-10f).sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.REACTOR, arc)
    }

    @Test
    fun bottomSideHit_targetsReactor() {
        // Impact from below (+Y direction, perpendicular to forward)
        val impactWorld = SceneOffset(0f.sceneUnit, 10f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.REACTOR, arc)
    }

    // --- Arc boundary test (exactly 45 degrees) ---

    @Test
    fun arcBoundary_45degrees_isBridgeOrReactor() {
        // Exactly at 45 degrees from forward (+X): impact at (10, 10)
        val impactWorld = SceneOffset(10f.sceneUnit, 10f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.BRIDGE, arc, "At exactly 45 degrees, should be in forward arc")
    }

    // --- Rotated ship ---

    @Test
    fun rotatedShip_forwardHit_targetsBridge() {
        // Ship rotated PI/2 (facing +Y). Forward hit comes from +Y direction.
        val rotation = (PI / 2.0).toFloat()
        val impactWorld = SceneOffset(0f.sceneUnit, 10f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, rotation.rad)
        assertEquals(InternalSystemType.BRIDGE, arc)
    }

    // --- Damage routing tests ---

    @Test
    fun routeDamage_allAbsorbedByPrimary() {
        val systems = ShipSystems(defaultSpecs())
        // Forward hit (+X) → BRIDGE. Damage=30, AP=10, density=10 → absorbs all
        ArcDamageRouter.routeDamage(
            impactWorld = SceneOffset(10f.sceneUnit, 0f.sceneUnit),
            shipPosition = shipPosition,
            shipRotation = noRotation,
            shipSystems = systems,
            damage = 30f,
            armourPiercing = 10f,
        )
        assertEquals(70f, systems.getSystem(InternalSystemType.BRIDGE).currentHp)
        // Other systems untouched
        assertEquals(100f, systems.getSystem(InternalSystemType.REACTOR).currentHp)
        assertEquals(100f, systems.getSystem(InternalSystemType.MAIN_ENGINE).currentHp)
    }

    @Test
    fun routeDamage_overPenetration_damageSpillsToSecondaries() {
        val specs = listOf(
            makeSpec(InternalSystemType.REACTOR, maxHp = 100f, density = 5f),
            makeSpec(InternalSystemType.MAIN_ENGINE, maxHp = 100f, density = 5f),
            makeSpec(InternalSystemType.BRIDGE, maxHp = 100f, density = 5f),
        )
        val systems = ShipSystems(specs)

        // Forward hit (+X) → BRIDGE primary. AP=20, density=5 → effective = damage * (5/20) = 25% absorbed
        val seededRandom = Random(42)
        ArcDamageRouter.routeDamage(
            impactWorld = SceneOffset(10f.sceneUnit, 0f.sceneUnit),
            shipPosition = shipPosition,
            shipRotation = noRotation,
            shipSystems = systems,
            damage = 100f,
            armourPiercing = 20f,
            random = seededRandom,
        )

        // BRIDGE should have taken damage
        assertTrue(systems.getSystem(InternalSystemType.BRIDGE).currentHp < 100f,
            "Bridge should have taken damage")
        // At least one secondary should also have taken damage
        val secondaryDamaged = systems.getSystem(InternalSystemType.REACTOR).currentHp < 100f
                || systems.getSystem(InternalSystemType.MAIN_ENGINE).currentHp < 100f
        assertTrue(secondaryDamaged, "At least one secondary system should have taken spill damage")
    }
}
