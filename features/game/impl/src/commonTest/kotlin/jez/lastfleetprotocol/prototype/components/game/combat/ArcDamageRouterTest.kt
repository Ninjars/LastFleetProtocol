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

    @Test
    fun forwardHit_targetsBridge() {
        // Ship at origin, facing up (-Y). Impact from directly ahead (above the ship).
        val impactWorld = SceneOffset(0f.sceneUnit, (-10f).sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.BRIDGE, arc)
    }

    @Test
    fun rearHit_targetsMainEngine() {
        // Impact from directly behind (below the ship, +Y direction)
        val impactWorld = SceneOffset(0f.sceneUnit, 10f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.MAIN_ENGINE, arc)
    }

    @Test
    fun rightSideHit_targetsReactor() {
        // Impact from the right side (+X)
        val impactWorld = SceneOffset(10f.sceneUnit, 0f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.REACTOR, arc)
    }

    @Test
    fun leftSideHit_targetsReactor() {
        // Impact from the left side (-X)
        val impactWorld = SceneOffset((-10f).sceneUnit, 0f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.REACTOR, arc)
    }

    // --- Arc boundary test (exactly 45 degrees) ---

    @Test
    fun arcBoundary_45degrees_isBridgeOrReactor() {
        // Exactly at 45 degrees from forward: should be on the boundary.
        // Forward arc is <= 45 degrees, so exactly at 45 should be BRIDGE.
        val impactWorld = SceneOffset(10f.sceneUnit, (-10f).sceneUnit) // 45 degrees from -Y
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, noRotation)
        assertEquals(InternalSystemType.BRIDGE, arc, "At exactly 45 degrees, should be in forward arc")
    }

    // --- Rotated ship ---

    @Test
    fun rotatedShip_forwardHit_targetsBridge() {
        // Ship rotated PI/2 CCW: local -Y (forward) maps to +X in world space.
        // So a forward hit comes from +X direction.
        val rotation = (PI / 2.0).toFloat()
        val impactWorld = SceneOffset(10f.sceneUnit, 0f.sceneUnit)
        val arc = ArcDamageRouter.determineArc(impactWorld, shipPosition, rotation.rad)
        assertEquals(InternalSystemType.BRIDGE, arc)
    }

    // --- Damage routing tests ---

    @Test
    fun routeDamage_allAbsorbedByPrimary() {
        val systems = ShipSystems(defaultSpecs())
        // Forward hit → BRIDGE. Damage=30, AP=10, density=10 → absorbs all
        ArcDamageRouter.routeDamage(
            impactWorld = SceneOffset(0f.sceneUnit, (-10f).sceneUnit),
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
        // Use low density so primary only absorbs partial damage
        val specs = listOf(
            makeSpec(InternalSystemType.REACTOR, maxHp = 100f, density = 5f),
            makeSpec(InternalSystemType.MAIN_ENGINE, maxHp = 100f, density = 5f),
            makeSpec(InternalSystemType.BRIDGE, maxHp = 100f, density = 5f),
        )
        val systems = ShipSystems(specs)

        // Forward hit → BRIDGE primary. AP=20, density=5 → effective = damage * (5/20) = 25% absorbed
        // Damage=100: BRIDGE absorbs 100 * (5/20) = 25, remaining = 75
        // Secondary systems also absorb 25 each, remaining = 25
        val seededRandom = Random(42)
        ArcDamageRouter.routeDamage(
            impactWorld = SceneOffset(0f.sceneUnit, (-10f).sceneUnit),
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
